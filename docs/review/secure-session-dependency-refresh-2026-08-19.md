# Secure-session dependency refresh (2026-08-19)

This is a Phase 1E pinned-version evidence refresh. It is not legal advice, not an independent security audit, and not an implementation approval. ADR 005's protocol-family direction is unchanged. ADR 010's gates remain mandatory.

**Access / research date:** 2026-08-19.

Primary candidate: Signal `libsignal` **v0.99.1**. Control comparison only: OpenMLS **0.8.1**. Neither library was added to Veil.

## Authoritative sources used

libsignal:

- Repository: <https://github.com/signalapp/libsignal>
- Tag `v0.99.1`: <https://github.com/signalapp/libsignal/releases/tag/v0.99.1>
- Annotated tag object `5f1fa865d15538b01654cfbd482abd95937684c6` peels to commit **`97801d22dcf9f5bf714f7b8fa3212cdc973ae1c8`**
- Files at that tag: `README.md`, `LICENSE`, `SECURITY.md`, `RELEASE.md`, `TESTING.md`, `Cargo.toml`, `rust-toolchain`, `.tool-versions`, `rust/protocol/**`, `java/android/build.gradle`, `java/build_jni.sh`, `java/code_size.json`, `.github/workflows/build_and_test.yml`
- GitHub Security Advisories API for `signalapp/libsignal` (empty list on 2026-08-19)
- Official upstream issues: [#490](https://github.com/signalapp/libsignal/issues/490), [#677](https://github.com/signalapp/libsignal/issues/677), [#684](https://github.com/signalapp/libsignal/issues/684)
- Official Signal specifications: [X3DH](https://signal.org/docs/specifications/x3dh/), [PQXDH](https://signal.org/docs/specifications/pqxdh/), [Double Ratchet](https://signal.org/docs/specifications/doubleratchet/), [Sesame](https://signal.org/docs/specifications/sesame/)
- Official 2016 Signal license note about App Store additional permission for the earlier `libsignal-protocol-c` library: <https://signal.org/blog/license-update/>

OpenMLS (control only):

- Repository: <https://github.com/openmls/openmls>
- Tag `openmls-v0.8.1` commit **`47dbedecad0c1fd8eb5368d582250ebfcc1e1ce6`**
- Files: `README.md`, `LICENSE`, `CHANGELOG.md`, `openmls/Cargo.toml`
- Advisories: [GHSA-qr9h-x63w-vqfm](https://github.com/openmls/openmls/security/advisories/GHSA-qr9h-x63w-vqfm), [GHSA-8x3w-qj7j-gqhf](https://github.com/openmls/openmls/security/advisories/GHSA-8x3w-qj7j-gqhf)
- [RFC 9420](https://www.rfc-editor.org/rfc/rfc9420.html)

Sources not used for technical or licensing conclusions: blogs summarizing libsignal (except the official Signal 2016 license post cited above), tutorials, Stack Overflow, Reddit, AI-generated summaries, third-party forks.

## Pin

| Item | Value |
|---|---|
| Upstream | `https://github.com/signalapp/libsignal` |
| Tag | `v0.99.1` |
| Annotated tag SHA | `5f1fa865d15538b01654cfbd482abd95937684c6` |
| **Commit SHA behind the tag** | **`97801d22dcf9f5bf714f7b8fa3212cdc973ae1c8`** |
| GitHub release published | 2026-07-23T21:05:42Z |
| Workspace version | `0.99.1` (`Cargo.toml` `[workspace.package]`) |
| Do not depend on | `main` |

The GitHub release body for `v0.99.1` is empty; the annotated tag message records: Node SVR2 enclave migration support, and Swift/Node timestamp-log changes. Those notes are Signal-product facing and are not Veil session APIs.

## Upstream support posture

Exact wording at `v0.99.1` `README.md`:

> This repository is used by the Signal client apps (Android, iOS, and Desktop) as well as server-side. **Use outside of Signal is unsupported.** In particular, the products of this repository are the Java, Swift, and TypeScript libraries that wrap the underlying Rust implementations. **All APIs and implementations are subject to change without notice, as are the JNI, C, and Node add-on "bridge" layers.** However, backwards-incompatible changes to the Java, Swift, TypeScript, and **non-bridge Rust APIs** will be reflected in the version number on a **best-effort basis**, including increases to the minimum supported tools versions.

Further README facts at this tag:

- Signal publishes Java packages **for its own use**: `org.signal:libsignal-server`, `org.signal:libsignal-client`, `org.signal:libsignal-android`.
- Maven location: `https://build-artifacts.signal.org/libraries/maven/` (older builds were on Maven Central).
- NPM package `@signalapp/libsignal-client` is likewise published **for its own use**.
- Contributions that will not be used by official Signal clients may still be considered only if they do not pose an undue maintenance burden.

`RELEASE.md` versioning methodology:

- The first version component **should always be 0**, "to indicate that Signal does not promise stability between releases of the library."
- A change is "breaking" if it requires updates in Signal client/server components **or in external Rust clients of** `libsignal-protocol`, `zkgroup`, `poksho`, `attest`, `device-transfer`, or `signal-crypto`.
- Breaking changes increment the second component.

Issue [#490](https://github.com/signalapp/libsignal/issues/490) (open, acknowledged): crates.io publication is requested and not done. Maintainer `jrose-signal` (2022-10-12): the project is not well-suited as a distro package; "we don't promise any support for use outside Signal"; "all APIs and implementations are subject to change without notice"; they "maintain protocol compatibility across our range of currently supported clients, but that's all we guarantee." Same issue (2022-11-03): publishing to crates.io is still only being considered; forks of `curve25519-dalek` and `boring` work against it. Status at review date: still open.

**Do not reinterpret source-available as supported third-party library.**

| Question | Evidence at v0.99.1 | Classification |
|---|---|---|
| Public/stable APIs | Java, Swift, TypeScript wrappers are the stated products; non-bridge Rust APIs get best-effort versioning | CONDITIONAL |
| Non-bridge Rust APIs | Versioned best-effort; not a support commitment | CONDITIONAL |
| JNI/C/Node bridges | Explicitly subject to change without notice | BLOCKED as a Veil ABI |
| Direct external Rust consumption | Not promised; crates.io unpublished; git/workspace consumption is how the repo is built | CONDITIONAL / unsupported |
| Versioning guarantees | 0.x; no stability promise; best-effort breaking-change numbers | BLOCKED as a stability guarantee |
| Official package model | Signal-internal Maven/NPM/iOS artifacts; not crates.io for `libsignal-protocol` | CONDITIONAL |
| Android support model | Artifacts and JNI build exist for Signal's own use | CONDITIONAL |
| Maintenance/update expectations | Frequent tagged releases; no third-party SLA | CONDITIONAL |

**Support-posture result: BLOCKED** as a supported third-party library. An explicit Veil "unsupported-upstream" maintenance plan could later move this to CONDITIONAL; no such plan exists yet.

## Protocol fit (specification vs exposed API)

Veil needs (ADR 005 / security-review entry criteria): one-to-one, asynchronous initiation, offline recipient, identity authentication, prekey lifecycle, forward secrecy, post-compromise security, Double Ratchet-style state, replay handling, bounded out-of-order delivery, session reset/replacement, safety-code/fingerprint verification, no group requirement, Android now, iOS later.

### Specifications

- [X3DH](https://signal.org/docs/specifications/x3dh/): published asynchronous prekey agreement.
- [PQXDH](https://signal.org/docs/specifications/pqxdh/): published post-quantum extended DH agreement used by current Signal clients.
- [Double Ratchet](https://signal.org/docs/specifications/doubleratchet/): published per-message ratchet, skipped-key storage, FS/PCS.
- [Sesame](https://signal.org/docs/specifications/sesame/): published multi-device session management.

### Library API actually exposed at v0.99.1 (`libsignal-protocol`)

Public crate surface (`rust/protocol/src/lib.rs`) includes, among other items:

- Identity: `IdentityKey`, `IdentityKeyPair`
- Addresses: `ProtocolAddress`, `DeviceId`
- Prekeys/session records: `PreKeyBundle`, `PreKeyRecord`, `SignedPreKeyRecord`, `KyberPreKeyRecord`, `SessionRecord`
- Handshake/session: `process_prekey`, `process_prekey_bundle`, `message_encrypt`, `message_decrypt`, `message_decrypt_prekey`, `message_decrypt_signal`
- Fingerprints: `Fingerprint`, `DisplayableFingerprint`, `ScannableFingerprint`
- Stores: `IdentityKeyStore`, `PreKeyStore`, `SignedPreKeyStore`, `KyberPreKeyStore`, `SessionStore`, `SenderKeyStore`, `ProtocolStore`
- Group/sender-key APIs (`group_encrypt` / `SenderKeyRecord`) exist but are **out of Veil V1 scope**

Module comments and implementation distinguish:

| Mechanism | Spec | v0.99.1 library behavior |
|---|---|---|
| X3DH | Published | Incoming prekey messages with pre-Kyber version fail: `"X3DH no longer supported"` (`session.rs`) |
| PQXDH | Published | Implemented in `pqxdh.rs` (4 EC DH + 1 ML-KEM encaps/decaps); used to initialize ratchet keys |
| Double Ratchet | Published | Present as `double_ratchet.rs`; combined with SPQR in `triple_ratchet.rs` |
| Triple Ratchet / SPQR | Library implementation over git dependency `spqr` tag `v1.5.3` | Encrypt/decrypt mix Double Ratchet message keys with Sparse Post-Quantum Ratchet state |
| Sesame-like session management | Published Sesame spec | `session_management.rs` documents "Sesame session management": trial-decrypt current then previous sessions, promote on success |

`pqxdh.rs` is `pub mod`, but the handshake types used for ratchet init are largely crate-internal; the application-facing 1:1 path is `process_prekey_bundle` / `process_prekey` plus `message_encrypt` / `message_decrypt`.

Fingerprint API exists and includes official-looking test vectors in `fingerprint.rs` (displayable 60-digit string; scannable protobuf). Stable-ID input is application-chosen; Signal's own tests use phone-number strings. Veil must not copy that identifier model; safety-code **derivation inputs remain an IMPORTANT Veil open question**.

**Protocol-family fit remains GOOD.** This does not approve the library.

## Rust integration paths (not implemented)

Phase 1D ownership (ADR 014): Kotlin owns Android platform behavior; Rust owns future approved security logic via UniFFI (`veil-ffi` → `veil-core`).

| Path | Description | Ownership | Support posture | Duplicate native | Build / NDK | iOS later | Persistence | Footprint / maintenance |
|---|---|---|---|---|---|---|---|---|
| **A** | `veil-core` consumes non-bridge `libsignal-protocol` (git pin of `97801d2`, not crates.io) | Matches ADR 014 | Unsupported third-party; best-effort non-bridge versioning | Avoids second JNI `.so` if protocol crate is linked into `libveil_ffi.so` | Must absorb Signal git forks (`curve25519-dalek`, `boring` workspace patch, `spqr`) and possible nightly-only JNI flags **if** other crates are pulled | Same Rust crate can later feed iOS | Veil implements store traits in Rust | High: git pin, forks, frequent 0.x breaks |
| **B** | Kotlin calls Signal Java/Android API (`libsignal-client` + `libsignal-android`) | Puts session crypto on the Kotlin side; contradicts ADR 014 | Packages published for Signal's own use | Adds `libsignal_jni*.so` beside `libveil_ffi.so` | Signal Android NDK pin `28.0.13004108` vs Veil `29.0.14206865`; JDK 21 for Signal Java vs Veil Java 17 bytecode | Would need the Swift product later, not one Rust core | Persistence would live in Android stores | High: two native runtimes, two upgrade clocks |
| **C** | Consume Signal JNI/C/Node/Swift bridge as a second FFI | Extra ABI under Veil | Bridges "subject to change without notice" | Duplicate or replace UniFFI | Nightly `-Z unstable-options --artifact-dir` in `build_jni.sh` | Bridge-per-platform | Unclear ownership | BLOCKED as a Veil ABI |

No path is supportable for implementation in this phase.

If a later phase is unblocked, **Path A is the only path consistent with Veil's Rust ownership boundary**. Path B and Path C are rejected as integration designs even if legal review later permitted linking.

## Rust toolchain compatibility

Veil pin: **Rust 1.88.0** (`rust/rust-toolchain.toml`). Phase 1E does not change it.

libsignal v0.99.1:

- `rust-toolchain`: **`nightly-2026-07-15`** (README: cargo downloads this nightly automatically)
- `[workspace.package] rust-version = "1.88"`
- `libsignal-protocol` crate: `edition = "2024"`, `rust-version = "1.85"`
- CI (`build_and_test.yml` at this tag) runs Rust jobs on **nightly and stable**, with stable taken from workspace `rust-version` (`1.88`)
- Fuzz targets are checked on that stable MSRV toolchain because they lack lockfiles
- JNI Android build uses `cargo build -Z unstable-options --artifact-dir` (`java/build_jni.sh`) — nightly cargo feature

**Coexistence with Veil 1.88.0:** plausible for Path A if only `libsignal-protocol` (and required path deps) are compiled on stable 1.88.0. **Not verified by a Veil build in this phase.** Full-repo / official JNI artifact production uses pinned nightly.

**Required future migration:** none is proven necessary for Path A against 1.88.0; none is proven unnecessary. Treat compile-on-1.88.0 as an unblock condition, not a current PASS.

## Android build compatibility

Veil: `minSdk 26`, NDK `29.0.14206865`, ABIs `arm64-v8a` + `x86_64`, UniFFI/JNA, Java 17 bytecode.

libsignal v0.99.1 Android (`java/android/build.gradle`, `java/build_jni.sh`):

- `minSdkVersion 23`, `compileSdk 34`, `targetSdkVersion 33`
- `ndkVersion = '28.0.13004108'`
- Officially supported JDK for Android builds: **JDK 21** (`.tool-versions`: `java openjdk-21.0.2`)
- Native ABIs built by default `android` target: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`
- `ANDROID_MIN_SDK_VERSION=23` in the NDK clang triple
- Packaging: JNI `.so` into `java/android/src/main/jniLibs`; AAR `libsignal-android` plus `libsignal-client`
- README: Windows/macOS libs inside `libsignal-client` must be excluded from Android APKs; `libsignal_jni_testing.so` can be excluded if unused

`minSdk 23` is **lower** than Veil 26, so it does not force Veil downward. NDK 28 vs Veil 29 is an integration risk if Path B ships Signal-built JNI next to Veil's `cargo-ndk` 29 artifacts. Path A linking into `libveil_ffi.so` would use Veil's NDK 29; that compile is not done here.

`java/code_size.json` records per-tag JNI/Android library size. Latest recorded row at this tag is **`v0.99.0` = 8,450,440 bytes** (~8.1 MiB). **`v0.99.1` is not yet in that file.** This is upstream's own size ledger for Signal's Android JNI library, not a measured Veil APK delta. Path A (protocol crate only) would not automatically equal that number.

Maven artifacts are documented as **for Signal's own use**. They are not an upstream support channel for Veil.

## Persistence / crash consistency

Mandatory stores Veil would have to implement (`rust/protocol/src/storage/traits.rs`):

| Store | Material |
|---|---|
| `IdentityKeyStore` | Local `IdentityKeyPair`; `get_local_registration_id()` `u32` that "should not change run over run" and should be regenerated if the same device unregisters and registers again; peer `IdentityKey` by `ProtocolAddress`; `is_trusted_identity` for Sending/Receiving |
| `PreKeyStore` | One-time EC prekeys (`PreKeyRecord`); `remove_pre_key` after successful prekey decrypt |
| `SignedPreKeyStore` | Signed EC prekeys |
| `KyberPreKeyStore` | Signed Kyber/ML-KEM prekeys. libsignal "makes no distinction between one-time and last-resort pre-keys" in the store type. `mark_kyber_pre_key_used` must delete one-time Kyber keys; last-resort keys "should not immediately be deleted" and must error if the same prekey combination is reused with the same base key |
| `SessionStore` | One `SessionRecord` per `ProtocolAddress` (Double Ratchet / Triple Ratchet state, including skipped keys and archived sessions) |
| `SenderKeyStore` | Group sender keys; **not required for Veil V1 1:1** |

In-memory reference implementations exist (`InMem*`). They are not a crash-safe persistence design.

Encrypt ordering (`message_encrypt`):

1. `load_session`
2. Ratchet encrypt (in-memory chain/SPQR advance)
3. `is_trusted_identity(Sending)` — may refuse
4. `save_identity`
5. `store_session`
6. Return ciphertext

Decrypt prekey ordering (`message_decrypt_prekey`):

1. `load_session` (or fresh record)
2. `process_prekey` (may create session; identity save is deferred)
3. Decrypt inner `SignalMessage`
4. `save_identity`
5. `mark_kyber_pre_key_used` and/or `remove_pre_key`
6. `store_session`
7. Return plaintext

Decrypt signal ordering (`message_decrypt_signal`):

1. `load_session`
2. Trial-decrypt current then previous sessions; promote on previous-session success (Sesame)
3. `is_trusted_identity(Receiving)` **after** decryption
4. `save_identity`
5. `store_session`

Library **does not expose a transaction API**. Store methods are independent async callbacks. Atomicity, if required, is an application property of the `*Store` implementations.

Observed crash implications from that ordering (not extra undocumented magic):

- **Crash after in-memory encrypt, before `store_session`:** chain is not persisted; retry would reuse the same sending-chain index / message-key derivation inputs. The library does not document a safe retry token. This is an application blocker until Veil wraps persist-before-send.
- **Crash after `store_session`, before the ciphertext is handed to the network:** that message key is consumed; the message may never be sent. Application must decide resend vs new encrypt.
- **Crash after successful decrypt, before `store_session`:** retry can decrypt again (message key not yet consumed on disk). Duplicate application delivery is possible unless Veil persists plaintext+session atomically.
- **Crash after `store_session` on decrypt, before the app records plaintext:** retry fails (key consumed). Plaintext can be lost unless the app persisted it in the same atomic unit.
- **Crash between `remove_pre_key` / `mark_kyber_pre_key_used` and `store_session`:** prekey inventory and session record can diverge.

`OutgoingTripleRatchet::from_session_state` **moves** PQ ratchet state out of `SessionState`; caller must `apply_to_session_state` or discard. That is an in-memory ownership contract, not disk atomicity.

**Unknowns that remain blockers:** whether Veil can implement atomic multi-store transactions on Android (SQLCipher/SQLite + Keystore) that cover identity, prekeys, and session records together; exact duplicate/replay behavior after each crash cut; whether retrying encrypt can reuse sensitive sending state. The library does not document a complete crash matrix. Do not invent one.

## Resource bounds

Constants in `rust/protocol/src/consts.rs` at v0.99.1 (library defaults, not application-settable APIs found in this review):

| Bound | Value | Notes |
|---|---|---|
| `MAX_FORWARD_JUMPS` | 25_000 | Skip-ahead limit |
| `MAX_MESSAGE_KEYS` | 2_000 | Skipped/stored message keys |
| `MAX_RECEIVER_CHAINS` | 5 | Concurrent receiver chains |
| `ARCHIVED_STATES_MAX_LENGTH` | 40 | Archived session states per record |
| `MAX_SENDER_KEY_STATES` | 5 | Group sender-key; out of V1 1:1 scope |
| `MAX_UNACKNOWLEDGED_SESSION_AGE` | 30 days | Stale unacknowledged prekey sessions refuse send |

Application-controlled (not library-enforced as a global cap in the reviewed files):

- Number of `ProtocolAddress` sessions (one record per address; Veil would cap peers/devices)
- Prekey inventory size and rotation
- When to delete last-resort Kyber prekeys after `mark_kyber_pre_key_used`
- Message plaintext size (no explicit `message_encrypt` size cap found in the reviewed public API)

CPU/memory cost of malformed input: not specified as an application-configurable budget. `MAX_FORWARD_JUMPS` / `MAX_MESSAGE_KEYS` bound some skip work. **Malformed-input processing cost remains incompletely established** for Veil's integration gate.

## Test / review evidence

Available at v0.99.1 (library testing, not Veil integration review):

- `TESTING.md`: `cargo test --workspace --all-features`; Java `./gradlew client:test server:test android:connectedAndroidTest`
- Protocol crate tests: `rust/protocol/tests/{session,ratchet,sealed_sender,groups}.rs` plus unit tests in modules (including fingerprint vectors)
- Fuzz targets under `rust/protocol/fuzz/fuzz_targets/`: `interaction.rs`, `session_management.rs`, `sealed_sender_v2.rs`; `cargo fuzz` README
- CI tests Rust nightly and stable
- Fingerprint tests cite Java `testVectorsVersion1` / `testVectorsVersion2`
- PQXDH / Double Ratchet **specifications** include protocol analysis; that is not a library audit of `97801d2`

Not found in reviewed official sources:

- An independent public audit report scoped to libsignal **v0.99.1**
- A Veil application-level review of storage, identity binding, or prekey lifecycle

**Separate:** protocol analysis ≠ library testing ≠ independent library review ≠ Veil application integration review. Signal's own use does not make Veil "audited."

## Security advisories

- `SECURITY.md` at v0.99.1: report to `security@signal.org`; no product CVE list in that file
- GitHub Security Advisories API for `signalapp/libsignal` on 2026-08-19: **empty list**
- Release tag notes for v0.99.1 do not disclose a protocol/session vulnerability fix

**NONE FOUND IN REVIEWED SOURCES** for unresolved critical/high issues in the intended 1:1 session/prekey/identity/Android/Rust-protocol components of v0.99.1.

This is not a claim that no vulnerabilities exist.

## Licensing inventory (not a legal opinion)

- `LICENSE` at v0.99.1 is the GNU Affero General Public License version 3, 19 November 2007
- `Cargo.toml` `[workspace.package] license = "AGPL-3.0-only"`
- README "Legal things": Copyright 2020-2026 Signal Messenger, LLC; "Licensed under the GNU AGPLv3"
- Java POM metadata names the license `AGPLv3`

Open official issues requesting an AGPL §7 App Store additional permission, **still open** at review date:

- [#677](https://github.com/signalapp/libsignal/issues/677) (created 2026-06-08)
- [#684](https://github.com/signalapp/libsignal/issues/684) (created 2026-07-10; labeled `acknowledged` 2026-08-03)

Those issues do **not** change the license. The 2016 [Signal license-update post](https://signal.org/blog/license-update/) added an App Store additional permission to **`libsignal-protocol-c`**, not to current AGPL Rust `libsignal`.

Material distribution conclusions: **LEGAL REVIEW REQUIRED** for Android APK, Google Play, direct APK / F-Droid-style distribution, future iOS / App Store, source-offer obligations, linking / combined-work, and modification obligations. No final legal opinion is given here.

Veil currently has **no selected project license**. Adopting AGPL `libsignal` may constrain Veil's future license and store distribution. Status: **LEGAL REVIEW REQUIRED**. No license file is added in Phase 1E.

## Operational acceptance (design only)

Because libsignal is 0.x and releases frequently, a future integration would require, before any Cargo/Gradle add:

- Exact pin to tag `v0.99.1` / commit `97801d22dcf9f5bf714f7b8fa3212cdc973ae1c8` (git dependency; not crates.io)
- Lockfile review of Signal's `Cargo.lock` plus Veil's; treat Signal git forks as sensitive crypto-provider changes
- SBOM covering `libsignal-protocol`, `signal-crypto`, `libcrux-ml-kem`, `spqr`, patched `curve25519-dalek` / `boring`
- Provenance record: tag SHA, commit SHA, fetch date, advisory query date
- Advisory monitoring: GitHub Advisories, `SECURITY.md` contact, Signal release tags
- Upgrade cadence: no automatic major/breaking 0.x bumps; each upgrade reviews `RELEASE.md` / tag message, persistence compatibility, vectors, and this acceptance matrix
- Emergency security upgrade: allow patch tags after notes/advisory review; still no silent suite downgrade (security invariant 14)
- Rollback: keep previous pin and golden vectors; rolling back ratchet state on devices is generally unsafe if mixed versions already exchanged messages — treat rollback as "stop shipping / force client upgrade," not "revert session files"
- Compatibility testing: official/adopted vectors, crash-before/after persist, prekey exhaustion, identity replacement, Android ABI rebuild of `libveil_ffi.so`

This policy is not implemented in Phase 1E.

## OpenMLS v0.8.1 control comparison

Do not reopen ADR 005's protocol-family decision.

| Item | Evidence |
|---|---|
| License | MIT (`openmls/Cargo.toml`, `LICENSE`) |
| Implementation | Rust RFC 9420 library; crates.io-published crate `openmls` 0.8.1 |
| Tag | `openmls-v0.8.1` commit `47dbedecad0c1fd8eb5368d582250ebfcc1e1ce6` |
| Changelog | 2026-02-13: expose leaf/parent accessors; update libcrux/rust_crypto providers after Cryspen advisories |
| Android / iOS | README: `aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android`, `i686-linux-android`, `aarch64-apple-ios` **built on CI, not tested**. Tested targets remain desktop/server. |
| Advisories | GHSA-qr9h-x63w-vqfm (persistence/FS, patched 0.7.1); GHSA-8x3w-qj7j-gqhf (tag length, high, patched 0.7.2 / 0.8.0). 0.8.1 is after both patches. |
| Debug features | `crypto-debug` / `content-debug` still exist and must stay forbidden in production |
| V1 fit | Group/epoch/commit/KeyPackage/delivery-service model remains extra machinery for intentional 1:1; does not solve ADR 007 rendezvous |

No evidence was found that contradicts ADR 005's rejection of MLS for V1 two-party conversations. Easier MIT licensing does not select OpenMLS.

## What this refresh does not do

- Does not add `libsignal`, OpenMLS, or any crypto crate
- Does not generate identity keys, prekeys, or sessions
- Does not implement rendezvous or E2EE
- Does not change Veil's Rust 1.88.0 pin
- Does not select a Veil project license
