#!/usr/bin/env node
/**
 * Make every past buyer arrive as a new joiner, and take their identity out of the orders.
 *
 * Two separate jobs, because the personal data is in two places:
 *
 *  1. Profiles and access. Deleting /buyer_profile/{uid} IS the reset: the Firebase Auth
 *     account survives, so the same person signs back in, gets the same uid, and the app
 *     builds them a fresh profile. Their access record goes too, so they arrive
 *     unapproved — in demo mode, as a stranger. Anonymous accounts are deleted on the
 *     same terms; they are already orphans in practice, since an anonymous account dies
 *     with its local token and allowBackup=false means a reinstall mints a new uid.
 *
 *  2. Orders. Deleting a profile removes nothing from an order: every one carries its own
 *     copy of the buyer under `buyerProfile` — displayName, emailAddress,
 *     telephoneNumber — plus a top-level `buyerId`. That copy is the real exposure (see
 *     doc/reports/buyer-profile-data-safety.md) and it outlives the profile.
 *
 * Orders are scrubbed, not deleted. The transaction — articles, quantities, prices,
 * market, dates, status — is a business record worth keeping for the CSV tax export and
 * for any retention obligation; the name attached to it is not. Dropping `buyerId` is
 * what makes an order unattributable: the buyer read rule grants access on
 * `buyerId === auth.uid`, so with it gone no buyer can read the order and none of them
 * sees a history. The seller still reads the whole tree by seller id, so the books are
 * untouched.
 *
 * A blanked displayName is deliberate rather than a placeholder string: the app already
 * renders a nameless buyer as "Bodenschatz", so the scrub leaves the naming to it.
 *
 * Demo orders are deleted outright, not scrubbed. They are throwaway by definition, and
 * /demo_orders/{sellerId} is readable by *any* signed-in user, so personal data sitting
 * there is worse than the same data under /orders.
 *
 * Idempotent: a second run finds nothing left to do.
 *
 *   node reset-buyers-to-new-joiners.mjs --project <id> [--keep <uid>]... [--apply]
 *
 * The seller's own buyer profile is kept automatically. Dry run by default.
 * Uses the firebase CLI's admin credentials, which bypass database rules.
 */
