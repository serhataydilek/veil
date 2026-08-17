# ADR 006: generic wake-up push and terminal blocking

## Status

Accepted for V1 architecture; platform-specific delivery behavior requires later validation.

## Context

Push improves offline responsiveness but links a push token, relay account, notification time, and likely mailbox activity. Blocking must stop useful traffic rather than merely hide UI messages, without giving the relay plaintext or creating unilateral reconnection.

## Threats / constraints

The push provider can correlate a token with delivery time and sender relay infrastructure. The relay can correlate its push-token mapping with active mailbox epochs. Push must never contain message content or routing identifiers. Android background limits make push-free immediate delivery expensive or unreliable.

## Options considered

- **Relay owns a direct token mapping and sends generic wakeups:** chosen V1; smallest system and clear metadata boundary.
- **Separate wake-up service:** may split databases/operators but still needs a correlation interface and adds availability, audit, and access-control surface. Not justified without a concrete threat-model gain.
- **Polling only:** reduces provider linkage but harms battery/latency; defer as an optional user-controlled future mode after measurement.
- **Rich push payloads:** rejected: plaintext, alias, contact ID, mailbox ID, count, and label are prohibited.

## Decision

For V1 the relay holds a minimal encrypted-at-rest mapping from a device-local push token to a currently reachable device delivery channel. It sends only a generic wake-up whose visible payload is `New message`. It sends no plaintext, alias, stable identity, contact ID, mailbox epoch/ID, message count, or conversation label. The client fetches and decrypts ciphertext over its authenticated relay connection after wake-up. Rotate/delete tokens on OS replacement, app reset, opt-out, block/destroy where relevant, and expiry; do not use tokens as identity or analytics keys.

This does not hide linkage: the relay can associate a wake-up with pending mailbox activity at send time, and the provider observes token and notification timing. Separating a wake-up subsystem is deferred because it cannot remove timing correlation without stronger, unselected infrastructure and would expand privileged systems. A future design must demonstrate a concrete reduction before adding it.

Blocking applies a local terminal deny state to the peer identity/session and all current/overlap mailbox epochs. The client discards inbound envelopes before rendering, does not acknowledge them as read, and refuses old-session control traffic. It makes an authenticated best-effort revocation request for those epochs; the relay deletes queued packets/routing state it controls. Reset differs by replacing an active session after explicit warning; destroy includes block-like terminal invalidation and requires fresh mutual pairing for any new relationship. Unblock never restores old handles or sessions.

## Security consequences

Push is not an authenticated message or delivery/read receipt. E2EE/session validation remains authoritative. Revocation may be delayed or ignored by a hostile relay, so local denial is the security boundary. A blocked peer can still cause network attempts until relay revocation/quota takes effect, but cannot create a trusted replacement session.

## Privacy consequences

Generic text protects lock-screen content, but token/timing linkage remains. The relay's direct mapping is short-lived and purpose-limited; no analytics, notification histories, or cross-conversation profile may be derived from it.

## Operational consequences

Token refresh, invalid-token cleanup, opt-out, retries, and generic payload validation must be implemented with minimal logs. A separated service is not added in V1. The client must distinguish transport delivery from a user-visible receipt.

## Residual risks

Push providers/OSs may delay, drop, or correlate wakeups. A malicious relay can issue excessive generic wakeups (rate-limit required) or withhold them. Screenshots and OS notification history settings are outside Veil's full control.

## Implementation requirements

- Store token mapping encrypted, TTL-bound, access-controlled, and redacted from logs/errors.
- Enforce a payload allow-list that rejects all fields except generic wake-up semantics.
- Apply local block before decrypt/render/ACK semantics; make revoke and deletion idempotent.
- Provide privacy-safe user controls for notifications and destruction.

## Tests required

Payload snapshot/allow-list tests; token rotation/opt-out/deletion tests; log/database inspection; blocked/revoked/offline queue races; no-read-receipt tests; and provider outage/retry/battery tests.

## Open issues

Exact Android push transport and token encryption/key-management details are IMPORTANT. A polling-only privacy mode and a separated wake-up service are LATER pending measured benefit and a revised threat model.
