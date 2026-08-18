# ADR 013: Android app lock and screen privacy

## Status

Accepted for Android local UI access control and window-level screen privacy. This does not select Veil protocol identity, recovery, or cryptographic unlock of the Phase 1B local-protection key.

## Context

Phase 1C needs an optional app lock and always-on capture limits before identity or conversation content exists. Platform authentication must remain separate from the AES local-protection key introduced in ADR 012.

## Decision

- App Lock is optional and disabled by default.
- Use the device’s existing secure authentication only. Veil does not own, store, or collect a PIN, password, or biometric template.
- Use stable AndroidX BiometricPrompt `1.1.0`. Do not use Credential Manager or a custom credential UI for this phase.
- Preferred authenticators are `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` on API 30+. API 28–29 use `BIOMETRIC_STRONG` when available with an explicit device-credential fallback. API 26–27 use device PIN / pattern / password confirmation only. Do not silently downgrade to `BIOMETRIC_WEAK`.
- If no suitable device screen lock is configured, App Lock is unavailable.
- Do not bind the Phase 1B AES local-protection key to biometric or device-credential authentication. Unlock success grants temporary local UI access only and is not a cryptographic unlock of Veil.
- Persist `appLockEnabled` only inside the existing authenticated VLP1 envelope as a small versioned inner payload. Do not persist it in plaintext SharedPreferences and do not create a second key or secret store.
- Unlock state is in-memory only. Process restart never restores an unlocked session. If the preference is enabled, cold start begins locked.
- When App Lock is enabled, leaving the application foreground relocks. There is no grace timeout in Phase 1C. Configuration changes are not treated as backgrounding. Authentication-in-progress is modeled so the system credential UI does not race-lock the session.
- Enabling or disabling the preference requires successful platform authentication and a durable protected-state commit. Cancellation or persistence failure leaves the previous preference unchanged.
- `WindowManager.LayoutParams.FLAG_SECURE` is always applied to the Veil activity. On API 33+, recent-app screenshot capture is disabled with `setRecentsScreenshotEnabled(false)`.
- Screen privacy is on by default and not user-disablable yet. It is not claimed to be universal: another camera, a compromised OS, accessibility services, root, OEM bugs, and some capture paths remain outside this control.
- Screen privacy does not depend on App Lock. Recents/screenshot protection remains active when App Lock is disabled.
- App Lock is not protocol identity authentication, server login, recovery, or a replacement for Android Keystore.
- Corrupt or missing protected state remains a higher-priority fail-closed condition. Inability to read `appLockEnabled` must not be treated as App Lock disabled.
- Failed Phase 1B→1C payload migration retains the valid legacy ciphertext for retry and does not treat App Lock as known-disabled.
- After authentication or protected-state persistence completes, lock session is re-evaluated against current foreground state. Enabling while backgrounded ends locked. A failed disable while backgrounded cannot leave an enabled session unlocked.

## Consequences

Users can optionally require device authentication to see Veil’s local UI. Capture limits reduce supported screenshot, recording, and recents-preview paths without promising forensic or universal prevention. Identity, rendezvous, E2EE, and timeout policy remain later work.
