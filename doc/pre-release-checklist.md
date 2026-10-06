# Pre-Release Checklist

**Written:** 2026-10-06
**Scope:** everything that must happen between `main` as it stands and a release build
reaching a real user, on either flavor.

This is the gate list. It does not restate the per-item detail in `doc/TODO.md` — it
says what blocks a release and in what order. Where TODO.md already has the detail,
this file points at it.

## Why this file exists

`main` now contains the buyer identity rekey (`c173ab2` and follow-ups). That code
**requires** database rules that are deployed only to the development project. A
release build cut from `main` today talks to `bodenschaetze-a988e`, where none of those
rules exist, and would be broadly broken with most failures swallowed rather than
surfaced. Merging the work did not make it releasable.

---

## Gate 1 — Rules and data on the release project

Nothing else matters until this is settled. **Six rules commits have never been
deployed anywhere but dev:**

```
c173ab2  Identify buyers by their auth uid
789dac4  Key access records on the auth uid, narrow the orders read
ca72a3d  Add redeemable invite-token rules
0031400  Fix stale buyer display name
56fe071  Store sales add-only, rules that forbid changing the books
ca1fa44  Add database rules for seller-only article data
```

Against the release project's current (pre-rekey) rules, a `main` build would have:
QR redemption denied (no `invite_tokens` rules), uid-keyed `buyer_access_status` writes
denied, `seller_articles` writes denied **including article deletion**, and sale
booking denied.

- [ ] **Export `bodenschaetze-a988e` before anything else.** The "everything is test
      and developer data" clearance in `doc/seller-buyer-identity-migration-plan.md`
      was confirmed for `fire-one-58ddc` **only**. A shallow read on 2026-10-06 shows
      the release project holds `articles`, `buyer_profile`, **`orders`** and
      `seller_profile`. Its contents have not been inventoried.
- [ ] **Inventory that export** the way the dev one was: which nodes exist, whether
      `buyer_profile` is already uid-keyed (key == id), whether any `orders` or
      `sales` records are real rather than test. The dev wipe turned out to be
      unnecessary; do not assume the same here, in either direction.
- [ ] **Decide the wipe question for this project on that evidence.** Real orders or
      booked sales change it from a cleanup into data loss — and `sales` /
      `sale_index` are add-only by rule precisely because they are tax records.
- [ ] **Deploy rules and ship the matching app build together.** A deployed ruleset
      that disagrees with the installed app rejects writes outright
      (`firebase/tests/README.md`). Deploying rules alone breaks every install.
- [ ] **Establish who has an existing install.** `version.properties` has
      `VERSION_CODE_BUY=38` / `VERSION_CODE_SELL=23` and uploads have been done by
      hand, so installs exist. Any install predating the rekey stops working when the
      rules land: order history reads empty (the `orders` read narrowed to per-order),
      and access writes are denied. There is no compatibility path — this was a
      deliberate decision, not an oversight.

## Gate 2 — Device verification that has never run against deployed rules

The dev device pass on 2026-10-05 proved the QR invite flow end to end (token minted
→ scanned → `redeemedBy` matched → APPROVED self-write accepted by the rules). These
paths were **not** covered and have only ever run against unit tests and the emulator:

- [ ] **Place an order and open order history.** This is the one that needed a code
      rewrite: `observeBuyerOrders` used to subscribe to the seller's whole order tree
      and filter client-side; the narrowed read forced it into one listener per order
      merged with `combine`.
- [ ] **Confirm a pickup and book the sale.** The add-only `sales` / `sale_index`
      rules went live in the same deploy and have never been hit from a device. Closes
      the walk-in device check already open in TODO.md.
- [ ] **Seller sees the buyer in the customer list** (`observeBuyers`).
- [ ] **Block a buyer**, and confirm a blocked buyer cannot order.
- [ ] **Pickup-day display.** `getOrderWindowStatus` now holds `DEADLINE_PASSED`
      through the whole pickup day instead of flipping to `PICKUP_PASSED` at 00:00, so
      a different branch of `BasketScreen.kt` runs on market day.
