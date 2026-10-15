# Seller Contact: Channels, Market Scope and Visibility

**Status:** Design only — nothing below is implemented
**Scope (proposed):** `shared/src/commonMain` (model + repository + About screen),
`buyMain` (top-bar menu, profile card), `sellMain` (contact editor),
`firebase/database.rules.json`

How the buyer app learns how to reach the seller, why that is currently four hardcoded
copies of two strings, and who should see the shortcut.

---

## Where it stands today

The seller's phone number and email address are **string resources**, baked into the APK:

```xml
<!-- values/strings.xml:605, and again in values-en/strings.xml:551 -->
<string name="about_phone">030 - 20239477</string>
<string name="about_email">bodenschaetze@cutthecrap.link</string>
```

They are read in four places:

| Where | What it does |
|---|---|
| `AppScaffold.kt:512-513` | the top-bar contact menu (Home and Profile), `tel:` / `mailto:` |
| `AboutScreenModern.kt:256,287` | the About screen's contact block — display text |
| `AboutScreenModern.kt:267,299` | the same block's click handlers |
| `CustomerProfileScreenModern.kt:1330-1331` | the profile contact card |

Two consequences, both already real:

**A phone number is sitting in a localization file.** It is duplicated across `values/`
and `values-en/`, neither of which is a translation of the other in any meaningful sense,
and changing it means editing two locale files and shipping a release.

**The About screen used to display one number and dial another.** The display read
`about_phone` — `030 - 20239477`, the correct number — while the click handler was a
literal that had drifted to `tel:03020249477`: `2024` against `2023` in the seventh
digit. Tapping the number on the About screen called the wrong line. The email below it
was duplicated the same way and happened to still agree.

Fixed ahead of this design by dialling what is displayed:

```kotlin
val phoneNumber = stringResource(Res.string.about_phone).filter { it.isDigit() || it == '+' }
…
onClick = { uriHandler.openUri("tel:$phoneNumber") }
```

That removes the drift but not its cause — the number is still a string resource in two
locale files, which is what the rest of this document addresses. Four copies of one fact
is why they diverged in the first place.

The buy app has **no read path for the seller profile at all**. `ProfileRepository.
getSellerProfile` exists but is never called from `buyMain`, and would fail if it were:
it reads the whole node (`sellersRef.child(targetSellerId).valueEvents`), and the rule is

```json
"seller_profile": { "$sellerId": {
    ".read": "auth != null && auth.uid === $sellerId",
    "$field": { ".read": "auth != null && $field !== 'knownClientIds' && …" } } }
```

A buyer passes the `$field` rule and fails the parent, so individual fields are readable
but the profile is not. (Separately, the `sellerId.isEmpty()` branch does
`sellersRef.valueEvents.first()` — a read of the `seller_profile` root, which has no
`.read` at all and is denied for everyone, the seller included.)

---

## The rule

> **The channel decides the scheme. A contact without a market belongs to every market.
> The shortcut is earned; the directory is not.**

---

## 1. Channels, not fields

The seller wants to add and remove options, so the model is a list, not a fixed pair:

```kotlin
enum class ContactChannel { PHONE, EMAIL, WHATSAPP, SIGNAL, WEB, INSTAGRAM }

data class SellerContact(
    val id: String = "",
    val channel: ContactChannel = ContactChannel.PHONE,
    val value: String = "",          // number, address or handle — never a full URI
    val label: String = "",          // optional override: "Bestellhotline"
    val marketId: String? = null,    // null = every market
    val position: Int = 0,
)
```

### The database never supplies a URI scheme

`value` holds a bare number, address or handle. The **channel** maps to the scheme and
the icon, in the app:

| Channel | Scheme | Icon |
|---|---|---|
| `PHONE` | `tel:` | `Icons.Default.Phone` |
| `EMAIL` | `mailto:` | `Icons.Default.Email` |
| `WHATSAPP` | `https://wa.me/` | — |
| `SIGNAL` | `https://signal.me/#p/` | — |
| `WEB` | `https://` | — |
| `INSTAGRAM` | `https://instagram.com/` | — |

