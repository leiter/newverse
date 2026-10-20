/**
 * What `seed-contacts-and-pricing.mjs` would write, as a pure function of a census.
 *
 * Separate from the script so the rules tests can assert against the payload the
 * script actually produces rather than a copy of it that can drift away from it.
 * Nothing here reads a database or a file: hand it the three nodes and it returns
 * the patches plus everything the script reports.
 */

export const PROFILE_ROOT = "seller_profile";
export const MARKETS_ROOT = "markets";

/** Markets named by id, because the legacy list's indices differ per seller. */
export const HANSAVIERTEL = "bb8590a8-6fb5-4a4e-a140-b493bb4df9ac";
export const ONKEL_TOMS = "dcdcedd4-81d5-4b6b-8566-c0426098fa69";
export const FUERSTENWALDE = "731ea8c0-612a-42d5-a389-479f1d811c28";

/** Contacts for the business. `tel` is the profile's own telephoneNumber. */
export const profileContacts = (tel) => [
  ...(tel && tel.trim() !== "" ? [{ channel: "PHONE", value: tel.trim(), label: "" }] : []),
  { channel: "EMAIL", value: "bestellung@bodenschaetze.example", label: "Bestellungen" },
  { channel: "WEB", value: "bodenschaetze.example", label: "" }
];

/**
 * Contacts per market.
 *
 * Fürstenwalde gets a stall mobile with a label, which is the case the label field
 * exists for. Hansaviertel repeats the office number the profile already carries, so
 * `resolveFor` has something to collapse: it dedupes on channel and normalised value,
 * and a seller who lists the same number twice should see one row, not two.
 */
export const marketContacts = (tel) => ({
  [FUERSTENWALDE]: [
    { channel: "WHATSAPP", value: "+4915112345678", label: "Standhandy Fürstenwalde" }
  ],
  ...(tel && tel.trim() !== ""
    ? { [HANSAVIERTEL]: [{ channel: "PHONE", value: tel.trim(), label: "" }] }
    : {})
});

/** Conditions per market. Onkel Toms Hütte is absent on purpose. */
export const PRICING = {
  [FUERSTENWALDE]: [
    { cause: "TRANSPORT", scopeType: "ALL", scopeValue: "", effect: "SURCHARGE", amount: 0.3,
      note: "Anfahrt Fürstenwalde" }
  ],
  [HANSAVIERTEL]: [
    { cause: "STALL_FEE", scopeType: "ALL", scopeValue: "", effect: "FACTOR", amount: 1.08,
      note: "Standgebühr Hansaviertel" },
    { cause: "STALL_FEE", scopeType: "CATEGORY", scopeValue: "Obst", effect: "FACTOR", amount: 1.05,
      note: "Obst: ermäßigte Standgebühr" },
    { cause: "TRANSPORT", scopeType: "CATEGORY", scopeValue: "Kartoffeln", effect: "SURCHARGE",
      amount: 0.2, note: "Kartoffeln: Transport" }
  ]
};

export const PRICING_REVISION = 1;

// --- rules, as firebase/database.rules.json states them ---------------------------

const CONTACT_FIELDS = ["channel", "value", "label"];
const CONDITION_FIELDS = ["cause", "scopeType", "scopeValue", "effect", "amount", "note"];
const EFFECTS = ["FACTOR", "SURCHARGE", "DISCOUNT"];
/** ContactChannel's own entries. The rules only require a string; the app drops the rest. */
const CHANNELS = ["PHONE", "EMAIL", "WHATSAPP", "SIGNAL", "WEB", "INSTAGRAM"];
/** PriceCause's entries, same reasoning. */
const CAUSES = ["STALL_FEE", "TRANSPORT", "HANDLING", "NEIGHBORHOOD_RATE", "VOLUME_COMMITMENT"];

/** RTDB returns a sparse list as an index-keyed map, so accept both shapes. */
const rows = (value) => {
  if (Array.isArray(value)) return value.filter((v) => v != null);
  if (value && typeof value === "object") return Object.values(value);
  return [];
};

