# Android toolchain

**Selected 2026-08-17; all versions are pinned.**

| Component | Version / choice |
|---|---|
| Android Gradle Plugin | 9.3.1 |
| Gradle wrapper | 9.5.0 |
| Kotlin | 2.4.10 |
| Compose BOM | 2026.06.00 |
| compile SDK / target SDK | 36 / 36 |
| min SDK | 26 |
| Java toolchain | JDK 17 minimum; local verification uses JDK 25 |
| AndroidX Biometric | 1.1.0 |
| AndroidX Lifecycle Process / ViewModel | 2.10.0, matching the existing lifecycle pin |
| Android NDK | 29.0.14206865 |
| JNA | 5.19.1 (`net.java.dev.jna:jna:5.19.1@aar`) |
| cargo-ndk | 4.1.2 |

Android’s official compatibility table requires Gradle 9.5.0 for the AGP 9.3 line; AGP 9.3.1 is the selected stable patch release. Kotlin 2.4.10 is the current stable bug-fix release in the supported 2.4 line ([Android Developers](https://developer.android.com/build/releases/about-agp), [Kotlin releases](https://kotlinlang.org/docs/releases.html)). The Compose BOM is used as Android’s recommended compatible Compose dependency set ([Compose BOM](https://developer.android.com/develop/ui/compose/bom)). API 36 is Android 16; the target is deliberately current stable API rather than an Android 17 preview ([Android 16](https://developer.android.com/about/versions/16)).

The wrapper was regenerated with Gradle 9.5.0, not manually substituted. `distributionSha256Sum` is the official Gradle 9.5.0 binary ZIP SHA-256: `553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746`. The generated `gradle-wrapper.jar` was SHA-256 verified as `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`, matching Gradle’s official checksum reference ([Gradle checksums](https://gradle.org/release-checksums/)).

`minSdk 26` sets the foundation at Android 8.0+, avoiding legacy platform behavior while retaining a broad device base. This is a product-support choice, not a security guarantee; revisit it before release. AGP requires a supported JDK 17-or-newer environment; the local machine supplies JDK 25. The source/target bytecode level remains Java 17 for compatibility.

Phase 1D pins NDK `29.0.14206865`, JNA `5.19.1`, and `cargo-ndk` `4.1.2` for the Rust/Android FFI path documented in [ADR 014](../decisions/014-rust-android-uniffi-bridge.md). Native builds target API 26 and package `arm64-v8a` plus `x86_64` only.
