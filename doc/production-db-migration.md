# Production Realtime Database Migration

**Written:** 2026-10-08, pipeline re-run 2026-10-09
**Source:** `bodenschaetze-a988e` export, 204 KB, taken 2026-10-08
**Output:** `tmp/db-migration/bodenschaetze-a988e-with-bookkeeping.json` — the file to
import. Intermediates beside it, one per step below, and `changes.log`.
**Status:** **partly applied.** `bodenschaetze-a988e-FINAL.json` — steps 1-3 as they
stood on 2026-10-08, *without* the `weightPerPiece` rename — was imported 2026-10-08
and the pending rules deployed; the articles sit under the new seller uid
`x64pN9m4wcYlZqB2qMTxpwIm9DD2` (see `402ce5c`). B1 and B2 below were **dropped**, not
applied: see that section. Two later changes are in the pipeline but **not in
production** — see "Still to apply" below.

Four steps, all offline — nothing contacts a Firebase project. Each writes the next
file in `tmp/db-migration/`:

```bash
# 1. articles and seller profile: categories, units, whitespace, ids, weight field
python3 firebase/scripts/migrate_prod_db.py <export.json> \
    bodenschaetze-a988e-migrated.json changes.log

# 2. fill buyerId on all 48 orders, which the pending rules gate buyer access on
node firebase/scripts/backfill-order-buyerid.mjs \
    --from-export bodenschaetze-a988e-migrated.json \
    --out bodenschaetze-a988e-migrated-with-buyerid.json

# 3. point the seller's three nodes at the uid Firebase Auth actually assigned
python3 firebase/scripts/rename_seller_uid.py \
    bodenschaetze-a988e-migrated-with-buyerid.json bodenschaetze-a988e-FINAL.json \
    2e2h2VdsyqM7QakqUfCVLkFCsUh1 x64pN9m4wcYlZqB2qMTxpwIm9DD2

# 4. reconstruct the seller-only half (doc/bookkeeping-backfill.md)
python3 firebase/scripts/backfill_bookkeeping.py \
    bodenschaetze-a988e-FINAL.json ../plf.bnn \
    bodenschaetze-a988e-with-bookkeeping.json bookkeeping-review.csv
```

Step 3 prints a reminder that `PROD_SELLER_ID` in `shared/build.gradle.kts` has to
match the new uid, or release builds read the old, now non-existent node. It already
does (`shared/build.gradle.kts:18`); it is only worth re-checking if the seller's uid
ever changes again.

`tmp/` is gitignored and every one of these files stays there: the export carries real
customer email addresses and phone numbers, and the price list is supplier data.
Neither may be committed.

## Still to apply

The import ran before the pipeline was finished, so two things the scripts now produce
have never reached live data. Neither can be delivered by re-importing: the database
has been written to since 2026-10-08, and a root import would discard everything since.
Both need a targeted update against the live node instead.

- **The `weightPerPiece` rename.** Production only ever wrote `weighPerPiece`, and the
  model reads `weightPerPiece` with no fallback, so all 104 live articles read back
  `0.0` and 76 real values are being ignored. The buyer's piece count for goods sold by
  weight comes from that field, so this is live data loss, not a cosmetic nit.
- **The `seller_articles` bookkeeping half** (step 4, `doc/bookkeeping-backfill.md`).
  Production has no `seller_articles` node at all, so the CSV tax export has nothing to
  read. This one is add-only and so is the safer of the two to apply.

## What production contains

Four top-level nodes — `articles`, `buyer_profile`, `orders`, `seller_profile`. One
seller, 104 articles, 20 buyers, 48 orders over 39 pickup days from 2021-03-25 to
2025-01-02. No `seller_articles`, `sales`, `sale_index`, `invitations` or
`invite_tokens`: those features have never written to production.

The database has been dormant for 21 months, which is what makes this a cheap moment
to migrate.

---

## Deferred: the two buyer-facing migrations

