#!/usr/bin/env node
/**
 * Offer only what is actually on the ledger, and withdraw the rest.
 *
 * During the trial the catalogue should say what the stall can account for. An
 * article that is on sale but not stocked is measured by nothing: it accumulates
 * demand against a shelf nobody counted, and at the end of the trial its number
 * cannot be turned into "buy this much", which is the whole point of the exercise.
 * So: anything the ledger knows stays on sale, everything else comes off.
 *
 * The set is read from the **database**, not from a list in this file — an article is
 * "stocked" when it is `stockGated` and has a `/stock` entry. That is the same
 * condition `seed-tryout-stock.mjs` establishes, so the two cannot drift apart, and
 * this keeps working unchanged when the seller gates another article by hand later.
 *
 * ### Why two fields, not one
 *
 * `available` is a derived field (`doc/guides/stock-driven-availability-design.md`
 * § "`available` is derived, not edited"). For an **ungated** article the derivation
 * reduces to
 *
 *     available = sellerAvailable && stockPermitsOrders(...)   // true when ungated
 *               = sellerAvailable
 *
 * so writing `available: false` on its own would last exactly until the seller next
 * opens the app: `AvailabilityPublisher` would recompute `sellerAvailable && true`,
 * find `true`, and publish the article straight back onto the shelf. The intent is
 * `sellerAvailable`; `available` is its shadow. Both are written — the first so the
 * decision sticks, the second so the shop reflects it now rather than after the next
 * launch. `scarcity` goes back to `UNKNOWN` where it is not already, because that is
 * what an ungated article publishes.
 *
 * ### What it will not do
 *
 * - **Withdraw a stocked article.** Those are the trial; they are never touched.
 * - **Run with an empty ledger.** With nothing stocked the rule would withdraw the
 *   entire catalogue, which is never what anybody meant.
 * - **Touch anything already off.** Only articles currently on sale are changed, so a
 *   re-run writes nothing.
 * - **Delete.** Nothing is removed; `--restore` puts every withdrawn article back.
 *
 * Dry run by default. Pass --apply to write.
 *
 *   node sell-only-stocked.mjs --project <id>
 *   node sell-only-stocked.mjs --project <id> --apply
 *   ... --from-export <export.json>   judge an export instead of the live database
 *   ... --keep "Name,Name"            leave these on sale though they are unstocked
 *   ... --restore                     put the unstocked articles back on sale
 *   ... --report <file.csv>
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const restore = args.includes("--restore");
const flag = (n) => (args.includes(n) ? args[args.indexOf(n) + 1] : null);
const exportPath = flag("--from-export");
const project = flag("--project");
const reportPath = flag("--report");
const keep = new Set((flag("--keep") ?? "").split(",").map((s) => s.trim()).filter(Boolean));

if (!exportPath && !project) {
  console.error(
    "Usage: node sell-only-stocked.mjs --project <id> [--apply]\n" +
    "       [--from-export <export.json>] [--keep \"Name,Name\"] [--restore] [--report <f.csv>]"
  );
  process.exit(2);
}

function read() {
  if (exportPath) return JSON.parse(readFileSync(exportPath, "utf8"));
  const get = (node) =>
    JSON.parse(
      execFileSync("firebase", ["database:get", node, "--project", project], {
        encoding: "utf8", maxBuffer: 64 * 1024 * 1024,
      })
    );
  return { articles: get("/articles"), seller_articles: get("/seller_articles"), stock: get("/stock") };
}

const db = read();
const articles = db.articles ?? {};
const sellerArticles = db.seller_articles ?? {};
const stock = db.stock ?? {};

const plan = [];
for (const [sellerId, catalog] of Object.entries(articles)) {
  const sellerHalf = sellerArticles[sellerId] ?? {};
  const levels = stock[sellerId] ?? {};

  const stocked = new Set(
    Object.keys(catalog).filter(
      (id) => (sellerHalf[id] ?? {}).stockGated === true && levels[id] !== undefined
    )
  );
  if (stocked.size === 0) {
    console.error(`${sellerId}: nothing is stocked - refusing to withdraw a whole catalogue.`);
    process.exit(1);
  }

  for (const [articleId, article] of Object.entries(catalog)) {
    const name = (article.productName ?? "").trim();
    const seller = sellerHalf[articleId] ?? {};
    const isStocked = stocked.has(articleId);
    const on = article.available === true;

    const row = { sellerId, articleId, name, isStocked, on, unit: article.unit ?? "",
                  price: article.price ?? 0, acquirePrice: seller.acquirePrice ?? 0 };

    if (isStocked) { plan.push({ ...row, action: "bleibt (bestandsgeführt)" }); continue; }
    if (keep.has(name)) { plan.push({ ...row, action: "bleibt (--keep)" }); continue; }

    if (restore) {
      plan.push({ ...row, action: on ? "schon im Verkauf" : "zurück in den Verkauf", change: !on });
    } else {
      plan.push({ ...row, action: on ? "aus dem Verkauf" : "schon aus", change: on });
    }
  }
}

const changing = plan.filter((p) => p.change);
const updates = {};
for (const p of changing) {
  const want = restore;
  const u = (updates[p.sellerId] ??= {});
  // The intent, so the decision survives the next derivation...
  u[`seller_articles/${p.sellerId}/${p.articleId}/sellerAvailable`] = want;
  // ...and its projection, so the shop shows it now.
  u[`articles/${p.sellerId}/${p.articleId}/available`] = want;
  // An ungated article publishes no band.
  u[`articles/${p.sellerId}/${p.articleId}/scarcity`] = "UNKNOWN";
}

const stocked = plan.filter((p) => p.isStocked);
const kept = plan.filter((p) => p.action === "bleibt (--keep)");
console.log(`bestandsgeführt (bleibt im Verkauf): ${stocked.length}`);
if (kept.length) console.log(`per --keep behalten:                ${kept.length}`);
console.log(`${restore ? "zurück in den Verkauf" : "aus dem Verkauf"}:              ${changing.length}`);
console.log(`unverändert:                        ${plan.length - stocked.length - kept.length - changing.length}\n`);

const w = (s, n) => String(s).padEnd(n);
console.log(`${w("Artikel", 26)} ${w("Einheit", 8)} ${w("VK", 7)} ${w("EK", 7)}`);
for (const p of changing.sort((x, y) => x.name.localeCompare(y.name))) {
  console.log(`${w(p.name.slice(0, 25), 26) } ${w(p.unit, 8)} ${w(p.price, 7)} ${w(p.acquirePrice, 7)}`);
}

if (reportPath) {
  const rows = [["artikel", "articleId", "einheit", "vk", "ek", "bestandsgefuehrt", "vorher_verfuegbar", "aktion"]];
  for (const p of plan) rows.push([p.name, p.articleId, p.unit, p.price, p.acquirePrice, p.isStocked, p.on, p.action]);
  writeFileSync(reportPath, rows.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(",")).join("\n"));
  console.log(`\nreport: ${reportPath}`);
}

if (!apply) {
  console.log("\ndry run - nothing written. Pass --apply to write.");
  for (const [sellerId, u] of Object.entries(updates)) console.log(`  ${sellerId}: ${Object.keys(u).length} paths`);
  process.exit(0);
}
if (!project) { console.error("\n--apply needs --project."); process.exit(2); }
if (changing.length === 0) { console.log("\nnichts zu tun."); process.exit(0); }

for (const [sellerId, u] of Object.entries(updates)) {
  const file = `/tmp/sell-only-stocked-${sellerId}.json`;
  writeFileSync(file, JSON.stringify(u, null, 2));
  execFileSync("firebase", ["database:update", "/", file, "--project", project, "--force"], { stdio: "inherit" });
  console.log(`wrote ${Object.keys(u).length} paths for ${sellerId}`);
}
