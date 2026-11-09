# The Catalogue on a Subdomain

**Written:** 2026-11-09
**Status:** DESIGN — nothing built, nothing deployed, no rule changed.
**Decided so far:** the data path — a **periodic snapshot**, not a live read.
**Not yet decided:** the scope (what the subdomain actually serves), which is the
thing that moves the estimate from about a day to several weeks.
**Related:** `firebase/database.rules.json`, `data/firebase/ArticleNodes.kt`,
`doc/guides/theme.md`, `GITHUB_PAGES_SETUP.md`

The question asked: *how complex is it to host the product catalogue on a subdomain?*
Answer: cheaper than it sounds, for a reason that is already in the codebase.

---

## What makes this cheap

### The publishable half is already its own node

The expensive part of publishing a catalogue is normally deciding what is publishable.
That decision is already made, and made for an unrelated reason.
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

The split exists to keep the seller's margins and sourcing away from buyers inside the
app. It happens to be precisely the boundary a public shop window needs. **There is no
data modelling to do here** — the node is already shaped like a catalogue, and the two
halves are already written and deleted together in one multi-path update.

### The images are already public

`Article.imageUrl` holds a Firebase Storage download URL with `?alt=media&token=…`.
Those are unauthenticated: any page on any domain can render them today. No migration,
no re-upload, no CORS work, no second image pipeline. This is half of "hosting a
catalogue" already solved.

### So the only obstruction is one line

```json
"articles": { "$sellerId": { ".read": "auth != null", … } }
```

The catalogue is readable by every **signed-in** user, not by the public. That is the
whole blocker — and under the decided snapshot approach below, it does not even need to
change.

---

## Decided: a periodic snapshot, not a live read

A script exports `/articles/{sellerId}` with admin credentials to a static JSON file
served beside the page. The page reads that file; nothing public-facing ever touches
the database.

Why this rather than letting the page read RTDB directly:

- **No rules change at all.** The admin export bypasses rules the way the existing
  seeding scripts do (`doc/guides/rtdb-test-fixtures.md`, and the dev seeding noted in
  project memory). `/articles` stays `auth != null`, so the app's security surface is
  untouched by a marketing page. One fewer thing to get wrong, and one fewer thing to
  re-audit later.
- **RTDB bills on downloaded bytes.** A public `.read` is an uncapped surface: anyone
  can pull the whole catalogue on a loop. ~317 rows is small in absolute terms, but it
  is small *and* uncapped, which is the wrong pair. A static file is served by a CDN
  and costs the same whether one person or a crawler reads it.
- **It is cacheable and survives a Firebase outage**, which a live read does not.

The cost is staleness, and that is the open question below.

---

## Estimates by scope

The scope was left undecided. These are the four readings, for whoever picks it up.

| Scope | Estimate | Note |
|---|---|---|
| **Read-only shop window** — names, prices, pictures, categories; people look, they order in the app | **~1–1.5 days** | The snapshot script, a page on the flyer palette, DNS + TLS |
| **A data feed, not a page** — JSON on the subdomain for something else to consume | **~half a day** | The snapshot script and hosting; no UI at all |
| **Shop window plus ordering in the browser** | **weeks** | Needs auth, basket, pickup slots, order writes — essentially the buy app on the web |
| **Move the catalogue off Firebase**, app reads from the subdomain | **weeks, and it changes the data layer** | Biggest option; touches `ArticleRepository` and every caller |

Breakdown of the first one: rules untouched, export script ~2h, page ~half a day,
DNS/TLS ~1h (mostly propagation), refresh trigger ~1–2h depending on the answer below.

**Do not resurrect `webApp` / the `js(IR)` target for this.** It does not compile today
(the three `Log.d(TAG)` leftovers from `d659337`), has never run against a real backend,
and web is explicitly unmaintained — see `CLAUDE.md` and
`doc/archive/web-implementation-audit.md`. A read-only shop window shares almost nothing
with the buy app's job, and building it as a plain static page costs less than fixing
the target would.

---

## Four things that would bite

### 1. `scarcity` is in the public fields

`ArticleScarcity` ships in `publicFields` and is derived from stock
(`doc/guides/stock-driven-availability-design.md`). In the app it tells a buyer to
hurry. On an open page it broadcasts the stall's stock position to anyone who looks,
competitors included. `available` is the milder version of the same question.

**Decide at render, not at export** — or rather, decide at export: the snapshot is the
natural place to drop a field, and a field that never leaves the database cannot leak
from a cache later.

### 2. German price display

Prices behind a login and prices on an open page are not quite the same thing. A public
page is more visibly an offer to the public, which brings in Grundpreis per kg/l where
it applies and a statement about VAT. The Impressum text already exists as string
resources (`about_impressum_*` in `strings.xml`) and would need to be on the page too.

**This belongs on the list already going to the tax advisor**
(`doc/plans/TODO.md` § "Questions for the tax advisor"), not in a code review.

### 3. The brand would split

`docs/` — the GitHub Pages site carrying privacy, terms and support — is still on the
old teal `#008577`, which the app left behind on 2026-10-26. Standing up a second public
surface on the flyer palette would leave two brands on two subdomains. Either put the
new page on the Bodenschätze palette and plan to bring `docs/` across, or do them
together. `doc/guides/theme.md` has the palette; `GITHUB_PAGES_SETUP.md` has the teal
history.

### 4. The seller id differs per environment

The page needs a seller id to name the catalogue, and it is not the same in both
Firebase projects:

| Project | Seller id |
|---|---|
| `fire-one-58ddc` (dev) | `cPkcZSiF3LMXjWoqW6AqpA9paoO2` |
| `bodenschaetze-a988e` (prod) | `2e2h2VdsyqM7QakqUfCVLkFCsUh1` |

This is the same trap `DEFAULT_SELLER_ID` fell into on 2026-10-08 — a hardcoded dev id
in a release build showed an empty catalogue. It belongs in the export script's
configuration, never typed into HTML. See
`doc/plans/build-identity-and-firebase-wiring.md`.

---

## Open questions

1. **Scope.** Which of the four rows above. Everything else waits on this.
2. **Refresh trigger.** What makes the snapshot current — a cron (daily? hourly?), a
   step in the seller's publish action, or a manual button? The catalogue changes when
   the seller edits an article or imports a BNN price list, which is bursty rather than
   continuous, so a cheap cron plus a manual "publish now" may beat a clever hook.
3. **Where it is hosted.** Firebase Hosting (same project, free TLS, `firebase.json`
   already exists but declares no `hosting` block) or GitHub Pages (already serving
   `docs/`, no `CNAME` yet, so no custom domain is configured today). Firebase Hosting
   is the smaller step if the export runs from CI next to a deploy.
4. **The subdomain itself.** Name and DNS not chosen.
5. **Does `scarcity` ship?** See bite 1.
6. **Does the page link into the app** — store badges, a deep link into
   `newverse://connect`, or nothing at all?

---

## What exists today, for the record

- `firebase.json` declares `database` and `emulators` only. **No `hosting` block.**
- `docs/` holds `index.html`, `privacy.html`, `support.html`, `terms.html` and `de/`,
  `en/`. **No `CNAME`**, so it serves on the default `github.io` domain.
- `webApp/` still exists in the build and does not compile.
- No export script for `/articles` exists; `firebase/scripts/` holds the migration and
  seeding scripts, which are the nearest prior art for admin-credentialled access.
