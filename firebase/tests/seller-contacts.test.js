import { before, after, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { createTestEnv, asUser, seed, SELLER, ALICE } from "./helpers.js";

/**
 * A buyer must be able to read the seller's contact channels, and nothing else
 * from the seller profile that is private.
 *
 * The buy app shows a contact shortcut to approved buyers, which reads
 * `seller_profile/{sellerId}/contacts`. The profile node as a whole is readable
 * only by the seller — it carries their own address and coordinates — so the
 * shortcut depends entirely on the per-field read rule holding. Nothing covered
 * it until now, which made "approved buyers see no contact options" impossible
 * to tell apart from a rules problem.
 *
 * See doc/guides/seller-contact-design.md.
 */
describe("seller profile contacts", () => {
  let env;

  const contacts = [
    { channel: "PHONE", value: "+4933612345", label: "Hof" },
    { channel: "EMAIL", value: "hof@example.com", label: "" }
  ];

  before(async () => { env = await createTestEnv(); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => {
    await env.clearDatabase();
    await seed(env, async (db) => {
      await db.ref(`seller_profile/${SELLER}`).set({
        sellerId: SELLER,
        displayName: "Eric",
        street: "Privatweg",
        houseNumber: "1",
        lat: "52.3",
        lng: "14.0",
        contacts
      });
    });
  });

  it("lets a signed-in buyer read the contacts field", async () => {
    const db = asUser(env, ALICE);
    await assertSucceeds(db.ref(`seller_profile/${SELLER}/contacts`).get());
  });

  it("lets a buyer read the markets field the same way", async () => {
    // getSellerContacts falls back to the profile's legacy markets list.
    const db = asUser(env, ALICE);
    await assertSucceeds(db.ref(`seller_profile/${SELLER}/markets`).get());
  });

  it("refuses a buyer the whole profile node", async () => {
    // Which is why the app reads child by child rather than the node.
    const db = asUser(env, ALICE);
    await assertFails(db.ref(`seller_profile/${SELLER}`).get());
  });

  it("still refuses the buyer-list fields", async () => {
    const db = asUser(env, ALICE);
    await assertFails(db.ref(`seller_profile/${SELLER}/knownClientIds`).get());
    await assertFails(db.ref(`seller_profile/${SELLER}/blockedClientIds`).get());
    await assertFails(db.ref(`seller_profile/${SELLER}/approvedBuyerIds`).get());
  });

  it("refuses an unauthenticated reader", async () => {
    const db = env.unauthenticatedContext().database();
    await assertFails(db.ref(`seller_profile/${SELLER}/contacts`).get());
  });

  it("refuses a buyer writing the seller's contacts", async () => {
    const db = asUser(env, ALICE);
    await assertFails(db.ref(`seller_profile/${SELLER}/contacts`).set(contacts));
  });

  it("lets the seller write their own contacts", async () => {
    const db = asUser(env, SELLER);
    await assertSucceeds(db.ref(`seller_profile/${SELLER}/contacts`).set(contacts));
  });
});
