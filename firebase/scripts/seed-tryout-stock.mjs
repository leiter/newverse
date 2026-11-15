#!/usr/bin/env node
/**
 * Put the tryout assortment on the stock ledger, so the trial measures something.
 *
 * The stall is about to run a trial, and the question it has to answer is not "did
 * the app work" but **how much of each article to buy** when it runs for real. That
 * number comes from demand measured against a known shelf, and in production the
 * shelf is not known: `/stock` and `/stock_movements` are empty, `stockGated` is
 * false on all 104 articles and `reorderLevel` is 0 on every one of them, so
 * `stockPermitsOrders` returns true before it looks at anything and `available` is
 * just a boolean somebody typed. Nothing is being recorded.
 *
 * This script gates a chosen set of articles, gives each an opening count, and sets
 * the reorder level that makes them *watched*. From then on the app does the
 * measuring by itself: committed demand accumulates against `onHand`, sales book
 * movements, and the refill list says what ran short.
 *
 * ### Why `reorderLevel` is not decoration
 *
 * `SellerArticleData.isWatched` **is** `reorderLevel > 0`, and
 * `stockMovementsForSale` records movements only for watched articles. An article
 * left at 0 would be gated, would show a scarcity band, and would still book nothing
 * when it sold — the trial would end with an empty ledger. Half a Gebinde is the
 * threshold here; it is also what `scarcityOf` measures "few" against, so the band a
 * buyer reads means "below half a crate".
 *
 * ### The opening count is a STOCKTAKE, not an INTAKE
 *
 * A stocktake declares what is actually there, and `StockNodes.levelChanges` turns it
 * into `LevelChange.SetTo` rather than an increment. That is what makes this script
 * safe to run twice: an intake of one Gebinde run twice is two Gebinde, a count of one
 * Gebinde run twice is still one. The movement ids are derived from the article id for
 * the same reason — a re-run rewrites the same rows instead of appending a second
 * opening count.
 *
 * That re-write is the one place this script steps outside the ledger's own rules,
 * which refuse to change a movement (`!data.exists()`). An admin write bypasses them,
 * and for a seed that is the behaviour wanted: the alternative is a ledger that cannot
 * be corrected without a second pass of counter-movements. Real movements — every one
 * the app writes — stay append-only.
 *
 * ### What it refuses to do
 *
 * - **An article with no `packageSize`.** The opening count would be this script's
 *   guess at how much a crate holds.
 * - **An article the catalogue has unavailable.** Gating something the seller has
 *   switched off would publish an availability they never asked for.
 * - **An article with no `sellerAvailable` recorded.** `publicationCorrections` skips
 *   a gated article with no intent, because closing it would make the pass's own write
 *   read back as the seller's wish and nothing could reopen it. All 104 have one
 *   today; this checks rather than assumes.
 * - **A name that matches two articles or none.** The set is named by product name
 *   because that is what a person checking it can read, so an ambiguous name is an
 *   error rather than a coin toss.
 *
 * ### The public half
 *
 * `available` and `scarcity` are derived fields owned by `AvailabilityPublisher`
 * (`doc/design/derived-price-and-availability.md` § 3). This script writes what that
 * derivation *would* produce — with a full Gebinde on hand and no demand yet, every
 * row comes out available and PLENTIFUL — so the database is self-consistent the
 * moment the seed lands rather than only after the seller next opens the app. The
 * publisher remains the authority and will agree with it.
 *
 * `price` is never written. Neither is `acquirePrice`: the cost sync owns that.
 *
 * Dry run by default. Pass --apply to write.
 *
 *   node seed-tryout-stock.mjs --project <id>
 *   node seed-tryout-stock.mjs --project <id> --apply
 *   ... --from-export <export.json>   judge an export instead of the live database
 *   ... --report <file.csv>           every row and what it got
 *   ... --divisor <n>                 reorder level = packageSize / n (default 2)
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";

/**
 * The tryout assortment, by product name.
 *
 * Named rather than keyed by push id so the list can be read and changed by the
 * person whose stall it is. Thirteen of these are also in the winter Sortiment
 * (`firebase/fixtures/winter-catalog.json`); the rest round it out to the staples a
 * stall cannot open without — a potato, an onion, a cabbage, an apple, a citrus, a
 * salad, a herb, a mushroom.
 *
 * Deliberately not `Milan` or `Salatgurke`: each of those names belongs to two
 * articles in the catalogue, which would split one article's demand across two rows
 * and spoil the measurement this whole exercise is for.
 *
 * `Champignons` is the brown Steinchampignon: Terra's KW47 range holds no white
 * champignon under any flag or name, and the catalogue's `Steinchampignons` was an
 * import copy of the same goods — same Terra row 128140, same cost, same price, same
 * Gebinde — which is why it was removed and this one kept. Seed this only once that
 * removal has happened; while both exist they share one row's cost and at most one of
 * them can be right.
 */
