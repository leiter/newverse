# GitLive Firebase Migration Guide

## Overview

This guide documents the incremental migration from Google Firebase SDK to GitLive Firebase SDK for the Newverse application. The migration is designed to be safe, reversible, and testable at each stage.

## Migration Status

**Current Phase**: Infrastructure Ready
- ✅ GitLiveAuthRepository implementation created
- ✅ GitLive SDK dependencies added
- ✅ Feature flag system implemented
- ✅ AuthRepositoryFactory for runtime switching
- ✅ Dependency injection updated
- ⏳ GitLive SDK initialization pending
- ⏳ Testing and validation pending

## Architecture Overview

### Repository Pattern
The application uses the Repository pattern, making it easy to swap implementations:

```
AuthRepository (interface)
    ├── FirebaseAuthRepository (Android only - existing)
    ├── GitLiveAuthRepository (cross-platform - new)
    └── InMemoryAuthRepository (testing)
```

### Feature Flag System
Location: `/shared/src/commonMain/kotlin/com/together/newverse/data/config/FeatureFlags.kt`

The feature flag system allows:
- Runtime switching between auth providers
- A/B testing with percentage rollout
- Platform-specific configuration
- Test user allowlisting

### Factory Pattern
Location: `/shared/src/commonMain/kotlin/com/together/newverse/data/repository/AuthRepositoryFactory.kt`

The factory automatically selects the appropriate implementation based on:
- Feature flag settings
- Platform (Android/iOS)
- User ID (for gradual rollout)
- Test user lists

## Configuration Options

### 1. Production Mode (Default - Safe)
```kotlin
// In AndroidDomainModule.kt
FeatureFlagConfig.configureForProduction()
```
- Uses Firebase exclusively
- No debug logging
- No parallel testing

### 2. Development Mode
```kotlin
FeatureFlagConfig.configureForDevelopment()
```
- 10% of users get GitLive
- Debug logging enabled
- Parallel testing enabled

### 3. GitLive Testing Mode
```kotlin
FeatureFlagConfig.configureForGitLiveTesting()
```
- All users get GitLive
- Debug logging enabled
- Good for testing iOS builds

### 4. A/B Testing Mode
```kotlin
FeatureFlagConfig.configureForABTesting(50) // 50% rollout
```
- Custom percentage rollout
- Both implementations tested in parallel
- Results logged for comparison

## How to Test

### Step 1: Enable GitLive for Testing

Edit `/shared/src/androidMain/kotlin/com/together/newverse/di/AndroidDomainModule.kt`:

```kotlin
// Change from:
FeatureFlagConfig.configureForProduction()

// To:
FeatureFlagConfig.configureForGitLiveTesting()
```

### Step 2: Build and Run

```bash
# Clean build
./gradlew clean

# Build for testing (Buy flavor)
./gradlew :androidApp:assembleBuyDebug

# Or build for Sell flavor
./gradlew :androidApp:assembleSellDebug
```

### Step 3: Monitor Logs

Look for these log prefixes:
- `🔐 GitLiveAuthRepository` - GitLive implementation logs
- `🔐 FirebaseAuthRepository` - Firebase implementation logs
- `🏭 AuthRepositoryFactory` - Factory selection logs
- `🔄 ParallelTesting` - Comparison logs (when enabled)

### Step 4: Test Auth Features

Test each authentication method:
1. Email/Password Sign In
2. Email/Password Sign Up
3. Google Sign In
4. Twitter Sign In
5. Anonymous/Guest Mode
6. Sign Out
7. Session Persistence (restart app)

## Gradual Rollout Strategy

### Phase 1: Internal Testing (Current)
```kotlin
FeatureFlagConfig.configureForGitLiveTesting()
```
- Test with internal team
- Validate all auth flows
- Compare with Firebase behavior

### Phase 2: Beta Testing (5% rollout)
```kotlin
FeatureFlagConfig.configureForABTesting(5)
```
- Monitor error rates
- Check performance metrics
- Gather user feedback

