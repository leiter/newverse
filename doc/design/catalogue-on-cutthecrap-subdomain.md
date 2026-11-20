# The Sortiment on bodenschaetze.cutthecrap.link

**Written:** 2026-11-20
**Status:** DESIGN — nothing built, nothing deployed, no rule changed.
**Decided:** scope, hosting, both subdomain names, the data path, the refresh cadence,
and where the pricing rule runs. What is left open is listed in §8 and is small.
**Replaces:** `doc/design/public-catalog-subdomain.md` (2026-11-09), **deleted
2026-11-20**. What still held is folded in here — §1a is that doc's
"why this is cheap" analysis, §2 its snapshot decision. What did not survive: its
hosting options (Firebase Hosting / GitHub Pages, both dropped for the existing
Apache box), its four-way undecided scope (closed, §1), and its prod seller id
(wrong, §7.4). Recover it with `git log -- doc/design/public-catalog-subdomain.md`
if a reason ever appears.
**Related:** `firebase/database.rules.json`, `data/firebase/ArticleNodes.kt`,
`domain/model/MarketPricing.kt`, `doc/guides/theme.md`, and the server repo at
`/home/mandroid/Musik/git_2025/server/cut.the.crap/`.
**Not on this branch:** `domain/model/PriceDisplay.kt`, `Article.priceUnitDisplay` and
`doc/guides/price-display-unit.md` live on the unmerged `price-per-100g` branch. This
doc refers to them throughout because §7.2 makes that branch load-bearing for a public
page — but on `main` they do not exist yet, so those references read forward, not back.

---

## 1. What is being built

A public, read-only **shop window**: a cut-down resemblance of the buy app's overview
screen, served as static files from the existing `cutthecrap.link` Apache box.

| | |
|---|---|
| Public page | `bodenschaetze.cutthecrap.link` — prod catalogue, prices visible |
| Dev page | `dev.bodenschaetze.cutthecrap.link` — dev catalogue, behind HTTP basic auth |
| Shows | Only articles with `available == true` |
| Shows per article | Picture, name, price, unit, Bio badge |
| Market picker | Yes — switching market re-prices the list |
| Does not show | `scarcity` (decided: dropped at export), seller-side fields |
| Does not do | Login, basket, ordering, messaging |

**This is not the Compose web app.** `webApp/` and the `js(IR)` target stay dead; see
§6 for why that decision was re-examined on 2026-11-20 and kept.

### What "a cut-down resemblance of the overview screen" means concretely

The buy app's card (`ui/mainscreen/ProductCard.kt`, `ModernProductCard`) carries:
image, name, price rendered through `priceDisplayFor(product, price.gross)`, a Bio
badge derived from `productName.contains("Bio")`, a scarcity band, and an info button
into the detail screen. The page keeps the first four and drops the last two:
`scarcity` never leaves the database (§7.1), and there is no detail screen to navigate
to. `detailInfo` can sit under the card as text if it is wanted; that is a page-design
question, not a data one.

The app lists through `state.filteredArticles` in a `LazyColumn` with category
toggles. The page can mirror the category grouping from `Article.category` directly —
it is in `publicFields` already.

---

## 1a. Why this is cheaper than it sounds

Folded in from the 2026-11-09 doc, which this one replaces.

### The publishable half is already its own node

The expensive part of publishing a catalogue is normally deciding what is publishable.
That decision is already made, and was made for an unrelated reason.
`ArticleNodes.publicFields` writes exactly this to `/articles/{sellerId}/{articleId}`:

```
productId, productName, available, unit, price, weightPerPiece,
imageUrl, category, searchTerms, detailInfo, taxRate, scarcity
```

and `privateFields` keeps these in `/seller_articles/{sellerId}/{articleId}`:

```
acquirePrice, markupFactor, supplier, origin, certification, quality,
barcode, packageSize, reorderLevel, stockGated, sellerAvailable
```

The split exists to keep the seller's margins and sourcing away from buyers *inside the
app*. It happens to be precisely the boundary a public shop window needs. **There is no
data modelling to do** — the node is already shaped like a catalogue, and the two halves
are already written and deleted together in one multi-path update.

