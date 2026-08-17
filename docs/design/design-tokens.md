# Design tokens

Tokens are semantic names, not Kotlin values, fixed colors, dimensions, or font files.

| Family | Tokens | Intent |
|---|---|---|
| Color | `color.background`, `color.surface`, `color.surface.elevated`, `color.text.primary`, `color.text.secondary`, `color.text.muted`, `color.accent`, `color.warning`, `color.destructive`, `color.success`, `color.divider` | Theme-resolved meaning; never use color alone for trust or status. |
| Spacing | `spacing.xs`, `spacing.sm`, `spacing.md`, `spacing.lg`, `spacing.xl` | Consistent rhythm, margins, row gaps, and dialog padding. |
| Radius | `radius.small`, `radius.medium`, `radius.large` | Restrained grouping; not decorative card proliferation. |
| Typography | `typography.display`, `typography.title`, `typography.section`, `typography.body`, `typography.message`, `typography.secondary`, `typography.metadata`, `typography.action`, `typography.destructive`, `typography.id` | System typography, scalable text; `id` is grouped monospace only. |
| Interaction | `state.focus`, `state.pressed`, `state.disabled`, `state.selected`, `state.warning`, `state.destructive` | Visible non-color-only feedback and predictable accessibility focus. |
