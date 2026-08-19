# Open questions before implementation

## BLOCKER

- **Concrete one-to-one library approval:** the Signal-style protocol direction is selected (ADR 005). Phase 1E records **libsignal v0.99.1** (`97801d22dcf9f5bf714f7b8fa3212cdc973ae1c8`) as the technically preferred candidate and **BLOCKED** (ADR 015). Legal distribution, unsupported-upstream plan, crash-atomic stores, operational pin/SBOM/advisory process, and independent application review must still pass; no custom ratchet is permitted. The crate is not added.
- **Final mutual-rendezvous construction:** the symmetric capability-pair tag was rejected because a relay can enumerate its live-capability corpus (ADR 007). Phase 1F records a **preferred review candidate** (ADR 016): client-secret capabilities, no raw-ID relay corpus, opaque pair locator, RFC 9382 SPAKE2 with key confirmation, store-and-forward mailbox. SPAKE2 authenticates **pair-secret knowledge** and **relay-without-`w` forgery resistance**; it does **not** prove distinct-owner participation. The candidate is **BLOCKED for implementation**. Independent review of the exact composition must still pass; AI analysis does not count. Whether Veil requires cryptographic owner proof (interpretation B) vs honest-client role assignment (interpretation A) is an unblock condition. ADR 001 server-issued capabilities remain current until that review approves a replacement.

Phase 0.8 review packages (`docs/review/`) prepared the original blockers. Phase 1E/1F add candidate write-ups (libsignal v0.99.1; client-secret SPAKE2 rendezvous) without resolving either implementation blocker. ADR 010 accepts only the dependency-selection process.

## IMPORTANT

- Capability issuance/revocation authentication and rate limiting/Sybil resistance without durable personal identifiers; assess accessibility and privacy cost of each option. If ADR 016's client-secret model is ever unblocked, expiry/one-time become honest-client guarantees unless a reviewed substitute for global consumption is added. Distinct-owner proof, if required, is a separate layer from issuance.
- Push metadata reduction architecture: assess polling, rotated wake handles, and independently operated separation against their actual correlation and availability costs; V1 knowingly accepts direct token linkability.
- Identity-key rotation/replacement and safety-code UX; decide whether any legitimate migration is ever supported.
- Platform implementation of the accepted product-state rules: app-lock grace timeout, clipboard behavior, keyboard-learning controls, and notification permission/OS history behavior. Phase 1C implements optional platform App Lock, always-on `FLAG_SECURE`, and API 33+ recents protection; timeout policy remains deferred. Identity/session storage remains pending.
- Database encryption, Keystore capabilities/fallbacks, backup exclusions, WAL cleanup, screenshots/clipboard/recent-preview policy by Android version.
- Packet-size buckets, ordering/reordering limit, mailbox rotation thresholds/revocation, ACK failure and crash consistency.
- Operational incident process without retaining sensitive logs; exact proxy log configuration, dependency provenance/SBOM/reproducible build policy.
- Report evidence format, recipient, retention, legal safeguards, and accidental-overdisclosure UX.

## LATER

- iOS mapping of the same core guarantees and platform storage differences.
- Nearby and experimental mesh threat models and consent UX.
- Usability research for no-account onboarding and expiry explanations.

Intentionally deferred: production schemas, APIs, Android/Rust code, exact wire format, operational deployment topology, and all Bluetooth/mesh implementation. The unsafe assumption to reject is that encryption alone provides anonymity or hides relationships; it does not.
