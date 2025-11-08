# AuthRepository Implementation Guide

## Overview

The AuthRepository has been successfully implemented with an in-memory implementation for development and testing. This document describes the implementation and how to use it.

## Architecture

### Components Created

1. **AuthRepository Interface** (`domain/repository/AuthRepository.kt`)
   - Defines authentication contract
   - Already existed in the project

2. **InMemoryAuthRepository** (`data/repository/InMemoryAuthRepository.kt`)
   - In-memory implementation for development/testing
   - Stores credentials in memory (not persistent)
   - Includes test users for development

3. **Integration with UnifiedAppViewModel** (`ui/state/UnifiedAppViewModel.kt`)
   - Observes authentication state changes
   - Implements login, logout, and register actions
   - Updates global app state based on auth changes

## Features

### Authentication Methods

- **Sign In**: `signInWithEmail(email: String, password: String)`
- **Sign Up**: `signUpWithEmail(email: String, password: String)`
- **Sign Out**: `signOut()`
- **Delete Account**: `deleteAccount()`
- **Observe Auth State**: `observeAuthState()` - Returns Flow of userId

### Test Users

The InMemoryAuthRepository comes with pre-configured test users:

```kotlin
Email: test@buyer.com
Password: password123
User ID: buyer_001

Email: test@seller.com
Password: password123
User ID: seller_001
```

## Usage in UnifiedAppViewModel

### Dispatching Auth Actions

```kotlin
// Sign in
viewModel.dispatch(UnifiedUserAction.Login(
    email = "test@buyer.com",
    password = "password123"
))

// Sign up
viewModel.dispatch(UnifiedUserAction.Register(
    email = "newuser@example.com",
    password = "securepassword",
    name = "John Doe"
))

// Sign out
viewModel.dispatch(UnifiedUserAction.Logout)
```

### Observing Auth State

The authentication state is automatically observed and updates the global app state:

```kotlin
viewModel.state.collect { state ->
    when (state.common.user) {
        is UserState.Guest -> {
            // User not logged in
        }
        is UserState.LoggedIn -> {
            // User logged in
            val userId = (state.common.user as UserState.LoggedIn).id
            val userName = (state.common.user as UserState.LoggedIn).name
            val userEmail = (state.common.user as UserState.LoggedIn).email
        }
        is UserState.Loading -> {
            // Loading state
        }
    }
}
```

### Auth Screen State

The auth screen has dedicated state for loading and errors:

```kotlin
viewModel.state.collect { state ->
    val authState = state.screens.auth

    if (authState.isLoading) {
        // Show loading indicator
    }

    authState.error?.let { error ->
        // Show error message
        Text(error.message)
    }
}
```

## Dependency Injection

The AuthRepository is provided through Koin in `DomainModule.kt`:

```kotlin
val domainModule = module {
    single<AuthRepository> { InMemoryAuthRepository() }
}
```

And injected into UnifiedAppViewModel in `AppModule.kt`:

```kotlin
viewModel { UnifiedAppViewModel(get(), get(), get(), get()) }
```

## State Flow

### Login Flow

1. User dispatches `UnifiedUserAction.Login`
2. `UnifiedAppViewModel.login()` is called
3. Sets `auth.isLoading = true`
4. Calls `authRepository.signInWithEmail()`
5. On success:
   - Shows success snackbar
   - `observeAuthState()` automatically updates `common.user` to `LoggedIn`
   - Sets `auth.isLoading = false`
6. On failure:
   - Shows error snackbar
   - Sets `auth.error` with error details
   - Sets `auth.isLoading = false`

### Logout Flow

1. User dispatches `UnifiedUserAction.Logout`
2. `UnifiedAppViewModel.logout()` is called
3. Calls `authRepository.signOut()`
4. On success:
   - Clears basket and user-specific data
   - `observeAuthState()` automatically updates `common.user` to `Guest`
   - Shows success snackbar

## Migration to Production

### Firebase Implementation

To replace InMemoryAuthRepository with Firebase:

1. Create `FirebaseAuthRepository` implementing `AuthRepository`
2. Add Firebase dependencies to `build.gradle.kts`
3. Update `DomainModule.kt`:

```kotlin
val domainModule = module {
    single<AuthRepository> { FirebaseAuthRepository() }
}
```

### Backend API Implementation

For custom backend:

1. Create `ApiAuthRepository` implementing `AuthRepository`
2. Use Ktor client or similar for HTTP requests
3. Implement JWT token management
4. Update `DomainModule.kt` to provide new implementation

## Example UI Implementation

### Login Screen

```kotlin
@Composable
fun LoginScreen(viewModel: UnifiedAppViewModel) {
    val state by viewModel.state.collectAsState()
    val authState = state.screens.auth

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column {
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") }
        )

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )

        Button(
            onClick = {
                viewModel.dispatch(UnifiedUserAction.Login(email, password))
            },
            enabled = !authState.isLoading
        ) {
            if (authState.isLoading) {
                CircularProgressIndicator()
            } else {
                Text("Sign In")
            }
        }

        authState.error?.let { error ->
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Quick test login button
        Button(onClick = {
            viewModel.dispatch(UnifiedUserAction.Login(
                "test@buyer.com",
                "password123"
            ))
        }) {
            Text("Test Login")
        }
    }
}
```

## Security Considerations

### Current Implementation (Development)

⚠️ **WARNING**: The InMemoryAuthRepository is for development only:
- Passwords stored in plain text
- No encryption
- Data lost on app restart
- No token management
- No session expiry

### Production Requirements

For production, implement:
- ✅ Password hashing (bcrypt, argon2)
- ✅ JWT token management
- ✅ Secure token storage
- ✅ Session expiry
- ✅ Refresh tokens
- ✅ Rate limiting
- ✅ HTTPS only
- ✅ Input validation
- ✅ CSRF protection

## Testing

### Unit Tests

```kotlin
class AuthRepositoryTest {
    private lateinit var repository: AuthRepository

    @Before
    fun setup() {
        repository = InMemoryAuthRepository()
    }

    @Test
    fun `sign in with valid credentials succeeds`() = runTest {
        val result = repository.signInWithEmail(
            "test@buyer.com",
            "password123"
        )
        assertTrue(result.isSuccess)
        assertEquals("buyer_001", result.getOrNull())
    }

    @Test
    fun `sign in with invalid credentials fails`() = runTest {
        val result = repository.signInWithEmail(
            "test@buyer.com",
            "wrongpassword"
        )
        assertTrue(result.isFailure)
    }
}
```

## Troubleshooting

### Issue: User state not updating after login

**Solution**: Ensure `observeAuthState()` is called in ViewModel init block.

### Issue: Login button stays disabled

**Solution**: Check that `auth.isLoading` is being set to false after login completes.

### Issue: Error messages not showing

**Solution**: Verify that error state is being set and collected in UI.

## Next Steps

1. ✅ Create login/register UI screens
2. ✅ Integrate with profile management
3. ✅ Add password reset functionality
4. ✅ Implement email verification
5. ✅ Add social login (Google, Apple)
6. ✅ Replace InMemoryAuthRepository with production implementation

## Files Modified

- `/shared/src/commonMain/kotlin/com/together/newverse/data/repository/InMemoryAuthRepository.kt` (new)
- `/shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt` (modified)
- `/shared/src/commonMain/kotlin/com/together/newverse/di/DomainModule.kt` (modified)
- `/shared/src/commonMain/kotlin/com/together/newverse/di/AppModule.kt` (modified)
