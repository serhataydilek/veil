# Logging baseline

Phase 1A has no third-party logging, analytics, or crash-reporting dependency. Android system diagnostics, if added later, must be generic lifecycle/development information only. Rust emits no logs.

Never log contact IDs, aliases, user-entered text, message content, tokens, mailbox handles, session state, private keys, or future key material. Debugging and diagnostics that could change this rule require a separate privacy/security review and redaction tests.
