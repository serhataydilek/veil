# Phase 1I supply-chain and release security foundation

## Scope

Phase 1I adds local, auditable build-security controls. It creates no protocol, identity, networking, push, messaging, cryptography, or production signing capability.

## Dependency and toolchain model

Android repositories are centralized in `android/settings.gradle.kts`: Google and Maven Central only, with project repositories rejected. The Gradle wrapper is pinned to 9.5.0 with a distribution SHA-256. Android versions reside in the version catalog: AGP 9.3.1, Kotlin 2.4.10, Compose BOM 2026.06.00, AndroidX/Biometric/JNA versions, and NDK 29.0.14206865. Java 17, compile/target SDK 36, and min SDK 26 are enforced in the app build.

Rust uses the committed `Cargo.lock` and `--locked` validation. The workspace has three local crates (`veil-core`, `veil-ffi`, and `veil-uniffi-bindgen`); UniFFI is exactly 0.32.0. `rust-toolchain.toml` pins Rust 1.88.0, clippy/rustfmt, and Android targets; Gradle checks matching Rust, cargo-ndk 4.1.2, and NDK pins before FFI builds.

Gradle dependency verification metadata is deliberately not auto-generated in this phase: generating checksums from an arbitrary local cache would create an unreviewed trust root. Repository restriction, wrapper checksum, version catalog, locked Rust resolution, and reviewable local reports are active controls; external checksum review remains required before committing Gradle verification metadata.

## SBOM and validation

`scripts/New-VeilSbom.ps1` writes ignored output under `artifacts/sbom`. It emits a CycloneDX 1.5 Rust component inventory from `cargo metadata --locked` and a separately hashed Android version-catalog inventory. Outputs contain no developer paths or credentials.

`scripts/Test-VeilApk.ps1` audits the built manifest and ZIP: only biometric/notification permissions and AndroidX's app-scoped dynamic-receiver permission are accepted; INTERNET, location, contacts, SMS, microphone, camera, media/storage, source, cargo metadata, developer paths, signing material, debug symbols, and unexpected native libraries fail. Expected native ABIs are arm64-v8a and x86_64, each with reviewed `libveil_ffi.so`, JNA `libjnidispatch.so`, and Compose path `libandroidx.graphics.path.so`.

`scripts/Test-VeilRepositoryHygiene.ps1` rejects signing/key files and scans tracked-style source areas for private-key blocks, token assignments, and developer paths. False positives fail visibly and need a reviewed pattern change; ignored build/cache folders are excluded.

`scripts/Invoke-VeilSecurityValidation.ps1` is the canonical local entry point. It runs hygiene, locked Rust validation, Android test/lint/builds, APK audits, SBOM generation, and `git diff --check`.

## Reproducibility assessment

Dependency resolution is pinned by Cargo.lock, Gradle wrapper/catalog, centralized repositories, and explicit toolchain checks. Android APK byte-for-byte reproducibility is not claimed: debug signing and archive/native build metadata can vary. The validation gate compares resolved inputs and audits output contents, not hashes as a substitute for a proven reproducible release process.

## Deferred

No external provenance attestation, signed release key, Gradle checksum metadata trust review, hosted scanner, or CI service is added. Phase 1E/1F review blockers remain unresolved.
