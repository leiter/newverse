# Seller-Published Buttons in the Buy App

**Status:** Implemented 2026-10-21; two bugs found on first device use the same day
and fixed — see **What went wrong first time**. The home-title announcement
(`TOPBAR_HOME_TITLE`) was added 2026-10-25 and has not yet run on a device.
**See also:** `quick-message-wishbox.md` — the same mechanism with an answer coming
back, which also adds the `active` and `persistence` flags to every published row.
**Scope:** `shared/src/commonMain` (model + repository), `buyMain` (top-bar
rendering, app state), `sellMain` (editor on the profile screen),
`firebase/database.rules.json` (+ `firebase/tests/seller-ui-actions.test.js`)

How a seller adds a button to the buy app's top bar without an app release, why it is
*not* carried on `seller_events`, and why the seller never gets to choose a URL scheme.

---

## What it does

The seller writes a row like this on their profile screen:

| Field | Example |
|---|---|
| `label` | `Wünsche` |
| `value` | `example.org/wuensche` |
| `location` | Top bar, profile screen |
| `audience` | All customers |
| `kind` | `OPEN_URL` |

Every buyer's app picks it up and draws a button on that screen's top bar. Tapping it
opens `https://example.org/wuensche` in the platform's URI handler. Removing the row
takes the button away again. No release, no flag, no code change.

## The node

```
seller_ui_actions/{sellerId}/{actionId}
  .read   auth != null                        # every signed-in user
  .write  auth != null && $sellerId === auth.uid
```

Its own top-level node, the same shape as `markets`, for the same two reasons: a thing
with validation rules of its own needs somewhere to hang them, and the alternative —
a field on `seller_profile` — would publish it to buyers through that node's `$field`
read map anyway, while coupling a button to every profile write.

Removal is a **delete, not a flag.** Unlike a market, which `Order.marketId` points at
long after it is retired, nothing references an action by id. There is no dangling
reference to protect and no reason to keep a withdrawn button readable.

**Since 2026-10-30 there is also an off switch**, which does not contradict that: the
reasoning above is about dangling references and still holds. What it missed is that a
seller sometimes wants the *wording* back. `active: false` parks a row without
deleting it — see `quick-message-wishbox.md`, which also adds the `persistence` flag
deciding whether a row goes when the buyer acts on it or only when they close it.

## Why not `seller_events`

This was the first instinct, and it cannot work. Three reasons, the first fatal:

1. **Buyers cannot read it.** `seller_events` is `".read": "auth.uid === $sellerId"`.
   Only the seller reads that log. Rendering from it in the buy app would mean
   inverting the channel's security model.
2. **It runs the other way.** Its write rule requires
   `newData.child('firebaseUserId').val() === auth.uid` *and* that the writer be an
   APPROVED buyer. The seller's only write is a delete. They cannot author an event.
3. **Wrong shape of fact.** It is an append-only log of things that *happened* —
   `GUEST_DATA_DELETED`, `ACCOUNT_LINKED`. A button is state that *is*. Answering
   "which buttons exist now" from an append-only log means replaying it and reading
   removals as tombstones, which is the drain-and-cursor machinery that
   `design/seller-buyer-sync-channel-design.md` describes and nobody has built.

`seller_events` is the buyer→seller channel. This is seller→buyer. They are different
directions with different readers, and one node cannot be both.

## The scheme is never the seller's to choose

`value` is stored **without a scheme** — `example.org/wuensche`, never
`https://example.org/wuensche` — and `ActionKind` supplies `https://` at render time.

This is the same rule as `SellerContact.toUri()`, and it is the most important thing
in the feature. A seller who could store a raw URI could make the buy app open an
arbitrary scheme from inside the app's own chrome: `intent://` into another installed
app, `market://`, or a credential-phishing page where the buyer's trust cue is the top
bar and not an address bar they cannot see. Storing only the remainder means the worst
a careless or compromised seller account can do is point at the wrong https page.

Enforced in three places, deliberately:

- **`ActionKind.normalize`** strips `https://` and `http://` on save *and* again on
  read, so a record written before the normalizer existed — or straight into the
  database by hand — still opens safely. `http://` is upgraded rather than preserved:
  this is the one place that decides the scheme, and it decides on the secure one.
