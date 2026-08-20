# Phase 1G local data

## Scope

Phase 1G adds an encrypted local conversation/message store and a fail-closed retention engine. It does not create protocol identity, contact capabilities, rendezvous, SPAKE2, libsignal, prekeys, secure sessions, network messaging, or E2EE.

## Storage

The database is platform SQLite (`SQLiteOpenHelper`) at app-private `veil-local.db`. Backup and data-extraction exclusions already cover the database domain. Schema v1:

| Table | Columns | Notes |
|---|---|---|
| `local_meta` | `meta_key`, `ciphertext` | Encrypted conservative time bound |
| `conversations` | `conversation_id`, `ciphertext` | Opaque local ID + encrypted shell |
| `messages` | `message_id`, `conversation_id`, `expiry_hint_ms`, `ciphertext` | Hint is an index only; FK cascade on conversation delete |

`PRAGMA foreign_keys = ON`, `PRAGMA secure_delete = ON`, journal mode DELETE (WAL is not enabled). `secure_delete` is not a forensic-erasure guarantee.

Unknown schema versions report incompatible/unavailable. There is no destructive migration.

## Encryption at rest

`AesGcmLocalRecordCipher` uses AES-GCM with the existing Phase 1B non-exportable AES-256 Keystore key. It does not call `provisioningKey()`. AAD is binary:

```text
UTF-8 "veil.local-record.v1"
record type discriminant (conversation=1, message=2, time-bound=3)
u16be local-id length + UTF-8 local id
u8 format version
```

Stored blobs use the VLR1 envelope (magic, version, 12-byte IV, bounded ciphertext). Maximum ciphertext is 128 KiB. That bound is a local persistence defense, not a protocol or network message-size policy.

Inner plaintext codecs are `VLC1` (conversation), `VLM1` (message), and `VLT1` (time bound). Parsers reject unknown versions and trailing bytes.

## Retention

Production initialization loads `RustCoreBridge` / `core_policy_snapshot()`. Only `AVAILABLE` with a valid `maxMessageAvailabilitySeconds` proceeds. Kotlin production code does not contain a 24-hour constant.

Effective deadline is the earliest of authenticated expiry and optional relay deadline, never later than `createdAt + maxMessageAvailability`. Replay/lifecycle updates cannot extend an accepted deadline. Expired plaintext is never returned.

`AndroidRetentionClock` is the only production wall/elapsed/boot boundary. On API 26+ it reads `Settings.Global.BOOT_COUNT`. Repositories and UI do not call `System.currentTimeMillis()` or `SystemClock.elapsedRealtime()` directly.

## Startup barrier

```text
ProtectionStatus READY
  → load Rust retention policy
  → open local DB (existing Keystore key only)
  → load/update conservative time bound
  → transactional expiry sweep
  → LocalDataStatus READY
  → message-capable UI may render
```

CHECKING/PURGING show a neutral wait. KEY_UNAVAILABLE, CORRUPT_OR_UNREADABLE, POLICY_UNAVAILABLE, INCOMPATIBLE, and ERROR do not render retained messages. READY requires a confirmed persisted conservative time bound.

Crypto failures are classified before any deletion:

- Authenticated record corruption (malformed VLR1, AEAD/tag failure, payload/ID/expiry mismatch) may delete the affected row.
- Key or provider unavailability (`ProviderException`, invalid/unavailable Keystore key, incomplete provider operation) preserves ciphertext, does not purge messages, does not provision a replacement key, and surfaces `KEY_UNAVAILABLE`.

Home remains “No conversations”. Production UI does not create ACTIVE conversations, persist drafts, or expose a fake send path.

## Deletion boundary

`LocalConversationRepository.destroy` removes messages and the relationship row. Reset deletes messages and may keep the shell. `LocalDataWiper.deleteDatabase` deletes `veil-local.db` for a future identity/local-storage destruction path. Phase 1B key-loss semantics are unchanged: missing or unavailable keys do not regenerate, do not delete encrypted message rows, and are not classified as time-bound corruption. Missing or authenticated-corrupt time metadata with existing messages still purges message rows early.
