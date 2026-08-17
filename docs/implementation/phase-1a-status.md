# Phase 1A status

## Implemented

- Pinned Android Kotlin/Compose scaffold, light/dark system theme, local-only onboarding, Home shell, Add ID local input shell, My ID unavailable state, and Settings shell.
- Android backup/data-extraction baseline with no runtime permissions.
- Dependency-free Rust workspace with non-cryptographic domain types and fail-closed review gates.

## Deliberately blocked

- Identity creation, contact-ID issuance, rendezvous, secure sessions, encryption, key exchange, safety-code derivation, transport, push, and persistence.
- `veil-core` reports security-review/dependency-approval errors instead of success for rendezvous and secure-session requests.

## Deferred

- Rust/Kotlin FFI selection; database/storage; trusted expiry enforcement; Android Keystore, app lock, screenshot/clipboard/keyboard controls; QR camera scanning; network/push; backend; all cryptographic implementation.
- Project licensing: no Veil project license has been selected. `veil-core` is explicitly non-publishable and declares no SPDX license until that decision is made.

## Build/test commands

```powershell
cd android; .\gradlew.bat test; .\gradlew.bat assembleDebug; .\gradlew.bat lint
cd rust; cargo fmt --check; cargo clippy --all-targets --all-features -- -D warnings; cargo test --all
```

## Known limitations

The app is a local offline shell. It has no identity, ID, message, network, persistence, or secure-messaging capability. It must not be described as a finished secure messenger.
