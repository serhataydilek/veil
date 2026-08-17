# Security-sensitive copy audit

| Copy | Status | Rule |
|---|---|---|
| `Private conversation`, `ID saved`, `Messages expire after 24 hours`, `New message`, `Identity changed`, `Verify identity` | APPROVED | Use only with documented local/state scope. |
| `Sending` | APPROVED | Temporary local send progress only; no recipient implication. |
| `Sent` | NOT PREFERRED V1 | If later used, explicitly means relay acceptance only; never delivered/read. |
| `Anonymous`, `Untraceable`, `Unhackable`, `Military-grade`, `Permanently erased`, `100% secure`, `Nobody can track you` | PROHIBITED | Unsupported or misleading claim. |
| `Encrypted`, `secure`, `protected` | CONTEXTUAL REVIEW | Use only where a decision needs it; do not decorate normal UI. |
| `Identity changed` | APPROVED | Do not imply certain attack or compromise. |

Copy about blockers, libraries, or protocol must retain the distinctions in the privacy-claims audit: architecture rule, target pending implementation, and blocked/not implementable.
