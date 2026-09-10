# Order Notifications — Design Options

**Status:** Proposal / not yet implemented
**Goal:**
1. Notify the buyer that their order has been packed and is ready for pickup
   (sections A–B).
2. Remind the buyer about order-cycle deadlines and pickup, driven by a
   configurable status-checking worker with per-type opt-outs (section C).

---

## Current State (what already exists)

- **No push infrastructure anywhere in the project.** The seller "notifications"
  feature is a local `NotificationListenerService` running on the seller's own
  device that watches Firebase RTDB and posts a system notification. There is no
  FCM, no APNs, no Cloud Functions, and no stored device tokens. Push is also
  listed as an unbuilt gap in the web-implementation notes.
- **The buyer app already has a real-time order stream:**
  `OrderRepository.observeBuyerOrders(...)` returns a `Flow<List<Order>>` with
  live updates.
- **A lightweight status-write path already exists:**
  `OrderRepository.updateOrderStatus(sellerId, date, orderId, status)` — a
  natural place to model a "mark as packed" write.
- `OrderStatus` = `DRAFT, PLACED, LOCKED, COMPLETED, CANCELLED, DEMO_ORDER`.
  This enum drives `isEditable()`, `isFinalized()`, and the auto-transition
  logic in `Order.updatedStatusOrNull()`.

The design splits into two independent decisions: **how "packed" is stored**
and **how the buyer is told**.

---

## A. Data Model for "Packed"

### A1. `readyAt: Long?` field on `Order` — recommended

New nullable timestamp on `Order`, orthogonal to `status`. Seller sets it;
`null` means not packed.

- **Pro:** Does not touch lifecycle/deadline logic or any `when (status)` site.
  Captures the "when" for free. An order can legitimately be `PLACED` or
  `LOCKED` *and* ready — readiness is not a point on the lifecycle line.
- **Con:** One more field to serialize/migrate (defaults to `null`, so safe).
- **Repository:** add `markOrderReady(sellerId, date, orderId, ready: Boolean)`
  mirroring `updateOrderStatus`. Keep it toggleable so the seller can undo a
  misclick.

### A2. New `OrderStatus.READY` enum value

Add a value to the lifecycle enum.

- **Pro:** Semantically tidy in isolation.
- **Con:** Collides with the lifecycle — readiness overlaps `PLACED`/`LOCKED`.
  Requires auditing `isEditable`, `isFinalized`, `updatedStatusOrNull`, and
  every exhaustive `when (status)`. More risk, no real gain.

### A3. Dedicated fulfillment sub-object / notifications subtree

A structured object under the order node.

- **Con:** Overkill for a single boolean today. Revisit if fulfillment grows
  more states (e.g. `PICKED_UP`, partial fulfilment, substitutions).

**Decision: A1.**

### Seller UX

- "Als gepackt markieren" button on the seller `OrderDetailScreen`.
- Visible only for active orders on/around pickup day.
- Shows a "Abholbereit · 14:32" state once set, with an undo affordance.

---

## B. How the Buyer Finds Out

### B1. In-app only, via the existing Flow — phase 1, cheap

The buyer app already collects `observeBuyerOrders`. When `readyAt` flips from
`null` to set, show a banner on the order-detail / home hero card
("Deine Bestellung ist abholbereit") plus a badge, and optionally add a row to
the buyer `NotificationsScreen` feed.

- **Cost:** ~0.5–1 day. No new infra, no permissions.
- **Platforms:** Android + iOS + web.
- **Limit:** only while the app is open. Must guard against re-firing on every
  Flow emission (compare previous `readyAt`, or persist a "seen" flag).

### B2. Local notification from a buyer-side observer — not recommended

Mirror the seller's `NotificationListenerService` / WorkManager pattern on the
buyer side to post a system notification while backgrounded.

- **Cost:** +1–2 days.
- **Limit:** fragile under Android background limits, dead once the app is
  swiped away, effectively a non-starter on iOS. Throwaway plumbing that B3
  replaces.

### B3. True push via FCM + Cloud Function — phase 2, the real solution