**Dropped 2026-10-08, not applied.** Every existing buyer profile is stale and points
at the old seller id, so the legacy buyers and their order history are written off
rather than migrated. The rules were deployed without either step. What follows is
kept as the record of what was found and what it would have taken.

Consequence, accepted: historical orders carry no `buyerId` and stay invisible to the
buyers who placed them; a returning legacy buyer has no
access record and lands in demo mode until they connect again.

### B1 — no order carries `buyerId`

All 48 orders have the field set `articles, buyerProfile, createdDate, id, marketId,
message, mode, notFavourite, pickUpDate, sellerId`. None has `buyerId`.

`firebase/database.rules.json` gates buyer access on exactly that field:

```
".read": "auth != null && ($sellerId === auth.uid || data.child('buyerId').val() === auth.uid)"
```

`null === auth.uid` is false, so deploying the rules makes every historical order
invisible to the buyer who placed it. The seller keeps full access.

The backfill is fully derivable and was checked end to end:
`buyer_profile/{authUid}/placedOrderIds` is a `{yyyyMMdd: orderId}` map holding 48
entries, 48 unique pairs, covering all 48 orders with no gaps, no duplicates and no
two buyers claiming the same order. One pass writing
`/orders/{sellerId}/{day}/{orderId}/buyerId = {authUid}` closes it.

**Scripted:** `firebase/scripts/backfill-order-buyerid.mjs`, dry run by default,
`--apply` to write, matching the conventions of `backfill-authuid.mjs`. It also takes
`--from-export <file>` to rehearse against a snapshot without touching a project.

Rehearsed against the 2026-10-08 export: all 48 orders resolve to a buyer, with no
unclaimed order, no order claimed by two profiles, and no existing `buyerId` to
conflict with. The script never overwrites a `buyerId` that is already set, and
reports rather than guesses when two profiles claim one order.

Run it **before** the rules deploy. The script itself works either way, since it goes
through the firebase CLI's admin credentials and those bypass rules; but deploying
first opens a window in which buyers cannot see their own order history.

**Applied to the export, not to the database.** `--from-export <in> --out <out>` writes
a copy of an export with the values filled in and contacts no project;
`bodenschaetze-a988e-migrated-with-buyerid.json` is the migrated export with all 48
`buyerId` values set. Verified against its input: `articles`, `buyer_profile` and
`seller_profile` byte-identical, same 48 orders with the same keys, `buyerId` the only
field added, no existing order field altered, and every value a profile whose
`placedOrderIds` claims that order. 14 distinct buyers, the busiest holding 11 orders.

The live database is untouched. Importing that file is what applies the backfill, so
B1 is carried by the same import as the article migration rather than needing a
separate `--apply` run.

### B2 — `buyer_access_status` does not exist

The node is absent, so all 20 buyers resolve to `AccessStatus.NONE`, and:

```kotlin
// shared/src/buyMain/.../BuyAppState.kt:96
val isDemoMode: Boolean get() = isAccessStatusLoaded && accessStatus != AccessStatus.APPROVED
```

Demo mode writes orders to a different root (`GitLiveOrderRepository.kt:330`,
`rootName = if (isDemo) "demo_orders" else "orders"`). Every existing customer would
order as normal and have it land where the seller never looks. Seeding
`/buyer_access_status/{sellerId}/{buyerUid}/status = "APPROVED"` for the 20 uids (or
the 14 with order history) avoids it.

### Also noted, not acted on

- All 20 buyers are `anonymous: true`, and 14 have no email. Anonymous Firebase
  accounts are bound to the app install; an uninstall orphans the profile and its
  order history with no way to re-link the person.
- `buyer_profile.id` is `""` in 19 of 20, and `order.id` is `""` in all 48. The node
  key carries identity; the field is vestigial. Decide whether to backfill or drop it
  from the model.
- Two profile generations: 19 old (`mode`, no address), 1 new (`street`,
  `houseNumber`, `isSelfPickup`) which is also the only one still carrying a legacy
  `buyerUUID` from before the auth-uid rekey.