Of those twelve public fields the page uses eight, ignores `searchTerms`,
`weightPerPiece` and `taxRate` (the last one feeds the VAT sentence, §7.2), and
deliberately drops `scarcity` (§7.1).

### The images are already public

`Article.imageUrl` holds a Firebase Storage download URL with `?alt=media&token=…`.
Those are unauthenticated: any page on any domain can render them today. No migration,
no re-upload, no CORS work, no second image pipeline — half of "hosting a catalogue"
already solved.

### So the only obstruction was one line

```json
"articles": { "$sellerId": { ".read": "auth != null", … } }
```

The catalogue is readable by every **signed-in** user, not by the public. That was the
whole blocker — and under the snapshot approach in §2 it does not even need to change.

---

## 2. The data path: a snapshot that carries answers, not rules

A script exports `/articles/{sellerId}` and `/markets/{sellerId}` with admin
credentials. Nothing public-facing ever touches the database, so `/articles` stays at
`.read: auth != null` and **no security rule changes for this feature.** That part was
decided on 2026-11-09 and the reasoning there still holds: RTDB bills on downloaded
bytes and a public `.read` is an uncapped surface, a static file is cacheable and
survives a Firebase outage.

What this doc adds is **where the pricing rule runs**, which the earlier doc did not
have to answer because it had no market picker.

### 2.1 The trap: a second implementation of the price law

`resolvePrice(article, pricing)` (`domain/model/MarketPricing.kt`) is a pure function,
about forty lines, depending on nothing but `kotlin.math`. It is tempting to
re-implement it in JavaScript on the page. **Do not.** The rule has four edges that
exist on purpose and that a re-implementation will get wrong eventually:

- narrowest matching scope wins *per cause*, and different causes stack;
- every factor is taken against the base price and factors are never compounded, so
  the answer cannot depend on the order conditions happen to be stored in;
- **every component is rounded to cents and the gross is their sum** — not the other
  way round, because a breakdown that does not add up is worse for a buyer than no
  breakdown at all;
- never below zero.

The same argument applies a second time to `priceDisplayFor`
(`domain/model/PriceDisplay.kt`): a kilo price above the threshold is quoted per 100 g
with the kilo price beside it, the *base* price decides the unit, and
`Article.priceUnitDisplay` overrides it per article. Two implementations of that drift
into a page quoting a different unit than the app for the same article.

### 2.2 The decision: precompute on the JVM with the real functions

A JVM entry point in this repo reads the exported dump, calls the **real**
`resolvePrice` and the **real** `priceDisplayFor`, and writes one `catalog.json` where
each article carries its already-resolved price and display string **per market**.

```
markets: [ { id, name, street, city, dayOfWeek, begin, end }, … ]
articles: [
  {
    id, name, unit, category, imageUrl, detailInfo, bio,
    prices: { "<marketId>": { gross, base, display, components: [ { cause, amount, note } ] } }
  }, …
]
```

Consequences, all of them good:

- **One implementation of the rule**, in Kotlin, already unit-tested
  (`PriceDisplayTest`, 17 cases).
- The page contains **no price logic at all** — the market picker becomes a key
  lookup, which is why it does not need to be deferred to a later stage.
- Size is a non-issue: roughly 3 markets × ~25 available articles today, and ~100 even
  if everything were available again.
- The breakdown travels with the number, so the page *can* explain a market surcharge
  in the seller's own words later without re-deriving anything.

The export and the precompute are one step, not two: the script's output is the file
the page fetches.

### 2.3 Why not simply let the page read RTDB

Covered in the 2026-11-09 doc and unchanged. Short version: it would need a rules
change, it is an uncapped bandwidth surface, and it dies when Firebase does.

---

## 3. Hosting: the existing Apache box

The server is the live `cutthecrap.link` host, `187.124.191.50` (Hostinger KVM 1, one
vCPU, Ubuntu, Apache + mod_wsgi + certbot), managed from
`/home/mandroid/Musik/git_2025/server/cut.the.crap/`. Note that that repo's
`doc/SERVER_SETUP.md` still names the **old** IP `153.92.221.202`; `deploy.sh` and
`doc/KUCKMAL_SETUP.md` are the current truth.

Three reasons this host rather than Firebase Hosting or GitHub Pages:

