# Changing a Password While Signed In — Implementation Plan

> **IMPLEMENTED** (2026-10-27). An in-app dialog re-authenticates with the
> current password and sets the new one, in both flavors. Built as planned below;
> what changed along the way is noted under "What the build changed".

**Status:** Implemented, not yet verified on a device
**Goal:** A signed-in user with an email/password account can change that
password from inside the app, without going through their inbox.

---

## The gap

A password can only be dealt with *before* sign-in. `sendPasswordResetEmail`
(`AuthRepository.kt:113`, `GitLiveAuthRepository.kt:300`) is wired to the
"Passwort vergessen?" button on `LoginScreen` and `ForcedLoginScreen`, and that
is the whole of it. Once signed in there is no way to reach a password:

- The buyer's account card (`LoginStatusCard.kt:218–232`) offers exactly two
  actions, **Abmelden** and **Konto löschen**.
- The seller's profile offers one, **Abmelden**
  (`SellerProfileScreen.kt:362`).

It is not only UI. `AuthRepository` has sixteen methods and neither
`updatePassword` nor `reauthenticate` is among them, so the domain layer cannot
express the operation either.

## What already exists to build on

- **The SDK has both calls, cross-platform.** GitLive 2.4.0 declares
  `FirebaseUser.updatePassword(password)` and
  `reauthenticate(credential: AuthCredential)` in its `commonMain`, with
  `appleMain` and `androidMain` actuals. Nothing here needs an `expect/actual`
  and nothing needs a new dependency.
- **`EmailAuthProvider.credential(email, password)` is already imported** into
  `GitLiveAuthRepository` for `linkWithEmail` (`:346`) — the same call builds
  the re-auth credential.
- **A three-password dialog already exists to copy.** `EmailLinkingDialog.kt`
  plus `showEmailLinkingDialog` / `emailLinkingPassword` /
  `emailLinkingConfirmPassword` / `emailLinkingError` on
  `CustomerProfileScreenState` (`SharedStateTypes.kt:492–496`) and the five
  `BuyAccountAction` entries behind them (`BuyAction.kt:53–57`) are the exact
  shape this needs. Follow it rather than inventing a second one.
- **Provider detection is in place.** `getProviderIds()` returns `"password"`
  for an email-backed account and `SharedStateTypes.kt:551` already maps it to
  `AuthProvider.EMAIL`.
- **Only one production implementation.** `GitLiveAuthRepository` is the single
  binding in `AndroidDomainModule.kt:64` and `IosDomainModule.kt:62`.

## Two things that will bite if ignored

**`REQUIRES_RECENT_LOGIN`.** Firebase refuses `updatePassword` on a session that
is more than a few minutes old, and app sessions here are long-lived, so the
*common* case is refusal, not the rare one. The flow must re-authenticate first.
Asking for the current password is therefore not a politeness — it is the
credential the re-auth needs, and it doubles as proof that the person holding
the unlocked phone is the account holder. The existing code already half-knows
this: `handleLinkingException` maps the same error to "Please sign out and sign
in again before linking" (`GitLiveAuthRepository.kt:395`).

**Accounts with no password.** A Google- or Apple-only account has nothing to
change, and `updatePassword` on one fails obscurely. The entry point has to be
hidden unless `"password"` is among the provider ids.

## Phases

### Phase 1 — Domain

`AuthRepository`: add

```kotlin
suspend fun reauthenticateWithPassword(currentPassword: String): Result<Unit>
suspend fun updatePassword(newPassword: String): Result<Unit>
```

Two methods rather than one combined `changePassword`, because the two failures
have to be told apart in the UI: a wrong *current* password belongs under the
first field, a rejected *new* one under the second. The repository reads the
signed-in user's email itself, so the UI never passes an address it could get
wrong.

`GitLiveAuthRepository`: implement both against `auth.currentUser`, failing
early when there is no user and when the user has no `password` provider.
Extend the error mapping with `REQUIRES_RECENT_LOGIN`, `WRONG_PASSWORD`,
`INVALID_CREDENTIAL` and `WEAK_PASSWORD` for this operation. Note that these
messages are hardcoded English in the existing code; keep to that convention
here and localize the whole file separately rather than making this one
inconsistent.

`InMemoryAuthRepository` (it keeps a plaintext `password` per user, `:315`) and
`FakeAuthRepository` get real implementations, not stubs — the tests in Phase 4
need them to reject a wrong current password.

### Phase 2 — Buy flavor

- `CustomerProfileScreenState`: `showChangePasswordDialog`,
  `changePasswordCurrent`, `changePasswordNew`, `changePasswordConfirm`,
  `changePasswordError`, `isChangingPassword`.
