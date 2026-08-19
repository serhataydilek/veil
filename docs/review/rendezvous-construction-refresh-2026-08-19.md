# Mutual rendezvous construction refresh

**Research date:** 2026-08-20. Filename keeps the Phase 1F package date `2026-08-19`.

This is a design/threat-model refresh. It is not an independent cryptographic review, not a protocol specification, and not an implementation approval. No cryptography, networking, or rendezvous endpoint was added to Veil.

Primary evaluation: `docs/review/client-secret-spake2-rendezvous.md`. Candidate decision: [ADR 016](../decisions/016-rendezvous-construction-candidate.md).

## Authoritative sources

| Source | Use |
|---|---|
| [RFC 9382](https://www.rfc-editor.org/rfc/rfc9382.html) SPAKE2 | Symmetric PAKE candidate. IRTF Informational, September 2023. Not IETF Standards Track. Not the CFRG PAKE competition winner. |
| [RFC 9497](https://www.rfc-editor.org/rfc/rfc9497.html) OPRF/VOPRF/POPRF | Primitive only. IRTF Informational. Does not specify Veil rendezvous. |
| [RFC 9497](https://www.rfc-editor.org/rfc/rfc9497.html) consensus note | RFC 9497 represents CFRG consensus; RFC 9382 represents individual CFRG member opinion(s). |
| CFRG PAKE selection | Balanced winner: CPace. Augmented winner: OPAQUE. SPAKE2 was a Round 2 balanced candidate and was **not selected**. Slides: <https://datatracker.ietf.org/meeting/interim-2020-cfrg-01/materials/slides-interim-2020-cfrg-01-sessa-results-of-the-pake-selection-process-00> |
| `draft-irtf-cfrg-cpace-21` | CPace remains an Internet-Draft as of 2026-08-20 (`https://datatracker.ietf.org/doc/draft-irtf-cfrg-cpace/`). Not a published RFC at this research date. |
| [Google Private Set Membership](https://github.com/google/private-membership) | Membership-query protocol. README: not an officially supported Google product. |
| [Signal private contact discovery](https://signal.org/blog/private-contact-discovery/) (2017-09-26) | SGX enclave + remote attestation for address-book membership. |
| [Signal ORAM layer](https://signal.org/blog/building-faster-oram/) (2022-08-19) | Path ORAM over enclaves; still a TEE/attestation trust model. |
| Abdalla and Pointcheval, CT-RSA 2005 | SPAKE2 security proof cited by RFC 9382 (`[REF]`). |
| Abdalla et al., CRYPTO 2020 | UC relaxed PAKE; RFC 9382 `[MNVAR]` for M=N / per-user M,N variants. |

Sources not used as cryptographic evidence: blogs/tutorials other than the two official Signal posts above, Stack Overflow, vendor marketing, or AI summaries.

Existing Veil decisions remain authoritative unless ADR 016 records a **conditional** supersession. ADR 007's rejection of `T = H(canonical(A, B))` stands.

## Core problem

ADR 007 rejected the Phase 0.5 tag because a relay that possesses:

- submitter capability `A`
- rendezvous tag `T`
- the finite corpus of every issued/live capability `X`

can compute `H(canonical(A, X))` until it recovers `B`. High capability entropy does not help that relay role. The attack is linear in the live corpus.

Phase 1F re-asks a narrower architectural question:

**Can Veil remove the live-capability corpus from the relay entirely?**

Candidate replacement for ADR 001's **server-issued** capability direction:

- client-generated, independently random, high-entropy contact capabilities
- never registered in raw form with the relay
- no server-side raw-capability registration database

This is not assumed safe because it sounds simpler. Removing issuance also removes server-enforced expiry, one-time consumption, and revocation against a malicious owner. Those tradeoffs are analyzed in the candidate document. They are honest-client product guarantees unless a different mechanism is added.

## What a single relay must not obtain in normal operation

| Item | ADR 002/007 withdrawn model | Phase 1F primary candidate |
|---|---|---|
| Raw contact capabilities | Issued and/or validated by the relay; live corpus exists | Never registered; relay has no raw-ID database |
| Submitter self-capability | Presented to prove possession | Not sent |
| Peer capability | Recoverable by corpus enumeration of the tag | Not sent; not recoverable from the locator without the pair |
| Pair secret / PAKE password | Tag was a deterministic function of the pair | Derived only on clients; not sent |
| Stable identity | Hidden until later handshake | Still hidden until later handshake (ADR 005/015 independently BLOCKED) |

The intended property is architectural: **the relay should not possess a raw-capability corpus in normal operation.** It is not mathematical unlinkability. If a suitable raw-ID dataset is later assembled (screenshots, backups, a candidate list, a full dump), locators become testable: `O(k)` with one known ID plus a candidate set of size `k`, `O(n)` with one known ID plus a full corpus, `O(n²)` with a full corpus and no anchor.

## Corpus-enumeration results (summary)

Full analysis: `docs/review/client-secret-spake2-rendezvous.md`.

| Model | Unmatched target recovery |
|---|---|
| Zero IDs known | Holds architecturally: no corpus to test |
| One ID known, no candidate corpus | Holds against recovery; observer can only confirm an independent guess |
| One ID known + candidate set `S` of size `k` | **Fails if the peer ID is in `S`:** `O(k)` tests of `locator(C_A, X_i)` |
| One ID known + full corpus | **Fails:** `O(n)` |
| No anchor + full corpus | **Fails:** `O(n²)` |
| Database leak of locators/intents only | Cannot recover raw capabilities until combined with an ID dataset; leaks attempts/timing/IP if retained |
| Historical locator + later leaked anchor/corpus | **Fails** once an anchor and/or `S` appears |

Do not claim unlinkability. Completed-match participant correlation (same locator, IPs, timing) remains an accepted V1 limitation if this family is ever deployed.

## Why a locator is not enough

A shared opaque locator proves that *someone who can compute it* opened a mailbox. It does not prove that a second participant knows `(C_A, C_B)`.

A malicious or compromised relay that sees the first party's locator can:

- copy the locator into a second slot
- invent a counterpart blob
- replay an old counterpart

and try to make the first client conclude **mutual pairing complete**.

Any candidate that stops at a deterministic locator is therefore **unsafe against a relay that copies the first locator**. The primary family adds RFC 9382 SPAKE2 with explicit key confirmation so a relay **without `w`** cannot fabricate a valid confirmed transcript. SPAKE2 does **not** prove distinct-owner participation. The relay may still deny service.

## Primary candidate family (review only)

```
client-secret contact capability
+ opaque pair rendezvous locator
+ RFC 9382 SPAKE2 / key confirmation
+ store-and-forward relay
```

Not implemented. Not accepted for implementation. Preferred only as the construction to send to external review (ADR 016).

## Alternatives (refresh)

Ratings are fit observations, not scores. Details: `docs/review/rendezvous-candidate-matrix.md`.

| # | Family | Result for Veil V1 |
|---|---|---|
| 1 | Server-issued capability + `H(A,B)` | **REJECTED** (ADR 007). Linear corpus enumeration. |
| 2 | Client-secret capability + deterministic locator only | Target privacy may improve (no raw corpus, no submitter ID). **Unsafe:** a relay without `w` can still copy the locator and invent a counterpart. |
| 3 | Client-secret + locator + SPAKE2 | **PRIMARY Phase 1F candidate.** Architecturally removes the live corpus. SPAKE2 authenticates pair-secret knowledge and relay-without-`w` forgery resistance (**properties 1–2**). It does **not** prove distinct-owner participation (**property 3**). BLOCKED for implementation pending exact-composition review, including whether Veil requires property 3. |
| 4 | RFC 9497 OPRF/VOPRF/POPRF composition | Primitive hides a client input from an evaluator. It does not create asynchronous mutual pairing, uniform unilateral UX, or one-time/expiry policy. If the relay has **no** raw corpus, OPRF does not address relay-without-`w` transcript forgery (a PAKE does) and does not by itself improve the chosen architecture. If a corpus is reintroduced, a single operator with key + corpus + comparison state may still test candidates (ADR 007). |
| 5 | Blind-issued / anonymous-credential capabilities | Can preserve server-enforced expiry/consumption without handing the relay raw IDs at validation time. Reintroduces issuance infrastructure, a form of issued-record corpus, and high review/ops cost. Not justified for V1 merely to globally enforce honest-client policy. |
| 6 | Google Private Set Membership | Answers membership queries while limiting what client and server learn about a set. Not a one-pair asynchronous mutual-intent protocol. Repository disclaims official Google product support. |
| 7 | Two-server split trust | Can split corpus vs match state **if** a corpus exists. With no raw corpus, it is not required to close ADR 007's specific attack. Adds availability, collusion, and operator cost. Not selected. |
| 8 | Signal-style enclave / ORAM contact discovery | TEE + remote attestation + (in the 2022 post) Path ORAM. Trusts Intel SGX-class hardware and attestation. Materially different from Veil's auditable single-relay model. Not selected because a larger messenger uses it. |

Selecting a construction because Signal or Google deployed a related idea is not a Veil decision rule (ADR 010).

## Decision rule (Phase 1F)

Allowed outcomes:

- **A.** Preferred construction for external review — blocked for implementation
- **B.** No acceptable single-relay construction — blocked
- **C.** Split-trust / TEE required — blocked

**Not allowed:** accepted for implementation, unless an independent cryptographic review of the exact Veil composition already exists. AI analysis is not that review.

Recorded outcome: **A**. See ADR 016.

## Unchanged product constraints

- Mutual action only; no unilateral request/notification/existence oracle.
- Stable identity stays hidden until a later authenticated secure-session handshake (ADR 005/015 independently BLOCKED; libsignal is not unblocked by this phase).
- Relay is honest-but-curious or later compromised; it can drop, delay, reorder, replay, and observe IP/timing.
- Global traffic-analysis resistance is a non-goal.
- No implementation in this phase.
