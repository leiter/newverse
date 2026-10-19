import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE, MALLORY } from "./helpers.js";

/**
 * Markets moved out of the seller profile's list into a node of their own, so
 * that pricing, eligibility and closures have somewhere to hang off a single
 * market — see doc/market-pricing-and-access-design.md §1.
 *
 * Public to every signed-in user, because a buyer must see where they can
 * collect before they are approved; written only by the seller who owns them.
 */
describe("markets", () => {
  let env;
  const OTHER_SELLER = "otherSellerUid000000000000";

  const market = (overrides = {}) => ({
    id: "m1",
    name: "Markt Kreuzberg",
    street: "Bergmannstr.",
    houseNumber: "1",
    city: "Berlin",
    zipCode: "10961",
    dayOfWeek: "Donnerstag",
    begin: "09:00",
    end: "14:00",
    dayIndex: 3,
    active: true,
    ...overrides
  });

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref(`markets/${SELLER}/m1`).set(market());
    });
  });

  // --- who may read ---------------------------------------------------------

  it("lets any signed-in user read a seller's markets", async () => {
    await assertSucceeds(asUser(env, ALICE).ref(`markets/${SELLER}`).once("value"));
  });

  it("lets an unapproved guest read them, so they can see where to collect", async () => {
    await assertSucceeds(asUser(env, MALLORY).ref(`markets/${SELLER}`).once("value"));
  });

  // --- who may write --------------------------------------------------------

  it("lets the seller add a market", async () => {
    await assertSucceeds(
      asUser(env, SELLER).ref(`markets/${SELLER}/m2`).set(market({ id: "m2", name: "Neukölln" }))
    );
  });

  it("denies a buyer writing a market", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`markets/${SELLER}/m2`).set(market({ id: "m2" }))
    );
  });

  it("denies another seller writing into this seller's markets", async () => {
    await assertFails(
      asUser(env, OTHER_SELLER).ref(`markets/${SELLER}/m2`).set(market({ id: "m2" }))
    );
  });

  // --- the key is the id ----------------------------------------------------

  it("denies a record whose id does not match its key", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`markets/${SELLER}/m2`).set(market({ id: "something-else" }))
    );
  });

  it("denies a record with no name", async () => {
    const { name, ...withoutName } = market({ id: "m2" });
    await assertFails(asUser(env, SELLER).ref(`markets/${SELLER}/m2`).set(withoutName));
  });

  it("denies an unknown field, so a typo cannot silently persist", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`markets/${SELLER}/m2`).set(market({ id: "m2", colour: "red" }))
    );
  });

  it("denies a day index outside the week", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`markets/${SELLER}/m2`).set(market({ id: "m2", dayIndex: 9 }))
    );
  });

  // --- soft deletion --------------------------------------------------------

  it("lets the seller deactivate a market rather than delete it", async () => {
    await assertSucceeds(asUser(env, SELLER).ref(`markets/${SELLER}/m1/active`).set(false));
  });

  // --- contacts travel with the market --------------------------------------

  it("accepts a market carrying its own contact", async () => {
    await assertSucceeds(
      asUser(env, SELLER).ref(`markets/${SELLER}/m2`).set(
        market({ id: "m2", contacts: [{ channel: "PHONE", value: "01701234567", label: "Stand" }] })
      )
    );
  });

  it("denies a contact with no value", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`markets/${SELLER}/m2`).set(
        market({ id: "m2", contacts: [{ channel: "PHONE" }] })
      )
    );
  });
});

/**
 * An order names the market it will be collected at. The rules check that the
 * market exists, so a typo or a stale id cannot produce an order nobody can
 * fulfil. Empty stays legal: orders predating the field carry it.
 */
describe("an order's market", () => {
  let env;

  const order = (overrides = {}) => ({
    id: "o1",
    buyerId: ALICE,
    sellerId: SELLER,
    origin: "PRODUCTION",
    ...overrides
  });

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref().update({
        [`markets/${SELLER}/m1/id`]: "m1",
        [`markets/${SELLER}/m1/name`]: "Markt Kreuzberg",
        [`buyer_access_status/${SELLER}/${ALICE}/status`]: "APPROVED"
      });
    });
  });

  it("accepts an order naming a market that exists", async () => {
    await assertSucceeds(
      asUser(env, ALICE).ref(`orders/${SELLER}/20241024/o1`).set(order({ marketId: "m1" }))
    );
  });

  it("denies an order naming a market that does not exist", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`orders/${SELLER}/20241024/o1`).set(order({ marketId: "ghost" }))
    );
  });

  it("denies an order naming another seller's market", async () => {
    await seed(env, async (db) => {
      await db.ref(`markets/otherSellerUid000000000000/m9`).set({ id: "m9", name: "Elsewhere" });
    });
    await assertFails(
      asUser(env, ALICE).ref(`orders/${SELLER}/20241024/o1`).set(order({ marketId: "m9" }))
    );
  });

  // Orders written before the field existed, and the single-market case where
  // the app has nothing to fill in yet.
  it("accepts an empty market id", async () => {
    await assertSucceeds(
      asUser(env, ALICE).ref(`orders/${SELLER}/20241024/o1`).set(order({ marketId: "" }))
    );
  });

  it("accepts an order with no market id at all", async () => {
    await assertSucceeds(
      asUser(env, ALICE).ref(`orders/${SELLER}/20241024/o1`).set(order())
    );
  });
});
