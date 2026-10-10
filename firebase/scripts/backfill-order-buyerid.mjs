#!/usr/bin/env node
/**
 * Backfill `buyerId` onto orders written before the rules keyed on it.
 *
 * The rules gate a buyer's access to their own order on `buyerId`:
 *
 *   ".read": "auth != null && ($sellerId === auth.uid || data.child('buyerId').val() === auth.uid)"
 *
 * Orders written before that change have no `buyerId`, and `null === auth.uid` is
 * false, so once the rules are deployed every historical order becomes invisible to
 * the buyer who placed it. The seller keeps full access either way.
 *
 * The owner is recoverable: `buyer_profile/{authUid}/placedOrderIds` is a
 * `{yyyyMMdd: orderId}` map, so it names every order that buyer placed.
 *
 * This script uses the firebase CLI's admin credentials, which bypass rules, so it
 * works before or after the rules deploy. Run it *before* regardless, otherwise
 * buyers lose their order history for the window in between.
 *
 * Dry run by default. Pass --apply to write.
 *
 *   node backfill-order-buyerid.mjs --project <projectId>           # report only
 *   node backfill-order-buyerid.mjs --project <projectId> --apply   # write
 *   node backfill-order-buyerid.mjs --from-export <export.json>     # offline check
 *   node backfill-order-buyerid.mjs --from-export <in.json> --out <out.json>
 *
 * --from-export reads a database export from disk instead of the live database and
 * never touches a project. On its own it only reports; with --out it writes a copy of
 * the export with the buyerId values filled in, for importing later.
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const exportPath = args.includes("--from-export")
  ? args[args.indexOf("--from-export") + 1]
  : null;
const project = args.includes("--project") ? args[args.indexOf("--project") + 1] : null;
const outPath = args.includes("--out") ? args[args.indexOf("--out") + 1] : null;

if (exportPath && apply) {
  console.error("--from-export is read-only; it cannot be combined with --apply.");
  process.exit(2);
}
if (outPath && !exportPath) {
  console.error("--out writes a transformed export, so it requires --from-export.");
  process.exit(2);
}
if (!exportPath && (!project || project.startsWith("--"))) {
  console.error(
    "Usage: node backfill-order-buyerid.mjs --project <projectId> [--apply]\n" +
    "       node backfill-order-buyerid.mjs --from-export <export.json> [--out <out.json>]"
  );
  process.exit(2);
}

let read;
let snapshot = null;
if (exportPath) {
  snapshot = JSON.parse(readFileSync(exportPath, "utf8"));
  read = (path) => snapshot[path.replace(/^\//, "")] ?? null;
  console.log(`Source: ${exportPath} (offline)`);
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
  console.log(`Project: ${project}`);
}
console.log(
  apply ? "Mode:   APPLY (writing to the database)\n"
    : outPath ? `Mode:   rewrite export -> ${outPath}\n`
      : "Mode:   dry run (no writes)\n"
);

// --- index every order claimed by a buyer profile ------------------------------

const profiles = read("/buyer_profile") ?? {};
const owner = new Map(); // "yyyyMMdd/orderId" -> authUid
const contested = [];

for (const [authUid, profile] of Object.entries(profiles)) {
  for (const [day, orderId] of Object.entries(profile?.placedOrderIds ?? {})) {
    if (typeof orderId !== "string" || orderId === "") continue;
    const key = `${day}/${orderId}`;
    const existing = owner.get(key);
    if (existing && existing !== authUid) {
      // Two profiles naming the same order: guessing an owner here could hand one
      // buyer's order history to another, so leave it for a human.
      contested.push({ key, uids: [existing, authUid] });
      continue;
    }
    owner.set(key, authUid);
  }
}
console.log(
  `Indexed ${owner.size} order(s) across ${Object.keys(profiles).length} buyer profile(s).`
);

// --- walk the orders tree ------------------------------------------------------

const orders = read("/orders") ?? {};
const planned = [];
const alreadySet = [];
const mismatched = [];
const unclaimed = [];

for (const [sellerId, byDay] of Object.entries(orders)) {
  for (const [day, byOrder] of Object.entries(byDay ?? {})) {
    for (const [orderId, order] of Object.entries(byOrder ?? {})) {
      const path = `/orders/${sellerId}/${day}/${orderId}/buyerId`;
      const claimedBy = owner.get(`${day}/${orderId}`) ?? null;
      const current = order?.buyerId ?? null;

      if (current) {
        if (claimedBy && claimedBy !== current) {
          mismatched.push({ path, current, claimedBy });
        } else {
          alreadySet.push(path);
        }
        continue;
      }
      if (claimedBy) planned.push({ path, buyerId: claimedBy });
      else unclaimed.push(`${sellerId}/${day}/${orderId}`);
    }
  }
}

const total = planned.length + alreadySet.length + mismatched.length + unclaimed.length;
console.log(`Scanned ${total} order(s).\n`);

if (alreadySet.length > 0) {
  console.log(`${alreadySet.length} order(s) already carry a buyerId - skipped.`);
}

if (planned.length > 0) {
  console.log(`\n${planned.length} order(s) can be backfilled:`);
  for (const { path, buyerId } of planned) console.log(`  ${path} = ${buyerId}`);
}

if (unclaimed.length > 0) {
  console.log(`\n${unclaimed.length} order(s) are claimed by no buyer profile:`);
  for (const p of unclaimed) console.log(`  ${p}`);
  console.log(
    "\nThese belong to buyers whose profile is gone, or whose placedOrderIds never\n" +
    "recorded the order. After the rules deploy only the seller can read them, which\n" +
    "is the correct outcome - but check them before assuming so."
  );
}

if (contested.length > 0) {
  console.log(`\n${contested.length} order(s) are claimed by more than one profile:`);
  for (const { key, uids } of contested) console.log(`  ${key} <- ${uids.join(", ")}`);
  console.log("\nNot backfilled. Resolve by hand; a wrong guess leaks order history.");
}

if (mismatched.length > 0) {
  console.log(`\n${mismatched.length} order(s) already carry a DIFFERENT buyerId:`);
  for (const { path, current, claimedBy } of mismatched) {
    console.log(`  ${path}: has ${current}, profile claims ${claimedBy}`);
  }
  console.log("\nLeft untouched. An existing buyerId is never overwritten.");
}

if (planned.length === 0) {
  console.log("\nNothing to write.");
  process.exit(0);
}

if (outPath) {
  // Walk the in-memory snapshot and set each planned value, then write it back out.
  // Only the buyerId field is touched; every other node is copied through verbatim.
  for (const { path, buyerId } of planned) {
    const [, , sellerId, day, orderId] = path.split("/");
    snapshot.orders[sellerId][day][orderId].buyerId = buyerId;
  }
  writeFileSync(outPath, JSON.stringify(snapshot, null, 1) + "\n");
  console.log(`\nWrote ${planned.length} buyerId value(s) into ${outPath}.`);
  console.log("Nothing was sent to any Firebase project.");
  process.exit(0);
}

if (!apply) {
  console.log("\nDry run. Re-run with --apply to write these values,");
  console.log("or --out <file> to write them into a copy of the export.");
  process.exit(0);
}

// `database:set <path> -` reads the value from stdin, but firebase-tools 11.29.1
// cannot stream a stdin body on Node 22 - the PUT dies with "Failed to make request"
// while every other call on the same credentials succeeds. Writing from a real file
// takes a different path through the CLI and works.
const scratch = mkdtempSync(join(tmpdir(), "backfill-buyerid-"));
const valueFile = join(scratch, "value.json");

let written = 0;
try {
  for (const { path, buyerId } of planned) {
    writeFileSync(valueFile, JSON.stringify(buyerId));
    execFileSync(
      "firebase",
      ["database:set", path, valueFile, "--project", project, "--force"],
      { encoding: "utf8" }
    );
    written++;
  }
} finally {
  rmSync(scratch, { recursive: true, force: true });
}
console.log(`\nWrote ${written} buyerId value(s).`);
