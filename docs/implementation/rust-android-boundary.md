# Rust–Android ownership boundary

Phase 1D selected UniFFI 0.32.0 with the stable Kotlin/JNA backend. See [ADR 014](../decisions/014-rust-android-uniffi-bridge.md) and [rust-android-uniffi.md](rust-android-uniffi.md). This is a technology and ownership decision, not an independent security audit of the FFI stack.

Kotlin owns Android lifecycle, Compose, navigation, permissions, notifications, platform authentication, Android secure-storage adapters, and user interaction. Rust owns shared domain/security policy now exported through a tiny UniFFI contract, and will later own approved identity/security state, approved rendezvous and secure-session integrations, message-envelope/security policy, shared expiry rules where appropriate, and transport-independent secure state.

`veil-core` remains platform-independent and forbids handwritten `unsafe`. `veil-ffi` is a narrow bridge crate: it maps `veil-core` policy into UniFFI types and does not contain Android Keystore, lifecycle, BiometricPrompt, filesystem paths, notifications, or Compose behavior.

The live FFI contract is policy/status only (`bridge_contract_version`, `core_policy_snapshot`). It does not create identity, contact IDs, rendezvous capabilities, sessions, or ciphertext. Kotlin must not copy future cryptographic logic; Android security-feature gates for rendezvous and secure session read Rust through `RustCoreBridge` and fail closed when the native library is missing, incompatible, or unreadable.
