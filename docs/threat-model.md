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
| ID enumeration | validity/presence | entropy, uniform responses, quotas | stolen/shared ID; oracle tests |
| Spammer creating many identities | many clients | quota/token/PoW options | determined Sybils; load tests |
| Malicious push provider | token/timing | generic wakeup, no content | delivery correlation; payload audit |
| Sensitive logging | developer/operator access | deny-list/redaction, no analytics | OS faults; log scans |
| OS/cloud backup | historical client data | disable/exclude backups | external snapshots; backup tests |
| Clock manipulation | expiry extension | monotonic anchor/no extension | forced early expiry; clock tests |
| Compromised dependency | supply-chain code | pinned/reviewed deps, SBOM/signing later | zero-days; CI audit |
| Protocol downgrade | older weaker mode | authenticated version, fail closed | legacy endpoint denial; downgrade tests |

Threat-model updates are mandatory for new transport, message type, identity change, telemetry, or server persistence.
