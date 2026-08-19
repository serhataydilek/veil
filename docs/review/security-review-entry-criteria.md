# Security review entry criteria

Veil must not begin cryptographic implementation until these entry criteria are met. Documentation and AI-generated analysis do not replace independent review.

## Rendezvous

- A candidate construction is selected with a full role/observation model. Phase 1F records ADR 016 as that candidate write-up; implementation remains blocked.
- The live-corpus enumeration attack is addressed, or a lower privacy target is explicitly accepted and reflected in product/privacy claims. ADR 016 addresses the **server-created** corpus by not retaining raw IDs in normal operation, and states that a later assembled candidate set enables `O(k)` recovery given one known ID.
- Independent cryptographic review is complete for the exact composition, or implementation remains blocked. Phase 1F documentation does not satisfy this gate.
- Test strategy includes corpus enumeration (including one known ID plus a candidate set `S`), malformed-input oracle, replay/duplicate/race, expiry/one-time, TTL/deletion, failure, collusion, malicious-client tests, **both-roles simulation**, and **stolen-both-capabilities** behavior. Distinct-owner participation must not be treated as proven by SPAKE2 alone.
- TTL/state/abuse model is approved without a unilateral user notification or existence oracle.
- Product/review must record whether pair-secret knowledge is sufficient or whether cryptographic distinct-owner proof is required. Independent review remains mandatory either way.

## Secure session

- A pinned library/version is selected through ADR 010's acceptance gates.
- License/distribution obligations have **LEGAL REVIEW REQUIRED** evidence.
- Upstream third-party support posture, API, persistence/crash behavior, prekey/session lifecycle, and resource bounds are understood.
- Official/adopted vectors and applicable security-review evidence are available and evaluated.

## General

- No contradiction with security invariants; no server plaintext; no silent identity replacement; no user-existence oracle.
- Scope remains one-to-one, text-only, without groups, media, or discovery.
- Dependency policy, advisory response, SBOM/provenance, and review/update ownership are prepared before integration.