1. **It already serves the other half of this app's web surface.**
   `cutthecrap.link/connect` is the buyer-invite deep-link redirect
   (`routes/other.py` → `templates/connect.html`), which `BuyMainActivity.kt:217`
   handles. Catalogue and invite link end up on one box, under one deploy script.
2. **There is a static-site precedent to copy exactly.**
   `jukestar.cutthecrap.link` is a plain `DocumentRoot` vhost with certbot on top
   (`doc/apache2-jukestar.conf` in the server repo).
3. **Static means no new WSGI daemon group**, which is the single most important
   property of this change. Adding a third `WSGIDaemonProcess` group is what wedged
   Apache for *every* site on the box with `AH03490: scoreboard is full` — the stock
   `ServerLimit` and `MaxConnectionsPerChild 0` combination documented in
   `doc/KUCKMAL_SETUP.md` §5a. Two static vhosts touch none of it.

### 3.1 The dev page's private login

HTTP basic auth, following the pattern already running on this box for `/joerg`:
`doc/apache2-joerg-auth.conf` is deployed as a `conf-available` **snippet**, not into
the vhost, deliberately so that a certbot renewal cannot remove it. Same trick scoped
to `dev.bodenschaetze.cutthecrap.link`.

Copy the server repo's post-deploy check as well: it warns when the htpasswd file is
missing, because a snippet enabled without its password file answers **500**, not a
password prompt.

### 3.2 Serving details to get right once

- `certbot --apache -d bodenschaetze.cutthecrap.link` and again for the dev host.
  Both are ordinary HTTP-01 certificates; no wildcard needed.
- `mod_deflate` on `text/html`, `text/css`, `application/javascript`,
  `application/json` — `catalog.json` compresses very well.
- Long-cache headers on images and hashed assets; `catalog.json` itself must **not**
  be long-cached, or a refreshed snapshot will not reach anyone.
- `.wasm` MIME type is *not* needed — that was only a concern for the Compose canvas
  bundle, which §6 rules out.

### 3.3 Deploy vehicle

The server repo's `deploy.sh` already pulls from source trees outside itself behind
per-feature guards (`CG_SRC` for CommonGround, `KUCKMAL_SRC` for the kuckmal API, each
with a `DEPLOY_*=false` flag). This follows that groove:

- `NEWVERSE_WEB_SRC` pointing at the generated page + `catalog.json`;
- `DEPLOY_NEWVERSE_WEB=false` by default;
- two vhost files and the auth snippet shipped through the existing **two-pass**
  `DEPLOY_APACHE_CONFIGS` config guard — configs go out on one run, app files on the
  next. That guard exists because `doc/*.conf` overwriting server-side tuning has
  already taken the box down once.

Prior art for the reserved-frontend shape: `api.kuckmal.cutthecrap.link` serves the
API while `kuckmal.cutthecrap.link` is documented as reserved for a frontend that does
not exist yet. This is the same arrangement, one slot further along.

---

## 4. Refresh

The catalogue changes when the seller edits an article or imports a BNN price list,
which is **bursty rather than continuous**. So: a cron on the server plus a manual
"publish now" beats a clever hook into the seller's publish action, and costs an hour
instead of a day. **Decided 2026-11-20: daily.** That is enough because `available` is
the only field that moves on its own, and it moves with stock rather than minute by
minute; anything more urgent than a day is what the manual publish is for.

Staleness is the price of the snapshot and is accepted. The page should say when it
was last refreshed, so a visitor reading a sold-out item knows why.

---

## 5. Why the market picker is nearly free

`Market.priceConditions` already lives on the market, which is already readable by any
signed-in user — put there, per `Market.kt`, precisely so "a buyer can price the
catalogue before being approved". Export it, precompute per market (§2.2), and the
picker is a dropdown plus a key lookup.

Two things it exposes that the app does not:

- **Per-market price differences become publicly comparable**, including each
  condition's `note` — the seller's own words explaining a surcharge. Inside the app
  a buyer sees one market at a time; on the page they see the spread. That is a
  positioning decision, not a bug, but it should be a conscious one.
- Market addresses and hours become public, which is marketing and presumably wanted.

---

## 6. The Compose web app, and why it stays dead

