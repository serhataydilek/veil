# Rendezvous candidate matrix

**Research date:** 2026-08-17. Ratings are fit observations, not proofs or security scores. Every non-blocked-looking family still needs a complete design and independent review.

| Candidate | Standard/reference | Single server | Split trust | Corpus enumeration protection | Async / mobile / offline | Abuse control | Complexity / auditability | Residual metadata | Maturity | Veil recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| Deterministic pair tag `H(A,B)` | Rejected in ADR 007 | YES | NO | POOR: relay enumerates corpus | GOOD / GOOD / GOOD | GOOD | GOOD / GOOD | Pre-match A→B interest | Simple | REJECTED |
| Single OPRF/VOPRF/POPRF composition | [RFC 9497](https://www.rfc-editor.org/rfc/rfc9497.html) | POSSIBLE | NO | UNKNOWN/MIXED: primitive hides a client input from evaluator, but one operator with key, corpus, and comparison state may still test candidates | MIXED / MIXED / GOOD | MIXED | HIGH / MIXED | Depends on composition/state roles | Standard primitive, no Veil workflow | BLOCKED pending a reviewed full construction |
| Private set membership/contact discovery | [Google PSM](https://github.com/google/private-membership) | POSSIBLE | NO | MIXED: membership-query privacy is not reciprocal rendezvous privacy | POOR / MIXED / MIXED | MIXED; corpus publishing/update and quota design required | HIGH / MIXED | Query/batch/update metadata | Implementation is not an officially supported Google product | NOT DIRECTLY ADAPTABLE; review only |
| Two-server split-trust matching | No selected standard composition | NO | YES | POTENTIALLY GOOD only with independent non-colluding operators and real key/corpus/match separation | MIXED / MIXED / MIXED | POOR/MIXED; cross-service controls risk correlation | VERY HIGH / POOR until specified | Collusion, availability, completed-match timing | Design-dependent | BLOCKED; assess only if operators are realistic |
| Explicit relay metadata acceptance | Product policy, not cryptographic family | YES | NO | POOR if it accepts pre-match recovery | GOOD / GOOD / GOOD | GOOD | GOOD / GOOD | Explicit pre-match target/interest graph | Simple | NOT APPROVED without a documented privacy downgrade and product-claim change |

PSM answers membership questions while limiting what a querying client learns about a server-held set; it is not a complete one-pair asynchronous mutual-intent protocol and its public repository disclaims official Google product support ([Google PSM](https://github.com/google/private-membership)).
