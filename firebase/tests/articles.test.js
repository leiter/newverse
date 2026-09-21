import { before, after, beforeEach, describe, it } from "node:test";
import assert from "node:assert/strict";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE, MALLORY } from "./helpers.js";

/**
 * An article has a public half every signed-in user can read (articles/) and a
 * seller-only half with purchase price, markup and sourcing (seller_articles/).
 * The app writes both halves in one multi-path update on the database root
 * (ArticleNodes.saveUpdate / deleteUpdate), which the database applies
 * atomically — or not at all.
 */
describe("articles and seller-only article data", () => {
  let env;
  const OTHER_SELLER = "otherSellerUid000000000000";

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref().update({
        [`articles/${SELLER}/a1/productName`]: "Apfel Topaz",
        [`articles/${SELLER}/a1/price`]: 3.5,
        [`seller_articles/${SELLER}/a1/acquirePrice`]: 1.96,
        [`seller_articles/${SELLER}/a1/markupFactor`]: 1.67
      });
    });
  });

  const exists = async (path) => {
    let found;
    await seed(env, async (db) => { found = (await db.ref(path).once("value")).exists(); });
    return found;
  };

  // --- nobody but the seller sees purchase data ----------------------------

  it("lets a buyer read the public catalog", async () => {
    await assertSucceeds(asUser(env, ALICE).ref(`articles/${SELLER}`).once("value"));
  });

  it("denies a buyer reading the seller-only catalog", async () => {
    await assertFails(asUser(env, ALICE).ref(`seller_articles/${SELLER}`).once("value"));
  });

  it("denies a buyer reading one article's seller-only half", async () => {
    await assertFails(asUser(env, ALICE).ref(`seller_articles/${SELLER}/a1`).once("value"));
  });

  it("denies a buyer reading a single purchase price", async () => {
    await assertFails(
      asUser(env, MALLORY).ref(`seller_articles/${SELLER}/a1/acquirePrice`).once("value")
    );
  });

  it("denies another seller reading the seller-only catalog", async () => {
    await assertFails(asUser(env, OTHER_SELLER).ref(`seller_articles/${SELLER}`).once("value"));
  });

  it("denies a buyer writing seller-only data", async () => {
    await assertFails(
      asUser(env, MALLORY).ref(`seller_articles/${SELLER}/a1/acquirePrice`).set(0)
    );
  });

  // --- the seller's own writes ---------------------------------------------

  it("lets the seller read their seller-only catalog", async () => {
    await assertSucceeds(asUser(env, SELLER).ref(`seller_articles/${SELLER}`).once("value"));
  });

  it("lets the seller create both halves in one update", async () => {
    await assertSucceeds(asUser(env, SELLER).ref().update({
      [`articles/${SELLER}/a2/productName`]: "Birne",
      [`seller_articles/${SELLER}/a2/acquirePrice`]: 2.1
    }));
  });

  it("lets the seller update only the seller-only half of an existing article", async () => {
    await assertSucceeds(
      asUser(env, SELLER).ref().update({ [`seller_articles/${SELLER}/a1/acquirePrice`]: 2.2 })
    );
  });

  it("keeps the seller-only half when only the public half is updated", async () => {
    // The save the app makes while the seller-only half is not loaded.
    await assertSucceeds(
      asUser(env, SELLER).ref().update({ [`articles/${SELLER}/a1/available`]: true })
    );
    assert.equal(await exists(`seller_articles/${SELLER}/a1/acquirePrice`), true);
  });

  it("lets the seller delete both halves in one update", async () => {
    await assertSucceeds(asUser(env, SELLER).ref().update({
      [`articles/${SELLER}/a1`]: null,
      [`seller_articles/${SELLER}/a1`]: null
    }));
    assert.equal(await exists(`articles/${SELLER}/a1`), false);
    assert.equal(await exists(`seller_articles/${SELLER}/a1`), false);
  });

  // --- the halves stay together --------------------------------------------

  it("denies seller-only data for an article that does not exist", async () => {
    await assertFails(
      asUser(env, SELLER).ref().update({ [`seller_articles/${SELLER}/ghost/acquirePrice`]: 1.0 })
    );
  });

  it("rejects the whole update when one path is forbidden", async () => {
    await assertFails(asUser(env, SELLER).ref().update({
      [`articles/${SELLER}/a3/productName`]: "Quitte",
      [`seller_articles/${OTHER_SELLER}/a3/acquirePrice`]: 1.0
    }));
    assert.equal(await exists(`articles/${SELLER}/a3`), false, "the allowed half was written");
  });

  it("does not prevent deleting only the public half", async () => {
    // Deletes skip .validate, so the rules cannot keep the halves together
    // here. The app always deletes both, and joins ignore orphaned halves.
    await assertSucceeds(
      asUser(env, SELLER).ref().update({ [`articles/${SELLER}/a1`]: null })
    );
    assert.equal(await exists(`seller_articles/${SELLER}/a1`), true);
  });
});
