import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import {
  createTestEnv, asUser, seed, conversationId,
  SELLER, ALICE, BOB, MALLORY
} from "./helpers.js";

/**
 * Conversation ids are derived from the participants (Conversation.createId),
 * so an attacker can compute the id of any conversation. Access must not depend
 * on anything the attacker can write for themselves.
 */
describe("conversations and messages", () => {
  let env;
  const aliceSeller = conversationId(ALICE, SELLER);

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref(`conversations/${aliceSeller}`).set({
        participantIds: { 0: [ALICE, SELLER].sort()[0], 1: [ALICE, SELLER].sort()[1] },
        participantNames: { [ALICE]: "Alice", [SELLER]: "Seller" },
        lastMessage: "see you thursday"
      });
      await db.ref(`messages/${aliceSeller}/msg1`).set({
        id: "msg1", senderId: ALICE, senderName: "Alice",
        text: "is the rhubarb still available?", timestamp: 1, isRead: false
      });
      await db.ref(`user_conversations/${ALICE}/${aliceSeller}`).set(true);
      await db.ref(`user_conversations/${SELLER}/${aliceSeller}`).set(true);
    });
  });

  // --- the critical bypass -------------------------------------------------

  it("denies a stranger writing themselves into a conversation index", async () => {
    await assertFails(
      asUser(env, MALLORY).ref(`user_conversations/${MALLORY}/${aliceSeller}`).set(true)
    );
  });

  it("denies a stranger reading a conversation they are not part of", async () => {
    await assertFails(asUser(env, MALLORY).ref(`conversations/${aliceSeller}`).once("value"));
  });

  it("denies a stranger reading the messages of a conversation", async () => {
    await assertFails(asUser(env, MALLORY).ref(`messages/${aliceSeller}`).once("value"));
  });

  it("denies a stranger posting into a conversation", async () => {
    await assertFails(
      asUser(env, MALLORY).ref(`messages/${aliceSeller}/spam`).set({
        id: "spam", senderId: MALLORY, senderName: "Mallory",
        text: "click here", timestamp: 2, isRead: false
      })
    );
  });

  it("denies a participant pulling an outsider into their conversation", async () => {
    await assertFails(
      asUser(env, ALICE).ref(`user_conversations/${MALLORY}/${aliceSeller}`).set(true)
    );
  });

  // --- messages stay owned by their sender ---------------------------------

  it("denies overwriting somebody else's message", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`messages/${aliceSeller}/msg1`).set({
        id: "msg1", senderId: SELLER, senderName: "Seller",
        text: "(edited)", timestamp: 1, isRead: false
      })
    );
  });

  it("denies deleting somebody else's message", async () => {
    await assertFails(asUser(env, SELLER).ref(`messages/${aliceSeller}/msg1`).remove());
  });

  it("denies sending a message under a forged senderId", async () => {
    await assertFails(
      asUser(env, SELLER).ref(`messages/${aliceSeller}/forged`).set({
        id: "forged", senderId: ALICE, senderName: "Alice",
        text: "i agree to pay double", timestamp: 3, isRead: false
      })
    );
  });

  // --- the app itself must still work --------------------------------------

  it("allows a participant to read their own conversation and messages", async () => {
    const alice = asUser(env, ALICE);
    await assertSucceeds(alice.ref(`conversations/${aliceSeller}`).once("value"));
    await assertSucceeds(alice.ref(`messages/${aliceSeller}`).once("value"));
  });

  it("allows a participant to send a message", async () => {
    await assertSucceeds(
      asUser(env, SELLER).ref(`messages/${aliceSeller}/msg2`).set({
        id: "msg2", senderId: SELLER, senderName: "Seller",
        text: "yes, two bunches left", timestamp: 4, isRead: false
      })
    );
  });

  it("allows the recipient to mark a message as read", async () => {
    await assertSucceeds(
      asUser(env, SELLER).ref(`messages/${aliceSeller}/msg1/isRead`).set(true)
    );
  });

  it("allows a participant to index a conversation for both sides", async () => {
    const bobSeller = conversationId(BOB, SELLER);
    const bob = asUser(env, BOB);
    await assertSucceeds(bob.ref(`user_conversations/${BOB}/${bobSeller}`).set(true));
    await assertSucceeds(bob.ref(`user_conversations/${SELLER}/${bobSeller}`).set(true));
  });
});
