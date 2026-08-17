# Privacy-safe error model

## Explicit local errors

The app may be specific about facts it determines locally: malformed ID, checksum failure, locally verifiable expiry, missing notification permission, no network, unable to reach relay, secure-storage/key failure, encryption/session failure, and suspicious local clock. These messages describe this device's condition, not a peer.

## Uniform remote-generic errors

Where a remotely influenced result is unavoidable, use a uniform outcome such as **Unable to use this ID** or **Unable to complete this action**. It must not differentiate unknown/expired remotely, revoked, blocked, peer offline, peer not reciprocating, rate-limited target, or identity existence. A locally known expiry can remain distinct because it needs no remote query.

## Rules

- Do not retry in a way that changes user-visible specificity.
- Do not emit unilateral-save push notifications.
- Errors, diagnostics, and support prompts contain no plaintext, IDs, aliases, tokens, or mailbox handles.
- Generic errors may tell the user to check their own connection or try later, but never say another person declined, exists, is reachable, or is unavailable.
- Clock uncertainty takes priority over convenience: early expiry is safer than extending availability.