### Phase 3: Staged Rollout
```kotlin
// Gradually increase percentage
FeatureFlagConfig.configureForABTesting(10)  // Week 1
FeatureFlagConfig.configureForABTesting(25)  // Week 2
FeatureFlagConfig.configureForABTesting(50)  // Week 3
FeatureFlagConfig.configureForABTesting(100) // Full rollout
```

### Phase 4: Complete Migration
- Remove Firebase dependencies
- Remove FirebaseAuthRepository
- Simplify to GitLive only

## Test User Configuration

Add specific users to test GitLive regardless of rollout percentage:

Edit `/shared/src/commonMain/kotlin/com/together/newverse/data/config/FeatureFlags.kt`:

```kotlin
val gitLiveTestUsers: Set<String> = setOf(
    "test_user_1",
    "test_user_2",
    "your_user_id_here"
)
```

## Platform-Specific Behavior

### Android
- Can use either Firebase or GitLive
- Selection based on feature flags
- Falls back gracefully if GitLive fails

### iOS (Future)
- Will use GitLive exclusively
- Firebase SDK not available for iOS
- Automatic selection by platform

## Troubleshooting

### Issue: GitLive SDK not initializing

**Solution**: The GitLive SDK requires initialization. Add this to your Application class:

```kotlin
// TODO: Add when implementing
GitLiveFirebase.initialize(context)
```

### Issue: Authentication fails with GitLive

**Check**:
1. GitLive SDK version compatibility
2. Firebase project configuration
3. Network connectivity
4. Debug logs for specific errors

### Issue: Need to rollback to Firebase

**Quick Rollback**:
```kotlin
// In AndroidDomainModule.kt
FeatureFlagConfig.configureForProduction()
```

This immediately switches all users back to Firebase.

## Benefits of This Approach

1. **Zero Risk**: Firebase remains default, GitLive is opt-in
2. **Gradual Migration**: Test with small percentages first
3. **Easy Rollback**: One-line configuration change
4. **A/B Testing**: Compare implementations side-by-side
5. **Platform Support**: Enables iOS support (future)
6. **Clean Architecture**: No changes to business logic

## Next Steps

1. **Complete GitLive SDK Setup**
   - Initialize GitLive Firebase
   - Configure authentication providers
   - Set up Firebase rules for GitLive

2. **Testing Phase**
   - Unit tests for GitLiveAuthRepository
   - Integration tests
   - UI tests for auth flows

3. **iOS Implementation**
   - Create iOS module
   - Configure GitLive for iOS
   - Test cross-platform compatibility

4. **Migrate Other Repositories**
   - ArticleRepository
   - OrderRepository
   - ProfileRepository

## Code Locations

- **GitLive Repository**: `/shared/src/commonMain/kotlin/com/together/newverse/data/repository/GitLiveAuthRepository.kt`
- **Feature Flags**: `/shared/src/commonMain/kotlin/com/together/newverse/data/config/FeatureFlags.kt`
- **Factory**: `/shared/src/commonMain/kotlin/com/together/newverse/data/repository/AuthRepositoryFactory.kt`
- **DI Config**: `/shared/src/androidMain/kotlin/com/together/newverse/di/AndroidDomainModule.kt`
- **Build Config**: `/shared/build.gradle.kts` (lines 63-66)

## Monitoring and Metrics

Track these metrics during migration:
- Authentication success rate
- Authentication latency
- Error rates by provider
- User feedback
- App crash rates

## Support

For questions or issues during migration:
1. Check debug logs (enable with `enableAuthDebugLogging = true`)
2. Review this guide
3. Test with InMemoryAuthRepository to isolate issues
4. Use parallel testing to compare behaviors

## Conclusion

This incremental migration approach ensures a safe transition from Firebase to GitLive while maintaining app stability and user experience. The feature flag system provides complete control over the rollout process with instant rollback capability.