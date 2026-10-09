# Backfilling the bookkeeping half of the production catalog

The production export carries `articles` but no `seller_articles`: the 104 live
articles have no purchase price, supplier, origin code or certification code — the
seller-only half the CSV tax export needs. This is how that half was reconstructed,
what it is based on, and what is still a guess.

Everything here operates on gitignored files under `tmp/`, because the Terra price
list and the export are supplier and customer data. Nothing in this document
reproduces a price.

## Inputs

| Input | What it is |
|---|---|
| `tmp/db-migration/bodenschaetze-a988e-FINAL.json` | the altered production RTDB, 104 articles under one seller |
| `tmp/plf.bnn` | Terra price list, "Obst & Gemüse KW 31/26" |

Two facts decide how far the price list can be trusted as a source:

- **The articles were all created in March 2021; the price list is July 2026.** Every
  push id in the export decodes to 2021-03-05 … 2021-03-26, and the BNN header reads
  `KW 31/26`. A purchase price taken from it is five years and a season away from the
  selling price beside it.
- **The article numbers do not match.** 56 of 104 articles carry a `productId`, but
  they are the seller's own shelf numbers (`17`, `233`, `F/`) and not one of them is a
  Terra article number. There is no key to join on, so the cross-check runs on names.

## Where each field comes from

`detailInfo` turned out to be the better source for the two code fields. In practice
it reads "certification + country" — `EG-Bio Italien`, `Deutschland Demeter`,
`Bioland Deutschland` — and unlike a price that does not go stale.

| Field | Source | Filled |
|---|---|---|
| `certification` | `detailInfo` text → `EG`/`DD`/`DB`/`DV` | 65 |
| | exact BNN row | 12 |
| | sibling article (inferred) | 9 |
| `origin` | `detailInfo` text → country code, or `REG` | 74 |
| | exact BNN row | 10 |
| | sibling article (inferred) | 8 |
| `acquirePrice`, `markupFactor` | exact BNN row, markup derived | 36 |
| `supplier`, `quality`, `packageSize` | exact BNN row | 36 |
| `reorderLevel` | left 0 — stock is counted, the seller is not warned | — |
| `barcode` | Terra gives none for fresh produce | 0 |

Place names that mean "our own region" (`Brandenburg`, `Waldpferdehof`, `Dahmsdorf`,
`aus der Region`, `Jansforst`) map to `REG`, not `DE`.

## Matching names without inventing matches

A first, naive pass matched 87 of 104 and was mostly wrong: `Robuschka` (a beetroot)
matched "Apfel Roter Jonagold" on the word *rote*, `Zwiebeln Gelb` matched "Zitronen,
gelb" on a colour, the five table potatoes matched "Süßkartoffel", and `Meerrettich`
matched "Rettich". Writing those purchase prices would have put plausible-looking
nonsense in the one field bookkeeping exists for.

What the match requires now:

- a **curated produce term** — an article whose head word is not in the table simply
  gets no match, with no generic fallback,
- the **same category** — `Obst` never matches `Gemüse`, which is what killed the
  beetroot-to-apple class of error,
- a **compatible unit** as a hard filter, not a score bonus, with a Kiste converted to
  its per-piece price via the count in the name,
- a term that sits **inside a longer word** of the article name is dropped, so
  `Meerrettich` no longer offers *rettich* and `Süßkartoffel` no longer offers
  *kartoffel*.

Matches are then graded, and only the top grade is written:

| Grade | Meaning | Count | Written |
|---|---|---|---|
| `EXACT` | the variety is named on both sides ("Topaz" in "Apfel Topaz"), or both are the plain produce with no qualifier left over | 36 | yes |
| `SPECIES` | same produce, different variety, colour or processing | 33 | no — price recorded in the review file only |
| `NONE` | no row at all; mostly the regional Demeter varieties Terra does not carry | 35 | no |

Colour words count as the default variety, so "Zwiebeln, gelb" stays exact. Processing
and premium words do not: that is what demotes "Knoblauch, **getrocknet**",
"**Gold**-Kiwi" and "**Mini** Romanasalat" to `SPECIES`.

## Sibling inference

Where a record was blank and another article of the same produce had a value, the
value was inherited — but only when **every** sibling agrees, so a disagreement
leaves the field blank rather than picking a winner. This filled 9 certifications and
8 origins: the second `Milan` from the first, `Rote Pepperoni` from `Pepperoni`, and
the Waldpferdehof carrot and beet varieties (`Jaune de Doub`, `Maruschka`, `Oxehella`,
`Wintersonne`) from `Robuschka`.

Two of these reach further than the rest and deserve a look before anyone relies on
them: `Meerrettich` takes origin `DE` from the daikon beside it, and `Portobello` and
`Steinchampignons` take Demeter from the `Champignons` grower.

## The cost basis, and its health warning

The 2026 purchase price **is** written into `acquirePrice` on the 36 exact matches,
with `markupFactor` derived by `ProductPricing.markupFactor(...)`, so the stored
invariant `price == sellPrice(acquirePrice, markupFactor, taxRate)` holds for every
one of them (verified to the 0.001 the tests allow, at the 7 % reduced rate these
articles fall back to).

The derived markups mostly land between 1.6 and 2.9, which reads like a real shop
margin. Two of the 36 do not:

| Article | Derived markup |
|---|---|
| Mangold | 1.013 |
| Spitzpaprika | 1.009 |

Both sit below the 1.15 floor the low-price catalog enforces. They are stored as they
came out, by decision, so the numbers are not quietly massaged — but a markup that
thin is a record of two mismatched dates, not of selling at a loss.

Four further articles would have come out under the floor and do not reach the stored
data at all, because the grading demoted them before the price was ever taken:
`Kiwi` (0.62, matched to premium Gold-Kiwi), `Zitrone` (1.08), `Wirsing Kohl` (1.02)
and `Eier Größe S` (1.11). That the suspect prices and the suspect matches are largely
the same rows is the useful signal here: a markup that looks wrong usually means the
match was wrong, not the margin.

## Running it

```bash
python3 firebase/scripts/backfill_bookkeeping.py \
    tmp/db-migration/bodenschaetze-a988e-FINAL.json \
    tmp/plf.bnn \
    tmp/db-migration/bodenschaetze-a988e-with-bookkeeping.json \
    tmp/db-migration/bookkeeping-review.csv
```

| File | Contents |
|---|---|
| `…-with-bookkeeping.json` | the export plus a `seller_articles` node, 104 records |
| `bookkeeping-review.csv` | one row per article: grade, matched Terra row, reference price, derived markup, and the source of every stored code |

`FINAL.json` is not modified. The merge refuses to run if the export already has a
`seller_articles` node, or if any private half would land without its public article —
the database rules reject that combination anyway
(`seller_articles/$sellerId/$articleId` validates that the public article exists).

## Still open

- Nobody has reviewed the 33 `SPECIES` rows. Each one has a Terra row and a reference
  price waiting in the CSV; confirming or rejecting them is the cheapest way to raise
  the 36 that carry a cost basis.
- 68 articles still have `acquirePrice` 0 (unknown). For the regional Demeter produce
  that will only ever come from the farm's own invoices, not from Terra.
- 18 articles have no certification and 12 no origin, because neither their own text,
  a Terra row, nor a sibling offered one.
- The export writes `weighPerPiece`, while the current model reads `weightPerPiece`.
  Every one of the 104 articles has the old spelling and none has the new one, so the
  app reads them all as 0.0. Unrelated to bookkeeping, but it is sitting in the same
  file and wants fixing in the same migration.
