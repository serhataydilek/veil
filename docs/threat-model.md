# Threat model

| Threat | capability / asset | protection and mitigation | cannot protect / eventual test |
|---|---|---|---|
| Relay compromise | queues, rendezvous, metadata | E2EE, bounded state, purge | traffic correlation; breach simulation |
| Database leak | persistent relay rows | ciphertext only, short TTL, no ID history | queued metadata; schema/TTL audit |
| Passive observer | network timing/size | TLS, E2EE, optional buckets | global correlation; traffic analysis |
| Active MITM | modify/inject/route | authenticated handshake/ciphertext, pinning policy | compromised endpoint; tamper tests |
| Malicious conversation partner | received plaintext | local block, report choice | peer can copy text; UX tests |
| Locked stolen phone | encrypted local store | Keystore, lock-screen protection | weak OS/device exploit; lock tests |
| Unlocked stolen phone | open UI/data | fast lock, no previews | current user data exposure; UI tests |
| Malware/root | device secrets/plaintext | platform hardening signals | privileged malware; threat disclosure |
| Identity private-key loss | identity/conversations | no recovery by design | unrecoverable loss; recovery-negative test |
| Identity-key replacement | impersonation | explicit warning/safety code | user ignores warning; transition tests |
| Replayed packet or modified ciphertext | session integrity | ratchet IDs, AEAD, expiry | bounded reordering tradeoff; vector tests |
| Expired packet replay | old ciphertext | creation-bound expiry check | device-time limits; restart/clock tests |
| Relationship mapping relay | pairing graph | private rendezvous candidate, scoped handles | active relay timing inference; privacy review |
| Relay-side active capability enumeration | intended peer capability, temporary graph, pair timing | current symmetric tag is rejected; require reviewed construction that prevents a role with live-capability corpus from testing candidates | a relay may infer completed match/timing; corpus-enumeration and database-leak tests |
| ID enumeration | validity/presence | entropy, uniform responses, quotas | stolen/shared ID; oracle tests |
| Spammer creating many identities | many clients | quota/token/PoW options | determined Sybils; load tests |
| Malicious push provider | token/timing | generic wakeup, no content | delivery correlation; payload audit |
| Sensitive logging | developer/operator access | deny-list/redaction, no analytics | OS faults; log scans |
| OS/cloud backup | historical client data | disable/exclude backups | external snapshots; backup tests |
| Clock manipulation | expiry extension | monotonic anchor/no extension | forced early expiry; clock tests |
| Compromised dependency | supply-chain code | pinned/reviewed deps, SBOM/signing later | zero-days; CI audit |
| Protocol downgrade | older weaker mode | authenticated version, fail closed | legacy endpoint denial; downgrade tests |

Threat-model updates are mandatory for new transport, message type, identity change, telemetry, or server persistence.

## Relay-side active capability enumeration

The withdrawn Phase 0.5 tag used the submitted capability A and target capability B in a deterministic unordered hash. A curious or compromised relay that knows A, observes the tag, and can enumerate its finite live-capability set computes one candidate tag per live X until it finds B. This is linear in the live corpus, not a brute-force attack on a 256-bit unknown. High capability entropy protects an outsider without that corpus; it does not protect the relay or a database thief holding issued/live records and rendezvous records. Client rate limiting cannot stop the relay's offline computation. Capability expiry and deletion reduce future leak value only if records/logs were actually purged; copied records preserve historical risk.

The simple tag is therefore not relay-opaque and is prohibited. The future minimum is no unilateral user oracle; the desired property is that no single relay role can recover an unmatched target by testing its live corpus. At completed match time, the relay may still infer a temporary pair and timing unless a stronger reviewed deployment is adopted. Global traffic-analysis protection is not a Veil claim.
