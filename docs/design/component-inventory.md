# Conceptual component inventory

This is an interaction/data-boundary inventory, not code.

| Component | Purpose / states | Allowed data | Prohibited data | Accessibility |
|---|---|---|---|---|
| VeilTopBar | Context title, back/overflow; normal/warning | local title, local action availability | profile/presence/delivery state | labelled actions, logical heading |
| ConversationRow | Open established conversation; normal/local-unread | alias or approved peer fallback, optional local preview/time | avatar, rotating ID, peer presence/read state | full row target, concise label |
| MessageBubble | Render text; own/peer, sending | text, local grouping/timestamp, transient sending | delivery/read icons, media/link preview | reading order, selectable text policy |
| TextComposer | Text-only composition; empty/active/restricted | current in-memory draft, local safe restriction | attachments, camera, microphone, persisted draft | labelled send, dynamic height, IME-safe |
| ContactIdField | Enter/paste ID; valid/local-error | entered ID, local format/checksum feedback | server validity or peer identity | grouped readable label/error |
| ContactIdDisplay | Show shareable temporary ID | current ID and locally known expiry | stable keys/device/session IDs | grouped copy/read-aloud chunks |
| QRDisplay | Render temporary capability | QR capability, local expiry/revoke state | stable identity or peer state | manual-ID alternative |
| SafetyCodeDisplay | Compare established identity | code/QR, local verification state | account framing, compromise certainty | instructions independent of color |
| PrivacyNotice | Explain relevant local limit | expiry, notification, screen/privacy wording | marketing claims | readable dismissal/links |
| LocalStatusBanner | Own offline/clock/identity state | local condition and safe action | peer connectivity/internal protocol data | announced non-disruptively |
| DestructiveConfirmDialog | Confirm block/reset/destroy | exact local consequence | remote reaction/deletion guarantees | focus trap, default cancel, clear destructive control |
| EmptyState | Explain absent local content | neutral local next actions | growth/referral/discovery copy | actionable, not illustration-only |
| SettingsRow | Reach controllable setting | local label/current value | unavailable feature controls | full target, state announced |
