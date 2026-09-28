import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE, BOB, MALLORY } from "./helpers.js";

describe("seller profile, orders and the event log", () => {
  let env;
  const DATE = "2026-08-27";
  const CARL = "carlUid00000000000000000000";

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref(`seller_profile/${SELLER}`).set({
        id: SELLER,
        displayName: "Hof Sonnenblume",
        city: "Berlin",
        // The customer roster. Every list is keyed by the buyer's auth uid.
        // Bob is a known client but not an approved buyer, which is what
        // separates the orders read gate from the event-log write gate.
        knownClientIds: { [ALICE]: true, [BOB]: true },
        approvedBuyerIds: { [ALICE]: "Alice Schmidt" },
        blockedClientIds: { [CARL]: "Carl Meyer" }
      });
      await db.ref(`orders/${SELLER}/${DATE}/order1`).set({
        id: "order1", buyerId: ALICE, pickUpDate: 9999999999999, status: "PLACED"
      });
    });
  });

  // --- the customer roster is not public -----------------------------------

  it("denies a stranger reading the seller's client lists", async () => {
    const mallory = asUser(env, MALLORY);
    await assertFails(mallory.ref(`seller_profile/${SELLER}/knownClientIds`).once("value"));
    await assertFails(mallory.ref(`seller_profile/${SELLER}/approvedBuyerIds`).once("value"));
    await assertFails(mallory.ref(`seller_profile/${SELLER}/blockedClientIds`).once("value"));
  });

  it("denies a stranger reading the whole seller profile at once", async () => {
    await assertFails(asUser(env, MALLORY).ref(`seller_profile/${SELLER}`).once("value"));
  });

  it("allows any signed-in buyer to read the seller's public display name", async () => {
    await assertSucceeds(
      asUser(env, MALLORY).ref(`seller_profile/${SELLER}/displayName`).once("value")
    );
  });

  it("allows the seller to read their own profile in full", async () => {
    await assertSucceeds(asUser(env, SELLER).ref(`seller_profile/${SELLER}`).once("value"));
  });

  it("denies a buyer editing the seller's profile", async () => {
    await assertFails(
      asUser(env, MALLORY).ref(`seller_profile/${SELLER}/displayName`).set("hacked")
    );
  });

  // --- orders --------------------------------------------------------------

  it("denies placing an order in another buyer's name", async () => {
    await assertFails(
      asUser(env, MALLORY).ref(`orders/${SELLER}/${DATE}/order2`).set({
        id: "order2", buyerId: ALICE, pickUpDate: 9999999999999, status: "PLACED"
      })
    );
  });

  it("denies a non-client reading the seller's orders", async () => {
    await assertFails(asUser(env, MALLORY).ref(`orders/${SELLER}`).once("value"));
  });

  it("denies a client subscribing to the seller's whole order tree", async () => {
    // Every order embeds a buyer snapshot including their email, so a tree-wide
    // read would hand any one customer the contact details of all the others.
    // Buyers observe one listener per known order instead - observeBuyerOrders.
    await assertFails(asUser(env, ALICE).ref(`orders/${SELLER}`).once("value"));
    await assertFails(asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}`).once("value"));
  });

  it("allows a client to place and read their own order", async () => {
    const alice = asUser(env, ALICE);
    await assertSucceeds(
      alice.ref(`orders/${SELLER}/${DATE}/order3`).set({
        id: "order3", buyerId: ALICE, pickUpDate: 9999999999999, status: "PLACED"
      })
    );
    await assertSucceeds(alice.ref(`orders/${SELLER}/${DATE}/order3`).once("value"));
  });

  it("denies a client reading another buyer's order", async () => {
    await seed(env, (db) =>
      db.ref(`orders/${SELLER}/${DATE}/order9`).set({
        id: "order9", buyerId: BOB, pickUpDate: 9999999999999, status: "PLACED"
      })
    );
    await assertFails(asUser(env, ALICE).ref(`orders/${SELLER}/${DATE}/order9`).once("value"));
  });

  it("allows the seller to read the whole order tree", async () => {
    await assertSucceeds(asUser(env, SELLER).ref(`orders/${SELLER}`).once("value"));
  });

  // --- the book keeping log ------------------------------------------------

  const event = (uid) => ({
    id: "evt1", sellerId: SELLER, type: "ACCOUNT_DELETED",
    buyerId: uid, firebaseUserId: uid,
    timestamp: 1, timestampIso: "2026-08-21T08:00:00Z",
    buyerUUID: "", buyerName: "", buyerEmail: "", cancelledOrderCount: 0, details: ""
  });

  it("denies a non-client appending to the seller's event log", async () => {
    await assertFails(
      asUser(env, MALLORY).ref(`seller_events/${SELLER}/evt1`).set(event(MALLORY))
    );
  });

  it("denies a known client who is not an approved buyer appending", async () => {
    // Bob is in knownClientIds but not approvedBuyerIds. The write gate moved to
    // approvedBuyerIds so that eligibility matches the access list the seller
    // actually manages, rather than knownClientIds which is populated from two
    // call sites and allowed to fail silently on one of them.
    await assertFails(
      asUser(env, BOB).ref(`seller_events/${SELLER}/evt1`).set(event(BOB))
    );
  });

  it("denies logging an event under somebody else's uid", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`seller_events/${SELLER}/evt1`).set(event(MALLORY))
    );
  });

  it("denies a buyer reading the seller's event log", async () => {
    await assertFails(asUser(env, ALICE).ref(`seller_events/${SELLER}`).once("value"));
  });

  it("keeps the log append-only, even for its author", async () => {
    const alice = asUser(env, ALICE);
    await assertSucceeds(alice.ref(`seller_events/${SELLER}/evt1`).set(event(ALICE)));
    await assertFails(alice.ref(`seller_events/${SELLER}/evt1/details`).set("rewritten"));
    await assertFails(alice.ref(`seller_events/${SELLER}/evt1`).remove());
  });

  it("allows an approved buyer to append, and the seller to read the log", async () => {
    await assertSucceeds(asUser(env, ALICE).ref(`seller_events/${SELLER}/evt1`).set(event(ALICE)));
    await assertSucceeds(asUser(env, SELLER).ref(`seller_events/${SELLER}`).once("value"));
  });

  it("allows the seller to prune the log but not to forge an entry", async () => {
    // The drain deletes applied change events — RTDB has no TTL and buyers
    // cannot delete. The log stays buyer-authored, so the seller cannot append.
    await seed(env, (db) => db.ref(`seller_events/${SELLER}/evt1`).set(event(ALICE)));
    const seller = asUser(env, SELLER);
    await assertFails(seller.ref(`seller_events/${SELLER}/evt2`).set(event(ALICE)));
    await assertSucceeds(seller.ref(`seller_events/${SELLER}/evt1`).remove());
  });
});
