#!/usr/bin/env node
/**
 * Move each seller's markets out of their profile and into a node of their own.
 *
 * They lived as `seller_profile/{sellerId}/markets`, a JSON *list*. A list index is
 * not a name, so nothing could hang off a single market: not pricing, not buyer
 * eligibility, not the rules that would let a buyer read one. This writes them to
 * `markets/{sellerId}/{marketId}`, keyed by the id the market already carries — see
 * doc/market-pricing-and-access-design.md §1.
 *
 * Add-only, and deliberately so: a market already present in the node was written by
 * the app, which does the same move on first load, and is newer than whatever the
 * profile list still holds. Those are reported and skipped, never merged into.
 *
 * The profile list is left in place. The apps read it as a fallback for one release,
 * so removing it here would strand anyone who has not updated. Delete it in a second
 * pass once the fallback comes out — pass --drop-legacy then.
 *
 * Markets carry their contacts, so those travel with them unchanged.
 *
 * dayIndex is repaired on the way through. The live dev data counts Sunday=1..
 * Saturday=7, while the app's market dialog has counted Monday=0..Sunday=6 for as long
 * as it has existed, so a Thursday market is stored as 5 and reads back as Saturday.
 * The new rules also cap the field at 6, so a 7 would be refused and the seller could
 * never edit that market again. dayOfWeek is the day a human typed and is therefore
 * authoritative: where the two disagree, the index is rebuilt from the name and the
 * correction reported. A record whose name is unrecognised keeps whatever index it has,
 * and is reported when that index is out of range.
 *
 * Every market needs an id, because it becomes the key. A list entry without one
 * cannot be moved and is reported rather than given a generated id: a fresh id would
 * not match the one an order or a closure already refers to.
 *
 * Writes go as one multi-path update per seller, keyed per field rather than per
 * market, so a field this script does not know about survives. Same shape as
 * ArticleNodes.saveUpdate and backfill-seller-articles.mjs.
 *
 * This script uses the firebase CLI's admin credentials, which bypass rules.
 *
 * Dry run by default. Pass --apply to write.
 *
 *   node migrate-markets-node.mjs --project <id>
 *   node migrate-markets-node.mjs --project <id> --apply
 *   node migrate-markets-node.mjs --from-export <export.json>
 *   node migrate-markets-node.mjs --project <id> --drop-legacy --apply
 *
 * --from-export reads the target state from a file instead of a live project, so the
 * whole plan can be checked without contacting anything.
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const PROFILE_ROOT = "seller_profile";
const MARKETS_ROOT = "markets";

/** Fields a market record may carry. Anything else in the source is reported. */
const KNOWN_FIELDS = [
  "id", "name", "street", "houseNumber", "city", "zipCode",
  "dayOfWeek", "begin", "end", "dayIndex", "active", "contacts"
];

/** The app's own order: the market dialog lists Monday first and stores 0..6. */
const DAY_NAMES = [
  ["montag", "monday"],
  ["dienstag", "tuesday"],
  ["mittwoch", "wednesday"],
  ["donnerstag", "thursday"],
  ["freitag", "friday"],
  ["samstag", "sonnabend", "saturday"],
  ["sonntag", "sunday"]
];

/** 0..6 for a day name in either locale, or null when it is not a day. */
const dayIndexFromName = (name) => {
  const needle = String(name ?? "").trim().toLowerCase();
  if (needle === "") return null;
  const found = DAY_NAMES.findIndex((names) => names.includes(needle));
  return found === -1 ? null : found;
};

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const dropLegacy = args.includes("--drop-legacy");
const flag = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : null);
const exportPath = flag("--from-export");
const project = flag("--project");

