# ADR 002: capability-pair rendezvous

## Status

Accepted as the V1 candidate architecture, conditional on external protocol review before implementation.

## Context

Both people must independently enter the other's current temporary capability. A unilateral entry must yield neither an existence/validity/online signal nor a notification. The relay should learn less than a direct lookup, while the system must remain auditable and mobile practical.

## Threats / constraints

We must prevent online guessing, relay-oracle responses, unsolicited requests, replay, and durable relationship graphs. The relay is assumed curious and eventually compromisable, but it necessarily observes a match while serving it. PSI alone does not create the required asynchronous two-party state machine and can add substantial mobile and audit complexity.

## Options considered

| Option | relay learns | assessment |
|---|---|---|
| Direct lookup: submit B's ID | B validity, unilateral interest, likely graph | rejected: immediate oracle and relationship record |
| Symmetric tag from two high-entropy capabilities | opaque pair tag; a match and timing | chosen V1 candidate; simple and bounded |
| Blind/private rendezvous / OPRF-like service | potentially less input knowledge | promising but not selected without a reviewed, deployable protocol and abuse design |
| Generic PSI | set intersection and protocol metadata | not justified for a one-pair, asynchronous mobile workflow |
| Client-issued capabilities | harder server validation/abuse control | deferred; server-issued opaque capabilities chosen by ADR 001 |

## Decision

For V1, a client proves possession of its own live server-issued capability and computes an opaque symmetric rendezvous tag from its own capability and the peer capability using a domain-separated standard hash over canonical ordered byte strings. It submits only the tag, a short-lived intent nonce, expiry/version, and an encrypted response blob for a fresh ephemeral pairing public key. The relay validates the submitter's own capability, stores the first intent under the opaque tag until the earliest expiry, and returns one uniform non-oracular result. When the complementary intent arrives, it atomically consumes one-time capabilities if relevant, delivers each encrypted blob to the other party, and deletes the rendezvous record.

This describes composition of standard capabilities, hashes, authenticated credentials, and public-key encryption. It is not a claim that the full construction is already reviewed: canonicalization, tag construction, intent authentication, replay handling, and information released at match require an external cryptographic review and test vectors before implementation.

After match, each client uses the peer's ephemeral pairing material to begin an authenticated secure-session handshake. Only inside that handshake does it receive and authenticate the stable peer identity material; safety-code display follows authentication.

## Security consequences

A single valid-looking attempt receives the same response whether no peer exists, the capability is expired, the peer is offline, or there is no reciprocal entry. Inputs must be rate-limited before storage. A malicious relay can suppress or delay matches but cannot create a trusted peer without breaking the later authenticated handshake.

## Privacy consequences

The relay sees a submitter's current self-capability and an opaque tag, and at match it can link the two temporary capabilities and timing for that one event. It does not receive plaintext peer IDs in the request path, stable identities, or a permanent graph by design. This reduces—not eliminates—relationship correlation; IP and timing remain observable.

## Operational consequences

The relay needs an atomic, TTL-indexed intent store and a uniform response path. Matching is one lightweight request each, suitable for intermittent mobile connectivity. A match is delivered only to already-participating clients; it creates no push or UI event beforehand.

## Residual risks

Tag collisions are negligible with correct primitives but must fail safely. Shared/stolen capabilities and a malicious relay can correlate events. A sophisticated private rendezvous may later offer better relay privacy, but should not replace this design without measurable benefit and review.

## Implementation requirements

- Specify canonical capability ordering and domain separation in a reviewed protocol document.
- Encrypt response blobs to one-use pairing keys; bind tag, expiry, version, and both capability types as authenticated context.
- Delete unmatched intents at earliest expiry; give all invalid/unmatched outcomes indistinguishable client behavior.
- Bound intent size, attempts, and match handling; no target lookup endpoint.

## Tests required

Oracle indistinguishability tests; reciprocal-match state-machine/property tests; replay, duplicate, race, expiry, one-time-consumption, tag canonicalization, and malformed-blob fuzz tests; relay schema/log inspection; and external cryptographic/privacy review.

## Open issues

The V1 candidate resolves the product boundary but remains a **security-review gate**, not a production-ready protocol. Evaluate an established private rendezvous/OPRF construction only if it demonstrably reduces relay knowledge without undermining abuse controls or auditability.
