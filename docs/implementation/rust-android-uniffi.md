# Rust–Android UniFFI integration

This is the Phase 1D implementation note for the selected FFI path. It is not a security audit of UniFFI, JNA, or the Android NDK.

## Pins

| Component | Version | Authoritative location | Purpose |
|---|---|---|---|
| UniFFI | 0.32.0 | `rust/crates/veil-ffi/Cargo.toml`, `rust/tools/veil-uniffi-bindgen/Cargo.toml` | Proc-macro FFI definitions and stable Kotlin/JNA bindings |
| JNA | 5.19.1 | `android/gradle/libs.versions.toml` | Load `libveil_ffi.so` from generated Kotlin (`net.java.dev.jna:jna:5.19.1@aar`) |
| Android NDK | 29.0.14206865 | `android/gradle/libs.versions.toml`, consumed as `android.ndkVersion` | Compile Rust `cdylib` for Android |
| cargo-ndk | 4.1.2 | `android/gradle.properties` `veil.cargoNdkVersion` | Reproducible Android Rust builds and `jniLibs` layout |
| Rust | 1.88.0 | `rust/rust-toolchain.toml`; Gradle `veil.rustVersion` must stay aligned | Existing workspace toolchain |
| Kotlin | 2.4.10 | `android/gradle/libs.versions.toml` | Existing Android toolchain |
| minSdk / native API | 26 | `minSdk` and `veil.nativeApiLevel` | Android 8.0+ devices and native minimum |

Do not duplicate unused pins in `android/gradle.properties`. Gradle toolchain verification parses `rustc --version` and `cargo ndk --version` and requires the exact version token (`rustc 1.88.0`, `cargo-ndk 4.1.2`). Nearby versions such as `4.1.20`, `14.1.2`, `4.1.1`, or `1.88.1`, and malformed tool output, are rejected.

Do not use `uniffi-bindgen-kotlin-jni`. Do not use a globally installed `uniffi-bindgen` whose version may drift.

## Crates and tools

- `rust/crates/veil-core` — platform-independent domain/security core. Still `#![forbid(unsafe_code)]`.
- `rust/crates/veil-ffi` — narrow UniFFI bridge (`cdylib` + `rlib`) over `veil-core`.
- `rust/tools/veil-uniffi-bindgen` — workspace-local UniFFI 0.32.0 CLI.

## Exported FFI contract

Bridge contract version: **1**.

Functions:

- `bridge_contract_version() -> u32`
- `core_policy_snapshot() -> CorePolicySnapshot`

Types:

- `CorePolicySnapshot { bridge_contract_version, max_message_availability_seconds, rendezvous_status, secure_session_status }`
- `SecurityGateStatus { ReviewRequired }`

There is no available gate variant. Mapping is exhaustive over current `veil-core` `FeatureAvailability` values, so a blocked core state cannot become available through the bridge.

## Generated-file policy

Do not commit:

- generated Kotlin (`uniffi/veil_ffi/veil_ffi.kt`)
- `libveil_ffi.so` / host `veil_ffi.dll`
- `rust/target/`
- Gradle `build/` UniFFI or `jniLibs` outputs

Generate into:

- host metadata/cdylib: `android/app/build/rust/host`
- Kotlin bindings: `android/app/build/generated/source/uniffi/kotlin`
- debug native libs: `android/app/build/generated/jniLibs/debug`
- release native libs: `android/app/build/generated/jniLibs/release`

Debug and release native outputs are separate. The Gradle tasks delete the destination directory before copying so a previous variant cannot be packaged by accident. Release app builds package Rust release artifacts.

## Gradle integration

`android/app/build.gradle.kts` resolves `cargo` and `rustup` from PATH or `CARGO_HOME`, sets `ANDROID_NDK_HOME` to the pinned NDK under the Android SDK, and:

1. Ensures exactly `rustc 1.88.0`, Android targets `aarch64-linux-android` / `x86_64-linux-android`, and exactly `cargo-ndk 4.1.2` (`cargo install cargo-ndk --version 4.1.2 --locked` if the reported version is not that exact token).
2. Builds the host `veil-ffi` library for UniFFI metadata.
3. Runs `cargo run -p veil-uniffi-bindgen --locked -- generate --library <host cdylib> --language kotlin --no-format`.
4. Builds Android libraries with `cargo ndk -t arm64-v8a -t x86_64 --platform 26` into the variant `jniLibs` directory.
5. Compiles generated Kotlin and packages `.so` files into the APK.

Prerequisites: Rust 1.88.0 via rustup, Android SDK with NDK `29.0.14206865`, JDK 17+.

## Kotlin adapter

Compose and the rest of the app depend on handwritten types in `com.veil.app.core`:

- `RustCoreBridge`
- `CoreBridgeSnapshot` / `CoreBridgeStatus` (`AVAILABLE`, `UNAVAILABLE`, `INCOMPATIBLE`)
- `SecurityFeatureGate` for rendezvous and secure-session status

Generated UniFFI calls stay inside `ProductionCoreNativeApi`. JVM unit tests inject `CoreNativeApi` fakes from test sources only.

Expected native/JNA/UniFFI failures (`Exception` or `LinkageError`, including `UnsatisfiedLinkError`) → `UNAVAILABLE`. Unsupported contract version → `INCOMPATIBLE`. The production adapter does not catch `Throwable`, so fatal VM conditions are not converted into `UNAVAILABLE`. Neither path returns a default successful policy snapshot, exposes exceptions / JNA / native paths to UI, nor logs filesystem/native-library paths.

## SecurityFeatureGate

`RENDEZVOUS` and `SECURE_SESSION` are authoritative from Rust through `RustCoreBridge`. If the bridge is unavailable, incompatible, or unmappable, those features stay `Unavailable`. They never fall back to available.

Contact-ID issuance is still not modeled in `veil-core` and remains a Kotlin `NotImplemented` state.

## Dependency licenses

Recorded from upstream package metadata. Veil’s project license remains undecided; this phase does not copy third-party license text into the repository.

| Dependency | Pin | Upstream license metadata | Purpose |
|---|---|---|---|
| UniFFI | 0.32.0 | MPL-2.0 | FFI definitions, scaffolding, and Kotlin bindgen |
| JNA | 5.19.1 | Apache-2.0 OR LGPL-2.1-or-later | Load the UniFFI Kotlin native library |
| cargo-ndk | 4.1.2 | Apache-2.0 OR MIT | Android Rust `cdylib` builds |
| Android NDK | 29.0.14206865 | Apache-2.0 | Android native sysroot and linker |

`getrandom` appears transitively through UniFFI’s `tempfile` usage in scaffolding generation. It is not a Veil protocol cryptographic dependency and is not used to generate identity, session, or message secrets.

## Native failure

A missing or corrupt `libveil_ffi.so` must not crash into a security-feature success path. Bridge initialization failure is `CoreBridgeStatus.UNAVAILABLE`; Rust-dependent capabilities remain blocked. Phase 1B/1C local privacy and storage can still run.