const TRYOUT = [
  // Kartoffeln
  "Goldmarie Kartoffel", "Karelia Kartoffel",
  // Zwiebelgewächse und Wurzeln
  "Zwiebeln Gelb", "Zwiebeln", "Knoblauch", "Möhren", "Robuschka", "Sellerie",
  "Pastinaken", "Lauch",
  // Kohl und Kürbis
  "Hokkaido Kürbis", "Rotkohl", "Spitzkohl", "Rosenkohl", "Kohlrabi",
  // Blatt und Frucht
  "Spinat", "Zucchini",
  // Obst
  "Charneaux", "Braeburn Apfel", "Clementinen", "Zitrone",
  // Salat, Kräuter, Pilze
  "Kopfsalat", "Feldsalat Lose", "Petersilie Glatt", "Champignons",
];

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const flag = (n) => (args.includes(n) ? args[args.indexOf(n) + 1] : null);
const exportPath = flag("--from-export");
const project = flag("--project");
const reportPath = flag("--report");
const divisor = Number.parseFloat(flag("--divisor") ?? "2");

if ((!exportPath && !project) || !Number.isFinite(divisor) || divisor <= 0) {
  console.error(
    "Usage: node seed-tryout-stock.mjs --project <id> [--apply]\n" +
    "       node seed-tryout-stock.mjs --from-export <export.json>\n" +
    "       [--report <file.csv>] [--divisor <n>]"
  );
  process.exit(2);
}

// ------------------------------------------------------------------ the catalog

function readCatalog() {
  if (exportPath) return JSON.parse(readFileSync(exportPath, "utf8"));
  const get = (node) =>
    JSON.parse(
      execFileSync("firebase", ["database:get", node, "--project", project], {
        encoding: "utf8", maxBuffer: 64 * 1024 * 1024,
      })
    );
  return { articles: get("/articles"), seller_articles: get("/seller_articles") };
}

const db = readCatalog();
const articles = db.articles ?? {};
const sellerArticles = db.seller_articles ?? {};

const round = (v, places) => Number(v.toFixed(places));
/** The gram is the finest division a retail scale reports: `ProductPricing.roundQuantity`. */
const quantity = (v) => round(v, 3);

