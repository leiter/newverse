# Remove from Basket Workflow

## Overview

Updated the X button on HeroProductCard to provide dual functionality:
1. **Cancel changes** when user has modified the quantity
2. **Remove from basket** when no changes have been made

## Implementation

### X Button Behavior (Line 498-517)

```kotlin
// Cancel/Remove button (X) - only show when item is in basket
if (isInBasket) {
    IconButton(
        onClick = {
            if (hasChanges) {
                // If user made changes, reset to original quantity
                onQuantityChange(originalQuantity)
            } else {
                // If no changes, set to 0 to remove item
                onQuantityChange(0.0)
            }
        },
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = if (hasChanges) "Cancel changes" else "Remove from basket",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

### Removal Logic (UnifiedAppViewModel.kt:1433-1438)

```kotlin
private fun addMainScreenToCart() {
    val selectedArticle = _state.value.screens.mainScreen.selectedArticle ?: return
    val quantity = _state.value.screens.mainScreen.selectedQuantity

    if (quantity <= 0.0) {
        // If quantity is 0, remove from basket if it exists
        viewModelScope.launch {
            basketRepository.removeItem(selectedArticle.id)
        }
        return
    }

    // ... rest of add/update logic
}
```

## User Workflows

### Workflow 1: Remove Item from Basket (No Changes)

**Initial State:**
- Product: Tomatoes (2kg in basket)
- Quantity field: "2"
- hasChanges: false
- Button: "Ändern" (DISABLED)

**User Action:** Click X button

**Result:**
```
1. X button clicked → hasChanges = false
   ├─> onQuantityChange(0.0) called
   └─> selectedQuantity = 0.0

2. UI updates:
   ├─> Quantity field: "0"
   ├─> hasChanges = true (0 ≠ 2)
   └─> Button: "Ändern" (ENABLED)

3. User clicks "Ändern" button

4. addMainScreenToCart() called:
   ├─> quantity = 0.0
   ├─> if (quantity <= 0.0) → TRUE
   ├─> basketRepository.removeItem("tomatoes")
   └─> Basket updated: Tomatoes REMOVED ✅

5. UI reflects removal:
   ├─> isInBasket = false (no X button anymore)
   ├─> Quantity: "0"
   └─> Button: "In den Korb" (DISABLED)
```

### Workflow 2: Cancel Changes (User Modified Quantity)

**Initial State:**
- Product: Tomatoes (2kg in basket)
- User changed quantity to 5kg
- Quantity field: "5"
- hasChanges: true
- Button: "Ändern" (ENABLED)

**User Action:** Click X button

**Result:**
```
1. X button clicked → hasChanges = true
   ├─> onQuantityChange(originalQuantity) // 2.0
   └─> selectedQuantity = 2.0

2. UI updates:
   ├─> Quantity field: "2" (reset to original)
   ├─> hasChanges = false (2 == 2)
   └─> Button: "Ändern" (DISABLED)

3. Changes canceled, basket unchanged ✅
```

### Workflow 3: Remove After Modification

**Initial State:**
- Product: Tomatoes (2kg in basket)
- User changed quantity to 5kg
- Quantity field: "5"
- hasChanges: true
- Button: "Ändern" (ENABLED)

**User Action 1:** Click X (cancel changes)
```
├─> Quantity resets to "2"
└─> hasChanges = false
```

**User Action 2:** Click X again (remove)
```
├─> Quantity set to "0"
├─> hasChanges = true
└─> Button enabled
```

**User Action 3:** Click "Ändern" (apply removal)
```
└─> Item removed from basket ✅
```

### Workflow 4: Direct Quantity to 0

**Initial State:**
- Product: Tomatoes (2kg in basket)
- Quantity field: "2"

**User Action:**
1. Type "0" in quantity field
2. Click "Ändern"

**Result:**
```
1. onQuantityChange(0.0)
   ├─> selectedQuantity = 0.0
   └─> hasChanges = true (0 ≠ 2)

2. Button enabled

3. User clicks "Ändern"

4. addMainScreenToCart():
   ├─> quantity = 0.0
   ├─> basketRepository.removeItem()
   └─> Item removed ✅
```

## Visual States

### State 1: Item in Basket, No Changes
```
┌────────────────────┐  ❌ [Ändern]
│  -   2 kg   +      │     (DISABLED)
└────────────────────┘

