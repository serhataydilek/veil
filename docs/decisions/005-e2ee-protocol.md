# ADR 005: one-to-one secure-session direction

## Status

Protocol-family direction accepted; concrete library selection remains a BLOCKER until legal, API, maintenance, and independent-review gates pass.

## Context

Veil is asynchronous, one-to-one, text-only, and needs offline initiation, identity authentication, forward secrecy, post-compromise security, replay handling, and bounded out-of-order delivery. It must use a maintained reviewed implementation, not copy or reimplement cryptographic code.

## Threats / constraints

A relay can substitute public material, reorder/replay/drop ciphertext, retain metadata, and be compromised. Devices can be lost. The selected API must fit Rust core and Android now, iOS later. A standard protocol does not solve Veil rendezvous, local storage, mailbox metadata, session reset, or product key-verification UX.

## Options considered

| Candidate | maturity and properties | Rust / licensing / scope assessment |
|---|---|---|
| Signal-style asynchronous key agreement + Double Ratchet | Published Signal specifications cover asynchronous prekey initiation and per-message ratcheting; Double Ratchet is analyzed and supports bounded skipped keys | Best protocol fit. Signal's `libsignal` has Rust and Android bindings, but its repository says packages are published for Signal's own use and its current license is AGPL-3.0; product/legal approval and supported external-use posture are required |
| MLS / OpenMLS with two members | RFC 9420 is an IETF Proposed Standard for asynchronous groups from two to thousands, with FS/PCS; OpenMLS is a maintained MIT-licensed Rust implementation | Strong standard/library, but group/epoch/commit/key-package semantics and metadata do not buy Veil's one-to-one scope; Android targets are built but upstream lists them as unsupported/not tested |
| Matrix Olm via vodozemac | Rust implementation with a published independent audit | Not selected: it brings Matrix-specific protocol/ecosystem assumptions; Matrix has reported and analyzed later E2EE issues, so an old audit is not a blanket assurance |

Authoritative references consulted: [Signal Double Ratchet specification](https://signal.org/docs/specifications/doubleratchet/), [Signal X3DH specification](https://signal.org/docs/specifications/x3dh/), [Signal Sesame session-management specification](https://signal.org/docs/specifications/sesame/), [libsignal repository](https://github.com/signalapp/libsignal), [libsignal AGPL-3.0 license](https://github.com/signalapp/libsignal/blob/main/LICENSE), [RFC 9420](https://www.rfc-editor.org/info/rfc9420/), [OpenMLS repository](https://github.com/openmls/openmls), and [Matrix's vodozemac audit notice](https://matrix.org/blog/2022/05/16/independent-public-audit-of-vodozemac-a-native-rust-reference-implementation-of-matrix-end-to-end-encryption/). This is a design review, not an endorsement or legal opinion.

## Decision

Adopt the **Signal-style asynchronous ratchet family as Veil's protocol direction**, not an implementation commitment: a reviewed X3DH/PQXDH-compatible offline setup as appropriate, followed by a reviewed Double Ratchet-style session with authenticated headers/associated data, bounded skipped-key storage, explicit session replacement, and safety-code verification. The direction has high confidence for functional fit; the library choice has low confidence until the gates below close.

Do not select MLS/OpenMLS for V1 two-party conversations. MLS is standards-based and OpenMLS is a credible future option if Veil grows into groups, but its group state, commits, key packages, epochs, and delivery-service metadata are extra complexity for a feature deliberately excluded from V1. Do not adopt vodozemac as a generic substitute.

Before Phase 1 crypto implementation, select a maintained Rust implementation only after: (1) legal review of license and Android/iOS distribution, especially libsignal AGPL obligations; (2) confirmation of upstream-supported third-party use or an explicit support plan; (3) API review for the exact current protocol version, prekey lifecycle, persistence, skipped-message cap, and Android/iOS bindings; (4) dependency/SBOM and vulnerability-response policy; and (5) independent application-level security review. If no appropriate library clears these gates, this remains blocked; Veil must not implement the ratchet itself.

## Security consequences

The later handshake binds stable identities only after mutual rendezvous. AEAD and ratchet state authenticate content/headers; unique message keys, bounded skipped-key handling, and session identity prevent altered/replayed traffic from being rendered. Forward secrecy and post-compromise recovery have the liveness assumptions documented by the selected protocol: recovery requires future honest entropy/message exchange.

## Privacy consequences

E2EE protects content, not relay IP/timing/mailbox metadata. Prekey publication must be scoped behind completed rendezvous rather than exposed as a global directory. Safety codes reveal peer identity only to the paired devices.

## Operational consequences

The secure core must persist session state atomically before reuse-sensitive sends, maintain an explicit prekey/session lifecycle, cap work/storage for out-of-order messages, and fail closed on unknown versions. Library upgrades are security releases and need compatibility/test-vector review.

## Residual risks

No protocol protects plaintext at an unlocked/compromised endpoint or a malicious peer. Server suppression is denial of service. Current upstream maintenance, audits, and advisories can change; this ADR's research date is 2026-08-17 and must be refreshed at dependency selection and release.

## Implementation requirements

- No copied protocol code or custom cipher/ratchet composition.
- Specify canonical serialization, associated data, version negotiation, prekey rotation, MAX_SKIP, persistence, and failure behavior before code.
- Pin dependencies and record license/audit/advisory decisions; disable debug crypto/content logging.
- Require integration vectors, interoperability tests where applicable, and external review.

## Tests required

Official/adopted vectors; offline initiation; identity substitution; modified/replayed/duplicate/expired envelopes; bounded reordering; crash-before/after persistence; prekey exhaustion/rotation; downgrade; reset/key-change; and cross-platform interop tests.

## Open issues

Concrete library/legal approval is still **BLOCKER**. PQ migration policy, formal session management, and exact safety-code derivation are IMPORTANT. A future group requirement would trigger a new MLS decision, not silently repurpose V1.

## Phase 0.8 due-diligence clarification

`docs/review/e2ee-library-due-diligence.md` records current upstream-source screening. It confirms that protocol-family fit is separate from library approval: libsignal remains blocked on legal review, upstream third-party support posture, pinned API/persistence fit, and independent application review; OpenMLS remains a conditional standards-based Rust option with built-but-not-tested upstream Android/iOS targets and group-state cost for V1. This clarification selects neither library and does not replace refreshed evidence at dependency selection.
