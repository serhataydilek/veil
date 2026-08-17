# Cryptographic dependency acceptance gates

A cryptographic dependency is approved only after every mandatory gate passes for a pinned candidate version. This document creates no approval.

| Gate | Mandatory evidence |
|---|---|
| Security | Maintained upstream; no known unresolved critical advisory affecting intended use; protocol/version fit; audit/review evidence understood; update process defined; debug plaintext/key logging disabled. |
| Integration | Viable Android path; Rust boundary understood; persistence and crash consistency compatible; offline prekey/session lifecycle fit; bounded CPU/memory/storage; usable official/adopted vectors. |
| Licensing | License and attribution documented; distribution obligations understood; Android distribution reviewed; future iOS implications reviewed before iOS work. **LEGAL REVIEW REQUIRED** wherever obligations or store compatibility are material. |
| Operational | Exact version/lock policy; upgrade and security-advisory monitoring; reproducibility plan; SBOM; transitive dependency review; incident response/rollback plan. |
| Independent review | External review scope covers the chosen version, integration assumptions, storage, identity binding, prekey/session lifecycle, and changed threat model—not merely a primitive/library name. |

Failure or missing evidence leaves the candidate `BLOCKED`; convenience, active Git history, package publication, or an AI assessment does not waive a gate.
