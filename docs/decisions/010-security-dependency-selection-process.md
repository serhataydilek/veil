# ADR 010: security dependency selection process

## Status

Accepted process. No cryptographic dependency or rendezvous construction is selected.

## Context

Veil's secure-session library and mutual rendezvous construction remain BLOCKED. Protocol-family appeal, source availability, packaging, or documentation cannot establish that a dependency is supportable, licensable, safely integrated, and externally reviewed for Veil.

## Decision

Before implementation, evaluate a pinned candidate through `docs/review/dependency-acceptance-gates.md` and follow `docs/security/dependency-policy.md`. Mandatory evidence covers security, integration/persistence, licensing, operations, and independent review. Legal questions are marked **LEGAL REVIEW REQUIRED**, not decided here. A rejected or incomplete candidate remains blocked; Veil will not custom-build, silently fork, or vendor cryptography to close the gap.

## Consequences

Selection takes longer but makes library version, support posture, storage semantics, advisory response, distribution obligations, and review scope explicit. It preserves the distinction between choosing a protocol direction and approving a library.

## Residual risks

Evidence can age and audits are scoped snapshots. The process requires future re-evaluation for version, architecture, or threat-model changes; it is not a security guarantee.

## Open issues

libsignal and OpenMLS remain unapproved. The final rendezvous construction remains a separate BLOCKER under ADR 007.
