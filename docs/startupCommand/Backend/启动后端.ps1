# CipherWS backend launcher (single entry point for the whole backend).
#
# Location-independent: every path it needs is derived from THIS file's folder
# by walking up until the repository root is found (the folder that contains
# deploy\, server\ and docs\ together). Move this file anywhere inside the
# repository and it keeps working -- there are no absolute paths and no
# hard-coded repository folder name.
#
# It drives the scripts inside deploy\ instead of duplicating their logic:
#   deploy\start-nacos.ps1   deploy\stop-nacos.ps1   deploy\run-services.ps1
#
# The FILE NAME is Chinese, but the CONTENT is intentionally ASCII-only:
# Windows PowerShell 5.1 reads .ps1 files as ANSI, so non-ASCII characters
# inside a script get mangled (verified: they showed up as mojibake in output).
# Chinese in file names is fine; inside the file it is not. The script prints
# its own name at runtime by building it from code points ($selfName).
#
# Usage (run from this folder; -Action defaults to start):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\<script name>.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\<script name>.ps1 -Action status
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\<script name>.ps1 -Action stop
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\<script name>.ps1 -Action restart
param(
    [ValidateSet('start', 'stop', 'status', 'restart')]
    [string]$Action = 'start',

    # seconds to wait for Nacos port 8848 to open
    [int]$NacosTimeoutSec = 240
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------- paths -----
# Everything is derived from THIS file's location, so the script keeps working
# wherever it is moved to inside the repository -- no absolute paths, no
# hard-coded repository folder name.
#
# The repository root is recognised by containing these three folders together
# (deploy is the one holding the scripts this launcher drives). We walk up from
# the script's own directory until we find it.
$markers = @('deploy', 'server', 'docs')
$repoDir = $PSScriptRoot
$found = $false
for ($i = 0; $i -lt 10; $i++) {
    $ok = $true
    foreach ($m in $markers) { if (-not (Test-Path (Join-Path $repoDir $m))) { $ok = $false } }
    if ($ok) { $found = $true; break }
    $parent = Split-Path -Parent $repoDir
    if (-not $parent) { break }
    $repoDir = $parent
}
if (-not $found) {
    Write-Host "Could not locate the CipherWS repository from: $PSScriptRoot" -ForegroundColor Red
    Write-Host "Expected to find deploy\, server\ and docs\ in one of the parent folders." -ForegroundColor Red
    exit 1
}

$deployDir = Join-Path $repoDir 'deploy'
$serverDir = Join-Path $repoDir 'server'
$jdkHome   = Join-Path $deployDir '.tools\jdk-17'
$logDir    = Join-Path $deployDir '.logs'

# Path prefix used in the "install the toolchain" hints, relative to this script.
$scriptDir  = $PSScriptRoot
$repoRel    = '.'
if ($scriptDir -ne $repoDir) {
    $repoRel = (Resolve-Path -Relative -Path $repoDir) -replace '^\.\\', ''
}

$psExe = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
if (-not (Test-Path $psExe)) { $psExe = 'powershell.exe' }

# The deploy scripts are invoked IN THIS PROCESS (see Invoke-DeployScript).
# Spawning `powershell -File ...` instead was tried first and caused the parent
# process to hang: the detached java processes started by run-services.ps1
# inherit the redirected stdio handles, so the parent never sees EOF on the
# child's output pipe. Calling in-process avoids that entirely.
#
# Script execution must be allowed for that to work, so bypass the policy for
# this process only (no system setting is modified).
try { Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force } catch { }

# The three services, in start order (say -> portal -> gateway).
# Ports must match each module's application.yml default.
$services = @(
    @{ Name = 'cipherws-say-service';    Port = 8081; Jar = 'cipherws-say-service\target\cipherws-say-service-0.0.1-SNAPSHOT.jar' },
    @{ Name = 'cipherws-portal-service'; Port = 8082; Jar = 'cipherws-portal-service\target\cipherws-portal-service-0.0.1-SNAPSHOT.jar' },
    @{ Name = 'cipherws-gateway';        Port = 8080; Jar = 'cipherws-gateway\target\cipherws-gateway-0.0.1-SNAPSHOT.jar' }
)

# endpoints used for the final verification
$apiUrl     = 'http://127.0.0.1:8080/say/saying?id=1'
$portalUrl  = 'http://127.0.0.1:8080/portal/say/2'
$consoleUrl = 'http://127.0.0.1:8849/'

# --------------------------------------------------------------- helpers ----
function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Get-PortState([int]$Port) {
    if (Test-Port $Port) { return 'UP' } else { return 'down' }
}

function Invoke-DeployScript([string]$Name, [hashtable]$Arguments) {
    $scriptPath = Join-Path $deployDir $Name
    if (-not (Test-Path $scriptPath)) { throw "Missing deploy script: $scriptPath" }
    # Hashtable splatting, so parameters are matched BY NAME. An array
    # (@('-Action','stop')) would bind '-Action' as the VALUE of -Action and fail.
    # Child scope keeps the deploy script's variables out of ours.
    # NOTE: these scripts call `exit` on some paths; when run in-process that
    # would terminate this script too, so callers must avoid those paths
    # (e.g. Start-Backend skips run-services.ps1 when every port is already up).
    & $scriptPath @Arguments
}

function Get-RunningServices {
    return @($services | Where-Object { Test-Port $_.Port })
}

function Show-Status {
    Write-Host ''
    Write-Host '  Component                 Port    State' -ForegroundColor White
    Write-Host '  ------------------------  ------  -------' -ForegroundColor DarkGray
    foreach ($s in $services) {
        $state = Get-PortState $s.Port
        $color = if ($state -eq 'UP') { 'Green' } else { 'Red' }
        Write-Host ("  {0,-24}  {1,-6}  {2}" -f $s.Name, $s.Port, $state) -ForegroundColor $color
    }
    foreach ($p in 8848, 8849, 9848) {
        $state = Get-PortState $p
        $color = if ($state -eq 'UP') { 'Green' } else { 'Red' }
        Write-Host ("  {0,-24}  {1,-6}  {2}" -f "nacos ($p)", $p, $state) -ForegroundColor $color
    }
    Write-Host ''
}

function Assert-Prerequisites {
    $missing = @()
    if (-not (Test-Path (Join-Path $jdkHome 'bin\java.exe'))) { $missing += "project-local JDK 17  -> $jdkHome" }
    if (-not (Test-Path (Join-Path $deployDir 'nacos\target\nacos-server.jar'))) { $missing += "Nacos                 -> $deployDir\nacos" }
    foreach ($s in $services) {
        $jar = Join-Path $serverDir $s.Jar
        if (-not (Test-Path $jar)) { $missing += "missing jar: $($s.Name)  (build with server\mvnw.cmd clean package)" }
    }
    if ($missing.Count) {
        Write-Host 'Prerequisites missing:' -ForegroundColor Red
        foreach ($m in $missing) { Write-Host "  - $m" -ForegroundColor Red }
        Write-Host ''
        Write-Host 'Install the project toolchain first:' -ForegroundColor Yellow
        Write-Host ("  powershell -NoProfile -ExecutionPolicy Bypass -File {0}\deploy\.tools\fetch-jdk17.ps1" -f $repoRel) -ForegroundColor Yellow
        Write-Host ("  powershell -NoProfile -ExecutionPolicy Bypass -File {0}\deploy\fetch-nacos.ps1" -f $repoRel) -ForegroundColor Yellow
        Write-Host ("  (paths are relative to this script: {0})" -f $PSScriptRoot) -ForegroundColor DarkGray
        exit 1
    }
}

function Start-NacosIfNeeded {
    if (Test-Port 8848) {
        Write-Host '[1/3] Nacos already running on 8848 -- reusing it' -ForegroundColor Yellow
        return
    }
    Write-Host '[1/3] Starting Nacos (standalone) ...' -ForegroundColor Cyan
    Invoke-DeployScript 'start-nacos.ps1' @{ TimeoutSec = $NacosTimeoutSec }
    if (-not (Test-Port 8848)) { throw 'Nacos did not open port 8848' }
}

function Start-Backend {
    $running = Get-RunningServices
    if ($running.Count -eq $services.Count) {
        Write-Host '[2/3] All three services already listening -- nothing to start' -ForegroundColor Yellow
        return
    }

    Write-Host '[2/3] Starting the three backend services ...' -ForegroundColor Cyan
    Invoke-DeployScript 'run-services.ps1' @{ Action = 'start' }

    $allUp = $true
    foreach ($s in $services) { if (-not (Test-Port $s.Port)) { $allUp = $false } }
    if (-not $allUp) {
        Write-Host 'Not every service is listening. Recent logs:' -ForegroundColor Red
        Get-ChildItem $logDir -Filter '*.out.log' -ErrorAction SilentlyContinue | ForEach-Object {
            Write-Host "--- tail of $($_.Name) ---" -ForegroundColor Yellow
            Get-Content $_.FullName -Tail 15 -ErrorAction SilentlyContinue | ForEach-Object { "  $_" }
        }
        exit 1
    }
}

function Test-Endpoints {
    Write-Host '[3/3] Checking endpoints ...' -ForegroundColor Cyan
    $checks = @(
        @{ Label = 'GET /say/saying?id=1   (gateway -> say)';    Url = $apiUrl;     Want = 200 },
        @{ Label = 'GET /portal/say/2      (gateway -> portal)'; Url = $portalUrl;  Want = 200 },
        @{ Label = 'Nacos console web UI';                        Url = $consoleUrl; Want = 302 }
    )
    $failed = 0
    foreach ($c in $checks) {
        $code = & curl.exe -sS -o NUL -w '%{http_code}' --max-time 25 $c.Url 2>$null
        $ok = ("$code" -eq "$($c.Want)")
        if (-not $ok) { $failed++ }
        $color = if ($ok) { 'Green' } else { 'Red' }
        Write-Host ("  {0,-46} HTTP {1}" -f $c.Label, $code) -ForegroundColor $color
    }
    Write-Host ''
    if ($failed -eq 0) {
        Write-Host 'Backend is up.' -ForegroundColor Green
    } else {
        Write-Host "$failed endpoint check(s) failed -- ports are open, the chain may need a few more seconds." -ForegroundColor Yellow
        Write-Host 'Re-check with:  -Action status' -ForegroundColor Yellow
    }
    Write-Host ''
    Write-Host '  API base   : http://127.0.0.1:8080' -ForegroundColor White
    Write-Host "  Nacos UI   : $consoleUrl  (auth disabled locally)" -ForegroundColor White
    Write-Host "  Logs       : $logDir" -ForegroundColor White
    Write-Host ''
    Write-Host '  Stop with  : powershell -NoProfile -ExecutionPolicy Bypass -File .\<this script> -Action stop' -ForegroundColor DarkGray
}

function Stop-Backend {
    Write-Host '[1/2] Stopping the three backend services ...' -ForegroundColor Cyan
    Invoke-DeployScript 'run-services.ps1' @{ Action = 'stop' }
    Write-Host '[2/2] Stopping Nacos ...' -ForegroundColor Cyan
    Invoke-DeployScript 'stop-nacos.ps1' @{}
    Write-Host ''
    $left = @()
    foreach ($p in 8080, 8081, 8082, 8848, 8849, 9848) { if (Test-Port $p) { $left += $p } }
    if ($left.Count) {
        Write-Host ("Still listening: {0}" -f ($left -join ', ')) -ForegroundColor Yellow
    } else {
        Write-Host 'All backend ports released.' -ForegroundColor Green
    }
}

# ------------------------------------------------------------------ main ----
Write-Host ''
Write-Host '  CipherWS backend' -ForegroundColor White
Write-Host "  repo: $repoDir" -ForegroundColor DarkGray
Write-Host ''

switch ($Action) {
    'status' {
        Show-Status
        Write-Host '  Start : powershell -NoProfile -ExecutionPolicy Bypass -File .\<this script>' -ForegroundColor DarkGray
        Write-Host '  Stop  : powershell -NoProfile -ExecutionPolicy Bypass -File .\<this script> -Action stop' -ForegroundColor DarkGray
        Write-Host ''
    }
    'stop' {
        Stop-Backend
        Write-Host ''
    }
    'restart' {
        Stop-Backend
        Write-Host ''
        Assert-Prerequisites
        Start-NacosIfNeeded
        Start-Backend
        Test-Endpoints
    }
    default {
        Assert-Prerequisites
        Start-NacosIfNeeded
        Start-Backend
        Test-Endpoints
    }
}
