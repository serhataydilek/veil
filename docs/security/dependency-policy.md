# Sensitive dependency policy

This is a future implementation policy; it creates no build tooling.

- Pin exact sensitive cryptographic dependency versions where the ecosystem supports it; review lockfile changes deliberately.
- Do not automatically apply major security-dependency upgrades. Review release notes, changelog/release history, security advisories, compatibility, vectors, and migration impact first.
- Maintain advisory monitoring, an SBOM, provenance records, and a reproducible-build goal before release.
- Review all direct and material transitive dependencies; treat crypto-provider changes as security changes.
- Ban unmaintained crypto crates and custom cryptographic forks unless explicitly reviewed and accepted through a new decision.
- Do not vendor cryptographic code to evade licensing, support, or update obligations.
- Never silently replace a crypto dependency or weaken suite/version negotiation. Require documented approval, test vectors, persistence migration review, and external-review reassessment when the threat model changes.
- Production configurations must forbid debug key/plaintext/content logging and verify redaction.