- `BuyAccountAction`: show / dismiss / three field updates / submit.
- `BuyAppViewModelAuth.kt`: handlers next to the email-linking ones, and the
  submit path — validate locally (non-empty, ≥ 6 characters to match
  `InMemoryAuthRepository:136`, new ≠ current, confirm matches), then
  re-authenticate, then update, then clear the fields and show a snackbar.
  Never log any of the three values; per the project's logging rule, PII is
  fine in `Log.d` but passwords are not PII, they are secrets, and they do not
  go in any log at any level.
- `ChangePasswordDialog.kt` in `ui/screens/buy/components/`, modelled on
  `EmailLinkingDialog`, with the `PasswordVisualTransformation` +
  `passwordVisible` toggle pattern from `LoginScreen.kt:224`.
- `LoginStatusCard.AuthenticatedStatus`: a third action, shown only when
  `AuthProvider.EMAIL` is in `authProviders`. The row is currently
  `SpaceBetween` with two buttons; three will need a rethink of that row rather
  than a third item squeezed in.

### Phase 3 — Sell flavor

The seller profile has no dialog state of its own for this, so the same fields
go on the seller's profile state and the same actions on `SellUserAction`
alongside `RequestPasswordReset` (`SellAction.kt:29`). The entry point is an
`OutlinedButton` above **Abmelden** (`SellerProfileScreen.kt:362`), under the
same `AuthProvider.EMAIL` condition.

**Put the dialog in `commonMain`,** not in `buyMain` — it is the same three
fields for both flavors, and `EmailLinkingDialog` living in `buyMain` is only
because guest linking is a buy-only concept. Password changing is not. That
means Phase 2 should create it in
`ui/screens/common/components/ChangePasswordDialog.kt` and both flavors pass
their own state and callbacks into it.

### Phase 4 — Tests and strings

- `InMemoryAuthRepositoryTest`: re-auth succeeds with the right password, fails
  with the wrong one; `updatePassword` changes what `signInWithEmail` accepts;
  both fail with no user signed in.
- `FakeAuthRepository`: a failure switch for each of the two calls, so the
  ViewModel tests can drive the error branches.
- ViewModel tests in `testBuy` and `testSell`: each local validation rejection,
  the wrong-current-password path, and the success path clearing the fields.
- Strings in `values/strings.xml` (German, primary) **and**
  `values-en/strings.xml`, named `change_password_*` to sit with
  `delete_account_*` and `link_account_*`. Any string with a substitution needs
  a positional `%1$s` specifier — a bare `%s` reaches the user literally.

## Decisions taken

- **In-app with current-password re-auth**, not a reset mail to a signed-in
  user. The password never leaves the app and the user never leaves for their
  inbox.
- **Both flavors.**
- **No "send me a link instead" fallback** in this first cut. Someone who has
  forgotten the current password while still signed in can sign out and use the
  existing forgotten-password button; adding a second route through the same
  dialog is worth doing only if that turns out to happen.

## Out of scope

Changing the *email* address (`verifyBeforeUpdateEmail` exists in the SDK and is
a larger question, because the buyer's email is embedded in every order — see
`reports/buyer-profile-data-safety.md`), and localizing
`GitLiveAuthRepository`'s error strings.

## What the build changed

Two things departed from the plan.

**The error slots hold a `StringResource`, not a resolved `String`.** The first
cut had the ViewModels resolve their error text with `getString`, and six of the
seller tests died on `Method getSystem in android.content.res.Resources not
mocked`: Compose Resources cannot be resolved in a plain JVM unit test. No
existing ViewModel test in the repo exercises a `getString` path, so nothing had
hit this before. `ChangePasswordState` now carries resource identifiers and
`ChangePasswordDialog` resolves them, which is the better split anyway — a
ViewModel has no resource environment and no business holding localized text.
`changePasswordError` is correspondingly non-suspend.

**The success snackbar resolves its label inside `runCatching`.** It is the one
`getString` left on the path, and by the time it runs the password has already
been changed; a confirmation label that cannot be resolved must not be reported
to the user as a failure.

## Tests

- `InMemoryAuthRepositoryTest`, section K — 9 tests. The in-memory double keeps
  a re-authentication window of its own, so `updatePassword` without a prior
  re-auth fails there exactly as Firebase fails in production, and one re-auth
  buys one change.
- `SellAppViewModelChangePasswordTest` and
  `BuyAppViewModelChangePasswordTest` — 11 each, deliberately parallel, so the
  two flavors cannot drift apart. They pin the split that matters: a rejected
  re-authentication reports under the *current* password field and never reaches
  `updatePassword`; a rejected update reports under the *new* one.

Full suites green: 1057 buy, 1178 sell.

## Still to do

- Exercise it on a device against real Firebase. The error mapping in
  `handlePasswordChangeException` is written from the documented error codes, not
  from observed ones.
