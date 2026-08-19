# Security review entry criteria

Veil must not begin cryptographic implementation until these entry criteria are met. Documentation and AI-generated analysis do not replace independent review.

## Rendezvous

- A candidate construction is selected with a full role/observation model. Phase 1F records ADR 016 as that candidate write-up; implementation remains blocked.
- The live-corpus enumeration attack is addressed, or a lower privacy target is explicitly accepted and reflected in product/privacy claims. ADR 016 addresses it architecturally by removing the raw corpus in normal operation, and states that a later full corpus would restore pair enumeration.
- Independent cryptographic review is complete for the exact composition, or implementation remains blocked. Phase 1F documentation does not satisfy this gate.
- Test strategy includes corpus enumeration, malformed-input oracle, replay/duplicate/race, expiry/one-time, TTL/deletion, failure, collusion, and malicious-client tests.
- TTL/state/abuse model is approved without a unilateral user notification or existence oracle.

## Secure session

- A pinned library/version is selected through ADR 010's acceptance gates.
- License/distribution obligations have **LEGAL REVIEW REQUIRED** evidence.
- Upstream third-party support posture, API, persistence/crash behavior, prekey/session lifecycle, and resource bounds are understood.
- Official/adopted vectors and applicable security-review evidence are available and evaluated.

## General

- No contradiction with security invariants; no server plaintext; no silent identity replacement; no user-existence oracle.
- Scope remains one-to-one, text-only, without groups, media, or discovery.
- Dependency policy, advisory response, SBOM/provenance, and review/update ownership are prepared before integration.
