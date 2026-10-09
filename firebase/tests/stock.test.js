import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE, MALLORY } from "./helpers.js";

/**
 * The stock ledger is the seller's own record of what is in storage. Only the
 * seller may read or write it, and movements may only ever be added: the level is
 * the sum of the movements, so a changed or deleted movement would rewrite
 * history. A mistake is corrected by recording a further movement, normally a
 * stocktake. The derived level under /stock is recomputed and may be overwritten.
 */
describe("stock movements and levels", () => {
  let env;
  const OTHER_SELLER = "otherSellerUid000000000000";
  const MONTH = "202610";

  const movement = (over = {}) => ({
    articleId: "apple",
    productId: "112108",
    quantity: 15,
    unit: "kg",
    kind: "INTAKE",
    source: "SCALE",
    recordedAt: 1791500000000,
    ...over
  });

  const level = (over = {}) => ({
    onHand: 12.4,
    unit: "kg",
    lastMovementAt: 1791500000000,
    lastCountedAt: 1791400000000,
    lastSource: "SCALE",
    ...over
  });

  const path = (sellerId, id, month = MONTH) => `stock_movements/${sellerId}/${month}/${id}`;

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref(path(SELLER, "m1")).set(movement());
      await db.ref(`stock/${SELLER}/apple`).set(level());
    });
  });

  // --- recording movements --------------------------------------------------

  it("lets the seller record a movement", async () => {
    await assertSucceeds(asUser(env, SELLER).ref(path(SELLER, "m2")).set(movement()));
  });

  it("lets the seller record every kind of movement", async () => {
    const db = asUser(env, SELLER);
    await assertSucceeds(db.ref(path(SELLER, "k1")).set(movement({ kind: "INTAKE" })));
    await assertSucceeds(db.ref(path(SELLER, "k2")).set(movement({ kind: "SALE", quantity: -2, source: "ORDERED" })));
    await assertSucceeds(db.ref(path(SELLER, "k3")).set(movement({ kind: "LOSS", quantity: -0.5, source: "MANUAL" })));
    await assertSucceeds(
      db.ref(path(SELLER, "k4")).set(movement({ kind: "STOCKTAKE", quantity: -2.6, countedTo: 12.4 }))
    );
  });

  it("lets the seller record several movements in one update, as a stocktake does", async () => {
    await assertSucceeds(asUser(env, SELLER).ref().update({
      [path(SELLER, "b1")]: movement({ articleId: "apple", kind: "STOCKTAKE", quantity: -1, countedTo: 14 }),
      [path(SELLER, "b2")]: movement({ articleId: "pear", kind: "STOCKTAKE", quantity: 2, countedTo: 6 })
    }));
  });

  // --- append only ----------------------------------------------------------

  it("denies changing a recorded movement, even for the seller", async () => {
    await assertFails(asUser(env, SELLER).ref(path(SELLER, "m1")).set(movement({ quantity: 99 })));
  });

  it("denies changing one field of a recorded movement", async () => {
    await assertFails(asUser(env, SELLER).ref(`${path(SELLER, "m1")}/quantity`).set(99));
  });

  it("denies deleting a recorded movement, even for the seller", async () => {
    await assertFails(asUser(env, SELLER).ref(path(SELLER, "m1")).remove());
  });

  // --- validation -----------------------------------------------------------

  it("denies a movement missing a required field", async () => {
    const db = asUser(env, SELLER);
    for (const field of ["articleId", "quantity", "unit", "kind", "source", "recordedAt"]) {
      const incomplete = movement();
      delete incomplete[field];
      await assertFails(db.ref(path(SELLER, `no_${field}`)).set(incomplete));
    }
  });

  it("denies a movement with no unit, which could not be read back", async () => {
    await assertFails(asUser(env, SELLER).ref(path(SELLER, "m2")).set(movement({ unit: "" })));
  });

  it("denies an unknown kind or source", async () => {
    const db = asUser(env, SELLER);
    await assertFails(db.ref(path(SELLER, "m2")).set(movement({ kind: "SHRINKAGE" })));
    await assertFails(db.ref(path(SELLER, "m3")).set(movement({ source: "GUESS" })));
  });

  it("denies a stocktake that does not say what was found", async () => {
    await assertFails(
      asUser(env, SELLER).ref(path(SELLER, "m2")).set(movement({ kind: "STOCKTAKE", quantity: -2.6 }))
    );
  });

  it("denies a non-numeric quantity", async () => {
    await assertFails(asUser(env, SELLER).ref(path(SELLER, "m2")).set(movement({ quantity: "15" })));
  });

  it("denies a field the ledger does not define", async () => {
    await assertFails(asUser(env, SELLER).ref(path(SELLER, "m2")).set(movement({ costCents: 400 })));
  });

  it("denies a month key that is not yyyyMM", async () => {
    const db = asUser(env, SELLER);
    await assertFails(db.ref(path(SELLER, "m2", "2026-10")).set(movement()));
    await assertFails(db.ref(path(SELLER, "m2", "oct")).set(movement()));
  });

  // --- who may touch it ------------------------------------------------------

  it("denies a buyer reading the ledger", async () => {
    await assertFails(asUser(env, ALICE).ref(`stock_movements/${SELLER}`).once("value"));
    await assertFails(asUser(env, ALICE).ref(path(SELLER, "m1")).once("value"));
  });

  it("denies a guest reading the ledger", async () => {
    await assertFails(asUser(env, MALLORY).ref(`stock_movements/${SELLER}`).once("value"));
  });

  it("denies another seller reading or writing this seller's ledger", async () => {
    await assertFails(asUser(env, OTHER_SELLER).ref(path(SELLER, "m1")).once("value"));
    await assertFails(asUser(env, OTHER_SELLER).ref(path(SELLER, "x1")).set(movement()));
  });

  it("denies a buyer recording a movement", async () => {
    await assertFails(asUser(env, ALICE).ref(path(SELLER, "x1")).set(movement()));
  });

  it("lets the seller read their own ledger", async () => {
    await assertSucceeds(asUser(env, SELLER).ref(`stock_movements/${SELLER}`).once("value"));
  });

  it("keeps each seller's ledger to themselves", async () => {
    await assertSucceeds(asUser(env, OTHER_SELLER).ref(path(OTHER_SELLER, "m1")).set(movement()));
  });

  // --- derived levels --------------------------------------------------------

  it("lets the seller overwrite a derived level, which is recomputed", async () => {
    await assertSucceeds(asUser(env, SELLER).ref(`stock/${SELLER}/apple`).set(level({ onHand: 9.9 })));
  });

  it("lets the seller write levels for several articles at once", async () => {
    await assertSucceeds(asUser(env, SELLER).ref(`stock/${SELLER}`).update({
      apple: level({ onHand: 9.9 }),
      pear: level({ onHand: 4, unit: "kg" })
    }));
  });

  it("lets the seller clear a level", async () => {
    await assertSucceeds(asUser(env, SELLER).ref(`stock/${SELLER}/apple`).remove());
  });

  it("denies a level without an amount or a unit", async () => {
    const db = asUser(env, SELLER);
    await assertFails(db.ref(`stock/${SELLER}/apple`).set({ unit: "kg" }));
    await assertFails(db.ref(`stock/${SELLER}/apple`).set({ onHand: 5 }));
    await assertFails(db.ref(`stock/${SELLER}/apple`).set(level({ unit: "" })));
  });

  it("denies a non-numeric level", async () => {
    await assertFails(asUser(env, SELLER).ref(`stock/${SELLER}/apple`).set(level({ onHand: "9.9" })));
  });

  it("denies a field the level does not define", async () => {
    await assertFails(asUser(env, SELLER).ref(`stock/${SELLER}/apple`).set(level({ reorderLevel: 3 })));
  });

  it("denies a buyer reading or writing levels", async () => {
    await assertFails(asUser(env, ALICE).ref(`stock/${SELLER}`).once("value"));
    await assertFails(asUser(env, ALICE).ref(`stock/${SELLER}/apple`).set(level()));
  });

  it("denies another seller writing levels here", async () => {
    await assertFails(asUser(env, OTHER_SELLER).ref(`stock/${SELLER}/apple`).set(level()));
  });
});
