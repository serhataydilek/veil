# ADR 007: rendezvous enumeration and linkability review

## Status

**BLOCKED PENDING EXTERNAL REVIEW OF A NEW CONSTRUCTION.** ADR 002's simple symmetric-hash V1 candidate is withdrawn; it remains recorded for history and must not be implemented.

Phase 1F records a **preferred review candidate** in [ADR 016](016-rendezvous-construction-candidate.md) (client-secret capabilities + opaque locator + RFC 9382 SPAKE2). That candidate is **not** accepted for implementation. This ADR's rejection of enumerable pair tags and its requirement for independent review **stand**.

## Context

Phase 0.5 proposed a tag derived from the unordered pair of A's and B's high-entropy contact capabilities. It correctly prevents an ordinary outside attacker from guessing arbitrary 256-bit capabilities, but it did not distinguish that threat from a relay that issues or retains the finite set of live capabilities.

## Threats / constraints

Veil requires no unilateral existence/reciprocity signal to users, short-lived public capabilities, offline mobile operation, limited state, and auditability. The relay is honest-but-curious or later compromised. It may know the submitter capability A, observe tag T, and enumerate every currently issued/live capability X. A database leak may yield the same corpus plus unmatched rendezvous records.

## Attack discovered

For `T = H(canonical_order(A, B))`, a relay computes `H(canonical_order(A, X))` for each live X and compares it with T. A match recovers B and reveals A→B interest before B reciprocates. This is O(number of live capabilities), not a 2^256 brute force. High capability entropy prevents outsiders who lack the issued-capability corpus from guessing B; it does not protect against a relay that possesses that corpus. Client rate limiting reduces abusive submissions but does not constrain the relay's offline enumeration. After expiry, deleted capability records reduce later leak value, but retained logs, live databases, or an attacker who copied records can still enable historical reconstruction.

## Options considered

| Option | privacy result | decision |
|---|---|---|
| Simple symmetric hash | relay recovers target by corpus enumeration | rejected as unsafe/insufficient |
| Single-server OPRF/VOPRF | hides a client's raw input during an RFC 9497 evaluation, but does not by itself stop one operator that has both the OPRF key/candidate corpus and final comparison state from testing candidates | not sufficient alone |
| Purpose-designed blind rendezvous using OPRF/PSM components | may prevent any one role from holding both test corpus and match representation | promising, but a complete asynchronous mutual protocol, abuse controls, and deployment model are unselected |
| Two-server split trust | non-colluding services can split capability corpus/key and match state | stronger potential privacy, but adds availability, operator independence, collusion assumption, and incident complexity; not realistic for unproven V1 without committed operators |
| Generic PSI/contact discovery | can privately answer membership/intersection questions | not selected: it does not by itself create asynchronous mutual pairing, consent, one-time use, or bounded intent lifecycle; likely excessive for one pair |
| Accept relay match metadata | relay may infer a pair at completed match | accepted limitation only after a construction prevents pre-match target recovery |

RFC 9497 defines OPRF/VOPRF/POPRF primitives, not Veil's rendezvous state machine. In particular, adding an OPRF without clearly separating which service sees raw candidate data, OPRF key material, and final match representation would be security theater. See [RFC 9497](https://www.rfc-editor.org/rfc/rfc9497.html). Google’s public PSM project illustrates membership-query scope, but its repository also does not specify Veil's mutual asynchronous workflow or provide a deployment decision for it: [Private Set Membership](https://github.com/google/private-membership).

## Decision

Do not implement, describe as opaque, or accept the symmetric-hash candidate. Preserve the following privacy target for the future reviewed construction:

- **Minimum requirement:** a unilateral user receives no indication that an ID exists, is valid, is online, or has reciprocated; the peer receives no request/notification.
- **Desired relay property:** before a true match, no single relay role should trivially recover the submitted peer capability by enumerating known live capabilities.
- **Accepted limitation:** at a completed match, the delivery architecture may let the relay infer that two temporary rendezvous participants matched, unless a stronger reviewed deployment is selected. It must not retain that association as a permanent graph.
- **Non-goal:** Veil does not promise anonymity or protection against global traffic analysis, IP/timing correlation, colluding services, or a malicious endpoint.

The construction remains blocked until external cryptographic review selects a deployable design and documents exactly what each service can observe. A future single-server design must demonstrate why a server with the live-capability corpus cannot perform the above test; otherwise it fails this ADR. A two-server design may be reconsidered only with independently operated non-colluding services, availability/failure rules, and a realistic abuse-control plan.

## Security consequences

No current V1 rendezvous protocol exists. The product rule—mutual action before conversation—remains a requirement, not authorization to ship a hash-based matcher. Post-match secure-session authentication remains separate and does not repair pre-match graph disclosure.

## Privacy consequences

Rotating contact IDs remain unlinkable in their serialized values, but neither rotation nor high entropy guarantees relay privacy. Mailbox rotation also reduces future retention value only; it does not eliminate live relationship or device correlation.

## Operational consequences

Phase 1 cannot implement mutual pairing until this blocker closes. Any candidate must state role separation, retained records/TTL, key custody, client round trips, offline behavior, failure/collusion behavior, rate limits, and independent test/audit plan.

## Residual risks

Even a stronger construction may still expose completed-match timing, source IP, traffic volume, and denial of service. A two-server construction loses its special property if services collude or one operator obtains both datasets.

## Implementation requirements

- Do not add a rendezvous endpoint based on `H(A,B)` or any equivalent enumerable deterministic pair token.
- Document each service's inputs, keys, output, logs, and deletion schedule before implementation.
- Require an external cryptographic design review, test vectors, corpus-enumeration tests that demonstrate resistance to the modeled attack, and privacy abuse review. Testing cannot cryptographically prove a rendezvous construction safe.
- Keep no unilateral UI/push signal regardless of future construction.

## Tests required

Model a relay with all live capabilities and recorded intents; prove by attack test that it cannot recover an unmatched target under the chosen design. Add collusion/failure tests for any split-trust design, uniform-response tests, replay/expiry/one-time/race tests, retention/log leak tests, and independent review.

## Open issues

The final reviewed mutual-rendezvous protocol/construction is a BLOCKER. Whether Veil can justify split trust or must explicitly accept more relay metadata is unresolved; accepting pre-match target recovery is not approved.

## Phase 0.8 review readiness

`docs/review/rendezvous-review-brief.md`, candidate matrix, and question set package the threat model for external review. They do not select a construction or convert the desired relay property into a current guarantee. RFC 9497 remains a primitive reference, not an approval of any Veil composition.