X button → Click to REMOVE (sets quantity to 0)
```

### State 2: Item in Basket, Modified
```
┌────────────────────┐  ❌ [✓ Ändern]
│  -   5 kg   +      │     (ENABLED)
└────────────────────┘

X button → Click to CANCEL (resets to 2kg)
```

### State 3: Item in Basket, Set to 0
```
┌────────────────────┐  ❌ [✓ Ändern]
│  -   0 kg   +      │     (ENABLED)
└────────────────────┘

"Ändern" button → Click to APPLY REMOVAL
```

### State 4: Item Removed
```
┌────────────────────┐  [🛒 In den Korb]
│  -   0 kg   +      │     (DISABLED)
└────────────────────┘

No X button (not in basket anymore)
```

## Benefits

### 1. **Dual Purpose X Button**
- Smart behavior based on context
- One button, two functions
- Saves UI space

### 2. **Confirmation Flow**
- Removal is two-step: Set to 0 → Apply
- Prevents accidental deletion
- User can cancel before applying

### 3. **Consistent with Edit Pattern**
- All changes (add, update, remove) go through "Ändern" button
- Single action point for modifications
- Clear confirmation step

### 4. **Discoverable**
- X button visible for all basket items
- Cancel function obvious when editing
- Remove function intuitive (X = close/remove)

## Comparison with Original

### Original UX (Before Changes)

```
Product in Basket:
┌────────────────────┐  [Aus Korb]
│  -   2 kg   +      │  (Remove Button)
└────────────────────┘

Issues:
❌ No way to edit quantity without removing
❌ Accidental clicks remove item immediately
❌ No "apply changes" concept
```

### New UX (After Changes)

```
Product in Basket:
┌────────────────────┐  ❌ [Ändern]
│  -   2 kg   +      │     (Apply Changes)
└────────────────────┘

Benefits:
✅ Edit quantity with apply step
✅ Cancel changes before applying
✅ Remove via X → 0 → Apply (two steps)
✅ Consistent edit pattern
```

## Edge Cases

### Edge Case 1: Quantity = Original = 0
**Scenario:** Item removed, user re-selects same product

**State:**
- isInBasket: false
- originalQuantity: 0
- quantity: 0
- No X button (not in basket)

**Behavior:** ✅ Correct - shows "In den Korb" button

### Edge Case 2: Rapid X Button Clicks
**Scenario:** User clicks X twice quickly

**State After Click 1:**
- If hasChanges=true → Reset to original
- If hasChanges=false → Set to 0

**State After Click 2:**
- hasChanges calculation updates
- Button behavior switches

**Behavior:** ✅ Works correctly due to reactive state

### Edge Case 3: Type 0 Then Click X
**Scenario:**
1. User types "0"
2. Clicks X button

**State:**
- quantity: 0
- originalQuantity: 2
- hasChanges: true

**Click X:**
- hasChanges=true → onQuantityChange(2.0)
- Resets to 2kg ✅

**Behavior:** ✅ Cancel works as expected

## Testing Checklist

- [x] ✅ Build successful
- [ ] X button shows when item in basket
- [ ] X button hidden when item not in basket
- [ ] Click X when no changes → sets quantity to 0
- [ ] Click X when has changes → resets to original
- [ ] Click "Ändern" with quantity=0 → removes item
- [ ] Item removed → X button disappears
- [ ] Item removed → shows "In den Korb" button
- [ ] Double click X → cancel then remove
- [ ] Type 0 manually → "Ändern" removes item

## Code Locations

- **X Button Logic**: `MainScreenModern.kt:498-517`
- **Remove Handler**: `UnifiedAppViewModel.kt:1433-1438`
- **hasChanges Calc**: `MainScreenModern.kt:228`

## Future Enhancements

### Possible Improvements:

1. **Visual Feedback**
   - Red tint on X button when in remove mode
   - Different icon when removing vs canceling
   - Tooltip on hover

2. **Confirmation Dialog**
   - Optional: Show dialog for removal
   - "Remove Tomatoes (2kg) from basket?"
   - Prevent accidental removals

3. **Undo**
   - Toast with "Undo" button after removal
   - Restore item with original quantity
   - 5-second timeout

4. **Swipe to Remove**
   - Alternative removal gesture
   - Swipe left on product card
   - Consistent with mobile patterns

---

**Version**: 1.0
**Date**: 2025-11-12
**Status**: ✅ Implemented
