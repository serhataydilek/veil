# ADR 003: rotating mailbox epochs and destructive conversation controls

## Status

Accepted for V1 architecture; rotation thresholds require implementation measurement.

## Context

A session mailbox that remains forever is a durable relay-visible relationship identifier. Veil needs offline delivery while reducing this linkability and ensuring block, reset, and destruction cannot silently revive old state.

## Threats / constraints

The relay sees mailbox activity, timing, old/new overlap, delivery acknowledgements, and source IP. It must not retain a permanent mailbox-history chain. Both peers can be offline or out of order. Rotation cannot drop valid queued packets or introduce a read receipt.

## Options considered

- **One permanent handle:** simplest but rejected as an unnecessarily durable correlation point.
- **Deterministic time-only derivation:** avoids relay negotiation but risks split-brain/offline loss and exposes a predictable schedule.
- **Derive every mailbox from ratchet state:** tightly couples routing to complex out-of-order ratchet state; rejected for V1 auditability.
- **Authenticated rotate-control with bounded overlap:** chosen. It uses the established session, preserves offline delivery, and makes linkability reduction explicit rather than absolute.

## Decision

Each secure conversation has a random opaque **mailbox epoch handle**. The initiating peer proposes a fresh random next handle in an authenticated encrypted `MailboxRotate` control message. The recipient provisions the new handle and returns an authenticated operational acknowledgement; only then may the initiator route new application envelopes to it. The relay accepts both handles during a bounded overlap, preserves queued old-epoch envelopes until delivery or their 24-hour message expiry, then deletes the old routing state. It stores no durable old-to-new edge after cleanup.

Rotate after the first of: 256 sent application envelopes in the epoch, seven days of active epoch age, or an explicit session reset. The count bounds long high-volume correlation; seven days bounds a quiet active relationship without frequent battery-wasting control traffic. They are initial operational limits, not cryptographic constants, and require load/battery validation before shipping. Reconnect alone is not a trigger because it creates needless observable churn.

**Block** is a local terminal deny state for the peer/session and every current/overlap mailbox epoch. It discards queued and future old-session envelopes before render, refuses old control traffic, and sends authenticated best-effort revocation; no existing session can resume. **Unblock** only removes that deny state. It does not restore session material or handles, reconnect, notify the peer, or bypass mutual pairing. **Reset** is cryptographically terminal: it discards ratchet/session state, invalidates mailbox epochs, and sends best-effort revocation, but may retain a local conversation shell/alias. **Destroy conversation** performs reset and removes local relationship state, aliases if the user requests full destroy, and messages subject to deletion limits. Reset, destroy, and unblock all leave the people disconnected; a new conversation requires both to enter current valid IDs, finish fresh mutual pairing, authenticate a fresh session, and provision a fresh mailbox epoch.

## Security consequences

Rotation control, next handle, epoch number, and revocation scope are authenticated inside E2EE session traffic. A peer cannot activate a next mailbox without acknowledged provisioning. Old epochs reject new traffic after overlap/revocation and cannot create a new session. Local terminal state is authoritative even if relay revocation fails; relay revocation only authorizes routing-state deletion and never decrypts content.

## Privacy consequences

Epochs shorten one mailbox's correlation window. The relay necessarily observes an old/new overlap and may correlate it in real time, plus traffic timing. Deletion reduces future database-leak value but does not erase access logs or a malicious relay's observations.

## Operational consequences

The relay needs current/next epoch state, atomic rotation, short overlap cleanup, and idempotent revocation. `MailboxRotate` acknowledgement is delivery control only, never user-visible and never a read receipt. If an acknowledgement is lost, the sender continues old-epoch routing and retries bounded control; it must not guess a new handle.

## Residual risks

Offline peers delay rotation; a hostile peer can withhold acknowledgement; a relay can correlate live handles and disrupt delivery. A malicious relay may retain or deliver stale packets, but the client must discard them before render after terminal state is recorded.

## Implementation requirements

- Generate handles with an audited CSPRNG; never derive from public ID or stable identity.
- Persist epoch state transactionally with secure-session state and make rotation/revocation idempotent.
- Enforce server acceptance window no longer than queued-message maximum; purge old state/links at completion.
- Treat block, reset, and destroy as hard terminal states for old session identifiers; unblock must not reconstruct any old state.

## Tests required

Offline and lost-ACK rotation tests; concurrent rotation conflict tests; queue migration/expiry tests; block/unblock/reset/destroy and revocation race/replay tests; persistence/crash recovery tests; and relay retention/log tests proving no retained mailbox-history chain.

## Open issues

Whether 256/seven-days is acceptable for mobile overhead and whether to add privacy-preserving padding remain IMPORTANT. The exact relay revocation authorization format requires protocol review.
