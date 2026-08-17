# Privacy model

Veil aims to minimize data, not provide perfect anonymity. The relay can observe connections and routing metadata; a global passive adversary can correlate timing. A malicious peer can retain plaintext it legitimately decrypts. Compromised/unlocked devices and malware defeat client confidentiality.

Privacy properties sought: no server plaintext, no public directory, no profiles or social graph feature, no contact upload, no presence/read/typing signals, local-only aliases, and expiry-bounded ciphertext queues. Pairing intentionally requires mutual action and gives neither unilateral party an existence or online signal.

The relay should minimize linkability through short-lived rendezvous state, per-session opaque mailbox handles, narrowly scoped rate-limit buckets, and deletion. It cannot promise to hide source IP, connection timing, traffic volume, or the fact that it routes to a mailbox. Padding into a small set of ciphertext size buckets reduces exact length leakage but costs bandwidth and does not hide timing; use only after measurement and review. Delays/cover traffic are not promised.

Push providers receive a device token and generic wake-up event at minimum. Default notification text is exactly `New message`; no alias, identity, plaintext, count, preview, or conversation label. Options to evaluate are provider wake-up-only pushes, polling, and user-selectable delayed polling; each trades battery, latency, and metadata differently.

No analytics SDKs, ads, third-party crash analytics, or cloud conversation backup are permitted. Security telemetry, if ever added, requires a separate privacy review, opt-in design, and a documented aggregate-only schema.
