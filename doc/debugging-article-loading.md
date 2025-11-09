# Debugging Article Loading Flow

## Overview
Comprehensive logging has been added throughout the authentication and article loading flow to diagnose why articles are not loading.

## Log Symbols
- 🔐 Firebase Authentication events
- 🔥 Firebase Article Repository events
- 📦 UnifiedAppViewModel product loading events
- 🎬 MainScreenViewModel article loading events
- ❌ Errors
- ⚠️ Warnings

## Expected Log Flow

### 1. App Startup - Authentication
```
App Init: Waiting for authentication to complete...
🔐 FirebaseAuthRepository.observeAuthState: Setting up auth state listener
🔐 FirebaseAuthRepository.observeAuthState: Auth state listener added
App Startup: Checking authentication...
```

### 2. Guest Login (if no persisted auth)
```
App Startup: No persisted auth, signing in as guest...
🔐 FirebaseAuthRepository.signInAnonymously: Starting anonymous sign in...
🔐 FirebaseAuthRepository.signInAnonymously: SUCCESS - userId=<uid>, isAnonymous=true
🔐 FirebaseAuthRepository.observeAuthState: Auth state changed - userId=<uid>, isAnonymous=true
App Startup: Guest sign-in successful, user ID: <uid>
```

### 3. Authentication Complete
```
App Init: Authentication complete, user ID: <uid>
```

### 4. UnifiedAppViewModel - Load Products
```
📦 UnifiedAppViewModel.loadProducts: START
📦 UnifiedAppViewModel.loadProducts: Set loading state to true
📦 UnifiedAppViewModel.loadProducts: Calling articleRepository.getArticles(sellerId='')
```

### 5. FirebaseArticleRepository - Setup Listener
```
🔥 FirebaseArticleRepository.observeArticles: START with sellerId=''
🔥 FirebaseArticleRepository.observeArticles: Getting articles for current user
🔥 FirebaseArticleRepository.observeArticles: Database reference obtained: articles/<userId>
🔥 FirebaseArticleRepository.observeArticles: Adding ChildEventListener...
🔥 FirebaseArticleRepository.observeArticles: ChildEventListener added, waiting for events...
```

### 6. MainScreenViewModel - Wait for Auth
```
🎬 MainScreenViewModel.waitForAuthThenLoad: START - Waiting for auth state...
🎬 MainScreenViewModel.waitForAuthThenLoad: User authenticated with ID: <uid>
🎬 MainScreenViewModel.waitForAuthThenLoad: Calling loadArticles()...
```

### 7. MainScreenViewModel - Load Articles
```
🎬 MainScreenViewModel.loadArticles: START
🎬 MainScreenViewModel.loadArticles: Set loading state to true
🎬 MainScreenViewModel.loadArticles: Calling articleRepository.getArticles(sellerId='')
```

### 8. Article Events Received
```
🔥 FirebaseArticleRepository: onChildAdded - key=<articleId>
🔥 FirebaseArticleRepository: Sending ADDED article '<productName>' (id=<articleId>)
📦 UnifiedAppViewModel.loadProducts: Received article event - mode=0, id=<articleId>, name=<productName>
📦 UnifiedAppViewModel.loadProducts: ADDED article '<productName>' (id=<articleId>)
📦 UnifiedAppViewModel.loadProducts: Product count: 0 → 1
🎬 MainScreenViewModel.loadArticles: Received article event - mode=0, id=<articleId>, name=<productName>
🎬 MainScreenViewModel.loadArticles: ADDED article '<productName>' (id=<articleId>)
🎬 MainScreenViewModel.loadArticles: Article count: 0 → 1
```

### 9. Initialization Complete
```
App Init: Initialization complete
```

## Common Issues to Look For

### Issue 1: Authentication Never Completes
**Symptoms:**
```
App Init: Waiting for authentication to complete...
(No further logs)
```

**Possible Causes:**
- Firebase auth state never emits a value
- observeAuthState() not triggering
- checkPersistedAuth() hanging

### Issue 2: Database Reference Error
**Symptoms:**
```
❌ FirebaseArticleRepository.observeArticles: ERROR getting database reference - <error>
```

**Possible Causes:**
- User not authenticated when calling Database.articles()
- Firebase not initialized
- Permission denied

### Issue 3: No Articles in Database
**Symptoms:**
```
🔥 FirebaseArticleRepository.observeArticles: ChildEventListener added, waiting for events...
(No onChildAdded events)
```

**Possible Causes:**
- Empty database at path `articles/<userId>`
- Incorrect database path
- Database rules denying read access

### Issue 4: Articles Received but Not Displayed
**Symptoms:**
```
🔥 FirebaseArticleRepository: Sending ADDED article 'Product' (id=123)
📦 UnifiedAppViewModel.loadProducts: Received article event - mode=0, id=123, name=Product
(Articles count increases but UI doesn't update)
```

**Possible Causes:**
- State not being observed in UI
- ViewModel not injected correctly
- Compose recomposition not triggered

### Issue 5: DTO Parsing Error
**Symptoms:**
```
🔥 FirebaseArticleRepository: onChildAdded - key=<articleId>
⚠️ FirebaseArticleRepository: onChildAdded - dto is null for key=<articleId>
```

**Possible Causes:**
- Database structure doesn't match ArticleDto fields
- Missing required fields in Firebase
- Incorrect data types

## How to Use These Logs

1. **Run the app** and monitor logcat with filter:
   ```bash
   adb logcat | grep -E "🔐|🔥|📦|🎬|❌|⚠️|App Init|App Startup"
   ```

2. **Compare actual logs** with expected flow above

3. **Identify where the flow stops** - the last log message will indicate where the problem is

4. **Check for error logs** (❌) - these will show exceptions and error messages

## Files with Logging

1. **UnifiedAppViewModel.kt**
   - `initializeApp()` - Overall initialization flow
   - `checkAuthenticationStatus()` - Auth checking
   - `signInAsGuest()` - Guest login
   - `loadProducts()` - Product loading with article events

2. **MainScreenViewModel.kt**
   - `waitForAuthThenLoad()` - Wait for auth before loading
   - `loadArticles()` - Article loading with events

3. **FirebaseAuthRepository.kt**
   - `observeAuthState()` - Auth state changes
   - `signInAnonymously()` - Guest login

4. **FirebaseArticleRepository.kt**
   - `observeArticles()` - Article stream setup
   - `onChildAdded()` - New articles
   - `onChildChanged()` - Updated articles
   - `onChildRemoved()` - Deleted articles
   - `onCancelled()` - Errors

## Next Steps

After running the app with logging:

1. Paste the actual logs here
2. Compare with expected flow
3. Identify the exact point where it diverges
4. Fix the issue based on the diagnostic information
