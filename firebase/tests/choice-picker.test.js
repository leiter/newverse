import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE } from "./helpers.js";

/**
 * The choice picker: a published question with a fixed set of answers.
 *
 * `CHOICE` is the second `ActionKind` to carry a question rather than a target, and
 * the first to carry a *list*. Two new fields come with it — `options` on the
 * published row, `selections` on the message that answers it — and both are pinned
 * here, because `$other: false` guards both nodes: a field this file does not name
 * is a field the database refuses, and the failure arrives at the buyer's send.
 *
 * The load-bearing decision these tests protect is that answers are stored as
 * **labels, not indices**. A stored index points at a row that is usually gone by
 * the time the seller reads the message, and reordering the options would silently
 * re-map every answer already filed. So `selections` carries strings, and the rules
 * refuse anything else.
 *
 * See doc/guides/quick-message-wishbox.md for the mechanism these extend.
 */
describe("choice picker", () => {
  let env;

  const row = (overrides = {}) => ({
    id: "act_choice",
    kind: "CHOICE",
    label: "Was sollen wir",
    value: "Was sollen wir nächste Woche mitbringen?",
    options: ["Bergkäse", "Dinkelmehl", "Eier"],
    selectionMode: "MULTI",
    location: "TOPBAR_HOME_CHOICE",
    audience: "EVERYONE",
    active: true,
    persistence: "UNTIL_CANCELLED",
    ...overrides
  });

  const answer = (overrides = {}) => ({
    id: "msg_choice",
    sellerId: SELLER,
    buyerId: ALICE,
    buyerName: "Alice",
    requestKind: "CHOICE",
    requestId: "act_choice",
    prompt: "Was sollen wir nächste Woche mitbringen?",
    options: ["Bergkäse", "Dinkelmehl", "Eier"],
    selections: ["Dinkelmehl", "Eier"],
    text: "Dinkelmehl, Eier",
    timestamp: 1700000000000,
    timestampIso: "2023-11-14T22:13:20Z",
    ...overrides
  });

  const publish = (uid, id, body) =>
    asUser(env, uid).ref(`seller_ui_actions/${SELLER}/${id}`).set(body);

  const send = (uid, id, body) =>
    asUser(env, uid).ref(`seller_inbox/${SELLER}/${id}`).set(body);

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => { await env.clearDatabase(); });

  // --- publishing the question ---------------------------------------------

  it("lets the seller publish a choice row", async () => {
    await assertSucceeds(publish(SELLER, "act_choice", row()));
  });

  it("lets every signed-in buyer read it", async () => {
    // The question is configuration every buyer must read, like every other
    // published row.
    await seed(env, (db) => db.ref(`seller_ui_actions/${SELLER}/act_choice`).set(row()));
    await assertSucceeds(asUser(env, ALICE).ref(`seller_ui_actions/${SELLER}`).once("value"));
  });

  it("does not let a buyer publish a choice row", async () => {
    await assertFails(publish(ALICE, "act_choice", row()));
  });

  // --- the kind belongs to its slot ----------------------------------------

  it("accepts CHOICE in the profile choice slot too", async () => {
    await assertSucceeds(publish(SELLER, "act_choice", row({
      location: "TOPBAR_PROFILE_CHOICE",
      selectionMode: "SINGLE"
    })));
  });

  it("refuses CHOICE in a button slot", async () => {
    // A picker has an overlay to open, not a URL; the slot decides the kind and an
    // impossible pairing must not reach a buyer's renderer.
    await assertFails(publish(SELLER, "act_choice", row({ location: "TOPBAR_HOME" })));
  });

  it("refuses CHOICE in the message box slot", async () => {
    await assertFails(publish(SELLER, "act_choice", row({ location: "TOPBAR_HOME_INPUT" })));
  });

  it("refuses a QUICK_MESSAGE in a choice slot", async () => {
    await assertFails(publish(SELLER, "act_q", row({
      id: "act_q",
      kind: "QUICK_MESSAGE",
      options: null,
      selectionMode: null
    })));
  });

  // --- the question is a sentence ------------------------------------------

  it("lets a choice question carry a colon", async () => {
    // Same exemption ANNOUNCE and QUICK_MESSAGE have, for the same reason: a CHOICE
    // value has no path to the platform's URI handler, so the no-scheme check that
    // guards OPEN_URL would only mangle a German sentence.
    await assertSucceeds(publish(SELLER, "act_choice", row({
      value: "Frage: was sollen wir mitbringen?"
    })));
  });

  // --- the options ----------------------------------------------------------

  it("refuses a row whose options are not strings", async () => {
    // The indices-vs-labels decision, enforced: a number here is an index into a
    // list that moves.
    await assertFails(publish(SELLER, "act_choice", row({ options: [1, 2, 3] })));
  });

  it("refuses an empty option", async () => {
    await assertFails(publish(SELLER, "act_choice", row({ options: ["Eier", ""] })));
  });

  it("refuses an option longer than 48 characters", async () => {
    // The cap exists so a full multi-select always fits the 500-character text cap
    // the inbox enforces, rather than failing at the buyer's send.
    await assertFails(publish(SELLER, "act_choice", row({ options: ["x".repeat(49)] })));
  });

  it("refuses options on a kind that has none", async () => {
    await assertFails(publish(SELLER, "act_a", {
      id: "act_a",
      kind: "ANNOUNCE",
      label: "Heute",
      value: "Heute ab 9 Uhr",
      location: "TOPBAR_HOME_TITLE",
      options: ["Eier"]
    }));
  });

  it("refuses an unknown selection mode", async () => {
    await assertFails(publish(SELLER, "act_choice", row({ selectionMode: "RANKED" })));
  });

  // --- the icon -------------------------------------------------------------

  it("lets the seller pick an icon from the set the app ships", async () => {
    await assertSucceeds(publish(SELLER, "act_choice", row({ icon: "CART" })));
  });

  it("refuses an icon the app does not ship", async () => {
    // The closed list is the whole protection: a seller who could store a free
    // string could aim the top bar at an asset the app never vetted.
    await assertFails(publish(SELLER, "act_choice", row({ icon: "SKULL" })));
  });

  it("refuses an icon stored as a number", async () => {
    // Names, not positions -- a number is an index into a list somebody can
    // renumber, and every stored row would silently change meaning.
    await assertFails(publish(SELLER, "act_choice", row({ icon: 3 })));
  });

  it("refuses an icon on a kind that is not drawn as one", async () => {
    await assertFails(publish(SELLER, "act_q", {
      id: "act_q",
      kind: "QUICK_MESSAGE",
      label: "Was fehlt",
      value: "Was fehlt euch?",
      location: "TOPBAR_HOME_INPUT",
      icon: "CART"
    }));
  });

  it("accepts a picker with no icon at all", async () => {
    // Absent means the question mark. The field is optional by design.
    await assertSucceeds(publish(SELLER, "act_choice", row({ icon: null })));
  });

  // --- answering it ---------------------------------------------------------

  it("lets a buyer send a choice answer", async () => {
    await assertSucceeds(send(ALICE, "msg_choice", answer()));
  });

  it("refuses an answer whose selections are indices", async () => {
    await assertFails(send(ALICE, "msg_choice", answer({ selections: [1, 2] })));
  });

  it("refuses an empty selection label", async () => {
    await assertFails(send(ALICE, "msg_choice", answer({ selections: ["Eier", ""] })));
  });

  it("refuses selections on a quick message", async () => {
    await assertFails(send(ALICE, "msg_choice", answer({
      requestKind: "QUICK_MESSAGE",
      selections: ["Eier"],
      options: null
    })));
  });

  it("still refuses an unknown request kind", async () => {
    await assertFails(send(ALICE, "msg_choice", answer({ requestKind: "RATING" })));
  });

  it("does not let a buyer answer as someone else", async () => {
    await assertFails(send(ALICE, "msg_choice", answer({ buyerId: SELLER })));
  });

  it("does not let a buyer rewrite a sent answer", async () => {
    // Add-only, like every other message: the seller may already have ordered the
    // Dinkelmehl.
    await seed(env, (db) => db.ref(`seller_inbox/${SELLER}/msg_choice`).set(answer()));
    await assertFails(send(ALICE, "msg_choice", answer({ selections: ["Bergkäse"] })));
  });

  it("does not let a buyer read the answers back", async () => {
    await seed(env, (db) => db.ref(`seller_inbox/${SELLER}/msg_choice`).set(answer()));
    await assertFails(asUser(env, ALICE).ref(`seller_inbox/${SELLER}/msg_choice`).once("value"));
  });

  it("lets the seller read the answers", async () => {
    await seed(env, (db) => db.ref(`seller_inbox/${SELLER}/msg_choice`).set(answer()));
    await assertSucceeds(asUser(env, SELLER).ref(`seller_inbox/${SELLER}`).once("value"));
  });
});
