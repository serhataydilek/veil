# Protocol boundaries (conceptual, not a wire protocol)

No proprietary cryptography or final packet encoding is specified here. All fields, canonical serialization, error behavior, and state transitions require protocol review and test vectors before implementation.

| Layer | responsibility | protection requirement |
|---|---|---|
| Identity | hidden device identity and post-match peer authentication | private key local; stable public material is not in a contact ID; key changes explicit |
| Rendezvous | mutually matching pairing intents | expiry, anti-probing, opaque symmetric tag candidate, no unilateral result |
| Secure Session | one-to-one key agreement and ratchet | authenticated encryption, forward secrecy, post-compromise security |
| Message Envelope | opaque routing/padding container | version, mailbox, expiry, sequence/message id authenticated |
| Delivery | bounded relay queue and acknowledgement | authenticated ACK; idempotent, no read semantics |
| Expiry | reject/purge expired data | server and client enforce creation-based upper bound |
| Transport | byte movement only | TLS/channel protection plus end-to-end envelope; no crypto decisions |

Veil's selected protocol direction is an independently reviewed Signal-style asynchronous ratchet family with reviewed prekey setup, rather than writing one. It fits offline initial delivery, forward secrecy, post-compromise security, authentication, and bounded out-of-order delivery when correctly deployed. MLS/OpenMLS remains a credible standard/Rust option, but is not selected for V1's one-to-one scope because its group-state machinery does not solve rendezvous privacy. The concrete library remains a BLOCKER pending legal, support, maintenance, and independent-review gates.

Ciphertexts must be authenticated; modified packets fail closed. Each session needs unique message identifiers and ratchet/sequence state to reject duplicate/replayed packets while tolerating an explicitly bounded reordering window. Expired envelopes are rejected even if otherwise valid. Protocol version and supported-suite negotiation must be authenticated inside the initial handshake and session messages; clients fail closed on unsupported/downgraded versions. Server delivery ACK means relay receipt by the recipient client, never read/view acknowledgement.

Mailbox epoch handles, pairing/handshake material, expiry, destination routing, and policy/version fields must be authenticated as associated data or protected protocol fields as chosen by the reviewed design. Stable identity material is released/authenticated only after mutual rendezvous in the secure-session handshake. Message plaintext, local aliases, private keys, and ratchet state are never relay fields.
