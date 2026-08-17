# Metadata map

| Datum | classification | retention | mitigation |
|---|---|---:|---|
| Source IP | necessary/dangerous | connection lifetime; no app retention | TLS, minimize logs; anonymity not promised |
| Destination relay, connection time, packet timing | necessary/dangerous | transient | short sessions; no claim against correlation |
| Packet size | avoidable in part | transient | bounded size buckets after review |
| Push token | necessary/dangerous | while notifications enabled | V1 relay mapping is TTL-bound, rotate/delete on opt-out; generic wake-up only |
| Rotating/contact ID | short-lived/dangerous | its expiry only | server-issued random capability; no stable identity material or history chain |
| Rendezvous state | necessary/dangerous | match/expiry | opaque pair tag, uniform response, external review gate |
| Mailbox epoch handle | necessary/dangerous | active epoch plus bounded overlap | random epoch, authenticated rotation/revocation, no retained history chain |
| Delivery ACK | necessary | immediate processing | authenticated, no read semantic; no history |
| Rate-limit state | necessary/dangerous | short rolling window | coarse, rotating keyed buckets, abuse review |
| Device/platform/version | avoidable mostly | not persisted by relay | protocol capability minimization; no fingerprint logs |
| Server access logs and reverse-proxy logs | dangerous | disabled or shortest rotation | redact IP/path/query/token; audited config |
| Crash reports | dangerous | none by default | no third-party SDK; scrub before any opt-in report |

The push provider also observes token, time, sending relay account, and generic notification delivery. The network sees source/destination and volume. The relay can infer mailbox activity. These limits must appear in product copy; encryption does not erase them.
