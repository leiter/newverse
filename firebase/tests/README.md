# Database security rule tests

Tests for `firebase/database.rules.json`. They run against the local Firebase
Emulator — no cloud project, no real data, no cost.

## Running them

    cd firebase/tests
    npm install          # once
    npm test

`npm test` boots the database emulator, loads the rules, runs the suite and
shuts the emulator down again.

## What they check

Each test acts as a specific user and asserts that a read or write is either
allowed or denied. `authenticatedContext(uid)` simply claims to be that uid,
which is what makes the attacker's side easy to express.

Both directions matter. A rule that denies too much breaks the app just as
badly as one that denies too little, so every restriction is paired with a
test that the legitimate path still works.

The attacker in these tests, `MALLORY`, is an ordinary signed-in user. In this
app that means anyone who installed it and tapped "Continue as guest" —
anonymous auth is free and unlimited, so `auth != null` is not a trust
boundary.

## Checking that a fix actually fixed something

    npm run test:against-old-rules

Runs the same suite against the ruleset from commit d954189, before the
hardening in 88450af. 13 of the denial tests fail there — the writes and reads
they forbid all succeed. That is the regression these tests exist to catch.

(Three further tests fail against the old rules for uninteresting reasons: two
seed data in a shape the old approval mechanism didn't use, and the read
receipt test fails because read receipts were genuinely broken under the old
rules.)

## Notes

- The suite runs single threaded (`--test-concurrency=1`): the emulator holds
  one shared database and the tests clear it between cases.
- `run-tests.sh` resolves `node` before invoking the firebase CLI. The CLI is a
  self-contained binary that puts its own bundled node — v14, with no test
  runner — ahead of yours on PATH for the script it spawns.
