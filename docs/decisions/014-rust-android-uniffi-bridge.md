# ADR 014: Rust–Android UniFFI bridge

## Status

Accepted for the Phase 1D FFI foundation. This does not implement Veil protocol identity, rendezvous, secure sessions, or message cryptography, and it is not an independent security audit of UniFFI or JNA.

## Context

Phases 1A–1C left Rust as the intended owner of future shared security and domain logic, with Kotlin owning Android platform behavior. The FFI technology was explicitly unselected. Phase 1D needs a real, tested Rust → Kotlin boundary so later approved identity, session, and message logic can live in Rust without being duplicated in Kotlin.

The experimental UniFFI JNI Kotlin backend (`uniffi-bindgen-kotlin-jni`) is unreleased and must not become Veil’s production FFI foundation.

## Decision

- Rust remains the owner of future shared security and domain logic. `veil-core` stays platform-independent and keeps `#![forbid(unsafe_code)]`.
- Kotlin remains the owner of Android platform behavior: lifecycle, Compose, Keystore, BiometricPrompt, filesystem paths, notifications, and permissions.
- Use UniFFI **0.32.0** with the stable standard Kotlin backend backed by JNA. Do not use `uniffi-bindgen-kotlin-jni`.
- Pin JNA **5.19.1** as `net.java.dev.jna:jna:5.19.1@aar`. Do not add `jna-platform` unless a later phase genuinely requires it.
- Pin Android NDK **29.0.14206865** and `cargo-ndk` **4.1.2**. Keep Rust **1.88.0**, Kotlin **2.4.10**, and `minSdk` **26**.
- Phase 1D Android ABIs are `arm64-v8a` (devices) and `x86_64` (current emulator validation). 32-bit Android support is out of scope.
- Generate Kotlin bindings and `.so` files into Gradle/Rust build directories. Do not commit generated bindings, native libraries, or bindgen outputs.
- Expose only a tiny immutable policy/health contract: `bridge_contract_version()` and `core_policy_snapshot()`. Values come from `veil-core`. Do not duplicate the 24-hour availability constant or rendezvous/session policy in `veil-ffi`.
- `BRIDGE_CONTRACT_VERSION = 1`. Kotlin must verify the expected version. An unsupported version is `CoreBridgeStatus.INCOMPATIBLE` and fails closed.
- Native load, UniFFI initialization, mapping, or version failure is `CoreBridgeStatus.UNAVAILABLE` or `INCOMPATIBLE`. Never return a successful default policy snapshot. Local Android privacy/storage from Phases 1B/1C may remain usable; Rust-security-dependent features remain unavailable.
- No FFI call may create rendezvous capabilities, sessions, identity material, contact IDs, random secrets, or encrypt/decrypt messages.
- Handwritten Veil Rust contains no `unsafe`, `extern "C"`, raw pointers, or manual JNI. UniFFI owns the generated FFI boundary.
- Use a workspace-local `veil-uniffi-bindgen` tool pinned to UniFFI 0.32.0 so bindgen cannot drift from the runtime crate.

## Consequences

Android can load `libveil_ffi.so` and read fail-closed policy from `veil-core`. Secure messaging, identity, rendezvous, and sessions remain unavailable pending review. UniFFI/JNA/NDK are build and FFI machinery, not a claim that the foreign boundary has been independently audited.
