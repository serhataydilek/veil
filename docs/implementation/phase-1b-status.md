# Phase 1B status

## Implemented

- Android-only AES-256/GCM local protection key in `AndroidKeyStore` with a constant versioned internal alias.
- Encrypted, authenticated, bounded sentinel envelope in `noBackupFilesDir` with atomic-file persistence.
- Fail-closed missing-key, unreadable-state, malformed-format, and explicit-purge semantics.
- Local developer onboarding copy for preparing storage and reporting readiness without claiming an identity exists.
- Internal-only Keystore protection-level classification; StrongBox is neither requested nor claimed.

## Tested

- JVM tests cover fresh state, successful provisioning, missing-key-with-ciphertext, no automatic replacement, malformed/unsupported/oversized formats, failed writes, purge semantics, and randomized GCM IV behavior.

## Instrumentation status

- Focused Android Keystore instrumentation tests are present for key creation, round trip, randomized encryption, missing-key handling, and purge.
- Instrumentation APK compilation is validated. Execution requires an available emulator or device and is not claimed here until run.

## Deliberately blocked

- Veil protocol identity, contact-ID issuance, rendezvous, secure sessions, E2EE, crypto-protocol selection, networking, backend, database, persistence beyond the sentinel, Rust FFI, and push.

## Deferred

- Biometric/device-credential app lock, explicit StrongBox request policy, database/storage design, protected identity/session material, migration/recovery policy, and Android device/version matrix validation.

## Known limitations

- Keystore protection level is device-dependent and not a product security claim.
- Key loss or unreadable ciphertext has no automatic recovery or reset.
- Logical purge does not promise physical-media erasure.

## Validation (2026-08-18)

- Android `test` passed with 11 JVM tests.
- Android `assembleDebug`, unsigned `assembleRelease`, `lint`, and `assembleAndroidTest` passed.
- No emulator or device was attached, so `connectedDebugAndroidTest` was not run.
- Rust `cargo fmt --check`, `cargo clippy --all-targets --all-features -- -D warnings`, and `cargo test --all` passed; Rust tests: 3.
