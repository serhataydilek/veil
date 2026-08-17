# E2EE library due diligence

**Research date:** 2026-08-17. This is a current-source screening record, not legal advice, an audit, or an approval. A good protocol family is not automatically an acceptable library.

## Signal-style direction and libsignal

Signal's published [X3DH](https://signal.org/docs/specifications/x3dh/), [PQXDH](https://signal.org/docs/specifications/pqxdh/), and [Double Ratchet](https://signal.org/docs/specifications/doubleratchet/) specifications make the family a strong conceptual fit for asynchronous one-to-one initiation, authentication, forward secrecy, post-compromise recovery, and bounded out-of-order handling. Exact behavior still depends on a selected API, persistence integration, and app-level session lifecycle.

The upstream [libsignal repository](https://github.com/signalapp/libsignal) says its platform-agnostic APIs are exposed to Java, Swift, and TypeScript while the implementations are Rust; it identifies `libsignal-protocol` as including Double Ratchet. Its Android README path describes artifacts for Android ABIs and says Signal publishes Java packages **for its own use**. That is evidence of technical Android packaging, not an upstream support commitment for independent products. Its public repository is AGPL-3.0; distribution, source-offer, linking, attribution, Android-store, and future iOS consequences require **LEGAL REVIEW REQUIRED**. Do not infer legal conclusions from the license text or package availability.

Its repository documents active releases/release notes rather than a conventional changelog and bridges for Java/Kotlin and Swift, but the application-facing public API stability, third-party support posture, safety-number UX, persistence ownership, prekey/session lifecycle, skipped-message bounds, dependency footprint, and Android/iOS distribution fit require a pinned-version API evaluation. This repository does not itself close Veil's reviewed-rendezvous blocker.

**Assessment:** strongest protocol-family fit; **library approval BLOCKED** on legal review, explicit upstream-support posture, version-pinned API/persistence study, dependency due diligence, and independent application review.

## OpenMLS

[RFC 9420](https://www.rfc-editor.org/rfc/rfc9420.html) specifies MLS as asynchronous group keying with forward secrecy and post-compromise security; [RFC 9750](https://www.rfc-editor.org/rfc/rfc9750.html) describes the application architecture/security goals. [OpenMLS](https://github.com/openmls/openmls) is an MIT-licensed Rust RFC 9420 implementation maintained by Phoenix R&D and CE Labs. Its README lists tested desktop/server targets but Android and iOS targets as built on CI and not tested. That is technically promising, not mobile-platform support.

OpenMLS has a `StorageProvider` model for sensitive state ([persistence documentation](https://book.openmls.tech/user_manual/persistence.html)), which makes persistence explicit but requires application crash/atomicity design. It exposes debug features that can print sensitive content/key material; production policy must forbid them. Its upstream advisory GHSA-qr9h-x63w-vqfm records a persistence-related forward-secrecy issue fixed in 0.7.1, demonstrating why pinning and advisory monitoring are mandatory—not perpetual assurance ([advisory](https://github.com/openmls/openmls/security/advisories/GHSA-qr9h-x63w-vqfm)).

MLS supports two members but brings groups, commits, epochs, KeyPackages, membership/authentication-service assumptions, and state-machine complexity that do not solve Veil's mutually private rendezvous problem. **Assessment:** credible standards-based Rust option but CONDITIONAL/POOR FIT for V1's intentional one-to-one scope; no selection.

## Separation rule

Protocol-family suitability, an implementation's technical availability, upstream support, license compatibility, version-specific audit evidence, and Veil integration approval are separate decisions. Neither a specification nor an independent audit is permanent security assurance. No other candidate is included merely to fill a table: no additional maintained, reviewed, Rust/Android-relevant implementation has passed this initial relevance screen.
