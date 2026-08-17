# ADR 008: product state semantics and relationship terminality

## Status

Accepted for Phase 0 product architecture. It does not approve the blocked rendezvous construction or a secure-session library.

## Context

Security constraints require a product state model that does not manufacture presence, existence, reciprocity, read, or request signals. Users still need predictable local behavior for saved IDs, mutual pairing, identity change, and destructive relationship actions.

## Decision

Use **Saved ID** only for a locally retained contact-ID value. Before a reviewed mutual match, every retry/background/timeout outcome is a local, non-oracular `NO_VISIBLE_REMOTE_STATE`; no requests, approvals, declines, user lookup, or unilateral notification exist. Conversation terminology is `ESTABLISHING`, `ACTIVE`, `OFFLINE`, `IDENTITY_CHANGED`, `BLOCKED`, `RESET`, and `DESTROYED` as defined in `docs/state/`.

Identity change pauses trust. It must not silently accept replacement material; user acknowledgment and fresh safety-code verification are necessary before any new identity is trusted. This says neither that compromise is certain nor that continuity is safe.

Block is a local terminal deny state. Unblock only removes the deny state: it restores no session, mailbox, traffic, peer notification, or connection. Reset terminals cryptographic session/mailbox state but may retain a local shell and alias. Destroy includes reset and removes relationship state; a full destroy removes its alias. Block, unblock, reset, and destroy all require fresh mutual pairing with current IDs before communication can resume. Old packets/session state cannot reactivate a relationship.

## Consequences

UI, notifications, errors, and persistence must not infer remote presence or intent. Product wording uses local facts, while authenticated relay/client acknowledgements remain operational—not reading—semantics. Future protocol work must conform to this state model and cannot add a visible unilateral request phase.