- **The rules** reject a `value` containing `:` or beginning with `/`. The colon stops
  a stored scheme; the leading slash stops `//evil.test`, which would otherwise
  resolve against the scheme and reach another host.
- **`kind` and `location` must name something the app implements.** The rules check
  them against the literal enum names. A record whose `kind` or `location` this app
  version does not know is skipped on read, not guessed at: a button whose behaviour
  is unknown has no sensible default, and one in an unknown slot would have to land
  somewhere arbitrary. Same degradation as `ContactChannel.fromStored` and
  `storedOrderOrigin` — a row written by a newer app simply does not appear in an
  older one.

## Where buttons appear

`ActionLocation` names whole slots — `TOPBAR_HOME`, `TOPBAR_PROFILE`,
`TOPBAR_HOME_TITLE`, which is not a button at all, and the two message-box slots
`quick-message-wishbox.md` adds — rather than
carrying a free-form surface and screen pair. A typo in `"topbar,profileScreen"`
either renders nowhere and looks like data loss, or renders everywhere; an unknown
enum name is skipped and the row is simply not shown. The enum→route mapping lives in
`AppScaffold`, not in the model: a domain type has no business knowing `NavRoutes`.

In the top bar itself:

(`TOPBAR_HOME_TITLE` is reached through `BuyAppState.homeTitleAnnouncement`, never
through `forLocation`, so an announcement is never drawn as a button. The test *"an
announcement is not drawn as a button"* pins it. Since 2026-10-30 this is structural
rather than conventional: `forLocation` returns nothing for a slot where
`ActionLocation.isButtonSlot` is false, so a careless call site cannot draw a title or
a message box as a button nobody can tap.)

- **One action** is its own labelled `TextButton`, because the label is the point —
  "Wünsche" says what no icon could.
- **More than one** collapses into an overflow menu rather than eating the title.
- Published buttons are drawn **before** the app's fixed actions, so the icons a buyer
  has learned the position of do not shift when the seller adds a link.
- Capped at `MAX_PER_LOCATION` (3) and ordered by label. The order is the label's and
  not the database's: two buttons that swap places between launches read as a bug.

**The cap is enforced in the repository and the editor, not the rules** — RTDB cannot
count children. `FakeSellerUiActionRepository` reproduces it for the same reason, so a
test that expects a refusal gets one.

## Visibility: not gated on approval

Unlike the top-bar contact shortcut, which is hidden from unapproved buyers because a
one-tap call button handed to every anonymous installer is unvetted inbound noise for
a small business, published buttons are shown to **every signed-in user**.

A link the seller chose to publish is the thing they want seen, and a button that
appears only after approval reads as broken to a new install. The rule test
`"lets an unapproved guest read them too"` pins this as a decision rather than an
oversight.

## The home title: an announcement, not a button

`ActionLocation.TOPBAR_HOME_TITLE` is the one slot that is not a button. While the
seller has an announcement published and the buyer has not closed it, the seller's
text **replaces the home screen's title**, scrolling if it is longer than the bar, with
a close button on the right. Dismissing it gives the ordinary title back.

