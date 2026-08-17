# Protocol boundaries (conceptual, not a wire protocol)

No proprietary cryptography or final packet encoding is specified here. All fields, canonical serialization, error behavior, and state transitions require protocol review and test vectors before implementation.

| Layer | responsibility | protection requirement |
|---|---|---|
| Identity | device identity and authenticated public keys | private key local; key changes explicit |
| Rendezvous | mutually matching pairing intents | expiry, anti-probing, authenticated intent, no unilateral result |
| Secure Session | one-to-one key agreement and ratchet | authenticated encryption, forward secrecy, post-compromise security |
| Message Envelope | opaque routing/padding container | version, mailbox, expiry, sequence/message id authenticated |
| Delivery | bounded relay queue and acknowledgement | authenticated ACK; idempotent, no read semantics |
| Expiry | reject/purge expired data | server and client enforce creation-based upper bound |
| Transport | byte movement only | TLS/channel protection plus end-to-end envelope; no crypto decisions |

For one-to-one messaging, prefer an independently reviewed Signal-style asynchronous double ratchet implementation/protocol family with a reviewed prekey mechanism, rather than writing one. It naturally addresses offline initial delivery, forward secrecy, post-compromise security, authentication, and out-of-order delivery when correctly deployed. MLS/OpenMLS offers standardized group-state machinery and future extensibility, but adds substantial group-oriented complexity, state/commit semantics, operational surface, and does not solve rendezvous privacy; it is not the default for Phase 0's one-to-one scope. This choice remains a BLOCKER pending expert review and a maintained Rust implementation audit.

Ciphertexts must be authenticated; modified packets fail closed. Each session needs unique message identifiers and ratchet/sequence state to reject duplicate/replayed packets while tolerating an explicitly bounded reordering window. Expired envelopes are rejected even if otherwise valid. Protocol version and supported-suite negotiation must be authenticated inside the initial handshake and session messages; clients fail closed on unsupported/downgraded versions. Server delivery ACK means relay receipt by the recipient client, never read/view acknowledgement.

Mailbox handles, prekey/public material, expiry, destination routing, and policy/version fields must be authenticated as associated data or protected protocol fields as chosen by the reviewed design. Message plaintext, local aliases, private keys, and ratchet state are never relay fields.
