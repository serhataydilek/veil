# Identity and mutual pairing

At first launch, the device generates a stable signing/authentication identity locally. Its private key is non-exportable where Android Keystore hardware support permits; a wrapped core key may be needed for protocol libraries and must be documented and protected by Keystore-held material. There is no account, recovery, password, phone, email, or device migration in Phase 0. Loss of identity material loses the identity and its conversations.

The stable public identity is not a contact address. A contact ID is an opaque, high-entropy capability carrying (or resolving to) an expiry-bound rendezvous credential and enough authenticated public material to prevent substitution. Its displayed form must have an error-detection checksum; it must never be sequential or searchable.

| ID | purpose | lifetime | terminal behavior |
|---|---|---:|---|
| Rotating | normal out-of-band sharing | 7 days + 6-hour grace | rejects new pairing; mapping purged after grace |
| One-time | one intended pairing | one successful match or explicit expiry | atomically consumed; tombstone only long enough to stop replay |
| QR | face-to-face exchange | 10 minutes | rejects after expiry |

These are policy values, not crypto parameters: they can change in a versioned policy update without changing identity/session cryptography. The server must not retain an old-ID-to-new-ID chain.

## Conceptual rendezvous (requires review)

Each party independently submits a valid peer capability plus a fresh, privacy-preserving pairing intent. The relay must reveal no outcome to either unilateral submitter. Only a matching, expiry-valid pair of complementary intents causes each device to receive the material needed to establish a session. A submitted intent is authenticated by its owner and bound to its expiry and protocol version; it is not a statement that an arbitrary ID exists.

This is deliberately not a protocol specification. Candidate designs include a reviewed private set-intersection/rendezvous service or a capability-based blind rendezvous design reviewed by external cryptographers. A simple server lookup is insufficient because it enables probing and relationship mapping. Before selection, model what the relay learns, resistance to online guessing, replay, and malicious-client amplification.

On success, both sides derive/session-negotiate a fresh conversation mailbox handle. Thereafter public IDs are unused for routing; rotation never interrupts a session. The handle is random, opaque, scoped to one session, replaceable on reset, and not a stable relationship identifier.

An identity-key change pauses the conversation, deletes no user evidence, and requires explicit user acknowledgment plus safety-code verification before a new session is trusted. Destroy/reset invalidates local session and mailbox credentials; old traffic must not reactivate it.
