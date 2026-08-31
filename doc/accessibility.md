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

Covered:

- `ModernProductCard` (buy product grid)
- `FavoriteProductCard` (buy favourites list)
- `BuyerBottomBar` (buy bottom navigation + rail) — icons made decorative so the
  localized text label is not read twice; basket badge announces "N Artikel"
  instead of a bare number.
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

- `ProfileHeaderCard` (buy profile, first card) — the whole card is one node via
  `clearAndSetSemantics`: name, verification, email and linked auth providers read
  in one go.
- `PersonalInfoCard` (buy profile, second card) — the section header is a single
  `Role.Button` toggle with an `onClickLabel` and expanded/collapsed
  `stateDescription`; the duplicate tap target on the chevron is hidden from the
  a11y tree. Field errors moved into `OutlinedTextField.supportingText` so they
  are announced with the field. Localised the "Toggle address visibility" and
  self-pickup hint strings.

Not yet covered: `ProductListItem` / `ProductDetailCard` (currently sell-only /
unused), the rest of the profile screen, other sell-flavour composables, forms,
dialogs.

### Traversal order

Compose exposes a single linear accessibility order — "swipe down" and "swipe
right" walk the same sequence. Order a container's children with
`Modifier.semantics { isTraversalGroup = true }` on the container and
`traversalIndex = <n>f` on each child; lower indices come first, and any click
action on the same node is merged into that stop.
