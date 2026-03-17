# AGENTS.md

This file is for AI coding agents working in this repository. Use it as the repo-specific operating guide before making changes.

## Repo Overview
- This is a single-module Android application in Kotlin.
- Build system: Gradle with Kotlin DSL.
- UI stack: Jetpack Compose with activity-driven flows.
- Android config lives primarily in [app/build.gradle.kts](/Users/bradleydegelau/Coding/cobalt-comet/app/build.gradle.kts) and [AndroidManifest.xml](/Users/bradleydegelau/Coding/cobalt-comet/app/src/main/AndroidManifest.xml).
- Current platform targets from Gradle:
  - `minSdk = 33`
  - `targetSdk = 36`
  - `compileSdk = 36`
  - Java/Kotlin target = 17

## Working Model
- Prefer small, targeted changes over broad refactors.
- Preserve current behavior unless the task explicitly changes product behavior.
- Do not add new architectural layers just to "clean things up". This app is organized around activities, utilities, receivers, and transport helpers.
- Treat manifest wiring, runtime permissions, and intent filters as part of the feature surface, not incidental config.

## Behavioral Map
- [MainActivity.kt](/Users/bradleydegelau/Coding/cobalt-comet/app/src/main/java/com/alienmantech/cobaltcomet/MainActivity.kt)
  - Main launcher activity.
  - Owns message history, setup/navigation, permission checks, contact management, and Bluetooth setup entrypoints.
- [ShareReceiverActivity.kt](/Users/bradleydegelau/Coding/cobalt-comet/app/src/main/java/com/alienmantech/cobaltcomet/ShareReceiverActivity.kt)
  - Handles inbound `ACTION_SEND` and supported `ACTION_VIEW` intents.
  - Parses shared map/location data and routes outbound messages to the selected recipient.
- [CommunicationUtils.kt](/Users/bradleydegelau/Coding/cobalt-comet/app/src/main/java/com/alienmantech/cobaltcomet/utils/CommunicationUtils.kt)
  - Encodes outgoing message payloads.
  - Decodes intercepted inbound messages.
  - Launches follow-up actions such as Google Maps navigation or browser fallback.
- [TransportDispatcher.kt](/Users/bradleydegelau/Coding/cobalt-comet/app/src/main/java/com/alienmantech/cobaltcomet/utils/TransportDispatcher.kt)
  - Selects SMS vs Bluetooth transport based on `PhoneEntry.type`.
  - Runs preflight validation before attempting delivery.
- [TransportPreflight.kt](/Users/bradleydegelau/Coding/cobalt-comet/app/src/main/java/com/alienmantech/cobaltcomet/utils/TransportPreflight.kt)
  - Central guardrails for base send validation and Bluetooth-specific checks.
- [Utils.kt](/Users/bradleydegelau/Coding/cobalt-comet/app/src/main/java/com/alienmantech/cobaltcomet/utils/Utils.kt)
  - Persists contacts and message history in shared preferences.
  - Handles legacy compatibility for saved contact data.
  - Contains Google Maps URL parsing, navigation launching, phone normalization, and date formatting helpers.
- [SmsReceiver.kt](/Users/bradleydegelau/Coding/cobalt-comet/app/src/main/java/com/alienmantech/cobaltcomet/receivers/SmsReceiver.kt)
  - Intercepts inbound SMS, reconstructs multipart messages, and forwards matching payloads into `CommunicationUtils`.
- Manifest-declared components in [AndroidManifest.xml](/Users/bradleydegelau/Coding/cobalt-comet/app/src/main/AndroidManifest.xml)
  - `ShareReceiverActivity`
  - `SmsReceiver`
  - `BluetoothDiscoveryReceiver`
  - `BluetoothMessageService`
  - If you change permissions, deep links, receivers, services, or exported behavior, update and verify the manifest with the code change.

## Compatibility Rules
- Keep encoded message payloads backward compatible unless the task explicitly changes the wire format.
- Treat `MessageModel` JSON and `PhoneEntry` JSON as compatibility-sensitive persisted data.
- `Utils.decodePhoneEntries` contains legacy CSV fallback behavior. Do not remove or silently break it without an explicit migration plan.
- When changing Google Maps URL parsing or geo intent handling, verify both coordinate-based and label/query-based inputs.

## Editing Guidance
- When changing share flows, check both sides:
  - Source side: intent parsing and message encoding.
  - Receive side: SMS interception, decode logic, persistence, and launch behavior.
- When changing transport behavior, verify:
  - recipient type selection
  - transport-specific preflight validation
  - failure handling and user-visible fallback behavior
- When changing permissions or Bluetooth behavior, inspect both activity permission checks and manifest declarations.
- Keep Compose changes consistent with the existing code style. This repo uses straightforward activity-local Compose screens rather than a separate design system layer.

## Validation
- Primary validation command:
  - `./gradlew test`
- Focused unit-test command when you only need the debug unit test target:
  - `./gradlew :app:testDebugUnitTest`
- `./gradlew test` succeeds in the current repo state and is the default check to run after meaningful changes.

## Test Expectations
- Automated test coverage is currently minimal.
- Add or update unit tests when touching:
  - message encoding or decoding
  - URL or geo parsing
  - saved-data serialization or compatibility
  - transport selection or preflight rules
- Instrumentation coverage is effectively placeholder-level, so UI, permissions, receivers, and intent handling still require careful manual reasoning even if unit tests pass.

## Practical Defaults
- If a task touches persisted data or wire format, assume backward compatibility matters.
- If a task touches Android component registration, assume manifest changes may be required.
- If a task touches navigation launch behavior, preserve fallback behavior unless the task says otherwise.
- If the safest implementation is unclear, prefer the narrower change.
