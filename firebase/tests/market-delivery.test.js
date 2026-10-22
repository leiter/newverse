import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE } from "./helpers.js";

/**
 * A market says whether the round that goes with its day exists at all. Delivery is a
 * capability of the stall, not a wish of the buyer: `BuyerProfile.isSelfPickup`
 * defaults to false, so every untouched profile asks for delivery and a pickup-only
 * market is the common path rather than a corner case.
 *
 * The client settles this in `Fulfilment.forMarket`; these are the rules that hold
 * when the client is an older build, or not the client at all.
 *
 * An absent `allowsDelivery` reads as "delivers", the same way the Kotlin default
 * does — flipping that would strip delivery from every market already stored.
 *
 * See doc/guides/ordering-business-rules.md § Delivery.
 */
describe("market delivery", () => {
  let env;
  const DATE = "20261029";

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref().update({
        [`markets/${SELLER}/delivers`]: { id: "delivers", name: "Kreuzberg", allowsDelivery: true },
        [`markets/${SELLER}/pickupOnly`]: { id: "pickupOnly", name: "Neukölln", allowsDelivery: false },
        // A market written before the field existed.
        [`markets/${SELLER}/legacy`]: { id: "legacy", name: "Wedding" },
        [`buyer_access_status/${SELLER}/${ALICE}`]: {
          status: "APPROVED", updatedAt: 1, displayName: "Alice Schmidt"
        }
      });
    });
  });

  const order = (overrides = {}) => ({
    id: "order1",
    buyerId: ALICE,
    buyerName: "Alice Schmidt",
    buyerEmail: "alice@example.com",
    createdDate: 1,
    sellerId: SELLER,
    marketId: "delivers",
    fulfilment: "DELIVERY",
    deliveryAddress: { street: "Bergmannstr.", houseNumber: "1" },
    pickUpDate: 9999999999999,
    status: "PLACED",
    origin: "PRODUCTION",
    ...overrides
  });

  // --- the field itself -----------------------------------------------------

  it("lets the seller turn delivery off for one market", async () => {
    await assertSucceeds(
      asUser(env, SELLER).ref(`markets/${SELLER}/delivers/allowsDelivery`).set(false)
    );
  });

  it("denies a non-boolean, so a typo cannot read as 'delivers'", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`markets/${SELLER}/delivers/allowsDelivery`).set("nein")
    );
  });

  it("keeps the field readable by any signed-in buyer", async () => {
    // The buy app has to answer this before the checkout, from a market node it can read.
    await assertSucceeds(
      asUser(env, ALICE).ref(`markets/${SELLER}/pickupOnly/allowsDelivery`).once("value")
    );
  });

  // --- what a buyer may place -----------------------------------------------

  it("accepts a delivery at a market that delivers", async () => {
    await assertSucceeds(
      asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order1`).set(order())
    );
  });

  it("refuses a delivery at a pickup-only market", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order1`)
        .set(order({ marketId: "pickupOnly" }))
    );
  });

  it("accepts a pickup at a pickup-only market", async () => {
    await assertSucceeds(
      asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order1`).set(order({
        marketId: "pickupOnly",
        fulfilment: "PICKUP",
        deliveryAddress: null
      }))
    );
  });

  it("accepts a delivery at a market stored before the field existed", async () => {
    await assertSucceeds(
      asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order1`)
        .set(order({ marketId: "legacy" }))
    );
  });

  it("accepts a delivery on an order that names no market", async () => {
    // An order from before markets were an entity. The marketId rule already allows
    // the empty string, and an order with no stall says nothing about delivery.
    await assertSucceeds(
      asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order1`)
        .set(order({ marketId: "" }))
    );
  });

  it("refuses switching an existing order to delivery at a pickup-only market", async () => {
    // The field-level write is the one a client with the dialog open would make.
    await seed(env, async (db) => {
      await db.ref(`orders/${SELLER}/${DATE}/order1`).set(order({
        marketId: "pickupOnly", fulfilment: "PICKUP", deliveryAddress: null
      }));
    });

    await assertFails(
      asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order1/fulfilment`).set("DELIVERY")
    );
  });

  it("lets an order placed as a delivery stand once the market stops delivering", async () => {
    // Same discipline as a closure: the seller may already have loaded the van. The
    // order keeps what it was placed with; only an edit moves it.
    await seed(env, async (db) => {
      await db.ref(`orders/${SELLER}/${DATE}/order1`).set(order());
      await db.ref(`markets/${SELLER}/delivers/allowsDelivery`).set(false);
    });

    await assertSucceeds(
      asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order1`).once("value")
    );
    // ...and the seller can still work it: marking it hidden is not a fulfilment change.
    await assertSucceeds(
      asUser(env, SELLER).ref(`orders/${SELLER}/${DATE}/order1/hiddenBySeller`).set(true)
    );
  });

  // --- demo orders are the same shop ----------------------------------------

  it("refuses a demo delivery at a pickup-only market", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`demo_orders/${SELLER}/${DATE}/order1`)
        .set(order({ marketId: "pickupOnly", origin: "DEMO" }))
    );
  });

  it("accepts a demo delivery at a market that delivers", async () => {
    await assertSucceeds(
      asUser(env, ALICE).ref(`demo_orders/${SELLER}/${DATE}/order1`)
        .set(order({ origin: "DEMO" }))
    );
  });
});
