import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE } from "./helpers.js";

/**
 * `pickupTime` is the buyer saying roughly when they will come to the stall.
 *
 * It is the one field on an order that is purely a courtesy: optional, absent on
 * every order placed before it was asked for, and read by a person rather than by
 * code. It was collected as a *required* profile field for a long time and shown to
 * nobody; now the order carries it and the seller's list reads it.
 *
 * So the rule is only about shape. The node is either absent or a readable `HH:mm` —
 * nothing else reaches the seller's screen as a time of day. No hour range: a market's
 * own opening hours are not modelled here, and a stall that starts at six has
 * customers who come at six.
 *
 * See doc/guides/ordering-business-rules.md § Pickup time.
 */
describe("order pickup time", () => {
  let env;
  const DATE = "20261029";

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref().update({
        [`markets/${SELLER}/m1`]: { id: "m1", name: "Kreuzberg", allowsDelivery: true },
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
    marketId: "m1",
    fulfilment: "PICKUP",
    pickUpDate: 9999999999999,
    status: "PLACED",
    origin: "PRODUCTION",
    ...overrides
  });

  const place = (overrides) =>
    asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order1`).set(order(overrides));

  // --- what a time may look like --------------------------------------------

  it("accepts a time of day", async () => {
    await assertSucceeds(place({ pickupTime: "14:30" }));
  });

  it("accepts an early hour, since a market's opening time is its own business", async () => {
    await assertSucceeds(place({ pickupTime: "06:15" }));
  });

  it("accepts the last minute of the day", async () => {
    await assertSucceeds(place({ pickupTime: "23:59" }));
  });

  it("accepts an order that names no time at all", async () => {
    // The field is optional, and every order placed before it existed has none.
    await assertSucceeds(place({}));
  });

  // --- what it may not --------------------------------------------------------

  it("denies an hour with no minutes", async () => {
    await assertFails(place({ pickupTime: "14" }));
  });

  it("denies an unpadded hour, which would sort wrong in the seller's list", async () => {
    await assertFails(place({ pickupTime: "9:30" }));
  });

  it("denies an hour past the clock", async () => {
    await assertFails(place({ pickupTime: "24:00" }));
  });

  it("denies minutes past the clock", async () => {
    await assertFails(place({ pickupTime: "14:60" }));
  });

  it("denies a sentence where a time belongs", async () => {
    // The seller reads this row; it is not a second message field.
    await assertFails(place({ pickupTime: "irgendwann am Nachmittag" }));
  });

  it("denies a number, so a timestamp cannot land here", async () => {
    await assertFails(place({ pickupTime: 1430 }));
  });

  it("denies the empty string, which is how absent is spelled", async () => {
    // The client writes null rather than "", and a stored "" would have the seller's
    // card deciding whether a blank string counts as a stated time.
    await assertFails(place({ pickupTime: "" }));
  });

  // --- editing it on a placed order -------------------------------------------

  it("lets the buyer change the time on an order they may still edit", async () => {
    await seed(env, async (db) => {
      await db.ref(`orders/${SELLER}/${DATE}/order1`).set(order({ pickupTime: "14:30" }));
    });

    await assertSucceeds(
      asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order1/pickupTime`).set("16:45")
    );
  });

  it("lets the buyer take the time back", async () => {
    await seed(env, async (db) => {
      await db.ref(`orders/${SELLER}/${DATE}/order1`).set(order({ pickupTime: "14:30" }));
    });

    await assertSucceeds(
      asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order1/pickupTime`).remove()
    );
  });

  it("refuses an unreadable time written field by field", async () => {
    await seed(env, async (db) => {
      await db.ref(`orders/${SELLER}/${DATE}/order1`).set(order({ pickupTime: "14:30" }));
    });

    await assertFails(
      asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order1/pickupTime`).set("halb drei")
    );
  });

  // --- demo orders are the same shop ------------------------------------------

  it("holds the same shape on a demo order", async () => {
    await assertSucceeds(
      asUser(env, ALICE).ref(`demo_orders/${SELLER}/${DATE}/order1`)
        .set(order({ origin: "DEMO", pickupTime: "11:00" }))
    );
    await assertFails(
      asUser(env, ALICE).ref(`demo_orders/${SELLER}/${DATE}/order2`)
        .set(order({ id: "order2", origin: "DEMO", pickupTime: "11" }))
    );
  });
});
