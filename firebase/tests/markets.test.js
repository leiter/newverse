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

/**
 * A market carries why its prices differ. The conditions are public, because the
 * buy app has to price the catalogue and explain the figure it shows.
 */
describe("market price conditions", () => {
  let env;

  const market = (overrides = {}) => ({
    id: "m1", name: "Markt Kreuzberg", dayIndex: 3, active: true, ...overrides
  });

  const condition = (overrides = {}) => ({
    cause: "STALL_FEE", scopeType: "ALL", scopeValue: "",
    effect: "SURCHARGE", amount: 0.25, note: "Standgebühr", ...overrides
  });

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => { await db.ref(`markets/${SELLER}/m1`).set(market()); });
  });

  it("lets the seller attach a condition", async () => {
    await assertSucceeds(
      asUser(env, SELLER).ref(`markets/${SELLER}/m1`).set(
        market({ priceConditions: [condition()], pricingRevision: 1 })
      )
    );
  });

  it("lets a buyer read them, so the catalogue can be priced", async () => {
    await seed(env, async (db) => {
      await db.ref(`markets/${SELLER}/m1/priceConditions`).set([condition()]);
    });
    await assertSucceeds(asUser(env, ALICE).ref(`markets/${SELLER}/m1`).once("value"));
  });

  it("denies a buyer writing one", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`markets/${SELLER}/m1/priceConditions`).set([condition()])
    );
  });

  it("denies an effect that is not one of the three", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`markets/${SELLER}/m1`).set(
        market({ priceConditions: [condition({ effect: "MAGIC" })] })
      )
    );
  });

  // The amount is always positive; discount is expressed by the effect, so a
  // negative surcharge cannot sneak a price up by being a double negative.
  it("denies a negative amount", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`markets/${SELLER}/m1`).set(
        market({ priceConditions: [condition({ amount: -1 })] })
      )
    );
  });

  it("denies an absurd amount", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`markets/${SELLER}/m1`).set(
        market({ priceConditions: [condition({ amount: 99999 })] })
      )
    );
  });

  it("denies a condition with no amount at all", async () => {
    const { amount, ...noAmount } = condition();
    await assertFails(
      asUser(env, SELLER).ref(`markets/${SELLER}/m1`).set(
        market({ priceConditions: [noAmount] })
      )
    );
  });

  it("denies an unknown field on a condition", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`markets/${SELLER}/m1`).set(
        market({ priceConditions: [condition({ secretMultiplier: 3 })] })
      )
    );
  });

  it("denies a negative pricing revision", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`markets/${SELLER}/m1`).set(market({ pricingRevision: -1 }))
    );
  });
});
