# Firebase Authentication - Source Files Reference

## Absolute File Paths

### Core Authentication Implementation

#### 1. Authentication Interface
**Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/domain/repository/AuthRepository.kt`
**Lines:** 79
**Purpose:** Defines the authentication contract/interface
**Methods:** 9 core auth methods
**Status:** Active

#### 2. Firebase Implementation (Production)
**Path:** `/home/mandroid/Videos/newverse/shared/src/androidMain/kotlin/com/together/newverse/data/repository/FirebaseAuthRepository.kt`
**Lines:** 428
**Purpose:** Google Firebase auth implementation for Android
**Methods:** All authentication methods (email, Google, Twitter, guest, session mgmt)
**Status:** Active
**Dependencies:** Firebase Auth KTX, Google Play Services Auth

#### 3. In-Memory Implementation (Testing)
**Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/data/repository/InMemoryAuthRepository.kt`
**Lines:** 235
**Purpose:** Mock implementation for development/testing
**Test Users:** buyer_001, seller_001
**Status:** Active

### Database & Extensions

#### 4. Firebase Extensions
**Path:** `/home/mandroid/Videos/newverse/shared/src/androidMain/kotlin/com/together/newverse/data/firebase/FirebaseExtensions.kt`
**Lines:** 259
**Purpose:** Extension functions for Firebase Realtime Database operations
**Key Functions:** observeChildEvents(), observeValue(), observeValueAs(), getListAs(), exists()
**Status:** Active

#### 5. Firebase Database Connection (Backup)
**Path:** `/home/mandroid/Videos/newverse/shared/src/androidMain/kotlin/com/together/newverse/data/firebase/Database.kt.backup`
**Status:** Backed up (not in use)
**Note:** Use if rolling back from GitLive migration

### State Management

#### 6. Unified App ViewModel
**Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt`
**Lines:** ~400 (partial, auth-related sections)
**Key Methods:**
- `init()` - Initialization with auth check
- `observeAuthState()` - Listen to auth changes
- `checkAuthenticationStatus()` - Check persisted session
- `loadOpenOrderAfterAuth()` - Load cart after login
**Status:** Active

### Dependency Injection

#### 7. Android Domain Module
**Path:** `/home/mandroid/Videos/newverse/shared/src/androidMain/kotlin/com/together/newverse/di/AndroidDomainModule.kt`
**Lines:** 34
**Provides:** AuthRepository → FirebaseAuthRepository
**Status:** Active

#### 8. App Module (Common)
**Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/di/AppModule.kt`
**Lines:** 31
**Purpose:** ViewModels and common app configuration
**Status:** Active

### Utility & Helper

#### 9. Google Sign-In Helper (if exists)
**Status:** May be in androidMain/kotlin/.../util/GoogleSignInHelper.kt
**Purpose:** Simplifies Google Sign-In integration
**Note:** Check in /shared/src/androidMain/kotlin/com/together/newverse/util/

### Related Repositories (Using Auth)

#### 10. Article Repository
**Path:** `/home/mandroid/Videos/newverse/shared/src/androidMain/kotlin/com/together/newverse/data/repository/FirebaseArticleRepository.kt`
**Uses:** authRepository for user identification
**Status:** Active

#### 11. Order Repository
**Path:** `/home/mandroid/Videos/newverse/shared/src/androidMain/kotlin/com/together/newverse/data/repository/FirebaseOrderRepository.kt`
**Uses:** authRepository for order queries and ownership
**Status:** Active

#### 12. Profile Repository
**Path:** `/home/mandroid/Videos/newverse/shared/src/androidMain/kotlin/com/together/newverse/data/repository/FirebaseProfileRepository.kt`
**Uses:** authRepository for user profile storage
**Status:** Active

### Build Configuration

#### 13. Shared Library Build Configuration
**Path:** `/home/mandroid/Videos/newverse/shared/build.gradle.kts`
**Firebase Dependencies:**
- Lines 77-80: Firebase Auth, Database, Storage

#### 14. Android App Build Configuration
**Path:** `/home/mandroid/Videos/newverse/androidApp/build.gradle.kts`
**Firebase Setup:**
- Line 6: Google Services plugin
- Lines 106-111: Firebase dependencies (using BOM)

### Firebase Configuration Files

#### 15. Main Google Services Configuration
**Path:** `/home/mandroid/Videos/newverse/androidApp/src/main/google-services.json`
**Purpose:** Main Firebase configuration for app

#### 16. Debug Google Services Configuration
**Path:** `/home/mandroid/Videos/newverse/androidApp/src/debug/google-services.json`
**Purpose:** Debug build Firebase configuration

#### 17. Release Google Services Configuration
**Path:** `/home/mandroid/Videos/newverse/androidApp/src/release/google-services.json`
**Purpose:** Release build Firebase configuration

### Documentation Files

#### 18. Full Implementation Overview
**Path:** `/home/mandroid/Videos/newverse/doc/firebase-auth-implementation-overview.md`
**Lines:** 600+
**Content:** Comprehensive technical documentation, architecture, flows, security
**Status:** NEWLY CREATED

