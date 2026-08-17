# Rust–Android ownership boundary

No FFI technology is selected or integrated in Phase 1A.

Kotlin owns Android lifecycle, Compose, navigation, permissions, notifications, platform authentication, Android secure-storage adapters, and user interaction. Rust will later own approved identity/security state, approved rendezvous and secure-session integrations, message-envelope/security policy, shared expiry rules where appropriate, and transport-independent secure state.

The current `veil-core` crate contains only non-cryptographic domain boundaries and fail-closed review gates. Kotlin must not copy cryptographic logic while this boundary is unresolved. JNI, UniFFI, JNA, and other bridge options require a later technical decision with Android/Rust build, error, threading, ABI, and security review.