Reopened and re-decided on 2026-11-20, because "bring the web platform up" could have
meant resurrecting `webApp/`. It does not, because nothing in "present the Sortiment"
needs it — login, basket, order writes and messaging are all out of scope, and a
read-only page shares almost nothing with the buy app's job.

What a resurrection would have cost, measured rather than guessed:

- `:shared:compileKotlinJs` **fails today**, as documented. But the documented count
  is stale: CLAUDE.md and the project memory both say "three `Log.d(TAG)` files"
  (`AppleSignInManager`, `GoogleSignInManager`, `ImageLoaderConfig`), and those three
  are indeed the only errors the compiler *reports* — because it bails on them before
  expect/actual matching. Two `expect class`es added since have **no `jsMain` actual
  at all**: `OrderReminderStorage` and `StockReminderStorage`. That number grows with
  every new `expect`, which is the real cost of a fourth target, not the five errors.
- **The Firebase bootstrap is wrong as written.**
  `webApp/src/jsMain/resources/index.html` initializes Firebase from `gstatic` ESM
  imports, while GitLive's JS artifacts bind the npm `firebase` module that webpack
  bundles. Those are two different module instances; the Kotlin side would most likely
  find no `[DEFAULT]` app and `Firebase.auth` would throw at startup. Never exercised,
  because the config placeholders were never filled in.
- **Unresolved:** whether CMP 1.10.3 still supports `js(IR)` canvas at all, versus
  wasmJs only. And wasmJs is not an escape — GitLive 2.4.0 ships no wasmJs artifact
  and whether 2.5.0 does is unclear.
- **A public URL on the buy app lands on a login wall.** The buy flavor does not
  auto-sign-in; a visitor sees the login screen and gets in only by tapping "Continue
  as Guest". Correct for an app-store install, poor for a link on a flyer — and making
  web auto-guest means anonymous accounts creatable from an open URL, which lands in
  the open abuse-containment questions of `doc/guides/demo-order-provenance.md`.

A static page has none of these properties. **If web is ever genuinely resumed, the
first step is still the five compile errors and then the Firebase bootstrap** — but
this feature does not pay for them.

Housekeeping that follows: CLAUDE.md's "three `jsMain` files" line should become "the
breakage grows with every new `expect`", so nobody budgets from the number five.

---

## 7. Four things that would bite

### 7.1 `scarcity` is dropped at export — decided

`ArticleScarcity` is in `ArticleNodes.publicFields` and is derived from stock
(`doc/guides/stock-driven-availability-design.md`). In the app it tells a buyer to
hurry; on an open page it broadcasts the stall's stock position to anyone who looks,
competitors included. **Decided 2026-11-20: it does not ship.**

Drop it in the export, not at render. A field that never leaves the database cannot
leak out of a cache, out of a `view-source:`, or out of a copy of `catalog.json`
somebody keeps.

`available` is the milder version of the same question and does ship — it has to,
since the page shows only available articles. Note what that implies: the page is a
public, dated record of what the stall had in stock. During the demand trial (§7.3)
that is also a public record of the trial's shelf.

### 7.2 Public prices bring PAngV in properly — the one open legal item

This is the point that was explained badly in conversation, so at length:

Behind a login, a price is shown to someone who has an account with this seller. On an
open page the same number is an **offer to the public**, and German price-indication
law (Preisangabenverordnung) attaches three obligations that the app has so far not
had to think about:

1. **Grundpreis** — a unit price per kg or per litre, next to the selling price, for
   goods sold by weight or volume. This is exactly the territory of the held-back
   `price-per-100g` branch, which quotes an expensive kilo price per 100 g with the
   kilo price beside it. On a public page that branch stops being a display nicety and
   becomes the compliance mechanism — including its open refinement that PAngV's real
   test is the *customary sale quantity*, not whether the price crosses 4,00 €/kg.
   **The branch's threshold constant is a positioning choice today; on a public page
   it is a legal one.**
2. **A statement about VAT** — whether the prices include it. `Article.taxRate` is
   filled for every prod article, so the data is there; the page needs the sentence.
3. **Impressum** reachable from the page. The text already exists as string resources
   (`about_impressum_*` in `strings.xml`); it needs to be on the page, not only in the
   app.