if (exportPath && apply) {
  console.error("--from-export is read-only; it cannot be combined with --apply.");
  process.exit(2);
}
if (!exportPath && (!project || project.startsWith("--"))) {
  console.error(
    "Usage: node migrate-markets-node.mjs --project <projectId> [--apply] [--drop-legacy]\n" +
    "       node migrate-markets-node.mjs --from-export <export.json>"
  );
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
console.log(apply ? "Mode:   APPLY (writing to the database)\n" : "Mode:   dry run (no writes)\n");

const profiles = read(`/${PROFILE_ROOT}`) ?? {};
const liveMarkets = read(`/${MARKETS_ROOT}`) ?? {};

// --- plan ------------------------------------------------------------------------

const planned = [];   // { sellerId, marketId, name, fields }
const occupied = [];  // already in the node
const unnamed = [];   // no id, so no key
const extras = [];    // field this script does not know about
const reindexed = []; // dayIndex rebuilt from dayOfWeek
const badIndex = [];  // out of range and no day name to rebuild from
const trimmed = [];   // leading or trailing whitespace removed

/** RTDB returns a sparse list as an index-keyed map, so accept both shapes. */
const rows = (value) => {
  if (Array.isArray(value)) return value.filter((v) => v != null);
  if (value && typeof value === "object") return Object.values(value);
  return [];
};

for (const [sellerId, profile] of Object.entries(profiles)) {
  const legacy = rows(profile?.markets);
  if (legacy.length === 0) continue;

  const already = liveMarkets[sellerId] ?? {};

  legacy.forEach((market, index) => {
    if (!market || typeof market !== "object") return;

    const marketId = typeof market.id === "string" ? market.id.trim() : "";
    const name = market.name ?? "(unnamed)";

    if (marketId === "") {
      unnamed.push({ sellerId, index, name });
      return;
    }
    if (already[marketId] !== undefined) {
      occupied.push({ sellerId, marketId, name });
      return;
    }

    for (const key of Object.keys(market)) {
      if (!KNOWN_FIELDS.includes(key)) extras.push({ sellerId, marketId, key });
    }

    // active is absent on every legacy record: they all predate soft deletion, and
    // a market in the list was by definition one the seller still ran.
    const fields = { active: true };
    for (const key of KNOWN_FIELDS) {
      if (market[key] !== undefined) fields[key] = market[key];
    }
    fields.id = marketId;

    // Whitespace a seller typed into a name or an address, which then shows up in
    // the buy app exactly as stored.
    for (const key of ["name", "street", "houseNumber", "city", "zipCode", "dayOfWeek"]) {
      if (typeof fields[key] !== "string") continue;
      const tidy = fields[key].trim();
      if (tidy !== fields[key]) {
        trimmed.push({ sellerId, marketId, key, from: fields[key], to: tidy });
        fields[key] = tidy;
      }
    }

    const byName = dayIndexFromName(fields.dayOfWeek);
    const stored = typeof fields.dayIndex === "number" ? fields.dayIndex : null;
    if (byName !== null && stored !== byName) {
      reindexed.push({ sellerId, marketId, day: fields.dayOfWeek, from: stored, to: byName });
      fields.dayIndex = byName;
    } else if (byName === null && (stored === null || stored < -1 || stored > 6)) {
      badIndex.push({ sellerId, marketId, day: fields.dayOfWeek, stored });
    }

    planned.push({ sellerId, marketId, name: fields.name ?? name, fields });
  });
}

const legacyCount = Object.values(profiles)
  .reduce((n, p) => n + rows(p?.markets).length, 0);

console.log(`${legacyCount} market(s) in seller profiles.\n`);
console.log(`  +  to move                     ${planned.length}`);
console.log(`  =  already in the node, skipped ${occupied.length}`);
console.log(`  !  no id, cannot be keyed      ${unnamed.length}`);

if (planned.length > 0) {
  console.log("\nMoving:");
  for (const { sellerId, marketId, name, fields } of planned) {
    const day = fields.dayOfWeek ? `  ${fields.dayOfWeek}` : "";
    const hours = fields.begin && fields.end ? ` ${fields.begin}-${fields.end}` : "";
    const contacts = rows(fields.contacts).length;
    console.log(
      `  ${sellerId}/${marketId}  ${name}${day}${hours}` +
      (contacts > 0 ? `  (${contacts} contact${contacts === 1 ? "" : "s"})` : "")
    );
  }
}

if (occupied.length > 0) {
  console.log(`\n${occupied.length} market(s) are already in the node:`);
  for (const { sellerId, marketId, name } of occupied) {
    console.log(`  ${sellerId}/${marketId}  ${name}`);
  }
  console.log(
    "\nSkipped, not merged into. The app performs the same move on first load, so a\n" +
    "record already there is the seller's current one and the profile list is stale."
  );
}

if (unnamed.length > 0) {
  console.log(`\n${unnamed.length} market(s) have no id and cannot be moved:`);
  for (const { sellerId, index, name } of unnamed) {
    console.log(`  ${sellerId} index ${index}  ${name}`);
  }
  console.log(
    "\nNot written. The id becomes the key, and minting a fresh one would not match\n" +
    "what an order or a closure already refers to. Give them an id in the app first."
  );
}

if (reindexed.length > 0) {
  console.log(`\n${reindexed.length} market(s) had a dayIndex that disagreed with their day name:`);
  for (const { sellerId, marketId, day, from, to } of reindexed) {
    console.log(`  ${sellerId}/${marketId}  ${day}: ${from} -> ${to}`);
  }
  console.log(
    "\nRebuilt from the name, which is what a human typed. The app counts Monday=0,\n" +
    "and the new rules refuse anything above 6, so an index of 7 would also have made\n" +
    "the market uneditable."
  );
}

if (badIndex.length > 0) {
  console.log(`\n${badIndex.length} market(s) have an unusable dayIndex and no day name to rebuild from:`);
  for (const { sellerId, marketId, day, stored } of badIndex) {
    console.log(`  ${sellerId}/${marketId}  dayOfWeek=${JSON.stringify(day)} dayIndex=${stored}`);
  }
  console.log("\nWritten as they are, but the rules will refuse the seller's next edit.");
}

if (trimmed.length > 0) {
  console.log(`\n${trimmed.length} field(s) had surrounding whitespace removed:`);
  for (const { sellerId, marketId, key, from, to } of trimmed) {
    console.log(`  ${sellerId}/${marketId}  ${key}: ${JSON.stringify(from)} -> ${JSON.stringify(to)}`);
  }
}

if (extras.length > 0) {
  const unique = [...new Set(extras.map((e) => e.key))];
  console.log(`\nUnknown field(s) in the source, not carried over: ${unique.join(", ")}`);
  console.log("The rules reject unknown fields on a market, so they would be refused anyway.");
}

if (planned.length === 0 && !dropLegacy) {
  console.log("\nNothing to write.");
  process.exit(0);
}

if (dropLegacy) {
  const sellers = [...new Set(Object.entries(profiles)
    .filter(([, p]) => rows(p?.markets).length > 0)
    .map(([sellerId]) => sellerId))];
  console.log(`\n--drop-legacy: will also clear seller_profile/<id>/markets for ${sellers.length} seller(s).`);
  console.log("Only safe once no released app still reads the profile list as a fallback.");
}

if (!apply) {
  console.log("\nDry run. Re-run with --apply to write.");
  process.exit(0);
}

// --- apply -----------------------------------------------------------------------

// One PATCH per seller against /markets/<sellerId>, keyed `{marketId}/{field}`, so a
// field written by a newer app version between the census and now is left alone and a
// market the plan does not name is untouched. `database:set <path> -` cannot stream a
// stdin body on Node 22 with firebase-tools 11.29.1, so the payload goes via a file.
const scratch = mkdtempSync(join(tmpdir(), "migrate-markets-"));
const payloadFile = join(scratch, "payload.json");

const fbWrite = (cmdArgs) =>
  execFileSync("firebase", [...cmdArgs, "--project", project], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 64
  });

let written = 0;
try {
  const bySeller = new Map();
  for (const { sellerId, marketId, fields } of planned) {
    const patch = bySeller.get(sellerId) ?? {};
    for (const [key, value] of Object.entries(fields)) {
      patch[`${marketId}/${key}`] = value;
    }
    bySeller.set(sellerId, patch);
  }

  for (const [sellerId, patch] of bySeller) {
    writeFileSync(payloadFile, JSON.stringify(patch));
    fbWrite(["database:update", `/${MARKETS_ROOT}/${sellerId}`, payloadFile]);
    const count = new Set(Object.keys(patch).map((k) => k.split("/")[0])).size;
    written += count;
    console.log(`  wrote ${count} market(s) for ${sellerId}`);
  }

  if (dropLegacy) {
    for (const [sellerId, profile] of Object.entries(profiles)) {
      if (rows(profile?.markets).length === 0) continue;
      fbWrite(["database:remove", `/${PROFILE_ROOT}/${sellerId}/markets`, "--force"]);
      console.log(`  cleared the profile list for ${sellerId}`);
    }
  }
} finally {
  rmSync(scratch, { recursive: true, force: true });
}

console.log(`\nDone. ${written} market(s) written to /${MARKETS_ROOT}.`);