If the seller could store a raw URI, write access to this node would become the ability
to make the buy app hand an arbitrary scheme to `uriHandler.openUri`. Only the seller can
write there, so the severity is low — but the cost of getting it right now is one enum,
and the cost of changing it after sellers have saved free-form data is a migration.

An **unknown channel value is skipped, not rendered and not crashed on** — the same
degradation as `storedOrderOrigin`, so a record written by a newer app version simply
does not appear in an older one.

Normalization belongs in the seller app at save time (strip spaces and dashes from a
phone number once), not in the buy app at render time. The current
`.filter { it.isDigit() || it == '+' }` at the call site is a symptom of storing a
display string where a dialable one was needed.

---

## 2. Market scope

A contact carries an optional `marketId`. `null` means every market — **the same
convention as `MarketClosure.marketId`** in `doc/market-pricing-and-access-design.md`, so
there is one rule to learn rather than two.

This is deliberately *not* the shape rejected for pricing. Per-market prices on an article
are N copies of one fact that drift apart. A market-scoped contact is one record with an
optional scope: the business email stays a single `null` record, and a stall mobile is one
additional record. Nothing is duplicated for the common case.

### What "the current market" means outside the basket

The top-bar menu appears on Home and Profile, neither of which has a selected pickup slot.
The contextual market resolves in this order, and the answer may be none:

1. the slot selected in the basket, if there is one
2. `BuyerProfile.defaultMarket`
3. the buyer's only eligible market, if they have exactly one
4. otherwise: none

The menu then shows every `marketId == null` contact, plus the contextual market's own
contacts if step 1–3 produced one. With no contextual market the buyer sees the
seller-wide contacts, which is the correct answer rather than a fallback.

### Shortcut and directory

| | Shows | For whom |
|---|---|---|
| **Top-bar menu** | seller-wide + contextual market | approved buyers only (§3) |
| **About screen** | everything, grouped by market | everyone |

The top bar is a shortcut to the one or two ways you would actually reach this seller
today. About is the directory and stays complete.

---

## 3. Visibility: the shortcut is earned

A one-tap call button handed to every anonymous installer is unvetted inbound noise for a
small business. The top-bar menu should appear only for approved buyers.

### Gate on approval, not on `isDemoMode`

`BuyAppState.kt:98` reads:

```kotlin
val isDemoMode: Boolean get() = isAccessStatusLoaded && accessStatus != AccessStatus.APPROVED
```

This is **`false` while the status is still loading**. Gating the menu on `!isDemoMode`
would show it at launch and then remove it a moment later, which is the worst of both.
The gate must be positive:

```kotlin
/** The contact shortcut is shown only once approval is known and granted. */
val canContactSeller: Boolean
    get() = isAccessStatusLoaded && accessStatus == AccessStatus.APPROVED
```

Absent until earned, never the reverse.

### This is friction, not protection

`seller_contact` has to be readable by any signed-in user, because the About screen serves
demo users too. The data is therefore in the database for anyone who can sign in. Hiding
the menu reduces casual inbound contact; it is not an access control and must not later be
mistaken for one.

### The one cohort with a real question

The most legitimate reason an unapproved buyer contacts a seller is *"how do I become a
customer?"* — the conversion path. Hiding the shortcut from exactly those people, while
leaving the answer two taps away, is the wrong trade on its own.

About is reached from a single link, on the Profile screen
(`CustomerProfileScreenModern.kt:1282`). From Profile that is fine — the menu being
removed and the About link sit on the same screen. From Home it is two taps.

So: put a contact link in `AccessStatusCard` (`CustomerProfileScreenModern.kt:498`), which
already renders for `NONE` and `PENDING` buyers and already explains their state. That
costs nothing, puts contact where the question actually arises, and removes the only real
objection to hiding the menu.

Convenient side effect: a demo buyer has no approved market eligibility, so a *contextual
market* is undefined for precisely the cohort the menu is hidden from. The two rules do
not interact awkwardly.

---

## 4. Storage

`seller_contact/{sellerId}/{contactId}` — a separate node, not a field on the profile.

