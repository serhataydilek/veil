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

**Block** is a local policy denying rendering and processing from a peer/session and discarding matching envelopes. **Reset** discards ratchet/session state and rotates mailbox credentials after explicit user action, retaining the conversation shell only locally. **Destroy conversation** removes local session, aliases/messages subject to deletion limits, blocks old handles, and sends an authenticated best-effort relay revocation for current/overlap handles. It does not guarantee delivery of revocation to a malicious relay. A destroyed relationship can communicate again only through a fresh mutual pairing using currently valid capabilities.

## Security consequences

Rotation control, next handle, epoch number, and revocation scope are authenticated inside E2EE session traffic. A peer cannot activate a next mailbox without acknowledged provisioning. Old epochs reject new traffic after overlap/revocation and cannot create a new session. Relay revocation authorizes routing-state deletion; it never decrypts content.

## Privacy consequences

Epochs shorten one mailbox's correlation window. The relay necessarily observes an old/new overlap and may correlate it in real time, plus traffic timing. Deletion reduces future database-leak value but does not erase access logs or a malicious relay's observations.

## Operational consequences

The relay needs current/next epoch state, atomic rotation, short overlap cleanup, and idempotent revocation. `MailboxRotate` acknowledgement is delivery control only, never user-visible and never a read receipt. If an acknowledgement is lost, the sender continues old-epoch routing and retries bounded control; it must not guess a new handle.

## Residual risks

Offline peers delay rotation; a hostile peer can withhold acknowledgement; a relay can correlate live handles and disrupt delivery. Users may still receive already-queued, valid pre-block data until the client applies its local block policy.

## Implementation requirements

- Generate handles with an audited CSPRNG; never derive from public ID or stable identity.
- Persist epoch state transactionally with secure-session state and make rotation/revocation idempotent.
- Enforce server acceptance window no longer than queued-message maximum; purge old state/links at completion.
- Treat reset/destroy as a hard terminal state for old session identifiers.

## Tests required

Offline and lost-ACK rotation tests; concurrent rotation conflict tests; queue migration/expiry tests; revocation race/replay tests; persistence/crash recovery tests; and relay retention/log tests proving no retained mailbox-history chain.

## Open issues

Whether 256/seven-days is acceptable for mobile overhead and whether to add privacy-preserving padding remain IMPORTANT. The exact relay revocation authorization format requires protocol review.
