# Basket Editing Strategy - Analysis & Recommendations

## Current Implementation Analysis

### How It Currently Works

#### 1. **Order Loading Flow**
- App starts → `UnifiedAppViewModel.loadOpenOrderAfterAuth()` loads open order
- Order items loaded into `BasketRepository` via `loadOrderItems()`
- `BasketViewModel` syncs state with loaded order
- User sees their existing order in basket

#### 2. **Edit Detection**
**Location:** `BasketViewModel.kt:135-194`

The system tracks changes through:
```kotlin
private fun checkIfBasketHasChanges(
    currentItems: List<OrderedProduct>,
    originalItems: List<OrderedProduct>
): Boolean
```

Detects:
- ✅ Items added (new productId)
- ✅ Items removed (productId missing)
- ✅ Quantity changes (amountCount differs)

#### 3. **Adding/Removing Items**

**From Main Screen:**
- User browses products → Selects product → Adds to cart
- `UnifiedAppViewModel.addMainScreenToCart()` → `basketRepository.addItem()` or `updateQuantity()`
- Basket immediately updated ✅
- `hasChanges` flag automatically updates ✅

**From Basket Screen:**
- User can adjust quantities via `BasketAction.UpdateQuantity`
- User can remove items via `BasketAction.RemoveItem`
- Changes tracked in real-time ✅

#### 4. **Update Flow**
When user clicks "Bestellung aktualisieren":
1. Validates edit deadline (3 days before pickup)
2. Checks basket not empty
3. Calls `orderRepository.updateOrder()`
4. Updates `originalOrderItems` to new state
5. Resets `hasChanges` to `false`

### Current Strengths ✅

1. **Seamless Editing** - Users can add/remove items freely
2. **Change Tracking** - System knows when order has been modified
3. **Deadline Protection** - Cannot edit within 3 days of pickup
4. **State Synchronization** - BasketRepository keeps all ViewModels in sync
5. **Empty Order Prevention** - Cannot save an empty order

### Current Issues & Edge Cases ⚠️

#### 1. **No Visual Indicator During Editing**
**Issue:** When editing an order, users can add/remove items, but there's no clear visual distinction between:
- Items from the original order
- Newly added items
- Items about to be removed

**Impact:** User might accidentally modify order without realizing

#### 2. **No Confirmation on Destructive Actions**
**Issue:** User can clear basket with `ClearBasket` action
- Loses all order data
- No undo
- Especially problematic if they have an active order loaded

**Impact:** Data loss risk

#### 3. **Automatic Editing Mode**
**Issue:** Order is immediately editable upon loading
- No explicit "Edit" action required
- User might change something accidentally

**Impact:** Accidental modifications

#### 4. **No Cancellation Option**
**Issue:** Once user starts editing, only options are:
- Update order (commit changes)
- Close screen (changes persist in basket)

**Missing:** "Discard Changes" / "Reset to Original" option

**Impact:** Cannot undo changes without reloading the app

#### 5. **Adding Items While Order Loaded**
**Issue:** User adds items from main screen while order is loaded
- Items added to basket ✅
- `hasChanges` tracked ✅
- BUT: No indication in UI that they're modifying an existing order

**Impact:** User might not realize they're editing an order vs. creating new one

#### 6. **Multiple Orders Scenario**
**Issue:** What if user wants to:
- View existing order but NOT edit it
- Start a fresh order for a different date

**Current:** Can only have one active basket

**Impact:** Limited flexibility

## Recommended Improvements

### Option A: **Cautious Approach** (Recommended for MVP)
Focus on preventing accidents while maintaining simplicity.

#### Changes:
1. **Add Explicit Edit Mode**
   ```kotlin
   sealed interface BasketMode {
       object ViewOnly    // Cannot modify
       object Editing     // Can modify
       object NewOrder    // Creating new order
   }
   ```

2. **Require Edit Button**
   - Order loads in **ViewOnly** mode
   - Show "Edit Order" button
   - Only after clicking can user modify

3. **Add Discard Changes Action**
   ```kotlin
   BasketAction.DiscardChanges  // Reloads original order items
   ```

4. **Visual Indicators**
   - Show banner: "Editing order #123 for 15.11.2025"
   - Mark modified items with indicator (•)
   - Show "X changes" in button text