- 15 of 48 orders embed an email or phone in their `buyerProfile` snapshot (3 distinct
  emails, 3 distinct phones), and the snapshots are stale copies that no profile edit
  reaches. This is the main finding in `doc/buyer-profile-data-safety.md`, quantified.
- `displayName` is being used as an order notes field — 25 distinct values across 20
  profiles, many of them multi-line shopping instructions
  (`"Kiste Johannes\n1x Sauerteig-Brot. Wenn vorhanden Avocados, gern 5x."`), while the
  order's own `message` field is empty in all 48. Customers never found it. Treat
  `displayName` as untrusted free text anywhere it is shown.

---

## Applied: articles and seller profile

368 edits. Every one is listed in `tmp/db-migration/changes.log`.

### Categories: 51 legacy values to 8 canonical ones (85 articles)

Production stored product names as categories (`Karotte`, `Äpfel`, `Pepperoni`), while
`ProductCategory` defines 13 canonical ones and the seller's category dropdown already
offers only those (`DefaultProductCatalogConfig.kt:12`). The migration applies the
mapping `ProductCategory.fromString()` already implements, so the data now matches what
the UI produces today.

| | |
|---|---|
| Gemüse | 48 |
| Obst | 20 |
| Salat | 11 |
| Kräuter | 8 |
| Pilze | 7 |
| Kartoffeln | 6 |
| Eier | 2 |
| Konserven | 2 |

Twelve legacy values fall outside `fromString()` and were mapped explicitly rather than
dumped into `Sonstiges`: `Pepperoni` (the code spells it `peperoni`), `Clementinen`
(code has the singular), `Pastinaken`, `Spitzkohl`, `Schwarzwurzel`, `Topinambur`,
`Ingwer`, `Kurkuma` to **Gemüse**; `Puntarelle` to **Salat**; `Seitlinge` to **Pilze**;
`Kaki` to **Obst**; and `Sauerkraut` to **Konserven**. That last one is the only real
judgment call — it is sold as a preserve rather than fresh produce.

**Searchability was preserved.** Article search does a raw substring match over
`productName`, `searchTerms` and `category` (`SharedStateTypes.kt:506-510`), so
collapsing the category column would have made the specific names unfindable. Any old
category not already present in `searchTerms` or `productName` was appended as a term;
two articles needed it (both `Rote Beete`). Verified: all 104 old categories and all
original search terms still match.

### The Obst and Gemüse filter chips were broken in production (32 articles)

`ProductFilter.OBST` and `GEMUESE` match on `searchTerms`, not on `category`:

```kotlin
// SharedStateTypes.kt:517-518
ProductFilter.OBST    -> filtered.filter { it.searchTerms.contains("obst", ignoreCase = true) }
ProductFilter.GEMUESE -> filtered.filter { it.searchTerms.contains("gemüse", ignoreCase = true) }
```

Not one of the 104 production articles had either word in `searchTerms`. **Both buyer
filter chips returned an empty list for every article in the catalogue.**

The migration appends the canonical category to `searchTerms`, plus the coarse chip
word where it applies. Potatoes, salad, herbs and mushrooms are grouped under
**Gemüse** — the greengrocer reading, since they all sit on the vegetable side of a
market stall. Of the 67 available articles: 11 now match Obst, 54 match Gemüse, and 2
match neither on purpose (`Eier Größe M`, `Sauerkraut`).

Change `GROUP` in the migration script if that grouping is wrong.

### Units: 7 spellings to 5 canonical (54 articles)

`Kg` x52 and `kg` x26 to `kg`; `St` x2 and `Stück` x15 to `Stück`; `Bund`, `Beutel`,
`Schale` unchanged. Result: `kg` 78, `Stück` 17, `Bund` 7, `Beutel` 1, `Schale` 1.
`ProductUnit.fromString()` already tolerated the variants, so this is cosmetic
consistency rather than a fix.

