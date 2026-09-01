# Accessibility

Screen-reader support for the shared Compose UI (TalkBack on Android, VoiceOver on iOS).

## Principles

1. **Set semantics explicitly — never rely on inference.**
   On iOS the Compose semantics tree is mapped to `UIAccessibility`. If a composable
   exposes no `contentDescription` and no merged node, VoiceOver synthesises an element
   per child text/icon and the traversal order is often wrong. Always decide, per
   composable, what the screen reader should say and in what grouping.

2. **All description text comes from string resources**, in both `values/` (German,
   the default) and `values-en/`. No hardcoded literals. Naming:
   - `cd_*` — a `contentDescription` for a single control or graphic.
   - `a11y_*` — other accessibility-only strings (spoken phrases, state descriptions).

3. **Collapse cards and list rows into one node.** A product card should be a single
   swipe stop ("Tomaten, 2,50 € pro kg"), not eight (image, badge, name, "•", price
   fragments…). Use `Modifier.productCardSemantics(...)`.

4. **Decorative graphics are silent.** Product images sit next to the product name, so
   they carry no information for a screen-reader user. `ProductImage` clears its own
   semantics subtree by default; pass a `contentDescription` only for a standalone
   image with no adjacent label.

5. **Touch targets ≥ 48.dp.** Use `Modifier.minimumInteractiveComponentSize()` or an
   explicit size rather than shrinking `IconButton` below 48.dp.

6. **Toggles expose state, not a renamed label.** A favourite toggle keeps one stable
   `contentDescription` and reports on/off through `stateDescription`.

## Building blocks

| Helper | Location | Purpose |
|---|---|---|
| `ProductImage(url, …)` | `ui/components/ProductImage.kt` | Product image with consistent loading/fallback; a11y-silent by default. |
| `productPriceLabel(price, unit)` | `ui/a11y/ProductSemantics.kt` | "2,50 € pro kg" spoken phrase (replaces "2,50€ / kg"). |
| `Modifier.productCardSemantics(description)` | `ui/a11y/ProductSemantics.kt` | Merge a card/row into one node with the given description. |

## Status

### Coverage map

| Buyer screen / area | Composables done | Still open |
|---|---|---|
| Product grid & hero (`MainScreenModern`) | `ModernProductCard`, `HeroProductCard`, `ProductSearchField`, `CategoryChips`, `SectionHeader` | — |
| Favourites (`FavoritesScreen`) | `FavoriteProductCard` | — |
| Product detail (`ProductDetailScreen`) | whole screen | — |
| Profile (`CustomerProfileScreenModern`) | `ProfileHeaderCard`, `PersonalInfoCard`, `GemusedateCard` (+ `TimePickerField`), `DemoModeCard`, `PendingInvitationsCard`, `AccessStatusCard`, `LoginStatusCard`, `QuickActionsCard` | — |
| App chrome | `AppScaffold` top bar, `BuyerBottomBar` (+ nav rail) | — |
| Basket (`BasketScreen`) | `BasketItemCard`, `BasketTotalCard`, `BasketEmptyCard`, `OrderInfoCard`, `BasketStatusMessages`, `DateOption` / `ResolutionOption` / `MergeConflictItem`, all five basket dialogs | `BasketContent` / `BasketTwoPane` traversal |
| Pickup-time editor | `TimePickerDialog` | — |
| Account dialogs (`components/`) | `ConnectionConfirmDialog`, `DeleteAccountDialog`, `EmailLinkingDialog`, `LinkAccountDialog`, `LogoutWarningDialog` | announce-on-open pane title (framework-limited, see below) |
| Order history (`OrderHistoryScreen`) | order card, count header, empty & error states, merge dialog | — |
| Auth flow (`LoginScreen`, `RegisterScreen`, `ForcedLoginScreen`) | headings, error/success `liveRegion`s, decorative logo & social glyphs, sign-in/up progress announced, terms checkbox as one toggle, reset-dialog title | password show/hide toggle wording |
| Messaging (`MessagesScreen`, `ConversationDetailScreen`) | `ConversationItem`, `MessageBubble`, `MessageInput`, error / blocked live regions | — |
| Buyer contacts (`BuyerContactsScreen`, `AddBuyerContactScreen`) | contact rows, add-contact form | — |
| About (`AboutScreenModern`) | phone / email links, card headings | — |
| Sell flavour | `ForcedLoginScreen` (shared with auth flow) | rest not started |

