# Newverse Documentation

**Reorganized 2026-10-20.** Docs are filed by *lifecycle*, not by topic, so a
document cannot quietly pass itself off as current:

| Folder | Means | Trust it? |
|--------|-------|-----------|
| [`guides/`](#guides) | How the app works **now** | Yes — verified against the code |
| [`design/`](#design) | Proposed, **not built** | As a proposal only |
| [`plans/`](#plans) | Work **in flight** | Yes, for what to do next |
| [`reports/`](#reports) | **Dated snapshots** | As of their date, no later |
| [`archive/`](#archive) | Superseded or **done** | As history |

Every file carries a status line under its title. If a guide and the code disagree,
**the code wins** — and the guide is a bug worth fixing in the same change.

> Not to be confused with `docs/` at the repo root, which is the GitHub Pages site
> (privacy, terms, support in DE/EN) — store legal pages, not developer docs.

---

## guides/

Current truth. Verified against `shared/src` on 2026-10-20 unless noted.

| Doc | Covers |
|-----|--------|
| [architecture.md](guides/architecture.md) | MVVM, state hoisting, actions, `AsyncState`/`AuthAwareState`/`FormState`, `BaseAppViewModel`, `ScreenStateRegistry`, the 14 repositories |
| [navigation.md](guides/navigation.md) | All routes, per-flavor nav graphs, flavor filtering, adding a screen |
| [auth-and-accounts.md](guides/auth-and-accounts.md) | Startup auth, `AuthFlowCoordinator`, **guest accounts are opt-in now**, linking, deletion |
| [ordering-business-rules.md](guides/ordering-business-rules.md) | Per-market pickup, deadlines, next-open-slot rule, order editing |
| [build-flavors.md](guides/build-flavors.md) | applicationIds, BuildKonfig, **and the `combined` build** |
| [testing.md](guides/testing.md) | Source sets, 984 tests, the 13 fakes, instrumentation, rules tests |
| [rtdb-test-fixtures.md](guides/rtdb-test-fixtures.md) | The three importable RTDB snapshots in `firebase/fixtures/`, their identities, and the 36 test cases they are built for |
| [localization.md](guides/localization.md) | Compose Resources, **positional `%1$s` only** |
| [theme.md](guides/theme.md) | Material 3 theme, the **Bodenschätze palette** from the flyer, the Nachterde dark scheme, why dark mode can look like light mode |
| [accessibility.md](guides/accessibility.md) | TalkBack/VoiceOver principles; coverage map is incomplete |
| [image-loading.md](guides/image-loading.md) | Coil 3.3.0, caching, platform support |
| [market-pricing-and-access-design.md](guides/market-pricing-and-access-design.md) | Markets as an entity, per-market pricing, eligibility, slots, closures (implemented 2026-10-19) |
| [seller-contact-design.md](guides/seller-contact-design.md) | Seller contact channels, market scope, visibility (implemented 2026-10-19) |
| [seller-ui-actions.md](guides/seller-ui-actions.md) | Seller-published top-bar buttons: the `seller_ui_actions` node, why not `seller_events`, why the seller never picks a scheme (implemented 2026-10-21) |
| [stock-driven-availability-design.md](guides/stock-driven-availability-design.md) | Committed demand, refill list, derived `available`, the placement gate, the refill reminder, the scarcity band (implemented 2026-10-20) |
| [demo-order-provenance.md](guides/demo-order-provenance.md) | `Order.origin`, path as the traffic boundary, demo→production transition |
| [demo-mode-and-profile-indication.md](guides/demo-mode-and-profile-indication.md) | Telling the buyer "finish this" vs. "not live yet" |
| [bnn-parser-guide.md](guides/bnn-parser-guide.md) | BNN supplier catalog format and parser |
| [google-signin-guide.md](guides/google-signin-guide.md) | Web Client ID, SHA-1, troubleshooting |
| [google-play-api-setup.md](guides/google-play-api-setup.md) | Fastlane Supply service account |
| [ios-setup.md](guides/ios-setup.md) | Xcode/CocoaPods build setup |
| [ios-back-navigation.md](guides/ios-back-navigation.md) | Edge-swipe back, what not to use in `commonMain` |

The **Firebase node layout and access rules** are not duplicated here: `CLAUDE.md`
has the node map and `firebase/database.rules.json` is the enforcement, with tests
in `firebase/tests/`.

## design/

Proposed. Nothing in here is built unless its banner says which part is.

| Doc | State |
|-----|-------|
| [order-ready-notification-design.md](design/order-ready-notification-design.md) | Options, no decision taken |
| [seller-buyer-sync-channel-design.md](design/seller-buyer-sync-channel-design.md) | Event direction built, eligibility blocker resolved; `PROFILE_CHANGED` event, producer and seller-side drain still missing |
| [cas-scale-integration.md](design/cas-scale-integration.md) | Research only — no hardware, no code |

## plans/

| Doc | State |
|-----|-------|
| [TODO.md](plans/TODO.md) | Feature status and roadmap — the per-item detail |
| [pre-release-checklist.md](plans/pre-release-checklist.md) | The release gate list, in order |
| [build-identity-and-firebase-wiring.md](plans/build-identity-and-firebase-wiring.md) | Which backend a build talks to — decided, not yet done |
| [agp-9-migration-prep.md](plans/agp-9-migration-prep.md) | Groundwork; do not start during a launch |
| [apple-store-release-plan.md](plans/apple-store-release-plan.md) | Store assets; largely unexecuted for iOS |
| [seller-buyer-identity-migration-plan.md](plans/seller-buyer-identity-migration-plan.md) | Phases 1, 2b and 2c done; Phase 3 and the tail of 4 open |

## reports/

Point-in-time findings. Each is accurate as of its date and no later.

| Doc | Date |
|-----|------|
| [manual-test-plan.md](reports/manual-test-plan.md) | 2026-09-10, vs `501625d` |
| [buyer-profile-data-safety.md](reports/buyer-profile-data-safety.md) | 2026-09-28, 5 findings |
| [bookkeeping-backfill.md](reports/bookkeeping-backfill.md) | 2026-10-10, completed |
| [apple-store-release-status.md](reports/apple-store-release-status.md) | 2026-08-05 |
| [feature-report.md](reports/feature-report.md) | 2026-03-25 — badly out of date |

## archive/

Superseded or finished. Kept for the reasoning and the history, each with a banner
saying what replaced it. Nothing here should be used to decide current behaviour.

| Doc | Why archived | Replaced by |
|-----|--------------|-------------|
| [app-startup-authentication-flow.md](archive/app-startup-authentication-flow.md) | Describes auto guest sign-in and a `UnifiedAppViewModel` that no longer exist | [guides/auth-and-accounts.md](guides/auth-and-accounts.md) |
| [forced-login-implementation.md](archive/forced-login-implementation.md) | Behaviour holds; every symbol named is gone | [guides/auth-and-accounts.md](guides/auth-and-accounts.md) |
| [draft-order-behavior.md](archive/draft-order-behavior.md) | Thursday-only, five-date picker | [guides/ordering-business-rules.md](guides/ordering-business-rules.md) |
| [order_editing_workflow.md](archive/order_editing_workflow.md) | Folded in; referenced a deleted file | [guides/ordering-business-rules.md](guides/ordering-business-rules.md) § Order Editing |
| [account-clearing-plan.md](archive/account-clearing-plan.md) | Implemented | `BuyAppViewModelAuth.kt:656` |
| [stock-keeping-design.md](archive/stock-keeping-design.md) | Implemented end to end | `StockRepository`, `sell/stock` |
| [seller-product-creation-implementation.md](archive/seller-product-creation-implementation.md) | Done; predates `ArticleNodes` | `CreateProductViewModel` |
| [buy-viewmodel-refactoring.md](archive/buy-viewmodel-refactoring.md) | Completion report, 2025-12-19 | — |
| [production-db-migration.md](archive/production-db-migration.md) | Applied; a root import is no longer safe | — |
| [web-implementation-audit.md](archive/web-implementation-audit.md) | Web is not maintained; target does not compile | — |

Plus one raw chat transcript from the flavor-distinction exploration, kept as a
`.txt` because it is a conversation, not documentation.

---

## What was stale, and what it now says

The reorganization turned up these contradictions between the docs and the code:

| Doc claimed | Actually |
|-------------|----------|
| Buy flavor auto-signs-in as a guest when no session exists | A guest account is created **only** when the buyer taps "Continue as Guest" |
| `UnifiedAppViewModel`, `BuildFlavor.kt`, `BuildFlavor.allowsGuestAccess` | None of them exist any more |
| Two flavors, buy and sell | Three configurations — a `combined` build sets **both** booleans to `false` |
| 234 unit tests, 6 fakes | 984 `@Test` across 68 files, 13 fakes |
| One `commonMain/NavGraph.kt`; 11 routes | Nav graph and scaffold are **per flavor**; ~26 routes |
| Pickup is Thursday; picker offers five dates | Pickup is per market; next open slot only, and it is implemented |
| Stock keeping "not yet implemented" | Implemented end to end, rules tests included |
| Account clearing is a plan | Implemented |
| Coil 3.0.4, Ktor 3.0.1, Navigation 2.8.0-alpha10 | Coil 3.3.0, Ktor 3.3.0, Navigation 2.9.1 |
| `FabGreen #0A6308`, `Orange #FA9C4D`, `ErrorRed #D51D47` | `#2E7D32`, `#FF6F00`, `#D32F2F` |
| Bare `%s` / `%d` are fine in strings | Only positional `%1$s` is substituted; bare reaches the user literally |
| `BuyerStoriesRunner` debug walkthroughs | Removed |

One error is in **`CLAUDE.md`, not here**: it lists the packages as
`com.together.newverse.buy` / `.sell`. The real applicationIds are
`com.together.buy` and `com.together.sell`.
