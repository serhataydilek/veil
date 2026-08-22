param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'; $root = Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'Test-VeilRepositoryHygiene.ps1') -RepositoryRoot $root
Push-Location (Join-Path $root 'rust'); try { cargo fmt --check; cargo clippy --workspace --all-targets --all-features -- -D warnings; cargo test --workspace --all-features --locked } finally { Pop-Location }
Push-Location (Join-Path $root 'android'); try { if (-not $SkipBuild) { .\gradlew.bat test lint assembleDebug assembleRelease assembleAndroidTest --no-daemon }; & (Join-Path $PSScriptRoot 'Test-VeilApk.ps1') -ApkPath 'app\build\outputs\apk\debug\app-debug.apk'; & (Join-Path $PSScriptRoot 'Test-VeilApk.ps1') -ApkPath 'app\build\outputs\apk\release\app-release-unsigned.apk' } finally { Pop-Location }
& (Join-Path $PSScriptRoot 'New-VeilSbom.ps1') -OutputDirectory (Join-Path $root 'artifacts\sbom')
git -C $root diff --check