```jsonc
"seller_contact": {
  "$sellerId": {
    ".read": "auth != null",
    ".write": "auth != null && $sellerId === auth.uid",
    "$contactId": {
      ".validate": "newData.hasChildren(['channel', 'value'])",
      "channel":  { ".validate": "newData.isString()" },
      "value":    { ".validate": "newData.isString() && newData.val().length <= 200" },
      "label":    { ".validate": "newData.isString() && newData.val().length <= 60" },
      "marketId": { ".validate": "newData.isString()" },
      "position": { ".validate": "newData.isNumber()" },
      "$other":   { ".validate": false }
    }
  }
}
```

Same read/write shape as `articles`, which is the existing precedent for "public to signed-in
users, written by the owner".

### Why not reuse `seller_profile`

`SellerProfile` already has `telephoneNumber` and `urls: List<String>`, and relaxing the
parent `.read` would be the shortest path. Rejected:

- It would publish everything else in the node — `lat`, `lng`, the seller's private street
  address — and make **every field added to the profile in future public by default**. The
  three legacy maps are already explicitly excluded there, which is evidence the node was
  never designed to be buyer-readable. This is the accident class catalogued in
  `doc/buyer-profile-data-safety.md`.
- The shape is wrong anyway: a contact list with add, remove and reorder is not a set of
  fixed profile fields.

`SellerProfile.telephoneNumber` stays as the seller's own record of their number. If the
seller app wants to offer it as a starting value when adding the first `PHONE` contact,
that is a convenience at save time, not a live dependency.

### Fallback

The existing `about_phone` / `about_email` strings stay as the seeded default: shown when
the node has not loaded, is empty, or the device is offline. Deleting them outright would
make a cold start on a bad connection show no contact at all.

**Hide the top-bar icon entirely when the resolved list is empty.** An icon that opens an
empty dropdown is worse than no icon.

---

## Suggested order of work

1. ~~**Fix the dialled-number bug.**~~ Done: `AboutScreenModern` now dials and mails what
   it displays instead of two literals.
2. **Model, node, rules, repository, DI.** Including the degradation path for unknown
   channels.
3. **Buy app read + the four call sites.** All four move together or the app shows
   different numbers on different screens.
4. **Seller editor** in `SellerProfileScreen` — add, remove, reorder. The market dialog at
   `SellerProfileScreen.kt:828` is the pattern to copy. The largest single chunk.
5. **Visibility rule** — `canContactSeller`, plus the contact link in `AccessStatusCard`.
6. **Market scoping.** Last: it depends on `Market` being a real entity
   (`doc/market-pricing-and-access-design.md` §1) and on the contextual-market resolution,
   neither of which exists yet. Until then every contact is `marketId = null` and the
   feature is complete without it.

Steps 1–5 are independent of the markets work. Step 6 is the only part that waits.

---

## Still open

### 1. Which channels ship in v1?

`PHONE` and `EMAIL` are the current behaviour. `WHATSAPP` is plausible for a market
trader; `SIGNAL`, `WEB` and `INSTAGRAM` are speculation. Every channel is an icon, a
label, two translations and a normalization rule. Shipping two and adding on demand is
probably right, but the enum should be defined with the full set from the start so stored
values never need rewriting.

### 2. Does the About screen group by market before markets exist?

Until `Market` is an entity, every contact is `marketId = null` and the grouping is a
single ungrouped list. Worth building the About screen ungrouped and adding grouping with
step 6, rather than writing grouping code against data that cannot yet vary.

### 3. Is `position` worth it?

Explicit ordering means the seller editor needs drag-to-reorder, which is the fiddliest
part of step 4. Ordering by channel with a fixed precedence (phone, then email, then the
rest) would drop the field and the interaction. Only worth it if a seller would really
want email above phone.

### 4. Should an approved buyer see other markets' contacts in the menu?

As specified, no — the menu shows seller-wide plus the contextual market, and About has
the rest. A buyer who collects at two markets and wants the other stall's number has to
open About. Probably correct, but it is an assumption, not a finding.
