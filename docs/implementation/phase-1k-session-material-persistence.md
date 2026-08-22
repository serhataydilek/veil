# Phase 1K session-material persistence foundation

## Scope

Phase 1K is a storage foundation only. It adds encrypted, opaque security-material persistence and atomic mutation among those records. It does not create a production session, identity, prekey, security record, protocol adapter, network behavior, or UI.

## Schema and migration

`veil-local.db` schema v2 adds:

| Table | Columns | Meaning |
|---|---|---|
| `security_records` | `owner_id`, `slot_id`, `ciphertext` | Composite primary key and one encrypted record blob |

The v1 → v2 upgrade is a transaction that only creates `security_records`; it preserves existing `local_meta`, `conversations`, and `messages` rows. Downgrades and unknown future schemas remain incompatible/fail-closed. The table stores ciphertext only: payload plaintext is neither a column nor an index, and owner/slot are the minimum lookup metadata needed for the composite key.

## Bounds and encoding

Owner and slot IDs are nonempty ASCII letters, digits, `-`, or `_`, each at most 64 UTF-8 bytes. Payloads are 1 through 65,536 bytes. These bounds cap allocations before codec encoding.

The security-material AAD is exactly:

```text
UTF-8 "veil.local-record.v1"
u8 SECURITY_MATERIAL discriminant (4)
u16be owner UTF-8 byte length + owner bytes
u16be slot UTF-8 byte length + slot bytes
u8 local-record format version (1)
```

The inner plaintext codec is `VLSM`:

```text
ASCII "VLSM" | u8 version (1) |
u16be owner length | owner bytes |
u16be slot length | slot bytes |
u32be payload length | payload bytes
```

It rejects invalid IDs, empty/oversized payloads, unknown versions, malformed lengths, owner/slot mismatches, and trailing bytes. The encrypted outer record remains the existing bounded VLR1 envelope.

## Encryption and failure boundary

Records reuse the existing Phase 1B non-exportable AES-256-GCM Keystore key; no second key is created and this path never calls `provisioningKey()`. The existing cipher obtains a fresh provider IV per encryption and VLR1 authenticates the ciphertext and AAD.

Underlying local-record crypto distinguishes authenticated corruption from key/provider unavailability. Both result in no security payload being returned by this dormant repository; neither path regenerates a key or deletes ciphertext. The existing startup/protection boundary remains responsible for fail-closed `KEY_UNAVAILABLE` handling before any normal local-store session is available.

## Transactions

`SecurityMaterialRepository.transaction { ... }` exposes `get`, `put`, and `delete` over one `SqliteLocalRecordStore` and one real SQLite transaction. Successful changes commit together; a callback, encryption, or write failure propagates and rolls back. Reads within that transaction observe its writes. Nested security-material transactions are rejected, so they cannot create an independent transaction or commit outer work accidentally. The in-memory test repository mirrors rollback and copy semantics.

The persistence-failure seam is internal and test-only: it runs after SQLite has accepted a security-record write, proving rollback after an earlier actual SQL mutation. It is not production API or a runtime debug flag.

Tests cover callback rollback, replacement and deletion restoration, new-row removal, success commits, read-your-writes, encryption failure after a prior SQL mutation, write failure after a prior SQL mutation, nested rejection, and `INSERT OR REPLACE` isolation.

## Durability and cross-domain limit

Phase 1K does not change the reviewed SQLite configuration: WAL remains disabled (`journal_mode=DELETE`), foreign keys are enabled, and `secure_delete` is enabled. `synchronous` is not explicitly configured here and therefore remains the platform SQLite default. SQLite transactions provide atomic commit/rollback under the platform's SQLite durability behavior; this is not a guarantee against every filesystem, storage-controller, process, or power-loss failure. `secure_delete` is not forensic erasure.

Supported now: atomic mutations among opaque security-material records. Not yet supported: atomic composition with conversation/message state, actual encrypt/decrypt plus ratchet-state persistence, or libsignal callback/store integration. The existing local store transaction is the database-scoped seam, but Phase 1K deliberately does not combine those domains. It narrows the crash-atomic storage blocker in ADR 015; it does not satisfy the full ADR 015 gate.

## Wipe, reset, and dormancy

`LocalDataWiper.deleteDatabase` removes the entire database and therefore `security_records` with it. Conversation destroy/reset operates on conversation/message rows only; it does not infer ownership of arbitrary security material. Protocol-level owner/slot lifecycle and deletion mapping are deferred.

The repository is intentionally dormant: startup, Home, and Settings do not create or read security records, no sentinel or placeholder is added, and no payload logging or telemetry is introduced.