#### 19. Quick Reference Guide
**Path:** `/home/mandroid/Videos/newverse/doc/firebase-auth-quick-reference.md`
**Lines:** 250+
**Content:** Quick lookup tables, code snippets, common issues
**Status:** NEWLY CREATED

#### 20. Auth Repository Implementation Guide
**Path:** `/home/mandroid/Videos/newverse/doc/auth-repository-implementation.md`
**Content:** Implementation guide with examples
**Status:** Existing

#### 21. App Startup Authentication Flow
**Path:** `/home/mandroid/Videos/newverse/doc/app-startup-authentication-flow.md`
**Content:** Startup sequence and initialization
**Status:** Existing

#### 22. Social Authentication
**Path:** `/home/mandroid/Videos/newverse/doc/social-authentication.md`
**Content:** Google and Twitter setup and integration
**Status:** Existing

#### 23. Firebase Migration Guide
**Path:** `/home/mandroid/Videos/newverse/doc/firebase-migration-to-gitlive.md`
**Content:** Migration to GitLive SDK guide
**Status:** Existing

#### 24. Firebase Migration Summary
**Path:** `/home/mandroid/Videos/newverse/doc/firebase-gitlive-migration-summary.md`
**Content:** Current migration status
**Status:** Existing

#### 25. Firebase Migration Current Status
**Path:** `/home/mandroid/Videos/newverse/doc/firebase-migration-current-status.md`
**Content:** Issues, rollback steps, recommendations
**Status:** Existing

## File Organization Summary

### By Purpose

**Authentication Core (3 files):**
- AuthRepository.kt (interface)
- FirebaseAuthRepository.kt (implementation)
- InMemoryAuthRepository.kt (testing)

**Database & Extensions (2 files):**
- FirebaseExtensions.kt (utilities)
- Database.kt.backup (archived)

**State Management (1 file):**
- UnifiedAppViewModel.kt (ViewModel)

**Dependency Injection (2 files):**
- AndroidDomainModule.kt
- AppModule.kt

**Integration (3 files):**
- FirebaseArticleRepository.kt
- FirebaseOrderRepository.kt
- FirebaseProfileRepository.kt

**Configuration (4 files):**
- shared/build.gradle.kts
- androidApp/build.gradle.kts
- google-services.json (3 variants)

**Documentation (8 files):**
- firebase-auth-implementation-overview.md (NEW)
- firebase-auth-quick-reference.md (NEW)
- 6 existing auth-related docs

### By Modification Status

**Active (In Use):**
- AuthRepository.kt
- FirebaseAuthRepository.kt
- InMemoryAuthRepository.kt
- FirebaseExtensions.kt
- UnifiedAppViewModel.kt
- AndroidDomainModule.kt
- AppModule.kt
- All build.gradle.kts files
- All google-services.json files
- Related repositories

**Archived (Backup):**
- Database.kt.backup
- FirebaseExtensions.kt.backup
- ArticleDto.kt.backup
- OrderDto.kt.backup
- FirebaseAuthRepository.kt.backup (older version)

## Quick Navigation

### I Need To:

**Understand authentication**: Start with `firebase-auth-implementation-overview.md`

**Find a specific method**: Check `firebase-auth-quick-reference.md` for method signatures

**Debug an issue**: Look in FirebaseAuthRepository.kt for error handling

**Set up testing**: Use InMemoryAuthRepository.kt with test users

**Change DI configuration**: Edit AndroidDomainModule.kt

**Add new auth method**: Modify FirebaseAuthRepository.kt (add to interface first)

**Implement iOS support**: Plan GitLive migration using firebase-migration-to-gitlive.md

**Check startup flow**: Read app-startup-authentication-flow.md

**Set up social login**: Follow social-authentication.md

## File Statistics

| File | Lines | Type | Status |
|------|-------|------|--------|
| FirebaseAuthRepository.kt | 428 | Implementation | Active |
| UnifiedAppViewModel.kt | ~400 | State Mgmt | Active |
| FirebaseExtensions.kt | 259 | Utilities | Active |
| InMemoryAuthRepository.kt | 235 | Testing | Active |
| AndroidDomainModule.kt | 34 | Config | Active |
| AppModule.kt | 31 | Config | Active |
| AuthRepository.kt | 79 | Interface | Active |
| **Total** | **~1,466** | **Combined** | **Active** |

## Backup Files Location

Path: `/home/mandroid/Videos/newverse/shared/src/androidMain/kotlin/com/together/newverse/data/`

```
firebase/
  ├── Database.kt.backup
  ├── FirebaseExtensions.kt.backup
  └── model/
      ├── ArticleDto.kt.backup
      └── OrderDto.kt.backup
repository/
  └── FirebaseAuthRepository.kt.backup
```

## Recommended Reading Order

1. **Quick Overview**: firebase-auth-quick-reference.md (5 min)
2. **Full Details**: firebase-auth-implementation-overview.md (20 min)
3. **Implementation Details**: AuthRepository.kt + FirebaseAuthRepository.kt (15 min)
4. **Integration**: app-startup-authentication-flow.md (10 min)
5. **Advanced**: firebase-migration-to-gitlive.md (15 min)

