# Home / conversations

Home is the primary conversation list. Its header identifies Veil and exposes concise entry points for Add ID, My ID, and Settings; implementation may place them in an overflow or a compact visible action arrangement, provided they remain discoverable without tabs.

Each established row shows only a local alias or approved abbreviated established-peer fallback and an optional local last-activity time. **V1 chooses Option B: no message preview in the list.** This reduces shoulder-surfing exposure, prevents an additional plaintext surface, and keeps the list a navigation tool. It does not show temporary rotating IDs as conversation names.

V1 also omits unread badges/markers. A local-only marker could be added only after accessibility/usability review, with no server “read” signal; it is not needed for the initial quiet hierarchy. Rows never include avatar, profile, online/typing state, delivery/read state, social badge, or peer-supplied metadata.

## Empty state

`No conversations` / `Exchange IDs with someone you know to connect.` Actions: **Add ID** and **Show my ID**. Never suggest friends, invitations, discovery, or referrals.
