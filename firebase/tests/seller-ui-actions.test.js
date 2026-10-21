import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE, MALLORY } from "./helpers.js";

/**
 * Buttons the seller publishes into the buy app — see doc/guides/seller-ui-actions.md.
 *
 * Same access shape as `markets`: readable by every signed-in user because the buy
 * app renders them before a buyer is approved, writable only by the seller who owns
 * them. The interesting rules here are the validations, because this node is the one
 * place a seller's input reaches the inside of the buy app's chrome — so `value` must
 * stay scheme-less and `kind` and `location` must name something the app implements.
 */
describe("seller_ui_actions", () => {
  let env;
  const OTHER_SELLER = "otherSellerUid000000000000";

  const action = (overrides = {}) => ({
    id: "act_1",
    kind: "OPEN_URL",
    label: "Wünsche",
    value: "example.org/wuensche",
    location: "TOPBAR_PROFILE",
    ...overrides
  });

  const write = (uid, id, body) =>
    asUser(env, uid).ref(`seller_ui_actions/${SELLER}/${id}`).set(body);

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });

  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref(`seller_ui_actions/${SELLER}/act_1`).set(action());
    });
  });

  // --- who may read ---------------------------------------------------------

  it("lets any signed-in user read a seller's actions", async () => {
    await assertSucceeds(asUser(env, ALICE).ref(`seller_ui_actions/${SELLER}`).once("value"));
  });

  it("lets an unapproved guest read them too", async () => {
    // Deliberate: the buy app renders the top bar before anyone is approved, and
    // hiding the buttons until approval would make them look broken for new installs.
    await assertSucceeds(asUser(env, MALLORY).ref(`seller_ui_actions/${SELLER}`).once("value"));
  });

  // --- who may write --------------------------------------------------------

  it("lets the owning seller publish an action", async () => {
    await assertSucceeds(write(SELLER, "act_2", action({ id: "act_2", location: "TOPBAR_HOME" })));
  });

  it("lets the owning seller remove an action", async () => {
    await assertSucceeds(
      asUser(env, SELLER).ref(`seller_ui_actions/${SELLER}/act_1`).remove()
    );
  });

  it("refuses a buyer publishing into a seller's top bar", async () => {
    // The whole point of the node: a buyer who could write here would be putting a
    // button of their choosing inside every other buyer's app.
    await assertFails(write(ALICE, "act_2", action({ id: "act_2" })));
  });

  it("refuses a buyer removing a seller's action", async () => {
    await assertFails(asUser(env, ALICE).ref(`seller_ui_actions/${SELLER}/act_1`).remove());
  });

  it("refuses another seller writing into this seller's actions", async () => {
    await assertFails(write(OTHER_SELLER, "act_2", action({ id: "act_2" })));
  });

  // --- the value may not carry a scheme ------------------------------------

  it("refuses a value containing a scheme", async () => {
    // ActionKind supplies https://. A stored scheme would let the seller choose one.
    await assertFails(write(SELLER, "act_2", action({ id: "act_2", value: "https://example.org" })));
  });

  it("refuses an intent:// payload", async () => {
    await assertFails(
      write(SELLER, "act_2", action({ id: "act_2", value: "intent://evil#Intent;end" }))
    );
  });

  it("refuses a javascript: payload", async () => {
    await assertFails(
      write(SELLER, "act_2", action({ id: "act_2", value: "javascript:alert(1)" }))
    );
  });

  it("refuses a protocol-relative value", async () => {
    // "//evil.test" would resolve against the scheme and reach another host.
    await assertFails(write(SELLER, "act_2", action({ id: "act_2", value: "//evil.test" })));
  });

  it("refuses an empty value", async () => {
    await assertFails(write(SELLER, "act_2", action({ id: "act_2", value: "" })));
  });

  // --- kind and location must name something the app implements ------------

  it("refuses an unknown kind", async () => {
    await assertFails(write(SELLER, "act_2", action({ id: "act_2", kind: "RUN_SCRIPT" })));
  });

  it("refuses an unknown location", async () => {
    await assertFails(write(SELLER, "act_2", action({ id: "act_2", location: "EVERYWHERE" })));
  });

  // --- shape ----------------------------------------------------------------

  it("refuses an id that disagrees with its key", async () => {
    await assertFails(write(SELLER, "act_2", action({ id: "act_9" })));
  });

  it("refuses a missing required field", async () => {
    const { label, ...withoutLabel } = action({ id: "act_2" });
    await assertFails(write(SELLER, "act_2", withoutLabel));
  });

  it("refuses an empty label", async () => {
    await assertFails(write(SELLER, "act_2", action({ id: "act_2", label: "" })));
  });

  it("refuses a label too long for a top bar", async () => {
    await assertFails(write(SELLER, "act_2", action({ id: "act_2", label: "x".repeat(25) })));
  });

  it("refuses an unknown field", async () => {
    await assertFails(
      write(SELLER, "act_2", action({ id: "act_2", onTap: "doSomethingElse" }))
    );
  });
});
