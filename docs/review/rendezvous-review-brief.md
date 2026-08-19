# Rendezvous external-review brief

**Research date:** 2026-08-20. Phase 0.8 package date 2026-08-17 remains historical. This brief now points at the Phase 1F candidate; it still does not implement cryptography. AI/document review is not independent cryptographic review.

## Product requirement

Conversation establishment requires that A independently has B's current temporary contact capability and B independently has A's current temporary contact capability. There is no unilateral request. Before a true mutual pairing, A must not learn whether B exists, registered the capability, is online, entered A, or rejected anything; B receives no unilateral request or notification. Stable identity material remains hidden until successful secure-session establishment.

## Relay threat model

The relay is honest-but-curious or later compromised. It sees source IP/timing unless separately mitigated, can retain copied data despite intended TTL, and can deny service. It is never trusted with plaintext message content.

ADR 001 currently issues or validates temporary capabilities, which creates a finite live-capability corpus. **Phase 1F's primary candidate removes that corpus:** contact capabilities are client-generated secrets never registered in raw form. Reviewers must still model the case where a corpus is later obtained (screenshots at scale, client-backup leak, a reintroduced registration API).

## Rejected construction

`T = H(canonical_order(A, B))` remains rejected (ADR 007). A relay holding `A`, `T`, and every live capability can recover `B` in linear corpus time.

A **locator-only** client-secret design is also rejected as a complete solution: the relay can copy the first party's locator and invent a second submission. Pair-secret authenticated key confirmation is required so a relay **that does not know `w`** cannot fabricate a valid confirmed transcript. That is not proof of distinct-owner participation.

## Phase 1F primary candidate (review only)

Client-secret capability + opaque pair locator + RFC 9382 SPAKE2 with explicit key confirmation + store-and-forward relay.

Details: `docs/review/client-secret-spake2-rendezvous.md`, `docs/review/rendezvous-construction-refresh-2026-08-19.md`, [ADR 016](../decisions/016-rendezvous-construction-candidate.md).

This construction is **preferred for external review** and **BLOCKED for implementation**. RFC 9382 is IRTF Informational, not IETF Standards Track, and SPAKE2 was not selected in the CFRG PAKE competition.

## Required properties

| Class | Requirement |
|---|---|
| MUST | No user-visible unilateral oracle or peer notification; replay resistance; expiry and one-time semantics (honest-client vs malicious-owner distinguished); asynchronous/mobile practicality; bounded state; explicit TTL/deletion; auditable implementation; stable identity hidden until secure session; a relay that does not know `w` cannot fabricate a confirmed SPAKE2 transcript. Distinct-owner participation is **not** a MUST until product/review explicitly requires it. |
| DESIRED | No single relay role can recover an unmatched target through a live raw-capability corpus **because that corpus is not retained**; minimized pre-match relationship metadata. |
| ACCEPTED LIMITATIONS | Completed-match timing, relay IP/timing, and V1 push device correlation may remain visible; global traffic-analysis resistance is not promised; one known capability plus a candidate corpus enables `O(k)` locator testing; a full raw-capability dump enables `O(n)` with an anchor or `O(n²)` without. |
| NON-GOALS | Perfect anonymity; endpoint-compromise protection; global enforcement of expiry/one-time against a malicious owner without reintroducing a corpus or credentials; protection from colluding independent services unless a selected design explicitly and realistically depends on non-collusion. |

## Reviewer boundary

The reviewer must evaluate the whole composition: who sees capability inputs, locators, SPAKE2 messages, confirmation MACs, stored records, failures, and deletion—not merely whether SPAKE2 or an OPRF is standard.

- [RFC 9382](https://www.rfc-editor.org/rfc/rfc9382.html) defines SPAKE2, not Veil's mailbox retry/crash profile.
- [RFC 9497](https://www.rfc-editor.org/rfc/rfc9497.html) defines OPRF/VOPRF/POPRF primitives, not Veil's asynchronous mutual rendezvous workflow.
