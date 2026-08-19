# Phase 1D status

## Implemented

- UniFFI 0.32.0 stable Kotlin/JNA bridge from `veil-ffi` to a handwritten Kotlin adapter.
- Workspace-local `veil-uniffi-bindgen` pinned to the same UniFFI version.
- Gradle integration that generates Kotlin bindings and packages `libveil_ffi.so` for `arm64-v8a` and `x86_64`.
- Exact toolchain verification for `rustc 1.88.0` and `cargo-ndk 4.1.2` (substring near-matches such as `4.1.20` are rejected).
- Bridge contract version 1 with fail-closed `AVAILABLE` / `UNAVAILABLE` / `INCOMPATIBLE` handling.
- Expected native/JNA/UniFFI failures (`Exception` or `LinkageError`) map to `UNAVAILABLE` without catching all `Throwable`.
- Security feature gates for rendezvous and secure session now read Rust policy through `RustCoreBridge`.
- Quiet development-shell copy: “Rust core connected” and “Secure messaging features remain unavailable pending review”.

## Tested

- Rust workspace tests for contract version 1, `MAX_MESSAGE_AVAILABILITY` sourced from `veil-core`, and review-required rendezvous/session mapping that cannot become available.
- JVM unit tests for exact `cargo-ndk`/`rustc` version parsing, compatible, incompatible, and unavailable bridge states, expected native `Exception`/`LinkageError` failure, blocked feature mapping, contact-ID not-implemented, and no fallback success. These tests use fakes and do not require native JNA.
- Instrumentation on the existing `Medium_Phone_API_36.1` emulator exercises the real Kotlin → UniFFI → JNA → `libveil_ffi.so` → `veil-ffi` → `veil-core` path.

## Deliberately blocked

- Veil protocol identity generation.
- Contact-ID issuance.
- Rendezvous protocol implementation.
- E2EE / secure-session implementation.
- Message encryption or decryption.
- Networking, `INTERNET` permission, backend, database, push, and message storage.
- Handwritten JNI / `unsafe` Rust ABI.
- Making any security capability available.

## Deferred

- 32-bit Android ABIs.
- Independent security audit of UniFFI/JNA.
- iOS bindings.
- Broader device/NDK matrix validation.

## Validation (2026-08-18, review-fix pass)

- Gradle now requires the exact reported version tokens `cargo-ndk 4.1.2` and `rustc 1.88.0`. Unused duplicate pins `veil.uniffiVersion`, `veil.jnaVersion`, and `veil.ndkVersion` were removed from `android/gradle.properties`.
- Production bridge code catches `Exception` and `LinkageError` only. Fatal VM errors are not converted into `UNAVAILABLE`.
- Rust `cargo fmt --check`, `cargo clippy --workspace --all-targets --all-features -- -D warnings`, and `cargo test --workspace --all-features --locked` passed: 8 tests (3 `veil-core`, 5 `veil-ffi`).
- Android `test` passed with 67 JVM tests, 0 failed (`FfiToolchainVersionTest` 8, `RustCoreBridgeTest` 6, `SecurityFeatureGateTest` 5, plus existing Phase 1B/1C tests).
- `assembleDebug` and unsigned `assembleRelease` passed, including APK inspection for `lib/arm64-v8a/libveil_ffi.so` and `lib/x86_64/libveil_ffi.so`. No Rust sources, Cargo files, `.pdb` files, or registry paths were packaged.
- `lint` passed. Remaining notices are existing target/compile SDK and generated-UniFFI unused-expression warnings.
- `assembleAndroidTest` passed.
- `connectedDebugAndroidTest` passed on `Medium_Phone_API_36.1` (AVD, Android 16, x86_64): 14 tests, 0 failures/errors, including two real UniFFI/JNA/`libveil_ffi.so` tests.
- No security feature became available. FFI contract remains `BRIDGE_CONTRACT_VERSION = 1` with only `bridge_contract_version()` and `core_policy_snapshot()`.

## Validation (2026-08-18, initial Phase 1D)

- Rust `cargo fmt --check`, `cargo clippy --workspace --all-targets --all-features -- -D warnings`, and `cargo test --workspace --all-features --locked` passed: 8 tests (3 `veil-core`, 5 `veil-ffi`).
- `cargo tree -p veil-ffi` shows UniFFI 0.32.0 and `veil-core` only as direct runtime crates. No protocol-crypto crate is present.
- Android `test` passed with 58 JVM tests, 0 failed (`RustCoreBridgeTest` 5, `SecurityFeatureGateTest` 5, plus existing Phase 1B/1C tests).
- `assembleDebug` and unsigned `assembleRelease` passed, including APK inspection for `lib/arm64-v8a/libveil_ffi.so` and `lib/x86_64/libveil_ffi.so`.
- `lint` passed. Remaining notices are existing target/compile SDK and generated-UniFFI unused-expression warnings.
- `assembleAndroidTest` passed.
- `connectedDebugAndroidTest` passed on `Medium_Phone_API_36.1` (AVD, Android 16, x86_64): 14 tests, 0 failures/errors, including two real UniFFI/JNA/`libveil_ffi.so` tests.
- A generated-output-clean rebuild regenerated UniFFI Kotlin and Android `.so` files and re-verified the debug APK.

## Known limitations

- The FFI boundary is real and tested, not independently security-audited.
- `getrandom` appears only as a UniFFI/tempfile transitive dependency used by scaffolding generation, not as Veil protocol cryptography.
- Native failure leaves Rust-backed features unavailable without disabling Phase 1B/1C local privacy storage.
