#!/usr/bin/env node
/**
 * Add the seller-only half of the catalog to a live database.
 *
 * Production carries `/articles` but has never had a `/seller_articles` node, so the
 * 104 live articles have no purchase price, supplier, origin or certification - the
 * half the CSV tax export reads. `backfill_bookkeeping.py` reconstructs it offline
 * (reasoning in doc/bookkeeping-backfill.md); this sends that reconstruction to a
 * project it cannot be imported into, because the database has been written to since
 * the snapshot and a root import would discard everything after it.
 *
 * Add-only, and that is the point: nothing here overwrites an existing value. An
 * article that already has a seller-only half is reported and skipped, never merged
 * into, because the live value was written by the seller and this file was derived
 * from a 2021 catalogue and a 2026 price list.
 *
 * What the source carries, after the matching rules in doc/bookkeeping-backfill.md:
 * 36 articles with a real acquirePrice and the markupFactor that reproduces the
 * stored selling price, and 68 with origin and certification only, read out of
 * detailInfo, where the name match was not good enough to trust a purchase price.
 * Those 68 carry markupFactor 1.0, which is exactly what the app writes for an
 * article with no purchase price, so nothing degrades when the seller next saves one.
 *
 * Every id is re-checked against live `/articles` before anything is written. The
 * rules require it - seller_articles/$sellerId/$articleId validates that the matching
 * public article exists - and admin credentials bypass .validate, so the check has to
 * happen here rather than being left to the database.
 *
 * Writes go as one multi-path update per seller, keyed per field rather than per
 * article: a field this file does not know about, written by a newer app version
 * between the census and the apply, survives. Same shape as ArticleNodes.saveUpdate.
 *
 * This script uses the firebase CLI's admin credentials, which bypass rules.
 *
 * Dry run by default. Pass --apply to write.
 *
 *   node backfill-seller-articles.mjs --source <with-bookkeeping.json> --project <id>
 *   node backfill-seller-articles.mjs --source <file> --project <id> --apply
 *   node backfill-seller-articles.mjs --source <file> --from-export <export.json>
 *
 * --source is the export holding the reconstructed seller_articles node.
 * --from-export takes the target state from a file instead of a live project, so the
 * whole plan can be checked without contacting anything.
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const PUBLIC_ROOT = "articles";
const PRIVATE_ROOT = "seller_articles";

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const flag = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : null);
const sourcePath = flag("--source");
const exportPath = flag("--from-export");
const project = flag("--project");

if (!sourcePath || sourcePath.startsWith("--")) {
  console.error(
    "Usage: node backfill-seller-articles.mjs --source <with-bookkeeping.json> \\\n" +
    "           --project <projectId> [--apply]\n" +
    "       node backfill-seller-articles.mjs --source <file> --from-export <export.json>"
  );
  process.exit(2);
}
if (exportPath && apply) {
  console.error("--from-export is read-only; it cannot be combined with --apply.");
  process.exit(2);
}
if (!exportPath && (!project || project.startsWith("--"))) {
  console.error("Either --project <projectId> or --from-export <export.json> is required.");
  process.exit(2);
}

// --- the reconstruction being delivered ----------------------------------------

const source = JSON.parse(readFileSync(sourcePath, "utf8"));
const incoming = source[PRIVATE_ROOT];
if (!incoming || Object.keys(incoming).length === 0) {
  console.error(`${sourcePath} has no ${PRIVATE_ROOT} node to deliver.`);
  process.exit(2);
}

let read;
if (exportPath) {
  const snapshot = JSON.parse(readFileSync(exportPath, "utf8"));
  read = (path) => snapshot[path.replace(/^\//, "")] ?? null;
  console.log(`Target: ${exportPath} (offline)`);
} else {
  const fb = (cmdArgs) =>
    execFileSync("firebase", [...cmdArgs, "--project", project], {
      encoding: "utf8",
      maxBuffer: 1024 * 1024 * 256
    });
  read = (path) => {
    const out = fb(["database:get", path]).trim();
    return out === "null" || out === "" ? null : JSON.parse(out);
  };
  console.log(`Target: ${project}`);
}
console.log(`Source: ${sourcePath}`);
console.log(apply ? "Mode:   APPLY (writing to the database)\n" : "Mode:   dry run (no writes)\n");

const livePublic = read(`/${PUBLIC_ROOT}`) ?? {};
const livePrivate = read(`/${PRIVATE_ROOT}`) ?? {};

// --- plan ----------------------------------------------------------------------

const planned = [];   // { sellerId, articleId, name, fields }
const occupied = [];  // already has a seller-only half
const orphaned = [];  // no matching public article, which the rules forbid

for (const [sellerId, bySeller] of Object.entries(incoming)) {
  const publicArticles = livePublic[sellerId] ?? {};
  const privateArticles = livePrivate[sellerId] ?? {};

  for (const [articleId, fields] of Object.entries(bySeller ?? {})) {
    const article = publicArticles[articleId];
    const name = article?.productName ?? "(no public article)";

    if (!article) {
      orphaned.push({ where: `${sellerId}/${articleId}` });
      continue;
    }
    if (privateArticles[articleId] !== undefined) {
      occupied.push({ where: `${sellerId}/${articleId}`, name });
      continue;
    }
    planned.push({ sellerId, articleId, name, fields });
  }
}

const incomingCount = Object.values(incoming).reduce((n, s) => n + Object.keys(s ?? {}).length, 0);
console.log(`${incomingCount} entr(ies) in the source.\n`);

const priced = planned.filter((p) => (p.fields.acquirePrice ?? 0) > 0);
console.log(`  +  to add                        ${planned.length}`);
console.log(`     of those with a purchase price ${priced.length}`);
console.log(`  =  already present, skipped      ${occupied.length}`);
console.log(`  !  no matching public article    ${orphaned.length}`);

if (priced.length > 0) {
  console.log(`\n${priced.length} entr(ies) carry a purchase price:`);
  for (const { where, name, fields } of priced.map((p) => ({ ...p, where: `${p.sellerId}/${p.articleId}` }))) {
    const { acquirePrice: ap, markupFactor: mf, supplier, origin, certification } = fields;
    console.log(
      `  ${name}\n        ${ap} x ${mf}` +
      `${supplier ? `  ${supplier}` : ""}${origin ? `  ${origin}` : ""}${certification ? `  ${certification}` : ""}`
    );
  }
}

const unpriced = planned.length - priced.length;
if (unpriced > 0) {
  console.log(`\n${unpriced} entr(ies) carry origin and certification only.`);
}

if (occupied.length > 0) {
  console.log(`\n${occupied.length} article(s) already have a seller-only half:`);
  for (const { where, name } of occupied) console.log(`  ${where}  ${name}`);
  console.log(
    "\nSkipped, not merged into. Those values were written by the seller against the\n" +
    "live catalogue; this file was derived from a 2021 export and a 2026 price list."
  );
}

if (orphaned.length > 0) {
  console.log(`\n${orphaned.length} entr(ies) have no matching public article:`);
  for (const { where } of orphaned) console.log(`  ${where}`);
  console.log(
    "\nNot written. The rules validate that seller_articles/$sellerId/$articleId has a\n" +
    "matching /articles entry, so these would be unreachable even if admin\n" +
    "credentials let them through."
  );
}

if (planned.length === 0) {
  console.log("\nNothing to write.");
  process.exit(0);
}

if (!apply) {
  console.log("\nDry run. Re-run with --apply to write these entries.");
  process.exit(0);
}

// --- apply ---------------------------------------------------------------------

// One PATCH per seller, keyed `{articleId}/{field}`: a field written by a newer app
// version between the census and now is left alone, and an article the plan does not
// name is untouched. `database:set <path> -` cannot stream a stdin body on Node 22
// with firebase-tools 11.29.1, so the payload goes through a real file.
const scratch = mkdtempSync(join(tmpdir(), "backfill-seller-articles-"));
const payloadFile = join(scratch, "payload.json");

let written = 0;
try {
  const bySeller = new Map();
  for (const { sellerId, articleId, fields } of planned) {
    if (!bySeller.has(sellerId)) bySeller.set(sellerId, {});
    const update = bySeller.get(sellerId);
    for (const [field, value] of Object.entries(fields)) {
      update[`${articleId}/${field}`] = value;
    }
    written++;
  }
  for (const [sellerId, update] of bySeller) {
    writeFileSync(payloadFile, JSON.stringify(update));
    execFileSync(
      "firebase",
      ["database:update", `/${PRIVATE_ROOT}/${sellerId}`, payloadFile, "--project", project, "--force"],
      { encoding: "utf8" }
    );
    console.log(`  ${sellerId}: ${Object.keys(update).length} path(s) in one update`);
  }
} finally {
  rmSync(scratch, { recursive: true, force: true });
}
console.log(`\nAdded ${written} seller-only entr(ies).`);
