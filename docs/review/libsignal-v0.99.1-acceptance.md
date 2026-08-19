# libsignal v0.99.1 acceptance gates

Pinned candidate: Signal `libsignal` tag **v0.99.1**, commit **`97801d22dcf9f5bf714f7b8fa3212cdc973ae1c8`**.

This evaluation applies `docs/review/dependency-acceptance-gates.md` and ADR 010. Evidence detail lives in `docs/review/secure-session-dependency-refresh-2026-08-19.md`. Access date: **2026-08-19**.

This document creates **no approval**. AI analysis does not satisfy legal review or independent application review.

## Candidate identity

| Field | Value |
|---|---|
| Upstream | <https://github.com/signalapp/libsignal> |
| Tag | `v0.99.1` |
| Commit | `97801d22dcf9f5bf714f7b8fa3212cdc973ae1c8` |
| License (file + Cargo) | AGPL-3.0-only |
| Intended Veil use | Future Rust-owned 1:1 session via `libsignal-protocol` (not added) |
| Not in scope | Groups, sealed sender as a product feature, zkgroup, SVR, usernames, device transfer, Signal servers |

## Gate: Security

**Result: CONDITIONAL**

| Check | Result | Evidence |
|---|---|---|
| Maintained upstream | PASS | Active tagged releases; v0.99.1 published 2026-07-23; workspace used by official Signal clients |
| Unresolved critical/high advisory on intended use | PASS for this query | GitHub Security Advisories API for `signalapp/libsignal` returned `[]` on 2026-08-19; tag notes do not list a session-crypto fix. **NONE FOUND IN REVIEWED SOURCES.** Not a claim that none exist. |
| Protocol / version fit | PASS for family | PQXDH + Double Ratchet / Triple Ratchet + Sesame-like session management at this tag match ADR 005's 1:1 async needs. X3DH is rejected by the library (`"X3DH no longer supported"`). |
| Audit / review evidence understood | CONDITIONAL | Protocol specs are public. Library has unit tests, workspace tests, and `cargo fuzz` targets. No independent review of **this** commit for Veil's integration was found. Signal's own production use is not a Veil audit. |
| Update process defined | CONDITIONAL | Designed in the 2026-08-19 refresh (pin/lock/SBOM/advisory/rollback). Not implemented. 0.x and "no stability promise" (`RELEASE.md`) make upgrades security events. |
| Debug plaintext/key logging disabled | CONDITIONAL | JNI builds strip debug-level logs unless `--debug-level-logs`. Protocol crate uses `log`. Veil production policy already forbids content/key logging; must be enforced again at integration. |
| Upstream support for third-party use | BLOCKED | README: "Use outside of Signal is unsupported." |

**Unknowns:** zero-days; SPQR (`spqr` git tag `v1.5.3`) and patched `curve25519-dalek` / `boring` advisory status beyond the libsignal repo advisory list; whether Triple Ratchet / PQXDH at this exact commit has a public third-party review.

**Required action:** independent application-level review of v0.99.1 as integrated; explicit Veil plan for unsupported upstream; implement advisory monitoring before adding the crate.

**Owner / reviewer type:** security reviewer (external) + Veil maintainer for operational monitoring.

## Gate: Integration

**Result: CONDITIONAL** (incomplete until crash-atomic stores and 1.88.0 compile are proven)

