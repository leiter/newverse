import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE, MALLORY } from "./helpers.js";

/**
 * What buyers send through a published message box — see
 * doc/guides/quick-message-wishbox.md.
 *
 * The mirror of `seller_ui_actions`, and the access shape is mirrored too: that node
 * is seller-writable and world-readable, this one is buyer-writable and
 * seller-readable. Two things are load-bearing here and both are tested from the
 * attacker's side. A buyer must not be able to read the node at all — one buyer's
 * wishes are not another's business — and a buyer must not be able to change or
 * delete a message once it is sent, or they could rewrite a wish the seller has
 * already acted on.
 *
 * MALLORY is an ordinary signed-in user: in this app that is anyone who installed it
 * and tapped "Continue as guest", so `auth != null` is not a trust boundary.
 */
describe("seller_inbox", () => {
  let env;

  const message = (overrides = {}) => ({
    id: "msg_1",
    sellerId: SELLER,
    buyerId: ALICE,
    buyerName: "Alice",
    requestKind: "QUICK_MESSAGE",
    requestId: "act_1",
    prompt: "Was fehlt euch im Angebot?",
    text: "Mehr Dinkelmehl",
    timestamp: 1700000000000,
    timestampIso: "2023-11-14T22:13:20Z",
    ...overrides
  });

  const write = (uid, id, body) =>
    asUser(env, uid).ref(`seller_inbox/${SELLER}/${id}`).set(body);

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref(`seller_inbox/${SELLER}/msg_1`).set(message());
    });
  });

  // --- who may read ---------------------------------------------------------

  it("lets the owning seller read their inbox", async () => {
    await assertSucceeds(asUser(env, SELLER).ref(`seller_inbox/${SELLER}`).once("value"));
  });

  it("does not let a buyer read the inbox they wrote to", async () => {
    // Not even their own message. A list of every customer's wishes is the seller's,
    // and there is no buyer-facing feature that needs to read one back.
    await assertFails(asUser(env, ALICE).ref(`seller_inbox/${SELLER}`).once("value"));
  });

  it("does not let a buyer read one message by id", async () => {
    await assertFails(asUser(env, ALICE).ref(`seller_inbox/${SELLER}/msg_1`).once("value"));
  });

  it("does not let another signed-in user read the inbox", async () => {
    await assertFails(asUser(env, MALLORY).ref(`seller_inbox/${SELLER}`).once("value"));
  });

  // --- who may write --------------------------------------------------------

  it("lets a signed-in buyer send a message", async () => {
    // Deliberately not gated on approval: a wishbox the seller published to everyone
    // is answerable by everyone, which is the point of a wishbox.
    await assertSucceeds(write(ALICE, "msg_2", message({ id: "msg_2" })));
  });

  it("lets an unapproved guest send a message", async () => {
    await assertSucceeds(
      write(MALLORY, "msg_3", message({ id: "msg_3", buyerId: MALLORY, buyerName: "" }))
    );
  });

  it("refuses a message that claims another buyer sent it", async () => {
    await assertFails(write(MALLORY, "msg_4", message({ id: "msg_4", buyerId: ALICE })));
  });

  it("refuses a message with no buyerId", async () => {
    const { buyerId, ...withoutSender } = message({ id: "msg_5" });
    await assertFails(write(ALICE, "msg_5", withoutSender));
  });

  it("refuses a message filed under another seller's id", async () => {
    await assertFails(write(ALICE, "msg_6", message({ id: "msg_6", sellerId: MALLORY })));
  });

  it("refuses a message whose id does not match its key", async () => {
    await assertFails(write(ALICE, "msg_7", message({ id: "something_else" })));
  });

  // --- add-only -------------------------------------------------------------

  it("does not let the sender edit a message once it is sent", async () => {
    // The seller may already have acted on it. Rewriting the wish behind their back
    // is the failure this rule exists for.
    await assertFails(write(ALICE, "msg_1", message({ text: "Doch nicht" })));
  });

  it("does not let the sender delete their own message", async () => {
    await assertFails(asUser(env, ALICE).ref(`seller_inbox/${SELLER}/msg_1`).remove());
  });

  it("does not let another buyer delete a message", async () => {
    await assertFails(asUser(env, MALLORY).ref(`seller_inbox/${SELLER}/msg_1`).remove());
  });

  it("lets the seller delete a message once they are done with it", async () => {
    // Deleting is the whole lifecycle — there is no handled flag, because nothing
    // references a message by id.
    await assertSucceeds(asUser(env, SELLER).ref(`seller_inbox/${SELLER}/msg_1`).remove());
  });

  it("lets the seller send to their own inbox from the buy app", async () => {
    // Falls out of the create branch — the seller is a signed-in user whose buyerId
    // matches — and is left that way on purpose. A seller installing the buy app on
    // the same account to check their own wishbox is a thing they will do, and a
    // refusal there looks like a broken feature. There is nothing to protect: the
    // inbox is their own data and nobody else reads it. They still cannot *edit* a
    // row, which is the rule that matters.
    await assertSucceeds(write(SELLER, "msg_8", message({ id: "msg_8", buyerId: SELLER })));
  });

  it("does not let the seller forge a message from a buyer", async () => {
    // The part that does matter: whoever writes the row, buyerId must be their own
    // uid, so a message in the inbox always names who actually sent it.
    await assertFails(write(SELLER, "msg_18", message({ id: "msg_18", buyerId: ALICE })));
  });

  // --- what a message may contain ------------------------------------------

  it("refuses an empty text", async () => {
    await assertFails(write(ALICE, "msg_9", message({ id: "msg_9", text: "" })));
  });

  it("refuses a text over 500 characters", async () => {
    await assertFails(write(ALICE, "msg_10", message({ id: "msg_10", text: "x".repeat(501) })));
  });

  it("accepts a text of exactly 500 characters", async () => {
    await assertSucceeds(
      write(ALICE, "msg_11", message({ id: "msg_11", text: "x".repeat(500) }))
    );
  });

  it("lets a message carry a colon", async () => {
    // Free text, never handed to a URI handler — unlike an OPEN_URL value. "Frage:
    // mehr Mehl" is an ordinary sentence.
    await assertSucceeds(
      write(ALICE, "msg_12", message({ id: "msg_12", text: "Frage: mehr Dinkelmehl?" }))
    );
  });

  it("refuses an unknown requestKind", async () => {
    // A kind this ruleset does not name is one the app does not implement. Same
    // strictness as the published row's own kind.
    await assertFails(
      write(ALICE, "msg_13", message({ id: "msg_13", requestKind: "RATING" }))
    );
  });

  it("refuses a message with no requestKind", async () => {
    const { requestKind, ...withoutKind } = message({ id: "msg_14" });
    await assertFails(write(ALICE, "msg_14", withoutKind));
  });

  it("refuses a buyerName over 120 characters", async () => {
    await assertFails(
      write(ALICE, "msg_15", message({ id: "msg_15", buyerName: "x".repeat(121) }))
    );
  });

  it("refuses a field the ruleset does not name", async () => {
    // $other: validate false. The consequence worth remembering: adding a field to
    // SellerInboxMessage breaks every send until the rules are redeployed — the whole
    // row, not just the new field.
    await assertFails(
      write(ALICE, "msg_16", message({ id: "msg_16", sentiment: "positive" }))
    );
  });

  it("accepts a message with no prompt or requestId", async () => {
    // Both are optional: a box with no question, or a row whose id has since gone.
    const { prompt, requestId, ...minimal } = message({ id: "msg_17" });
    await assertSucceeds(write(ALICE, "msg_17", minimal));
  });
});