A Cloud Function triggers on the RTDB write to
`.../orders/{date}/{orderId}/readyAt`, reads the buyer's FCM token from
`/buyers/{buyerId}/profile`, and sends a notification+data message that
deep-links to the order.

- **Needs:**
  - FCM dependency in `shared` / `androidApp`.
  - APNs setup for iOS.
  - Token registration + refresh, stored on the buyer profile.
  - A Firebase Functions project.
  - Notification-tap → order deep link.
  - A per-buyer opt-out toggle.
- **Cost:** ~3–5 days including iOS/APNs and Functions.
- **Payoff:** works with the app closed on both platforms; the same token +
  Function infra is reusable for every future push.

### B4. Email / SMS from the same Cloud Function — optional complement

Firebase "Trigger Email" extension (buyer email is already on the profile) or
Twilio for SMS, sent from the same Function.

- **Cost:** ~0.5 day on top of a Function.
- **Use as:** a fallback for users who denied push, not the primary channel.
  Heavy for a weekly-pickup app and carries per-message cost.

---

## C. Reminder Notifications — Configurable Status-Checking Worker

A client-side engine that periodically evaluates the buyer's current order
state and fires the right reminder(s) for where the order is in its lifecycle.
Each notification carries an inline **"turn this reminder off"** action, and the
profile screen exposes a switch per reminder type.

### C1. Reminder types (buyer side)

All conditions are evaluated against the buyer's order(s) for the **upcoming
pickup cycle** (cycle key = pickup date `yyyyMMdd`). Timing uses the existing
`OrderDateUtils.calculateNextPickupDate()` and `order.canEdit()` logic.

| Type | Fires when | Suggested timing |
|---|---|---|
| `ORDER_READY` | `order.readyAt != null` and not yet notified (see section B) | on detection |
| `ORDER_DEADLINE` | no active `PLACED`/`LOCKED` order for the upcoming Thursday, and/or a non-empty `DRAFT` basket exists | Mon evening + Tue ~18:00 before the Tue 23:59 deadline |
| `EDIT_WINDOW_CLOSING` | `status == PLACED` && `canEdit()` && deadline within ~6 h | Tue evening |
| `PICKUP_REMINDER` | `status == LOCKED` && pickup is today or tomorrow | Wed evening + Thu morning |
| `UNCLAIMED_ORDER` | `status == LOCKED` && pickup date has passed && not marked collected | Thu evening / Fri |

`ORDER_DEADLINE` is the only type that needs "the user has *not* done X"
targeting — the client can evaluate this for its own account, but a lapsed user
whose app never runs is only reachable via a server-side engine (see C5).

### C2. Preference model

`BuyerNotificationPreferences` — persisted locally with DataStore, and mirrored
to `/buyers/{buyerId}/profile` if/when the server engine (C5) is added.

```
masterEnabled: Boolean            // global kill switch
orderReadyEnabled: Boolean
orderDeadlineEnabled: Boolean
editWindowEnabled: Boolean
pickupReminderEnabled: Boolean
unclaimedOrderEnabled: Boolean
quietHoursStart: LocalTime?       // suppress + defer notifications in-window
quietHoursEnd: LocalTime?
lastNotifiedCycle: Map<ReminderType, String>   // per-type dedupe, value = cycle key
```

- Toggling a type off cancels any pending worker/notification for it.
- `lastNotifiedCycle` guarantees at-most-once per type per pickup cycle even if
  the worker runs many times.

### C3. Worker design (Android — `WorkManager`)

**Self-rescheduling one-time chain (recommended over a fixed periodic worker):**

1. `ReminderWorker : CoroutineWorker` runs → loads orders (repository/local
   cache) + `BuyerNotificationPreferences` + `now`.
2. Delegates to a pure `ReminderEvaluator` in `commonMain`
   (`orders + prefs + now → List<ReminderType>`) — no platform deps, fully
   unit-testable.
3. For each returned type not already in `lastNotifiedCycle` for this cycle and
   not inside quiet hours: post a local notification, then record the cycle key.
4. Compute the **next** relevant instant from the order state and enqueue a
   `OneTimeWorkRequest` with `setInitialDelay(...)`, re-arming the chain.

