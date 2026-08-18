# Phase 1C status

## Implemented

- Optional Veil App Lock, disabled by default, using platform authentication only.
- AndroidX BiometricPrompt 1.1.0 with API-ranged strong-biometric and device-credential policy.
- In-memory lock session that never persists `UNLOCKED` and reconstructs `LOCKED` after process death when the preference is enabled.
- Foreground/background relocking through `ProcessLifecycleOwner`, with authentication-in-progress modeled so the system credential UI does not race-lock.
- Dedicated locked root UI; normal navigation is not composed while locked.
- `appLockEnabled` persisted inside the existing Phase 1B VLP1 envelope as a versioned `VLS1` payload.
- Atomic migration from the Phase 1B sentinel, defaulting App Lock to disabled. A failed migration write keeps the legacy ciphertext and reports `MIGRATION_FAILED` instead of known-disabled App Lock.
- Always-on `FLAG_SECURE` and API 33+ recents screenshot disablement, independent of App Lock.
- Preference and unlock completion re-evaluate foreground state so an enabled App Lock cannot remain `UNLOCKED` after a real background event.

## Tested

- JVM tests: 49 passed, 0 failed (`ProtectedStateStoreTest` 21, `AppPrivacyControllerTest` 24, `ProtectedLocalPayloadTest` 3, `SecurityFeatureGateTest` 1). Existing Phase 1B Keystore/encrypted-state tests remain and stayed green.

## Instrumentation status

- `connectedDebugAndroidTest` passed on `Medium_Phone_API_36.1` (AVD, Android 16): 12 tests, 0 failures/errors (5 Phase 1B Keystore tests plus 7 Phase 1C tests).

## Deliberately blocked

- Veil protocol identity, contact-ID issuance, rendezvous, secure sessions, E2EE, networking, backend, database, persistence beyond the protected local payload, Rust FFI, push, clipboard behavior, and keyboard-learning controls.
- Binding the Phase 1B AES local-protection key to biometrics.
- Custom Veil-owned PIN/password UI.
- App-lock grace timeouts.

## Deferred

- User-selectable lock delay, StrongBox request policy, database/storage design, protected identity/session material, clipboard and keyboard controls, and broader Android device/version matrix validation.

## Known limitations

- Screen privacy is not universal. Another camera, compromised OS, accessibility services, root, OEM bugs, and some capture implementations can still observe the display.
- App Lock is local UI access control, not cryptographic unlock, identity authentication, or recovery.
- If protected state cannot be read, Veil remains fail-closed; App Lock cannot be treated as disabled to bypass that state.

## Validation (2026-08-18)

- Android `test` passed with 49 JVM tests.
- Android `assembleDebug`, unsigned `assembleRelease`, `lint`, and `assembleAndroidTest` passed. Lint reported no errors; remaining warnings are the existing target/compile SDK notices.
- `connectedDebugAndroidTest` passed on the existing `Medium_Phone_API_36.1` AVD: 12 tests, 0 failures/errors.
- Rust source/workspace is unchanged.
