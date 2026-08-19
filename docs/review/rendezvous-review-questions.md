# Rendezvous external-review questions

Phase 1F candidate: client-secret capabilities + opaque locator + RFC 9382 SPAKE2 over a store-and-forward relay. Details: `docs/review/client-secret-spake2-rendezvous.md`.

No answer should be inferred from a primitive name alone. The reviewer should require a role/observation table, a threat-specific argument for corpus enumeration (including anchored `O(k)`), and a threat-specific argument for relay-without-`w` transcript forgery. Do not treat “mutuality” as a single property.

## Distinct-owner vs pair secret

1. Is proof of pair-secret knowledge sufficient for Veil's product semantics (both users independently add each other's IDs as an **honest-client** rule)?
2. Must rendezvous prove participation by the **owner** of each contact capability (cryptographic distinct-owner guarantee)?
3. If owner proof is needed, should Veil add fresh per-capability asymmetric owner-authentication material (not the stable identity, not reused across IDs, not registered raw with the relay)?
4. Is SPAKE2 still the right construction after owner authentication is added, or does that stacking make a different AKE preferable?
5. Can the composition be simplified without inventing a custom AKE?
6. What security property remains when both contact capabilities are stolen? (Bearer model: the thief knows `w`.)
7. Can a unilateral participant who knows both encodings instantiate both SPAKE2 roles, and is that accepted as honest-client UI/state violation?

## Corpus and locator

8. Can an unmatched target capability be recovered by enumerating a live raw-capability corpus under the documented relay threat model?
9. Is the anchored-corpus `O(k)` attack (known `C_A` + locator `L` + candidate set `S`) acceptable under Veil's privacy target?
10. Which party sees each raw capability, and when? Does any normal relay API receive a raw contact capability?
11. Is the rendezvous locator pseudorandom to a relay that lacks the capability pair? Are domain separation, canonical encoding, and rotation specified without ambiguity?
12. Which party sees a match representation (locator, SPAKE messages) and can test it against candidates?

## SPAKE2 composition

13. Is Veil's high-entropy pair secret an acceptable RFC 9382 password/`w` input if memory-hard hashing is omitted? Is the `w` mapping a compliant application definition or a forbidden custom variant?
14. Are deterministic lexicographic SPAKE2 roles sound, or must the RFC 9382 `M=N` variant be used for simultaneous first messages?
15. Are SPAKE2 identity strings (canonical capabilities) sufficient to prevent unknown-key-share if they are never sent to the relay?
16. Is store-and-forward, delay, reorder, duplication, and crash/resume compatible with RFC 9382, or does the mailbox profile break the proof's execution model?
17. Can a malicious **relay that does not know `w`** cause an honest client to accept confirmation (copy, fabricate, replay, swap locator, swap roles, split transcripts, race)?
18. What extra power, if any, does a relay gain by colluding with one legitimate participant who already knows both capability values?

## Product semantics and oracles

19. Can expiry, revocation, and one-time semantics be enforced without a raw-ID server corpus? Which guarantees hold only for honest clients?
20. Can replay, duplicate, retry, or races reveal information or consume a one-time capability incorrectly?
21. Can malformed/expired/unsupported inputs create an oracle through response, timing, storage, or push behavior?
22. Does the design remain safe when either client is malicious, including self-submission of both roles and intentional mass sharing of one ID?

## State, abuse, trust models

23. What exact state exists at every role, and what TTL/deletion/verifiable cleanup rule applies? Is rendezvous TTL unjustifiably copied from the 24-hour message envelope rule?
24. What proofs, security arguments, test vectors, attack tests, and independent reviews exist for the **exact** composition (not SPAKE2 in isolation)?
25. Which components or compositions are novel rather than standardized (mailbox PAKE profile, locator KDF, role assignment, optional owner-auth)?
26. Can the design remain bounded and practical with intermittent mobile clients, delayed delivery, abuse controls, and denial of service **without** a durable account identifier?
27. Does compromise of the relay retroactively expose stored unmatched relationships (raw IDs vs locators only)?
28. What metadata appears only upon a successful match?
29. If SPAKE2 is declined, does CPace (`draft-irtf-cfrg-cpace`) or an OPRF/PSM/TEE design actually improve Veil's mutual asynchronous workflow, or only substitute a different primitive?
30. What technical and operational assumptions would be required if two-server independence or a TEE/attestation model were proposed instead?

Historical Phase 0.8 numbering is superseded by this list for Phase 1F review.
