# Phase 1L Android privacy baseline correctness

## Scope

Phase 1L corrects two Android instrumentation baseline expectations against the existing approved privacy design. It adds no notification delivery, UI, permission prompt, networking, protocol identity, rendezvous, E2EE, or `INTERNET` permission. ADR 015 and ADR 016 remain blocked.

## App Lock

An enabled App Lock session is memory-only. On background, an unlocked session records only a monotonic timestamp and remains `UNLOCKED`; that timestamp is never written to protected state. A foreground return within the 30-second grace stays unlocked. A return after 30 seconds, a negative monotonic duration, or invalid timing fails closed to `LOCKED`. The policy uses monotonic time, never wall time. Process recreation/cold start reconstructs an enabled preference as `LOCKED`, never `UNLOCKED`.

The former `backgroundTransitionRelocks` instrumentation test asserted immediate relocking inside `onProcessBackground()`. That contradicted the grace contract and was replaced with deterministic injected-monotonic-clock coverage for both within-grace resume and expired-grace relocking. Existing protected-state integration still proves that no unlocked session is persisted and a recreated controller starts locked.

## Notifications

Android `NotificationChannel` lockscreen visibility is system/ranker-controlled on modern Android and is not an application-enforceable privacy guarantee. The former instrumentation assertion that a retrieved channel must report `VISIBILITY_SECRET` was therefore platform-invalid; the API 36 emulator returned `VISIBILITY_NO_OVERRIDE` (`-1000`).

Veil now represents channel policy separately from future notification-content policy. The dormant channel remains low importance and badge-disabled. Future notification delivery, which does not exist in Phase 1L, must set each individual notification's visibility to `Notification.VISIBILITY_SECRET` before delivery is accepted. Channel settings alone do not make sensitive content secret.

There is still no emitted notification, push provider, message content, launch/onboarding permission prompt, or automatic permission request. `POST_NOTIFICATIONS` remains a Settings-only future permission boundary.

## Validation

The two prior main-baseline failures were reproduced on the API 36 emulator before this phase. Updated focused instrumentation validates the actual App Lock lifecycle and only app-controlled notification-channel properties. JVM tests validate the separate future-notification visibility policy.
