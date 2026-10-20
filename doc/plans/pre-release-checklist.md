# Pre-Release Checklist

**Written:** 2026-10-06
**Scope:** everything that must happen between `main` as it stands and a release build
reaching a real user, on either flavor.

This is the gate list. It does not restate the per-item detail in `doc/plans/TODO.md` — it
says what blocks a release and in what order. Where TODO.md already has the detail,
this file points at it.

## Why this file exists

`main` now contains the buyer identity rekey (`c173ab2` and follow-ups). That code
**requires** database rules that are deployed only to the development project. A
release build cut from `main` today talks to `bodenschaetze-a988e`, where none of those
rules exist, and would be broadly broken with most failures swallowed rather than
surfaced. Merging the work did not make it releasable.

*Update 2026-10-08:* the rules are now deployed to `bodenschaetze-a988e` and the data
migrated (Gate 1). What remains of that gap is shipping the builds that match.

---

## Gate 1 — Rules and data on the release project

**Status 2026-10-10: rules deployed, data migrated; only the matching build remains.**
`firebase/database.rules.json` is published to `bodenschaetze-a988e` as of this date,
so the six rules commits below are live there, and the articles are
imported under the new seller uid (`doc/archive/production-db-migration.md`). Legacy buyer
data was written off rather than migrated.

*Re-deployed 2026-10-10.* The 2026-10-08 deploy published the file as of `c173ab2`.
The stock ledger (`79035d1`) added `/stock` and `/stock_movements` afterwards and was
merged to `main` on 2026-10-10, so between those dates the deployed ruleset had no
stock nodes at all: a release build cut from `main` would have had every write from
the stock screen (`c980946`) denied, with the failures swallowed rather than surfaced.
`firebase deploy --only database --project prod` closes that. `seller_articles` was
unchanged across the window, so the hand-published version from 2026-09-21 was
correct.

The lesson is the one Gate 1 already states and this still managed to miss: a rules
commit is not live because it is on `main`. The 136 tests in `firebase/tests` pass
against the file, never against what is deployed.

*Re-deployed 2026-10-15 to **both** projects*, adding the two `origin` validators from
`e2e0f7d`. Verified this time by reading `/.settings/rules.json` back from each live
database and diffing it against `HEAD` rather than trusting the deploy output: both now
report 87 of 87 rules, nothing missing, nothing extra, nothing differing.

That read-back immediately earned itself. **Dev was 26 rules behind, not 2** — it was
missing the entire stock ledger (`/stock`, `/stock_movements`, 24 rules from `79035d1`)
as well as the two new ones. The 2026-10-10 remedy above deployed only to `prod`, so for
five days `fire-one-58ddc` — the project every debug build talks to — had no stock rules
at all and was *behind* production, which inverts the assumption everyone makes about
dev. Both are level now.

So the lesson above gains a second half: a rules commit is not live because it is on
`main`, **and a deploy is not verified because the CLI said "released successfully"** —
it says that whichever project you aimed it at. Read the rules back:

```sh
curl -s -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  "https://<db>.firebaseio.com/.settings/rules.json"
```

(dev is `fire-one-58ddc.firebaseio.com`, prod is
`bodenschaetze-a988e-default-rtdb.firebaseio.com`; the URLs differ in shape because the
databases are in different regions, and `firebase database:settings:get` cannot read
rules.)

These six rules commits had, until then, been deployed only to dev:

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

- [x] **Export `bodenschaetze-a988e` before anything else.** Done 2026-10-08. The "everything is test
      and developer data" clearance in `doc/plans/seller-buyer-identity-migration-plan.md`
      was confirmed for `fire-one-58ddc` **only**. A shallow read on 2026-10-06 shows
      the release project holds `articles`, `buyer_profile`, **`orders`** and
      `seller_profile`.
- [x] **Inventory that export** the way the dev one was: which nodes exist, whether
      `buyer_profile` is already uid-keyed (key == id), whether any `orders` or
      `sales` records are real rather than test. The dev wipe turned out to be
      unnecessary; do not assume the same here, in either direction.
      Done: `doc/archive/production-db-migration.md` — one seller, 104 articles, 20 buyers,
      48 orders (2021-03 → 2025-01), no `sales`.
- [x] **Decide the wipe question for this project on that evidence.** Real orders or
      booked sales change it from a cleanup into data loss — and `sales` /
      `sale_index` are add-only by rule precisely because they are tax records.
      Decided: articles and seller profile migrated; the 20 buyer profiles are stale
      (they point at the old seller id) and were written off along with their order
      history. The `buyerId` backfill (B1) and access seeding (B2) were dropped.
- [ ] **Deploy rules and ship the matching app build together.** A deployed ruleset
      that disagrees with the installed app rejects writes outright
      (`firebase/tests/README.md`). Deploying rules alone breaks every install.
      **Half done:** rules deployed 2026-10-08; the matching builds (iOS TestFlight,
      Android buy/sell) have not shipped yet, so every existing install is broken
      until they do.
- [x] **Establish who has an existing install.** `version.properties` has
      `VERSION_CODE_BUY=38` / `VERSION_CODE_SELL=23` and uploads have been done by
      hand, so installs exist. Any install predating the rekey stops working when the
      rules land: order history reads empty (the `orders` read narrowed to per-order),
      and access writes are denied. There is no compatibility path — this was a
      deliberate decision, not an oversight.
      Settled by writing off the legacy buyers: their installs break and that is
      accepted.

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

### Backup and auth persistence — buy flavor (added 2026-10-15)