import { execFileSync } from "node:child_process";
import { writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const project = args.includes("--project") ? args[args.indexOf("--project") + 1] : null;
const keep = new Set();
args.forEach((a, i) => { if (a === "--keep" && args[i + 1]) keep.add(args[i + 1]); });

if (!project || project.startsWith("--")) {
  console.error("Usage: node reset-buyers-to-new-joiners.mjs --project <id> [--keep <uid>]... [--apply]");
  process.exit(2);
}

const fb = (a) => execFileSync("firebase", [...a, "--project", project],
  { encoding: "utf8", maxBuffer: 1024 * 1024 * 256 });
const read = (p) => {
  const out = fb(["database:get", p]).trim();
  return out === "null" || out === "" ? null : JSON.parse(out);
};

console.log(`Project: ${project}`);
console.log(apply ? "Mode:    APPLY (writing)\n" : "Mode:    dry run (no writes)\n");

const sellerIds = Object.keys(read("/articles") ?? {});
if (sellerIds.length !== 1) {
  console.error(`Expected exactly one seller under /articles, found ${sellerIds.length}.`);
  process.exit(1);
}
const sellerId = sellerIds[0];
keep.add(sellerId); // the seller's own buyer profile is theirs, not a customer's
console.log(`Seller:  ${sellerId}`);
console.log(`Keeping: ${[...keep].join(", ")}\n`);

// What the app itself writes and reads: orderToMap stores these flat on the order.
const ORDER_IDENTITY = ["buyerName", "buyerEmail"];
// What production's legacy orders carry instead, under a nested `buyerProfile`. No code
// reads it, but the personal data in it is just as real, so both shapes are scrubbed.
const IDENTITY = ["displayName", "emailAddress", "telephoneNumber", "photoUrl", "id"];

// --- plan ---------------------------------------------------------------------

const profiles = read("/buyer_profile") ?? {};
const access = read(`/buyer_access_status/${sellerId}`) ?? {};
const requests = read(`/access_requests/${sellerId}`) ?? {};
const orders = read(`/orders/${sellerId}`) ?? {};
const demoOrders = read(`/demo_orders/${sellerId}`) ?? {};

const profileDeletes = [];
let anonCount = 0, namedCount = 0;
for (const [uid, p] of Object.entries(profiles)) {
  if (keep.has(uid)) continue;
  (p?.anonymous === true ? anonCount++ : namedCount++);
  profileDeletes.push(uid);
}
const accessDeletes = Object.keys(access).filter((u) => !keep.has(u));
const requestDeletes = Object.keys(requests).filter((u) => !keep.has(u));

const orderScrubs = [];
let alreadyClean = 0, keptOrders = 0;
for (const [date, byOrder] of Object.entries(orders)) {
  for (const [orderId, o] of Object.entries(byOrder ?? {})) {
    if (!o || typeof o !== "object") continue;
    if (o.buyerId && keep.has(o.buyerId)) { keptOrders++; continue; }
    const bp = o.buyerProfile ?? {};
    const dirty = o.buyerId !== undefined ||
      ORDER_IDENTITY.some((f) => o[f] !== undefined && o[f] !== "") ||
      IDENTITY.some((f) => bp[f] !== undefined && bp[f] !== "") ||
      bp.placedOrderIds !== undefined;
    if (!dirty) { alreadyClean++; continue; }
    orderScrubs.push({ date, orderId });
  }
}

const demoDeletes = [];
for (const [date, byOrder] of Object.entries(demoOrders)) {
  for (const [orderId, o] of Object.entries(byOrder ?? {})) {
    if (o?.buyerId && keep.has(o.buyerId)) continue;
    demoDeletes.push({ date, orderId });
  }
}

// --- report (uids and counts only; no names, no addresses) ----------------------

console.log("buyer_profile");
console.log(`  delete  ${profileDeletes.length}  (${anonCount} anonymous, ${namedCount} named)`);
console.log(`  keep    ${Object.keys(profiles).length - profileDeletes.length}`);
console.log("buyer_access_status");
console.log(`  delete  ${accessDeletes.length}`);
console.log("access_requests");
console.log(`  delete  ${requestDeletes.length}`);
console.log("orders");
console.log(`  scrub identity from  ${orderScrubs.length}`);
console.log(`  already clean        ${alreadyClean}`);
console.log(`  kept (excluded uid)  ${keptOrders}`);
console.log("demo_orders");
console.log(`  delete  ${demoDeletes.length}`);

if (profileDeletes.length) {
  console.log(`\nprofiles to delete:\n  ${profileDeletes.join("\n  ")}`);
}
if (orderScrubs.length) {
  console.log(`\norders to scrub:`);
  for (const s of orderScrubs) console.log(`  ${s.date}/${s.orderId}`);
}

const nothing = !profileDeletes.length && !accessDeletes.length && !requestDeletes.length &&
  !orderScrubs.length && !demoDeletes.length;
if (nothing) { console.log("\nNothing to do."); process.exit(0); }
if (!apply) { console.log("\nDry run. Re-run with --apply to write."); process.exit(0); }

// --- apply ---------------------------------------------------------------------

const scratch = mkdtempSync(join(tmpdir(), "reset-buyers-"));
const payload = join(scratch, "payload.json");
const update = (path, body) => {
  writeFileSync(payload, JSON.stringify(body));
  fb(["database:update", path, payload, "--force"]);
};

try {
  if (orderScrubs.length || demoDeletes.length) {
    const u = {};
    for (const { date, orderId } of orderScrubs) {
      u[`${date}/${orderId}/buyerId`] = null;
      for (const f of ORDER_IDENTITY) u[`${date}/${orderId}/${f}`] = "";
      u[`${date}/${orderId}/buyerProfile/placedOrderIds`] = null;
      for (const f of IDENTITY) u[`${date}/${orderId}/buyerProfile/${f}`] = "";
    }
    if (Object.keys(u).length) {
      update(`/orders/${sellerId}`, u);
      console.log(`  scrubbed ${orderScrubs.length} order(s)`);
    }
    if (demoDeletes.length) {
      const d = {};
      for (const { date, orderId } of demoDeletes) d[`${date}/${orderId}`] = null;
      update(`/demo_orders/${sellerId}`, d);
      console.log(`  deleted ${demoDeletes.length} demo order(s)`);
    }
  }
  if (profileDeletes.length) {
    const p = {};
    for (const uid of profileDeletes) p[uid] = null;
    update("/buyer_profile", p);
    console.log(`  deleted ${profileDeletes.length} profile(s)`);
  }
  if (accessDeletes.length) {
    const a = {};
    for (const uid of accessDeletes) a[uid] = null;
    update(`/buyer_access_status/${sellerId}`, a);
    console.log(`  deleted ${accessDeletes.length} access record(s)`);
  }
  if (requestDeletes.length) {
    const r = {};
    for (const uid of requestDeletes) r[uid] = null;
    update(`/access_requests/${sellerId}`, r);
    console.log(`  deleted ${requestDeletes.length} access request(s)`);
  }
} finally {
  rmSync(scratch, { recursive: true, force: true });
}
console.log("\nDone. Every remaining buyer arrives unapproved, with no profile and no history.");
