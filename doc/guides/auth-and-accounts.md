# Authentication and Accounts

**Status:** current — verified against `shared/src` on 2026-10-20
**Scope:** `commonMain` (`AuthFlowCoordinator`, `GitLiveAuthRepository`, `LoginScreen`),
`buyMain` (`BuyAppViewModelAuth`), `sellMain` (`SellAppViewModel`)
**Supersedes:** `archive/app-startup-authentication-flow.md`,
`archive/forced-login-implementation.md`

How a build decides who the user is at startup, what a guest account is and is not,
and what the buy flavor can do to an account once it exists.

---

## There is no automatic guest login

This is the single most important correction to the older docs. The buy flavor used to
sign the user in anonymously whenever no session was found. **It no longer does.**

A buyer with no session lands on `LoginScreen`, which offers email/password,
Google, Apple and — as the last button on the screen — **"Continue as Guest"**
(`LoginScreen.kt:383`). An anonymous Firebase user is created *only* when that button
is tapped, which dispatches `BuyUserAction.ContinueAsGuest` →
`BuyAppViewModel.continueAsGuest()` (`BuyAppViewModelAuth.kt:946`), which calls
`authRepository.signInAnonymously()` under a 15-second timeout.

Nothing in `BuyAppViewModelInitialization.kt` signs anyone in. It only *observes*
auth state. There is no `BuildFlavor.allowsGuestAccess`, no `signInAsGuest()` on
startup, and no `BuildFlavor.kt` at all any more — the older docs describe a
`UnifiedAppViewModel` design that no longer exists in the tree.

## Startup flow

```
App launch
  → AppScaffold (per flavor: buyMain/ or sellMain/ ui/navigation/AppScaffold.kt)
  → BaseAppViewModel.initializeAuthCoordinator()
  → AuthFlowCoordinator.initialize(scope)
        1. authRepository.checkPersistedAuth()      — fast path, reads currentUser
        2. authRepository.observeAuthState()        — definitive, ongoing

AuthState.Initializing     → SplashScreen
AuthState.Authenticated    → resumeInitializationAfterAuth(userInfo) → main UI
AuthState.NotAuthenticated → LoginScreen (buy) / ForcedLoginScreen (sell)
```

`AuthFlowCoordinator` holds one extra subtlety worth knowing before you debug a
"logged out on launch" report: the **first** `null` emission during startup is not
trusted. Firebase Auth may still be restoring the persisted session, so the
coordinator waits 3 seconds once, re-reads `getCurrentUserId()`, and only then
settles on `NotAuthenticated` (`AuthFlowCoordinator.kt`, the `!initialCheckDone`
branch). It deliberately does not poll, because polling interferes with Firebase's
own restore.

Related: a restore-at-install used to wipe the session entirely and looked like a
crash. That is fixed with `allowBackup=false` plus extraction rules — see
`plans/pre-release-checklist.md`.

## AuthState

`AuthState` (in `ui/state/core/AuthFlowCoordinator.kt`) is the source of truth:

| State | Meaning |
|-------|---------|
| `Initializing` | Persisted session is still being determined. Show the splash. |
| `NotAuthenticated` | No user. Buy → login screen; sell → forced login. |
| `Authenticated` | `userId`, `email`, `displayName`, `photoUrl`, `isAnonymous` |

`isAnonymous` is carried on `Authenticated` and propagated into every
`AuthAwareState.Authenticated` the coordinator produces, so any screen can tell a
guest from a registered buyer without asking the repository again.

## Per-flavor behaviour

| | Buy | Sell |
|---|---|---|
| No session | `LoginScreen`, including a "Continue as Guest" button | `ForcedLoginScreen`, no guest path |
| `requiresLogin` | always `false` (`BuyAppViewModelInitialization.kt:135`) | `true` while unauthenticated |
| `ContinueAsGuest` action | creates an anonymous user | accepted but a no-op (`SellAppViewModel.kt:220`) |

The sell flavor still *declares* `SellUserAction.ContinueAsGuest` because it shares
`LoginScreen` with buy; the handler is an explicit empty branch.

## Guest accounts in the buy flavor

A guest is a real anonymous Firebase user with a uid, a profile node and a basket.
Three things can happen to it:

**Upgrade (linking).** `linkWithEmailPassword` / `linkWithGoogle`
(`BuyAppViewModelAuth.kt:457`, `:485`) keep the uid and therefore keep favourites,
basket and profile. The profile is read *before* linking so its anonymous-state
values can be carried across, and per-user storage is migrated via
`BuyerSellerConfig.migrateAnonymousUser(previousUserId, userId)`. A
`SellerEventType` record is written so the seller learns the buyer upgraded.

**Guest logout.** `confirmGuestLogout()` (`:378`) is a *destructive* logout: profile,
basket and favourites are deleted, a `GUEST_DATA_DELETED` seller event is written, and
the resulting state is `NotAuthenticated` rather than `Guest` — deliberately, because
the auth account is gone and offering "continue as guest" on it again would be wrong.

**Account deletion.** `confirmDeleteAccount()` (`:656`) cancels future orders, leaves
past orders alone for the seller's records, deletes the profile and clears per-user
storage, then deletes the auth account. The business rules behind which orders are
cancelled and which are preserved are in `archive/account-clearing-plan.md` (the plan
is implemented; the doc is kept for the reasoning).

## Providers

Email/password, Google and Apple, all through the GitLive Firebase SDK so one
`commonMain` repository serves both platforms. `FeatureFlags.authProvider`
(`FIREBASE`, `GITLIVE`, `AUTO`) selects the implementation. Google Sign-In setup,
including the Web Client ID and SHA-1 fingerprint, is in
`guides/google-signin-guide.md`.

`refreshAuthProvider()` (`:917`) re-resolves the provider after linking, falling back
to `AuthProvider.ANONYMOUS` when no provider is linked.

## Debugging

```bash
adb logcat -s AuthFlowCoord -s GitLiveAuthRepository -s BuyAppViewModel
```

`AuthFlowCoordinator` logs every state transition at debug level, including which of
the two paths (persisted fast path vs. `observeAuthState`) produced it. PII in `Log.d`
is fine here — debug logging is stripped from release builds. Keep it out of
`Log.w`/`Log.e`.
