# Identity and mutual pairing

At first launch, the device generates a stable signing/authentication identity locally. Its private key is non-exportable where Android Keystore hardware support permits; a wrapped core key may be needed for protocol libraries and must be documented and protected by Keystore-held material. There is no account, recovery, password, phone, email, or device migration in Phase 0. Loss of identity material loses the identity and its conversations.

The stable public identity is not a contact address. A contact ID is an opaque, high-entropy expiry-bound capability with a checksum; it is never sequential or searchable. **It must not contain a stable public key, fingerprint, device identifier, or other trivially linkable stable value.**

ADR 001 currently specifies **server-issued** capabilities. Phase 1F [ADR 016](decisions/016-rendezvous-construction-candidate.md) prefers, **for external review only**, client-generated secrets that are never registered in raw form, so the relay has no live raw-capability corpus. That issuance change is **BLOCKED for implementation** and does not rewrite ADR 001. Unlinkable encoding rules are unchanged.

| ID | purpose | lifetime | terminal behavior |
|---|---|---:|---|
| Rotating | normal out-of-band sharing | 7 days + 6-hour grace | rejects new pairing; mapping purged after grace |
| One-time | one intended pairing | one successful match or explicit expiry | atomically consumed; tombstone only long enough to stop replay |
| QR | face-to-face exchange | 10 minutes | rejects after expiry |

These are policy values, not crypto parameters: they can change in a versioned policy update without changing identity/session cryptography. The server must not retain an old-ID-to-new-ID chain.

## Conceptual rendezvous (requires review)

Each honest client must independently hold the peer's current temporary capability and emit only its assigned rendezvous role after local entry. The relay must reveal no outcome to either unilateral submitter. SPAKE2, if used, authenticates pair-secret knowledge; it does not prove the original owner of the opposite ID participated. A submitted intent is not a statement that an arbitrary ID exists.

This is deliberately not a production protocol specification. The former deterministic capability-pair tag is rejected because a relay with the live-capability corpus can enumerate the peer before match. A simple server lookup is likewise insufficient. A locator-only mailbox is insufficient because a relay that does not know the pair secret can still copy the first locator. Phase 1F prefers a client-secret + locator + RFC 9382 SPAKE2 family for **external review** (ADR 016) as pair-secret authentication and relay-without-`w` forgery resistance, and keeps implementation **BLOCKED**. Distinct-owner cryptographic proof is an open review question, not a SPAKE2 guarantee. V1 mutual rendezvous stays blocked until that exact composition (or a replacement) is independently reviewed.

On success, each client receives the peer's ephemeral pairing material and starts an authenticated secure-session handshake; only then are stable peer identity materials revealed and a safety code available. Thereafter public IDs are unused for routing. Routing uses random mailbox epochs, negotiated by authenticated control traffic with bounded overlap and replacement on reset/destroy; it reduces rather than eliminates relay correlation.

An identity-key change pauses the conversation, deletes no user evidence, and requires explicit user acknowledgment plus safety-code verification before a new session is trusted. Destroy/reset invalidates local session and mailbox credentials; old traffic must not reactivate it.

## Product lifecycle clarification

First launch creates a device-owned identity locally; there is no sign-in, recovery, migration, or silent replacement after protected-key loss or local-state corruption. A one-sided ID entry is only a local saved value, even while the app is backgrounded. Duplicate entries deduplicate locally; malformed/checksum-invalid IDs are local errors; locally verifiable expiry may be explicit. A syntactically valid but meaningless ID must receive uniform non-oracular handling. Rotating replacement IDs do not map old IDs to new ones. One-time consumption occurs only after the final reviewed mutual match, never merely from one party's submission. Detailed state machines are in `docs/state/identity-lifecycle.md`, `contact-id-lifecycle.md`, and `pairing-lifecycle.md`.
