# Security invariants

1. Private identity keys never leave the owning device; exported backup/recovery is unsupported.
2. No normal server operation receives message plaintext or requires decryption.
3. One-sided possession/submission of a contact ID cannot create, notify, or confirm a conversation.
4. Arbitrary IDs cannot be enumerated for registration, presence, pairing, or profile data.
5. Public contact IDs are high entropy, purpose-bound, and expire; they contain no trivially linkable stable identity material and old-to-new mappings are not retained.
6. Stable peer identity material is revealed/authenticated only after mutual rendezvous and secure-session handshake.
7. Established conversations route through rotating scoped mailbox epochs, not current public IDs; old epoch chains are not retained.
8. Normal Veil behavior never resurfaces messages after creation + 24 hours; ambiguous time states expire early, never late.
9. Delivered relay packets are promptly deleted and never retained as conversation history.
10. Local aliases, contact-book data, and profile-like data never leave the device.
11. Transport substitution cannot weaken end-to-end session protections.
12. Identity changes require explicit warning and user acknowledgment; no silent trust replacement.
13. Modified, duplicate, expired, and unsupported-version packets fail closed.
14. Protocol negotiation cannot silently downgrade a security version/suite.
15. Logs, diagnostics, notifications, and crashes contain no plaintext, keys, IDs, aliases, tokens, or mailbox handles.
16. Blocking/reset/destruction prevents old session or mailbox state from silently reactivating; new contact requires fresh mutual pairing.
17. Only a deliberate user report can disclose selected decrypted content.
18. A rendezvous representation must never be described as relay-opaque when a relay with the live-capability corpus can recover a peer by candidate enumeration.
19. Rotating contact IDs and mailbox epochs do not by themselves prevent device-level linkability through push infrastructure.
20. Unblocking never restores a previous secure session, mailbox epoch, or connection.
21. A reset or destroyed relationship can be recreated only through fresh mutual pairing using current IDs.
22. No metadata-minimization claim may imply elimination of timing, relay, provider, or device-level correlation unless the architecture actually provides it.

Future tests must include property/state-machine tests for expiry/reset/replay, protocol vectors and downgrade tests, relay leak/retention integration tests, logging redaction tests, database/WAL restart tests, and adversarial rendezvous/enumeration testing.