- [ ] **The pickup date picker now offers 5 dates, not 2** (B1). Buyers can order up
      to five weeks out. Nothing downstream — seller views, Abrechnung, the picker's
      own layout — has ever seen that.

## Gate 3 — Decisions to make before strangers use it

- [ ] **Any signed-in user can write an order to any seller.** The `orders` write rule
      only excludes `BLOCKED`; a PENDING buyer, or one with no access record at all,
      passes. `connectWithToken` also calls `performConnection` before redemption
      succeeds, so an expired or already-redeemed QR link still yields a connected,
      order-capable buyer. This predates the rekey (the old rule checked
      `blockedClientIds` the same way) and the rekey did not change it. Decide whether
      approval should gate ordering before the app is public.
- [ ] **Buyer email is embedded in every order** —
      `doc/buyer-profile-data-safety.md` §1. The narrowed `orders` read removed the
      cross-buyer exposure; the field itself is still copied into each record.
- [ ] **No `.validate` whitelist on `/orders`** — same doc, §4. Phase 4 of the
      migration plan was going to add it while the rules file was open.
- [ ] **Tax advisor questions** (TODO.md, Bookkeeping & Tax). Regular VAT or
      Kleinunternehmer §19 — the CSV export assumes regular VAT and its VAT columns
      would be wrong under §19. Whether the records satisfy GoBD / KassenSichV at all,
      given there is no TSE and the project owner can still edit data in the console.
      These gate the *seller* flavor being used for real books, not the build.

## Gate 4 — Release mechanics

Detail is in TODO.md under **High Priority: Release**; the blockers in short:

- [ ] **Android:** check the sell `versionCode` against Play before the next sell
      upload — the counter restarts at 23. Tag each hand upload
      (`fastlane tag_release flavor:buy|sell`); no Android build has a tag yet.
- [ ] **fastlane is not usable yet:** `Appfile` still has the placeholder
      `json_key_file`, no service-account key exists, fastlane itself is not installed
      (no `Gemfile.lock`), the key path is not in `.gitignore` **and the repo is
      public**, and `fastlane/metadata/android` holds only the buyer listing while
      `deploy_sell` does not skip metadata — so it would publish buyer text on the
      sell listing.
- [ ] **iOS is not submittable:** `CFBundleVersion` / `CURRENT_PROJECT_VERSION` wiring,
      screenshots, real store metadata and the two `subtitle.txt` files. The app has
      only ever been compiled, never run on a device. Known code issues: iOS
      `NetworkConnectivity` always reports online, `HeroProductCard` uses a fixed
      width. Also: `09160de` changed `iosMain/di/IosDomainModule.kt` and that has
      never been compiled on any machine — the dev box is Linux.
- [ ] **Web is not maintained** (see `CLAUDE.md`, Current Status). Make sure no release
      process tries to build `:webApp`; `compileKotlinJs` currently fails.

## Gate 5 — Quality baseline

- [ ] **6 failing unit tests**, all stale expectations rather than live bugs
      (diagnosed 2026-10-06, see TODO.md → Known failing unit tests). Fix or delete
      them so a real regression is visible: `calculateEditDeadline throws for
      non-pickup day` needs a decision on whether the commented-out `require`
      belongs; the 4 `BuyAppViewModelTest` and 1 `SellAppViewModelTest` failures
      assert a startup/navigation design that no longer exists.
- [ ] **User-visible localization gaps** in a German-primary app: G1 (46 untranslated
      strings — German text in the English UI) and G2/G3 (hardcoded English error
      messages in the German app). TODO.md, Suspected Bugs.
- [ ] **Remaining manual-test-plan items**, E2 first (critical, unconfirmed: a
      time-zone change can orphan a placed order), then C1, C2, C3, E1, E3.

---

## Order of operations

1. Gate 5 and the Gate 2 items that need no deploy — all free, all on dev.
2. Gate 1 export and inventory. **Read-only. Do this before deciding anything.**
3. Gate 3 decisions, since two of them change the rules file you are about to deploy.
4. Gate 1 deploy + matching build, in one window, apps closed.
5. Gate 2 on the release project.
6. Gate 4, flavor by flavor.

Keep `bodenschaetze-a988e` untouched until step 2 is done and its contents are known.
