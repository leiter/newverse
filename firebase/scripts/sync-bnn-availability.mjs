#!/usr/bin/env node
/**
 * Set `available` and `productId` on live articles from a week's Terra BNN file.
 *
 * The BNN file carries an availability flag per row - only `A` counts as available,
 * the same reading as `BnnParser.kt` - so a fresh file is the authority on what can
 * be ordered this week. This walks the live catalog, finds each article's BNN row,
 * writes `available` from that row's flag, and fills `productId` with the Terra
 * article number so the next run needs no matching at all.
 *
 * Matching is by name and it is rough, which is a deliberate choice and worth being
 * honest about. The seller's `productId` values are their own shelf numbers ("17",
 * "233", "F/") with zero overlap with Terra article numbers, so a first run has
 * nothing better to go on. Names cannot separate `Apfel Braeburn` from `Apfel Gala`,
 * so some matches will be wrong and some articles will be switched off because the
 * matcher failed rather than because the produce is gone. The `--report` output names
 * every match it made, and once `productId` holds Terra numbers a later run matches
 * on the number and the guessing stops.
 *
 * Two consequences to know before running it:
 *
 * - An article with no match is marked unavailable. About a quarter of this catalog
 *   is the farm's own produce (origin REG: Milan, Robuschka, the herbs), which is
 *   never in a Terra list, so those get switched off even though a Terra file says
 *   nothing about them. --skip-unmatched leaves them alone instead.
 * - `available` set by hand in the seller app is overwritten. The file wins.
 *
 * `productId` is only ever filled, never overwritten: an article that already holds
 * a Terra number keeps it, and so does one holding a shelf number, unless
 * --replace-product-id says otherwise. The shelf numbers are the seller's own data
 * and this script is not the place to decide they are worth less.
 *
 * Writes go as one multi-path update per seller, keyed per field, so nothing outside
 * `available` and `productId` is touched. Same shape as ArticleNodes.saveUpdate.
 *
 * Dry run by default. Pass --apply to write.
 *
 *   node sync-bnn-availability.mjs --bnn <plf.bnn> --project <id>
 *   node sync-bnn-availability.mjs --bnn <plf.bnn> --project <id> --apply
 *   node sync-bnn-availability.mjs --bnn <plf.bnn> --from-export <export.json>
 *   ... --report <file.csv>        every match, for checking afterwards
 *   ... --skip-unmatched           leave an unmatched article's availability alone
 *   ... --replace-product-id       overwrite a productId that is not a Terra number
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

// BNN v3 column positions, as BnnParser.kt reads them.
const P_ID = 0, P_AVAIL = 1, P_NAME = 6, P_UNIT = 23;

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const skipUnmatched = args.includes("--skip-unmatched");
const replaceProductId = args.includes("--replace-product-id");
const flag = (n) => (args.includes(n) ? args[args.indexOf(n) + 1] : null);
const bnnPath = flag("--bnn");
const exportPath = flag("--from-export");
const project = flag("--project");
const reportPath = flag("--report");

if (!bnnPath || bnnPath.startsWith("--")) {
  console.error(
    "Usage: node sync-bnn-availability.mjs --bnn <plf.bnn> --project <id> [--apply]\n" +
    "       node sync-bnn-availability.mjs --bnn <plf.bnn> --from-export <export.json>\n" +
    "       [--report <file.csv>] [--skip-unmatched] [--replace-product-id]"
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

// --- the week's list -----------------------------------------------------------

// Terra ships these DOS-encoded (cp850), as the seller's own file import expects.
// Node's TextDecoder has no cp850, and the bytes that differ from latin-1 are few:
// these are every high byte that occurs in Terra's lists, German plus two strays.
const CP850 = {
  0x81: "ü", 0x82: "é", 0x84: "ä", 0x8E: "Ä", 0x94: "ö",
  0x99: "Ö", 0x9A: "Ü", 0xA4: "ñ", 0xE1: "ß"
};
const bnnBytes = readFileSync(bnnPath);
let bnnText = "";
for (const b of bnnBytes) bnnText += b < 0x80 ? String.fromCharCode(b) : (CP850[b] ?? "?");
const bnnLines = bnnText.split(/\r?\n/).filter((l) => l.length > 0);
const header = bnnLines[0].split(";");
const bnn = [];
for (const line of bnnLines.slice(1)) {
  const f = line.split(";");
  if (f.length < 70 || !f[P_ID].trim()) continue;
  bnn.push({
    artnr: f[P_ID].trim(),
    name: f[P_NAME].trim(),
    unit: f[P_UNIT].trim().toUpperCase(),
    flag: f[P_AVAIL].trim()
  });
}
if (bnn.length === 0) {
  console.error(`${bnnPath} parsed to no rows; is it a BNN v3 file?`);
  process.exit(2);
}

// --- name matching -------------------------------------------------------------

const STOP = new Set((
  "kg st g bd kal ca im aus und der die das von bio demeter terra lose schachtel " +
  "beutel becher glas stuck sortierung premium frisch neu stk packung kiste " +
  // The article's own category leaks in through searchTerms and matches anything:
  // "gemuse" is a prefix of "Gemuesezwiebeln", which swallowed a third of the
  // catalog before these were dropped.
  "gemus obst salat krauter kraut pilz kartoffel konserv eier sonstig"
).split(" "));

const fold = (s) =>
  s.toLowerCase()
    .replace(/ä/g, "a").replace(/ö/g, "o").replace(/ü/g, "u").replace(/ß/g, "ss")
    .normalize("NFKD").replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();

// German plurals, crudely. Enough to make Zitrone meet Zitronen.
const stem = (t) => {
  for (const suf of ["en", "er", "n", "e", "s"]) {
    if (t.length > 4 && t.endsWith(suf)) return t.slice(0, -suf.length);
  }
  return t;
};
const toks = (s) =>
  new Set(fold(s).split(" ").filter((t) => t.length > 2 && !STOP.has(t)).map(stem));

const unitOk = {
  kg: new Set(["KG"]),
  "stück": new Set(["ST", "KI", "PA", "KT", "TO"]),
  bund: new Set(["BD"]),
  beutel: new Set(["BT", "NE"]),
  schale: new Set(["SC", "SCH"])
};

const bnnToks = bnn.map((b) => ({ ...b, toks: toks(b.name) }));

/** Best row for an article, or null. Rough by design - see the header. */
function bestMatch(article) {
  const at = toks(`${article.productName ?? ""} ${(article.searchTerms ?? "").replace(/,/g, " ")}`);
  if (at.size === 0) return null;
  const units = unitOk[(article.unit ?? "").toLowerCase()] ?? null;
  let best = null;
  for (const row of bnnToks) {
    let exactShared = 0, fuzzyShared = 0, longest = 0;
    for (const t of at) {
      if (row.toks.has(t)) {
        exactShared++;
        longest = Math.max(longest, t.length);
      } else if (t.length >= 6) {
        // Sellerie inside Knollensellerie: a German compound names its head last,
        // so only a suffix counts. A prefix is a different good - Brokkoli is not
        // Brokkolisprossen, and Gemuese is not Gemuesezwiebeln.
        for (const rt of row.toks) {
          if (rt.length < 6) continue;
          if (rt.endsWith(t) || t.endsWith(rt)) {
            fuzzyShared++;
            longest = Math.max(longest, Math.min(t.length, rt.length));
            break;
          }
        }
      }
    }
    if (exactShared === 0 && fuzzyShared === 0) continue;
    const unitMatch = units ? (units.has(row.unit) ? 1 : 0) : 0;
    // A long shared word is the strongest signal: it is usually the variety or the
    // produce itself. The unit agreeing breaks ties, and a shorter BNN name is
    // preferred so the plain produce wins over a processed variant.
    const score = [longest, exactShared, unitMatch, fuzzyShared, -row.name.length];
    if (!best || cmp(score, best.score) > 0) best = { row, score, exactShared, fuzzyShared };
  }
  return best;
}
function cmp(a, b) {
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return a[i] < b[i] ? -1 : 1;
  return 0;
}

