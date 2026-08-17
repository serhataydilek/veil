# Abuse resistance

Mutual pairing removes unsolicited inbound chat: knowing one ID does not create a conversation, expose its validity, or notify its owner. Contact IDs are high-entropy, expiry-bound capabilities and inputs must have uniform, non-oracular responses. Do not expose search, bulk import, invite discovery, broadcast, groups, bots, or mass messaging.

The relay may apply short-window, privacy-reviewed abuse controls to unauthenticated connection attempts and authenticated but unlinkable-ish rendezvous actions: IP/network-edge controls, proof-of-work or privacy-preserving tokens, per-device/credential quotas, and one-time-ID issuance limits. Each control has evasion, accessibility, and privacy costs; none becomes a hidden account/profile. Rate-limit records are coarse, purpose-bound, and short-lived. Repeated device farming remains an acknowledged residual risk.

Blocking is a local deny rule that prevents a peer/session from producing visible messages and can discard future envelopes. It needs no plaintext server inspection. Reset/destroy removes mailbox/session credentials locally and sends an authenticated best-effort revocation only if the selected protocol permits; stale packets never establish a replacement session.

Reporting is opt-in and explicit per evidence item. The reporting UI identifies exactly which locally decrypted text, sender safety information, and context will be submitted; nothing is silently copied to the server. Reports are a separate product/legal policy and must not introduce routine plaintext scanning or server decryption.
