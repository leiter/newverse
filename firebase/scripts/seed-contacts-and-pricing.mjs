#!/usr/bin/env node
/**
 * Give the dev database the contact and pricing data the apps already read.
 *
 * Both features shipped as live code with no data behind them: `seller_profile` has
 * no `contacts` child, and no market has `contacts`, `priceConditions` or
 * `pricingRevision`. So `SellerContactDirectory` resolves empty, the buy app's
 * contact shortcut stays hidden exactly as designed, and `resolvePrice` returns the
 * base price for every article because `MarketPricing.isEmpty` is always true.
 * Nothing is broken; there is simply nothing to read. This writes a first set.
 *
 * Contacts go to two places, and both are needed. `getSellerContacts` prefers
 * `/markets/{sellerId}` and falls back to the profile's legacy `markets` list, but
 * when the buy app already holds the seller profile it returns
 * `SellerProfile.toContactDirectory()` instead, and that reads the legacy list. Write
 * only the node and a market's contacts appear or vanish depending on whether the
 * profile happens to be cached. The profile's own `contacts` child has one home.
 *
 * The phone contact is derived, not invented: `telephoneNumber` is already on both
 * profiles, and is the old single field that predates `SellerContact`, which is why
 * the shortcut cannot see it. Everything else is dev test data on `.example`, a
 * reserved domain that cannot resolve, so nothing here can reach a real person.
 *
 * Pricing is shaped to exercise the three rules in `resolvePrice` rather than to be
 * realistic:
 *
 *   - Fürstenwalde       one TRANSPORT surcharge on everything — the simple case.
 *   - Hansaviertel       a STALL_FEE factor on everything, a narrower STALL_FEE
 *                        factor on Obst, and a TRANSPORT surcharge on Kartoffeln.
 *                        Obst takes the 1.05 and not the 1.08, because the narrower
 *                        scope wins for one cause; Kartoffeln takes both, because
 *                        different causes stack.
 *   - Onkel Toms Hütte   nothing, deliberately. The unpriced path has to stay live:
 *                        it is what every market looks like today.
 *
 * Scopes name categories that dev articles actually carry (Obst ×5, Kartoffeln ×2) —
 * a scope matching nothing would apply to nothing and look like a broken engine.
 *
 * No condition takes a price below the seller's purchase price, so the editor's
 * below-cost warning stays unprovoked. That check reads seller-only data from
 * `/seller_articles` and belongs to a seller editing conditions, not to seed data.
 *
 * Admin credentials bypass the rules, so every record is validated here against
 * `firebase/database.rules.json` before it goes anywhere: a market that violates
 * them would be accepted over the CLI and then rejected when the seller next edited
 * it in the app, locking them out of their own market. Same failure the dayIndex
 * repair in migrate-markets-node.mjs exists to prevent.
 *
 * One field is repaired rather than added. `telephoneNumber` is a JSON number on one
 * dev profile, and every reader does `value["telephoneNumber"] as? String ?: ""`, so
 * that seller's phone number already reads back as empty in the app. Rewriting it as
 * the string it was meant to be loses nothing — nothing can currently read it — and
 * the contact derived from it would otherwise be skipped for that seller. Reported
 * under REPAIRED, never silent. Pass --no-repair to leave it alone.
 *
 * Add-only otherwise. A field already present was written by the app and is newer than
 * anything here; it is reported and skipped, never merged into or overwritten.
 * Writes go as one PATCH per seller keyed `{marketId}/{field}`, so a market this
 * script does not name is untouched.
 *
 * What it would write lives in seed-plan.mjs, as a pure function of the census, so
 * the rules tests in firebase/tests can assert against this exact payload instead of
 * a copy of it. seed-contacts-and-pricing.test.js is that test.
 *
 * Dry run by default. Pass --apply to write.
 *
 *   node seed-contacts-and-pricing.mjs --project fire-one-58ddc
 *   node seed-contacts-and-pricing.mjs --project fire-one-58ddc --apply
 *   node seed-contacts-and-pricing.mjs --from-export <export.json>
 *   node seed-contacts-and-pricing.mjs --project <id> --out plan.json
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { buildPlan, MARKETS_ROOT, PROFILE_ROOT } from "./seed-plan.mjs";

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const repair = !args.includes("--no-repair");
const flag = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : null);
const exportPath = flag("--from-export");
const outPath = flag("--out");
const project = flag("--project");

if (exportPath && apply) {
  console.error("--from-export is read-only; it cannot be combined with --apply.");
  process.exit(2);
}
if (!exportPath && (!project || project.startsWith("--"))) {
  console.error(
    "Usage: node seed-contacts-and-pricing.mjs --project <projectId> [--apply] [--no-repair]\n" +
    "       node seed-contacts-and-pricing.mjs --from-export <export.json>"
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

const census = {
  profiles: read(`/${PROFILE_ROOT}`) ?? {},
  markets: read(`/${MARKETS_ROOT}`) ?? {},
  articles: read("/articles") ?? {}
};

const plan = buildPlan(census, { repair });
const { marketPatches, profilePatches } = plan;

// --- report -------------------------------------------------------------------------

const list = (title, items) => {
  if (items.length === 0) return;
  console.log(`${title} (${items.length})`);
  for (const item of items) console.log(`  ${item}`);
  console.log();
};

list("PLANNED", plan.planned);
list("REPAIRED \u2014 wrong type, unreadable as stored", plan.repaired);
list("SKIPPED \u2014 already present, left alone", plan.skipped);
list("NOT FOUND", plan.missing);

if (plan.violations.length > 0) {
  list("RULE VIOLATIONS \u2014 nothing written", plan.violations);
  console.error("Refusing to write: admin credentials bypass the rules, so these would");
  console.error("be accepted here and then rejected when the seller edited them in the app.");
  process.exit(1);
}
console.log("Validated against firebase/database.rules.json: every record passes.\n");

if (outPath) {
  writeFileSync(outPath, JSON.stringify({
    [`/${MARKETS_ROOT}`]: Object.fromEntries(marketPatches),
    [`/${PROFILE_ROOT}`]: Object.fromEntries(profilePatches)
  }, null, 2) + "\n");
  console.log(`Wrote the planned patches to ${outPath}; nothing applied.`);
  process.exit(0);
}

if (!apply) {
  console.log(`Dry run: ${plan.fields} field(s) would be written. Pass --apply to write.`);
  process.exit(0);
}

// --- apply --------------------------------------------------------------------------

const scratch = mkdtempSync(join(tmpdir(), "seed-contacts-"));
const payloadFile = join(scratch, "payload.json");

const fbWrite = (cmdArgs) =>
  execFileSync("firebase", [...cmdArgs, "--project", project], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 64
  });

let written = 0;
try {
  for (const [root, patches] of [[MARKETS_ROOT, marketPatches], [PROFILE_ROOT, profilePatches]]) {
    for (const [sellerId, patch] of patches) {
      if (Object.keys(patch).length === 0) continue;
      writeFileSync(payloadFile, JSON.stringify(patch));
      // --force: the CLI refuses a write without it when stdin is not a terminal.
      fbWrite(["database:update", `/${root}/${sellerId}`, payloadFile, "--force"]);
      written += Object.keys(patch).length;
      console.log(`  wrote ${Object.keys(patch).length} field(s) to /${root}/${sellerId}`);
    }
  }
} finally {
  rmSync(scratch, { recursive: true, force: true });
}

console.log(`\nDone: ${written} field(s) written.`);
