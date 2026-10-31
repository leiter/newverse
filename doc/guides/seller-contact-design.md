# Seller Contact: Channels, Market Scope and Visibility

**Status:** Implemented 2026-10-19. The design below is what was built; the two
deviations found while building it are marked **BUILT DIFFERENTLY**.
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

> **The channel decides the scheme. A contact belongs to the thing it is edited on — a
> market, or the seller. The shortcut is earned; the directory is not.**

---

## 1. Channels, not fields

The seller wants to add and remove options, so the model is a list, not a fixed pair:

```kotlin
enum class ContactChannel { PHONE, EMAIL, WHATSAPP, SIGNAL, WEB, INSTAGRAM, LOCATION }

data class SellerContact(
    val channel: ContactChannel = ContactChannel.PHONE,
    val value: String = "",     // number, address or handle — never a full URI
    val label: String = "",     // optional override: "Bestellhotline"
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
| `LOCATION` | `https://www.google.com/maps/search/?api=1&query=` | `Icons.Default.LocationOn` |

### `LOCATION`

A pin — where the stall actually stands — entered as a latitude and a longitude and
stored as one `"lat,lng"` string in the same `value` field every other channel uses. So
it needs no schema change, no rules change (`value` is already any string up to 200
characters) and no second code path: it is validated, resolved, deduplicated, hidden when
empty and kept live by exactly the machinery the phone number goes through. Available on
the business *and* on each market, because both editors are one composable over one list.

A web map URL rather than the `geo:` URI the coordinates deserve. `geo:` is the standard
and Android honours it, but iOS registers no handler, so a seller's pin would silently do
nothing on half the installs. The https form opens the maps app on Android, Apple Maps or
Safari on iOS, and a browser anywhere else.

Two rules the pair does not share with the other channels:

* **The editor shows two fields, not one.** A single box holding `52.5200,13.4050` is a
  format to be taught; two labelled boxes are two numbers to be copied, which is what the
  seller is actually doing — reading them off a map on another screen. They are text
  fields, not numeric ones, because a half-typed `52.` or a lone `-` has to survive long
  enough to be finished.
* **An out-of-range figure is withheld, never clamped.** A latitude of 95 is a typo;
  clamping it to 90 would send the buyer somewhere confidently wrong, so `isUsable` is
  false and the row simply never reaches them. `normalize` keeps the seller's own digits
  rather than reprinting a parsed `Double`, which can yield `1.0E-5` — a string no map
  reads back.

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

## 2. Two levels, each edited where it belongs

A contact is **not** a record in a flat list tagged with a market id. It lives on the
thing it describes, and is edited there:

| Level | Held on | Edited in | Means |
|---|---|---|---|
| Seller | `SellerProfile` | the profile form that already exists | how to reach the business |
| Market | `Market` | the market dialog, `SellerProfileScreen.kt:828` | how to reach *this stall* |

The seller sets a stall's number while altering that stall, next to its address and
opening hours, because that is where they are already thinking about it. No separate
contact screen and no market dropdown.

### Why not one flat list with a `marketId`

It was the first shape proposed here and it is worse for this purpose. A flat
`seller_contact/{sellerId}/{contactId}` list with an optional `marketId` needs its own
node, its own rules, its own editor screen, and a market picker inside that editor — all
to express a relationship the market dialog expresses by simply being open. It also
leaves contacts behind when a market is deleted, which is extra cleanup. Nesting makes
the market the owner, so the contacts go with it.

### The duplication trap, and the way out

If *every* contact were per-market, a seller with three stalls would enter their business
email three times and change it in three places. That is precisely the drift that put a
wrong phone number in the About screen (§Where it stands today).

So the seller level is the default and the market level is additive. A seller who never
opens a market dialog still has working contacts; a market only carries what is genuinely
different about it.

### Union, not override

The buyer sees the seller's contacts **and** the contextual market's, deduplicated by
`(channel, value)`:

- Seller has one number, market adds nothing → one row. The common case, no noise.
- Market repeats the seller's number → still one row, because the pair matches.
- Market has its own mobile → two rows, distinguished by their labels
  (`Büro` / `Stand Kreuzberg`).

