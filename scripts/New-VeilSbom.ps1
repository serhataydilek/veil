param([string]$OutputDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts\sbom'))
$ErrorActionPreference = 'Stop'; $root = Split-Path $PSScriptRoot -Parent; New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$cargo = cargo metadata --locked --format-version 1 --manifest-path (Join-Path $root 'rust\Cargo.toml') | ConvertFrom-Json
$components = @($cargo.packages | ForEach-Object { [ordered]@{ type='library'; name=$_.name; version=$_.version; purl="pkg:cargo/$($_.name)@$($_.version)" } })
$rustBom = [ordered]@{ bomFormat='CycloneDX'; specVersion='1.5'; serialNumber='urn:uuid:00000000-0000-0000-0000-000000000000'; version=1; metadata=[ordered]@{ component=[ordered]@{ type='application'; name='veil-rust'; version='0.1.0' } }; components=$components }
$rustBom | ConvertTo-Json -Depth 8 | Set-Content -NoNewline (Join-Path $OutputDirectory 'veil-rust.cdx.json')
$catalog = Get-Content (Join-Path $root 'android\gradle\libs.versions.toml') -Raw
$android = [ordered]@{ format='veil-android-version-catalog'; generatedFrom='android/gradle/libs.versions.toml'; sha256=(Get-FileHash (Join-Path $root 'android\gradle\libs.versions.toml') -Algorithm SHA256).Hash; content=$catalog }
$android | ConvertTo-Json -Depth 4 | Set-Content -NoNewline (Join-Path $OutputDirectory 'veil-android-inventory.json')
Write-Output "SBOM/inventory written to $OutputDirectory"
