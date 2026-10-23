#!/usr/bin/env node
/**
 * Record the lifecycle of orders whose pickup day is long past.
 *
 * Production's 48 orders carry no `status` field at all — they predate the field.
 * `storedOrderStatus` reads a missing status as PLACED rather than DRAFT, which is
 * right for display, but it also meant `countsAsCommitted()` saw every one of them as
 * goods the seller still owes. The refill list the seller buys from was inflated by
 * 21,435 units across 38 articles, including 21,161 Stück of eggs ordered for pickups
 * between March 2021 and January 2025.
 *
 * Nothing heals this on its own. Only the *buy* app calls
 * `Order.transitionStatusIfNeeded`, and only for an order the buyer actually opens, so
 * an order nobody reopens stays PLACED for ever.
 *
 * This writes `status = "COMPLETED"` on those orders, which is not an invention: it is
 * exactly the state the app's own machine reaches for a long-past order — PLACED ->
 * LOCKED once the edit deadline passes, LOCKED -> COMPLETED once the pickup date does.
 * The script only records what already happened.
 *
 * `status` is the only field written. One path per order, nothing else touched.
 *
 * What it refuses to touch, and why:
 *
 *   future pickup      genuinely outstanding — this is the demand the seller needs
 *   COMPLETED          already says so
 *   CANCELLED          ran its course the other way; never fulfilled
 *   NOT_PICKED_UP      a no-show is a fact about the order, not a missing status
 *   stored DRAFT       a draft is not an order yet. `storedOrderStatus` maps it to
 *                      PLACED on read, so it does inflate demand — but writing
 *                      COMPLETED onto a basket nobody placed would be a lie, and the
 *                      pickup-day guard in `countsAsCommitted` already excludes it.
 *                      Reported, never written. (Production has none.)
 *   origin DEMO        a legacy half-state under /orders, already hidden from both
 *                      the buyer's history and from demand
 *
 * "Pickup day has passed" is compared as whole local days, like the guard in
 * `countsAsCommitted`: `pickUpDate` is the pickup day at 00:00 local, so an instant
 * comparison would treat an order being collected this morning as past.
 *
 * This script uses the firebase CLI's admin credentials, which bypass rules.
 *
 * Dry run by default. Pass --apply to write.
 *
 *   node complete-collected-orders.mjs --project <projectId>           # report only
 *   node complete-collected-orders.mjs --project <projectId> --apply   # write
 *   node complete-collected-orders.mjs --from-export <export.json>     # offline check
 *
 * --from-export reads a database export from disk and never contacts a project.
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const DONE = "COMPLETED";
/** Statuses that already record an outcome; see the header. */
const SETTLED = new Set([DONE, "CANCELLED", "NOT_PICKED_UP"]);
/** Origins that count as a real order. A DEMO record under /orders is a half-state. */
const REAL = new Set([undefined, null, "PRODUCTION", "TRANSITIONED"]);

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const exportPath = args.includes("--from-export")
  ? args[args.indexOf("--from-export") + 1]
  : null;
const project = args.includes("--project") ? args[args.indexOf("--project") + 1] : null;
const zone = args.includes("--timezone")
  ? args[args.indexOf("--timezone") + 1]
  : "Europe/Berlin";

if (exportPath && apply) {
  console.error("--from-export is read-only; it cannot be combined with --apply.");
  process.exit(2);
}
if (!exportPath && (!project || project.startsWith("--"))) {
  console.error(
    "Usage: node complete-collected-orders.mjs --project <projectId> [--apply]\n" +
    "       node complete-collected-orders.mjs --from-export <export.json>\n" +
    "       [--timezone <IANA zone>]  (default Europe/Berlin, the seller's own)"
  );
  process.exit(2);
}

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
console.log(`Zone:   ${zone} (pickup days are compared in it)`);
console.log(apply ? "Mode:   APPLY (writing to the database)\n" : "Mode:   dry run (no writes)\n");

/** The local calendar day of an epoch-millis instant, as yyyy-mm-dd, in `zone`. */
const localDay = (millis) =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).format(new Date(millis));

const today = localDay(Date.now());

// --- classify every order ------------------------------------------------------

const orders = read("/orders") ?? {};
const planned = [];
const upcoming = [];
const settled = [];
const drafts = [];
const demo = [];
const undated = [];

