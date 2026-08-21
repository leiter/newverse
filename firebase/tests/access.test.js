import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE, MALLORY } from "./helpers.js";

/**
 * A buyer is identified to the seller by buyerUUID, but that value lives in the
 * buyer's own profile and can be set to anything. Authorisation must key on the
 * auth uid instead.
 */
describe("access requests and approval", () => {
  let env;
  const ALICE_UUID = "uuid-alice";
  const BOB_UUID = "uuid-bob";

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      // Mallory can put anything she likes in her own profile.
      await db.ref(`buyer_profile/${MALLORY}`).set({ id: MALLORY, buyerUUID: BOB_UUID });
      await db.ref(`buyer_access_status/${SELLER}/${BOB_UUID}`).set({
        status: "APPROVED", buyerUUID: BOB_UUID, authUID: "bobUid00000000000000000000", updatedAt: 1
      });
      await db.ref(`access_requests/${SELLER}/${ALICE_UUID}`).set({
        buyerUUID: ALICE_UUID, displayName: "Alice", requestedAt: 1, authUID: ALICE
      });
    });
  });

  // --- self approval -------------------------------------------------------

  it("denies a buyer approving their own access", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/uuid-fresh`).set({
        status: "APPROVED", buyerUUID: "uuid-fresh", authUID: ALICE, updatedAt: 2
      })
    );
  });

  it("denies a buyer upgrading an existing request to approved", async () => {
    await seed(env, (db) =>
      db.ref(`buyer_access_status/${SELLER}/${ALICE_UUID}`).set({
        status: "PENDING", buyerUUID: ALICE_UUID, authUID: ALICE, updatedAt: 1
      })
    );
    await assertFails(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE_UUID}/status`).set("APPROVED")
    );
  });

  it("denies claiming another buyer's uuid by editing your own profile", async () => {
    // Mallory's profile already says her buyerUUID is Bob's.
    const mallory = asUser(env, MALLORY);
    await assertFails(mallory.ref(`buyer_access_status/${SELLER}/${BOB_UUID}`).once("value"));
    await assertFails(mallory.ref(`buyer_access_status/${SELLER}/${BOB_UUID}`).remove());
  });

  it("denies submitting a request in somebody else's name", async () => {
    await assertFails(
      asUser(env, MALLORY).ref(`access_requests/${SELLER}/uuid-victim`).set({
        buyerUUID: "uuid-victim", displayName: "Victim", requestedAt: 2, authUID: ALICE
      })
    );
  });

  // --- blanket write nodes -------------------------------------------------

  it("denies a stranger wiping a seller's pending access requests", async () => {
    await assertFails(asUser(env, MALLORY).ref(`access_requests/${SELLER}`).remove());
    await assertFails(asUser(env, MALLORY).ref(`access_requests/${SELLER}/${ALICE_UUID}`).remove());
  });

  it("denies a stranger reading a seller's pending access requests", async () => {
    await assertFails(asUser(env, MALLORY).ref(`access_requests/${SELLER}`).once("value"));
  });

  it("denies a stranger touching another buyer's invitation index", async () => {
    await assertFails(asUser(env, MALLORY).ref(`buyer_invitations/${ALICE}`).remove());
    await assertFails(asUser(env, MALLORY).ref(`buyer_invitations/${ALICE}/inv1`).set(true));
  });

  it("denies reassigning an invitation to another seller", async () => {
    await seed(env, (db) =>
      db.ref("invitations/inv1").set({
        id: "inv1", sellerId: SELLER, buyerId: MALLORY,
        status: "PENDING", createdAt: 1, expiresAt: 9999999999999
      })
    );
    await assertFails(asUser(env, MALLORY).ref("invitations/inv1/sellerId").set(MALLORY));
  });

  // --- the app itself must still work --------------------------------------

  it("allows a buyer to submit and withdraw their own pending request", async () => {
    const alice = asUser(env, ALICE);
    await assertSucceeds(
      alice.ref(`buyer_access_status/${SELLER}/uuid-fresh`).set({
        status: "PENDING", buyerUUID: "uuid-fresh", authUID: ALICE, updatedAt: 2
      })
    );
    await assertSucceeds(alice.ref(`buyer_access_status/${SELLER}/uuid-fresh`).remove());
    await assertSucceeds(alice.ref(`access_requests/${SELLER}/${ALICE_UUID}`).remove());
  });

  it("allows a buyer to read their own access status", async () => {
    await seed(env, (db) =>
      db.ref(`buyer_access_status/${SELLER}/${ALICE_UUID}`).set({
        status: "PENDING", buyerUUID: ALICE_UUID, authUID: ALICE, updatedAt: 1
      })
    );
    await assertSucceeds(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE_UUID}`).once("value")
    );
  });

  it("allows the seller to approve a request and clear it", async () => {
    const seller = asUser(env, SELLER);
    await assertSucceeds(
      seller.ref(`buyer_access_status/${SELLER}/${ALICE_UUID}`).update({
        status: "APPROVED", updatedAt: 3, buyerUUID: ALICE_UUID
      })
    );
    await assertSucceeds(seller.ref(`access_requests/${SELLER}/${ALICE_UUID}`).remove());
    await assertSucceeds(seller.ref(`access_requests/${SELLER}`).once("value"));
  });

  it("allows a buyer to manage their own invitation index", async () => {
    await assertSucceeds(asUser(env, ALICE).ref(`buyer_invitations/${ALICE}/inv1`).set(true));
  });

  it("allows a seller to index an invitation they own onto a buyer", async () => {
    await seed(env, (db) =>
      db.ref("invitations/inv2").set({
        id: "inv2", sellerId: SELLER, buyerId: ALICE,
        status: "PENDING", createdAt: 1, expiresAt: 9999999999999
      })
    );
    await assertSucceeds(asUser(env, SELLER).ref(`buyer_invitations/${ALICE}/inv2`).set(true));
  });
});
