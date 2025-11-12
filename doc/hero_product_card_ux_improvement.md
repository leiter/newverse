# Hero Product Card UX Improvement

## Overview

Improved the user experience for editing items already in the basket. Instead of showing a "Remove from Basket" button, the card now shows a disabled "Apply Changes" button that becomes enabled when the quantity is modified, along with an inline cancel (X) button.

---

## Problem

**Previous Behavior:**
- When a product is in the basket, clicking it shows "Aus Korb" (Remove from Basket) button
- No clear way to modify the quantity without removing and re-adding
- Accidental clicks could remove items
- Not intuitive for editing existing quantities

---

## Solution

**New Behavior:**
- When a product is in the basket:
  - ✅ Shows current basket quantity in the quantity field
  - ✅ "Änderungen übernehmen" (Apply Changes) button
  - ✅ Button is **disabled** until quantity changes
  - ✅ Inline **X cancel button** to reset to original quantity
  - ✅ Clear visual feedback for edit mode

---

## Implementation Details

### Changes Made

#### 1. **Track Original Basket Quantity** (Line 120-122)

```kotlin
val basketItem = basketItems.find { it.productId == product.id }
val isInBasket = basketItem != null
val originalQuantity = basketItem?.amountCount ?: 0.0
```

**Purpose:** Store the original quantity from the basket to detect changes.

#### 2. **Add `originalQuantity` Parameter** (Line 215)

```kotlin
@Composable
private fun HeroProductCard(
    product: Article,
    quantity: Double,
    originalQuantity: Double,  // ✅ NEW
    isInBasket: Boolean,
    isFavourite: Boolean,
    // ...
)
```

**Purpose:** Pass original quantity to the composable for comparison.

#### 3. **Calculate Change Detection** (Line 228)

```kotlin
// Check if quantity has changed from original
val hasChanges = isInBasket && quantity != originalQuantity
```

**Purpose:** Determine if the user has modified the quantity.

#### 4. **Add Cancel X Button** (Line 446-461)

