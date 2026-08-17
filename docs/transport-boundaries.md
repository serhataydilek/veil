# Transport boundaries

The core exposes an abstract `Transport`: establish a channel, send opaque bounded envelopes, receive opaque envelopes, and return transport-level failure/delivery observations. It accepts no plaintext, private key, alias, or cryptographic choice. Session logic validates versions, expiry, replay, and ciphertext before delivery to UI.

`InternetTransport` initially uses authenticated channel transport to the relay. `NearbyTransport` and `ExperimentalMeshTransport` may later move the same envelopes but must not change session identifiers, authenticated associated data, expiry, or key lifecycle. Each transport requires an independent threat assessment: nearby discovery can leak proximity; mesh can leak forwarding relationships and amplify replay.

Transport acknowledgements are operational only. An authenticated recipient-client ACK is interpreted by Delivery as permission to remove the relay packet; it never produces a user-visible receipt. No transport may buffer past the envelope expiry.