Re-arm the chain also on app start and on `BOOT_COMPLETED`. Add a once-a-day
`PeriodicWorkRequest` safety net that re-arms the chain if a one-time work was
dropped by the system.

Constraints: no network required (reads cached state; refresh opportunistically),
`setRequiresBatteryNotLow(false)`. Expect Doze batching — treat all timing as
best-effort ± the maintenance window.

### C4. "Turn this reminder off" notification action

- `NotificationCompat.Builder.addAction(icon, "Erinnerung ausschalten",
  pendingIntent)`.
- `pendingIntent` targets a `BroadcastReceiver` (`DisableReminderReceiver`) with
  an extra `reminderType`.
- Receiver: flip that pref to `false` in DataStore, cancel the notification,
  cancel any pending worker for the type, post a brief confirmation.
- Content-tap intent deep-links to the order detail screen (or the profile
  notification settings for `ORDER_DEADLINE`).

### C5. iOS parity / server alternative

`WorkManager` is Android-only. Options:

- **iOS-local:** `BGTaskScheduler` (background refresh, best-effort) +
  `UNUserNotificationCenter` for locally-scheduled notifications computed from
  order state on foreground. Same `ReminderEvaluator` from `commonMain`.
- **Server engine (cleaner long-term):** a scheduled Cloud Function (Cloud
  Scheduler / pub-sub cron) runs Mon/Tue/Wed/Thu, queries buyers by order
  state, and sends FCM. Shares one implementation across platforms and is the
  only way to reach lapsed users for `ORDER_DEADLINE`. Depends on the same
  FCM + token infra as **B3**.

The WorkManager engine is a solid Android-first phase 1; C5-server is the
consolidation step once B3 exists.

### C6. Shared vs platform code

| Layer | Contents |
|---|---|
| `commonMain` | `ReminderType` enum, `BuyerNotificationPreferences`, `ReminderEvaluator` (pure), preference repository interface |
| `androidMain` | `ReminderWorker`, chain scheduler, `DisableReminderReceiver`, notification builder, `BOOT_COMPLETED` receiver |
| `iosMain` | `BGTaskScheduler` registration + `UNUserNotificationCenter` scheduling |
| `buyMain` | Profile → "Benachrichtigungen" section: switch per type + quiet-hours pickers, wired through a ViewModel to the preference repository |

---

## Recommendation

1. **A1** — `readyAt: Long?` on `Order` + `markOrderReady(...)` in
   `OrderRepository` + seller "Mark as packed" button.
2. **B1** — in-app banner via the existing Flow. Ships in ~a day, immediate
   value.
3. **C** — `ReminderEvaluator` in `commonMain` + Android `WorkManager` chain +
   `BuyerNotificationPreferences` + profile settings section + per-notification
   "turn off" action. Start with `PICKUP_REMINDER` and `EDIT_WINDOW_CLOSING`
   (pure self-state, no server needed), then `ORDER_DEADLINE` / `UNCLAIMED_ORDER`.
4. **B3** — FCM + Cloud Function for closed-app *ready* delivery, with a
   `notifyWhenOrderReady` opt-out on `BuyerProfile`.
5. **C5-server** — fold the reminder engine into a scheduled Cloud Function once
   B3's FCM infra exists; gives iOS parity and lapsed-user reach.
6. **B4** — email fallback only, later, if desired.

### Rough effort

| Piece | Effort |
|---|---|
| A1 model + seller button + B1 in-app banner | ~1–1.5 days |
| B2 buyer local notification (if pursued instead of B3) | +1–2 days |
| C evaluator + Android worker chain + prefs + profile UI + off-action | ~3–4 days |
| C iOS parity (`BGTaskScheduler` + `UNUserNotificationCenter`) | +1.5–2 days |
| B3 FCM + Cloud Function (Android + iOS + token mgmt) | +3–5 days |
| C5 server reminder engine on top of B3 | +2–3 days |
| B4 email/SMS on top of a Function | +0.5 day |

---

## Phased Implementation Plan

Each phase is independently shippable and leaves the build green on all three
targets. **MVP = Phases 0–2** (~1 week): seller can mark packed, buyer sees it
in-app, and Android reminders work for self-state order types.

