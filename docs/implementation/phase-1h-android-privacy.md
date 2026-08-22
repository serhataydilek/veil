# Phase 1H Android privacy hardening

## Scope

Phase 1H hardens local Android interaction privacy only. It adds no network permission, backend, FCM, protocol identity, contact IDs, rendezvous, SPAKE2, secure session, E2EE, messaging, or analytics.

## App Lock grace policy

App Lock remains disabled by default. When enabled, a successful device authentication creates an in-memory-only UI session. A process restart or reboot begins locked. The only grace state is an in-memory monotonic timestamp recorded when the app backgrounds; it is not written to VLP1, a Bundle, or saved state.

The conservative default is 30 seconds: enough for an accidental app switch, short enough to limit unattended exposure. Resume at or before 30 seconds remains unlocked. A negative or expired monotonic duration locks. Wall-clock changes cannot extend the window because the policy uses `System.nanoTime()`. Unknown protected-state configuration, protection failure, a failed migration, or a normal cold start remains locked/unavailable.

The neutral waiting root remains visible while protection or local-retention startup is unresolved. The locked root is rendered instead of composing normal navigation behind it.

## Clipboard

`ClipboardPrivacy` is the sole future copy boundary for explicitly requested sensitive copies. It does not copy anything automatically and is not connected to a fake message flow. On Android 13+, Veil marks its copied clip as sensitive to suppress supported previews. A random opaque ownership label is checked before an approximately 60-second delayed clear; replacement clipboard content is never cleared. Clipboard content is never logged.

## Text input and IME

`VeilInputPrivacy` distinguishes normal user text, private message text, and secret/security input. Private and secret classifications disable suggestions and autofill in the policy; secret input is obscured. The current contact-ID entry uses password input and disables autocorrect, so Android sets a non-learning, secret-style input type. Accessibility is not globally disabled.

An IME is a separate process. Veil can request suitable flags but cannot guarantee that a malicious, compromised, or OEM keyboard does not observe typed text.

## Notifications

There is no delivery, push provider, or notification emission in Phase 1H. The notification foundation declares `POST_NOTIFICATIONS` for a future deliberate user-facing feature but makes no runtime request at launch or elsewhere. It exposes a future channel specification only: low importance, no badge, and `VISIBILITY_SECRET`. It contains no message body, sender, identifier, pairing, safety, or cryptographic field and creates no notification extras.

## Logging audit

Production Android sources contain no application logging calls. No telemetry or analytics was added. The existing security invariant prohibiting plaintext, keys, aliases, IDs, ciphertext, IVs/nonces, authentication material, and clipboard values in logs remains applicable.

## Platform limitations

`FLAG_SECURE` and disabled recents screenshots cover supported Android capture paths, not cameras, rooted/compromised devices, accessibility abuse, OEM defects, or every recording route. Clipboard and notification-history behavior are platform-controlled once content leaves Veil; the implementation minimizes what Veil puts there rather than claiming universal deletion.

## Validation

JVM tests cover monotonic grace acceptance/expiry/rollback, process-lock behavior, protection-failure paths, clipboard ownership replacement, text privacy classification, notification API/channel policy, and the existing fail-closed state machine. Existing instrumentation covers `FLAG_SECURE`, recents setup, Android protected state, local retention, and app-lock lifecycle. Full Android and Rust validation is recorded with the Phase 1H PR after execution.
