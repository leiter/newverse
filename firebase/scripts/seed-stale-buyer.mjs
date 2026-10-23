#!/usr/bin/env node
/**
 * Give one buyer account a synthetic past: a profile plus a few long-collected orders.
 *
 * For testing the returning-buyer path — a signed-in account that is NOT approved by
 * the seller, so it sits in demo mode, but which has real history under /orders. That
 * combination is what commit ba7b7ac made visible and it cannot be reached by using
 * the app, because nothing in the app writes a past order.
 *
 * Why this synthesises rather than re-points an existing buyer. Substituting a real
 * customer's uid looks cheaper and is not:
 *
 *   - Every order embeds its own copy of the buyer under `buyerProfile`, carrying
 *     displayName, emailAddress and telephoneNumber. Re-pointing the uid alone would
 *     show the test account another real customer's name, e-mail and phone number.
 *     Avoiding that means scrubbing the donor out of every order anyway, at which
 *     point nothing of the donor is left but the shape.
 *   - It consumes the donor: their profile is gone and their history now belongs to
 *     someone else. Recoverable only from a backup.
 *   - It is not idempotent. Re-running cannot find the donor again, because the uid
 *     it keyed on no longer exists.
 *
 * Synthesising touches no existing record, invents no third party, and is undone by
 * deleting exactly what it wrote — which --revert does.
 *
 * Line items are drawn from the seller's live catalogue so prices and units are real;
 * articles are not personal data. Orders are written COMPLETED with a pickup date in
 * the past, which is what a collected order looks like and keeps them out of
 * `committedDemand` (see countsAsCommitted).
 *
 * Nothing here is a real person's data. The buyer identity written is the one passed
 * on the command line, and it is written identically into the profile and into every
 * order, so the record is self-consistent.
 *
 *   node seed-stale-buyer.mjs --project <id> --uid <authUid> --email <addr> \
 *        [--name "<display name>"] [--orders 4] [--apply]
 *   node seed-stale-buyer.mjs --project <id> --uid <authUid> --revert [--apply]
 *
 * The uid must be the uid Firebase Auth already assigned to that account, or the app
 * will never read the profile. Dry run by default; --apply writes.
 *
 * Uses the firebase CLI's admin credentials, which bypass database rules.
 */
import { execFileSync } from "node:child_process";
import { writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const val = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : null);

const project = val("--project");
const uid = val("--uid");
const email = val("--email");
const name = val("--name") ?? "Annabel";
const count = Number(val("--orders") ?? 4);
const apply = flag("--apply");
const revert = flag("--revert");

if (!project || !uid || (!revert && !email)) {
  console.error(
    "Usage: node seed-stale-buyer.mjs --project <id> --uid <authUid> --email <addr>\n" +
    "                                 [--name \"<display name>\"] [--orders N] [--apply]\n" +
    "       node seed-stale-buyer.mjs --project <id> --uid <authUid> --revert [--apply]"
  );
  process.exit(2);
}
if (!revert && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
  console.error(`--email ${email} does not look like an address.`);
  process.exit(2);
}
if (!revert && (!Number.isInteger(count) || count < 1 || count > 20)) {
  console.error("--orders must be a whole number between 1 and 20.");
  process.exit(2);
}

const fb = (a) =>
  execFileSync("firebase", [...a, "--project", project], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 256
  });
const read = (path) => {
  const out = fb(["database:get", path]).trim();
  return out === "null" || out === "" ? null : JSON.parse(out);
};

console.log(`Project: ${project}`);
console.log(`Buyer:   ${uid}`);
console.log(apply ? "Mode:    APPLY (writing)\n" : "Mode:    dry run (no writes)\n");

// The seller is whoever owns the catalogue. Exactly one, by design.
const sellerIds = Object.keys(read("/articles") ?? {});
if (sellerIds.length !== 1) {
  console.error(`Expected exactly one seller under /articles, found ${sellerIds.length}.`);
  process.exit(1);
}
const sellerId = sellerIds[0];
console.log(`Seller:  ${sellerId}`);

const existingProfile = read(`/buyer_profile/${uid}`);

// --- revert -------------------------------------------------------------------

if (revert) {
  const placed = existingProfile?.placedOrderIds ?? {};
  const paths = Object.entries(placed).map(([date, orderId]) => `/orders/${sellerId}/${date}/${orderId}`);
  console.log(`\nWould delete /buyer_profile/${uid} and ${paths.length} order(s):`);
  for (const p of paths) console.log(`  ${p}`);
  if (!paths.length && !existingProfile) {
    console.log("\nNothing to remove.");
    process.exit(0);
  }
  if (!apply) {
    console.log("\nDry run. Re-run with --apply to delete.");
    process.exit(0);
  }
  for (const p of paths) fb(["database:remove", p, "--force"]);
  fb(["database:remove", `/buyer_profile/${uid}`, "--force"]);
  console.log(`\nRemoved the profile and ${paths.length} order(s).`);
  process.exit(0);
}

// --- pick a market and some articles -------------------------------------------

const markets = Object.values(read(`/markets/${sellerId}`) ?? {}).filter((m) => m?.active !== false);
if (!markets.length) {
  console.error(`No active market under /markets/${sellerId}; an order needs a marketId that resolves.`);
  process.exit(1);
}
const market = markets[0];

const catalogue = Object.entries(read(`/articles/${sellerId}`) ?? {})
  .map(([id, a]) => ({ id, ...a }))
  .filter((a) => typeof a.price === "number" && a.price > 0 && a.productName);
