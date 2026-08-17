# Top-level application state machine

```mermaid
stateDiagram-v2
  [*] --> FIRST_LAUNCH
  FIRST_LAUNCH --> IDENTITY_INITIALIZING: create identity
  IDENTITY_INITIALIZING --> READY: protected local state created
  IDENTITY_INITIALIZING --> FIRST_LAUNCH: generation fails before identity exists
  READY --> LOCKED: app lock/background policy
  LOCKED --> READY: platform authentication
  READY --> DEGRADED_OFFLINE: own transport unavailable
  DEGRADED_OFFLINE --> READY: own transport restored
  READY --> CLOCK_SUSPICIOUS: ordering/time confidence fails
  CLOCK_SUSPICIOUS --> READY: fail-closed purge and safe revalidation
  READY --> IDENTITY_LOST_OR_CORRUPTED: keys or encrypted state unavailable
  IDENTITY_LOST_OR_CORRUPTED --> LOCAL_DATA_PURGE: user confirms removal
  LOCAL_DATA_PURGE --> TERMINAL_IDENTITY_DESTROYED
  TERMINAL_IDENTITY_DESTROYED --> FIRST_LAUNCH: create a new identity
  READY --> LOCAL_DATA_PURGE: explicit destroy identity
```

| State | Entry and view | Allowed / forbidden | Persistence, exit, and privacy |
|---|---|---|---|
| FIRST_LAUNCH | No usable identity; explain device ownership and irreversible loss. | Create identity; no messaging, IDs, or recovery. | No identity state. Exit only to initialization. |
| IDENTITY_INITIALIZING | Local key generation progress without account language. | Cancel only before durable creation; no navigation to product. | Never expose key material; success enters READY, failure returns to FIRST_LAUNCH with a local failure explanation. |
| READY | Normal local product. | All locally authorized actions. | Encrypted durable state; may enter lock, offline, suspicious time, or loss. |
| LOCKED | App content hidden; platform credential/biometric gate later. | Unlock; no content previews/actions. | State persists. Failure to authenticate remains locked. |
| DEGRADED_OFFLINE | Show only own condition: Offline or Unable to reach relay. | Read valid local content, compose, bounded retries. No peer-status inference. | Durable local state. Restored transport returns READY; no peer notification. |
| CLOCK_SUSPICIOUS | Privacy-safe time warning; expired data removed before display. | Inspect surviving data, resolve local time; no send/pair action whose expiry cannot be safely bounded. | Persist conservative lower bound. Exit only after fail-closed reconciliation; may force reset/early expiry. |
| IDENTITY_LOST_OR_CORRUPTED | “Old identity cannot be recovered.” | Review explanation, remove unusable local data, create a new identity afterwards. No silent replacement, messaging, or restore. | Preserve evidence only as safely readable; inaccessible key/state is not overwritten. |
| LOCAL_DATA_PURGE | Explicit destructive confirmation/progress. | Cancel before commitment if possible; otherwise no product action. | Removes selected local identity/relationship data and denies old sessions. |
| TERMINAL_IDENTITY_DESTROYED | Identity is gone. | Create new identity. No undo/recovery. | Terminal for prior identity; peers need fresh mutual exchange. |

Failure never causes a replacement identity to be silently generated, because peers could mistake a different identity for continuity.
