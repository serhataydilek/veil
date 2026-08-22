param([Parameter(Mandatory)][string]$ApkPath)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) { throw "APK not found: $ApkPath" }
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if (-not $sdk) { throw 'ANDROID_SDK_ROOT or ANDROID_HOME is required for APK manifest inspection.' }
$aapt = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'aapt.exe' } | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $aapt) { throw 'aapt.exe was not found in Android build-tools.' }
$permissions = & $aapt dump permissions $ApkPath | Select-String "uses-permission: name='([^']+)'" | ForEach-Object { $_.Matches[0].Groups[1].Value }
$allowed = @('android.permission.USE_BIOMETRIC','android.permission.USE_FINGERPRINT','android.permission.POST_NOTIFICATIONS','com.veil.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION')
$unexpected = $permissions | Where-Object { $_ -notin $allowed }
if ($unexpected) { throw "Unexpected APK permission(s): $($unexpected -join ', ')" }
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $ApkPath))
try {
  $names = @($zip.Entries | ForEach-Object FullName)
  $forbidden = $names | Where-Object { $_ -match '(?i)(\.pdb$|\.rs$|Cargo\.(toml|lock)$|\.pem$|\.p12$|\.jks$|\.keystore$|(^|/)\.env)' -or $_ -match '(?i)(users/|/home/|\.cargo/registry|rust/crates)' }
  if ($forbidden) { throw "Forbidden APK entries: $($forbidden -join ', ')" }
  $expectedNative = @('libveil_ffi.so','libjnidispatch.so','libandroidx.graphics.path.so')
  $native = $names | Where-Object { $_ -match '^lib/(arm64-v8a|x86_64)/' }
  $expectedEntries = foreach ($abi in @('arm64-v8a','x86_64')) { foreach ($library in $expectedNative) { "lib/$abi/$library" } }
  if (@($native | Where-Object { $_ -notin $expectedEntries }) -or @($expectedEntries | Where-Object { $_ -notin $native })) { throw "Unexpected native APK entries: $($native -join ', ')" }
  $otherNative = $names | Where-Object { $_ -match '^lib/' -and $_ -notin $expectedEntries }
  if ($otherNative) { throw "Unexpected native APK entries: $($otherNative -join ', ')" }
} finally { $zip.Dispose() }
Write-Output "APK audit passed: $ApkPath"