### Phase 0 — Shared foundation (no user-visible change)

- `Order.readyAt: Long?` + serialization in `GitLiveOrderRepository`,
  `MockOrderRepository`, `FakeOrderRepository`.
- `OrderRepository.markOrderReady(sellerId, date, orderId, ready)` + all impls.
- `commonMain` notification package: `ReminderType`,
  `BuyerNotificationPreferences`, `ReminderEvaluator` (pure), preference
  repository interface.
- Preference repository impl (DataStore) — Android first; `expect`/`actual` stub
  for iOS.
- String scaffolding in `strings.xml` (de) + `values-en/strings.xml`.
- **Tests:** `ReminderEvaluatorTest` (table-driven per type / status / time
  window); `markOrderReady` path in `FakeOrderRepository`.
- **Exit:** compiles on buy + sell + iOS, tests pass, nothing wired to UI.

### Phase 1 — Order-ready, in-app only (A1 + B1)

- Seller `OrderDetailScreen` + ViewModel: "Als gepackt markieren" action calling
  `markOrderReady`; toggle/undo; visible only for active orders near pickup day;
  shows "Abholbereit · HH:mm".
- Buyer order-detail / home hero: ready banner + badge fed by the existing
  `observeBuyerOrders` Flow; idempotency guard keyed on `orderId + readyAt`
  (persist a "seen" marker).
- Optional: `NotificationsScreen` feed row.
- **Exit:** seller marks packed → buyer sees the banner live while the app is
  open. Manual QA on both flavors.

### Phase 2 — Reminder engine, Android, self-state types (subset of C)

- `ReminderWorker` + self-rescheduling one-time chain; once-a-day
  `PeriodicWorkRequest` safety net; re-arm on app start and `BOOT_COMPLETED`.
- Notification channel + builder; `POST_NOTIFICATIONS` request flow (API 33+).
- Wire `PICKUP_REMINDER` and `EDIT_WINDOW_CLOSING` first (pure self-state, no
  "hasn't ordered" logic).
- `DisableReminderReceiver` + per-notification "Erinnerung ausschalten" action.
- Profile "Benachrichtigungen" section: master switch, per-type toggles,
  quiet-hours pickers, wired to the preference repository.
- Worker enforces quiet hours + `lastNotifiedCycle` dedupe.
- **Exit:** buyer with a `LOCKED` order gets a Thursday-morning pickup reminder;
  the off-action works; toggles persist and take effect next run.

### Phase 3 — Remaining reminder types + ready-via-worker (rest of C, Android)

- Add `ORDER_DEADLINE` (no active order for the upcoming cycle and/or non-empty
  `DRAFT` basket) and `UNCLAIMED_ORDER`.
- Fold `ORDER_READY` into the worker path so it also fires with the app closed
  on Android (bridge until Phase 4).
- Cadence caps / fatigue tuning.
- **Exit:** all five types firing on Android according to preferences.

### Phase 4 — Closed-app ready delivery: FCM + Cloud Function (B3)

- FCM deps in `shared` / `androidApp`; APNs setup for iOS.
- Token registration + refresh → `/buyers/{buyerId}/profile`; add
  `notifyWhenOrderReady` and mirror `BuyerNotificationPreferences` to the
  profile.
- Cloud Function on the `.../orders/{date}/{orderId}/readyAt` write → FCM
  notification+data message → deep link to the order.
- Notification-tap deep-link handling in both apps.
- **Exit:** buyer receives the ready push with the app killed, on Android + iOS.

### Phase 5 — iOS reminder parity (C5-local)

- `BGTaskScheduler` registration + `UNUserNotificationCenter` local scheduling,
  reusing the `commonMain` `ReminderEvaluator`.
- Profile settings section is already shared (`buyMain`) — verify wiring.
- **Exit:** reminders fire on iOS (best-effort background refresh).

### Phase 6 — Server reminder engine (C5-server) + email fallback (B4)

- Scheduled Cloud Function (pub-sub cron, Mon/Tue/Wed/Thu) porting the
  `ReminderEvaluator` conditions server-side; queries buyers by order state;
  sends FCM. Enables lapsed-user `ORDER_DEADLINE` reach; the client worker
  becomes a backup.
