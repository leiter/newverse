#!/usr/bin/env node
/**
 * Rename `weighPerPiece` to `weightPerPiece` on live articles.
 *
 * Production only ever wrote `weighPerPiece`, missing the t. The model reads
 * `weightPerPiece` with no fallback (`ArticleNodes.articleFromMap`), so every article
 * reads back as 0.0 and the buyer's piece count for goods sold by weight is lost.
 *
 * `migrate_prod_db.py` fixes this in the offline pipeline, but the production import
 * ran on 2026-10-08 from a file produced before that fix existed, so live data still
 * carries the misspelling. Re-importing is not an option: the database has been
 * written to since, and a root import would discard everything after the snapshot.
 * Hence this targeted update - and hence no --out mode, since there is no export to
 * import.
 *
 * Nothing is being destroyed in the meantime. Saves go through a per-field multi-path
 * update (`ArticleNodes.saveUpdate`), one path per field the app knows, so a seller
 * save writes `weightPerPiece` but never touches `weighPerPiece`. The original value
 * survives every edit, which is what makes this recoverable at all. It also means an
 * article can now hold both keys, so there are three states to tell apart:
 *
 *   A  only weighPerPiece          untouched since the import    copy across, drop old
 *   B  both, weightPerPiece == 0   seller saved back the 0.0 it read   same
 *   C  both, weightPerPiece != 0   seller typed a real value     keep new, drop old
 *
 * In state C the two values usually agree, because the seller retyped what was always
 * there. When they disagree the article is left completely alone and reported: only a
 * human can say whether the new value is a deliberate correction, and overwriting it
 * would destroy the one piece of information here that is not recoverable.
 *
 * Writes happen new-key-first, old-key-second, so an interrupted run leaves the value
 * present under both names rather than neither. Re-running is idempotent.
 *
 * This script uses the firebase CLI's admin credentials, which bypass rules.
 *
 * Dry run by default. Pass --apply to write.
 *
 *   node fix-weight-per-piece.mjs --project <projectId>            # report only
 *   node fix-weight-per-piece.mjs --project <projectId> --apply    # write
 *   node fix-weight-per-piece.mjs --from-export <export.json>      # offline check
 *
 * --from-export reads a database export from disk instead of the live database and
 * never contacts a project.
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const OLD = "weighPerPiece";
const NEW = "weightPerPiece";

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const exportPath = args.includes("--from-export")
  ? args[args.indexOf("--from-export") + 1]
  : null;
const project = args.includes("--project") ? args[args.indexOf("--project") + 1] : null;

if (exportPath && apply) {
  console.error("--from-export is read-only; it cannot be combined with --apply.");
  process.exit(2);
}
if (!exportPath && (!project || project.startsWith("--"))) {
  console.error(
    "Usage: node fix-weight-per-piece.mjs --project <projectId> [--apply]\n" +
    "       node fix-weight-per-piece.mjs --from-export <export.json>"
  );
  process.exit(2);
}

let read;
if (exportPath) {
  const snapshot = JSON.parse(readFileSync(exportPath, "utf8"));
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
console.log(apply ? "Mode:   APPLY (writing to the database)\n" : "Mode:   dry run (no writes)\n");

// --- classify every article ----------------------------------------------------

const articles = read("/articles") ?? {};
const planned = [];    // { path, value, state, dropOnly }
const conflicts = [];  // state C where the two values disagree
const clean = [];      // nothing to do
const malformed = [];  // the old key holds something that is not a number

for (const [sellerId, bySeller] of Object.entries(articles)) {
  for (const [articleId, article] of Object.entries(bySeller ?? {})) {
    const where = `${sellerId}/${articleId}`;
    const name = article?.productName ?? "(unnamed)";

    if (!article || !(OLD in article)) {
      clean.push(where);
      continue;
    }

    const oldValue = article[OLD];
    if (typeof oldValue !== "number" || !Number.isFinite(oldValue)) {
      malformed.push({ where, name, oldValue });
      continue;
    }

    const base = `/articles/${sellerId}/${articleId}`;
    const newValue = article[NEW];

    if (newValue === undefined || newValue === null) {
      // A: untouched since the import.
      planned.push({ base, where, name, value: oldValue, state: "A", dropOnly: false });
    } else if (typeof newValue !== "number" || !Number.isFinite(newValue)) {
      malformed.push({ where, name, oldValue, newValue });
    } else if (newValue === 0) {
      // B: the app read 0.0 and wrote it straight back. The real value is the old one.
      planned.push({ base, where, name, value: oldValue, state: "B", dropOnly: false });
    } else if (newValue === oldValue) {
      // C, agreeing: the stale key is simply redundant.
      planned.push({ base, where, name, value: newValue, state: "C", dropOnly: true });
    } else {
      // C, disagreeing: not ours to resolve.
      conflicts.push({ where, name, oldValue, newValue });
    }
  }
}

const total = planned.length + conflicts.length + clean.length + malformed.length;
console.log(`Scanned ${total} article(s).\n`);

const byState = (s) => planned.filter((p) => p.state === s).length;
console.log(`  A  rename, no new key yet        ${byState("A")}`);
console.log(`  B  new key is 0.0, take the old  ${byState("B")}`);
console.log(`  C  both agree, drop the old key  ${byState("C")}`);
console.log(`  -  already clean                 ${clean.length}`);
console.log(`  !  disagreeing, left alone       ${conflicts.length}`);
console.log(`  ?  malformed, left alone         ${malformed.length}`);

if (planned.length > 0) {
  console.log(`\n${planned.length} article(s) to update:`);
  for (const { where, name, value, state, dropOnly } of planned) {
    const what = dropOnly ? `drop ${OLD}, keep ${value}` : `${NEW} = ${value}`;
    console.log(`  [${state}] ${where}  ${name}\n        ${what}`);
  }
}

if (conflicts.length > 0) {
  console.log(`\n${conflicts.length} article(s) hold two different weights:`);
  for (const { where, name, oldValue, newValue } of conflicts) {
    console.log(`  ${where}  ${name}: ${OLD}=${oldValue}, ${NEW}=${newValue}`);
  }
  console.log(
    "\nLeft untouched. The new value may be a deliberate correction by the seller,\n" +
    "and unlike the rest of this migration that judgement is not recoverable from\n" +
    "the data. Resolve these by hand."
  );
}

if (malformed.length > 0) {
  console.log(`\n${malformed.length} article(s) hold a non-numeric weight:`);
  for (const m of malformed) {
    console.log(`  ${m.where}  ${m.name}: ${OLD}=${JSON.stringify(m.oldValue)}` +
      (m.newValue !== undefined ? `, ${NEW}=${JSON.stringify(m.newValue)}` : ""));
  }
  console.log("\nLeft untouched.");
}

if (planned.length === 0) {
  console.log("\nNothing to write.");
  process.exit(0);
}

if (!apply) {
  console.log("\nDry run. Re-run with --apply to write these values.");
  process.exit(0);
}

// `database:set <path> -` reads the value from stdin, but firebase-tools 11.29.1
// cannot stream a stdin body on Node 22 - the PUT dies with "Failed to make request"
// while every other call on the same credentials succeeds. Writing from a real file
// takes a different path through the CLI and works. backfill-order-buyerid.mjs had
// the same latent bug, never hit because it has only ever been run as a dry run.
const scratch = mkdtempSync(join(tmpdir(), "fix-weight-"));
const valueFile = join(scratch, "value.json");

// New key first, then the old one: an interrupted run leaves the value under both
// names, never under neither.
let renamed = 0;
let dropped = 0;
try {
  for (const { base, value, dropOnly } of planned) {
    if (!dropOnly) {
      writeFileSync(valueFile, JSON.stringify(value));
      execFileSync(
        "firebase",
        ["database:set", `${base}/${NEW}`, valueFile, "--project", project, "--force"],
        { encoding: "utf8" }
      );
      renamed++;
    }
    execFileSync("firebase", ["database:remove", `${base}/${OLD}`, "--project", project, "--force"], {
      encoding: "utf8"
    });
    dropped++;
  }
} finally {
  rmSync(scratch, { recursive: true, force: true });
}
console.log(`\nWrote ${renamed} ${NEW} value(s) and removed ${dropped} ${OLD} key(s).`);
