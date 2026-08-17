# Data retention and deletion limits

The hard product invariant is: normal Veil behavior must not make a message available after 24 hours from its creation timestamp. Server expiry is an upper bound, not a guarantee of physical-media erasure at an exact instant.

| State | why it exists | maximum retention |
|---|---|---:|
| Undelivered ciphertext envelope | offline delivery | creation + 24 h |
| Delivered ciphertext envelope | delivery race handling | delete after authenticated recipient ACK, promptly |
| Rendezvous intent | mutual pairing | ID expiry / match, then promptly purge |
| One-time consumed marker | stop reuse/replay | shortest reviewed replay window |
| Rate-limit state | abuse resistance | short rolling window; no durable personal profile |
| Mailbox epoch routing | active epoch + bounded overlap | queued-message expiry or authenticated revocation, then promptly purge |
| Relay logs | incident operation | disabled/minimized; short documented rotation only |

On-device, expiry is recorded from an authenticated creation/expiry pair plus a boot-session monotonic anchor and persisted non-decreasing wall-clock lower bound. Relay ingress may shorten a queue deadline but can never extend the authenticated expiry. On launch, before UI rendering or notification display, purge expired rows, attachments-free caches, and session queue entries. Clock rollback, reboot ambiguity, or invalid ordering must fail closed (expire early); clock jumps forward may also expire early. Genuine human creation time cannot be cryptographically proven against a malicious sender.

Encrypted database rows and indexes should be deleted transactionally; temporary plaintext should be avoided and overwritten/best-effort cleared where APIs allow. SQLite/WAL, journal, page cache, filesystem snapshots, flash wear-leveling, OS crash artifacts, and prior device backups can preserve remnants. Therefore Veil must not claim forensic-impossible deletion. Disable Android Auto Backup/data extraction where applicable, exclude encrypted data from backup, avoid plaintext notification/clipboard use, and document OS/version residual risk. Clipboard copy is explicit, warned, and automatically cleared only where the OS permits; screenshots and screen recording cannot be fully prevented.
