# Auto-Select First Article Bug Fix

## Problem

When the app first loads with items in the basket (e.g., strawberries), and strawberries is the first article in the list, the ProductHeroCard incorrectly shows:
- ❌ Quantity: 0
- ❌ "Ändern" button ENABLED (should be disabled)

This happened even though strawberries was actually in the basket with a quantity > 0.

## Root Cause

The bug was in `loadMainScreenArticles()` at line 1610-1620.

### Original Code (BROKEN)

```kotlin
// Auto-select first article if none selected
val selectedArticle = _state.value.screens.mainScreen.selectedArticle
    ?: currentArticles.firstOrNull()  // ❌ Just get first article

_state.update { current ->
    current.copy(
        screens = current.screens.copy(
            mainScreen = current.screens.mainScreen.copy(
                isLoading = false,
                articles = currentArticles,
                selectedArticle = selectedArticle,  // ❌ Directly set in state
                error = null
            )
        )
    )
}
```

### The Problem

When the first article arrives from Firebase:
1. `selectedArticle` is `null` (no article selected yet)
2. Code sets `selectedArticle = currentArticles.firstOrNull()` (e.g., strawberries)
3. **Directly updates state** with `selectedArticle = strawberries`
4. **Does NOT call `selectMainScreenArticle()`**
5. **`selectedQuantity` remains at default value of `0.0`**

This means:
- `selectedArticle = strawberries` ✅
- `selectedQuantity = 0.0` ❌ (should be basket quantity)

When the HeroProductCard renders:
- `quantity = 0.0` (from state)
- `originalQuantity = 3.0` (from basket)
- `hasChanges = true` (0.0 ≠ 3.0) ❌ WRONG!
- Button ENABLED ❌ WRONG!

## The Fix

### New Code (FIXED)

```kotlin
// Update articles list first
_state.update { current ->
    current.copy(
        screens = current.screens.copy(
            mainScreen = current.screens.mainScreen.copy(
                isLoading = false,
                articles = currentArticles,
                error = null
            )
        )
    )
}

// Auto-select first article if none selected (using proper selection logic)
val currentSelectedArticle = _state.value.screens.mainScreen.selectedArticle
if (currentSelectedArticle == null && currentArticles.isNotEmpty()) {
    val firstArticle = currentArticles.first()
    println("🎬 UnifiedAppViewModel.loadMainScreenArticles: Auto-selecting first article: ${firstArticle.productName}")
    selectMainScreenArticle(firstArticle)  // ✅ Use proper selection method
}
```

### How It Works Now

1. **Update articles list** in state (without touching selectedArticle)
2. **Check if no article selected** and list is not empty
3. **Call `selectMainScreenArticle(firstArticle)`** instead of direct assignment
4. `selectMainScreenArticle()` properly:
   - Checks basket for existing item
   - Sets `selectedQuantity = basketItem?.amountCount ?: 0.0`
   - Updates both `selectedArticle` AND `selectedQuantity` ✅

Now when HeroProductCard renders:
- `quantity = 3.0` ✅ (from basket via selectMainScreenArticle)
- `originalQuantity = 3.0` ✅ (from basket)
- `hasChanges = false` ✅ (3.0 == 3.0)
- Button DISABLED ✅ CORRECT!

## Flow Comparison

### Before (BROKEN)

```
App Init
  └─> loadMainScreenArticles()
      └─> First article arrives (Strawberries)
          ├─> selectedArticle = null
          ├─> selectedArticle = currentArticles.firstOrNull()  ❌
          └─> _state.update {
                  selectedArticle = strawberries,  ❌ Direct assignment
                  selectedQuantity = 0.0  ❌ Not updated!
              }

UI Renders
  ├─> quantity = 0.0  ❌
  ├─> originalQuantity = 3.0 (from basket)
  ├─> hasChanges = true  ❌
  └─> Button ENABLED  ❌
```

### After (FIXED)

```
App Init
  └─> loadMainScreenArticles()
      └─> First article arrives (Strawberries)
          ├─> Update articles list
          └─> if (selectedArticle == null)
                  └─> selectMainScreenArticle(strawberries)  ✅
                      ├─> basketItem = basket.find(strawberries)
                      ├─> initialQuantity = basketItem?.amountCount  (3.0)
                      └─> _state.update {
                              selectedArticle = strawberries,  ✅
                              selectedQuantity = 3.0  ✅ From basket!
                          }

UI Renders
  ├─> quantity = 3.0  ✅
  ├─> originalQuantity = 3.0  ✅
  ├─> hasChanges = false  ✅
  └─> Button DISABLED  ✅
```

