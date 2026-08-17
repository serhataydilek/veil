# Product copy guidelines

## Rules

- State only a local fact unless the protocol safely establishes a relationship state.
- Do not turn a temporary contact ID, local alias, or device identity into an account or username.
- Say encrypted, local, temporary, expires, contact ID, mutual connection, and private conversation precisely; do not claim anonymous, untraceable, permanently deleted, military-grade, or unhackable.
- Never imply peer presence, intent, identity existence, receipt, reading, or a server-side request queue.
- Explain limits plainly: the relay cannot read messages, but timing/network metadata and a recipient who keeps text remain outside that claim.

## Preferred wording

| Situation | Use | Do not use |
|---|---|---|
| Saved entry | `ID saved` | `Request sent`, `User found` |
| Generic remote failure | `Unable to use this ID` | `User does not exist`, `Declined` |
| Pairing before match | `Saved IDs` | `Pending requests` |
| Loss/reinstall | `Create new identity` | `Sign in again`, `Recover account` |
| Time ambiguity | `Time needs attention. Messages may expire early.` | an exact remaining lifetime |
| Notification | `New message` | sender, alias, preview, count, or conversation label |

Historical/security explanations may name prohibited concepts to explain why they are excluded; they must not describe active product behavior.
