# Client-secret capability + SPAKE2 rendezvous (Phase 1F candidate)

**Research date:** 2026-08-20.

This document evaluates a **review-only** construction. It does not specify wire formats, choose a KDF primitive, add dependencies, generate capabilities, or implement SPAKE2.

AI analysis is not independent cryptographic review. Implementation remains **BLOCKED**.

Companion: `docs/review/rendezvous-construction-refresh-2026-08-19.md`. Decision: [ADR 016](../decisions/016-rendezvous-construction-candidate.md).

## 1. Candidate family

```
CLIENT-SECRET CONTACT CAPABILITY
+ OPAQUE PAIR RENDEZVOUS LOCATOR
+ RFC 9382 SPAKE2 + EXPLICIT KEY CONFIRMATION
+ STORE-AND-FORWARD RELAY
```

Goal relative to ADR 007: remove the live raw-capability corpus from the relay in normal operation, and authenticate reciprocity so a relay that sees the first locator cannot fabricate a completed mutual pairing.

## 2. Client-generated contact capabilities

### 2.1 Candidate properties

- Generated locally by the owning client.
- At least 256 bits of CSPRNG capability entropy.
- Independently random on every rotation; no deterministic old→new relation.
- No stable identity-derived material, public identity key, device ID, or account identifier.

Candidate serialized metadata (local object, not a server credential):

- format version
- capability type (rotating / one-time / QR)
- expiration
- random capability value
- checksum (integrity of the encoding, not a relay MAC)

The relay must not receive the raw capability merely because it exists. There is **no** server-side raw-capability registration database in this candidate.

### 2.2 Can this replace ADR 001 issuance?

ADR 001 chose **server-issued** opaque credentials so the relay could validate expiry and consumption without storing an identity mapping. Option 2 in ADR 001 (client-generated random capability plus server registration) was deferred because registration/revocation and abuse enforcement were harder.

This candidate goes further: **no registration at all**. That is a conflict with ADR 001's issuance direction, recorded in ADR 016. It is not a silent rewrite.

What issuance actually bought:

| ADR 001 server-issued property | Still available without a raw-ID corpus? |
|---|---|
| High entropy, unlinkable encoded values | Yes, if the client CSPRNG and encoding are correct |
| No stable identity in the ID | Yes |
| Relay validates expiry | **No** — expiry becomes a local encoding check |
| Relay atomically consumes one-time IDs | **No** — consumption becomes local owner policy + locator tombstones |
| Relay revocation list | **No** — owner stops participating and rotates |
| Issuance throttling as abuse control | **No** — abuse moves to IP/rolling limits, size caps, and future optional tokens/PoW |

Honest-user security under Veil's **mutual-only** rule does not require the relay to know raw IDs. A stranger who holds only `C_A` cannot complete pairing unless the owner of `C_A` independently enters the stranger's current ID.

Global server-side ID consumption is **not** treated as a critical honest-user security property for this candidate. It **is** required if Veil insists on enforcing expiry/one-time/revocation against a **malicious or buggy owner client**. That policy is not promised by cryptography here. If a future review declares global consumption mandatory, this candidate becomes **BLOCKED** and issuance (or blind issuance) must be revisited.

## 3. Rotating / one-time / QR without a raw-ID corpus

Current product policies (unchanged as policy values):

| Type | Policy |
|---|---|
| Rotating | 7 days + 6-hour grace |
| One-time | one completed mutual pairing |
| QR | approximately 10 minutes |

Candidate enforcement:

- Expiry is in the shared encoding; both clients check locally.
- Owner refuses new intents that use its own expired IDs.
- Peer refuses locally expired entered IDs.
- An old ID cannot create a match unless its owner independently participates.
- One-time owner permits only one active/completed pairing **on that honest device**.
- Relay may tombstone a **completed pair locator** for bounded replay protection.
- No durable raw-ID revocation database.

### 3.1 Honest-client product guarantees vs malicious-client enforcement

