import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";

/**
 * Rules file under test. Override with RULES_FILE to run the same suite
 * against an older ruleset, e.g. to confirm a fix actually changed something.
 */
const rulesPath = fileURLToPath(
  new URL(process.env.RULES_FILE ?? "../database.rules.json", import.meta.url)
);

export async function createTestEnv() {
  return initializeTestEnvironment({
    projectId: "newverse-rules-test",
    database: {
      host: "127.0.0.1",
      port: 9000,
      rules: readFileSync(rulesPath, "utf8")
    }
  });
}

/** A signed-in user. Anonymous guests are indistinguishable from these. */
export const asUser = (env, uid) => env.authenticatedContext(uid).database();

/** Seed data bypassing the rules, the way the app would have written it. */
export const seed = (env, fn) => env.withSecurityRulesDisabled((ctx) => fn(ctx.database()));

/** Conversation ids are derived, not random: Conversation.createId(). */
export const conversationId = (a, b) => [a, b].sort().join("_");

export const SELLER = "sellerUid00000000000000000";
export const ALICE = "aliceUid000000000000000000";
export const BOB = "bobUid00000000000000000000";
/** The attacker: a guest who tapped "Continue as guest". */
export const MALLORY = "malloryUid0000000000000000";