- Optional: Firebase "Trigger Email" extension for users who denied push.
- **Exit:** reminders no longer depend on the app ever running.

### Dependencies

```
Phase 0 ──┬── Phase 1
          ├── Phase 2 ── Phase 3
          └── Phase 5
Phase 4 (independent; needed before Phase 6)
Phase 6 depends on Phase 4
```

Phase 4 can be scheduled whenever push is prioritized project-wide (it also
unblocks other push features). Phases 5–6 follow iOS readiness.

---

## Edge Cases to Pin Down (any channel)

- **Idempotency:** do not re-notify on repeated Flow emissions. Key on
  `orderId + readyAt`. For reminders, key on `reminderType + cycle key`
  (`lastNotifiedCycle`).
- **Edit after packed:** decide whether editing an order clears `readyAt`.
- **Multiple open orders:** banner/notification must identify which order.
- **Offline buyer:** Flow catches up on reconnect; push queues via FCM. The
  reminder worker reads cached order state, so it still fires offline; refresh
  opportunistically.
- **Notification fatigue:** master kill switch, per-type opt-out, quiet hours,
  and at-most-once-per-cycle dedupe. Never send `ORDER_DEADLINE` /
  `EDIT_WINDOW_CLOSING` once the buyer has ordered / the window has closed.
- **Permission (Android 13+):** request `POST_NOTIFICATIONS`; degrade quietly if
  denied and reflect that state in the profile settings section.
- **Business-rule drift:** reminder timing is derived from
  `OrderDateUtils` / `order.canEdit()`, not hard-coded, so deadline changes flow
  through automatically.
- **Localization:** German first (`shared/src/commonMain/composeResources/values/strings.xml`),
  then `values-en/strings.xml`.
- **Time zone / pickup day:** readiness and reminders are anchored to pickup
  Thursday; surface timestamps in the buyer's local time.

---

## Affected Files (implementation sketch, for reference)

- `shared/src/commonMain/.../domain/model/Order.kt` — add `readyAt`.
- `shared/src/commonMain/.../domain/repository/OrderRepository.kt` — add
  `markOrderReady(...)`.
- `shared/src/commonMain/.../data/repository/GitLiveOrderRepository.kt` /
  `MockOrderRepository.kt` / `commonTest/.../FakeOrderRepository.kt` —
  implement it.
- Seller `OrderDetailScreen` (`shared/src/sellMain/...`) + its ViewModel —
  "Mark as packed" action.
- Buyer order-detail / home screens (`shared/src/buyMain/...`) — ready banner
  + badge, plus optional `NotificationsScreen` feed entry.
- `strings.xml` (de) + `values-en/strings.xml` — new strings.

### Reminder engine (section C)

- `shared/src/commonMain/.../domain/notification/` — `ReminderType`,
  `BuyerNotificationPreferences`, `ReminderEvaluator`, preference repository
  interface.
- `shared/src/commonTest/.../ReminderEvaluatorTest.kt` — table-driven cases per
  type / order status / time window.
- `shared/src/androidMain/.../notification/` — `ReminderWorker`, chain
  scheduler, `DisableReminderReceiver`, `BootReceiver`, notification builder.
- `shared/src/iosMain/.../notification/` — `BGTaskScheduler` +
  `UNUserNotificationCenter` scheduling.
- DataStore setup for preferences (local persistence).
- Buyer profile screen (`shared/src/buyMain/...`) + ViewModel — a
  "Benachrichtigungen" section: switch per `ReminderType`, quiet-hours pickers.
- `AndroidManifest` (buy flavor) — `POST_NOTIFICATIONS`,
  `RECEIVE_BOOT_COMPLETED`, receiver + worker registration.

### Server consolidation (phase 2)

- `functions/` project, `BuyerProfile` FCM token + `notifyWhenOrderReady` flag,
  Android/iOS FCM setup.
- Scheduled Cloud Function (Cloud Scheduler / pub-sub) reusing the
  `ReminderEvaluator` logic server-side for lapsed-user `ORDER_DEADLINE` reach.