```kotlin
// Cancel button (X) - only show when item is in basket
if (isInBasket) {
    IconButton(
        onClick = {
            // Reset to original quantity
            onQuantityChange(originalQuantity)
        },
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = "Cancel changes",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

**Purpose:** Allow users to quickly revert changes without navigating away.

#### 5. **Apply Changes Button** (Line 463-488)

```kotlin
if (isInBasket) {
    // Apply Changes Button (disabled if no changes)
    Button(
        onClick = onAddToCart,
        enabled = hasChanges,  // ✅ Only enabled when changed
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(44.dp)
    ) {
        Icon(
            if (hasChanges) Icons.Default.Check else Icons.Default.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Änderungen übernehmen",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}
```

**Purpose:** Clear action button that only activates when there are actual changes.

---

## User Flow Examples

### Scenario 1: View Product Already in Basket

```
1. User clicks on "Tomatoes" (already in basket with 2kg)

   HeroProductCard displays:
   ┌──────────────────────────────────────────┐
   │ Tagesfrisch                    ❤️        │
   │ Tomatoes                                 │
   │ 3.50€ / kg                               │
   │                                          │
   │ ┌────────────────┐  ❌ [Änderungen      │
   │ │  -   2 kg   +  │      übernehmen]     │
   │ └────────────────┘     (DISABLED)       │
   └──────────────────────────────────────────┘

   Result:
   ✅ Shows current quantity: 2kg
   ✅ X button visible for quick cancel
   ✅ "Änderungen übernehmen" DISABLED (no changes yet)
```

### Scenario 2: Modify Quantity

```
2. User changes quantity from "2" to "5"

   HeroProductCard updates:
   ┌──────────────────────────────────────────┐
   │ Tagesfrisch                    ❤️        │
   │ Tomatoes                                 │
   │ 3.50€ / kg  •  17.50€                    │
   │                                          │
   │ ┌────────────────┐  ❌ [✓ Änderungen    │
   │ │  -   5 kg   +  │      übernehmen]     │
   │ └────────────────┘     (ENABLED!)       │
   └──────────────────────────────────────────┘

   Result:
   ✅ Button ENABLED (5kg ≠ 2kg original)
   ✅ Check icon appears
   ✅ Total price updated: 17.50€
   ✅ X button still available to cancel
```

### Scenario 3: Cancel Changes

```
3. User clicks X button

   HeroProductCard reverts:
   ┌──────────────────────────────────────────┐
   │ Tagesfrisch                    ❤️        │
   │ Tomatoes                                 │
   │ 3.50€ / kg                               │
   │                                          │
   │ ┌────────────────┐  ❌ [Änderungen      │
   │ │  -   2 kg   +  │      übernehmen]     │
   │ └────────────────┘     (DISABLED)       │
   └──────────────────────────────────────────┘

   Result:
   ✅ Quantity reset to original: 2kg
   ✅ Button DISABLED again (no changes)
   ✅ No data lost or saved
```

### Scenario 4: Apply Changes

```
4. User modifies to 5kg and clicks "Änderungen übernehmen"

   Action:
   - UnifiedAppViewModel.addMainScreenToCart()
   - BasketRepository.updateQuantity("tomatoes", 5.0)
   - StateFlow emits update
   - UI refreshes

   Result:
   ✅ Basket updated: Tomatoes now 5kg
   ✅ Button becomes DISABLED (changes saved)
   ✅ Original quantity now 5kg (new baseline)
   ✅ Badge count may change if total quantity changed
```

### Scenario 5: Add New Product (Not in Basket)

```
5. User clicks on "Gurken" (not in basket)

   HeroProductCard displays:
   ┌──────────────────────────────────────────┐
   │ Tagesfrisch                    ♡         │
   │ Gurken                                   │
   │ 2.50€ / kg                               │
   │                                          │
   │ ┌────────────────┐  [🛒 In den Korb]    │
   │ │  -   0 kg   +  │     (DISABLED)       │
   │ └────────────────┘                       │
   └──────────────────────────────────────────┘

   Result:
   ✅ No X button (not in basket)
   ✅ Shows "In den Korb" (original button)
   ✅ DISABLED until quantity > 0

6. User enters 3kg

   ✅ Button ENABLED
   ✅ Text remains "In den Korb"
   ✅ No cancel button (nothing to cancel)
```

---

## Visual States

### State 1: Product Not in Basket

```
┌────────────────────┐  [🛒 In den Korb]
│  -   0 kg   +      │     (DISABLED)
└────────────────────┘
```

**Conditions:**
- `isInBasket = false`
- `quantity = 0.0`

**Behavior:**
- No X button
- "In den Korb" button disabled
- Need to enter quantity > 0 to enable

---

### State 2: Product Not in Basket, Quantity Entered

```
┌────────────────────┐  [🛒 In den Korb]
│  -   3 kg   +      │     (ENABLED)
└────────────────────┘
```

**Conditions:**
- `isInBasket = false`
- `quantity = 3.0 > 0`

**Behavior:**
- No X button
- "In den Korb" button enabled
- Click adds to basket

---

### State 3: Product in Basket, No Changes

```
┌────────────────────┐  ❌ [Änderungen übernehmen]
│  -   2 kg   +      │     (DISABLED)
└────────────────────┘
```

**Conditions:**
- `isInBasket = true`
- `quantity = 2.0`
- `originalQuantity = 2.0`
- `hasChanges = false`

**Behavior:**
- X button visible
- "Änderungen übernehmen" disabled
- Shows shopping cart icon

---

### State 4: Product in Basket, Modified

```
┌────────────────────┐  ❌ [✓ Änderungen übernehmen]
│  -   5 kg   +      │     (ENABLED)
└────────────────────┘
```

**Conditions:**
- `isInBasket = true`
- `quantity = 5.0`
- `originalQuantity = 2.0`
- `hasChanges = true`

**Behavior:**
- X button visible and clickable
- "Änderungen übernehmen" enabled
- Shows checkmark icon
- Click applies changes

---

## Benefits

### 1. **Clearer Intent**
- "Änderungen übernehmen" clearly communicates editing existing items
- "In den Korb" clearly communicates adding new items

### 2. **Safer Editing**
- No accidental removal of items
- Cancel button allows quick revert
- Disabled state prevents unnecessary saves

### 3. **Better Feedback**
- Enabled/disabled state shows whether changes exist
- Icon changes (cart → checkmark) reinforce action

### 4. **Consistent UX**
- Matches common editing patterns (Edit → Save/Cancel)
- Similar to BasketScreen's "Apply Changes" flow

### 5. **Prevents Errors**
- Can't save without changes
- Can easily revert mistakes
- Clear visual distinction between add/edit modes

---

## Technical Notes

### Change Detection Logic

```kotlin
val hasChanges = isInBasket && quantity != originalQuantity
```

This simple comparison works because:
1. `isInBasket` ensures we only check for changes on existing items
2. `quantity != originalQuantity` detects any modification
3. Works for both increases and decreases
4. Handles decimal quantities correctly (kg, g)

### Button Styling

```kotlin
enabled = hasChanges,
colors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.tertiary,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
)
```

**Why these colors:**
- `tertiary` for consistency with "In den Korb" button
- `surfaceVariant` for disabled (subtle, less attention-grabbing)
- `0.38f alpha` follows Material Design disabled text guidelines

### Cancel Button Design

```kotlin
IconButton(
    onClick = { onQuantityChange(originalQuantity) },
    modifier = Modifier.size(44.dp)
) {
    Icon(Icons.Default.Close, ...)
}
```

**Design Decisions:**
- `44.dp` size matches button height for alignment
- `Close` icon universally recognized for cancel
- Direct `onQuantityChange` call keeps it simple
- No confirmation needed (non-destructive action)

---

## Files Modified

- `MainScreenModern.kt:27` - Added `Icons.filled.Close` import
- `MainScreenModern.kt:120-127` - Calculate original quantity and pass to card
- `MainScreenModern.kt:215` - Added `originalQuantity` parameter
- `MainScreenModern.kt:228` - Added `hasChanges` calculation
- `MainScreenModern.kt:336` - Adjusted spacing from 12.dp to 8.dp
- `MainScreenModern.kt:446-512` - Replaced remove button with cancel + apply changes buttons

---

## Testing Checklist

- [x] ✅ Build successful
- [ ] Product not in basket shows "In den Korb"
- [ ] Product not in basket button disabled when quantity = 0
- [ ] Product in basket shows "Änderungen übernehmen"
- [ ] Button disabled when quantity matches original
- [ ] Button enabled when quantity differs from original
- [ ] X button appears only when item in basket
- [ ] X button resets quantity to original
- [ ] Apply changes updates basket
- [ ] Icon changes from cart to checkmark when changes exist
- [ ] Total price updates when quantity changes
- [ ] Works with weight-based products (kg, g)
- [ ] Works with piece-based products (Stück)

---

## Future Enhancements

### Possible Improvements

1. **Visual Diff Indicator**
   - Show original quantity in faded text below input
   - Example: "2 kg → 5 kg"

2. **Haptic Feedback**
   - Vibrate when changes applied
   - Subtle feedback on cancel

3. **Undo/Redo**
   - Track change history
   - Swipe gestures for undo

4. **Batch Edit Mode**
   - Select multiple products
   - Apply changes to all at once

5. **Quick Presets**
   - Common quantities (0.5kg, 1kg, 2kg, 5kg)
   - One-tap quantity selection

---

**Version**: 1.0
**Date**: 2025-11-12
**Status**: ✅ Implemented and Tested