for (const [sellerId, byDate] of Object.entries(orders)) {
  for (const [date, byOrder] of Object.entries(byDate ?? {})) {
    for (const [orderId, order] of Object.entries(byOrder ?? {})) {
      if (!order || typeof order !== "object") continue;
      const where = `${sellerId}/${date}/${orderId}`;
      const buyer = order?.buyerProfile?.displayName || order?.buyerId || "(unknown)";
      const status = order?.status;
      const origin = order?.origin;
      const row = { sellerId, date, orderId, where, buyer, status, origin };

      if (!REAL.has(origin)) { demo.push(row); continue; }
      if (SETTLED.has(status)) { settled.push(row); continue; }
      if (status === "DRAFT") { drafts.push(row); continue; }

      const pickUpDate = order.pickUpDate;
      if (typeof pickUpDate !== "number" || !Number.isFinite(pickUpDate) || pickUpDate <= 0) {
        // No pickup day to judge. The guard in countsAsCommitted already excludes it,
        // and there is nothing here to say what became of the order.
        undated.push(row);
        continue;
      }

      const day = localDay(pickUpDate);
      if (day >= today) { upcoming.push({ ...row, day }); continue; }
      planned.push({ ...row, day });
    }
  }
}

const total =
  planned.length + upcoming.length + settled.length + drafts.length + demo.length + undated.length;
console.log(`Scanned ${total} order(s). Today is ${today} in ${zone}.\n`);
console.log(`  ->  pickup day passed, to be ${DONE}   ${planned.length}`);
console.log(`  --  pickup still ahead, left alone     ${upcoming.length}`);
console.log(`  ok  already settled                    ${settled.length}`);
console.log(`  ?   stored DRAFT, left alone           ${drafts.length}`);
console.log(`  ?   DEMO under /orders, left alone     ${demo.length}`);
console.log(`  ?   no pickup date, left alone         ${undated.length}`);

if (planned.length > 0) {
  console.log(`\n${planned.length} order(s) to mark ${DONE}:`);
  for (const p of [...planned].sort((a, b) => a.day.localeCompare(b.day))) {
    const had = p.status === undefined ? "no status" : `status=${p.status}`;
    console.log(`  ${p.day}  ${p.where}\n        ${p.buyer} — ${had} -> ${DONE}`);
  }
}

for (const [label, rows] of [
  ["still ahead", upcoming],
  ["already settled", settled],
  ["stored DRAFT", drafts],
  ["DEMO under /orders", demo],
  ["no pickup date", undated]
]) {
  if (rows.length === 0) continue;
  console.log(`\n${rows.length} order(s) ${label}, untouched:`);
  for (const r of rows.slice(0, 20)) {
    console.log(`  ${r.where}  ${r.buyer}${r.day ? `  (${r.day})` : ""}`);
  }
  if (rows.length > 20) console.log(`  ... and ${rows.length - 20} more`);
}

if (planned.length === 0) {
  console.log("\nNothing to write.");
  process.exit(0);
}
if (!apply) {
  console.log("\nDry run. Re-run with --apply to write these statuses.");
  process.exit(0);
}

// One PATCH per seller. Keys are relative paths under /orders/{sellerId}, which is what
// makes this a multi-path update rather than a node overwrite: an order not named here
// is untouched, and only the `status` child of the ones named is written. All of a
// seller's orders land together or not at all.
//
// Written from a file rather than stdin: firebase-tools cannot stream a stdin body on
// Node 22, as fix-weight-per-piece.mjs documents.
const scratch = mkdtempSync(join(tmpdir(), "complete-orders-"));
const payloadFile = join(scratch, "payload.json");

let written = 0;
try {
  const bySeller = new Map();
  for (const p of planned) {
    if (!bySeller.has(p.sellerId)) bySeller.set(p.sellerId, {});
    bySeller.get(p.sellerId)[`${p.date}/${p.orderId}/status`] = DONE;
  }
  for (const [sellerId, update] of bySeller) {
    writeFileSync(payloadFile, JSON.stringify(update));
    execFileSync(
      "firebase",
      ["database:update", `/orders/${sellerId}`, payloadFile, "--project", project, "--force"],
      { encoding: "utf8" }
    );
    const paths = Object.keys(update).length;
    written += paths;
    console.log(`  ${sellerId}: ${paths} path(s) in one update`);
  }
} finally {
  rmSync(scratch, { recursive: true, force: true });
}
console.log(`\nWrote ${DONE} to ${written} order(s).`);
