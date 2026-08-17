# Privacy claims audit

**Audit date:** 2026-08-17. These labels govern future product/README/marketing claims. Architecture intent is not shipped behavior.

| Claim | Status | Required qualification |
|---|---|---|
| Server plaintext is forbidden by the architecture | CURRENT ARCHITECTURAL GUARANTEE | Not an implemented/deployed system claim; endpoints and operations still require implementation and review. |
| No unilateral user existence/reciprocity oracle in product semantics | CURRENT PRODUCT REQUIREMENT | Future transport/protocol/error paths must preserve it. |
| Local aliases, no profiles/discovery/presence/read/typing, generic push content, and 24-hour maximum availability are architecture/product rules | CURRENT ARCHITECTURAL GUARANTEE | Enforce and validate before release; OS/endpoint limits remain. |
| Forward secrecy, post-compromise security, authenticated session, safety verification | TARGET PENDING IMPLEMENTATION | Depends on approved library/version, correct integration/persistence, and review. |
| Pre-match relay inability to recover intended peer from a live corpus | BLOCKED / NOT YET IMPLEMENTABLE | No rendezvous construction is selected or externally reviewed. Do not claim relay-opaque pairing. |
| Contact-ID unlinkability and mailbox rotation | TARGET/QUALIFIED | Serialized IDs are designed not to carry stable identity; relay/IP/timing/push correlation and live overlap remain. |
| E2EE confidentiality | TARGET PENDING IMPLEMENTATION | Requires approved secure-session dependency and implementation; never implies anonymity or endpoint security. |
| Audited, reviewed, secure, safe, production ready, anonymous, or unlinkable | NOT APPROVED AS UNQUALIFIED CLAIMS | Name the exact component, version, scope, date, and limitation if external evidence later supports a limited claim. |

No current document may describe a blocker as implemented merely because a design direction exists.
