# Metadata map

| Datum | classification | retention | mitigation |
|---|---|---:|---|
| Source IP | necessary/dangerous | connection lifetime; no app retention | TLS, minimize logs; anonymity not promised |
| Destination relay, connection time, packet timing | necessary/dangerous | transient | short sessions; no claim against correlation |
| Packet size | avoidable in part | transient | bounded size buckets after review |
| Push token | necessary/dangerous | while notifications enabled | V1 direct relay mapping knowingly links mailbox epochs/conversations for one device; TTL, rotate/delete, generic wake-up only |
| Rotating/contact ID | short-lived/dangerous | its expiry only | server-issued random capability; no stable identity material or history chain |
| Rendezvous representation | dangerous / blocked | no accepted V1 retention yet | deterministic pair tag rejected: relay can enumerate live corpus; reviewed new construction required |
| Mailbox epoch handle | necessary/dangerous | active epoch plus bounded overlap | random epoch, authenticated rotation/revocation, no retained history chain |
| Delivery ACK | necessary | immediate processing | authenticated, no read semantic; no history |
| Rate-limit state | necessary/dangerous | short rolling window | coarse, rotating keyed buckets, abuse review |
| Device/platform/version | avoidable mostly | not persisted by relay | protocol capability minimization; no fingerprint logs |
| Server access logs and reverse-proxy logs | dangerous | disabled or shortest rotation | redact IP/path/query/token; audited config |
| Crash reports | dangerous | none by default | no third-party SDK; scrub before any opt-in report |

The push provider also observes token, time, sending relay account, and generic notification delivery. The network sees source/destination and volume. The relay can infer mailbox activity and, under V1 push, associate multiple activities with one pseudonymous device token. These limits must appear in product copy; encryption does not erase them.
