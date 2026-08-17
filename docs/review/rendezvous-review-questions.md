# Rendezvous external-review questions

1. Can an unmatched target capability be recovered by enumerating the live corpus under the documented relay threat model?
2. Which party sees each raw capability, and when?
3. Which party owns each evaluation key?
4. Which party sees a match representation and can test it against candidates?
5. Does compromise of one server retroactively expose stored unmatched relationships?
6. What metadata appears only upon a successful match?
7. Can replay, duplicate, retry, or races reveal information or consume a one-time capability incorrectly?
8. Can malformed/expired/unsupported inputs create an oracle through response, timing, storage, or push behavior?
9. Can expiry, revocation, and one-time semantics compose safely with the construction?
10. Does the design remain safe when either client is malicious?
11. What exact state exists at every role, and what TTL/deletion/verifiable cleanup rule applies?
12. What technical and operational assumptions establish two-server independence and non-collusion?
13. What proofs, security arguments, test vectors, attack tests, and independent reviews exist for the exact composition?
14. Which components or compositions are novel rather than standardized?
15. Can the design remain bounded and practical with intermittent mobile clients, delayed delivery, abuse controls, and denial of service?

No answer should be inferred from a primitive name alone. The reviewer should require a role/observation table and a threat-specific argument for corpus enumeration.