### Whitespace and ids (11 edits)

Trailing spaces trimmed from 10 text fields — 2 article `detailInfo`, and the seller's
`firstName` (`"Marco "`), `lastName` (`"Leiter "`), street and the three market
name/city fields. `seller_profile/{id}/id` was `""` and is now the node key;
`sellerId` was already correct.

### `weighPerPiece` renamed to `weightPerPiece` (104 articles)

Production only ever wrote **`weighPerPiece`**, missing the `t`. The model reads
`weightPerPiece` (`Article.kt`, `ArticleNodes.articleFromMap`) and has no fallback for
the old spelling, so every one of the 104 articles was reading back as `0.0` and
76 real values were being thrown away. The buyer's piece count for goods sold by
weight is derived from it, so this was silent data loss, not a cosmetic nit.

The field is renamed and the value kept. Only `/articles` carries it — order lines
hold their own denormalized copies and were checked: the misspelling appears exactly
104 times in the export, all of them under `/articles`.

`verify_prod_migration.py` asserts the rename rather than tolerating it: field sets
must match with the rename normalised away, and every value must come through
unchanged under the new name.

### Deliberately left alone

- **`productId`** — 6 duplicate values including the empty string. These are BNN
  supplier codes; inventing replacements would break matching against supplier price
  lists. Reported only.
- **21 order lines reference a deleted article** (`-MVWgIGBmC45n3pIo7OY`, "Bananen").
  Harmless, because order lines carry denormalized copies, but anything resolving a
  line back to an article by id must tolerate the miss.
- **`mode: -1`** everywhere, meaning unknown. Left as found.
- **`seller_articles`** — not created by this script. The bookkeeping split needs a
  purchase price and markup per article and none of that exists in the export, so this
  migration leaves all 104 articles without a seller-side half. It is reconstructed
  afterwards from the article descriptions and the Terra price list by
  `backfill_bookkeeping.py`, as a separate step on this file — see
  `doc/bookkeeping-backfill.md`.

---

## Verification

Run against the source and output:

```
buyer_profile byte-identical ......... OK
orders byte-identical ................ OK
104 articles, same keys .............. OK
no field added or removed ............ OK
price / imageUrl / available /
  productId / weighPerPiece / mode
  untouched .......................... OK
every old category still findable .... OK   (0 lost)
no original search term lost ......... OK   (0 lost)
```

`detailInfo` differs on 2 articles — trailing-whitespace trim only, confirmed
character by character.

All 104 `imageUrl`s point at `bodenschaetze-a988e.appspot.com`, so no dev-bucket URLs
leaked into production. Every `marketId` on an order and every `defaultMarket` on a
buyer resolves to one of the seller's three markets; no orphans.

## How it was applied, and why that route is now closed

These files are full-database exports, so importing one **replaces the entire
database**. That was acceptable on 2026-10-08, when the database had been dormant for
21 months. What was done:

1. Fresh export from the console, diffed against the source to confirm nothing moved.
2. `bodenschaetze-a988e-FINAL.json` imported at the database root.
3. Spot-checked in the seller app: category chips, unit display, the two `Rote Beete`
   articles under search.
4. ~~B1 and B2 above remain open and still block the rules deploy.~~ Both dropped
   (legacy buyer data is stale); rules deployed 2026-10-08.

The database has been in use since, so the two items under "Still to apply" cannot
follow the same route: a root import would discard every order and profile change made
after that snapshot. They need a targeted multi-path update against the live nodes,
written from the pipeline output rather than imported as a whole.

## Related

- A latent, unrelated bug: `ProductCategory.fromString()` has no entry for `"obst"` or
  `"gemüse"` themselves, so those two canonical names round-trip to `SONSTIGES`. It
  does not affect this migration — the only caller is `BnnParser.kt:212`, which
  classifies supplier product names, never stored article categories. Worth a two-line
  fix when that file is next touched.
