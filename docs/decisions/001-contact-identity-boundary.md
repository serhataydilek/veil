# ADR 001: unlinkable public contact capabilities

## Status

Accepted for V1 architecture; exact encoding and issuance abuse controls require review.

**Phase 1F issuance conflict:** [ADR 016](016-rendezvous-construction-candidate.md) records a **preferred review candidate** that would replace **server-issued** capabilities with **client-generated secrets never registered in raw form**. That supersession is **conditional and BLOCKED for implementation** pending external review. This ADR's issuance/registration direction remains the current accepted decision until that review approves a replacement. Unlinkable encoding rules (no stable public key, device ID, account, or deterministic old→new relation) are not withdrawn. Do not treat ADR 016 as production authorization.

## Context

Veil needs rotating, one-time, and QR contact IDs without turning its stable device identity into a public address. A person holding one temporary ID must not be able to link it to another period's ID, query an identity, or obtain a peer's authentication key before mutual pairing.

## Threats / constraints

The relay, a recipient, a screenshot recipient, or a database thief can compare all contact IDs they see. IDs must resist guessing and enumeration, expire, support one-time use and short grace, and have no durable old-to-new server history. There is no account or recovery service.

## Options considered

1. **Embed a stable public key/fingerprint.** Easy peer authentication; rejected because every ID becomes linkable.
2. **Client-generated random capability plus server registration.** Can be unlinkable, but requires a reviewed registration/revocation design and makes validity/abuse enforcement more difficult.
3. **Server-issued opaque capability.** A relay can validate expiry and consumption without identity data; it is the chosen V1 direction.
4. **Derived rotating public identifier.** Rejected for V1: derivation/binding creates a high review burden and risks linkability.

## Decision

V1 contact IDs are opaque, independently random 256-bit bearer capabilities, encoded with version, type, expiration, and a checksum. The serialized ID contains **no stable public key, fingerprint, device identifier, account-like handle, or deterministic identity-derived value**. The service issues a signed or MAC-authenticated opaque credential containing only capability material and expiry; it stores no identity mapping merely to validate it. The issuing/validation key and capability database are server secrets, not user identity data.

The client retains the raw capabilities locally. A rotating capability is issued for seven days with an explicit six-hour acceptance grace; replacement is a fresh random value, not a derivation. One-time capabilities are atomically consumed only after a completed mutual match and otherwise expire. QR capabilities expire at ten minutes. Revocation is an explicit best-effort invalidation of the currently presented capability; it is not a mechanism to discover a replacement ID. The relay retains only an expiry/consumption/revocation record until the stated cleanup period, never a replacement chain.

A device creates an ephemeral pairing key for each pairing intent. Stable identity authentication material is encrypted to the peer's ephemeral pairing key and released only after mutual rendezvous; it is then bound and authenticated in the selected secure-session handshake. This binding is a protocol-review requirement, not a new primitive.

## Security consequences

Possession is a high-entropy capability, not proof of identity. ID theft enables attempted pairing until expiry, so users must share IDs through an appropriate channel. The relay can validate a presented self-capability and enforce expiry/one-time use, but cannot infer a stable identity from the identifier alone.

## Privacy consequences

Weekly IDs are not trivially linkable through their content. A relay may still correlate issuance, IP, timing, or a user reusing a published ID; this design reduces only identifier-level linkability and makes no anonymity claim.

## Operational consequences

The relay needs key rotation for credential validation, expiry cleanup, atomic consumption, and a minimal revocation store. Issuance throttling is separate short-lived abuse state, not an account system.

## Residual risks

Server compromise can expose live capabilities and timing. Screenshots, copied IDs, and network correlation are outside the ID format's protection. The credential format and the release of identity material need independent cryptographic review.

## Implementation requirements

- Use an audited CSPRNG and constant-time credential validation library.
- Treat IDs as secrets: redact them from logs, URLs, crash reports, and notifications.
- Version the format; reject unknown versions and never fall back to a weaker format.
- Persist only capability status with explicit TTL; purge expired rotating IDs after grace.

## Tests required

Statistical/non-determinism tests across rotations; parser/checksum fuzzing; expiry/grace/revocation/atomic-consumption tests; database schema and retention audit; log redaction test; and an external review of format, issuance, and pairing-key binding.

## Open issues

The exact issuer credential mechanism, revocation authentication, issuance quota, and whether blind issuance materially improves the threat model remain IMPORTANT. This decision does not resolve rendezvous privacy.
