# Android local protection

## Scope

Phase 1B provides Android-only at-rest protection infrastructure. It creates no Veil protocol identity, contact ID, rendezvous state, secure session, network state, or Rust/FFI boundary.

## Platform APIs and key policy

`AndroidLocalProtectionKeyStore` uses `KeyStore`, `KeyGenerator`, `KeyGenParameterSpec`, and `KeyProperties` with the `AndroidKeyStore` provider. The internal alias is the constant versioned value `veil.local-protection.v1`; it is never derived from a user, device, account, or installation value and is not logged.

The key is AES-256, non-exportable, and authorized only for encryption/decryption using GCM and no padding. StrongBox is not requested or assumed. Where supported, `KeyInfo` is classified internally as software, trusted environment, StrongBox, or unknown; no result is displayed as a product claim, persisted, or sent anywhere. User-authentication binding, `BiometricPrompt`, and `CryptoObject` are intentionally deferred.

## Protected state and format

The only persisted content is the `LOCAL_PROTECTION_READY:1` sentinel. It is stored at `context.noBackupFilesDir/veil-local-state.v1`; existing backup and data-transfer exclusions remain in force. No plaintext companion file is written.

The format is big-endian and deliberately bounded:

```text
magic (4 bytes: VLP1)
version (1 byte: 1)
IV length (1 byte: 12)
ciphertext length (4 bytes, maximum 4096)
IV (12 bytes)
ciphertext plus GCM tag (at least 16 bytes)
```

The cipher is `AES/GCM/NoPadding`. The provider generates a fresh randomized IV for every encryption. The fixed non-secret AAD is `veil.local-state.v1`. Unsupported versions, malformed/truncated bytes, invalid lengths, oversized lengths, trailing data, and authentication failure all fail closed without unbounded allocation.

## Persistence and lifecycle

Writes use `AtomicFile.startWrite`, `finishWrite`, and `failWrite`; a failed write preserves the prior valid file where the platform can do so and never reports the new state as committed. The local lifecycle is:

```text
NOT_PROVISIONED -> PROVISIONING -> READY
READY -> KEY_UNAVAILABLE | CORRUPT_OR_UNREADABLE
READY | KEY_UNAVAILABLE | CORRUPT_OR_UNREADABLE -> PURGING -> PURGED
```

`NOT_PROVISIONED` is only for no state file. Existing ciphertext with a missing/unavailable Keystore key is `KEY_UNAVAILABLE`, never a fresh install. Existing but malformed or unauthenticated ciphertext is `CORRUPT_OR_UNREADABLE`. Neither state auto-creates a key, overwrites data, or claims recovery.

Purge is an explicit operation. It first removes the logical Keystore alias, then the state file, and rechecks both. If alias deletion succeeds but file removal fails, the unreadable ciphertext remains and cleanup is reported incomplete. It does not claim forensic physical-media erasure.

## Limitations

The sentinel only proves the substrate can create a key, persist encrypted local state, and reopen it. It is not an identity-created event. Device/key loss leaves protected state unrecoverable. Android Keystore protection differs by OS/device and does not defend an already compromised unlocked endpoint.
