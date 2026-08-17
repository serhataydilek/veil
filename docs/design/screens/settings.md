# Settings

Keep Settings small and only expose controllable behavior:

- **Privacy:** app lock, screen-privacy explanation, clipboard policy, notification-privacy explanation.
- **Notifications:** permission/status and generic wake-hint explanation.
- **Identity:** My ID, safety information, and destroy identity.
- **Blocked relationships:** local list with explicit Unblock flow.
- **About:** version/legal/privacy information when implemented.

No account, profile, password, recovery, discovery, media, or social settings exist.

## Lock and notifications

Later implementation uses platform credential/biometric authentication only—no custom password or biometric database. Options may be **Lock immediately** and **Lock after short delay**. On lock, hide conversation content and recent-app preview where the platform permits.

Ask notification permission contextually, not at first launch: “Veil uses notifications only to tell you that new encrypted data may be available. Notifications never include the sender or message content.” Actions: **Enable notifications** and **Not now**. Veil remains usable without permission.

## Unblock

Within Blocked relationships, confirmation says: “This does not reconnect you. A new mutual ID exchange is required.” Actions are **Cancel** and **Unblock**. It removes only the local deny state and restores no old session.
