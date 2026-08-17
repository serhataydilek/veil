# Identity and mutual pairing

At first launch, the device generates a stable signing/authentication identity locally. Its private key is non-exportable where Android Keystore hardware support permits; a wrapped core key may be needed for protocol libraries and must be documented and protected by Keystore-held material. There is no account, recovery, password, phone, email, or device migration in Phase 0. Loss of identity material loses the identity and its conversations.

The stable public identity is not a contact address. A contact ID is an opaque, server-issued, high-entropy expiry-bound capability with a checksum; it is never sequential or searchable. **It must not contain a stable public key, fingerprint, device identifier, or other trivially linkable stable value.**

| ID | purpose | lifetime | terminal behavior |
|---|---|---:|---|
| Rotating | normal out-of-band sharing | 7 days + 6-hour grace | rejects new pairing; mapping purged after grace |
| One-time | one intended pairing | one successful match or explicit expiry | atomically consumed; tombstone only long enough to stop replay |
| QR | face-to-face exchange | 10 minutes | rejects after expiry |

These are policy values, not crypto parameters: they can change in a versioned policy update without changing identity/session cryptography. The server must not retain an old-ID-to-new-ID chain.

## Conceptual rendezvous (requires review)

Each party must independently prove possession of its own valid capability and submit a pairing intent. The relay must reveal no outcome to either unilateral submitter. Only a matching, expiry-valid pair of complementary intents may cause each device to receive the other's encrypted ephemeral pairing material. A submitted intent is authenticated, expiry/version-bound, and not a statement that an arbitrary ID exists.

This is deliberately not a production protocol specification. The former deterministic capability-pair tag is rejected because a relay with the live-capability corpus can enumerate the peer before match. A simple server lookup is likewise insufficient. V1 mutual rendezvous is blocked until a reviewed construction prevents this pre-match recovery while preserving uniform unilateral behavior and abuse resistance.

On success, each client receives the peer's ephemeral pairing material and starts an authenticated secure-session handshake; only then are stable peer identity materials revealed and a safety code available. Thereafter public IDs are unused for routing. Routing uses random mailbox epochs, negotiated by authenticated control traffic with bounded overlap and replacement on reset/destroy; it reduces rather than eliminates relay correlation.

An identity-key change pauses the conversation, deletes no user evidence, and requires explicit user acknowledgment plus safety-code verification before a new session is trusted. Destroy/reset invalidates local session and mailbox credentials; old traffic must not reactivate it.
