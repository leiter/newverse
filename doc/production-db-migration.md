# Production Realtime Database Migration

**Written:** 2026-10-08
**Source:** `bodenschaetze-a988e` export, 204 KB, taken 2026-10-08
**Output:** `tmp/db-migration/bodenschaetze-a988e-migrated.json` (+ `changes.log`)
**Status:** produced and verified, **not applied**

`tmp/` is gitignored and both files stay there: the export carries real customer
email addresses and phone numbers and must not be committed.

## What production contains

Four top-level nodes — `articles`, `buyer_profile`, `orders`, `seller_profile`. One
seller, 104 articles, 20 buyers, 48 orders over 39 pickup days from 2021-03-25 to
2025-01-02. No `seller_articles`, `sales`, `sale_index`, `invitations` or
`invite_tokens`: those features have never written to production.

The database has been dormant for 21 months, which is what makes this a cheap moment
to migrate.

---

## Deferred: the two buyer-facing migrations

**Both are needed before the pending rules reach production. Neither is in the
migrated file — `buyer_profile` and `orders` are carried over byte-identical.**

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

**It has to run before the rules deploy**, not after — afterwards only the seller
account can still write those nodes.

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

264 edits. Every one is listed in `tmp/db-migration/changes.log`.

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

### Deliberately left alone

- **`productId`** — 6 duplicate values including the empty string. These are BNN
  supplier codes; inventing replacements would break matching against supplier price
  lists. Reported only.
- **21 order lines reference a deleted article** (`-MVWgIGBmC45n3pIo7OY`, "Bananen").
  Harmless, because order lines carry denormalized copies, but anything resolving a
  line back to an article by id must tolerate the miss.
- **`mode: -1`** everywhere, meaning unknown. Left as found.
- **`seller_articles`** — not created. The bookkeeping split needs purchase price and
  markup per article, and none of that exists anywhere. All 104 articles will start
  with no seller-side half.

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

## Applying it

The file is a full-database export, so importing it **replaces the entire database**.
Take a fresh export first — the source here is a snapshot from 2026-10-08 09:12 and
anything written since would be lost.

1. Fresh export from the console, diff it against the source to confirm nothing moved.
2. Import `bodenschaetze-a988e-migrated.json` at the database root.
3. Spot-check in the seller app: category chips, unit display, the two `Rote Beete`
   articles under search.
4. B1 and B2 above remain open and still block the rules deploy described in
   `doc/pre-release-checklist.md`.

## Related

- A latent, unrelated bug: `ProductCategory.fromString()` has no entry for `"obst"` or
  `"gemüse"` themselves, so those two canonical names round-trip to `SONSTIGES`. It
  does not affect this migration — the only caller is `BnnParser.kt:212`, which
  classifies supplier product names, never stored article categories. Worth a two-line
  fix when that file is next touched.
