# Veil

Veil is an Android-first private ephemeral messenger under development. This repository is not a finished secure messenger: end-to-end encryption is not integrated, and mutual rendezvous remains under external security review.

The security/privacy architecture and implementation gates live in [`docs/`](docs/). Phase 1A provides only a local Android shell and a Rust core boundary that fails closed for unresolved security functionality.

## Development

```powershell
cd android
.\gradlew.bat test
.\gradlew.bat assembleDebug

cd ..\rust
cargo fmt --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test --all
```

See [`docs/implementation/phase-1a-status.md`](docs/implementation/phase-1a-status.md), [`docs/implementation/phase-1b-status.md`](docs/implementation/phase-1b-status.md), and [`docs/implementation/phase-1c-status.md`](docs/implementation/phase-1c-status.md) for implemented, blocked, and deferred work.
