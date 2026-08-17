# Veil Phase 0 architecture

Veil is an Android-first, text-only, one-to-one ephemeral messenger. It is private, not anonymous: relays and network providers can still observe some metadata.

```mermaid
flowchart LR
  subgraph Device[User device: trusted only while device is secure]
    UI[Kotlin UI] --> Core[Rust core: identity, pairing, sessions, expiry]
    Core --> Store[Keystore + encrypted local database]
    Core --> T[Transport interface]
  end
  T --> R[Untrusted relay]
  R --> DB[(Minimal PostgreSQL state)]
  R --> P[Push provider]
  P --> Device
  T -. future .-> N[Nearby / experimental mesh]
```

## Trust and responsibility boundaries

The device owns private identity material, local aliases, session state, and plaintext. The Rust core enforces crypto and expiry policies; Kotlin must not receive private-key export capabilities. The relay accepts authenticated, encrypted envelopes, performs bounded delivery and expiry, and never decrypts content. PostgreSQL holds only short-lived capability, rendezvous, mailbox-epoch, rate-limit, push-token, and undelivered-packet state. Push is a generic wake-up hint, not a message channel.

```mermaid
flowchart LR
  A[Plaintext in sender device] -->|authenticated encryption| E[Ciphertext envelope]
  E --> Relay
  Relay -->|same ciphertext, bounded mailbox| B[Recipient device]
  B -->|decrypt locally| C[Plaintext in recipient device]
  Relay -. generic wakeup only .-> Push[Push provider]
```

Local storage is separated into Keystore-protected secrets and an encrypted database. The database may contain local aliases and cached messages, but neither is uploaded. The relay has no endpoint for profiles, discovery, contacts, groups, or analytics.

## Transport boundary

`SecureSession` produces and consumes opaque envelopes; `Transport` only sends, receives, and reports bounded delivery results. It must not choose ciphers, inspect plaintext, derive identities, or alter expiry. This preserves equivalent session guarantees across `InternetTransport`, future `NearbyTransport`, and `ExperimentalMeshTransport`.

Public contact capabilities and session routing are distinct: an opaque, unlinkable contact capability is used only for mutual rendezvous. A completed session routes through a random mailbox epoch handle. Authenticated rotation creates a fresh epoch with bounded overlap; the relay deletes old routing state and does not retain a mailbox-history chain. See the Phase 0.5 ADRs for the decision boundaries.

## Never sent to the server

Message plaintext; private identity or session keys; local aliases; contact book; profiles; read/typing/presence state; decrypted reports unless a user explicitly selects them; plaintext notification content; and conversation-history backups.