Override would have been the other option, and it is wrong here: a stall mobile that is
only answered on market day does not replace the office line, it adds to it. Dedup by
value means a seller who copies the same number into a market is not punished for it.

### What "the current market" means outside the basket

The top-bar menu appears on Home and Profile, neither of which has a selected pickup slot.
The contextual market resolves in this order, and the answer may be none:

1. the slot selected in the basket, if there is one
2. `BuyerProfile.defaultMarket`
3. the buyer's only eligible market, if they have exactly one
4. otherwise: none

The menu then shows the seller-level contacts, plus the contextual market's own if
step 1–3 produced one, deduplicated as above. With no contextual market the buyer sees
the seller-level contacts, which is the correct answer rather than a fallback.

### Shortcut and directory

| | Shows | For whom |
|---|---|---|
| **Top-bar menu** | seller level + contextual market, deduplicated | approved buyers only (§3) |
| **About screen** | seller level, then every market as its own group | everyone |

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

`seller_profile/{sellerId}/contacts` and `…/markets` have to be readable by any signed-in
user, because the About screen serves demo users too — and under the existing `$field`
rule they already are. The data is therefore in the database for anyone who can sign in.
Hiding the menu reduces casual inbound contact; it is not an access control and must not
later be mistaken for one.

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

## 4. Storage: no new node

Contacts ride on the structures that already exist. Nothing new is added to
`database.rules.json`.

```kotlin
data class SellerProfile(
    …
    val contacts: List<SellerContact> = emptyList(),   // new
)

data class Market(
    …
    val contacts: List<SellerContact> = emptyList(),   // new
)
```

Both serialize into `seller_profile/{sellerId}` — the profile's own list at
`…/contacts`, and each market's inside the existing `…/markets` list, which already
stores markets as a list of maps.

### Buyers can already read this

The rule on the node is:

```json
"seller_profile": { "$sellerId": {
    ".read": "auth != null && auth.uid === $sellerId",
    "$field": { ".read": "auth != null && $field !== 'knownClientIds' && …" } } }
```

A `$field` read grants the whole subtree beneath that field, so a buyer may read
`seller_profile/{sellerId}/markets` and `seller_profile/{sellerId}/contacts` today,
without a rules change. What a buyer may *not* do is read the profile node itself — which
is why `getSellerProfile` cannot be reused as-is (see §Where it stands today).

So the buy app needs a narrow new method reading exactly those two fields:

```kotlin
fun observeSellerContacts(sellerId: String): Flow<SellerContactDirectory>
```

Two child reads, no access to `lat`, `lng` or the seller's street address — the fields
stay closed by default rather than being published wholesale, which was the objection to
relaxing the parent `.read`.

**A subscription, not a fetch.** This shipped first as
`suspend fun getSellerContacts(): Result<…>`, called once when the buy app connected to
a seller and once more after auth. That made the directory a snapshot of whatever the
seller had published the last time that buyer connected: a number changed in the morning
was still being dialled in the afternoon, and a contact withdrawn entirely stayed in the
top bar until the app was killed. The one-shot has been removed rather than kept
alongside, so there is no stale path left for a future caller to pick by accident.

`BuyAppViewModel.observeSellerContacts()` starts the collection once, keyed off
`connectedSellerId`, so connect, switch and disconnect are one event and nothing has to
remember to re-subscribe. It is the single writer of `BuyAppState.sellerContacts`;
everything on screen derives from that one field, which is what makes the top bar
overflow and the About card agree by construction rather than by two subscriptions
happening to stay in step.

The markets half falls back to the legacy `seller_profile/{sellerId}/markets` list when
the `/markets/{sellerId}` node is *empty*, not only when it errors — an unmigrated seller
has markets, they are just not in the node yet, and treating the empty node as the answer
would silently drop every stall contact they have.

### What this costs

`GitLiveProfileRepository` hand-rolls the market map in both directions
(`mapSnapshotToSellerProfile` around line 452, `sellerProfileToMap` around line 565).
A nested contact list adds roughly fifteen lines to each. That is the whole price of
dropping a node, a rules block and an editor screen.

