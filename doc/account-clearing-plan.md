# Account Clearing Implementation Plan

## Overview

This document outlines the plan to implement proper account clearing/deletion in the newverse project, based on the approach used in the sibling project "universe".

## Comparison: Universe vs Newverse

| Aspect | Universe | Newverse (Current) |
|--------|----------|-------------------|
| Async Pattern | RxJava (Single/Observable) | Coroutines (StateFlow) |
| Deletion Strategy | Conditional: orders first, then profile | Profile only, no order cleanup |
| Progress Tracking | `CleanUpResult` data class | None |
| Dialog UI | Menu popup | Dialog state exists, but **UI not rendered** |
| Auth Deletion | Chained after data cleanup | Attempted but may fail (credential too old) |
| Connection Check | Wrapped in connection verification | None |

## Business Rules for Order Handling

**Key Requirement:** When a user deletes their account:
- **Future orders** (pickup date in the future) → **CANCEL** (set status to `CANCELLED`)
- **Past orders** (pickup date has passed) → **KEEP** (preserve for seller records)

This ensures:
1. Sellers still have historical order data for accounting/records
2. Future orders won't be fulfilled for a deleted account
3. No orphaned "pending" orders remain active

## Current Issues in Newverse

### Issue 1: Missing Delete Account Dialog
- State field exists: `showDeleteAccountDialog: Boolean`
- Actions defined: `ShowDeleteAccountDialog`, `DismissDeleteAccountDialog`, `ConfirmDeleteAccount`
- **Problem:** No dialog Composable is rendered in `CustomerProfileScreenModern.kt`

### Issue 2: No Order Cleanup
- Universe deletes all orders before profile
- **New approach:** Cancel future orders only, keep historical orders
- Newverse currently deletes profile only, leaving orphaned active orders

### Issue 3: Firebase Auth Requires Recent Authentication
- Firebase requires recent login (within ~5 minutes) to delete account
- Error: `CREDENTIAL_TOO_OLD_LOGIN_REQUIRED`
- Needs re-authentication flow before deletion

### Issue 4: No Progress Tracking
- Universe uses `CleanUpResult` to track what was deleted
- Newverse has no visibility into deletion progress

## Implementation Plan

### Step 1: Create CleanUpResult Model

**File:** `shared/src/commonMain/kotlin/com/together/newverse/domain/model/CleanUpResult.kt`

```kotlin
data class CleanUpResult(
    val started: Boolean = false,
    val futureOrderIds: List<String> = emptyList(),    // Orders with future pickup dates
    val cancelledOrders: List<String> = emptyList(),   // Successfully cancelled orders
    val skippedOrders: List<String> = emptyList(),     // Past orders (kept for history)
    val profileDeleted: Boolean = false,
    val authDeleted: Boolean = false,
    val errors: List<String> = emptyList()
)
```

### Step 2: Add Order Cancellation to Repository

**File:** `shared/src/commonMain/kotlin/com/together/newverse/domain/repository/ProfileRepository.kt`

Add interface method:
```kotlin
/**
 * Clear user data when account is deleted.
 * - Future orders (pickup date > now) are CANCELLED
 * - Past orders are kept for seller records
 * - Buyer profile is deleted
 */
suspend fun clearUserData(sellerId: String, buyerProfile: BuyerProfile): Result<CleanUpResult>
```

**File:** `shared/src/commonMain/kotlin/com/together/newverse/data/repository/GitLiveProfileRepository.kt`

Implement with this logic:
1. Load all orders from `buyerProfile.placedOrderIds`
2. For each order:
   - If `pickUpDate > now` → Set status to `CANCELLED`
   - If `pickUpDate <= now` → Skip (keep for history)
3. Delete buyer profile
4. Return `CleanUpResult` with counts