**None of this is a code question and none of it should be settled in a code review.**
It belongs on the list already going to the tax advisor
(`doc/plans/TODO.md` § "Questions for the tax advisor"). The engineering consequence is
only this: §2.2's precompute should produce the Grundpreis string from the real
`priceDisplayFor`, so that whatever the advisor says is implemented once.

### 7.3 Prod prices are being measured right now

The trial live since 2026-11-15 exists to **measure demand against a known shelf** —
25 available articles, each stocked and `stockGated`, so the shop equals the ledger.
A systematic repricing was deliberately withheld, because changing prices as the
measurement starts makes the result unusable for the question it was run to answer.

**Decided 2026-11-20: prod prices ship publicly anyway.** Recorded here because it is
a real interaction, not an objection: publishing the shelf to a wider audience is
itself a change in the conditions being measured. Whoever reads the trial's numbers
afterwards needs to know the page went live, and when. Mentioned again in
`doc/plans/TODO.md` where the trial is tracked.

### 7.4 The seller id, and one stale doc line

| Project | Seller id |
|---|---|
| `fire-one-58ddc` (dev) | `cPkcZSiF3LMXjWoqW6AqpA9paoO2` |
| `bodenschaetze-a988e` (prod) | `x64pN9m4wcYlZqB2qMTxpwIm9DD2` |

The **code is already correct** everywhere: `shared/build.gradle.kts:17-18`
(`DEV_SELLER_ID` / `PROD_SELLER_ID`, fed to `buildConfigField`) and
`DefaultSellerId.ios.kt` both have the right prod id, resolved per build type since the
2026-10-08 fix. `jsMain`'s actual hardcodes the dev id, which is harmless while the js
target is dead.

Exactly **one** place was wrong: the table in the deleted
`public-catalog-subdomain.md`, which listed `2e2h2VdsyqM7QakqUfCVLkFCsUh1`. That id
appears otherwise only in `doc/archive/production-db-migration.md`, where it is correct
as history — it is the id the production database was migrated *from*. So the stale
line looks like a migration-era copy that was never updated, and it died with the file.

The seller id belongs in the export script's configuration, never typed into the page.
This is the trap `DEFAULT_SELLER_ID` fell into on 2026-10-08: a hardcoded dev id in a
release build showed an empty catalogue.

---

## 8. Open questions

Small, and none of them blocks starting.

1. ~~**Refresh frequency.**~~ **Closed 2026-11-20: daily, plus a manual publish.** (§4)
2. **Does `detailInfo` appear on the page**, or is the card name-and-price only? (§1)
3. **Does the page link into the app** — store badges, a link to
   `cutthecrap.link/connect`, or nothing?
4. ~~**The brand split.**~~ **Closed 2026-11-20.** `docs/` — the GitHub Pages site
   carrying privacy, terms and support — was still on the pre-2026-10-26 teal
   `#008577`. It is now on the Bodenschätze palette (`doc/guides/theme.md`), a pure
   recolour of all twelve files with no copy or markup change, so this page has one
   brand to match rather than a choice between two. Details and the mapping are in
   `GITHUB_PAGES_SETUP.md`. The Impressum requirement in §7.2 may still pull `docs/`
   into this work, but as content, not colour.
5. **Grundpreis / VAT / Impressum wording** — tax advisor, not us. (§7.2)

---

## 9. Shape of the work

| Step | Where | Estimate |
|---|---|---|
| JVM export + precompute (`catalog.json` from `/articles` + `/markets`, real `resolvePrice` + `priceDisplayFor`) | newverse | ~half a day |
| The page itself — overview-screen resemblance, category grouping, market picker, flyer palette | newverse | ~half a day |
| `NEWVERSE_WEB_SRC` + `DEPLOY_NEWVERSE_WEB` guard, two vhosts + auth snippet through the config guard | server repo | ~2h |
| DNS for both names, certbot twice, htpasswd for dev | server | ~1h, mostly propagation |
| Refresh cron + manual publish | server | ~1–2h |

Call it **two days** with the market picker included, most of it the page.

Nothing in this plan changes a security rule, adds a WSGI process group, or touches the
app. The riskiest single step is the Apache config deploy, and the two-pass guard
already exists to contain exactly that.
