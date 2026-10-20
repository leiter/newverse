#!/usr/bin/env node
/**
 * Census of the two `OrderedProduct` fields the buy app used to write badly:
 * `amount` and `piecesCount`. Read-only — this script never writes.
 *
 * Both were fixed in the app on 2026-10-19. The question this answers is whether the
 * records already in a database need repairing as well, and the two fields have very
 * different answers.
 *
 * `amount` is a *display* string ("1,500 kg"). The buy app stored
 * `quantity.toString()` in it from the first add onwards, so live records hold "1.0":
 * no unit, a dot for a comma, and no way for the seller to tell two kilos from two
 * pieces. It has exactly one consumer, `OrderedProduct.getFormattedAmount()`, and
 * that now ignores a value with no letters in it and formats from `amountCount`
 * instead — so every screen already reads correctly against unrepaired data. The
 * count below is therefore informational: it says how much legacy data is leaning on
 * that fallback, not how much is broken on screen.
 *
 * `piecesCount` is a number, and nothing heals it on read:
 *
 *   - `Article.weightPerPiece` defaults to 0.0 and the seller form only offers the
 *     field for *countable* units, never for kg or g — which are the only units whose
 *     piece count the buy app computed. So `(quantity / 0.0).toInt()` ran for every
 *     hand-made kg article, and `Infinity.toInt()` is Int.MAX_VALUE: 2147483647
 *     pieces, written to the order.
 *   - `updateQuantity` scaled the pieces-per-unit ratio even when the count was -1
 *     ("not applicable"), carrying the sign through into a negative count.
 *
 * Neither value is reconstructible: `weightPerPiece` was 0, so there is no basis to
 * derive a real count from. The only honest repair is -1, which is what the field
 * already means when absent. That makes the repair cheap but also cosmetic — decide
 * it from the numbers this prints.
 *
 * Nodes scanned, which is every place an OrderedProduct is persisted:
 *
 *   /orders/{sellerId}/{yyyyMMdd}/{orderId}/articles/*
 *   /demo_orders/{sellerId}/{yyyyMMdd}/{orderId}/articles/*
 *   /buyer_profile/{buyerId}/draftBasket/items/*
 *
 * This uses the firebase CLI's admin credentials, which bypass rules.
 *
 *   node audit-ordered-product-fields.mjs --project <projectId>
 *   node audit-ordered-product-fields.mjs --project <p> --paths    # list every hit
 *   node audit-ordered-product-fields.mjs --from-export <export.json>
 *
 * --from-export reads a database export from disk and never contacts a project.
 * --paths prints the full path of each affected article, which is what a repair would
 * be written from; without it the output is counts and a short sample.
 */
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";

const args = process.argv.slice(2);
const showPaths = args.includes("--paths");
const exportPath = args.includes("--from-export")
  ? args[args.indexOf("--from-export") + 1]
  : null;
const project = args.includes("--project") ? args[args.indexOf("--project") + 1] : null;

if (!exportPath && (!project || project.startsWith("--"))) {
  console.error(
    "Usage: node audit-ordered-product-fields.mjs --project <projectId> [--paths]\n" +
    "       node audit-ordered-product-fields.mjs --from-export <export.json> [--paths]"
  );
  process.exit(2);
}

/** A piece count this large can only have come from Infinity.toInt(). */
const PIECES_ABSURD = 1_000_000;
/** -1 is the legitimate "not applicable"; anything below it is the scaling bug. */
const PIECES_MIN = -1;