| Field | Example |
|---|---|
| `kind` | `ANNOUNCE` (never the seller's choice — see below) |
| `value` | `Heute ab 9: Bodenschätze aus der Region` |
| `label` | `Heute ab 9:` (derived; the seller is not asked) |
| `location` | Top bar, home screen (scrolling text) |

### The slot decides the kind

`ActionLocation` now carries the one `ActionKind` it renders, and
`SellerUiAction.readyToStore()` applies it on save — in the repository *and* in the
seller's editor, which is why it lives on the model rather than at either call site.
The seller never picks a kind; they pick a slot.

The two mismatched pairings are refused rather than rendered somehow. An `ANNOUNCE`
in a button slot has nothing to open, and an `OPEN_URL` in the title has no button to
be tapped; neither has a sensible default, so both are dropped by `isUsable`, skipped
by `parseActions`, and rejected by the rules. The same reasoning as an unknown `kind`,
applied to a *known* kind in the wrong place.

### An announcement is text, so the no-colon rule is lifted — for it alone

The `value` rule still bans `:` and a leading `/`, but only when `kind` is `OPEN_URL`:

```
"value": "… && (newData.parent().child('kind').val() === 'ANNOUNCE' || (!contains(':') && !beginsWith('/')))"
```

This is safe for exactly one reason, and the reason is worth being explicit about:
**an announcement has no path to the platform's URI handler.** The colon ban exists to
stop a stored scheme reaching `openUri`; `ActionKind.ANNOUNCE` has an empty `scheme`,
an identity `normalize`, and is rendered as `Text`, never handed to `LocalUriHandler`.
`OPEN_URL` is untouched, and the rule tests pin both halves — *"lets an announcement
carry a colon"* beside *"still refuses a colon in an OPEN_URL value"*.

Without this, "Heute ab 9: Bodenschätze" — an ordinary German sentence — would be
refused by the database, and running it through the URL normalizer would quietly eat
the leading words.

### Exactly one, picked deterministically

`TOPBAR_HOME_TITLE.maxActions` is 1, where the button slots keep 3. A title is one
thing: two cannot collapse into an overflow menu the way two buttons can, and
concatenating them would make one buyer's dismissal apply to a sentence the seller
never wrote. The cap is per slot now, enforced in the repository and the editor as
before, since RTDB still cannot count children.

Should two somehow exist — written by hand, or from two devices —
`announcementFor` takes the **lowest id**, not the lowest label. Two buyers must not
see different announcements, and one buyer must not see them alternate between
launches.

### `label` is derived, not asked for

The rules require a non-empty `label`, and an announcement has no button to label. So
`readyToStore()` fills it with the opening words of the text, cut at a word boundary —
asking a market trader to write a sentence *and* name it is one field too many. It is
what the row is recognised by in the seller's own list; buyers never see it.

## Cancelling an announcement

The buyer's close button is the point of the feature as much as the text is. It is
part of `SellerAnnouncementTitle` rather than a top-bar action, because it has to
disappear with the announcement, and an icon in the action slot would sit beyond the
app's own icons and read as one of them.

### The key is the id *and* a hash of the text

`SellerUiAction.dismissalKey` is `"$id:${value.stableHash()}"`.

Keyed on the **id alone**, a seller who corrects the wording of an announcement in
place would never reach a buyer who dismissed the previous one — the silent-stale-
configuration failure this feature has already shipped twice (see **What went wrong
first time**). Keyed on the **text alone**, two sellers' identical announcements would
share one dismissal. Together: re-reading the same words stays gone, new words come
back.

`stableHash` is a hand-rolled FNV-1a rather than `String.hashCode()`, which promises
nothing across platforms or app versions. The key is written to device storage, where
a changed algorithm would silently un-dismiss every announcement. It is not a security
property — a collision costs one buyer one unseen announcement.

### Stored per install, not per buyer

`AnnouncementDismissalStorage` is the one storage class in `data/config/` with **no**
`setActiveUserId`. `DemoOrderStorage` and `PendingInviteTokenStorage` give every userId
its own container because they hold the buyer's own data, which must not leak between
accounts on a shared phone. A dismissed announcement is not the buyer's data; it is
this screen's answer to "I have read this". Per-user scoping would bring the banner
back the moment a guest links their account, which is when they are least interested
in reading it again.

For the same reason `clearedForSignOut` leaves `dismissedAnnouncements` alone, which
is called out in that function's doc so it is not later read as an oversight.

Dismissal **does not expire**. A buyer who said they are done with a sentence is done
with it; the seller re-words the row when there is something new to say. The stored set
is capped at `MAX_DISMISSAL_KEYS` (50) and evicts oldest-first, because nothing ever
removes a key — the worst an evicted key can do is show a long-deleted announcement
once more, which cannot happen since the seller deleted the row.

### What the buyer sees fail soft

The dismissal is applied to state whether or not the write lands. The buyer asked for
the banner to go away, and it going away is the part they can see; a failed write means
it returns next launch, which is a much smaller surprise than a close button that does
nothing. Likewise `loadDismissedAnnouncements` swallows a read failure to "nothing
dismissed" — the cost is one announcement shown again, against failing startup over a
read of a UI preference.

## Rolling text

`SellerAnnouncementTitle` uses `Modifier.basicMarquee(iterations = Int.MAX_VALUE)`.

Chosen over a hand-rolled offset animation for one specific property: **it animates
only when the content actually overflows its box.** A short announcement therefore
sits still, and text that jitters when it already fits reads as a bug. The title also
lives in the home screen's `exitUntilCollapsedScrollBehavior` bar, so it has to
tolerate a shrinking container, which the marquee re-measures into.

Accessibility: a marquee animates a *visual* offset, so the node carries the whole
sentence as its `contentDescription` and is marked a `heading()`. A screen reader reads
the announcement once, in full, rather than whichever fragment is on screen.

**Not handled in this iteration:** reduced-motion. Compose Multiplatform has no common
API for the platform's "reduce motion" setting, and the marquee will animate for a
buyer who has asked it not to. Worth an expect/actual if anyone reports it.

Only the home screen's title is replaced. The basket and profile titles carry an item
count and a total the buyer is acting on right now, which an announcement must not hide.

## Who sees a button

`ActionAudience` narrows a button to part of the customer base:

| Value | Shown to |
|---|---|
| `EVERYONE` | every signed-in buyer (the default) |
| `DEMO_ONLY` | buyers the seller has not approved — still in demo mode |
| `APPROVED_ONLY` | buyers who can place a real order |

An onboarding link belongs in front of people who are not approved yet; this week's
list belongs in front of the customers who can actually order.

**Both narrowed audiences wait for the access status to be *known*.** `isApproved` and
`isDemo` are passed separately rather than derived from each other, because both are
false while the status is still loading — so a narrowed button is absent until the app
knows which side of the line this buyer is on, rather than appearing at launch and
vanishing a moment later. That is the flash `canContactSeller` documents and avoids,
and the test `a narrowed button stays hidden while the status is still loading` pins
it.

**The cap is applied after the audience filter**, so a button hidden from this buyer
does not use up one of the three places. A seller with three demo-only buttons would
otherwise leave an approved buyer with nothing.

`audience` is the one **optional** field on the record — deliberately not in the
rules' `hasChildren` list. Rows written before audiences existed were shown to
everyone and go on being shown to everyone: `ActionAudience.stored(null)` returns
`EVERYONE`, while an *unknown* name returns null and skips the row, the same
distinction `ActionKind` and `ActionLocation` do not need to make.

## Motion: where a thing came from

Two different events share the top bar, and they must not look the same.

A **route change** relabels the title. The buyer caused it, they already know what they
tapped, and a title that flew in would read as a second navigation stacked on the one
they just made. So it crossfades, and nothing moves.

A **remote item** — an announcement, a message box, a published button, the contact
shortcut — arrives on its own, from someone else, possibly while the buyer is looking at
the screen. Appearing instantly is how a tap lands on a thing that was not there when
the finger started moving. These drop in from above and lift back out, which says where
they came from and gives the eye something to follow. Buttons among the fixed icons
scale from 0.8 instead of sliding, because a slide would shove the app's own affordances
sideways and the whole reason published buttons are drawn inboard is that a seller must
never move an icon the buyer has learned the position of.

Both are short — 220ms in, 160ms out. The exit is the quicker of the two on purpose: the
slot has to be free before the next thing can use it, and an announcement that lingers
delays the title the buyer is waiting for.

The spec lives in `TopBarMotion.kt`, with `TopBarTitleSlot` as the key the title
animates between. That key carries what the slot needs to draw itself and nothing that
changes more often than the slot does — not the basket's live count, not the draft in a
message box. Keying on those would restart the transition on every keystroke and every
tap of a plus button; the bodies read them straight from state instead, which recomposes
without animating.

Each animated body also reads the state it was handed rather than the surrounding
variables, so the outgoing half of a transition keeps drawing the thing being taken away
instead of flipping to its replacement halfway out.

## What this is not

- **Not a way to ship UI.** `kind` is an enum of behaviours the buy app already
  implements. Adding `CALL` or `OPEN_SCREEN` later is a new entry and a new render
  branch — a release — not a change to what sellers may store. The seller configures;
  they do not program.
- **Not localized.** `label` is the seller's own words, so a German label reaches an
  English-locale buyer as written. Accepted deliberately: with one seller it is the
  right trade, and the alternative is asking a market trader for translations.

## What went wrong first time

Both bugs surfaced on the first real use — seller publishes a button, buyer app shows
nothing — and neither was caught by the tests as first written.

**1. The buy app read the actions once, at startup.** `getActions` ran in
`resumeInitializationAfterAuth` and on `performConnection`, and nowhere else. A seller
publishing a button while buyers have the app open changed nothing until the app was
killed. Configuration that needs a restart is not configuration.

Fixed by watching the node in `startObservingAccessStatus`, beside the access status
and allowed markets, which already exist to carry exactly this kind of
seller-side change into an open app. The one-shot reads are kept so the first frame
after launch is not empty while the subscription warms up.

**2. The editor wrote a row the moment it became complete, and dropped it.** Each row
handed itself to the repository as soon as it had both a label and a target, then
removed itself from the editor while the live flow round-tripped. Typing a label one
character at a time meant the row completed on the *first* keystroke and vanished
mid-word. The first button published on dev ended up labelled `g`.

Fixed by holding edits locally and writing on an explicit save, which is what
`SellerContactsCard` beside it already does. Nothing is written until the seller
presses the button.

**3. A refused write looked exactly like a saved one** — see the section above on
deploying a field. And **4.**, found by the test written for 3: `_uiActions` was
declared below the `init` block that starts its own subscription, so a repository fast
enough to emit during construction hit a null flow. Firebase is never that fast, which
is why it ran on a device and crashed against a fake; the flows now sit above `init`.

The lesson for the tests: both the model tests and the rule tests were about what a
seller *may store*, and they were thorough about it. Neither asked whether a published
button ever reaches a buyer who is already looking at the screen. There are now
regression tests for both directions of that — a button appearing and disappearing
without a restart — and they fail if the subscription is removed.

## Deploying a field is two steps, and the rules are the first

`$actionId` ends in `"$other": { ".validate": false }`, which rejects any field the
ruleset does not name. That is the right strictness and it has a consequence worth
stating plainly: **adding a field to `SellerUiAction` breaks every write until the
rules are redeployed.** Not the new field — the *whole row*.

This bit once, with `audience`. The app wrote rows carrying it against a ruleset
deployed before it existed, every write was denied, and the editor showed nothing. The
order is: rules first, or app and rules together, never app first.

The refusal is now visible. `SellerProfileViewModel.uiActionError` carries it to the
editor, because the repository logging a warning while the UI looked successful is
what made this expensive to find.

## Known limits

- **One seller makes the blast radius small, and that will not always be true.** The
  seller account is a channel into every buyer's app. The enum-constrained shape is
  what keeps that channel narrow; it is the reason not to "just store a URL" when the
  next convenience argues for it.
- **`MAX_PER_LOCATION` is not enforceable in rules.** A client writing directly to the
  database could exceed it. The render-side `take()` means the buy app still shows at
  most three, so the failure mode is an ignored row rather than a broken top bar.
- **Adding a slot** is an enum entry, a rules literal, a route branch in
  `AppScaffold`, and a display name — in that order, or the editor will offer a slot
  the renderer drops. Done twice since, for the message box.

## Files

| Where | What |
|---|---|
| `domain/model/SellerUiAction.kt` | The record, `ActionKind`, `ActionLocation`, `forLocation` |
| `domain/repository/SellerUiActionRepository.kt` | The interface |
| `data/repository/GitLiveSellerUiActionRepository.kt` | RTDB implementation, normalization, the cap |
| `buyMain/.../BuyAppState.kt` | `sellerUiActions`, `topBarActionsFor` |
| `buyMain/.../AppScaffold.kt` | Rendering and the enum→route mapping |
| `sellMain/.../UiActionListEditor.kt` | The seller's editor |
| `sellMain/.../SellerProfileViewModel.kt` | `uiActions`, `saveUiAction`, `deleteUiAction` |
| `firebase/tests/seller-ui-actions.test.js` | 22 rule tests, mostly about `value` |
| `commonTest/.../SellerUiActionTest.kt` | 22 model tests: schemes, slots, audiences |
| `buyMain/.../BuyAppViewModelAccess.kt` | The live subscription, beside access status |
| `testBuy/.../BuyAppViewModelUiActionsTest.kt` | 10 tests: the buttons reach the state, live, per audience |
