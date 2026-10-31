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

  // --- the slot decides the kind -------------------------------------------
  //
  // An ANNOUNCE in a button slot has nothing to open and an OPEN_URL in the title
  // slot has no button to be tapped. Neither is a degraded rendering the app could
  // fall back on, so the pairing is refused here rather than filtered in the client.

  const announcement = (overrides = {}) => action({
    kind: "ANNOUNCE",
    location: "TOPBAR_HOME_TITLE",
    label: "Bodenschätze",
    value: "Heute ab 9: Bodenschätze aus der Region",
    ...overrides
  });

  it("accepts an announcement on the home title", async () => {
    await assertSucceeds(write(SELLER, "act_2", announcement({ id: "act_2" })));
  });

  it("refuses an OPEN_URL on the home title", async () => {
    await assertFails(
      write(SELLER, "act_2", announcement({ id: "act_2", kind: "OPEN_URL", value: "example.org" }))
    );
  });

  it("refuses an ANNOUNCE in a button slot", async () => {
    await assertFails(
      write(SELLER, "act_2", announcement({ id: "act_2", location: "TOPBAR_HOME" }))
    );
  });

  // --- an announcement is text, not an address -----------------------------

  it("lets an announcement carry a colon", async () => {
    // The no-colon check exists to stop a stored scheme reaching the platform's URI
    // handler. An announcement never reaches it, and "Heute ab 9: ..." is an ordinary
    // German sentence. Loosened for this kind alone, deliberately.
    await assertSucceeds(
      write(SELLER, "act_2", announcement({ id: "act_2", value: "Neu: Bodenschätze" }))
    );
  });

  it("still refuses a colon in an OPEN_URL value", async () => {
    await assertFails(
      write(SELLER, "act_2", action({ id: "act_2", value: "https://evil.test" }))
    );
  });

  it("still refuses a leading slash in an OPEN_URL value", async () => {
    // "//evil.test" would resolve against the scheme and reach another host.
    await assertFails(
      write(SELLER, "act_2", action({ id: "act_2", value: "/evil.test" }))
    );
  });

  it("refuses an announcement longer than the stored cap", async () => {
    await assertFails(
      write(SELLER, "act_2", announcement({ id: "act_2", value: "x".repeat(501) }))
    );
  });

  // --- audience -------------------------------------------------------------

  it("accepts each audience the app implements", async () => {
    for (const audience of ["EVERYONE", "DEMO_ONLY", "APPROVED_ONLY"]) {
      await assertSucceeds(write(SELLER, "act_2", action({ id: "act_2", audience })));
    }
  });

  it("refuses an unknown audience", async () => {
    await assertFails(write(SELLER, "act_2", action({ id: "act_2", audience: "STAFF" })));
  });

  it("still accepts a row with no audience at all", async () => {
    // Optional on purpose: rows written before audiences existed were shown to
    // everyone and must stay valid, which is why it is not in hasChildren.
    const { audience, ...withoutAudience } = action({ id: "act_2", audience: "EVERYONE" });
    await assertSucceeds(write(SELLER, "act_2", withoutAudience));
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

  // --- the message box ------------------------------------------------------

  const box = (overrides = {}) => ({
    id: "q_1",
    kind: "QUICK_MESSAGE",
    label: "Was fehlt euch",
    value: "Was fehlt euch im Angebot?",
    location: "TOPBAR_HOME_INPUT",
    ...overrides
  });

  it("accepts a message box in either input slot", async () => {
    for (const location of ["TOPBAR_HOME_INPUT", "TOPBAR_PROFILE_INPUT"]) {
      await assertSucceeds(write(SELLER, "q_1", box({ location })));
    }
  });

  it("lets a question carry a colon", async () => {
    // Same lift as an announcement, and for the same reason: a QUICK_MESSAGE value is
    // a sentence shown as a placeholder, never handed to a URI handler.
    await assertSucceeds(write(SELLER, "q_1", box({ value: "Frage: was fehlt euch?" })));
  });

  it("still refuses a colon in an OPEN_URL value", async () => {
    await assertFails(write(SELLER, "act_2", action({ id: "act_2", value: "javascript:alert(1)" })));
  });

  it("refuses a QUICK_MESSAGE outside an input slot", async () => {
    // Nothing to render: a message box in a button slot has no room, and in the
    // announcement slot there would be two things claiming the title.
    for (const location of ["TOPBAR_HOME", "TOPBAR_PROFILE", "TOPBAR_HOME_TITLE"]) {
      await assertFails(write(SELLER, "q_1", box({ location })));
    }
  });

  it("refuses a button or an announcement in an input slot", async () => {
    await assertFails(
      write(SELLER, "q_1", box({ kind: "OPEN_URL", value: "example.org" }))
    );
    await assertFails(write(SELLER, "q_1", box({ kind: "ANNOUNCE" })));
  });

  // --- switched off, and hidden once used -----------------------------------

  it("accepts a row the seller switched off", async () => {
    // Off, not gone: the seller keeps the wording. The buy app drops it on read.
    await assertSucceeds(write(SELLER, "act_2", action({ id: "act_2", active: false })));
  });

  it("refuses a non-boolean active flag", async () => {
    await assertFails(write(SELLER, "act_2", action({ id: "act_2", active: "no" })));
  });

  it("accepts both persistence values on a row that can be acted on", async () => {
    for (const persistence of ["UNTIL_CANCELLED", "HIDE_ON_ACTION"]) {
      await assertSucceeds(write(SELLER, "act_2", action({ id: "act_2", persistence })));
      await assertSucceeds(write(SELLER, "q_1", box({ persistence })));
    }
  });

  it("refuses HIDE_ON_ACTION on an announcement", async () => {
    // An announcement has no action to hide on, so the pairing is nonsense rather
    // than a degraded rendering — refused here as well as settled in the model.
    await assertFails(
      write(SELLER, "n_1", announcement({ id: "n_1", persistence: "HIDE_ON_ACTION" }))
    );
  });

  it("accepts UNTIL_CANCELLED on an announcement", async () => {
    await assertSucceeds(
      write(SELLER, "n_1", announcement({ id: "n_1", persistence: "UNTIL_CANCELLED" }))
    );
  });

  it("refuses an unknown persistence", async () => {
    await assertFails(
      write(SELLER, "act_2", action({ id: "act_2", persistence: "HIDE_AFTER_A_WEEK" }))
    );
  });

  it("still accepts a row with neither flag", async () => {
    // Both optional, like audience: rows written before the flags existed were active
    // and stayed until cancelled, and must remain valid.
    await assertSucceeds(write(SELLER, "act_2", action({ id: "act_2" })));
  });
});
