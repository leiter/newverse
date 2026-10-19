import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE, MALLORY } from "./helpers.js";

/**
 * Approval says whether a buyer may order; allowedMarkets says where. An invite
 * token can be scoped to one stall, and the rules make the buyer's own record
 * match what the token granted — otherwise redeeming a Kreuzberg invite would be
 * an invitation to grant yourself every market.
 *
 * See doc/market-pricing-and-access-design.md §3.
 */
describe("market eligibility", () => {
  let env;
  const FUTURE = Date.now() + 60 * 60 * 1000;

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref().update({
        [`markets/${SELLER}/m1`]: { id: "m1", name: "Kreuzberg" },
        [`markets/${SELLER}/m2`]: { id: "m2", name: "Neukölln" },
        // A token scoped to one stall, already claimed by Alice.
        [`invite_tokens/${SELLER}/scoped`]: {
          createdAt: Date.now(), expiresAt: FUTURE,
          displayNameHint: "Alice", grantsMarkets: "m1", redeemedBy: ALICE
        },
        // A token from before scoping existed: no grantsMarkets at all.
        [`invite_tokens/${SELLER}/legacy`]: {
          createdAt: Date.now(), expiresAt: FUTURE,
          displayNameHint: "Mallory", redeemedBy: MALLORY
        }
      });
    });
  });

  const record = (overrides = {}) => ({
    status: "APPROVED", displayName: "A", updatedAt: Date.now(), ...overrides
  });

  // --- redeeming a scoped invite -------------------------------------------

  it("lets a buyer claim exactly what their token granted", async () => {
    await assertSucceeds(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}`)
        .set(record({ viaToken: "scoped", allowedMarkets: "m1" }))
    );
  });

  it("denies a buyer widening the grant to another market", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}`)
        .set(record({ viaToken: "scoped", allowedMarkets: "m1,m2" }))
    );
  });

  // Empty means every market, so this is the escalation that matters most.
  it("denies a buyer claiming every market by leaving the scope empty", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}`)
        .set(record({ viaToken: "scoped", allowedMarkets: "" }))
    );
  });

  it("denies a buyer omitting the scope entirely", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}`)
        .set(record({ viaToken: "scoped" }))
    );
  });

  it("denies a buyer swapping the granted market for a different one", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}`)
        .set(record({ viaToken: "scoped", allowedMarkets: "m2" }))
    );
  });

  // --- tokens minted before scoping existed --------------------------------

  it("lets a legacy token grant every market, which is what it always meant", async () => {
    await assertSucceeds(
      asUser(env, MALLORY).ref(`buyer_access_status/${SELLER}/${MALLORY}`)
        .set(record({ viaToken: "legacy", allowedMarkets: "" }))
    );
  });

  it("denies a legacy redeemer inventing a scope", async () => {
    await assertFails(
      asUser(env, MALLORY).ref(`buyer_access_status/${SELLER}/${MALLORY}`)
        .set(record({ viaToken: "legacy", allowedMarkets: "m1" }))
    );
  });

  // --- the scope cannot be edited afterwards -------------------------------

  it("denies a buyer widening their own scope later", async () => {
    await seed(env, async (db) => {
      await db.ref(`buyer_access_status/${SELLER}/${ALICE}`)
        .set(record({ viaToken: "scoped", allowedMarkets: "m1" }));
    });

    await assertFails(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}/allowedMarkets`).set("m1,m2")
    );
  });

  it("still lets a buyer change their own display name", async () => {
    await seed(env, async (db) => {
      await db.ref(`buyer_access_status/${SELLER}/${ALICE}`)
        .set(record({ viaToken: "scoped", allowedMarkets: "m1" }));
    });

    await assertSucceeds(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}/displayName`).set("Alice B")
    );
  });

  it("lets the seller change a buyer's scope", async () => {
    await seed(env, async (db) => {
      await db.ref(`buyer_access_status/${SELLER}/${ALICE}`)
        .set(record({ viaToken: "scoped", allowedMarkets: "m1" }));
    });

    await assertSucceeds(
      asUser(env, SELLER).ref(`buyer_access_status/${SELLER}/${ALICE}/allowedMarkets`).set("m1,m2")
    );
  });

  // --- requesting access carries no scope ----------------------------------

  it("lets a buyer request access with no scope", async () => {
    await assertSucceeds(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}`)
        .set({ status: "PENDING", displayName: "Alice", updatedAt: Date.now() })
    );
  });

  it("denies a pending buyer granting themselves markets", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}`)
        .set({ status: "PENDING", displayName: "Alice", updatedAt: Date.now(), allowedMarkets: "m1,m2" })
    );
  });

  // --- the token's own scope is immutable ----------------------------------

  it("denies a redeemer rewriting the token's grant on the way through", async () => {
    await seed(env, async (db) => {
      await db.ref(`invite_tokens/${SELLER}/fresh`).set({
        createdAt: Date.now(), expiresAt: FUTURE, displayNameHint: "Bob", grantsMarkets: "m1"
      });
    });

    await assertFails(
      asUser(env, ALICE).ref(`invite_tokens/${SELLER}/fresh`).set({
        createdAt: Date.now(), expiresAt: FUTURE, displayNameHint: "Bob",
        grantsMarkets: "m1,m2", redeemedBy: ALICE
      })
    );
  });

  it("lets the seller mint a scoped token", async () => {
    await assertSucceeds(
      asUser(env, SELLER).ref(`invite_tokens/${SELLER}/new`).set({
        createdAt: Date.now(), expiresAt: FUTURE, displayNameHint: "Bob", grantsMarkets: "m1"
      })
    );
  });
});
