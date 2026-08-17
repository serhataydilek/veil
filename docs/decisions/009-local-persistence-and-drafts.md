# ADR 009: local persistence and draft removal

## Status

Accepted for Phase 0 product architecture; platform-specific secure-storage/backup mechanisms remain IMPORTANT implementation work.

## Context

Restart behavior must be predictable without turning Veil into a cloud account or preserving plaintext beyond need. Expiry and terminal states must survive crashes, while drafts and obsolete transient pairing state add privacy risk with little V1 value.

## Decision

Persist only encrypted local state needed for normal continuity: active identity/protected-key references, active rotating-ID lifecycle, policy-kept unmatched saved IDs, secure conversation/session state, valid unexpired messages and bounded queues, local aliases, block state, and safety-verification state. Persist expiry/time-confidence state so restart cannot extend a lifetime.

Do not cloud-back up, export, migrate, or recover identity/conversation state. Purge expired messages and stale temporary plaintext before render after restart; retain one-time/QR consumption data only for the shortest reviewed replay-tombstone period. V1 does not persist unsent drafts: composer plaintext is discarded on app exit, process death, lock, or restart. Block state persists; unblock never revives former session/mailbox state. Alias persists with the local relationship shell until the user chooses full destruction, when it is removed.

## Consequences

The app is less convenient after interruption but retains less plaintext. Encrypted database, Keystore fallback, backup exclusion, WAL/journal cleanup, crash consistency, screenshot, and clipboard behavior remain implementation validation items. No product copy may imply physical-media erasure is guaranteed.
