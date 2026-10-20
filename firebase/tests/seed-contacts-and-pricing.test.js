import { before, after, beforeEach, describe, it } from "node:test";
import { strict as assert } from "node:assert";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE, MALLORY } from "./helpers.js";
import {
  buildPlan, FUERSTENWALDE, HANSAVIERTEL, ONKEL_TOMS, MARKETS_ROOT, PROFILE_ROOT
} from "../scripts/seed-plan.mjs";

/**
 * The payload `seed-contacts-and-pricing.mjs` writes has to be a payload the rules
 * accept.
 *
 * It is applied over the firebase CLI, whose admin credentials bypass the rules
 * entirely. So a record the rules would reject is written anyway and nothing fails —
 * until the seller next edits that market in the app, which is refused, leaving them
 * unable to touch their own market again. The seed script validates its own output
 * against a transcription of the rules, but a transcription can drift; this runs the
 * real ruleset against the real payload, which is why it imports the plan from the
 * script's own module rather than restating it.
 *
 * The census fixture is the shape dev had before the seed: no contacts anywhere, no
 * pricing, markets in both the node and the profile's legacy list, and one profile
 * whose telephoneNumber is a number rather than a string.
 *
 * See doc/guides/market-pricing-and-access-design.md and seller-contact-design.md.
 */
