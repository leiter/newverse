import { before, after, beforeEach, describe, it } from "node:test";
import assert from "node:assert/strict";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE, MALLORY } from "./helpers.js";

/**
 * Booked sales are the seller's books. They may only ever be added: no change and
 * no delete, not even by the seller — a mistake is corrected by recording a
 * cancellation. A sale and its order index entry are written together in one
 * multi-path update (SaleNodes.recordUpdate) and must point at each other.
 */
describe("sales and the sale index", () => {
  let env;
  const OTHER_SELLER = "otherSellerUid000000000000";
  const MONTH = "202609";

  const sale = (orderId = "order1") => ({
    orderId,
    confirmedAt: 1790000000000,
    pickUpDate: 1789990000000,
    lines: {
      0: {
        articleId: "apple", productId: "112108", productName: "Apfel Topaz", unit: "kg",
        quantity: 1.62, unitPriceCents: 319, taxRate: 0.07, acquirePriceCents: 196
      }
    }
  });

  /** The update the app sends for a new sale. */
  const recordUpdate = (sellerId, saleId, orderId = "order1", month = MONTH) => ({
    [`sales/${sellerId}/${month}/${saleId}`]: sale(orderId),
    [`sale_index/${sellerId}/${orderId}/${saleId}`]: month
  });

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => { await db.ref().update(recordUpdate(SELLER, "s1")); });
  });

  const exists = async (path) => {
    let found;
    await seed(env, async (db) => { found = (await db.ref(path).once("value")).exists(); });
    return found;
  };

  // --- recording ------------------------------------------------------------

  it("lets the seller record a sale with its index entry", async () => {
    await assertSucceeds(asUser(env, SELLER).ref().update(recordUpdate(SELLER, "s2", "order2")));
  });

  it("lets the seller record a cancellation for an order already booked", async () => {
    await assertSucceeds(asUser(env, SELLER).ref().update(recordUpdate(SELLER, "s2", "order1")));
  });

  it("denies a sale without its index entry", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`sales/${SELLER}/${MONTH}/s2`).set(sale("order2"))
    );
  });

  it("denies an index entry without its sale", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`sale_index/${SELLER}/order2/s2`).set(MONTH)
    );
  });

  it("denies an index entry pointing at the wrong month", async () => {
    await assertFails(asUser(env, SELLER).ref().update({
      [`sales/${SELLER}/${MONTH}/s2`]: sale("order2"),
      [`sale_index/${SELLER}/order2/s2`]: "202610"
    }));
  });

  it("denies an index entry filed under another order", async () => {
    await assertFails(asUser(env, SELLER).ref().update({
      [`sales/${SELLER}/${MONTH}/s2`]: sale("order2"),
      [`sale_index/${SELLER}/order3/s2`]: MONTH
    }));
  });

  it("denies a malformed month key", async () => {
    await assertFails(asUser(env, SELLER).ref().update(recordUpdate(SELLER, "s2", "order2", "2026-09")));
  });

  it("denies a sale without lines", async () => {
    const { lines, ...withoutLines } = sale("order2");
    await assertFails(asUser(env, SELLER).ref().update({
      [`sales/${SELLER}/${MONTH}/s2`]: withoutLines,
      [`sale_index/${SELLER}/order2/s2`]: MONTH
    }));
  });

  // --- nothing booked can change --------------------------------------------

  it("denies overwriting a booked sale, even by the seller", async () => {
    await assertFails(asUser(env, SELLER).ref().update(recordUpdate(SELLER, "s1")));
  });

  it("denies changing one field of a booked sale", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`sales/${SELLER}/${MONTH}/s1/lines/0/quantity`).set(0.5)
    );
  });

  it("denies deleting a booked sale", async () => {
    await assertFails(asUser(env, SELLER).ref(`sales/${SELLER}/${MONTH}/s1`).remove());
    assert.equal(await exists(`sales/${SELLER}/${MONTH}/s1`), true);
  });

  it("denies deleting a whole month of sales", async () => {
    await assertFails(asUser(env, SELLER).ref(`sales/${SELLER}/${MONTH}`).remove());
  });

  it("denies deleting an index entry", async () => {
    await assertFails(asUser(env, SELLER).ref(`sale_index/${SELLER}/order1/s1`).remove());
  });

  // --- nobody else sees or writes the books -----------------------------------

  it("lets the seller read their sales and index", async () => {
    await assertSucceeds(asUser(env, SELLER).ref(`sales/${SELLER}/${MONTH}`).once("value"));
    await assertSucceeds(asUser(env, SELLER).ref(`sale_index/${SELLER}/order1`).once("value"));
  });

  it("denies a buyer reading the seller's sales", async () => {
    await assertFails(asUser(env, ALICE).ref(`sales/${SELLER}`).once("value"));
    await assertFails(asUser(env, ALICE).ref(`sale_index/${SELLER}`).once("value"));
  });

  it("denies another seller reading the sales", async () => {
    await assertFails(asUser(env, OTHER_SELLER).ref(`sales/${SELLER}/${MONTH}`).once("value"));
  });

  it("denies a stranger booking a sale for the seller", async () => {
    await assertFails(asUser(env, MALLORY).ref().update(recordUpdate(SELLER, "fake", "order9")));
  });
});
