# Rendezvous external-review brief

**Research date:** 2026-08-17. This brief frames a review problem; it does not select or specify a cryptographic construction. AI/document review is not independent cryptographic review.

## Product requirement

Conversation establishment requires that A independently has B's current temporary contact capability and B independently has A's current temporary contact capability. There is no unilateral request. Before a true mutual pairing, A must not learn whether B exists, registered the capability, is online, entered A, or rejected anything; B receives no unilateral request or notification. Stable identity material remains hidden until successful secure-session establishment.

## Relay threat model

The relay issues or observes temporary capabilities and may know the finite live-capability corpus. It sees source IP/timing unless separately mitigated, can be honest-but-curious, later compromised, retain copied data despite intended TTL, and deny service. It is never trusted with plaintext message content.

## Rejected construction

`T = H(canonical_order(A, B))` is rejected. A relay holding `A`, `T`, and every live capability can compute `H(canonical_order(A, X))` for each corpus member `X`; equality reveals B. This is linear corpus enumeration, not guessing a 256-bit unknown, so capability entropy does not solve it for that relay role.

## Required properties

| Class | Requirement |
|---|---|
| MUST | No user-visible unilateral oracle or peer notification; replay resistance; expiry and one-time semantics; asynchronous/mobile practicality; bounded state; explicit TTL/deletion; auditable implementation; stable identity hidden until secure session. |
| DESIRED | No single relay role can recover an unmatched target through its live corpus; minimized pre-match relationship metadata and persistent graph creation. |
| ACCEPTED LIMITATIONS | Completed-match timing, relay IP/timing, and V1 push device correlation may remain visible; global traffic-analysis resistance is not promised. |
| NON-GOALS | Perfect anonymity; endpoint-compromise protection; protection from colluding independent services unless a selected design explicitly and realistically depends on non-collusion. |

## Reviewer boundary

The reviewer must evaluate the whole composition: who sees capability inputs, evaluation keys, candidate corpus, match representation, stored records, failures, and deletion—not merely whether an underlying primitive is standard. RFC 9497 defines OPRF/VOPRF/POPRF primitives, not Veil's asynchronous mutual rendezvous workflow or its relay-role separation ([RFC 9497](https://www.rfc-editor.org/rfc/rfc9497.html)).
