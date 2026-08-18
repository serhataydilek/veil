# Privacy-sensitive screen behavior

| Surface | Control level | Direction and limitation |
|---|---|---|
| Recent-app preview | Platform-controllable where available | Phase 1C disables recents screenshots on API 33+ and relies on `FLAG_SECURE` plus lock/background behavior on older APIs. This is not universal. |
| Screenshots / recording | Partially controllable | Phase 1C always applies `FLAG_SECURE`. Never promise universal prevention. |
| Clipboard | Partially controllable | Copy is explicit with warning; clear only where OS permits. Other apps/keyboard may observe clipboard under platform rules. |
| Keyboard suggestions | Partially controllable | Evaluate disabling personalized learning for composer where Android permits; balance usability, IME/version behavior, and validate later. |
| Accessibility services | Not reliably controllable | Respect user-granted platform access; do not make a false confidentiality claim. |
| Autofill | Platform-controllable in part | Do not expose identity/ID/message fields as account credentials; validate platform behavior. |
| Notification history | Not reliably controllable | Generic `New message` minimizes content; OS/provider may retain timing/history outside Veil control. |

These are design constraints only. Implementation must assess Android version, device policy, and accessibility impact before claiming a control exists.
