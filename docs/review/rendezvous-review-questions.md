# Rendezvous external-review questions

Phase 1F candidate: client-secret capabilities + opaque locator + RFC 9382 SPAKE2 over a store-and-forward relay. Details: `docs/review/client-secret-spake2-rendezvous.md`.

No answer should be inferred from a primitive name alone. The reviewer should require a role/observation table and a threat-specific argument for corpus enumeration **and** for relay-forged reciprocity.

## Corpus and locator

1. Can an unmatched target capability be recovered by enumerating a live raw-capability corpus under the documented relay threat model? What changes when that corpus is absent, when one ID is known, and when a full corpus is later obtained?
2. Which party sees each raw capability, and when? Does any normal relay API receive a raw contact capability?
3. Is the rendezvous locator pseudorandom to a relay that lacks the capability pair? Are domain separation, canonical encoding, and rotation specified without ambiguity?
4. Which party sees a match representation (locator, SPAKE messages) and can test it against candidates?

## SPAKE2 composition

5. Is Veil's high-entropy pair secret an acceptable RFC 9382 password/`w` input if memory-hard hashing is omitted? Is the `w` mapping a compliant application definition or a forbidden custom variant?
6. Are deterministic lexicographic SPAKE2 roles sound, or must the RFC 9382 `M=N` variant be used for simultaneous first messages?
7. Are SPAKE2 identity strings (canonical capabilities) sufficient to prevent unknown-key-share if they are never sent to the relay?
8. Is store-and-forward, delay, reorder, duplication, and crash/resume compatible with RFC 9382, or does the mailbox profile break the proof's execution model?
9. Can a malicious relay cause an honest client to conclude **mutual pairing complete** without a peer who knows the capability pair (copy, fabricate, replay, swap locator, swap roles, split transcripts, race)?

## Product semantics and oracles

10. Can expiry, revocation, and one-time semantics be enforced without a raw-ID server corpus? Which guarantees hold only for honest clients?
11. Can replay, duplicate, retry, or races reveal information or consume a one-time capability incorrectly?
12. Can malformed/expired/unsupported inputs create an oracle through response, timing, storage, or push behavior?
13. Does the design remain safe when either client is malicious, including self-submission of both roles and intentional mass sharing of one ID?

## State, abuse, trust models

14. What exact state exists at every role, and what TTL/deletion/verifiable cleanup rule applies? Is rendezvous TTL unjustifiably copied from the 24-hour message envelope rule?
15. What proofs, security arguments, test vectors, attack tests, and independent reviews exist for the **exact** composition (not SPAKE2 in isolation)?
16. Which components or compositions are novel rather than standardized (mailbox PAKE profile, locator KDF, role assignment)?
17. Can the design remain bounded and practical with intermittent mobile clients, delayed delivery, abuse controls, and denial of service **without** a durable account identifier?
18. Does compromise of the relay retroactively expose stored unmatched relationships (raw IDs vs locators only)?
19. What metadata appears only upon a successful match?
20. If SPAKE2 is declined, does CPace (`draft-irtf-cfrg-cpace`) or an OPRF/PSM/TEE design actually improve Veil's mutual asynchronous workflow, or only substitute a different primitive?
21. What technical and operational assumptions would be required if two-server independence or a TEE/attestation model were proposed instead?

Historical Phase 0.8 numbering is superseded by this list for Phase 1F review.
