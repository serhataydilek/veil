# ADR 004: fail-closed bounded message expiry

## Status

Accepted for V1 enforcement model; no absolute trusted-time claim.

## Context

Veil must not intentionally make a message available in normal operation for more than 24 hours after genuine creation. Mobile wall clocks are mutable, monotonic clocks reset on reboot, senders can lie, relay clocks can be wrong or malicious, and recipients may be offline.

## Threats / constraints

No endpoint can prove an arbitrary user's real-world composition time. Physical deletion from flash/backups is not guaranteed. The design therefore enforces a conservative availability upper bound using independent observations and expires early on ambiguity rather than extending lifetime.

## Options considered

- **Sender timestamp only:** rejected; sender can backdate/future-date and local rollback can extend display.
- **Relay timestamp only:** rejected; a malicious/wrong relay could extend lifetime and it does not know local creation.
- **Wall clock only:** rejected; rollback and reboot defeat it.
- **Conservative multi-bound deadline:** chosen; it is enforceable for honest clients and fails closed when time confidence falls.

## Decision

At creation an honest client immediately records an authenticated creation timestamp and expiry (`creation + 24h`), together with a boot-session monotonic reading and a persisted non-decreasing observed-wall-clock lower bound. The secure envelope authenticates creation/expiry; the recipient never accepts an expiry more than 24 hours after sender creation and rejects implausible future creation beyond a small reviewed clock-skew allowance.

At relay ingress, the relay records a receipt time and gives the packet a server queue deadline no later than `min(authenticated expiry, ingress + 24h)`. Its timestamp may shorten availability but can never extend the authenticated deadline. On receipt, a client sets the display deadline to the earliest of authenticated expiry, relay queue deadline, and any already-persisted local lower-bound implication. While boot monotonic time is available, elapsed time independently advances expiry. Before UI/notification display and on launch, the client purges expired state.

If wall-clock rollback, lost persisted bounds, reboot ambiguity, or impossible timestamp ordering could make a deadline later, the client expires the message/session queue early and requires a fresh secure-session state as appropriate. A clock jump forward may also expire early. Sender creation time is enforceable only for honest clients; a malicious sender can lie about when they authored text, so Veil must not promise a cryptographic proof of genuine human creation time.

## Security consequences

Creation, expiry, protocol version, mailbox epoch, and message identity are authenticated. Replay never extends availability. Relay queues independently enforce TTL and delete after recipient ACK. Clients never use a relay time as authority to extend a message.

## Privacy consequences

The relay necessarily sees ingress and deletion timing for queued ciphertext. No plaintext expiry event is sent to push providers. Conservative early expiry may reveal less history, at the usability cost of lost late messages.

## Operational consequences

The client needs durable encrypted expiry records and monotonic/boot identifiers; relay storage needs TTL indexes and deletion verification. Time anomalies must be observable only through local privacy-safe diagnostics, never raw message identifiers or timestamps in routine logs.

## Residual risks

Unlocked/malware-compromised devices can copy plaintext before expiry. OS snapshots, WAL, flash wear-leveling, and backups can retain remnants. A malicious relay can deny service or expire early; no distributed scheme here proves a true wall clock.

## Implementation requirements

- Use a single reviewed time module with no UI override path.
- Transactionally persist message expiry and a non-decreasing local time lower bound in encrypted storage.
- Enforce relay TTL at write and read; never rely solely on asynchronous cleanup.
- Disable backup/extraction where the platform allows and avoid plaintext temporary storage.

## Tests required

Clock rollback/forward, reboot, stopped-app, offline-before-upload, delayed-delivery, forged sender timestamp, wrong relay time, replay, ACK/delete, WAL/restart, and property tests proving no accepted state extends a prior deadline.

## Open issues

The skew allowance, device-attestation value, and user-visible recovery behavior after a suspicious clock state require UX/security review. These do not alter the fail-closed rule.