| Property | Honest clients | Malicious / compromised owner |
|---|---|---|
| Expired ID will not start a new pairing | Yes, both sides check the encoding | Owner can ignore expiry; a peer who also skips local checks can still pair |
| One-time ID used for one pairing | Yes, owner marks consumed after confirmation | Owner can reuse the same ID with many peers; each pair has a different locator |
| Stolen ID used without owner | No: mutual entry still required | Same; theft of one ID is not unilateral pairing |
| Stolen ID + owner socially engineered | Owner may enter the thief's ID | Server consumption after first match would have blocked a *second* pairing; local policy only if the first device stays honest |
| Published ID later “revoked” | Owner abandons attempts and rotates | Cannot globally invalidate copies already held |

**Answer:** these semantics can be **safely enforced for honest users** without a raw-ID server corpus. They cannot be globally enforced against a malicious capability owner. Veil already treats possession as a capability, not identity proof (ADR 001). Do not promise what cryptography cannot enforce.

Malformed checksums and locally visible expiry remain explicit local errors (existing product rule). A syntactically valid but meaningless ID still must not become a remote existence oracle.

## 4. Pair secret and rendezvous locator

Both parties possess `C_A` (A's current contact capability) and `C_B` (B's current contact capability) after mutual local entry. Neither raw value is sent to the relay.

Do not approve a precise hash/KDF merely because it is familiar. Required properties:

| Property | Requirement |
|---|---|
| Determinism | Same unordered pair always yields the same pair secret and locator |
| Pseudorandomness to the relay | Without the pair, the locator is indistinguishable from random among valid locator strings |
| Domain separation | Distinct from session keys, mailbox IDs, SPAKE2 `w`, pairing-channel keys, and every other Veil use |
| Canonical encoding | Fixed, unambiguous, length-prefixed byte encoding of each capability; defined total order |
| No identity-derived inputs | Capability bytes only, plus domain labels and version |
| Rotation | New IDs ⇒ new pair secret ⇒ new locator. No stable reuse |
| Logging | Raw capability inputs never appear in logs, URLs, crash reports, or relay traces |

Conceptual split (names only):

1. `PAIR_SECRET` — domain-separated derivation from `canonical(C_A, C_B)`.
2. `RENDEZVOUS_LOCATOR` — separate domain-separated derivation from `PAIR_SECRET` (or from the same canonical pair with a different label). The relay sees only this locator.
3. SPAKE2 shared secret / `w` — yet another domain-separated mapping from `PAIR_SECRET` into RFC 9382's `w` (Section 7).

The relay does **not** see `C_A`, `C_B`, or `PAIR_SECRET`.

Equal-value handling: if the two capability encodings compare equal, treat as local failure (self-pair / impossible collision). 256-bit independent secrets make equality negligible; still reject.

Canonicalization mismatch (different version, length prefix, or sort order) yields different locators and different SPAKE2 roles. Result: failed pairing (availability), not a false success, if both sides fail closed on confirmation.

## 5. Corpus-enumeration analysis

Repeat ADR 007 against this architecture. The relay's stored match representation is the locator `L`, not `H(A,B)` bound to a presented `A`.

### A. Locator, zero raw capabilities

The relay cannot test candidates. There is no corpus. **Enumeration resistance holds architecturally.**

### B. Relay later learns one capability

Given `C_A` and `L`, recovering `C_B` requires inverting the locator derivation. For a reviewed PRF/KDF this is not feasible. The relay **can** confirm a guessed `C_B'` by recomputing `L`. That is a confirmation test, not recovery of an unknown peer.

If the client also still proved possession of `C_A` to the relay, the attack would collapse toward ADR 007. This candidate **must not** send the raw self-capability.

### C. Small subset of unrelated capabilities

For every pair in the subset, recompute locators and compare to stored `L` values. A pair present in both the subset and an unmatched intent is revealed. Unknown capabilities outside the subset are not recovered.

### D. Full raw-capability corpus

**Fails.** For each unordered pair `(X, Y)` in the corpus, compute the locator and compare to `L`. Cost is `O(n²)` in corpus size, not `O(n)` as in ADR 007 (because `A` is no longer given). For a large live set this is still practical for a determined operator, not a 256-bit brute force.

If a full raw-capability corpus exists, this family **does not** provide enumeration resistance. The design's claim is that **normal operation must not give the relay that corpus**.

