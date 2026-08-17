# Data retention and deletion limits

The hard product invariant is: normal Veil behavior must not make a message available after 24 hours from its creation timestamp. Server expiry is an upper bound, not a guarantee of physical-media erasure at an exact instant.

| State | why it exists | maximum retention |
|---|---|---:|
| Undelivered ciphertext envelope | offline delivery | creation + 24 h |
| Delivered ciphertext envelope | delivery race handling | delete after authenticated recipient ACK, promptly |
| Rendezvous intent | mutual pairing | ID expiry / match, then promptly purge |
| One-time consumed marker | stop reuse/replay | shortest reviewed replay window |
| Rate-limit state | abuse resistance | short rolling window; no durable personal profile |
| Mailbox handle/session routing | active delivery | until reset/end plus short operational cleanup |
| Relay logs | incident operation | disabled/minimized; short documented rotation only |

On-device, expiry is recorded from a creation time plus a monotonic-time anchor when available, with server-signed/relay time only as a bounded sanity signal, never an authority that can extend lifetime. On launch, before UI rendering or notification display, purge expired rows, attachments-free caches, and session queue entries. Clock rollback must never extend an already observed expiry; clock jumps forward may expire early. The exact trusted-time policy is a blocker.

Encrypted database rows and indexes should be deleted transactionally; temporary plaintext should be avoided and overwritten/best-effort cleared where APIs allow. SQLite/WAL, journal, page cache, filesystem snapshots, flash wear-leveling, OS crash artifacts, and prior device backups can preserve remnants. Therefore Veil must not claim forensic-impossible deletion. Disable Android Auto Backup/data extraction where applicable, exclude encrypted data from backup, avoid plaintext notification/clipboard use, and document OS/version residual risk. Clipboard copy is explicit, warned, and automatically cleared only where the OS permits; screenshots and screen recording cannot be fully prevented.