`android:allowBackup="false"` plus `androidApp/src/main/res/xml/data_extraction_rules.xml`
landed on 2026-10-15 to stop Android's restore-at-install from destroying the signed-in
user (TODO.md → *Backup restore silently destroyed the signed-in user*). The symptom was a
buyer silently losing their identity, their seller approval and their order history on
every cold start, with no error anywhere — so this is a release blocker on the buy flavor,
and the behaviour below has **not** been proven on a release build.

Already verified, do not redo:

- Debug buy build, on a Pixel 7a: uninstall + install logs `restoreAtInstall` but then only
  `initiateOneRestore packageName=@pm@` — no `RestoreDescription{com.together.buy : STREAM}`
  and no `Clearing app data preparatory to full restore`. Guest sign-in then survived two
  cold starts with the same uid.
- `bmgr backupnow com.together.buy` → `Backup is not allowed`, so backups are no longer
  created, not merely ignored on restore.
- Packaged **release** APK, after R8 and resource shrinking: `allowBackup=false` present and
  `dataExtractionRules` resolves to a rules file whose `<cloud-backup>` and
  `<device-transfer>` sections are both intact. Build-pipeline failure modes are ruled out;
  runtime behaviour is not.

Still to do, on a release build:

- [ ] **Device-to-device transfer.** The `<device-transfer>` half is the production scenario
      that motivated the whole change and is the one carrying the most reasoning and the
      least evidence — `allowBackup="false"` alone does **not** cover it on API 31+. Needs
      two phones and Google's real migration flow: set up phone B from phone A with the buy
      app installed and signed in, then confirm phone B starts at the login screen rather
      than in a half-restored state, and that signing in there yields a working session.
- [ ] **Uninstall + reinstall of the release APK** on a device, repeating the debug check
      above: no `Clearing app data preparatory to full restore` in the log, and a session
      that survives two cold starts afterwards.
- [ ] **`bmgr backupnow` against the release build** returns `Backup is not allowed`.
- [ ] **Nothing depended on restored data.** Reasoned but never exercised: profile, orders,
      favourites and access status are server-side and keyed by the account, so only the
      local cache is lost. Confirm the one user-visible casualty is acceptable — the order
      reminder settings reset to default on a new device — and that the connected seller id
      and demo orders rebuilding themselves breaks nothing.

Related decision: turning backup off removes one way a guest's session could have
survived a device change, which sharpens the open question in Gate 3 about guest data
being unrecoverable. A credentialed buyer is unaffected — their uid is stable per provider
identity, so signing in again restores everything server-side.

Note for whoever runs these: a device already in the poisoned state is cured by
`adb shell pm clear <package>`, **not** by reinstalling — the reinstall is what triggers the
restore. And before treating a report of "the app crashed" as a crash, check
`adb logcat -b events` for `am_crash`; this entire episode had none.

## Gate 3 — Decisions to make before strangers use it

- [ ] **Any signed-in user can write an order to any seller.** The `orders` write rule
      only excludes `BLOCKED`; a PENDING buyer, or one with no access record at all,
      passes. `connectWithToken` also calls `performConnection` before redemption
      succeeds, so an expired or already-redeemed QR link still yields a connected,
      order-capable buyer. This predates the rekey (the old rule checked
      `blockedClientIds` the same way) and the rekey did not change it. Decide whether
      approval should gate ordering before the app is public.
- [ ] **Buyer email is embedded in every order** —
      `doc/reports/buyer-profile-data-safety.md` §1. The narrowed `orders` read removed the
      cross-buyer exposure; the field itself is still copied into each record.
- [ ] **No `.validate` whitelist on `/orders`** — same doc, §4. Phase 4 of the
      migration plan was going to add it while the rules file was open.
- [ ] **A guest's data is unrecoverable, and nothing nudges them to upgrade**
      (added 2026-10-15). An anonymous account's only credential is the persisted
      session itself — no password, no provider. Lose it and the uid is unreachable
      forever: the account still holds the buyer's profile, their orders and the
      seller's approval, but nobody can ever sign into it again. The session is lost on
      clear-storage, on uninstall/reinstall, and on moving to a new phone (more so now
      that backup is off, see Gate 2). A Google or email buyer is unaffected by all
      three — the uid is stable per provider identity, so signing in again restores
      `/buyer_profile/{uid}`, `/buyer_access_status/{sellerId}/{uid}` and their orders;
      only the local cache goes.
      The upgrade path exists and is correct: `linkWithEmail` / `linkWithGoogle` use
      `linkWithCredential`, which **keeps the uid**, so orders and approval survive, and
      `BuyAppViewModelAuth.kt:514-520` migrates per-user local storage as a backstop.
      But it only ever opens from a user action (`BuyAccountAction.ShowLinkAccountDialog`
      on the profile screen). Nothing prompts at the moment the stakes change — in
      particular right after the seller approves the buyer, which is when a guest first
      holds something irreplaceable. The 2026-10-15 incident is a worked example of what
      that loss looks like: seven orphaned guest identities on one device.
      **Decide:** prompt a guest to link on approval (and/or on first order), or accept
      guest data as disposable and say so in the UI. The first is a dialog that already
      exists and a trigger; the second is wording.
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

*As it actually went (2026-10-08):* steps 2 and 4's rules deploy happened ahead of
step 3, so the two Gate 3 items that would change the rules file (order write gating,
`/orders` `.validate`) now need a second rules deploy. The matching builds from step 4
are still outstanding.
