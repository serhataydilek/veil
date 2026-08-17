# ADR 011: visual and interaction language

## Status

Accepted for Phase 0 design specification. This ADR creates no implementation values, assets, Figma files, or application code.

## Context

Veil’s product/security state model requires an interface that is calm and usable without turning temporary capabilities, operational acknowledgements, or peer behavior into social or security theatre.

## Decision

Adopt a quiet, minimal, text-first visual language with system light/dark theme support and semantic design tokens only. There are no avatars, profiles, media controls, social mechanics, presence, typing, delivery, or read UI. The composer is text-only. Privacy/security copy is neutral and local-state-specific; normal screens do not use hacker/cybersecurity imagery or exaggerated security labels.

Use one primary Home conversation list with explicit Add ID, My ID, and Settings entry points rather than bottom tabs. V1 hides conversation-list message previews and omits unread badges. It shows transient Sending only, never persistent sent/delivered/read indicators. Mandatory expiry is a subtle explanation; expired content disappears without tombstones.

Block, reset, destroy, identity-loss, and identity-change screens must express their established terminal/pause semantics precisely. Accessibility, scalable text, contrast, large targets, screen-reader behavior, manual QR alternatives, motion reduction, and realistic platform privacy limitations are requirements. No final colors, dimensions, typography files, or implementation-specific values are selected.

## Consequences

The visual blueprint lives in `docs/design/`. Future implementation must preserve the existing no-oracle, no-presence, no-recovery, text-only, and expiry boundaries. It must not infer that the unresolved rendezvous or secure-session blockers are resolved.

## Open issues

Platform-version implementation/validation for app lock, screenshots, app-switcher behavior, clipboard, keyboard learning, notification history, and dynamic-layout details remains IMPORTANT. Rendezvous and secure-session dependency blockers remain unchanged.