### E. Database leak of locators/intents only

Opaque locators, SPAKE2 public messages, confirmation MACs, TTLs, and any retained IP/timing. No raw IDs, pair secret, or PAKE key. Leak value is attempt metadata, not target recovery.

### F. Locator leak plus later screenshots/capability leaks

Same as B/C: confirmation of suspected pairs when enough raw IDs become known. Historical locators remain testable forever if copied, even after TTL on the live database.

## 6. RFC 9382 SPAKE2 suitability

Authoritative: [RFC 9382](https://www.rfc-editor.org/rfc/rfc9382.html).

Recorded accurately:

| Claim | Status |
|---|---|
| Symmetric (balanced) PAKE | Yes. Both parties share `w`. Not augmented. RFC 9382 §7: SPAKE2 does not support augmentation; use OPAQUE if needed. |
| Two parties sharing a secret derive a strong shared key | Yes. Output `Ke` after the transcript hash (RFC 9382 §4). |
| Explicit key confirmation | Yes. Second round `cA` / `cB` as MACs over transcript `TT`. “A MUST NOT consider the protocol complete until it receives and verifies `cB`.” Symmetric for B (§3.1). |
| Security proof | Yes. RFC 9382 §7 cites Abdalla–Pointcheval CT-RSA 2005 for prime-order groups, reducing to GDH; M and N generation is critical. |
| Test vectors | Yes. Appendix B, ciphersuite P256-SHA256-HKDF-HMAC. |
| Document class | IRTF Informational (September 2023). **Not** IETF Standards Track. Status section: not a candidate for any Internet Standard. |
| CFRG PAKE competition | SPAKE2 **was not selected**. CFRG recommended CPace (balanced) and OPAQUE (augmented). RFC 9382 was published anyway because of existing Kerberos/other use, and because applications that cannot hash-to-curve at execution time can use SPAKE2. |

Do not call RFC 9382 an IETF Standards Track protocol.

### 6.1 High-entropy secret as SPAKE2 password

RFC 9382 §3.2: protocols **MUST** define how `w` is computed. Typically `w = MHF(pw) mod p`. The hash **SHOULD** be a memory-hard function to slow brute-force of *user passwords*. Standards such as NIST SP 800-56A r3 are cited for reducing mod-p bias.

Veil's pair secret is high-entropy (two 256-bit capabilities). Offline dictionary attack on a low-entropy password is **not** the design threat. Using a memory-hard function is unnecessary for that threat and would add mobile cost.

Required application mapping (not a custom SPAKE2 variant):

- Domain-separate `PAIR_SECRET` into SPAKE2 password bytes.
- Map those bytes to `w` with a specified, bias-aware reduction into the group order, as RFC 9382 requires applications to define.
- Do not change M, N, transcript `TT`, confirmation MACs, or ciphersuite algebra.

Whether omitting MHF for this high-entropy input is acceptable is an **external-review question**. It is not a license to invent a new PAKE.

Ciphersuite is also a review parameter. RFC 9382 Table 1 lists P-256/P-384/P-521, edwards25519, and edwards448 with specified Hash/KDF/MAC combinations. Ristretto255 is **not** an RFC 9382 Table 1 ciphersuite.

### 6.2 Why not switch this phase to CPace

CPace was the CFRG-selected balanced PAKE. As of 2026-08-20 it is `draft-irtf-cfrg-cpace-21`, not a published RFC. Phase 1F was tasked to evaluate RFC 9382 SPAKE2. A reviewer may still prefer CPace; that would be a new candidate, not a silent substitution.

## 7. Role assignment

RFC 9382 §3.1: roles of A and B are assumed agreed. A uses `M` and goes first in the sample flow; B uses `N`. If role assignment is not possible, a symmetric `M=N` variant **MUST** be used (§5).

Veil parties both know both capabilities and each knows which is their own. Deterministic assignment is possible **before** any relay message:

- lexicographically smaller canonical capability encoding → SPAKE2 role A (`M`)
- lexicographically larger → SPAKE2 role B (`N`)

The relay does not need the capabilities to store two slots labeled with application role tags. Those tags are not identities.

### 7.1 Why not `M=N` as the default

RFC 9382 §5: `M=N` **MUST** be used when it is not possible to determine who should use `M` or `N`, or when using a single shared secret with nil identities. The per-user M,N variant “may not be suitable for protocols that require the initial messages to be generated by each party at the same time **and that do not know the exact identity of the parties before the flow begins**.”

In this candidate, both parties know the capability encodings (usable as SPAKE2 identity strings) *before* generating first-round messages. Simultaneous generation is therefore compatible with assigned roles. Assigned `M≠N` gives role separation against naive reflection of `pA` into the `pB` slot.

`M=N` remains a fallback if a reviewer rejects deterministic roles. It is not required by the facts above.

Nil identities are **not** recommended. RFC 9382 §3.3: omitting identities “MUST only be done for applications in which identities are implicit. Otherwise, the protocol risks unknown key-share attacks.” Candidate identities are the canonical capability encodings bound to roles A/B, computed locally, **never sent to the relay**.

### 7.2 Role-related attacks

| Issue | Analysis |
|---|---|
| Reflection | Copy `pA` into B's slot. With `M≠N`, `pB` is `Y + wN`, not `X + wM`. Confirmation still requires knowledge of `w`. Clients MUST abort on invalid group elements (RFC 9382 §7) and SHOULD abort if `pA` equals `pB` when roles are distinct. |
| Role confusion | Canonicalization mismatch assigns opposite roles. Handshake fails closed at confirmation. Availability issue, not false mutuality. |
| Unknown key share | Transcript `TT` includes `len(A)\|\|A\|\|len(B)\|\|B` and `w`. Using the two capability encodings as A/B identities binds `Ke` to that pair. Do not use empty identities. |
| Canonicalization mismatch | Different locators and/or roles; no complete confirmation. |
| Equal-value collision | Reject locally. |
| Malformed capabilities | Local parse/checksum failure; no relay oracle. |
| Relay-visible role labels | Optional slot names (`share-A` / `share-B`). They do not reveal capabilities. Swapping slots is an active attack; confirmation should fail under `M≠N`. |

## 8. Asynchronous store-and-forward

RFC 9382 sample flow is sequential. Algebra of the first round is not:

- `pA = xP + wM` does not depend on `pB`
- `pB = yP + wN` does not depend on `pA`

Each client can generate its first-round value offline, store the ephemeral scalar, and queue `pA`/`pB` under the locator. When the counterpart arrives, both compute `K` and then exchange confirmation MACs. Pairing is **not** complete until required confirmation succeeds.

RFC 9382 already models an active network adversary (modify, drop). A store-and-forward relay is that adversary plus delay, reorder, duplication, and replay. **Do not assume mailbox transport automatically preserves PAKE security.** The application must:

- Treat each `(locator, pA, pB)` pair as one SPAKE2 instance (transcript `TT` includes `pA` and `pB` in a fixed order, not arrival order).
- Never mix confirmation MACs across instances.
- Never reuse `x` or `y` (RFC 9382 §7: reuse “results in significant insecurity”).
- Require both `cA` and `cB` before success.
- Bound how often first-round values may be replaced (replacement changes `TT`; old confirmations must not verify).

This composition (SPAKE2 over an untrusted mailbox) is **not specified** as a profile in RFC 9382. Compatibility of the algebra and the PAKE active-adversary model makes it a reasonable candidate. It is not a proof that Veil's retries, crashes, and TTL rules are safe. That is an external-review requirement.

## 9. Relay must not fabricate mutuality

Successful client state **MUTUAL PAIRING COMPLETE** requires cryptographic peer confirmation: verified SPAKE2 key confirmation for the transcript of this attempt.

| Relay action | Expected honest-client result |
|---|---|
| Copy first party's SPAKE message as second party | Confirmation fails without `w`; abort if `pA = pB` |
| Fabricate random counterpart group element | Client may compute some `K'`; relay cannot compute matching confirmation MACs without `w` |
| Replay old counterpart from the same locator | Old ephemeral is gone or bound to another transcript; confirmation fails or is ignored after completion tombstone |
| Replay an entire old handshake | Completed attempts are locally finished and relay-tombstoned; stale confirmations do not match a fresh `x`/`y` |
| Swap messages between two locators | Different pair secrets ⇒ different `w`; confirmation fails |
| Swap role A/B messages | `M≠N` mismatch; confirmation fails |
| Replay old key-confirmation messages | MAC is over `TT`, which includes this instance's `pA`, `pB`, `K`, `w` |
| Race two different attempts | Attempt/transcript binding; at most one confirmed success; extras fail or are superseded |
| Present different transcripts to each endpoint | Split view: different `Ke`; peer confirmation MAC does not verify |

The relay **can** cause denial of service (drop, delay, fill storage, desynchronize attempts). It **must not** be able to cause an honest client to conclude pairing complete without another party that knows the shared capability pair.

This property is justified by RFC 9382's intended PAKE + mandatory confirmation, **if** Veil instantiates identities, `w`, abort-on-invalid-points, no nonce reuse, and instance binding correctly. Because that “if” is the composition, the candidate is **preferred for review**, not implementation-approved. If a reviewer shows the composition allows forged completion, the candidate is **BLOCKED**.

## 10. Attempt / crash state (requirements only)

No implementation. No forensic-deletion claim.

A SPAKE2 attempt contains an ephemeral scalar that must never be reused.

| Topic | Requirement |
|---|---|
| Storage | Unfinished attempt state (locator, role, ephemeral scalar, outbound first-round message, TTL, transcript/attempt binding) is secret local state, protected like other key material (Keystore-wrapped where applicable). |
| Randomness | Fresh `x`/`y` per attempt; never reuse; CSPRNG. |
| Process death / reboot | Persist enough to resume **or** securely delete and start a replacement with new ephemerals. Do not continue with a lost scalar. |
| Abandoned attempts | Local TTL aligned with remaining ID lifetime / type-bounded rendezvous TTL; then delete attempt state. |
| Replacement | Starting a replacement invalidates prior confirmation for that locator on this device; new ephemerals. Bound replacements to stop livelock. |
| Multiple relay messages | Identify the instance by a client-chosen attempt identifier and/or hash of the current `(pA, pB)` pair. Ignore confirmations that do not match. |
| Key confirmation persistence | After local verification, persist “confirmed” before UX success, then delete ephemeral SPAKE state. Keep only what the later pairing-channel step needs. |
| Duplicate results | If already confirmed, ignore further messages for that locator aside from bounded replay defense. |
| Deletion | Best-effort local delete after completion or expiry. SQLite/WAL/flash remnants may remain; do not claim forensic erasure (`docs/data-retention.md`). |

## 11. Pairing-channel output

If key confirmation succeeds, conceptually derive a temporary `PAIRING_CHANNEL_KEY` from SPAKE2 output `Ke` with a distinct domain label. Use it only to authenticate and encrypt the **post-rendezvous** exchange of **ephemeral pairing material**.

Do **not** in this phase (and not as a silent next step from this document):

- generate stable Veil identity
- implement libsignal
- create a secure session
- expose stable identity before this reviewed rendezvous completes

Later connection, still independently blocked:

1. Confirmed SPAKE2 ⇒ pairing channel.
2. Ephemeral pairing keys travel on that channel (ADR 001/002 conceptual release).
3. Authenticated one-to-one session handshake (ADR 005 family; ADR 015 libsignal v0.99.1 still **BLOCKED**).
4. Stable identity material is revealed and bound only inside that handshake (invariant 6).
5. Safety code after authentication.

libsignal remains independently BLOCKED. Preferring this rendezvous candidate does not start Phase 1G and does not add session code.

## 12. User oracle

Preserve the existing UX invariant.

Before cryptographically confirmed mutual pairing, A learns nothing about whether B exists, B's ID was ever valid, B is online, B entered A, or B rejected anything. B receives no unilateral request or notification.

Relay API responses before completion must be conceptually uniform: accept-to-store, empty-or-blob read, generic failure. No Accept / Decline / Pending request from X / User exists / Invalid remote ID.

Local checksum and local expiry remain local errors.

Non-empty mailbox at a locator is knowledge that **someone who can compute that locator** posted. That is pair-secret knowledge, not a directory lookup. The client still **must not** surface pairing success until confirmation verifies. Failed confirmation, garbage blobs, and empty mailboxes share uniform user-visible waiting/generic-failure behavior after timeout. Do not distinguish “forged peer” from “no peer.”

## 13. Relay observation table

| Observation | Relay sees? | Notes |
|---|---|---|
| Source IP | Yes | Necessary/dangerous; no anonymity claim |
| Request timing | Yes | Correlation |
| Opaque rendezvous locator | Yes | Pseudorandom without the pair |
| Role label | If used, yes | Slot name, not a capability |
| SPAKE2 public messages `pA`/`pB` | Yes | Group elements; not `w` |
| Confirmation messages `cA`/`cB` | Yes | MACs; not `Ke` |
| Attempt TTL | Yes | May weakly correlate with ID type if type-bounded |
| Return/wake handle | If pairing uses V1 push, yes | Existing accepted device correlation |
| Completed-match timing | Yes | Stop-poll / tombstone is visible |
| Raw `C_A` | **No** | Architectural invariant of this candidate |
| Raw `C_B` | **No** | |
| Pair secret | **No** | |
| PAKE-derived key `Ke` / pairing-channel key | **No** | |
| Stable identity | **No** | Still later, still blocked |
| Message plaintext | **No** | No user messages at this layer |

Completed-match participant correlation (two clients, one locator, IPs, timing) is an **accepted V1 limitation**. Pre-match **target recovery** without a raw corpus is the property this family is designed to restore.

## 14. Retention (candidate policy)

Do not reuse message 24-hour retention merely because it exists for ciphertext envelopes. Rendezvous TTL follows ID type, remaining lifetime, and asynchronous mobile behavior, then privacy cost.

Client computes an intent deadline as the minimum of remaining local lifetimes of the two IDs, then clamps to a type cap. The relay stores only that TTL integer plus blobs. Numeric TTL can leak type (QR vs rotating). That leak is accepted as residual metadata unless a later review forces coarser TTL buckets.

| Record | Purpose | Created | Maximum TTL | Deletion trigger | Replay tombstone | Linkable across retries? | Database-leak value |
|---|---|---|---|---|---|---|---|
| Locator mailbox (first-round blobs) | Hold `pA`/`pB` | First store | Type cap below | Expiry, confirmation completion, or replacement bound | n/a (live) | Same locator if IDs unchanged | Opaque locator + group elements + times |
| Confirmation blobs | Hold `cA`/`cB` | After first round | Same cap | Same | n/a | Same locator | MACs + times |
| Completed-locator tombstone | Stop replay of a finished pair | After a client signals completion **or** after both confirmations observed as a storage event | Short: **24 hours** or until the intent deadline, whichever is **shorter** | Tombstone expiry | Yes, this row | Same locator only; not a user graph | Locator + completion time |
| Rate-limit counters | DoS | On request | Short rolling window (hours, not days) | Window slide | n/a | Coarse IP/network | IP/bucket counts |
| Access logs | Incident | Should be off | If enabled: shortest rotation | Rotation | n/a | IP + locator if logged — **forbidden to log raw IDs; locators still sensitive** | High if locators logged |

Type caps (candidate; reviewable):

| ID type | Intent cap | Rationale |
|---|---|---|
| QR | 15 minutes | Face-to-face; ID lifetime ~10 minutes plus small async slack |
| One-time | min(remaining encoded expiry, 7 days) | Must allow delayed peer entry; not unlimited |
| Rotating | min(remaining including grace, 7 days + 6 hours) | Must not outlive the IDs used to compute the locator |

Unmatched state is purged at the deadline. Do not retain a replacement chain of locators.

## 15. Abuse / DoS

Removing issuance moves abuse control off the capability database.

Do **not** create a durable account identifier for rate limiting.

Candidate bounds (reviewable numbers, not implemented):

| Control | Candidate rule |
|---|---|
| Active mailboxes per network source | Short-window cap per IP/prefix; rotate buckets |
| Records per locator | At most one live `pA`, one live `pB`, one live `cA`, one live `cB` (replace in place) |
| Attempts / replacements per locator | Small cap, then reject further stores until TTL |
| Message size | Hard cap near one encoded group element + small framing (reject oversized before storage) |
| Malformed SPAKE | Relay treats blobs as opaque sized bytes. It does not parse SPAKE2. Clients abort on invalid points (RFC 9382 §7). |
| Replay | Completion tombstone; clients ignore stale transcripts |
| Storage exhaustion | Global unmatched-locator cap; TTL; per-source cap |

Future, **not selected** now:

- IP-based short rolling limits — default V1 direction, coarse, evasible by botnets
- Anonymous rate tokens — extra protocol and possibly a tracing/credential design; not casual
- Proof-of-work — accessibility and battery cost; optional later if abuse is measured

A complex anonymous-credential issuance system is **not** selected to replace the removed corpus.

## 16. Malicious clients

| Behavior | Honest-user impact | Policy vs owner |
|---|---|---|
| Client submits both SPAKE2 roles itself | Harmless self-conversation if they hold two capabilities; cannot complete someone else's locator without that pair | Not a relay break |
| Sybil many clients | Storage/DoS; rate limits | Residual farming (existing abuse doc) |
| Owner shares one rotating ID widely | Each pair has its own locator; expected for rotating IDs | Product, not a bug |
| Attacker steals one capability | Cannot compute locators with third parties; cannot pair unless owner enters attacker's ID | Possession is capability |
| Attacker steals both capabilities | Can complete this rendezvous and obtain the pairing channel | Equivalent to being both parties for this pair; later identity/session still separate and blocked |
| Reused one-time ID | Honest owner refuses second; peers cannot detect globally | Malicious owner can reuse |
| Owner ignores expiry | Peer who checks locally refuses | Both skipping checks can pair |
| Malformed locator flooding | DoS | Size/rate caps |

Cryptography authenticates knowledge of the pair. It does not enforce the owner's social policy.

## 17. Attack matrix

| Attack | Rating | Rationale |
|---|---|---|
| Live-corpus enumeration (ADR 007) | **Prevented** in normal operation | No live raw-ID corpus; locator not bound to a presented `A` |
| One-ID-known enumeration | **Mitigated** | Cannot recover the unknown ID; can confirm a guess |
| Full-corpus leak | **Accepted / fails if corpus exists** | `O(n²)` pair test; architecture must not create that corpus |
| Relay active forgery of mutuality | **Prevented** if composition is correct | SPAKE2 confirmation needs `w`; otherwise **blocked/unknown** until review |
| Relay replay | **Mitigated** | Instance binding + tombstone + no ephemeral reuse; relay can still DoS |
| Relay transcript substitution | **Prevented** if confirmation is mandatory | Split `Ke`; MAC fail |
| Unknown key share | **Mitigated** | Capability encodings as RFC 9382 identities; nil identities forbidden |
| Reflection | **Mitigated** | `M≠N` + abort rules + confirmation |
| Stale attempt | **Mitigated** | Local TTL, new ephemerals on replace, ignore mismatched confirmations |
| Duplicate intent | **Mitigated** | Replace-in-place; one live blob per role |
| Race | **Mitigated** | Transcript binding; one success; extras fail; residual desync DoS |
| Capability expiry | **Mitigated** for honest clients | Local checks; **not** globally enforced |
| One-time reuse | **Mitigated** for honest owner | **Accepted** gap vs malicious owner |
| Malicious client | **Accepted** for owner policy | Cannot impersonate a pair they do not know |
| Sybil | **Mitigated** | Rate/size caps; residual farming |
| Database leak (locators only) | **Mitigated** | No raw IDs; attempt metadata remains |
| Log leak | **Mitigated** if locators/IPs redacted | **Accepted** if operators log locators |
| Completed-match metadata | **Accepted** | V1 limitation |
| Relay/service compromise | **Mitigated** for pre-match target recovery | Still DoS, IP/timing, completed-match correlation; E2EE of later messages is a different layer (blocked) |

Ratings other than Prevented/Mitigated/Accepted/Blocked/unknown are not used.

## 18. Rust library inventory (no dependency added)

Inventory only. No crate is selected. Veil pin remains Rust **1.88.0**. Android would consume a future approved crate only through `veil-core` / UniFFI (ADR 014), not Kotlin crypto.

### 18.1 `spake2` (RustCrypto)

| Field | Evidence at 2026-08-20 |
|---|---|
| Upstream | `https://github.com/RustCrypto/PAKEs` (`spake2/` crate) |
| Latest stable | **0.4.0** (2023-07-28). Pre-release **0.5.0-pre.0** (2026-01-25). |
| License | MIT OR Apache-2.0 |
| Maintenance | Active org; README still documents Magic Wormhole / python-spake2 compatibility |
| MSRV | 0.4.0: Rust 1.60. 0.5.0-pre.0: Rust 1.85 |
| Test vectors | Not advertised as RFC 9382 Appendix B. Interop with warner/python-spake2 |
| Audit | README: **never** received an independent third-party audit. “USE AT YOUR OWN RISK.” |
| Android | Pure Rust, `forbid(unsafe_code)`, `no_std` capable; no official Android packaging |
| RFC 9382 exact? | **No.** README links `draft-irtf-cfrg-spake2-10`. Docs cite Boneh–Shoup “PAKE2” and Abdalla–Pointcheval. Includes `start_symmetric()`. Ed25519Group default. Not an RFC 9382 ciphersuite profile with Appendix B vectors. |

### 18.2 `pakery-spake2`

| Field | Evidence at 2026-08-20 |
|---|---|
| Upstream | `https://github.com/djx-y-z/pakery` |
| Latest stable | **0.2.1** (2026-07-13) |
| License | MIT OR Apache-2.0 |
| Maintenance | Young (created 2026-03); ~531 downloads; single-publisher ecosystem. crates.io **0.1.0** published by a different user (`myaiagent100`) than later versions (`djx-y-z`) — supply-chain caution. |
| MSRV | 1.79 |
| Test vectors | README claims validation against RFC 9382 test vectors. **Not independently re-run in this phase.** |
| Audit | None found |
| Android | Pure Rust / `no_std` claimed; not an Android product |
| RFC 9382 exact? | **Claims** RFC 9382. README example uses **Ristretto255**, which is **not** in RFC 9382 Table 1. Treat exact-profile compliance as **unverified**. |

### 18.3 Related crates (not SPAKE2 RFC 9382)

| Crate | Note |
|---|---|
| `pake-cpace` (jedisct1/rust-cpace) | CPace-Ristretto255; last crates.io 0.1.7 (2023-12-15); draft-era, not RFC 9382 |
| `pakery-cpace` | Claims `draft-irtf-cfrg-cpace`; same young workspace as pakery-spake2 |
| `cpace` on docs.rs | Experimental draft; “don’t deploy until 1.0” |
| `opaque-ke` | Augmented PAKE (OPAQUE). Wrong shape: relay would hold a verifier, reintroducing server password-equivalent state RFC 9382 §7 warns about |

**No library is approved.** A future pin must pass ADR 010 gates, match the **exact** RFC 9382 (or reviewer-chosen) profile, and not be added in this phase.

## 19. External review requirements

Minimum scope for the **exact Veil composition**, not “SPAKE2 in general”:

1. Locator/pair-secret domain separation and canonical encoding.
2. Mapping from pair secret to RFC 9382 `w` without a custom PAKE.
3. Role assignment vs `M=N`.
4. Identities in `TT` (capability encodings) never sent to the relay, still binding UKS.
5. Store-and-forward, retries, races, and crash/resume.
6. Proof that a relay without `w` cannot cause confirmation success.
7. Corpus models A–F, including honest statement that a full raw corpus restores pair enumeration.
8. Oracle/uniform-response behavior.
9. Retention and abuse without an account identifier.
10. Test strategy: RFC 9382 vectors for the chosen ciphersuite; corpus-enumeration tests; relay-forgery tests; replay/race; expiry/one-time honest-vs-malicious; log redaction.

Entry criteria in `docs/review/security-review-entry-criteria.md` still require that independent review (or remaining blocked). This Phase 1F package is the candidate write-up those criteria asked for. It does not satisfy the independent-review criterion.
