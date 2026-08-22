param([string]$RepositoryRoot = (Split-Path $PSScriptRoot -Parent))
$ErrorActionPreference = 'Stop'
$excluded = '\\(\.git|\.gradle|build|target|\.idea)\\'
$files = Get-ChildItem -LiteralPath $RepositoryRoot -Recurse -File | Where-Object { $_.FullName -notmatch $excluded }
$forbiddenFiles = $files | Where-Object { $_.Name -match '(?i)^(local\.properties|\.env(\..*)?)$|\.(jks|keystore|p12|pfx)$|^id_(rsa|ed25519)$' }
if ($forbiddenFiles) { throw "Forbidden sensitive file(s): $($forbiddenFiles.FullName -join ', ')" }
$patterns = @('-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----','(?i)(api[_-]?key|secret|token)\s*[=:]\s*["''][A-Za-z0-9_-]{12,}["'']','(?i)([a-z]:\\users\\|/home/)')
$hits = foreach ($file in $files | Where-Object { $_.Length -lt 2MB -and $_.FullName -notmatch '\\scripts\\Test-Veil(Apk|RepositoryHygiene)\.ps1$' }) { Select-String -LiteralPath $file.FullName -Pattern $patterns -AllMatches -ErrorAction SilentlyContinue }
if ($hits) { throw "Potential secret or developer path detected: $((@($hits.Path | Select-Object -Unique)) -join ', ')" }
Write-Output 'Repository hygiene passed.'
