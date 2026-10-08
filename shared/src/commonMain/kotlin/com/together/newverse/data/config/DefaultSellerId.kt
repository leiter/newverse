package com.together.newverse.data.config

/**
 * The seller this build talks to when the buyer has not connected to one yet.
 *
 * The marketplace has a single seller, but its auth uid differs per Firebase project,
 * so this value follows the same debug/release axis that selects the backend itself
 * (`androidApp/src/{debug,release}/google-services.json` on Android, the copied
 * `GoogleService-Info` plist on iOS). A build pointing at a seller that does not exist
 * in its project reads an empty `/articles/{sellerId}` node and shows no catalogue.
 *
 * See `doc/build-identity-and-firebase-wiring.md`.
 */
expect val defaultSellerId: String
