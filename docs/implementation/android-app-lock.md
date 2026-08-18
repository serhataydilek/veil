# Android app lock and screen privacy

## Scope

Phase 1C adds optional local UI access control and always-on window privacy. It creates no Veil protocol identity, contact ID, rendezvous, secure session, network state, or Rust/FFI boundary. A successful unlock grants temporary access to the local UI only. It does not cryptographically unlock Veil or the Phase 1B AES key.

## Authentication

`AndroidAppAuthenticator` uses `androidx.biometric:biometric:1.1.0` and the platform `KeyguardManager`. The only added permission is `android.permission.USE_BIOMETRIC`, which is not a runtime permission.

| API range | Policy |
|---|---|
| 30+ | `BIOMETRIC_STRONG \| DEVICE_CREDENTIAL` through BiometricPrompt |
| 28–29 | `BIOMETRIC_STRONG` when available; otherwise explicit device-credential confirmation. The unsupported strong-biometric-plus-device-credential combination is not requested. |
| 26–27 | Device PIN / pattern / password confirmation only |

`BIOMETRIC_WEAK` is not used. If no suitable device screen lock is configured, App Lock is unavailable and Settings explains that a device screen lock must be configured first. Prompt copy is platform-neutral (`Unlock Veil`); it does not name Face ID, Touch ID, or password. Application-level results are `SUCCESS`, `CANCELLED`, `NOT_CONFIGURED`, `TEMPORARILY_UNAVAILABLE`, `LOCKED_OUT`, and `ERROR`. Raw platform error strings are not shown.

## Preference and migration

`appLockEnabled` defaults to false and is stored only inside the existing VLP1 AES-GCM envelope. The inner plaintext is a 7-byte `VLS1` payload: schema version, ready marker, and the boolean preference. Unlock status, authentication method, timestamps, and identity data are not persisted.

Phase 1B’s `LOCAL_PROTECTION_READY:1` sentinel is decrypted first, then migrated atomically to `VLS1` with `appLockEnabled = false`. A failed write leaves the previous valid file in place and does **not** treat App Lock as known-disabled or enter `LOCK_NOT_REQUIRED`. The controller reports `MIGRATION_FAILED` / `UNAVAILABLE` until a later retry can complete the write. Unsupported inner versions fail closed as unreadable state. Missing or corrupt protected state remains `KEY_UNAVAILABLE` / `CORRUPT_OR_UNREADABLE` and must not be interpreted as App Lock disabled.

Enabling or disabling the preference authenticates first and reports the new value only after a verified protected-state commit.

## Session

In-memory states are `EVALUATING`, `LOCK_NOT_REQUIRED`, `LOCKED`, `AUTHENTICATING`, `UNLOCKED`, and `UNAVAILABLE`. `UNLOCKED` is never written to disk, `Bundle`, or `SavedStateHandle`. Process recreation with the preference enabled starts `LOCKED`. `ProcessLifecycleOwner` treats real backgrounding as a lock when the preference is enabled; configuration changes do not. Authentication-in-progress and a pending unlock prevent the system credential UI from immediately relocking a successful result. After every authentication or protected-state write completes, the session is re-derived from whether protected state is valid, whether App Lock is enabled, and whether the process is still in the foreground. Enabling App Lock while the process is backgrounded ends `LOCKED`. A failed disable while backgrounded cannot leave an enabled session `UNLOCKED`.

The locked root is a dedicated screen (`Veil` / `Veil is locked.` / `Unlock`). Normal navigation is not composed behind an overlay.

## Screen privacy

`FLAG_SECURE` is always applied to `MainActivity`. On API 33+, `setRecentsScreenshotEnabled(false)` is also applied. These controls do not depend on App Lock and do not claim universal screenshot prevention.