// --- target --------------------------------------------------------------------

let read;
if (exportPath) {
  const snapshot = JSON.parse(readFileSync(exportPath, "utf8"));
  // Either a whole-database export, or the output of `database:get /articles`, which
  // is the node itself with no wrapper. Both are things you naturally have on disk.
  read = (p) => {
    const key = p.replace(/^\//, "");
    if (snapshot && Object.prototype.hasOwnProperty.call(snapshot, key)) return snapshot[key];
    return key === "articles" ? snapshot : null;
  };
  console.log(`Target: ${exportPath} (offline)`);
} else {
  const fb = (a) =>
    execFileSync("firebase", [...a, "--project", project], {
      encoding: "utf8", maxBuffer: 1024 * 1024 * 256
    });
  read = (p) => {
    const out = fb(["database:get", p]).trim();
    return out === "null" || out === "" ? null : JSON.parse(out);
  };
  console.log(`Target: ${project}`);
}
console.log(`BNN:    ${bnnPath}  (${header[5] ?? "?"}, ${bnn.length} rows, ` +
            `${bnn.filter((b) => b.flag === "A").length} available)`);
console.log(apply ? "Mode:   APPLY (writing to the database)\n" : "Mode:   dry run (no writes)\n");

const articles = read("/articles") ?? {};

// A Terra article number is all digits; a shelf number like "17" is too, so only an
// exact hit in this week's list counts as "already a Terra number".
const known = new Set(bnn.map((b) => b.artnr));

const plan = [];
for (const [sellerId, bySeller] of Object.entries(articles)) {
  for (const [articleId, a] of Object.entries(bySeller ?? {})) {
    const m = bestMatch(a);
    const now = a.available === true;
    const row = m?.row ?? null;
    const want = row ? row.flag === "A" : false;
    const unmatched = !row;

    const fields = {};
    if (!(unmatched && skipUnmatched) && want !== now) fields.available = want;

    const currentPid = (a.productId ?? "").toString().trim();
    const pidIsTerra = known.has(currentPid);
    if (row && !pidIsTerra && (currentPid === "" || replaceProductId)) {
      fields.productId = row.artnr;
    }

    plan.push({
      sellerId, articleId, name: a.productName ?? "", unit: a.unit ?? "",
      now, want, unmatched, row, currentPid, pidIsTerra,
      exact: m?.exactShared ?? 0, fuzzy: m?.fuzzyShared ?? 0, fields
    });
  }
}

// --- report --------------------------------------------------------------------

const changed = plan.filter((p) => Object.keys(p.fields).length > 0);
const matched = plan.filter((p) => !p.unmatched);
console.log(`Scanned ${plan.length} article(s).\n`);
console.log(`  matched to a BNN row            ${matched.length}`);
console.log(`  no match                        ${plan.length - matched.length}` +
            (skipUnmatched ? "  (left alone)" : "  (-> unavailable)"));
console.log(`  available now                   ${plan.filter((p) => p.now).length}`);
console.log(`  available after                 ${plan.filter((p) => (p.unmatched && skipUnmatched) ? p.now : p.want).length}`);
console.log(`\n  availability changes            ${plan.filter((p) => "available" in p.fields).length}`);
console.log(`    on  -> off                    ${plan.filter((p) => p.fields.available === false).length}`);
console.log(`    off -> on                     ${plan.filter((p) => p.fields.available === true).length}`);
console.log(`  productId filled                ${plan.filter((p) => "productId" in p.fields).length}`);
console.log(`    already a Terra number        ${plan.filter((p) => p.pidIsTerra).length}`);
console.log(`    kept a shelf number           ${plan.filter((p) => !p.pidIsTerra && p.currentPid !== "" && !("productId" in p.fields)).length}`);

if (reportPath) {
  const esc = (v) => `"${String(v).replace(/"/g, '""')}"`;
  const lines = [[
    "article", "unit", "available_now", "available_after", "match",
    "bnn_artnr", "bnn_name", "bnn_unit", "flag", "shared_exact", "shared_fuzzy",
    "product_id_before", "product_id_after"
  ].join(";")];
  for (const p of plan) {
    lines.push([
      p.name, p.unit, p.now,
      (p.unmatched && skipUnmatched) ? p.now : p.want,
      p.unmatched ? "NONE" : (p.fuzzy > 0 && p.exact === 0 ? "FUZZY" : "NAME"),
      p.row?.artnr ?? "", p.row?.name ?? "", p.row?.unit ?? "", p.row?.flag ?? "",
      p.exact, p.fuzzy, p.currentPid, p.fields.productId ?? p.currentPid
    ].map(esc).join(";"));
  }
  writeFileSync(reportPath, "﻿" + lines.join("\n") + "\n");
  console.log(`\nReport written to ${reportPath} (every article, matched or not).`);
}

if (changed.length === 0) {
  console.log("\nNothing to write.");
  process.exit(0);
}
if (!apply) {
  console.log(`\nDry run. ${changed.length} article(s) would change.` +
              (reportPath ? " Check the report, then re-run with --apply." :
                            " Re-run with --report <file.csv> to see each one, or --apply to write."));
  process.exit(0);
}

// --- apply ---------------------------------------------------------------------

const scratch = mkdtempSync(join(tmpdir(), "sync-bnn-availability-"));
const payloadFile = join(scratch, "payload.json");
let avail = 0, pids = 0;
try {
  const bySeller = new Map();
  for (const p of changed) {
    if (!bySeller.has(p.sellerId)) bySeller.set(p.sellerId, {});
    const u = bySeller.get(p.sellerId);
    for (const [k, v] of Object.entries(p.fields)) {
      u[`${p.articleId}/${k}`] = v;
      if (k === "available") avail++; else pids++;
    }
  }
  for (const [sellerId, update] of bySeller) {
    writeFileSync(payloadFile, JSON.stringify(update));
    execFileSync(
      "firebase",
      ["database:update", `/articles/${sellerId}`, payloadFile, "--project", project, "--force"],
      { encoding: "utf8" }
    );
    console.log(`  ${sellerId}: ${Object.keys(update).length} path(s) in one update`);
  }
} finally {
  rmSync(scratch, { recursive: true, force: true });
}
console.log(`\nWrote ${avail} availability change(s) and ${pids} productId value(s).`);
