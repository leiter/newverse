# Initialization Flow - Remaining Fixes

## Status: IN PROGRESS

We're refactoring the initialization flow to be sequential:
1. Check Auth ✅
2. Load Profile (if signed in) ✅
3. Load Order (if signed in) ✅
4. Load Articles ✅

## Completed
- ✅ Added `InitializationStep` sealed class to UnifiedAppState
- ✅ Updated SplashScreen to use InitializationStep
- ✅ Rewrote `initializeApp()` with sequential flow
- ✅ Added `loadUserProfile()` function
- ✅ Added `loadCurrentOrder()` function

## Compilation Errors to Fix

### 1. Fix when expression in initializeApp() (line 348)
```kotlin
// Current (BROKEN):
when (userState) {
    is UserState.LoggedIn -> { ... }
    is UserState.Guest -> { ... }
    UserState.NotLoggedIn -> { ... } // ERROR: NotLoggedIn doesn't exist
}

// FIX: Add Loading case and remove NotLoggedIn
when (userState) {
    is UserState.LoggedIn -> { /* load profile & order */ }
    is UserState.Guest -> { /* skip user data */ }
    is UserState.Loading -> { /* wait or skip */ }
}
```

### 2. Fix checkAuthenticationStatus() - wrong InitializationStep type
Multiple lines (422, 436, 446, 459, 483, 493) are passing String instead of InitializationStep.

```kotlin
// BROKEN:
initializationStep = "Checking authentication..."

// FIX:
initializationStep = InitializationStep.CheckingAuth
```

### 3. Fix loadUserProfile() - API mismatch
```kotlin
// BROKEN (line 1059, 1063, 1066):
val userId = ... .userId  // userId doesn't exist on LoggedIn
profileRepository.getBuyerProfile(userId)  // takes no parameters
profile.name  // BuyerProfile doesn't have 'name'

// FIX:
val userId = (_state.value.common.user as? UserState.LoggedIn)?.id ?: return
val result = profileRepository.getBuyerProfile()  // No userId parameter
println("✅ Profile loaded: ${profile.firstName} ${profile.lastName}")
```

### 4. Fix loadCurrentOrder() - API doesn't exist
```kotlin
// BROKEN (line 1105, 1109):
val userId = ... .userId  // should be .id
orderRepository.getOrdersByUserId(userId)  // method doesn't exist

// FIX:
val userId = (_state.value.common.user as? UserState.LoggedIn)?.id ?: return
// Need to check OrderRepository interface for correct method
// Might need to use observeOrders() and filter, or add new method
```

## Next Steps

1. Fix the when expression to handle all UserState cases
2. Update checkAuthenticationStatus() to use InitializationStep enum
3. Fix loadUserProfile() to match actual API
4. Fix loadCurrentOrder() - check OrderRepository for correct method
5. Build and test
6. Update documentation

## Architecture Benefits (When Complete)

✅ Sequential loading (Profile → Order → Articles)
✅ User sees progress through splash screen
✅ State-dependent initialization
✅ Better error handling
✅ Foundation for multiplatform (Phase 2)