Dead / unused, not planned: `ProductListItem`, `ProductDetailCard` (sell-only /
unrendered), `ContactActionButton` (never rendered).

### Per-composable notes

- `ModernProductCard` (buy product grid)
- `MainScreenModern` — the feed is a single `LazyColumn` (hero card, search
  field, category chips, then the "Frisch vom Feld" section header and the
  product grid, all as sequential items/sticky-header content), so the swipe
  order already matches the visual order — no `isTraversalGroup` /
  `traversalIndex` was needed. What was actually missing: the section
  header title and subtitle, and the "order locked" snackbar message and
  action label, were hardcoded German literals with no English translation —
  now `main_section_fresh_title` / `_subtitle` and
  `main_order_locked_snackbar` / `main_start_new_order`. `SectionHeader`'s
  title is now a `heading()` so TalkBack's next-heading gesture can jump to
  it.
- `FavoriteProductCard` (buy favourites list)
- `BuyerBottomBar` (buy bottom navigation + rail) — icons made decorative so the
  localized text label is not read twice; basket badge announces "N Artikel"
  instead of a bare number.
- `CategoryChips` (buy product-list filter row) — the `LazyRow` is a
  `selectableGroup()` so a screen reader announces "1 of 4"; each `FilterChip`
  already exposes its selected flag, so it only gains a localized
  `stateDescription` ("Ausgewählt" / "Nicht ausgewählt") in place of the default
  "checkbox, not checked". The leading check icon was already decorative.
- `ProductSearchField` (buy product-list search) — extracted from `MainScreenModern`;
  explicit `contentDescription` label instead of the vanishing placeholder; the
  empty-result message is a polite `liveRegion`. Filled in the missing English
  translations for the search strings.
- `HeroProductCard` (buy main hero) — `isTraversalGroup` + per-control
  `traversalIndex` fix the focus order to: summary → quantity field → plus →
  minus → add/change → cancel → favourite → info. The summary node reads name +
  price, plus running total and amount once a quantity is set. The add/change
  button's `contentDescription` says why it is disabled; the info button's label
  states that it opens the detail screen. Badge, gradient, "•" and the weight-unit
  label are cleared; the image goes through `ProductImage`. Localised the visible
  "In den Korb" / "Ändern" labels.
