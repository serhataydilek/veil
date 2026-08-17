# Mandatory message-expiry UX

Use one subtle stable conversation-header note: `Messages expire after 24 hours.` It is explanatory, not a dramatic timer. Do not use self-destructing, burn timer, destruction countdown, or spy language.

V1 removes expired content cleanly with no per-message tombstone. If all content disappears, show an empty body or `No messages`; preserve only the permitted conversation shell. Do not show a countdown beside each message. A long-press may present remaining lifetime only while local time confidence is sufficient; otherwise no false precision is shown.

When the app opens after expiry, content is purged before rendering. If time is suspicious, explain possible early expiry through the dedicated local banner/state, rather than inventing an exact deadline.