## What `selectMainScreenArticle()` Does

```kotlin
private fun selectMainScreenArticle(article: Article) {
    // Check if this product is already in the basket
    val basketItems = basketRepository.observeBasket().value
    val existingItem = basketItems.find { it.productId == article.id }

    // If it exists, pre-populate the quantity with the existing amount
    val initialQuantity = existingItem?.amountCount ?: 0.0  // ✅

    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                mainScreen = current.screens.mainScreen.copy(
                    selectedArticle = article,
                    selectedQuantity = initialQuantity  // ✅ Set from basket
                )
            )
        )
    }

    println("🎯 UnifiedAppViewModel.selectMainScreenArticle: Selected ${article.productName}, existing quantity: $initialQuantity")
}
```

**Benefits:**
1. ✅ Reads current basket state
2. ✅ Finds item if it exists
3. ✅ Sets `selectedQuantity` to basket quantity (or 0 if not in basket)
4. ✅ Logs for debugging

## Testing Scenarios

### Scenario 1: First Article is in Basket

**Setup:**
- Basket: [Strawberries(3kg), Tomatoes(2kg)]
- Article list loads with Strawberries as first item

**Expected Result:**
- ✅ Auto-selects Strawberries
- ✅ Quantity shows: "3"
- ✅ Button shows: "Ändern" (DISABLED)
- ✅ Console log: `🎯 UnifiedAppViewModel.selectMainScreenArticle: Selected Strawberries, existing quantity: 3.0`

### Scenario 2: First Article NOT in Basket

**Setup:**
- Basket: [Tomatoes(2kg)]
- Article list loads with Strawberries as first item (not in basket)

**Expected Result:**
- ✅ Auto-selects Strawberries
- ✅ Quantity shows: "0"
- ✅ Button shows: "In den Korb" (DISABLED)
- ✅ Console log: `🎯 UnifiedAppViewModel.selectMainScreenArticle: Selected Strawberries, existing quantity: 0.0`

### Scenario 3: User Manually Selects Article

**Setup:**
- First article auto-selected
- User clicks on different product (Tomatoes)

**Expected Result:**
- ✅ Selects Tomatoes
- ✅ Calls `selectMainScreenArticle(Tomatoes)`
- ✅ Quantity loaded from basket
- ✅ Button state correct

### Scenario 4: Basket Empty on Load

**Setup:**
- Basket: []
- Article list loads with Strawberries as first item

**Expected Result:**
- ✅ Auto-selects Strawberries
- ✅ Quantity shows: "0"
- ✅ Button shows: "In den Korb" (DISABLED)
- ✅ No changes detected

## Code Changes

### File: `UnifiedAppViewModel.kt`

**Location:** Lines 1607-1630

**Change Type:** Logic fix

**Changes:**
1. Removed direct assignment of `selectedArticle` in state update
2. Split state update into two parts:
   - First: Update articles list
   - Second: Call `selectMainScreenArticle()` if needed
3. Added logging for auto-selection

## Benefits

1. ✅ **Consistent Selection Logic** - All article selections go through `selectMainScreenArticle()`
2. ✅ **Proper Quantity Initialization** - Basket quantity always synced on selection
3. ✅ **Correct Button States** - hasChanges calculated correctly
4. ✅ **Better UX** - No confusing enabled button when no changes made
5. ✅ **Debuggable** - Logs show exact flow

## Related Files

- `UnifiedAppViewModel.kt:1390-1410` - `selectMainScreenArticle()` implementation
- `UnifiedAppViewModel.kt:1607-1630` - `loadMainScreenArticles()` auto-selection (FIXED)
- `MainScreenModern.kt:120-134` - UI calculation of originalQuantity
- `MainScreenModern.kt:228` - HeroProductCard hasChanges calculation

## Verification

After this fix, check console logs for:

```
🎬 UnifiedAppViewModel.loadMainScreenArticles: Auto-selecting first article: Strawberries
🎯 UnifiedAppViewModel.selectMainScreenArticle: Selected Strawberries, existing quantity: 3.0
```

This confirms the fix is working correctly.

---

**Issue**: Auto-selected first article shows wrong quantity and button state
**Root Cause**: Direct state assignment bypassed proper selection logic
**Solution**: Use `selectMainScreenArticle()` for all article selections
**Status**: ✅ Fixed
**Version**: 1.1
**Date**: 2025-11-12
