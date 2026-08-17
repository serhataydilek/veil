# Open questions before implementation

## BLOCKER

- **Reviewed one-to-one protocol and Rust library:** select, audit maintenance posture, define prekey/offline semantics, safety verification, and formal integration boundary. MLS/OpenMLS versus a Signal-style ratchet must be decided; MLS is not justified merely by standardization.
- **Mutual rendezvous construction:** choose a reviewed approach that resists existence probing and minimizes relay graph knowledge; externally review it before implementation.
- **Contact-ID construction:** server-issued random capabilities versus cryptographically derived rotating capabilities, including revocation, grace, and no-history guarantees.
- **Expiry/trusted-time semantics:** define a robust 24-hour policy under offline use and adversarial wall-clock changes without claiming impossible deletion.
- **Metadata acceptance bar:** decide exact relay/proxy log configuration, mailbox-linkability limits, and whether padding is worth its cost.
- **Push model:** choose wake-up-only provider use, polling, token lifecycle, and privacy copy.

## IMPORTANT

- Rate limiting and Sybil resistance without durable personal identifiers; assess accessibility and privacy cost of each option.
- Identity-key rotation/replacement and safety-code UX; decide whether any legitimate migration is ever supported.
- Database encryption, Keystore capabilities/fallbacks, backup exclusions, WAL cleanup, screenshots/clipboard/recent-preview policy by Android version.
- Packet-size buckets, ordering/reordering limit, mailbox reset/revocation, ACK failure and crash consistency.
- Operational incident process without retaining sensitive logs; dependency provenance/SBOM/reproducible build policy.
- Report evidence format, recipient, retention, legal safeguards, and accidental-overdisclosure UX.

## LATER

- iOS mapping of the same core guarantees and platform storage differences.
- Nearby and experimental mesh threat models and consent UX.
- Usability research for no-account onboarding and expiry explanations.

Intentionally deferred: production schemas, APIs, Android/Rust code, exact wire format, operational deployment topology, and all Bluetooth/mesh implementation. The unsafe assumption to reject is that encryption alone provides anonymity or hides relationships; it does not.