| Check | Result | Evidence |
|---|---|---|
| Viable Android path | CONDITIONAL | Signal Android JNI/AAR exists (minSdk 23, ABIs including `arm64-v8a` and `x86_64`) **for Signal's own use**. Veil Path A would compile protocol code into `libveil_ffi.so` with NDK 29 — **not built this phase**. Path B duplicates JNI and violates Rust ownership. |
| Rust boundary understood | CONDITIONAL | Path A matches ADR 014. Direct crates.io consume is unavailable (issue #490 open; crates.io `libsignal-protocol` is an unrelated 2019 third-party crate). Git pin of this tag would be required. |
| Persistence and crash consistency compatible | BLOCKED / incomplete | Stores are documented. **No transaction API.** Encrypt/decrypt persist after in-memory ratchet advance. Crash-before-write / crash-after-write / retry-reuse behavior is not fully specified by upstream as a matrix. Unknowns remain blockers (ADR 010). |
| Offline prekey / session lifecycle fit | CONDITIONAL | `PreKeyBundle`, signed + Kyber prekeys, one-time EC removal, last-resort Kyber reuse check, 30-day unacknowledged session age. Prekey **distribution** is application/relay work, not provided by the library. |
| Bounded CPU / memory / storage | CONDITIONAL / incomplete | Library constants: `MAX_MESSAGE_KEYS=2000`, `MAX_FORWARD_JUMPS=25000`, `MAX_RECEIVER_CHAINS=5`, `ARCHIVED_STATES_MAX_LENGTH=40`. Session-count-per-peer, prekey inventory, and message-size caps are application-owned. Malformed-input CPU budget not established. |
| Usable official / adopted vectors | CONDITIONAL | Fingerprint test vectors and protocol crate tests/fuzzers exist. Veil still needs integration vectors (ADR 005 tests required) after any future pin. |
| Toolchain coexistence with Rust 1.88.0 | CONDITIONAL | Workspace `rust-version = "1.88"`; protocol crate edition 2024 / rust-version 1.85; CI tests stable 1.88. Pinned nightly `nightly-2026-07-15` is used for the full repo / JNI `-Z` artifact-dir build. Veil pin unchanged; compile not verified. |

**Unknowns:** Path A compile on exact `rustc 1.88.0` with Veil's `forbid(unsafe_code)` crate graph; NDK 29 vs Signal NDK 28 if any C/C++ (`boring`) code is pulled; APK size for a protocol-only link (upstream JNI ledger shows ~8.1 MiB for `v0.99.0` full Android JNI, which is the wrong artifact for Path A).

**Required action:** design atomic store transactions; verify 1.88.0 compile without changing Veil's pin; keep Path B/C rejected.

**Owner / reviewer type:** Veil systems/Android engineer + protocol implementer; security reviewer on crash model.

## Gate: Licensing

**Result: BLOCKED**

| Check | Result | Evidence |
|---|---|---|
| License and attribution documented | PASS as inventory | `LICENSE` = AGPLv3 text; Cargo `AGPL-3.0-only`; README copyright 2020-2026 Signal Messenger, LLC |
| Distribution obligations understood | BLOCKED | **LEGAL REVIEW REQUIRED.** This review only inventories: source offer, network-interactive AGPL §13, combined-work/linking, modification. No opinion. |
| Android distribution reviewed | BLOCKED | **LEGAL REVIEW REQUIRED** for Play and direct APK / F-Droid-style. Open issues #677 and #684 discuss store-term tension; they do not amend the license. |
| Future iOS implications | BLOCKED | Same issues request an AGPL §7 App Store additional permission that `libsignal-protocol-c` received in 2016. Current `libsignal` LICENSE at v0.99.1 has **no** such extra permission. Issues remain open. |
| Veil project license impact | BLOCKED | Veil has no selected license. Adopting AGPL libsignal **may constrain** later choices. **LEGAL REVIEW REQUIRED.** No license file added. |

**Unknowns:** whether a future Signal additional permission will exist; how AGPL combined-work analysis applies to UniFFI-linked `libveil_ffi.so` in an APK — both are legal questions.

**Required action:** qualified legal review covering Android APK, Play, direct/F-Droid, future App Store, source obligations, linking, and modification. Do not treat #677/#684 as a license change.

**Owner / reviewer type:** legal counsel. Not an AI assessment.

## Gate: Operational

**Result: CONDITIONAL**

| Check | Result | Evidence |
|---|---|---|
| Exact version / lock policy | CONDITIONAL | Pin specified: tag `v0.99.1` / commit `97801d2`. Git dependency required. Not added to Veil lockfiles. |
| Upgrade and advisory monitoring | CONDITIONAL | Process designed in the refresh doc. Not implemented. Frequency of 0.x tags is high; each bump is a security review. |
| Reproducibility plan | CONDITIONAL | Upstream `Cargo.lock` exists. Signal git patches (`curve25519-dalek`, `boring`) must be pinned by tag. |
| SBOM | CONDITIONAL | Required before integration; not generated. |
| Transitive dependency review | CONDITIONAL | Incomplete: `libcrux-ml-kem`, `spqr` v1.5.3, patched dalek/boring, `prost`, `rand` 0.9, etc. listed in workspace `Cargo.toml` but not fully SBOM'd. |
| Incident response / rollback | CONDITIONAL | Designed: pin rollback of **shipping** is possible; rollback of **already exchanged sessions** is generally unsafe. |

**Unknowns:** Signal's unpublished-crate supply chain (git tags vs crates.io); how quickly Veil could rebuild Android native after an emergency tag.

**Required action:** implement pin/lock/SBOM/advisory/rollback tooling before any Cargo add.

**Owner / reviewer type:** Veil maintainer + release owner.

## Gate: Independent review

**Result: BLOCKED**

| Check | Result | Evidence |
|---|---|---|
| External review covers this version | BLOCKED | None performed for Veil × v0.99.1 |
| Integration assumptions | BLOCKED | UniFFI + application stores + no Signal servers not reviewed |
| Storage / identity binding / prekey / session lifecycle | BLOCKED | Library APIs identified; Veil mapping not reviewed |
| Changed threat model | BLOCKED | Adding PQXDH/Triple Ratchet/SPQR and registration IDs would change the threat model and requires review when implemented |

Documentation and AI-generated analysis do **not** replace this gate (`docs/review/security-review-entry-criteria.md`).

**Required action:** independent cryptographic/application review scoped to the pinned commit, Veil stores, identity binding after rendezvous, prekey publication scope, crash persistence, and resource bounds.

**Owner / reviewer type:** independent security reviewer (external). Not the implementing agent.

## Mandatory-gate summary

| Gate | Result |
|---|---|
| Security | CONDITIONAL |
| Integration | CONDITIONAL (crash-consistency incomplete) |
| Licensing | BLOCKED |
| Operational | CONDITIONAL |
| Independent review | BLOCKED |

A cryptographic dependency is approved only if **every** mandatory gate passes. They do not.

**Overall: not accepted for implementation.**

## Integration-path recommendation

| Path | Implementation supportability |
|---|---|
| A. `veil-core` consumes non-bridge `libsignal-protocol` | Not supportable now. Only technically consistent path **if** all gates later pass. |
| B. Kotlin / Signal Android Java API | Not supportable: Signal-own-use packages; splits crypto out of Rust; duplicate JNI. |
| C. Signal JNI/C/Node bridge | Not supportable: bridges "subject to change without notice." |

**Recommended implementation path today: none. Status BLOCKED.**

## Decision rule application

LEGAL REVIEW evidence is absent. Independent application review is absent. Upstream third-party support is explicitly denied. Crash-atomicity is not provided by the library.

Correct status: **TECHNICALLY PREFERRED CANDIDATE — BLOCKED PENDING EXTERNAL GATES.**