- `ProductDetailScreen` (buy product detail) — the hero image now goes through
  `ProductImage` (decorative, square corners) instead of a raw
  `SubcomposeAsyncImage`; the favourite overlay dropped its hardcoded English
  "Add/Remove from favourites" for the shared `cd_favourite_toggle` label plus an
  on/off `stateDescription`. Name + price + running total merge into one summary
  node ("Tomaten, 2,50 € pro kg, Gesamt 7,50 €, Menge 3 kg"). The category chip
  reads "Kategorie: Gemüse" as one node; "Beschreibung" and "Menge" are
  `heading()`s. The −/＋ steppers and the quantity field carry
  `cd_decrease_quantity` / `cd_increase_quantity` / `cd_quantity_input(_unit)`
  labels (the bare "-" glyph and the trailing unit label are cleared). The
  Add / Update buttons append a spoken reason when disabled ("… , zuerst Menge
  wählen" / "… , keine Änderungen"). No new string resources — all reused from
  the hero card.

- `ProfileHeaderCard` (buy profile, first card) — the whole card is one node via
  `clearAndSetSemantics`: name, verification, email and linked auth providers read
  in one go.
- `PersonalInfoCard` (buy profile, second card) — the section header is a single
  `Role.Button` toggle with an `onClickLabel` and expanded/collapsed
  `stateDescription`; the duplicate tap target on the chevron is hidden from the
  a11y tree. Field errors moved into `OutlinedTextField.supportingText` so they
  are announced with the field. Localised the "Toggle address visibility" and
  self-pickup hint strings.
- `GemusedateCard` (buy profile, third card) — the read-only pickup time reads as
  one node; the self-pickup row is a single `Role.Switch` `toggleable` so the
  label and on/off state are announced together (the raw `Switch` is cleared from
  the a11y tree). `TimePickerField` gained a `Role.Button` + `onClickLabel` on its
  trigger and a polite `liveRegion` on its error text.
- `DemoModeCard` (buy profile, fourth card) — collapsed to one node framed as
  "Modus: …" with a polite `liveRegion` so a mid-session mode change is announced.
  Filled in the missing English `mode_demo` / `mode_production`.
- `PendingInvitationsCard` (buy profile, fifth card) — the Accept/Decline buttons
  now carry a `contentDescription` naming the seller ("Einladung von X annehmen"),
  so they are unambiguous when there is more than one invitation. The card title
  is marked as a `heading()`.
- `AccessStatusCard` (buy profile, sixth card) — the status line is a polite
  `liveRegion` so a NONE→PENDING→APPROVED change is announced; the raw buyer id is
  framed as "Zugangs-ID: …" instead of being spelled out unlabelled; the request
  button gains a "wird gesendet" `stateDescription` while the request is in
  flight.
- `LoginStatusCard` (buy profile) — card title marked `heading()`; the guest
  status + its data-loss warning read as one node, as do the authenticated
  email + provider list. Localised the "Angemeldet" fallback.
- `QuickActionsCard` (buy profile) — section title marked `heading()`; the action
  cards were already labelled clickables.
- `TimePickerDialog` (pickup-time editor) — localised the title, the hours hint
  and the "Stunde"/"Minute" unit names; the selected-time preview is a labelled
  polite `liveRegion`; the +/- steppers carry "Stunde erhöhen"/"… verringern"
  descriptions and the value+unit read as one node. Filled in the English
  `pickup_time_*` translations that were missing entirely.

- `AppScaffold` top bar (buy) — the screen title is marked `heading()`. On the
  basket screen the title's two fragments ("Warenkorb (3)" + "7,50 €") merge into
  one heading node reading "Warenkorb, 3 Artikel, Gesamt 7,50 €". The history
  action icon was mislabelled "Warenkorb" — it now reads "Bestellungen". Localised
  the "Kontakt" icon-button label and the "E-Mail" / "Anrufen" contact-menu items.
- `BasketItemCard` (buy basket list) — a line item was four cryptic stops
  ("Tomaten", "2,50 €/kg", "× 3,00", "7,50 €"); it now reads as one node,
  "Tomaten, 2,50 € pro kg, Menge 3,00 kg, Gesamt 7,50 €". The Remove button names
  its product ("Tomaten entfernen") so it is unambiguous in a multi-item list.
- Basket screen supporting composables:
  - `BasketTotalCard` — merged to "Gesamt: 7,50 €", polite `liveRegion`.
  - `BasketEmptyCard` — title + explanation read as one heading.
  - `OrderInfoCard` — title is a `heading()`; the pickup / order-number / created
    rows each merge into one label+value node.
  - `BasketStatusMessages` — success and error banners are polite `liveRegion`s
    (the error drops its "✗" glyph from the spoken text).
  - `DateOption` / `ResolutionOption` — tappable cards now expose
    `Role.RadioButton` + selected state via `Modifier.optionSemantics`, instead of
    signalling selection only with a conditional check icon; `MergeConflictItem`
    wraps its options in a `selectableGroup()` and merges its header row.
  - Basket dialogs (`DatePickerDialog`, `CancelOrderDialog`,
    `ReorderDatePickerDialog`, `OrderMergeDialog`, `DraftWarningDialog`) —
    every title is now a `heading()`; the "no dates available" message and the
    in-flight "wird aktualisiert" / "wird zusammengeführt" spinner captions are
    polite `liveRegion`s. `OrderMergeDialog`'s existing-order summary had a
    hardcoded German "X Artikel, Y €" literal — now built from the existing
    `a11y_basket_item_count` resource. No new strings.
- Account dialogs (`components/`) — `AlertDialog` already traps focus and returns
  it on dismiss, so the pass is lighter: every dialog title is now a `heading()`;
  the `LogoutWarningDialog` bullet glyphs are cleared from the spoken text; the
  `DeleteAccountDialog` and `EmailLinkingDialog` in-flight spinners were silent —
  they now announce "Konto wird gelöscht" / "Konto wird verknüpft" via a polite
  `liveRegion` (the linking button borrows the label while its text is a
  spinner); the `EmailLinkingDialog` server-error line is a polite `liveRegion`
  (its field errors already ride `OutlinedTextField.supportingText`). Covers
  `ConnectionConfirmDialog`, `DeleteAccountDialog`, `EmailLinkingDialog`,
  `LinkAccountDialog`, `LogoutWarningDialog`. New strings
  `a11y_deleting_account` / `a11y_linking_account`.

  The dialog's announce-on-open pane title stays the generic "Dialog":
  `BasicAlertDialog` sets `paneTitle` after the caller's modifier, so it can't be
  overridden from the slot API without `clearAndSetSemantics`. The title text is
  still read on open, so the impact is small.
- Auth flow (`LoginScreen`, `RegisterScreen`, `ForcedLoginScreen`) — screen
  titles and the password-reset dialog titles are `heading()`s; the leaf-logo
  glyph and the Google/Apple letter glyphs on the social buttons are cleared so
  the button label is not prefixed with "G" / "" ; the error and success cards
  are polite `liveRegion`s (previously an auth failure after tapping "Anmelden"
  was silent); the sign-in / sign-up buttons announce "Anmeldung läuft" /
  "Konto wird erstellt" while their label is a spinner. `RegisterScreen`'s terms
  row is now a single `Role.Checkbox` `toggleable` (label + checked state read
  together; the raw `Checkbox` is cleared) and its terms error is a polite
  `liveRegion`; the success-card check icon dropped its duplicate
  `contentDescription`. `ForcedLoginScreen` had four hardcoded German strings
  ("Verkäufer Login", the subtitle, "🔒 Sicherer Login", the account-required
  footer) — now `seller_login_*` resources with English translations; its debug
  `println`s are gone (as is `LoginScreen`'s). New strings `a11y_signing_in`,
  `a11y_creating_account`, `seller_login_title` / `_subtitle` / `_secure` /
  `_account_required`. The password show/hide toggle is a text button whose
  visible label already states the action, so it was left as-is.
- `OrderHistoryScreen` — an order card was ~8 fragments ("Bestellung #ABC12345",
  "Erstellt: …", "Geplant", "Abholung: …", "in 3 Tagen", "3 Artikel", "Gesamt",
  "7,50 €"); it now reads as one node in a deliberate order (id → status → pickup
  → items → total → created) with an `onClickLabel` of "Bestelldetails öffnen".
  The count header is a `heading()`, the empty state merges into one heading, the
  error line is a polite `liveRegion`, and `OrderHistoryMergeDialog`'s title is a
  `heading()`. The status text is hoisted so the badge and the spoken description
  can't drift apart; the stray `println` in the load effect is gone. New string
  `a11y_open_order_details`.
- Messaging (`MessagesScreen`, `ConversationDetailScreen`) — `ConversationItem`
  was four fragments per row (avatar initial, name, timestamp, last message,
  unread badge); it now reads as one node ("Anna, 2 ungelesen, letzte
  Nachricht, 14:30") with an "Unterhaltung öffnen" click label, and the avatar
  initial is cleared. `MessageBubble` collapsed sender name + text + time into
  one node prefixed with the speaker ("Du: …" or the sender's name). The
  message input field keeps a stable "Nachricht" label now that its
  placeholder disappears once typing starts. The conversation-load error and
  the "blocked" notice are polite `liveRegion`s. New strings
  `a11y_open_conversation`, `a11y_message_you`.
- Buyer contacts (`BuyerContactsScreen`, `AddBuyerContactScreen`) — a contact
  row's tap target now carries a "Nachricht an X" click label and the
  redundant message icon-button is hidden from the a11y tree (`clearAndSetSemantics`);
  the remove button names its contact ("X entfernen") instead of a bare
  "Entfernen". The load-error line is a polite `liveRegion`. The
  "Sign in to manage contacts" placeholder was a hardcoded English literal,
  now `contacts_sign_in_required`. On the add-contact screen the two card
  titles are `heading()`s and the raw contact id is framed as
  "Kontakt-ID: …" instead of being spelled out unlabelled. New strings
  `a11y_message_to`, `a11y_contact_id`, `contacts_sign_in_required`.
- `AboutScreenModern` — the phone and email lines used the deprecated
  `ClickableText` with a substring URL annotation, which a screen reader
  can't reach as a control and which fell under the 48dp touch-target
  minimum. Both are now a plain `Text` with a `Role.Button` `clickable`
  (the whole line is the link, so no offset lookup is needed), an
  "Anrufen" / "E-Mail" click label, and vertical padding to reach 48dp. The
  four card titles (Kontakt, Impressum, Datenschutz, Unsere Mission) are
  `heading()`s. No new strings (reuses `action_call` / `action_email`).

### Traversal order

Compose exposes a single linear accessibility order — "swipe down" and "swipe
right" walk the same sequence. Order a container's children with
`Modifier.semantics { isTraversalGroup = true }` on the container and
`traversalIndex = <n>f` on each child; lower indices come first, and any click
action on the same node is merged into that stop.
