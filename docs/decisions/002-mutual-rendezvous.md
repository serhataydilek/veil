# ADR 002: capability-pair rendezvous

## Status

Superseded for security by ADR 007. Retained as historical reasoning; the selected symmetric-hash candidate is unsafe against relay-side live-capability enumeration and must not be implemented.

## Context

Both people must independently enter the other's current temporary capability. A unilateral entry must yield neither an existence/validity/online signal nor a notification. The relay should learn less than a direct lookup, while the system must remain auditable and mobile practical.

## Threats / constraints

We must prevent online guessing, relay-oracle responses, unsolicited requests, replay, and durable relationship graphs. The relay is assumed curious and eventually compromisable, but it necessarily observes a match while serving it. PSI alone does not create the required asynchronous two-party state machine and can add substantial mobile and audit complexity.

## Options considered

| Option | relay learns | assessment |
|---|---|---|
| Direct lookup: submit B's ID | B validity, unilateral interest, likely graph | rejected: immediate oracle and relationship record |
| Symmetric tag from two high-entropy capabilities | relay can enumerate live capabilities with submitted A and recover B before match | rejected by ADR 007 |
| Blind/private rendezvous / OPRF-like service | potentially less input knowledge | promising but not selected without a reviewed, deployable protocol and abuse design |
| Generic PSI | set intersection and protocol metadata | not justified for a one-pair, asynchronous mobile workflow |
| Client-issued capabilities | harder server validation/abuse control | deferred; server-issued opaque capabilities chosen by ADR 001 |

## Decision

The former V1 candidate had a client prove possession of its own live capability and submit a tag derived from its own and peer capability. It is now rejected: because the relay knows the submitted self capability and can enumerate its live-capability corpus, it can recover the peer in linear corpus time. The tag was not relay-opaque.

An OPRF, PSI, or split-trust component does not automatically solve this. ADR 007 requires a reviewed construction that establishes role separation and prevents a single relay role from obtaining both enumerable candidates and testable match representation.

After match, each client uses the peer's ephemeral pairing material to begin an authenticated secure-session handshake. Only inside that handshake does it receive and authenticate the stable peer identity material; safety-code display follows authentication.

## Security consequences

A single valid-looking attempt receives the same response whether no peer exists, the capability is expired, the peer is offline, or there is no reciprocal entry. Inputs must be rate-limited before storage. A malicious relay can suppress or delay matches but cannot create a trusted peer without breaking the later authenticated handshake.

## Privacy consequences

The relay sees a submitter's current self-capability and the former tag; it can reconstruct the peer capability from its own live-capability set before match. At match it also links temporary participants and timing. The absence of a plaintext peer-ID field did not provide the claimed privacy.

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

The final reviewed rendezvous construction is a **BLOCKER**. Evaluate established OPRF/PSI/split-trust components only as part of a design that demonstrably prevents relay corpus enumeration without undermining abuse controls or auditability.
