# Phase 1J settings and privacy UI

Home remains a truthful empty root: it shows `No conversations`, has no Add ID or My ID control, and opens Settings as its only product action. Settings returns to Home through the existing top-bar back behavior; lock and unavailable roots remain above navigation.

Privacy exposes the authoritative Phase 1C/1H App Lock state only. The toggle is not optimistic: authentication and protected-state persistence complete before its checked value changes. Screen privacy and clipboard rows are informational, accurately describing platform limits.

Notifications use the existing `NotificationPrivacy` model. On API 33+ Settings reads the runtime permission and presents an explicit `Enable notifications` action only when disabled; launch and onboarding never request it. The action uses Android's permission flow and maps its callback back to authoritative enabled/disabled state. On pre-33 devices the UI says runtime permission is not required, without claiming deliverability. No notification or messaging event is created.

About obtains the installed package version through `PackageManager` rather than a duplicate UI constant. New controls use visible headings, text labels, native `Switch` semantics, and ordinary button touch targets; state is represented in text as well as control state.

Deferred: real Add ID, My ID, rotating/one-time IDs, QR pairing, protocol identity, rendezvous, secure sessions, E2EE, and messaging.
