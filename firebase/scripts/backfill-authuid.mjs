#!/usr/bin/env node
/**
 * Backfill `authUID` onto access records written before the rules keyed on it.
 *
 * The hardened rules bind a buyer to their access records through `authUID`,
 * because `buyerUUID` lives in the buyer's own profile and can be set to any
 * value. Records written before that change have no `authUID`, so after the
 * deploy their owner can no longer read or withdraw them. This fills it in.
 *
 * Sources, in order of preference:
 *   1. buyer_access_status/{sellerId}/{buyerUUID}/authUID - written by
 *      submitAccessRequest since well before the rules change.
 *   2. a buyerUUID -> uid map built from buyer_profile.
 *
 * Dry run by default. Pass --apply to write.
 *
 *   node backfill-authuid.mjs --project <projectId>          # report only
 *   node backfill-authuid.mjs --project <projectId> --apply  # write
 *
 * Uses the firebase CLI's existing login; no service account needed.
 */
import { execFileSync } from "node:child_process";

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const project = args[args.indexOf("--project") + 1];

if (!args.includes("--project") || !project || project.startsWith("--")) {
  console.error("Usage: node backfill-authuid.mjs --project <projectId> [--apply]");
  process.exit(2);
}

const fb = (cmdArgs) =>
  execFileSync("firebase", [...cmdArgs, "--project", project], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 256
  });

const read = (path) => {
  const out = fb(["database:get", path]).trim();
  return out === "null" || out === "" ? null : JSON.parse(out);
};

console.log(`Project: ${project}`);
console.log(apply ? "Mode:    APPLY (writing)\n" : "Mode:    dry run (no writes)\n");

const profiles = read("/buyer_profile") ?? {};
const uuidToUid = new Map();
for (const [uid, profile] of Object.entries(profiles)) {
  const uuid = profile?.buyerUUID;
  if (uuid) uuidToUid.set(uuid, uid);
}
console.log(`Indexed ${uuidToUid.size} buyer profiles carrying a buyerUUID.`);

const statuses = read("/buyer_access_status") ?? {};
const requests = read("/access_requests") ?? {};

const planned = [];
const unresolved = [];

const resolve = (buyerUUID, sellerId) =>
  statuses[sellerId]?.[buyerUUID]?.authUID || uuidToUid.get(buyerUUID) || null;

for (const [node, tree] of [["buyer_access_status", statuses], ["access_requests", requests]]) {
  for (const [sellerId, byUuid] of Object.entries(tree)) {
    for (const [buyerUUID, record] of Object.entries(byUuid ?? {})) {
      if (record?.authUID) continue;
      const authUID = resolve(buyerUUID, sellerId);
      if (authUID) {
        planned.push({ path: `/${node}/${sellerId}/${buyerUUID}/authUID`, authUID });
      } else {
        unresolved.push(`${node}/${sellerId}/${buyerUUID}`);
      }
    }
  }
}

if (planned.length === 0 && unresolved.length === 0) {
  console.log("\nNothing to backfill - every access record already carries an authUID.");
  process.exit(0);
}

console.log(`\n${planned.length} record(s) can be backfilled:`);
for (const { path, authUID } of planned) console.log(`  ${path} = ${authUID}`);

if (unresolved.length > 0) {
  console.log(`\n${unresolved.length} record(s) cannot be resolved to a uid:`);
  for (const p of unresolved) console.log(`  ${p}`);
  console.log(
    "\nThese belong to buyers whose profile is gone, so nobody can claim them.\n" +
    "After the deploy only the seller will be able to read or clear them, which\n" +
    "is the correct outcome - but review them before assuming so."
  );
}

if (!apply) {
  console.log("\nDry run. Re-run with --apply to write these values.");
  process.exit(0);
}

let written = 0;
for (const { path, authUID } of planned) {
  execFileSync("firebase", ["database:set", path, "-", "--project", project, "--force"], {
    input: JSON.stringify(authUID),
    encoding: "utf8"
  });
  written++;
}
console.log(`\nWrote ${written} authUID value(s).`);
