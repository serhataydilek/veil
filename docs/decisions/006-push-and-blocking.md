# ADR 006: generic wake-up push and terminal blocking

## Status

Accepted for V1 with explicit device-level linkability limitation; platform-specific delivery behavior requires later validation.

## Context

Push improves offline responsiveness but a provider token is a pseudonymous, often long-lived device correlation point. A relay mapping it to delivery channels can link multiple rotating contact IDs, mailbox epochs, conversations, and long periods of activity. Blocking must stop useful traffic rather than merely hide UI messages, without giving the relay plaintext or creating unilateral reconnection.

## Threats / constraints

The push provider can correlate a token with delivery time and sender relay infrastructure. The relay can correlate its token mapping with active mailbox epochs and thereby form a device-level activity graph even though contact IDs/mailboxes rotate. Push must never contain message content or routing identifiers. Android background limits make push-free immediate delivery expensive or unreliable.

## Options considered

- **Direct relay token mapping:** chosen V1 (Option 1). Simple, reliable, and battery efficient, but the relay gains strong cross-mailbox/device linkability.
- **Per-device wake queue with token separation:** a separate subsystem could hold provider tokens, but routing must still tell it which device queue to wake. With one operator or colluding services, useful linkage remains; it adds access-control, availability, and audit surface.
- **Short-lived relay-visible wake handles:** could rotate a handle while a separate subsystem retains the provider token. It reduces retained identifiers at the routing relay only if that subsystem is independently separated and mapping/link logs are tightly bounded; it does not stop a colluding operator or provider timing correlation.
- **Polling only:** reduces provider linkage but harms battery/latency and faces Android background restrictions; defer as an optional user-controlled future mode after measurement.
- **Rich push payloads:** rejected: plaintext, alias, contact ID, mailbox ID, count, and label are prohibited.

## Decision

Veil V1 deliberately chooses **Option 1**: the relay holds a minimal encrypted-at-rest mapping from a device-local push token to a currently reachable device delivery channel. It sends only a generic wake-up whose visible payload is `New message`. It sends no plaintext, alias, stable identity, contact ID, mailbox epoch/ID, message count, or conversation label. The client fetches and decrypts ciphertext over its authenticated relay connection after wake-up. Rotate/delete tokens on OS replacement, app reset, opt-out, and expiry; do not use tokens as analytics keys.

This knowingly accepts device-level push-token linkability at the relay for reliable background delivery. Message contents remain hidden, but rotating IDs and mailbox epochs do not make a device unlinkable to the relay while its token mapping exists. The provider observes token and notification timing. Separating a wake-up subsystem is deferred because it cannot remove timing correlation without stronger, independently operated infrastructure; a future design must demonstrate a concrete reduction before adding it.

**Block** applies a local terminal deny state to the peer/session and all current/overlap mailbox epochs. The client discards queued and later old-session traffic before render and refuses old control traffic; it sends best-effort authenticated revocation. **Unblock** only removes that local deny state. It does not restore a session/handle, reconnect, notify the old peer, or bypass mutual pairing. **Reset** is cryptographically terminal: it revokes old epochs and removes session state but may retain the local conversation shell/alias. **Destroy** includes reset plus removal of local relationship state, aliases if the user requests full destroy, and messages subject to deletion limits. After reset or destroy—and after an unblock—communication requires both people to enter current valid IDs, complete fresh mutual pairing, create a fresh session, and receive a fresh mailbox epoch.

## Security consequences

Push is not an authenticated message or delivery/read receipt. E2EE/session validation remains authoritative. Revocation may be delayed or ignored by a hostile relay, so local denial is the security boundary. A blocked peer can still cause network attempts until relay revocation/quota takes effect, but cannot create a trusted replacement session.

## Privacy consequences

Generic text protects lock-screen content, but token/timing linkage remains. The direct mapping is purpose-limited and minimized, but V1 explicitly accepts that it is a cross-conversation pseudonymous device correlation point for its lifetime. No analytics or notification-history product may be derived from it.

## Operational consequences

Token refresh, invalid-token cleanup, opt-out, retries, and generic payload validation must be implemented with minimal logs. A separated service is not added in V1. The client must distinguish transport delivery from a user-visible receipt.

## Residual risks

Push providers/OSs may delay, drop, or correlate wakeups. A malicious relay can issue excessive generic wakeups (rate-limit required) or withhold them. Screenshots and OS notification history settings are outside Veil's full control.

## Implementation requirements

- Store token mapping encrypted, TTL-bound, access-controlled, and redacted from logs/errors; document that TTL does not erase live or copied correlation.
- Enforce a payload allow-list that rejects all fields except generic wake-up semantics.
- Apply local block before decrypt/render/ACK semantics; make revoke and deletion idempotent.
- Provide privacy-safe user controls for notifications and destruction.

## Tests required

Payload snapshot/allow-list tests; token rotation/opt-out/deletion tests; log/database inspection; blocked/revoked/offline queue races; no-read-receipt tests; and provider outage/retry/battery tests.

## Open issues

Exact Android push transport and token encryption/key-management details are IMPORTANT. A polling-only privacy mode, rotated wake handles, and a genuinely separated wake-up service are LATER pending measured benefit, independent-operator assumptions, and a revised threat model.