### Why not a separate `seller_contact` node after all

It was the first proposal here. Rejected once contacts became market-owned: a separate
node would have to mirror the market relationship with a `marketId` foreign key, needs
its own rules and validation, and orphans its rows when a market is deleted. Nesting
gets ownership, atomic save and cascade delete for free.

### BUILT DIFFERENTLY: one more public child, for closures

`getSellerClosures` reads `seller_profile/{sellerId}/closures` the same way, added
while building the markets work — see `doc/guides/market-pricing-and-access-design.md` §4.
Three public children now: `displayName`, `contacts`, `closures`, plus `markets`.

### `SellerProfile.telephoneNumber` stays

It remains the seller's own record of their number, unrelated to what is published. The
seller app may offer it as the starting value when adding a first `PHONE` contact — a
convenience at save time, not a live dependency.

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
2. **`SellerContact` + serialization** in `GitLiveProfileRepository`, both directions,
   including the skip-unknown-channel path. No rules change.
3. **Seller editing** — a contact list in the profile form, and the same list inside the
   market dialog (`SellerProfileScreen.kt:828`). One composable used twice. The largest
   chunk.
4. **Buy app read** — `observeSellerContacts`, reading the two fields rather than the
   node, plus the union-and-dedup resolution.
5. **The four call sites.** All four move together or the app shows different numbers on
   different screens.
6. **Visibility rule** — `canContactSeller`, plus the contact link in `AccessStatusCard`.

All six are done. **BUILT DIFFERENTLY:** the fourth call site,
`ContactActionButton` in `CustomerProfileScreenModern`, turned out to be dead code —
defined, never referenced anywhere in either flavor. It was deleted rather than
ported, so three call sites now read the published list and the bundled strings
survive only as the About screen's offline fallback.

None of this waits on the markets work. Contacts attach to `Market` as it exists today —
a value class in the profile's `markets` list — and move with it unchanged when markets
become their own node (`doc/guides/market-pricing-and-access-design.md` §1).

The contextual-market resolution in §2 is the one part that stays degenerate until then:
with no `Order.marketId` and no eligibility, step 3 of the resolution ("the buyer's only
eligible market") is the only one that can fire, so a single-market seller works fully
and a multi-market seller shows seller-level contacts in the top bar and everything on
About. That is correct behaviour, not a stub.

---

## Still open

### 1. Which channels ship in v1?

`PHONE` and `EMAIL` are the current behaviour. `WHATSAPP` is plausible for a market
trader; `SIGNAL`, `WEB` and `INSTAGRAM` are speculation. Every channel is an icon, a
label, two translations and a normalization rule. Shipping two and adding on demand is
probably right, but the enum should be defined with the full set from the start so stored
values never need rewriting.

### 2. Ordering within a level

`position` was dropped from `SellerContact` along with `id` and `marketId`: list order in
the stored array is the seller's order, which the market dialog already gives for free.
That works until someone wants to reorder without retyping, at which point the editor
needs drag-to-reorder. Fixed precedence by channel (phone, then email, then the rest)
would avoid it entirely. Only worth deciding if a seller would really want email above
phone.

### 3. Should an approved buyer see other markets' contacts in the menu?

As specified, no — the menu shows seller-level plus the contextual market, and About has
everything. A buyer who collects at two markets and wants the other stall's number has to
open About. Probably correct, but it is an assumption, not a finding.

### 4. Does a market need to suppress a seller-level contact?

Union means a market can only add. A stall that specifically should *not* be reached on
the office line has no way to say so. A `hidden` flag or an explicit override mode would
cover it, at the cost of the simplicity union buys. Nobody has asked for it; worth
leaving out until they do.

### 5. Does the seller-level list need a migration from `telephoneNumber`?

A seller who has filled in `telephoneNumber` today gets an empty `contacts` list
tomorrow, and the buy app falls back to the `about_*` strings rather than their actual
number. Seeding the first `PHONE` contact from `telephoneNumber` on first load would
avoid that, but writes to the profile on a read path. The alternative is leaving it to
the seller to enter once. Low stakes with one seller; worth a thought before there are
several.
