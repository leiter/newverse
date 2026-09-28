import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE, BOB, MALLORY } from "./helpers.js";

/**
 * Access records are keyed by the buyer's Firebase auth uid. The key *is* the
 * identity, so ownership is `$buyerUid === auth.uid` and there is no indirection
 * through an `authUID` field to get wrong.
 *
 * This replaces the buyerUUID-keyed model, where a buyer picked their own
 * identifier in their own profile and the rules had to cross-check it. That
 * indirection is what made seller pre-approval unredeemable: a record written by
 * the seller had no authUID, and the rules demanded you already own a record in
 * order to claim it. Pre-approval now goes through `invite_tokens` instead —
 * see invite-tokens.test.js.
 */
describe("access requests and approval", () => {
  let env;

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref(`buyer_access_status/${SELLER}/${BOB}`).set({
        status: "APPROVED", updatedAt: 1, displayName: "Bob"
      });
      await db.ref(`access_requests/${SELLER}/${ALICE}`).set({
        displayName: "Alice", requestedAt: 1
      });
    });
  });

  // --- self approval -------------------------------------------------------

  it("denies a buyer approving their own access", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}`).set({
        status: "APPROVED", updatedAt: 2
      })
    );
  });

  it("denies a buyer upgrading an existing request to approved", async () => {
    await seed(env, (db) =>
      db.ref(`buyer_access_status/${SELLER}/${ALICE}`).set({ status: "PENDING", updatedAt: 1 })
    );
    await assertFails(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}/status`).set("APPROVED")
    );
  });

  // --- one buyer, one key --------------------------------------------------

  it("denies reading or removing another buyer's access record", async () => {
    const mallory = asUser(env, MALLORY);
    await assertFails(mallory.ref(`buyer_access_status/${SELLER}/${BOB}`).once("value"));
    await assertFails(mallory.ref(`buyer_access_status/${SELLER}/${BOB}`).remove());
  });

  it("denies creating an access record under somebody else's uid", async () => {
    await assertFails(
      asUser(env, MALLORY).ref(`buyer_access_status/${SELLER}/${ALICE}`).set({
        status: "PENDING", updatedAt: 2
      })
    );
  });

  it("denies submitting a request under somebody else's uid", async () => {
    await assertFails(
      asUser(env, MALLORY).ref(`access_requests/${SELLER}/${ALICE}`).set({
        displayName: "Victim", requestedAt: 2
      })
    );
  });

  // --- blanket write nodes -------------------------------------------------

  it("denies a stranger wiping a seller's pending access requests", async () => {
    await assertFails(asUser(env, MALLORY).ref(`access_requests/${SELLER}`).remove());
    await assertFails(asUser(env, MALLORY).ref(`access_requests/${SELLER}/${ALICE}`).remove());
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
      alice.ref(`buyer_access_status/${SELLER}/${ALICE}`).set({ status: "PENDING", updatedAt: 2 })
    );
    await assertSucceeds(alice.ref(`buyer_access_status/${SELLER}/${ALICE}`).remove());
    await assertSucceeds(alice.ref(`access_requests/${SELLER}/${ALICE}`).remove());
  });

  it("allows a buyer with an existing record to request access again", async () => {
    // submitAccessRequest uses setValue, which overwrites. A buyer retrying
    // after a lingering or rejected request must not be locked out.
    await seed(env, async (db) => {
      await db.ref(`access_requests/${SELLER}/${ALICE}`).set({
        displayName: "Alice", requestedAt: 1
      });
      await db.ref(`buyer_access_status/${SELLER}/${ALICE}`).set({
        status: "PENDING", updatedAt: 1, displayName: "Alice"
      });
    });
    const alice = asUser(env, ALICE);
    await assertSucceeds(alice.ref(`access_requests/${SELLER}/${ALICE}`).set({
      displayName: "Alice", requestedAt: 2
    }));
    await assertSucceeds(alice.ref(`buyer_access_status/${SELLER}/${ALICE}`).set({
      status: "PENDING", updatedAt: 2, displayName: "Alice"
    }));
  });

  it("denies a blocked buyer resetting themselves back to pending", async () => {
    await seed(env, (db) =>
      db.ref(`buyer_access_status/${SELLER}/${ALICE}`).set({
        status: "BLOCKED", updatedAt: 1, displayName: "Alice"
      })
    );
    const alice = asUser(env, ALICE);
    await assertFails(alice.ref(`buyer_access_status/${SELLER}/${ALICE}`).set({
      status: "PENDING", updatedAt: 2, displayName: "Alice"
    }));
    // nor by deleting the block and starting over
    await assertFails(alice.ref(`buyer_access_status/${SELLER}/${ALICE}`).remove());
    await assertFails(alice.ref(`access_requests/${SELLER}/${ALICE}`).set({
      displayName: "Alice", requestedAt: 2
    }));
  });

  it("allows a buyer to read their own access status", async () => {
    await seed(env, (db) =>
      db.ref(`buyer_access_status/${SELLER}/${ALICE}`).set({ status: "PENDING", updatedAt: 1 })
    );
    await assertSucceeds(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}`).once("value")
    );
  });

  it("allows an approved buyer to update their own display name", async () => {
    await seed(env, (db) =>
      db.ref(`buyer_access_status/${SELLER}/${ALICE}`).set({
        status: "APPROVED", updatedAt: 1, displayName: "Alice"
      })
    );
    await assertSucceeds(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}/displayName`).set("Alicia")
    );
  });

  it("denies an approved buyer smuggling a status change into a display name update", async () => {
    await seed(env, (db) =>
      db.ref(`buyer_access_status/${SELLER}/${ALICE}`).set({
        status: "APPROVED", updatedAt: 1, displayName: "Alice"
      })
    );
    await assertFails(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}`).set({
        status: "BLOCKED", updatedAt: 2, displayName: "Alicia"
      })
    );
  });

  it("denies a blocked buyer updating their own display name", async () => {
    await seed(env, (db) =>
      db.ref(`buyer_access_status/${SELLER}/${ALICE}`).set({
        status: "BLOCKED", updatedAt: 1, displayName: "Alice"
      })
    );
    await assertFails(
      asUser(env, ALICE).ref(`buyer_access_status/${SELLER}/${ALICE}/displayName`).set("Alicia")
    );
  });

  it("allows the seller to approve a request and clear it", async () => {
    const seller = asUser(env, SELLER);
    await assertSucceeds(
      seller.ref(`buyer_access_status/${SELLER}/${ALICE}`).update({
        status: "APPROVED", updatedAt: 3
      })
    );
    await assertSucceeds(seller.ref(`access_requests/${SELLER}/${ALICE}`).remove());
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
