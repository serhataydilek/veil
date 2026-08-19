# ADR 015: secure-session library candidate (libsignal v0.99.1)

## Status

**TECHNICALLY PREFERRED CANDIDATE — BLOCKED PENDING EXTERNAL GATES.**

This is option B of the Phase 1E decision rule. libsignal v0.99.1 is not accepted for implementation and is not rejected as a protocol-family mismatch. No cryptographic dependency is added.

## Context

ADR 005 selected the Signal-style asynchronous ratchet **family**, not a library. ADR 010 requires a pinned candidate to pass security, integration, licensing, operational, and independent-review gates. Phase 1D established a UniFFI path so future approved session logic can live in Rust (`veil-core`), not Kotlin.

Phase 1E evaluates Signal `libsignal` tag **v0.99.1**, commit **`97801d22dcf9f5bf714f7b8fa3212cdc973ae1c8`**, against those gates. Evidence: `docs/review/secure-session-dependency-refresh-2026-08-19.md` and `docs/review/libsignal-v0.99.1-acceptance.md`. Research date: 2026-08-19.

OpenMLS v0.8.1 was refreshed only as a control. ADR 005 is not reopened.

## Threats / constraints

A relay can substitute public material, reorder/replay/drop ciphertext, and retain metadata. Devices crash. Veil must persist ratchet state so encrypt/decrypt cannot reuse consumed keys incorrectly. The selected API must fit Rust core and Android now, iOS later. Source availability is not support. AGPL obligations and store distribution are legal questions.

## Options considered

| Option | Outcome |
|---|---|
| A. Accept libsignal v0.99.1 for implementation | Rejected: licensing, independent review, unsupported third-party posture, and crash-atomicity gates are not satisfied |
| B. Prefer libsignal v0.99.1 technically, keep BLOCKED | Selected |
| C. Reject libsignal v0.99.1 | Rejected: protocol-family fit remains the strongest among screened candidates; failure is gate completeness, not 1:1 protocol mismatch |
| Consume Signal Java/Android APIs from Kotlin | Rejected as an integration design: contradicts ADR 014 ownership; artifacts are published for Signal's own use; would add a second JNI runtime |
| Consume Signal JNI/C bridges | Rejected: README at this tag says bridge layers are subject to change without notice |
| Select OpenMLS v0.8.1 instead | Rejected for V1 1:1: MIT/Rust/RFC 9420 remain attractive, but group/epoch/commit/KeyPackage semantics still do not match Veil V1; Android targets remain built-not-tested. Licensing ease is not a protocol-fit reason |

## Decision

Record **libsignal v0.99.1** as Veil's **technically preferred** secure-session library candidate and **do not implement it**.

Pin for any future re-evaluation:

- Upstream `https://github.com/signalapp/libsignal`
- Tag `v0.99.1`
- Commit `97801d22dcf9f5bf714f7b8fa3212cdc973ae1c8`

Do not depend on `main`. Do not add the crate to Cargo or Gradle until every ADR 010 gate passes for that pin (or a newly evaluated pin).

If implementation is ever unblocked, the only integration path consistent with ADR 014 is Path A: `veil-core` consumes the non-bridge `libsignal-protocol` crate (git pin), exported through the existing UniFFI boundary. That path is not supportable today.

ADR 005's protocol-family direction stands. This ADR does not generate identity keys, prekeys, sessions, or ciphertext.

### Unblock conditions (all required)

1. **LEGAL REVIEW REQUIRED** evidence covering AGPL-3.0-only obligations for Android APK, Google Play, direct APK / F-Droid-style distribution, future iOS / App Store, source-code offer, linking/combined-work, and modification. Open issues [#677](https://github.com/signalapp/libsignal/issues/677) and [#684](https://github.com/signalapp/libsignal/issues/684) must be treated as unresolved distribution questions, not as a license change.
2. **Independent application-level security review** of this pin (or a replacement pin) covering integration assumptions, storage, identity binding after rendezvous, prekey/session lifecycle, crash persistence, and resource bounds. AI analysis does not satisfy this gate.
3. An explicit Veil **unsupported-upstream support plan**, because the v0.99.1 README states use outside Signal is unsupported and `RELEASE.md` states 0.x does not promise stability.
4. A Veil **atomic persistence design** wrapping `IdentityKeyStore`, prekey stores, and `SessionStore` so encrypt/decrypt cannot commit ciphertext or plaintext without matching durable ratchet state. Library callbacks are not a transaction API. Remaining crash unknowns must be closed or explicitly accepted with tests.
5. **Operational machinery**: exact git pin, lockfile review, SBOM, provenance, advisory monitoring, upgrade cadence, emergency upgrade, and rollback policy implemented before the dependency is added.
6. **Compile verification** that the intended Path A crate set builds with Veil's pinned **Rust 1.88.0** without silently changing that pin. If a pin change is required, that is a separate ADR.
7. **Rendezvous remains independently BLOCKED** (ADR 007). Session implementation must not start solely because this library candidate is preferred.

A later tag may replace v0.99.1 only by repeating this process.

## Security consequences

Preferring this candidate does not enable E2EE. When unblocked, the library's current 1:1 path is PQXDH initiation plus Triple Ratchet (Double Ratchet + SPQR), Sesame-like multi-session trial decrypt, and application-owned trust (`is_trusted_identity`). X3DH is no longer accepted by this library version. Identity replacement remains a product/TOFU decision via the store, matching invariant 12.

## Privacy consequences

Prekey bundles and identity keys are application-published. They must stay behind completed rendezvous (ADR 005 / invariant 6). The library does not provide rendezvous. Fingerprint APIs exist; Veil's stable-id input for safety codes is still IMPORTANT and unset.

## Operational consequences

Git-pin only (not crates.io). Expect frequent 0.x tags. Treat every upgrade as a security release. Do not auto-merge libsignal updates.

## Residual risks

Unsupported third-party use. AGPL/store uncertainty. Crash-consistency left to Veil. Supply chain includes Signal git forks. Independent review of this commit is absent. Evidence ages; re-evaluate at any pin change.

## Implementation requirements

- No Cargo/Gradle add in this phase.
- No copied ratchet/handshake code.
- No handwritten JNI/unsafe to "wrap" libsignal.
- Production must keep debug crypto/content logging off.
- If later added: pin commit SHA, record this ADR, refresh gates.

## Tests required (after unblock, not now)

Official/adopted vectors; offline initiation; identity substitution; modified/replayed/duplicate envelopes; bounded reordering against `MAX_MESSAGE_KEYS` / `MAX_FORWARD_JUMPS`; crash-before/after persistence; prekey exhaustion/rotation/Kyber last-resort reuse; downgrade (including X3DH rejection); reset/key-change; UniFFI export of session operations.

## Open issues

All unblock conditions above. PQ policy is now coupled to this library's PQXDH + SPQR, not a separate invented suite. Safety-code identifier material remains IMPORTANT. iOS legal path is part of legal review, not an iOS implementation start.