let read;
if (exportPath) {
  const snapshot = JSON.parse(readFileSync(exportPath, "utf8"));
  read = (path) => snapshot[path.replace(/^\//, "")] ?? null;
  console.log(`Source: ${exportPath} (offline)`);
} else {
  read = (path) => {
    const out = execFileSync("firebase", ["database:get", path, "--project", project], {
      encoding: "utf8",
      maxBuffer: 1024 * 1024 * 256
    }).trim();
    return out === "null" || out === "" ? null : JSON.parse(out);
  };
  console.log(`Project: ${project}`);
}
console.log("Mode:   read-only census\n");

// --- classify one persisted article -------------------------------------------

/** A display string always names the unit, so a real one carries letters. */
const isBareNumber = (amount) =>
  typeof amount === "string" && amount.trim() !== "" && !/[^\d.,\s+-]/.test(amount);

const findings = {
  amountBare: [],        // "1.0" where "1,000 kg" was meant
  amountMissing: [],     // absent or empty: already formatted on read, nothing to do
  piecesAbsurd: [],      // Int.MAX_VALUE from the division by zero
  piecesNegative: [],    // below -1, from the scaled ratio
  piecesOk: 0,
  articles: 0,
  orders: 0
};

function inspect(where, article) {
  if (!article || typeof article !== "object") return;
  findings.articles++;

  const amount = article.amount;
  const name = article.productName ?? "(unnamed)";
  if (amount === undefined || amount === null || amount === "") {
    findings.amountMissing.push({ where, name });
  } else if (isBareNumber(amount)) {
    findings.amountBare.push({ where, name, amount });
  }

  const pieces = article.piecesCount;
  if (typeof pieces === "number") {
    if (pieces >= PIECES_ABSURD) findings.piecesAbsurd.push({ where, name, pieces });
    else if (pieces < PIECES_MIN) findings.piecesNegative.push({ where, name, pieces });
    else findings.piecesOk++;
  }
}

/** /orders and /demo_orders share a shape: seller → yyyyMMdd → orderId → articles. */
function scanOrderNode(node, root) {
  for (const [sellerId, byDay] of Object.entries(read(root) ?? {})) {
    for (const [day, byOrder] of Object.entries(byDay ?? {})) {
      for (const [orderId, order] of Object.entries(byOrder ?? {})) {
        findings.orders++;
        const articles = order?.articles;
        if (!articles) continue;
        // Written as a list by the app, so Firebase may hand back either an array or
        // an index-keyed object depending on gaps.
        for (const [key, article] of Object.entries(articles)) {
          inspect(`${node}/${sellerId}/${day}/${orderId}/articles/${key}`, article);
        }
      }
    }
  }
}

scanOrderNode("orders", "/orders");
scanOrderNode("demo_orders", "/demo_orders");

let drafts = 0;
for (const [buyerId, profile] of Object.entries(read("/buyer_profile") ?? {})) {
  const items = profile?.draftBasket?.items;
  if (!items) continue;
  drafts++;
  for (const [key, item] of Object.entries(items)) {
    inspect(`buyer_profile/${buyerId}/draftBasket/items/${key}`, item);
  }
}

// --- report --------------------------------------------------------------------

const sample = (rows, render) => {
  const shown = showPaths ? rows : rows.slice(0, 5);
  for (const row of shown) console.log(`    ${render(row)}`);
  if (!showPaths && rows.length > shown.length) {
    console.log(`    … and ${rows.length - shown.length} more (--paths for all)`);
  }
};

console.log(
  `Scanned ${findings.orders} order(s) and ${drafts} draft basket(s): ` +
  `${findings.articles} persisted article(s).\n`
);

console.log("piecesCount — needs a repair if non-zero, nothing heals it on read");
console.log(`  absurd (>= ${PIECES_ABSURD.toLocaleString("en-US")})   ${findings.piecesAbsurd.length}`);
sample(findings.piecesAbsurd, (r) => `${r.where}  ${r.name}: ${r.pieces}`);
console.log(`  negative (< -1)             ${findings.piecesNegative.length}`);
sample(findings.piecesNegative, (r) => `${r.where}  ${r.name}: ${r.pieces}`);
console.log(`  plausible                   ${findings.piecesOk}`);

console.log("\namount — informational: getFormattedAmount() already handles both");
console.log(`  bare number ("1.0")         ${findings.amountBare.length}`);
sample(findings.amountBare, (r) => `${r.where}  ${r.name}: ${JSON.stringify(r.amount)}`);
console.log(`  absent or empty             ${findings.amountMissing.length}`);

const repairable = findings.piecesAbsurd.length + findings.piecesNegative.length;
console.log(
  repairable === 0
    ? "\nNo piecesCount repair needed: the code guards are the whole fix.\n" +
      "Record that in doc/production-db-migration.md so the question is not reopened."
    : `\n${repairable} article(s) hold an impossible piecesCount.\n` +
      "A repair sets each to -1 ('not applicable'), which is what the field means when\n" +
      "absent and all that is recoverable — weightPerPiece was 0, so no real count can\n" +
      "be derived. It has to go in as targeted multi-path updates: the root-import route\n" +
      "closed on 2026-10-08 (doc/production-db-migration.md) and a root import now would\n" +
      "discard every order written since that snapshot."
);