if (catalogue.length < 2) {
  console.error("Need at least two priced articles in the catalogue to build an order.");
  process.exit(1);
}

// Deterministic, so a re-run with the same arguments plans the same orders.
let seed = [...uid].reduce((h, c) => (h * 31 + c.charCodeAt(0)) >>> 0, 7);
const rnd = () => ((seed = (seed * 1103515245 + 12345) >>> 0) / 4294967296);
const pick = (xs) => xs[Math.floor(rnd() * xs.length)];

/** Past dates landing on this market's weekday: dayIndex counts Monday as 0. */
const pickupDays = (n) => {
  const out = [];
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  const target = ((market.dayIndex ?? 3) + 1) % 7; // JS: Sunday 0
  d.setDate(d.getDate() - 7); // start a clear week back, never today
  while (out.length < n) {
    if (d.getDay() === target) out.push(new Date(d));
    d.setDate(d.getDate() - 1);
  }
  return out;
};

const key = (d) =>
  `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}`;

const buyerProfile = {
  id: uid,
  displayName: name,
  emailAddress: email,
  telephoneNumber: "",
  photoUrl: "",
  anonymous: false,
  defaultMarket: market.id,
  defaultPickUpTime: market.begin ?? ""
};

const placedOrderIds = {};
const orders = [];

for (const day of pickupDays(count)) {
  const date = key(day);
  // Same id shape the app would have produced, but stamped so it is obviously seeded
  // and so a re-run replaces its own rows instead of piling up new ones.
  const orderId = `seed-${uid.slice(0, 8)}-${date}`;
  const lines = [];
  const used = new Set();
  const lineCount = 2 + Math.floor(rnd() * 3);
  while (lines.length < lineCount) {
    const a = pick(catalogue);
    if (used.has(a.id)) continue;
    used.add(a.id);
    const byPiece = /st(ü|u)ck/i.test(a.unit ?? "");
    const qty = byPiece ? 1 + Math.floor(rnd() * 4) : Math.round((0.5 + rnd() * 2) * 1000) / 1000;
    lines.push({
      id: "",
      productId: a.id,
      productName: a.productName,
      unit: a.unit ?? "",
      price: a.price,
      amount: byPiece ? `${qty} ${a.unit}` : `${qty.toFixed(3).replace(".", ",")} ${a.unit}`,
      amountCount: qty,
      piecesCount: byPiece ? qty : -1,
      mode: -1
    });
  }
  const pickUpDate = day.getTime();
  orders.push({
    path: `/orders/${sellerId}/${date}/${orderId}`,
    date,
    orderId,
    value: {
      id: orderId,
      sellerId,
      buyerId: uid,
      buyerProfile,
      marketId: market.id,
      createdDate: pickUpDate - 2 * 86400_000,
      pickUpDate,
      message: "",
      notFavourite: true,
      // Collected long ago: this is what makes it history rather than demand.
      status: "COMPLETED",
      origin: "PRODUCTION",
      fulfilment: "PICKUP",
      articles: lines
    }
  });
  placedOrderIds[date] = orderId;
}

// --- report --------------------------------------------------------------------

console.log(`Market:  ${market.name} (${market.id}), ${market.dayOfWeek}\n`);
if (existingProfile) {
  const had = Object.keys(existingProfile.placedOrderIds ?? {}).length;
  console.log(`/buyer_profile/${uid} already exists (${had} order(s) indexed).`);
  console.log("It will be overwritten with the identity below.\n");
}
console.log(`/buyer_profile/${uid}`);
console.log(`  displayName   ${name}`);
console.log(`  emailAddress  ${email}`);
console.log(`  anonymous     false`);
console.log(`  defaultMarket ${market.id}`);
console.log(`  placedOrderIds ${Object.keys(placedOrderIds).length} entries\n`);
console.log(`${orders.length} order(s):`);
for (const o of orders) {
  const total = o.value.articles.reduce((s, l) => s + l.price * l.amountCount, 0);
  console.log(`  ${o.date}  ${o.orderId}`);
  console.log(`        ${o.value.articles.length} line(s), ${total.toFixed(2)} EUR, COMPLETED`);
  for (const l of o.value.articles) {
    console.log(`          ${l.amount.padEnd(14)} ${l.productName} @ ${l.price.toFixed(2)}`);
  }
}

if (!apply) {
  console.log("\nDry run. Re-run with --apply to write. Undo later with --revert.");
  process.exit(0);
}

// One multi-path update per node, so the orders land together and the profile's index
// is never written without the orders it points at.
const scratch = mkdtempSync(join(tmpdir(), "seed-stale-buyer-"));
const payload = join(scratch, "payload.json");
const write = (path, body) => {
  writeFileSync(payload, JSON.stringify(body));
  fb(["database:update", path, payload, "--force"]);
};
try {
  const orderUpdate = {};
  for (const o of orders) orderUpdate[`${o.date}/${o.orderId}`] = o.value;
  write(`/orders/${sellerId}`, orderUpdate);
  console.log(`\n  wrote ${orders.length} order(s)`);
  write(`/buyer_profile/${uid}`, { ...buyerProfile, placedOrderIds });
  console.log(`  wrote /buyer_profile/${uid}`);
} finally {
  rmSync(scratch, { recursive: true, force: true });
}
console.log("\nDone. The account now has history and is still unapproved, so it opens in demo mode.");