```kotlin
override suspend fun clearUserData(sellerId: String, buyerProfile: BuyerProfile): Result<CleanUpResult> {
    return try {
        var result = CleanUpResult(started = true)
        val now = Clock.System.now().toEpochMilliseconds()

        if (buyerProfile.placedOrderIds.isEmpty()) {
            // No orders, just delete profile
            deleteBuyerProfile(buyerProfile.id)
            return Result.success(result.copy(profileDeleted = true))
        }

        val futureOrders = mutableListOf<String>()
        val cancelledOrders = mutableListOf<String>()
        val skippedOrders = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // Process each order
        for ((orderId, orderPath) in buyerProfile.placedOrderIds) {
            try {
                // Load order to check pickup date
                val orderRef = sellersRef.child(sellerId)
                    .child("orders").child(orderId).child(orderPath)
                val snapshot = orderRef.valueEvents.first()
                val order = mapSnapshotToOrder(snapshot)

                if (order != null) {
                    if (order.pickUpDate > now) {
                        // Future order → Cancel it
                        futureOrders.add(orderPath)
                        val cancelledOrder = order.copy(status = OrderStatus.CANCELLED)
                        orderRef.setValue(orderToMap(cancelledOrder))
                        cancelledOrders.add(orderPath)
                    } else {
                        // Past order → Keep for seller records
                        skippedOrders.add(orderPath)
                    }
                }
            } catch (e: Exception) {
                errors.add("Failed to process order $orderPath: ${e.message}")
            }
        }

        // Delete buyer profile
        deleteBuyerProfile(buyerProfile.id)

        Result.success(result.copy(
            futureOrderIds = futureOrders,
            cancelledOrders = cancelledOrders,
            skippedOrders = skippedOrders,
            profileDeleted = true,
            errors = errors
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### Step 3: Add Re-authentication Support

**File:** `shared/src/commonMain/kotlin/com/together/newverse/domain/repository/AuthRepository.kt`

Add methods:
```kotlin
suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit>
suspend fun reauthenticateWithEmail(email: String, password: String): Result<Unit>
suspend fun requiresReauthentication(): Boolean
```

**File:** `shared/src/commonMain/kotlin/com/together/newverse/data/repository/GitLiveAuthRepository.kt`

Implement re-authentication using Firebase's `reauthenticate()` method.

### Step 4: Create Delete Account Dialog

**File:** `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/components/DeleteAccountDialog.kt`

```kotlin
@Composable
fun DeleteAccountDialog(
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_account_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.delete_account_warning))
                if (isLoading) {
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isLoading
            ) {
                Text(
                    stringResource(Res.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}
```

### Step 5: Add Dialog to CustomerProfileScreenModern

**File:** `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/CustomerProfileScreenModern.kt`

Add after existing dialogs (around line 166):
```kotlin
// Delete Account Dialog
if (state.showDeleteAccountDialog) {
    DeleteAccountDialog(
        isLoading = state.isLoading,
        onConfirm = { onAction(UnifiedAccountAction.ConfirmDeleteAccount) },
        onDismiss = { onAction(UnifiedAccountAction.DismissDeleteAccountDialog) }
    )
}
```

### Step 6: Refactor ViewModel Delete Flow

**File:** `shared/src/commonMain/kotlin/com/together/newverse/ui/state/BuyAppViewModel.kt`

Refactor `confirmDeleteAccount()`:

```kotlin
private fun confirmDeleteAccount() {
    viewModelScope.launch {
        try {
            setLoading(true)

            val userId = getCurrentUserId() ?: return@launch
            val profile = profileRepository.getBuyerProfile(userId).getOrNull()

            if (profile == null) {
                // No profile, just delete auth
                deleteAuthAccount()
                return@launch
            }

            // Step 1: Clear user data (cancel future orders + delete profile)
            val sellerId = _state.value.common.currentSellerId
            val cleanUpResult = profileRepository.clearUserData(sellerId, profile)

            cleanUpResult.onSuccess { result ->
                println("Cleanup: cancelled=${result.cancelledOrders.size}, " +
                        "kept=${result.skippedOrders.size}, profile=${result.profileDeleted}")
                if (result.errors.isNotEmpty()) {
                    println("Cleanup errors: ${result.errors}")
                }
            }

            // Step 2: Clear local basket
            basketRepository.clearBasket()

            // Step 3: Delete Firebase Auth (may require re-auth)
            deleteAuthAccount()

            // Step 4: Reset state
            resetToGuestState()

            // Show success message with order info
            val cancelledCount = cleanUpResult.getOrNull()?.cancelledOrders?.size ?: 0
            val message = if (cancelledCount > 0) {
                "Konto gelöscht. $cancelledCount Bestellung(en) storniert."
            } else {
                "Konto gelöscht."
            }
            showSnackbar(message, SnackbarType.INFO)

        } catch (e: Exception) {
            handleDeleteError(e)
        } finally {
            setLoading(false)
        }
    }
}

private suspend fun deleteAuthAccount() {
    val result = authRepository.deleteAccount()
    result.onFailure { e ->
        if (e.message?.contains("CREDENTIAL_TOO_OLD") == true) {
            // Need re-authentication - show re-auth dialog
            _state.update { it.copy(requiresReauthForDeletion = true) }
        } else {
            throw e
        }
    }
}
```

### Step 7: Add Re-authentication UI Flow

Add state for re-authentication:
```kotlin
data class CustomerProfileScreenState(
    // ... existing fields
    val requiresReauthForDeletion: Boolean = false
)
```

Add dialog for re-authentication prompting user to sign in again.

### Step 8: Add String Resources

**File:** `shared/src/commonMain/composeResources/values/strings.xml`

```xml
<string name="delete_account_title">Konto löschen</string>
<string name="delete_account_warning">Ihr Profil wird gelöscht. Offene Bestellungen werden storniert. Vergangene Bestellungen bleiben für den Verkäufer sichtbar.</string>
<string name="delete_account_reauth_required">Bitte melden Sie sich erneut an, um Ihr Konto zu löschen.</string>
<string name="account_deleted_success">Konto gelöscht</string>
<string name="account_deleted_with_orders">Konto gelöscht. %d Bestellung(en) storniert.</string>
```

**File:** `shared/src/commonMain/composeResources/values-en/strings.xml`

```xml
<string name="delete_account_title">Delete Account</string>
<string name="delete_account_warning">Your profile will be deleted. Pending orders will be cancelled. Past orders will remain visible to the seller.</string>
<string name="delete_account_reauth_required">Please sign in again to delete your account.</string>
<string name="account_deleted_success">Account deleted</string>
<string name="account_deleted_with_orders">Account deleted. %d order(s) cancelled.</string>
```

### Step 9: Remove Debug Code

Remove from `LoginStatusCard.kt`:
- `onTestDeleteAuth` parameter
- "Test" button in GuestStatus

Remove from `UnifiedAppActions.kt`:
- `TestDeleteAuth` action

Remove from `BuyAppViewModel.kt`:
- `testDeleteAuth()` function

## Implementation Order

1. **Create CleanUpResult model** - Foundation for tracking progress
2. **Add clearUserData to repository** - Cancel future orders, keep past orders
3. **Create DeleteAccountDialog** - UI component with clear warning text
4. **Add dialog to CustomerProfileScreen** - Wire up UI
5. **Refactor ViewModel deletion flow** - Use new clearUserData method
6. **Add re-authentication support** - Handle Firebase credential requirement
7. **Add string resources** - Localization (German/English)
8. **Remove debug code** - Clean up TestDeleteAuth artifacts

## Testing Checklist

- [ ] Guest user can delete account (anonymous auth + profile)
- [ ] Authenticated user can delete account (requires re-auth if session old)
- [ ] Future orders (pickup date in future) are cancelled
- [ ] Past orders (pickup date passed) are NOT modified
- [ ] Seller can still see cancelled orders in their order list
- [ ] Seller can still see past orders from deleted buyer
- [ ] Local basket is cleared
- [ ] State resets to guest after deletion
- [ ] Google sign-out is triggered
- [ ] Error handling shows appropriate messages
- [ ] Loading state is shown during deletion
- [ ] Cancel button dismisses dialog without action
- [ ] Snackbar shows correct count of cancelled orders

## Firebase Paths Affected

```
/buyer_profile/{userId}              ← Profile DELETED
/orders/{sellerId}/{date}/{orderId}  ← Future orders: status → CANCELLED
                                     ← Past orders: UNCHANGED
Firebase Auth user record            ← Auth account DELETED
```

## Order Status Flow

```
Future Order (pickUpDate > now):
  DRAFT/PLACED/LOCKED → CANCELLED

Past Order (pickUpDate <= now):
  Any status → UNCHANGED (preserved for seller records)
```

## Risk Considerations

1. **Partial Cancellation:** If cancellation fails mid-process, some orders may not be cancelled. Error list in CleanUpResult tracks these failures.

2. **Re-authentication UX:** Users may not understand why they need to sign in again. Clear messaging required.

3. **Network Failures:** Wrap operations in connection check like universe does.

4. **Seller Visibility:** Sellers will see cancelled orders with deleted buyer info. Consider anonymizing buyer data in order records if privacy is a concern.