/**
 * The patches to apply, and everything worth reporting about them.
 *
 * `census` is the three nodes as the database holds them. Returns the two patch maps
 * keyed by seller, plus the planned / skipped / missing / repaired lines and any rule
 * violation found. A violation is returned, not thrown: the script prints the whole
 * plan before refusing, because a payload that breaks one record should still show
 * what the other records would have done.
 */
export function buildPlan({ profiles = {}, markets = {}, articles = {} }, { repair = true } = {}) {
  const marketPatches = new Map();
  const profilePatches = new Map();
  const planned = [];
  const skipped = [];
  const missing = [];
  const repaired = [];
  const violations = [];

  const check = (ok, where, why) => { if (!ok) violations.push(`${where}: ${why}`); };

  const validateContact = (c, where) => {
    check(Object.keys(c).every((k) => CONTACT_FIELDS.includes(k)), where, "unknown field (rules reject $other)");
    check(typeof c.channel === "string" && c.channel !== "", where, "channel must be a non-empty string");
    check(CHANNELS.includes(c.channel), where, `channel '${c.channel}' is not a ContactChannel, the app would drop it`);
    check(typeof c.value === "string" && c.value.length <= 200, where, "value must be a string <= 200 chars");
    check(typeof c.value === "string" && c.value.trim() !== "", where, "value is blank, so isUsable is false and it never renders");
    check(typeof c.label === "string" && c.label.length <= 60, where, "label must be a string <= 60 chars");
  };

  const validateCondition = (c, where) => {
    check(Object.keys(c).every((k) => CONDITION_FIELDS.includes(k)), where, "unknown field (rules reject $other)");
    check(typeof c.cause === "string", where, "cause must be a string");
    check(CAUSES.includes(c.cause), where, `cause '${c.cause}' is not a PriceCause, the app would drop it`);
    check(EFFECTS.includes(c.effect), where, `effect must be one of ${EFFECTS.join("/")}`);
    check(typeof c.amount === "number" && c.amount >= 0 && c.amount <= 10000, where, "amount must be 0..10000");
    check(typeof c.scopeValue === "string" && c.scopeValue.length <= 200, where, "scopeValue must be a string <= 200 chars");
    check(typeof c.note === "string" && c.note.length <= 200, where, "note must be a string <= 200 chars");
    if (c.scopeType === "CATEGORY" || c.scopeType === "ARTICLE") {
      check(typeof c.scopeValue === "string" && c.scopeValue.trim() !== "", where,
        `${c.scopeType} scope with a blank scopeValue is dropped on read`);
    }
  };

  /** Categories the articles actually carry, so a CATEGORY scope can be checked. */
  const liveCategories = new Set();
  for (const bySeller of Object.values(articles)) {
    for (const article of Object.values(bySeller ?? {})) {
      const category = (article?.category ?? "").trim();
      if (category !== "") liveCategories.add(category.toLowerCase());
    }
  }

  for (const [sellerId, profile] of Object.entries(profiles)) {
    const rawTel = profile?.telephoneNumber;
    // A number here is a dev data bug, not a format: every reader does
    // `as? String ?: ""`, so it already reads back empty. Read it either way so the
    // derived contact does not silently go missing for that seller.
    const tel = typeof rawTel === "string" ? rawTel : typeof rawTel === "number" ? String(rawTel) : "";
    if (typeof rawTel === "number") {
      if (repair) {
        profilePatches.set(sellerId, { ...(profilePatches.get(sellerId) ?? {}), telephoneNumber: tel });
        repaired.push(`${sellerId} seller_profile/telephoneNumber  ${rawTel} (number) -> "${tel}" (string), ` +
          `unreadable by the app until this is a string`);
      } else {
        skipped.push(`${sellerId} seller_profile/telephoneNumber is a number and --no-repair was passed, ` +
          `so the app still reads it as empty`);
      }
    }

    // 1. the business's own contacts
    if (profile?.contacts !== undefined) {
      skipped.push(`${sellerId} seller_profile/contacts already present`);
    } else {
      const contacts = profileContacts(tel);
      contacts.forEach((c, i) => validateContact(c, `${sellerId} profile contact[${i}]`));
      profilePatches.set(sellerId, { ...(profilePatches.get(sellerId) ?? {}), contacts });
      planned.push(`${sellerId} seller_profile/contacts  ${contacts.length} contact(s)` +
        (tel === "" ? "  (no telephoneNumber to derive a phone from)" : `  phone from ${tel}`));
    }

    // 2. each market's contacts and pricing, in the node and the legacy list
    const perMarket = marketContacts(tel);
    const legacy = rows(profile?.markets);
    const legacyIndexOf = (marketId) => {
      const index = legacy.findIndex((m) => (m?.id ?? "").trim() === marketId);
      return index === -1 ? null : index;
    };

    for (const marketId of [FUERSTENWALDE, HANSAVIERTEL, ONKEL_TOMS]) {
      const live = markets[sellerId]?.[marketId];
      if (live === undefined) {
        missing.push(`${sellerId} markets/${marketId} does not exist, nothing to seed`);
        continue;
      }
      const name = live.name ?? "(unnamed)";
      const patch = marketPatches.get(sellerId) ?? {};

      const contacts = perMarket[marketId];
      if (contacts !== undefined) {
        if (live.contacts !== undefined) {
          skipped.push(`${sellerId} markets/${marketId} (${name}) contacts already present`);
        } else {
          contacts.forEach((c, i) => validateContact(c, `${sellerId} ${name} contact[${i}]`));
          patch[`${marketId}/contacts`] = contacts;
          planned.push(`${sellerId} markets/${marketId}  ${name}: ${contacts.length} contact(s)`);

          // Mirror into the legacy list, which is what toContactDirectory reads.
          const index = legacyIndexOf(marketId);
          if (index === null) {
            missing.push(`${sellerId} ${name} is not in the profile's legacy markets list, no mirror written`);
          } else if (legacy[index].contacts !== undefined) {
            skipped.push(`${sellerId} seller_profile/markets/${index} (${name}) contacts already present`);
          } else {
            profilePatches.set(sellerId, {
              ...(profilePatches.get(sellerId) ?? {}),
              [`markets/${index}/contacts`]: contacts
            });
            planned.push(`${sellerId} seller_profile/markets/${index}/contacts  ${name} (mirror)`);
          }
        }
      }

      const conditions = PRICING[marketId];
      if (conditions === undefined) {
        planned.push(`${sellerId} markets/${marketId}  ${name}: no conditions, left unpriced by design`);
      } else if (live.priceConditions !== undefined) {
        skipped.push(`${sellerId} markets/${marketId} (${name}) priceConditions already present`);
      } else {
        conditions.forEach((c, i) => {
          validateCondition(c, `${sellerId} ${name} condition[${i}]`);
          if (c.scopeType === "CATEGORY" && !liveCategories.has(c.scopeValue.trim().toLowerCase())) {
            violations.push(`${sellerId} ${name} condition[${i}]: no article has category ` +
              `'${c.scopeValue}', so this condition would apply to nothing`);
          }
        });
        patch[`${marketId}/priceConditions`] = conditions;
        if (live.pricingRevision === undefined) patch[`${marketId}/pricingRevision`] = PRICING_REVISION;
        planned.push(`${sellerId} markets/${marketId}  ${name}: ${conditions.length} condition(s), ` +
          `pricingRevision=${live.pricingRevision ?? PRICING_REVISION}`);
      }

      if (Object.keys(patch).length > 0) marketPatches.set(sellerId, patch);
    }
  }

  const fields = [...marketPatches.values(), ...profilePatches.values()]
    .reduce((n, patch) => n + Object.keys(patch).length, 0);

  return { marketPatches, profilePatches, planned, skipped, missing, repaired, violations, fields };
}
