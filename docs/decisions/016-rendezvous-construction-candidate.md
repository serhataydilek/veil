# ADR 016: mutual rendezvous construction candidate (client-secret SPAKE2)

## Status

**PREFERRED CONSTRUCTION FOR EXTERNAL REVIEW — BLOCKED FOR IMPLEMENTATION.**

This is option A of the Phase 1F decision rule, **qualified** by an unresolved distinct-owner question (below). The construction is not accepted for implementation. AI analysis is not independent cryptographic review. No cryptographic dependency, rendezvous endpoint, capability generator, or owner-authentication primitive is added.

## Context

ADR 007 rejected `T = H(canonical(A, B))` because a relay with submitter capability `A`, tag `T`, and a finite live-capability corpus can recover `B`. ADR 002 is historical. Phase 1 rendezvous remains blocked.

ADR 001 currently issues server-authenticated opaque contact capabilities. That issuance is exactly what creates the live corpus ADR 007 exploited.

Phase 1F asks whether Veil can **remove the raw-capability corpus from the relay entirely** by using client-generated secrets that are never registered in raw form, an opaque pair locator, RFC 9382 SPAKE2 with mandatory key confirmation, and a store-and-forward mailbox.

Evidence: `docs/review/rendezvous-construction-refresh-2026-08-19.md` and `docs/review/client-secret-spake2-rendezvous.md`. Research date: 2026-08-20; mutuality/corpus language corrected in the same Phase 1F branch.

Secure-session work remains independently BLOCKED (ADR 005/015). This ADR does not start Phase 1G.

## Threats / constraints

The relay may delay, reorder, replay, drop, or fabricate mailbox contents and always sees IP/timing. Clients are often offline. Pairing must not create a user-existence oracle. Possession of a contact ID is not identity. Honest-client expiry/one-time policy must be distinguished from malicious-owner enforcement.

A bare shared locator does not prove that a second participant knows `(C_A, C_B)`.

Anyone who holds **both** contact capability values can derive `w` and instantiate **both** SPAKE2 roles. SPAKE2 must not be described as proving distinct-owner participation.

## Options considered

| Option | Outcome |
|---|---|
| A. Prefer client-secret + locator + RFC 9382 SPAKE2 for external review; keep BLOCKED | **Selected**, with distinct-owner proof explicitly in review scope |
| B. No acceptable single-relay construction | Not taken: removing the server corpus closes ADR 007's *normal-operation* attack; SPAKE2 still addresses relay-without-`w` transcript forgery at the protocol-property level, pending composition review |
| C. Split-trust / TEE required | Rejected: not required to close the specific ADR 007 attack once the server raw corpus is absent; TEE/ORAM is a different trust model |
| Accept `H(A,B)` or locator-only | Rejected: enumeration and/or relay-without-`w` locator copying |
| RFC 9497 OPRF as the rendezvous | Rejected as a substitute: primitive, not the async mutual state machine; does not stop a relay without `w` from inventing blobs |
| Blind-issued capabilities to keep global expiry | Not selected for V1: complexity vs honest-client local expiry |
| Google PSM / Signal SGX contact discovery | Rejected as not Veil's workflow or trust model |
| Require cryptographic distinct-owner proof in this ADR | **Not silently decided.** If review requires it, SPAKE2-only is incomplete (see owner-authentication option in the evaluation doc). |
| Mark accepted for implementation | Forbidden: no independent review of the exact composition exists |

## Decision

Record this family as Veil's **preferred mutual-rendezvous construction for external review** and **do not implement it**:

1. Contact capabilities are generated on the client (≥256-bit independent secrets) and are **never registered in raw form** with the relay. They remain **bearer** secrets: possession is not ownership.
2. Both parties derive a domain-separated pair secret from a canonical encoding of the two capabilities, then a separate opaque **rendezvous locator**. The relay stores store-and-forward blobs only under that locator.
3. **RFC 9382 SPAKE2** is the preferred review primitive for **pair-secret authenticated key establishment** and **relay-forgery resistance**. It does **not** by itself establish distinct-owner participation, because either holder of both capability values knows the shared secret. SPAKE2 is IRTF Informational, was **not** selected in the CFRG PAKE competition, and is **not** IETF Standards Track.
4. Roles: lexicographically smaller canonical capability encoding → SPAKE2 A (`M`); larger → SPAKE2 B (`N`). Identities in the SPAKE2 transcript are those encodings and are not sent to the relay. `M=N` is the RFC-mandated fallback if roles cannot be agreed; it is not the default here because both parties know the encodings before generating first messages.
5. Pairing is complete only after both confirmation messages verify. That confirms **properties 1–2** (relay without `w` cannot forge; peer knows `w`). It does **not** confirm **property 3** (opposite-capability owner independently participated). Derive a temporary pairing-channel key from SPAKE2 `Ke` only to protect later ephemeral pairing-material exchange. Do not create stable identity, libsignal sessions, or E2EE here.
6. Expiry and one-time use are **honest-client and local-peer checks**, plus optional bounded locator tombstones. They are **not** globally enforced against a malicious owner without reintroducing a raw-ID corpus.
7. Official clients generate only their assigned SPAKE2 role after local peer-ID entry (**interpretation A**, honest-client product semantic). Whether Veil **also** requires cryptographic distinct-owner proof (**interpretation B**) is an **unblock condition**, not a hidden assumption. If **B** is required, this SPAKE2-only family is **incomplete**.