const now = Date.now();
const month = (() => {
  const d = new Date(now);
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}`;
})();

// -------------------------------------------------------------------- the plan

const plan = [];

for (const [sellerId, catalog] of Object.entries(articles)) {
  const sellerHalf = sellerArticles[sellerId] ?? {};

  // One pass over the catalogue per name, so a name matching two articles is caught
  // rather than resolved to whichever came first.
  const byName = new Map();
  for (const [articleId, article] of Object.entries(catalog)) {
    const name = (article.productName ?? "").trim();
    if (!name) continue;
    (byName.get(name) ?? byName.set(name, []).get(name)).push(articleId);
  }

  for (const name of TRYOUT) {
    const ids = byName.get(name) ?? [];
    if (ids.length === 0) {
      plan.push({ sellerId, name, skip: "nicht im Katalog" });
      continue;
    }
    if (ids.length > 1) {
      plan.push({ sellerId, name, skip: `${ids.length} Artikel tragen diesen Namen` });
      continue;
    }

    const articleId = ids[0];
    const article = catalog[articleId];
    const seller = sellerHalf[articleId] ?? {};
    const unit = (article.unit ?? "").trim();
    const pkg = Number(seller.packageSize ?? 0) || 0;

    if (!unit) { plan.push({ sellerId, name, articleId, skip: "keine Einheit" }); continue; }
    if (pkg <= 0) { plan.push({ sellerId, name, articleId, skip: "kein Gebinde (packageSize)" }); continue; }
    if (article.available !== true) {
      plan.push({ sellerId, name, articleId, skip: "im Katalog nicht verfügbar" });
      continue;
    }
    // `SellerArticle.sellerAvailable` falls back to the public flag when this is
    // absent, so gating without it would let the correction pass's own write read
    // back as the seller's intent. publicationCorrections skips such a row forever.
    if (typeof seller.sellerAvailable !== "boolean") {
      plan.push({ sellerId, name, articleId, skip: "kein sellerAvailable hinterlegt" });
      continue;
    }

    const onHand = quantity(pkg);
    const reorderLevel = quantity(pkg / divisor);
    // remaining = onHand - committed demand, and nothing is committed against a fresh
    // ledger, so this is the whole Gebinde. Against a reference of half a Gebinde that
    // is PLENTIFUL; spelled out rather than hard-coded so the arithmetic is visible.
    const remaining = onHand;
    const scarcity =
      remaining <= 0 ? "LAST_FEW"
      : remaining <= reorderLevel / 2 ? "LAST_FEW"
      : remaining <= reorderLevel ? "LIMITED"
      : "PLENTIFUL";

    plan.push({
      sellerId, name, articleId, unit, pkg, onHand, reorderLevel, scarcity,
      productId: String(article.productId ?? ""),
      acquirePrice: Number(seller.acquirePrice ?? 0) || 0,
      wasGated: seller.stockGated === true,
      priorReorder: Number(seller.reorderLevel ?? 0) || 0,
      priorScarcity: article.scarcity ?? "UNKNOWN",
    });
  }
}

// ------------------------------------------------------------------- the update

const seeded = plan.filter((p) => !p.skip);
const skipped = plan.filter((p) => p.skip);
const updates = {};

for (const p of seeded) {
  const u = (updates[p.sellerId] ??= {});
  const movementId = `seed-tryout-${p.articleId}`;
  const m = `stock_movements/${p.sellerId}/${month}/${movementId}`;

  // Exactly the fields StockNodes.movementToMap writes, and only those: the rules
  // refuse any other child. quantity is the correction a count implies — found minus
  // what the ledger showed, which on an empty ledger is the whole count.
  u[`${m}/articleId`] = p.articleId;
  u[`${m}/quantity`] = p.onHand;
  u[`${m}/unit`] = p.unit;
  u[`${m}/kind`] = "STOCKTAKE";
  u[`${m}/source`] = "MANUAL";
  u[`${m}/recordedAt`] = now;
  u[`${m}/countedTo`] = p.onHand;
  u[`${m}/note`] = "Eröffnungsbestand Probebetrieb";
  if (p.productId) u[`${m}/productId`] = p.productId;

  // The level the movement adds up to: StockNodes.levelFields, plus onHand written
  // outright rather than as an increment, which is what a stocktake does.
  const l = `stock/${p.sellerId}/${p.articleId}`;
  u[`${l}/onHand`] = p.onHand;
  u[`${l}/unit`] = p.unit;
  u[`${l}/lastMovementAt`] = now;
  u[`${l}/lastCountedAt`] = now;
  u[`${l}/lastSource`] = "MANUAL";

  // The gate and the threshold that makes the article watched.
  u[`seller_articles/${p.sellerId}/${p.articleId}/stockGated`] = true;
  u[`seller_articles/${p.sellerId}/${p.articleId}/reorderLevel`] = p.reorderLevel;

  // The derived half, only where it would change: `available` is already true for
  // every row that got this far, so in practice this is the band.
  if (p.priorScarcity !== p.scarcity) {
    u[`articles/${p.sellerId}/${p.articleId}/scarcity`] = p.scarcity;
  }
}

// ---------------------------------------------------------------------- report

console.log(`tryout set: ${TRYOUT.length} names, ${seeded.length} seeded, ${skipped.length} skipped`);
console.log(`ledger month: ${month}, reorder level = Gebinde / ${divisor}\n`);

const w = (s, n) => String(s).padEnd(n);
console.log(`${w("Artikel", 24)} ${w("Einheit", 8)} ${w("Bestand", 9)} ${w("Melde", 9)} ${w("Band", 10)} ${w("EK-Wert", 9)}`);
let capital = 0;
for (const p of seeded) {
  capital += p.onHand * p.acquirePrice;
  console.log(
    `${w(p.name.slice(0, 23), 24)} ${w(p.unit, 8)} ${w(p.onHand, 9)} ${w(p.reorderLevel, 9)} ` +
    `${w(p.scarcity, 10)} ${w(round(p.onHand * p.acquirePrice, 2), 9)}` +
    (p.wasGated ? "  (war schon gated)" : "")
  );
}
console.log(`\nEröffnungsbestand zu Einkaufspreisen: ${round(capital, 2)} EUR`);

if (skipped.length > 0) {
  console.log("\nübersprungen:");
  for (const p of skipped) console.log(`  ${w(p.name.slice(0, 30), 31)} ${p.skip}`);
}

if (reportPath) {
  const rows = [["artikel", "articleId", "productId", "einheit", "bestand", "meldebestand", "band", "ek", "ek_wert", "status"]];
  for (const p of seeded) {
    rows.push([p.name, p.articleId, p.productId, p.unit, p.onHand, p.reorderLevel,
      p.scarcity, p.acquirePrice, round(p.onHand * p.acquirePrice, 2), "seeded"]);
  }
  for (const p of skipped) {
    rows.push([p.name, p.articleId ?? "", "", "", "", "", "", "", "", p.skip]);
  }
  writeFileSync(reportPath, rows.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(",")).join("\n"));
  console.log(`\nreport: ${reportPath}`);
}

// ----------------------------------------------------------------------- write

if (!apply) {
  console.log("\ndry run - nothing written. Pass --apply to write.");
  for (const [sellerId, update] of Object.entries(updates)) {
    console.log(`  ${sellerId}: ${Object.keys(update).length} paths`);
  }
  process.exit(0);
}
if (!project) {
  console.error("\n--apply needs --project (an export cannot be written back).");
  process.exit(2);
}
if (seeded.length === 0) {
  console.error("\nnothing to seed - refusing to write an empty update.");
  process.exit(1);
}

for (const [sellerId, update] of Object.entries(updates)) {
  const file = `/tmp/seed-tryout-stock-${sellerId}.json`;
  writeFileSync(file, JSON.stringify(update, null, 2));
  // Positional infile, not `--data`: that takes escaped JSON on the command line and
  // would write the literal string "@/tmp/...". `--force` skips the prompt.
  execFileSync(
    "firebase",
    ["database:update", "/", file, "--project", project, "--force"],
    { stdio: "inherit" }
  );
  console.log(`wrote ${Object.keys(update).length} paths for ${sellerId}`);
}
