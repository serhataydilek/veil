# ADR 017: local message storage and retention

## Status

Accepted for Phase 1G local persistence. This does not activate Veil protocol identity, rendezvous, secure sessions, networking, or end-to-end message encryption.

## Context

Phase 1B provides a non-exportable Android Keystore AES-256 local-protection key and a tiny VLP1 protected-state file. Phase 1D exports `maxMessageAvailabilitySeconds` from `veil-core` through `RustCoreBridge`. Product policy (ADR 004, ADR 009, security invariant 8) requires fail-closed bounded availability, no persisted unsent drafts, and a startup purge before message UI can render.

Phase 1G needs an auditable on-device store for conversation shells and ephemeral text without expanding the dependency or protocol-crypto surface.

## Decision

- Use platform `SQLiteOpenHelper` / `SQLiteDatabase` with the app-private file `veil-local.db`. Do not add Room, SQLCipher, or other database/crypto libraries.
- Encrypt each conversation, message, and conservative-time record with `AES/GCM/NoPadding`, the existing Phase 1B Keystore key (`veil.local-protection.v1`), a provider-generated 12-byte IV, and a 128-bit tag.
- Do not reuse `AesGcmProtectedBlobCipher` or its AAD `veil.local-state.v1`. Local records use `AesGcmLocalRecordCipher` with domain-separated AAD `veil.local-record.v1` plus record type, local ID, and format version. Conversation, message, and time-bound records never share indistinguishable AAD.
- The database must not provision or replace the local-protection key. Access requires `ExistingKeyResult.Available`. Missing, unavailable, or provider-failed keys make local data unavailable. `ProtectedStateStore.provision()` remains the only provisioning path.
- Store opaque local conversation IDs and encrypted payloads. Store opaque local message IDs, conversation IDs, an untrusted plaintext expiry index hint, and an encrypted authenticated payload. The hint is never the security deadline; a mismatch with authenticated metadata fails closed and deletes the row.
- Keep `PRAGMA foreign_keys = ON` and `PRAGMA secure_delete = ON`. Do not enable WAL; use DELETE journal mode. `secure_delete` is best-effort and does not guarantee forensic erasure.
- Rust `maxMessageAvailabilitySeconds` is the production maximum-retention authority. Kotlin must not duplicate a 24-hour policy constant. Unavailable, incompatible, or invalid Rust policy fails closed and does not substitute 24 hours.
- Persist an encrypted conservative time lower bound (`wallLowerBoundMs`, elapsed realtime at observation, boot observation). Same-boot elapsed time advances the bound if wall time rolls back. Wall jumps forward may expire early. New or ambiguous boots, and missing/corrupt time state while messages exist, purge message rows early without destroying conversation shells.
- Purge expired message content before `LocalDataStatus.READY`. CHECKING/PURGING UI is a neutral wait. Failure statuses do not render retained messages or leak counts/aliases.
- Composer text stays in process memory. No plaintext draft persistence. DESTROYED conversations are removed after cleanup rather than retained as a tombstone graph.
- Unknown database schema versions fail closed without destructive migration.

## Consequences

Local conversation/message continuity becomes possible after process death for valid unexpired rows only. Pairing, ACTIVE production conversations, send, rendezvous, and E2EE remain blocked. SQLite pages, journals, snapshots, and flash wear-leveling can retain remnants; Veil must not claim forensic deletion. Device/key loss still makes encrypted rows unreadable without key regeneration.

## Residual risks

An unlocked or malware-compromised device can copy plaintext before expiry. Platform clocks are not a proof of genuine human creation time. Indexed expiry hints can be tampered; reads authenticate and delete on mismatch rather than trusting the index.