describe("seed-contacts-and-pricing payload", () => {
  let env;

  /** The three live markets, as the census found them. */
  const LIVE_MARKETS = {
    [FUERSTENWALDE]: { name: "Fürstenwalde", dayOfWeek: "Samstag", dayIndex: 5 },
    [HANSAVIERTEL]: { name: "Ökomarkt im Hansaviertel", dayOfWeek: "Freitag", dayIndex: 4 },
    [ONKEL_TOMS]: { name: "Wochenmarkt Onkel Toms Hütte", dayOfWeek: "Donnerstag", dayIndex: 3 }
  };

  const marketNode = (sellerId) => Object.fromEntries(
    Object.entries(LIVE_MARKETS).map(([id, fields]) => [id, { id, active: true, ...fields }])
  );

  /** The legacy list, in an order that is not the node's: dev's indices differ per seller. */
  const legacyList = [
    { id: HANSAVIERTEL, ...LIVE_MARKETS[HANSAVIERTEL] },
    { id: ONKEL_TOMS, ...LIVE_MARKETS[ONKEL_TOMS] },
    { id: FUERSTENWALDE, ...LIVE_MARKETS[FUERSTENWALDE] }
  ];

  const census = (telephoneNumber) => ({
    profiles: {
      [SELLER]: {
        displayName: "BodenSchätze", telephoneNumber, markets: legacyList
      }
    },
    markets: { [SELLER]: marketNode(SELLER) },
    // A CATEGORY scope that matches no article is a violation, so the fixture has to
    // carry the categories the conditions name.
    articles: {
      [SELLER]: {
        a1: { category: "Obst", price: 3.04 },
        a2: { category: "Kartoffeln", price: 2.9 },
        a3: { category: "Sonstiges", price: 2.59 }
      }
    }
  });

  const plan = () => buildPlan(census("030-20249477"));

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, (db) => db.ref(`${MARKETS_ROOT}/${SELLER}`).set(marketNode(SELLER)));
  });

  it("plans something, and plans nothing that breaks the rules", () => {
    const { violations, fields } = plan();
    assert.deepEqual(violations, []);
    assert.ok(fields > 0, "a plan over an unseeded census should write something");
  });

  it("the seller may write the markets patch", async () => {
    const { marketPatches } = plan();
    const db = asUser(env, SELLER);
    await assertSucceeds(db.ref(`${MARKETS_ROOT}/${SELLER}`).update(marketPatches.get(SELLER)));
  });

  it("the seller may write the profile patch", async () => {
    const { profilePatches } = plan();
    const db = asUser(env, SELLER);
    await assertSucceeds(db.ref(`${PROFILE_ROOT}/${SELLER}`).update(profilePatches.get(SELLER)));
  });

  it("the seller can still save a seeded market from the app afterwards", async () => {
    const { marketPatches } = plan();
    await seed(env, (db) => db.ref(`${MARKETS_ROOT}/${SELLER}`).update(marketPatches.get(SELLER)));

    // Every field marketToMap writes, which is what the app sends when the seller
    // saves. If the seed left a record the rules reject, this is where the seller
    // finds out they are locked out of their own market.
    const db = asUser(env, SELLER);
    await assertSucceeds(db.ref(`${MARKETS_ROOT}/${SELLER}/${HANSAVIERTEL}`).set({
      id: HANSAVIERTEL, name: "Ökomarkt im Hansaviertel", street: "Altonaer Str.",
      houseNumber: "15", city: "Berlin", zipCode: "10557", dayOfWeek: "Freitag",
      begin: "08:00", end: "13:00", dayIndex: 4, active: true, pricingRevision: 2,
      priceConditions: marketPatches.get(SELLER)[`${HANSAVIERTEL}/priceConditions`],
      contacts: marketPatches.get(SELLER)[`${HANSAVIERTEL}/contacts`]
    }));
  });

  it("a buyer may read the seeded contacts, but still not the profile node", async () => {
    const { profilePatches } = plan();
    await seed(env, (db) => db.ref(`${PROFILE_ROOT}/${SELLER}`).update(profilePatches.get(SELLER)));
    const buyer = asUser(env, ALICE);
    // getSellerContacts reads the child, never the node: the node carries the
    // seller's own address. The shortcut needs both halves of that to hold.
    await assertSucceeds(buyer.ref(`${PROFILE_ROOT}/${SELLER}/contacts`).get());
    await assertFails(buyer.ref(`${PROFILE_ROOT}/${SELLER}`).get());
  });

  it("a buyer may read the seeded market pricing", async () => {
    const { marketPatches } = plan();
    await seed(env, (db) => db.ref(`${MARKETS_ROOT}/${SELLER}`).update(marketPatches.get(SELLER)));
    await assertSucceeds(asUser(env, ALICE).ref(`${MARKETS_ROOT}/${SELLER}`).get());
  });

  it("another seller may not write these markets", async () => {
    const { marketPatches } = plan();
    await assertFails(asUser(env, MALLORY).ref(`${MARKETS_ROOT}/${SELLER}`).update(marketPatches.get(SELLER)));
  });

  it("mirrors each market's contacts into the legacy list by id, not position", () => {
    const patch = plan().profilePatches.get(SELLER);
    // toContactDirectory reads the profile's legacy list, so a market's contacts have
    // to land there too — at the index that market actually occupies. The fixture's
    // list is deliberately in a different order from the node.
    assert.ok(patch[`markets/${legacyList.findIndex((m) => m.id === FUERSTENWALDE)}/contacts`]);
    assert.ok(patch[`markets/${legacyList.findIndex((m) => m.id === HANSAVIERTEL)}/contacts`]);
    assert.equal(patch[`markets/1/contacts`], undefined, "Onkel Toms Hütte has no contacts to mirror");
  });

  it("leaves one market unpriced, so the no-conditions path stays covered", () => {
    const patch = plan().marketPatches.get(SELLER);
    assert.equal(patch[`${ONKEL_TOMS}/priceConditions`], undefined);
    assert.equal(patch[`${ONKEL_TOMS}/pricingRevision`], undefined);
  });

  it("repairs a numeric telephoneNumber, and derives the phone contact from it", () => {
    const { profilePatches, repaired } = buildPlan(census(17312345678));
    const patch = profilePatches.get(SELLER);
    // Every reader does `value["telephoneNumber"] as? String ?: ""`, so a number
    // reads back empty: the field is unreadable until it is a string.
    assert.equal(patch.telephoneNumber, "17312345678");
    assert.equal(repaired.length, 1);
    const phone = patch.contacts.find((c) => c.channel === "PHONE");
    assert.equal(phone.value, "17312345678", "the derived contact must not go missing for that seller");
  });

  it("leaves a numeric telephoneNumber alone under --no-repair", () => {
    const { profilePatches, repaired, skipped } = buildPlan(census(17312345678), { repair: false });
    assert.equal(profilePatches.get(SELLER).telephoneNumber, undefined);
    assert.deepEqual(repaired, []);
    assert.ok(skipped.some((s) => s.includes("telephoneNumber")));
  });

  it("writes nothing a second time", () => {
    const { marketPatches, profilePatches } = plan();
    // Add-only: a field already present was written by the app and is newer than
    // anything the script would put there.
    const seeded = census("030-20249477");
    for (const [key, value] of Object.entries(marketPatches.get(SELLER))) {
      const [marketId, field] = key.split("/");
      seeded.markets[SELLER][marketId][field] = value;
    }
    for (const [key, value] of Object.entries(profilePatches.get(SELLER))) {
      if (key.startsWith("markets/")) {
        const [, index, field] = key.split("/");
        seeded.profiles[SELLER].markets[Number(index)][field] = value;
      } else {
        seeded.profiles[SELLER][key] = value;
      }
    }
    const again = buildPlan(seeded);
    assert.equal(again.fields, 0, "a second run over its own output must plan no writes");
    assert.deepEqual(again.violations, []);
  });

  it("refuses a CATEGORY scope that matches no article", () => {
    const empty = census("030-20249477");
    // A scope naming a category nothing carries applies to nothing, and reads as a
    // broken pricing engine rather than as missing data.
    empty.articles[SELLER] = { a1: { category: "Sonstiges", price: 2.59 } };
    const { violations } = buildPlan(empty);
    assert.ok(violations.some((v) => v.includes("Obst")));
    assert.ok(violations.some((v) => v.includes("Kartoffeln")));
  });
});
