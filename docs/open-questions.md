# Open questions before implementation

## BLOCKER

- **Concrete one-to-one library approval:** the Signal-style protocol direction is selected, but legal distribution, upstream support posture, API/persistence fit, maintenance, and independent-review gates must select a library; no custom ratchet is permitted.
- **Final mutual-rendezvous construction:** the symmetric capability-pair tag was rejected because a relay can enumerate its live-capability corpus. Select and externally review a deployable construction that prevents pre-match target recovery by any single relay role.

## IMPORTANT

- Capability issuance/revocation authentication and rate limiting/Sybil resistance without durable personal identifiers; assess accessibility and privacy cost of each option.
- Push metadata reduction architecture: assess polling, rotated wake handles, and independently operated separation against their actual correlation and availability costs; V1 knowingly accepts direct token linkability.
- Identity-key rotation/replacement and safety-code UX; decide whether any legitimate migration is ever supported.
- Database encryption, Keystore capabilities/fallbacks, backup exclusions, WAL cleanup, screenshots/clipboard/recent-preview policy by Android version.
- Packet-size buckets, ordering/reordering limit, mailbox rotation thresholds/revocation, ACK failure and crash consistency.
- Operational incident process without retaining sensitive logs; exact proxy log configuration, dependency provenance/SBOM/reproducible build policy.
- Report evidence format, recipient, retention, legal safeguards, and accidental-overdisclosure UX.

## LATER

- iOS mapping of the same core guarantees and platform storage differences.
- Nearby and experimental mesh threat models and consent UX.
- Usability research for no-account onboarding and expiry explanations.

Intentionally deferred: production schemas, APIs, Android/Rust code, exact wire format, operational deployment topology, and all Bluetooth/mesh implementation. The unsafe assumption to reject is that encryption alone provides anonymity or hides relationships; it does not.
