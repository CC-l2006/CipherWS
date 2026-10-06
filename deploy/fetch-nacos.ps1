# Project-local Nacos 3.3.0-RC installer.
# Installs into <project>\deploy\nacos -- it does NOT create services, does not
# touch system environment variables, PATH or the registry.
#
# Usage (run from the project root; Windows PowerShell 5.1 is enough):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\fetch-nacos.ps1
#
# Why 3.3.0-RC specifically: the project targets Spring Cloud Alibaba 2025.0.0.0,
# which ships nacos-client 3.0.3; the docker assets in docker/ also reference
# nacos/nacos-server:v3.3.0-RC. Keep the server version aligned with those.
$ErrorActionPreference = 'Stop'

# This script lives in <project>\deploy -- go up one level for the project root.
$projectRoot = Split-Path -Parent $PSScriptRoot
$nacosHome   = Join-Path $projectRoot 'deploy\nacos'

$version  = '3.3.0-RC'
$fileName = "nacos-server-$version.zip"
# Primary source: Nacos' own CDN (measured ~11x faster than GitHub from CN networks).
# Fallback: the GitHub release asset, which publishes the same zip and md5.
$sources  = @(
    "https://download.nacos.io/nacos-server/$fileName",
    "https://github.com/alibaba/nacos/releases/download/$version/$fileName"
)
$size     = 230078214   # exact byte count of the 3.3.0-RC zip
# The .md5 sidecar only exists on the GitHub release, not on download.nacos.io.
$md5Url   = "https://github.com/alibaba/nacos/releases/download/$version/$fileName.md5"
# Fallback used only if the .md5 file cannot be fetched/parsed.
$md5Expected = 'c5e4127108ba3f77c7230735b4f07a4d'

# This script lives next to the payload it installs.
$deployDir = $PSScriptRoot
$zip = Join-Path $deployDir $fileName

if (Test-Path (Join-Path $nacosHome 'bin\startup.cmd')) {
    Write-Host "[SKIP] $nacosHome already looks installed" -ForegroundColor Yellow
    exit 0
}

function Get-Text([string]$u) {
    $c = & curl.exe -sS -L -A 'Mozilla/5.0' --max-time 60 $u
    if ($LASTEXITCODE -ne 0) { throw "curl failed for $u" }
    return $c
}

# 1) Download
if (Test-Path $zip) {
    $got = (Get-Item $zip).Length
    if ($got -lt $size) {
        Write-Host "Existing archive looks truncated ($got bytes), re-downloading" -ForegroundColor Yellow
        Remove-Item $zip -Force
    }
}
if (-not (Test-Path $zip)) {
    Write-Host "[1/4] Downloading Nacos $version (~219 MB) ..." -ForegroundColor Cyan
    $ok = $false
    foreach ($src in $sources) {
        Write-Host "      trying $src" -ForegroundColor DarkGray
        $sw = [Diagnostics.Stopwatch]::StartNew()
        & curl.exe -sS -L -A 'Mozilla/5.0' --retry 2 --retry-delay 2 --max-time 3600 -o $zip $src
        $sw.Stop()
        if ($LASTEXITCODE -eq 0 -and (Test-Path $zip) -and (Get-Item $zip).Length -eq $size) {
            Write-Host ("      done: {0:N1} MB in {1:N1}s" -f ((Get-Item $zip).Length / 1MB), $sw.Elapsed.TotalSeconds)
            $ok = $true
            break
        }
        Write-Host "      source failed or size mismatch, trying next" -ForegroundColor Yellow
        if (Test-Path $zip) { Remove-Item $zip -Force }
    }
    if (-not $ok) { throw "All download sources failed" }
}

# 2) Verify against the official md5 published next to the asset.
#    The published file uses the BSD form:  MD5 (nacos-server-3.3.0-RC.zip) = <hash>
Write-Host "[2/4] Verifying MD5 against the official .md5 ..." -ForegroundColor Cyan
$official = $md5Expected
try {
    $raw = (Get-Text $md5Url)
    $m = [regex]::Match($raw, '(?i)\b[0-9a-f]{32}\b')
    if ($m.Success) { $official = $m.Value.ToLower() }
    else { Write-Host "      could not parse official .md5, using pinned value" -ForegroundColor Yellow }
} catch {
    Write-Host "      could not fetch official .md5, using pinned value" -ForegroundColor Yellow
}
$actual = (Get-FileHash -LiteralPath $zip -Algorithm MD5).Hash.ToLower()
if ($actual -ne $official) { throw "MD5 mismatch.`n official $official`n actual   $actual" }
Write-Host "      OK $actual"

# 3) Extract
Write-Host "[3/4] Extracting ..." -ForegroundColor Cyan
$tmp = Join-Path $deployDir '_tmp_nacos'
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
Expand-Archive -LiteralPath $zip -DestinationPath $tmp -Force

$extracted = Get-ChildItem $tmp -Directory | Where-Object { $_.Name -like 'nacos*' } | Select-Object -First 1
if (-not $extracted) { throw "No nacos* directory found in the archive" }
New-Item -ItemType Directory -Path (Split-Path -Parent $nacosHome) -Force | Out-Null
Move-Item -Path $extracted.FullName -Destination $nacosHome
Remove-Item $tmp -Recurse -Force

Write-Host "[4/4] Installed." -ForegroundColor Cyan
Write-Host ""
Write-Host "Nacos installed at: $nacosHome" -ForegroundColor Green
Write-Host "Next: configure and start with  .\deploy\start-nacos.ps1" -ForegroundColor Green
