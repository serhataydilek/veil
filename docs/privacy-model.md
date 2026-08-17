# Privacy model

Veil aims to minimize data, not provide perfect anonymity. The relay can observe connections and routing metadata; a global passive adversary can correlate timing. A malicious peer can retain plaintext it legitimately decrypts. Compromised/unlocked devices and malware defeat client confidentiality.

Privacy properties sought: no server plaintext, no public directory, no profiles or social graph feature, no contact upload, no presence/read/typing signals, local-only aliases, and expiry-bounded ciphertext queues. Pairing intentionally requires mutual action and gives neither unilateral party an existence or online signal.

The former capability-pair hash is not relay-opaque: a relay with the live-capability corpus can enumerate targets from a submitted self-capability and deterministic tag. Rendezvous is BLOCKED pending a reviewed construction that prevents this pre-match recovery. Even then, Veil may accept that a relay infers a completed temporary match and timing. Rotating contact IDs/mailbox epochs reduce identifier/history retention but cannot promise to hide source IP, connection timing, traffic volume, live epoch overlap, or the fact that it routes to a mailbox. Padding into a small set of ciphertext size buckets reduces exact length leakage but costs bandwidth and does not hide timing; use only after measurement and review. Delays/cover traffic are not promised.

For V1, Veil knowingly accepts direct relay push-token mapping for reliable background delivery. A push token is a pseudonymous device-level correlation point: the relay can link its multiple mailbox epochs, conversations, and notification activity while that mapping exists. Default notification text is exactly `New message`; no alias, identity, plaintext, contact ID, mailbox ID, count, preview, or conversation label. Contents remain hidden, but rotating IDs do not make the device unlinkable to the relay. A separate wake-up service is not justified in V1; polling and reduced-metadata modes remain later evaluations.

No analytics SDKs, ads, third-party crash analytics, or cloud conversation backup are permitted. Security telemetry, if ever added, requires a separate privacy review, opt-in design, and a documented aggregate-only schema.

## Product-state privacy rules

The client never translates an entered/saved ID, retry, timeout, or background activity into a peer fact. It has no request, acceptance, decline, account, presence, typing, last-seen, or read state. A generic remote failure must not distinguish an absent, expired, revoked, blocked, offline, or non-reciprocating peer. Conversation `Sent`, if surfaced, means only relay acceptance; recipient-client ACK is operational deletion permission and never a visible receipt. Unsent drafts are not persisted, and expired state is purged before display. See `docs/product/privacy-ux.md`, `docs/product/error-model.md`, and `docs/state/`.
