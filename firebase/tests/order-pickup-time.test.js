import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE } from "./helpers.js";

/**
 * `pickupTime` is the buyer saying roughly when they want the order: when they will
 * come to the stall, or when they would like it brought round.
 *
 * It is the one field on an order that is purely a courtesy: optional, absent on
 * every order placed before it was asked for, and read by a person rather than by
 * code. It was collected as a *required* profile field for a long time and shown to
 * nobody; now the order carries it and the seller's list reads it.
 *
 * The node is either absent or a time the stall could meet: a readable `HH:mm` between
 * 7:00 and 18:59, which is the same range `ProfileValidation.isTimeInBusinessHours`
 * applies to the profile field and the same range the picker offers. A collection
 * promised at 03:00 is not a promise anyone can keep, and the seller reading the list
 * has no way to tell it from a typo.
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

  it("accepts one on a delivery as readily as on a pickup", async () => {
    // The rule never looked at `fulfilment`; the app used to clear the field on a
    // delivery and now carries it, so this is the guard on that staying allowed.
    await assertSucceeds(place({
      fulfilment: "DELIVERY",
      deliveryAddress: {
        street: "Oranienstr.",
        houseNumber: "1",
        zipCode: "10999",
        city: "Berlin",
        note: ""
      },
      pickupTime: "16:30"
    }));
  });

  it("accepts the first minute the stall is open", async () => {
    await assertSucceeds(place({ pickupTime: "07:00" }));
  });

  it("accepts the last, which is 18:59 — the profile reads hour 18 as in range", async () => {
    await assertSucceeds(place({ pickupTime: "18:59" }));
  });

  it("denies an hour before the stall opens", async () => {
    await assertFails(place({ pickupTime: "06:15" }));
  });

  it("denies an hour after it closes", async () => {
    await assertFails(place({ pickupTime: "19:00" }));
  });

  it("denies the middle of the night", async () => {
    await assertFails(place({ pickupTime: "03:00" }));
  });

  it("accepts an order that names no time at all", async () => {
    // The field is optional, and every order placed before it existed has none.
    await assertSucceeds(place({}));
  });

  // --- what it may not --------------------------------------------------------

  it("denies an hour with no minutes", async () => {
    await assertFails(place({ pickupTime: "14" }));
  });

  it("denies minutes past the clock", async () => {
    await assertFails(place({ pickupTime: "14:60" }));
  });

  it("denies 9:30 spelled without its leading zero", async () => {
    // Also sorts wrong in the seller's list, which reads these as strings.
    await assertFails(place({ pickupTime: "9:30" }));
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

  // --- who is collecting, and on what number ----------------------------------

  it("accepts a different collector's name", async () => {
    await assertSucceeds(place({ pickupName: "Jonas Weber" }));
  });

  it("accepts an order that names no collector, which means the buyer", async () => {
    await assertSucceeds(place({}));
  });

  it("denies a collector name long enough to be a message", async () => {
    await assertFails(place({ pickupName: "x".repeat(100) }));
  });

  it("denies an empty collector name, which is how absent is spelled", async () => {
    await assertFails(place({ pickupName: "" }));
  });

  it("accepts a phone number written the way people write them", async () => {
    await assertSucceeds(place({ pickupPhone: "+49 (030) 12 34-56" }));
  });

  it("accepts a number with a slash, as German numbers are often written", async () => {
    await assertSucceeds(place({ pickupPhone: "030 / 1234567" }));
  });

  it("denies letters in a phone number", async () => {
    // The row is dialled, not read: a sentence here is a second message field.
    await assertFails(place({ pickupPhone: "ruft bitte an" }));
  });

  it("denies a number longer than any real one", async () => {
    await assertFails(place({ pickupPhone: "1".repeat(31) }));
  });

  it("denies an empty phone number", async () => {
    await assertFails(place({ pickupPhone: "" }));
  });

  it("lets the buyer take their number back off a placed order", async () => {
    await seed(env, async (db) => {
      await db.ref(`orders/${SELLER}/${DATE}/order1`)
        .set(order({ pickupPhone: "0170 1234567" }));
    });

    // Asked per order means withdrawable per order, which a profile field is not.
    await assertSucceeds(
      asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order1/pickupPhone`).remove()
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
