# Privacy UX and state restoration

## Expiry

The conversation screen carries one subtle, stable statement that messages expire within 24 hours. Timestamps remain minimal; an optional long-press may show remaining lifetime only while the time module has sufficient confidence. Do not place a countdown beside every message. On opening after 48 hours, purge before render and show the clean conversation shell/empty state, not per-message tombstones. Messages expiring while open disappear cleanly; no “Message expired” trail is retained by default. Clock jumps backward/forward or suspicious ordering can cause early removal. When confidence is degraded, do not show fake precision; show a local privacy-safe warning and suspend unsafe actions.

## Safety and local aliases

After authenticated pairing, the conversation offers **Verify identity** with a numeric safety code and/or QR verification. Verification status is local: `UNVERIFIED`, `VERIFIED`, or `IDENTITY_CHANGED`. Identity change pauses trust and requires explicit acknowledgment plus new verification; it does not assert compromise. Aliases are optional local-only labels, never sent, backed up to Veil, or treated as usernames. Without one, display an abbreviated representation derived only from established local peer/session material, never a temporary contact ID.

## Lock and screen privacy (implementation intent)

Android App Lock uses device credential/biometric authentication only and keeps no biometric database. There is no user-selected timeout in Phase 1C: when enabled, leaving the foreground relocks. Hide recent-app previews and enable Android screenshot protection where appropriate, but clearly state that screenshots, recordings, accessibility software, modified devices, and OS behavior cannot be universally prevented. Clipboard copying is explicit, warns about OS visibility, and is cleared only where the OS allows. Notification history is outside Veil's full control.

## Offline and restart

Veil can display only its own condition: Offline or Unable to reach relay. It must not infer a peer's connectivity from delay, retry, push, or queue behavior. Across restart, retain active identity, aliases, established secure conversation state, valid unexpired messages, block state, verified safety state, policy-kept unmatched saved IDs, and active rotating-ID state. Remove expired messages before UI, stale temporary plaintext, obsolete one-time/QR transient data except the shortest reviewed replay tombstone, and expired queues. V1 does **not** persist unsent composer drafts: leaving the composer, app process death, lock, or restart discards its plaintext. No state is cloud backed up.

## Reinstall and loss

On identity loss or reinstall, use: “Old identity cannot be recovered. Create a new identity to use Veil. Old contacts will need a fresh mutual ID exchange.” Do not offer sign-in, password reset, restore, or migration.