### Three properties

| | Property | SPAKE2-only candidate |
|---|---|---|
| 1 | Relay without `w` cannot fabricate a confirmed transcript | Intended, pending composition review |
| 2 | Confirmed peer knows the pair secret / both capability values | Yes |
| 3 | Owner of the opposite contact capability independently participated | **No** |

### Conflict with ADR 001

This candidate **conflicts with ADR 001's server-issued capability direction**.

- ADR 001 remains the **historical/current accepted issuance decision** until a reviewed replacement is implementation-approved.
- **ADR 016 conditionally supersedes only the issuance/registration portion of ADR 001**, and only as a **review candidate**. It does **not** implement, and does not claim, that supersession is approved for production.
- Unlinkable encoding rules in ADR 001 (no **stable** public key, device ID, account, or deterministic old→new derivation) **stand**.
- A **fresh per-contact** owner-authentication public key, if review later requires property 3, would not be that forbidden stable identity **if** it is independently generated per ID, never reused, not derived from the stable identity, and has no old→new relation. ADR 001 does **not** already approve embedding such material.
- If external review rejects client-secret issuance, ADR 001 issuance remains in force and this candidate is withdrawn.

Do not rewrite ADR 001's history.

### Unblock conditions (all required)

1. Independent cryptographic review of the **exact Veil composition** (locator KDF properties, `w` mapping, roles/identities, mailbox/async profile, confirmation, crash/resume). AI analysis does not satisfy this gate.
2. Demonstration, including attack tests, that a relay **without** the pair secret cannot cause confirmed pairing (property 1), and that unmatched target recovery fails in the **no-dataset** model.
3. Explicit product/review decision: is pair-secret knowledge (**A**) sufficient, or must rendezvous prove distinct-owner participation (**B**)? If **B**, specify owner-authentication or a different AKE; SPAKE2-only must not ship.
4. Explicit product acceptance that expiry/one-time/revocation are not globally enforced against malicious owners, **or** a reviewed reintroduction of issuance/credentials.
5. Reviewed TTL, tombstone, logging, and abuse-control numbers without a durable account identifier and without a unilateral oracle.
6. If a library is later proposed, it must implement the reviewed profile (RFC 9382 exact or reviewer-chosen alternative such as CPace) and pass ADR 010. Inventory in the evaluation doc is not a selection. **No crate is added now.**
7. ADR 007's implementation prohibition on enumerable pair tags remains until this construction (or a replacement) is unblocked.
8. Secure session remains independently BLOCKED (ADR 015). Rendezvous implementation must not start solely because this candidate is preferred.
9. Anchored-corpus `O(k)` recovery must be accepted in privacy claims **or** mitigated by a reviewed change.

## Security consequences

In normal operation the relay has no raw-ID corpus, so ADR 007's linear enumeration does not apply **unless** an external candidate set is later combined with one known ID (`O(k)`) or a full dump is obtained (`O(n)` with an anchor, `O(n²)` without). That is stated, not hidden.

RFC 9382 key confirmation is the argument that a relay **without `w`** cannot fabricate a confirmed transcript. That argument is conditional on a correct composition (no ephemeral reuse, valid point checks, identities present, instance binding). It is **not** an argument that two distinct owners participated.

High-entropy `w` without memory-hard hashing is an application mapping the RFC requires to be defined; it is a review question, not a custom PAKE.

## Privacy consequences

Pre-match target recovery is architecturally addressed by not storing raw IDs **and** only while no useful raw-ID dataset exists. Completed-match correlation, IP, timing, locator equality, optional TTL-type leakage, and later `O(k)` tests against screenshots remain. No anonymity claim.

## Operational consequences

Abuse control moves to short-window IP/network limits, per-locator caps, and size limits. Issuance quotas disappear with issuance. QR/one-time/rotating use type-bounded rendezvous TTLs, not the 24-hour message envelope rule.

## Residual risks

Mailbox SPAKE2 profile is novel relative to RFC 9382's sample interactive flow. Rust crates either are not RFC 9382-exact (RustCrypto `spake2`) or are young and unverified (`pakery-spake2`). Malicious owners can ignore expiry/one-time and can simulate both SPAKE2 roles. Bearer-capability theft of **both** IDs yields `w`. Relay DoS remains. Distinct-owner proof is unresolved.

## Implementation requirements

- No Cargo/Gradle add in this phase.
- No SPAKE2, KDF, capability generation, owner-auth signatures, or rendezvous endpoint.
- No copied PAKE or custom variant.
- Do not describe the locator as mathematically unlinkable.
- Do not describe SPAKE2 as authenticating reciprocity or distinct-owner participation.

## Tests required (after unblock, not now)

RFC 9382 Appendix B vectors for the chosen ciphersuite; corpus models including one-ID-plus-`S`; relay-without-`w` forgery/replay/substitution; both-roles simulation; stolen-both-capabilities; role confusion and equal-capability reject; oracle uniformity; crash/resume without nonce reuse; TTL/tombstone; log redaction; honest vs malicious one-time/expiry.

## Open issues

All unblock conditions above, especially interpretation **A** vs **B** for distinct-owner proof. CPace may be preferred by reviewers (`draft-irtf-cfrg-cpace-21` at research date). Whether owner-authentication should be added to SPAKE2 or replace it. Exact KDF/ciphersuite/`w` mapping. Whether type-bounded TTLs leak ID class. Future optional rate tokens/PoW.