5. **Confirmation Dialogs**
   - Confirm before clearing basket if order loaded
   - Confirm before discarding changes

#### Implementation:
```kotlin
// BasketScreenState
data class BasketScreenState(
    // ... existing fields
    val mode: BasketMode = BasketMode.NewOrder,
    val isModified: Boolean = false  // Replaces hasChanges
)

// BasketAction
sealed interface BasketAction {
    // ... existing actions
    data object StartEditing : BasketAction
    data object DiscardChanges : BasketAction
    data object ConfirmClearBasket : BasketAction
}
```

**Files to Modify:**
- `BasketViewModel.kt` - Add mode handling
- `BasketScreen.kt` - Add edit mode UI
- `BasketRepository.kt` - Add `resetToLoadedOrder()` method

---

### Option B: **Flexible Approach** (For Future Enhancement)
Advanced features for power users.

#### Additional Changes:
1. **Multiple Basket Support**
   - Can view order without affecting basket
   - Can have "draft" basket while order is active
   - Switch between baskets

2. **Order History Integration**
   - View past orders
   - Clone order to new basket
   - Compare current vs. original

3. **Optimistic UI Updates**
   - Show changes immediately
   - Sync in background
   - Handle conflicts

4. **Undo/Redo Stack**
   - Track each modification
   - Allow undo last N actions
   - Persist across sessions

---

### Option C: **Immediate Mode** (Current Behavior Enhanced)
Keep current flow but add safety nets.

#### Minimal Changes:
1. **Add Banner When Editing**
   ```
   [i] You are editing order #123 (pickup: 15.11.2025)
   ```

2. **Show Change Summary**
   ```
   "Update Order" button shows:
   - 2 items added
   - 1 item removed
   - 3 quantities changed
   ```

3. **Add "Reset" Button**
   - Shows next to "Update Order"
   - Reloads original order state
   - Enabled only when `hasChanges == true`

4. **Prevent Accidental Clear**
   - Disable "Clear Basket" when order loaded
   - Or show confirmation dialog

---

## Detailed Recommendation: Option A Implementation

### Phase 1: Add Edit Mode (Priority: HIGH)

#### 1.1 Update BasketRepository
```kotlin
interface BasketRepository {
    // ... existing methods

    /**
     * Reset basket to the original loaded order items
     * @return true if reset successful, false if no order loaded
     */
    suspend fun resetToLoadedOrder(): Boolean

    /**
     * Get original order items (before any edits)
     */
    fun getOriginalOrderItems(): List<OrderedProduct>?
}
```

#### 1.2 Update BasketViewModel State
```kotlin
data class BasketScreenState(
    // ... existing fields
    val editMode: EditMode = EditMode.Disabled,
    val showDiscardDialog: Boolean = false,
    val showClearDialog: Boolean = false
)

enum class EditMode {
    Disabled,   // Viewing order, cannot modify
    Enabled     // Can modify order
}

sealed interface BasketAction {
    // ... existing actions
    data object EnableEditing : BasketAction        // Start editing
    data object DiscardChanges : BasketAction       // Reset to original
    data object RequestClearBasket : BasketAction   // Show dialog
    data object ConfirmClearBasket : BasketAction   // Actually clear
    data object CancelClearBasket : BasketAction    // Cancel dialog
}
```

#### 1.3 Update UI (BasketScreen.kt)
```kotlin
// At top of screen when order loaded
if (state.orderId != null && state.editMode == EditMode.Disabled) {
    Card(/* ... */) {
        Row(/* ... */) {
            Text("Viewing Order #${state.orderId.take(8)}")
            Button(onClick = { onAction(BasketAction.EnableEditing) }) {
                Text("Edit Order")
            }
        }
    }
}

// Disable item modification when in ViewOnly mode
BasketItemCard(
    // ...
    enabled = state.editMode == EditMode.Enabled,
    onRemove = if (state.editMode == EditMode.Enabled) {
        { onAction(BasketAction.RemoveItem(item.productId)) }
    } else null
)

// Show Discard button when changes exist
if (state.hasChanges && state.editMode == EditMode.Enabled) {
    OutlinedButton(
        onClick = { onAction(BasketAction.DiscardChanges) }
    ) {
        Text("Discard Changes")
    }
}
```

### Phase 2: Add Visual Indicators (Priority: MEDIUM)

