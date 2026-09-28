import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE, BOB, MALLORY } from "./helpers.js";

/**
 * Invite tokens are bearer claim tickets. The seller mints one before the buyer has
 * any identity — for a QR code at a market stall — so the token cannot be keyed on the
 * buyer's auth uid. Whoever presents it first redeems it, exactly once, and from then
 * on every durable record keys on their auth uid instead.
 *
 * The rule that makes this work is "writable because unclaimed": a buyer may set
 * redeemedBy only while it is absent. That is what the buyer_access_status rules cannot
 * express — they require data.authUID === auth.uid, so a buyer must already own a record
 * in order to claim it, and seller pre-approval deadlocks.
 */
describe("invite tokens", () => {
  let env;
  const TOKEN = "token-from-qr-code";
  const OTHER_SELLER = "seller2Uid0000000000000000";
  const HOUR = 60 * 60 * 1000;

  const unredeemed = (expiresInMs = HOUR) => ({
    createdAt: Date.now(),
    expiresAt: Date.now() + expiresInMs,
    displayNameHint: "Stall walk-in"
  });

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => { await env.clearDatabase(); });

  // --- seller side ---------------------------------------------------------

  it("allows the seller to mint a token", async () => {
    await assertSucceeds(
      asUser(env, SELLER).ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed())
    );
  });

  it("allows the seller to list their own tokens", async () => {
    await seed(env, (db) => db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed()));
    await assertSucceeds(
      asUser(env, SELLER).ref(`invite_tokens/${SELLER}`).once("value")
    );
  });

  it("allows the seller to revoke a token", async () => {
    await seed(env, (db) => db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed()));
    await assertSucceeds(
      asUser(env, SELLER).ref(`invite_tokens/${SELLER}/${TOKEN}`).remove()
    );
  });

  it("denies another seller minting a token in your namespace", async () => {
    await assertFails(
      asUser(env, OTHER_SELLER).ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed())
    );
  });

  it("denies a stranger enumerating a seller's tokens", async () => {
    await seed(env, (db) => db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed()));
    await assertFails(
      asUser(env, MALLORY).ref(`invite_tokens/${SELLER}`).once("value")
    );
  });

  // --- redemption ----------------------------------------------------------

  it("allows a buyer to read an unredeemed token they hold", async () => {
    await seed(env, (db) => db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed()));
    await assertSucceeds(
      asUser(env, ALICE).ref(`invite_tokens/${SELLER}/${TOKEN}`).once("value")
    );
  });

  it("allows a buyer to redeem an unredeemed token", async () => {
    await seed(env, (db) => db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed()));
    await assertSucceeds(
      asUser(env, ALICE).ref(`invite_tokens/${SELLER}/${TOKEN}/redeemedBy`).set(ALICE)
    );
  });

  it("denies a second buyer redeeming an already redeemed token", async () => {
    await seed(env, (db) =>
      db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set({ ...unredeemed(), redeemedBy: ALICE })
    );
    await assertFails(
      asUser(env, BOB).ref(`invite_tokens/${SELLER}/${TOKEN}/redeemedBy`).set(BOB)
    );
  });

  it("denies redeeming in somebody else's name", async () => {
    await seed(env, (db) => db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed()));
    await assertFails(
      asUser(env, MALLORY).ref(`invite_tokens/${SELLER}/${TOKEN}/redeemedBy`).set(ALICE)
    );
  });

  it("allows the redeemer to still read the token afterwards", async () => {
    await seed(env, (db) =>
      db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set({ ...unredeemed(), redeemedBy: ALICE })
    );
    await assertSucceeds(
      asUser(env, ALICE).ref(`invite_tokens/${SELLER}/${TOKEN}`).once("value")
    );
  });

  it("denies a stranger reading a token once it is redeemed", async () => {
    await seed(env, (db) =>
      db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set({ ...unredeemed(), redeemedBy: ALICE })
    );
    await assertFails(
      asUser(env, MALLORY).ref(`invite_tokens/${SELLER}/${TOKEN}`).once("value")
    );
  });

  it("denies redeeming an expired token", async () => {
    await seed(env, (db) =>
      db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed(-HOUR))
    );
    await assertFails(
      asUser(env, ALICE).ref(`invite_tokens/${SELLER}/${TOKEN}/redeemedBy`).set(ALICE)
    );
  });

  // --- tampering -----------------------------------------------------------

  it("denies a buyer extending the expiry while redeeming", async () => {
    await seed(env, (db) => db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed()));
    const base = unredeemed();
    await assertFails(
      asUser(env, ALICE).ref(`invite_tokens/${SELLER}/${TOKEN}`).set({
        ...base, expiresAt: Date.now() + 100 * HOUR, redeemedBy: ALICE
      })
    );
  });

  it("denies a buyer rewriting the display name hint while redeeming", async () => {
    await seed(env, (db) => db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed()));
    await assertFails(
      asUser(env, ALICE).ref(`invite_tokens/${SELLER}/${TOKEN}/displayNameHint`).set("mine now")
    );
  });

  it("denies a buyer minting a token out of nothing", async () => {
    await assertFails(
      asUser(env, MALLORY).ref(`invite_tokens/${SELLER}/${TOKEN}`).set({
        ...unredeemed(), redeemedBy: MALLORY
      })
    );
  });

  it("denies a buyer deleting a token", async () => {
    await seed(env, (db) => db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed()));
    await assertFails(
      asUser(env, ALICE).ref(`invite_tokens/${SELLER}/${TOKEN}`).remove()
    );
  });

  it("denies a buyer smuggling an unknown field in on redemption", async () => {
    await seed(env, (db) => db.ref(`invite_tokens/${SELLER}/${TOKEN}`).set(unredeemed()));
    await assertFails(
      asUser(env, ALICE).ref(`invite_tokens/${SELLER}/${TOKEN}/grantsAdmin`).set(true)
    );
  });
});
