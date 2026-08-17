# Security invariants

1. Private identity keys never leave the owning device; exported backup/recovery is unsupported.
2. No normal server operation receives message plaintext or requires decryption.
3. One-sided possession/submission of a contact ID cannot create, notify, or confirm a conversation.
4. Arbitrary IDs cannot be enumerated for registration, presence, pairing, or profile data.
5. Public contact IDs are high entropy, purpose-bound, and expire; old-to-new mappings are not retained.
6. Established conversations route through scoped session/mailbox handles, not current public IDs.
7. Normal Veil behavior never resurfaces messages after creation + 24 hours.
8. Delivered relay packets are promptly deleted and never retained as conversation history.
9. Local aliases, contact-book data, and profile-like data never leave the device.
10. Transport substitution cannot weaken end-to-end session protections.
11. Identity changes require explicit warning and user acknowledgment; no silent trust replacement.
12. Modified, duplicate, expired, and unsupported-version packets fail closed.
13. Protocol negotiation cannot silently downgrade a security version/suite.
14. Logs, diagnostics, notifications, and crashes contain no plaintext, keys, IDs, aliases, tokens, or mailbox handles.
15. Blocking/reset prevents old session state from silently reactivating.
16. Only a deliberate user report can disclose selected decrypted content.

Future tests must include property/state-machine tests for expiry/reset/replay, protocol vectors and downgrade tests, relay leak/retention integration tests, logging redaction tests, database/WAL restart tests, and adversarial rendezvous/enumeration testing.