#### 2.1 Track Item State
```kotlin
data class BasketItemState(
    val item: OrderedProduct,
    val status: ItemStatus
)

enum class ItemStatus {
    Original,      // From original order
    Modified,      // Quantity changed
    Added,         // Newly added
    Removed        // Marked for removal
}
```

#### 2.2 Show Status in UI
```kotlin
BasketItemCard(
    // ...
    badge = when (itemState.status) {
        ItemStatus.Added -> "NEW"
        ItemStatus.Modified -> "MODIFIED"
        ItemStatus.Removed -> "REMOVED"
        else -> null
    }
)
```

### Phase 3: Add Confirmations (Priority: MEDIUM)

#### 3.1 Clear Basket Dialog
```kotlin
if (state.showClearDialog) {
    AlertDialog(
        onDismissRequest = { onAction(BasketAction.CancelClearBasket) },
        title = { Text("Clear Basket?") },
        text = {
            if (state.orderId != null) {
                Text("This will discard your current order (#${state.orderId})")
            } else {
                Text("This will remove all items from your basket")
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(BasketAction.ConfirmClearBasket) }) {
                Text("Clear")
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(BasketAction.CancelClearBasket) }) {
                Text("Cancel")
            }
        }
    )
}
```

## Migration Path

### Step 1: Add Safety Features (Week 1)
- ✅ Add `resetToLoadedOrder()` to BasketRepository
- ✅ Add "Discard Changes" action
- ✅ Add confirmation dialog for clear basket
- ✅ Update tests

### Step 2: Add Edit Mode (Week 2)
- ✅ Add `EditMode` enum
- ✅ Update state management
- ✅ Add "Edit Order" button
- ✅ Disable modifications in view mode
- ✅ Update tests

### Step 3: Add Visual Indicators (Week 3)
- ✅ Track item status (added/modified/removed)
- ✅ Show badges on items
- ✅ Show change summary
- ✅ Update tests

### Step 4: Polish & UX (Week 4)
- ✅ Add animations
- ✅ Improve error messages
- ✅ User testing
- ✅ Documentation

## Testing Scenarios

### Scenario 1: Edit Existing Order
1. App starts with open order → Order loads
2. User navigates to basket → Sees "Edit Order" button
3. User clicks "Edit Order" → Items become editable
4. User adds item from main screen → Item added, badge shows "1 change"
5. User clicks "Update Order" → Order saved, returns to view mode

### Scenario 2: Discard Changes
1. User edits order → Makes changes
2. User clicks "Discard Changes" → Confirmation dialog
3. User confirms → Basket resets to original state
4. "Update Order" button disabled (no changes)

### Scenario 3: Accidental Clear Prevention
1. User has order loaded
2. User tries to clear basket → Confirmation dialog
3. Dialog explains this will discard order
4. User cancels → Basket unchanged

### Scenario 4: Add Items While Not Editing
1. Order loaded in view mode
2. User browses main screen → Adds item
3. System detects order loaded → Shows dialog:
   "You have an order loaded. Do you want to:
   - Add to existing order
   - Start new order"

## Code Locations Reference

| Feature | File | Lines |
|---------|------|-------|
| Order loading | `UnifiedAppViewModel.kt` | 95-153 |
| Change detection | `BasketViewModel.kt` | 135-194 |
| Add/Remove items | `BasketViewModel.kt` | 209-231 |
| Update order | `BasketViewModel.kt` | 443-539 |
| Basket UI | `BasketScreen.kt` | 55-250 |
| BasketRepository | `InMemoryBasketRepository.kt` | 13-99 |

## Conclusion

**Recommended Approach: Option A (Cautious)**

This balances:
- ✅ User safety (prevents accidents)
- ✅ Clear intent (explicit edit mode)
- ✅ Flexibility (can still modify freely when editing)
- ✅ Implementation effort (moderate changes)
- ✅ User experience (intuitive)

**Priority Order:**
1. **HIGH**: Add confirmation dialogs (Quick win, prevents data loss)
2. **HIGH**: Add "Discard Changes" action (User control)
3. **MEDIUM**: Implement edit mode (Better UX)
4. **LOW**: Add visual indicators (Polish)

**Estimated Effort:**
- Phase 1: 2-3 days
- Phase 2: 1-2 days
- Phase 3: 1 day
- **Total: 4-6 days**

## Date
2025-11-12
