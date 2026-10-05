# Start the project-local Nacos (standalone, no Docker).
#
# Usage (from the project root):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\start-nacos.ps1
#
# Options:
#   -Foreground   run in this window and block (default is detached background)
#   -TimeoutSec   seconds to wait for ports to open (default 180)
#
# Why this script exists instead of calling bin\startup.cmd directly:
#   1. startup.cmd requires JAVA_HOME, which this machine deliberately does NOT
#      set globally; we point it at deploy\.tools\jdk-17 for this process only.
#   2. It gives a clear port table and fails loudly instead of leaving a stray
#      window when Nacos cannot start.
#
# Ports (aligned with docker/run.sh in this repo):
#   8848  main HTTP + gRPC  -> what the microservices connect to
#   8849  console UI        -> browser (upstream default 8080 collides with the gateway)
#   9848  client gRPC       -> 8848 + 1000
#   9849  server gRPC (cluster only, unused in standalone)
param(
    [switch]$Foreground,
    [int]$TimeoutSec = 180
)

$ErrorActionPreference = 'Stop'

# This script lives in <project>\deploy -- go up one level for the project root.
$projectRoot = Split-Path -Parent $PSScriptRoot
$nacosHome   = Join-Path $projectRoot 'deploy\nacos'
$jdkHome     = Join-Path $projectRoot 'deploy\.tools\jdk-17'
$startupCmd  = Join-Path $nacosHome 'bin\startup.cmd'
$logDir      = Join-Path $nacosHome 'logs'
$nacosLog    = Join-Path $logDir 'nacos.log'

$mainPort    = 8848
$consolePort = 8849
$grpcPort    = 9848

function Test-PortOpen([int]$Port, [string]$HostName = '127.0.0.1', [int]$Ms = 700) {
    # Skip if the port is already taken by an unrelated process.
    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($conns) { return $true }
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        if ($async.AsyncWaitHandle.WaitOne($Ms, $false) -and $client.Connected) {
            $client.Close(); return $true
        }
        $client.Close(); return $false
    } catch { return $false }
}

if (-not (Test-Path $startupCmd)) {
    Write-Host "Nacos not found at $nacosHome" -ForegroundColor Red
    Write-Host "Run first: powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\fetch-nacos.ps1" -ForegroundColor Yellow
    exit 1
}
$javaExe = Join-Path $jdkHome 'bin\java.exe'
if (-not (Test-Path $javaExe)) {
    Write-Host "Project-local JDK not found: $javaExe" -ForegroundColor Red
    Write-Host "Run first: powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\.tools\fetch-jdk17.ps1" -ForegroundColor Yellow
    exit 1
}

if (Test-PortOpen $mainPort) {
    Write-Host "Port $mainPort is already listening -- Nacos looks already running." -ForegroundColor Yellow
    Write-Host "Console: http://127.0.0.1:$consolePort/   (stop with .\deploy\stop-nacos.ps1)" -ForegroundColor Yellow
    exit 0
}

New-Item -ItemType Directory -Path $logDir -Force | Out-Null

# JAVA_HOME etc. are set only in this process and inherited by startup.cmd.
$env:JAVA_HOME = $jdkHome
$env:PATH = (Join-Path $jdkHome 'bin') + ';' + $env:PATH
# 512m is the standalone default; keep it explicit so the setting is discoverable.
if (-not $env:CUSTOM_NACOS_MEMORY) { $env:CUSTOM_NACOS_MEMORY = '-Xms512m -Xmx512m -Xmn256m' }

Write-Host "Starting Nacos (standalone) ..." -ForegroundColor Cyan
Write-Host "  JAVA_HOME = $env:JAVA_HOME"
Write-Host "  NACOS     = $nacosHome"
Write-Host "  Log       = $nacosLog"
Write-Host ""

if ($Foreground) {
    & $startupCmd -m standalone
    exit $LASTEXITCODE
}

# Launch javaw.exe (windowless JVM) directly instead of shelling out to
# bin\startup.cmd. Two reasons:
#   1. startup.cmd runs in a console window; closing that window kills Nacos.
#      javaw has no console, so nothing can be closed by accident.
#   2. The environment reaches the server process directly, so no wrapper cmd
#      sits between us and the JVM.
# The argument list below mirrors what startup.cmd builds for -m standalone.
$javawExe  = Join-Path $jdkHome 'bin\javaw.exe'
$serverJar = Join-Path $nacosHome 'target\nacos-server.jar'
if (-not (Test-Path $serverJar)) { throw "Missing $serverJar" }

$jvmMemory = if ($env:CUSTOM_NACOS_MEMORY) { $env:CUSTOM_NACOS_MEMORY } else { '-Xms512m -Xmx512m -Xmn256m' }
$nacosHomeFwd = $nacosHome.Replace('\', '/')

# Java 9+ needs these --add-opens flags (startup.cmd adds them the same way).
$major = 0
try {
    $verText = (& (Join-Path $jdkHome 'bin\java.exe') -version 2>&1 | Select-Object -First 1) -replace '"', ''
    if ($verText -match 'version "(\d+)') { $major = [int]$Matches[1] }
    elseif ($verText -match 'version "1\.(\d+)') { $major = [int]$Matches[1] }
} catch { $major = 0 }
$addOpens = @()
if ($major -ge 9) {
    $addOpens = @(
        '--add-opens=java.base/java.lang=ALL-UNNAMED',
        '--add-opens=java.base/java.lang.reflect=ALL-UNNAMED',
        '--add-opens=java.base/java.util=ALL-UNNAMED'
    )
}

$argList = @()
$argList += ($jvmMemory -split '\s+' | Where-Object { $_ })
$argList += $addOpens
$argList += '-Dnacos.standalone=true'
$argList += '-Dnacos.deployment.type=merged'
$argList += "-Dnacos.home=$nacosHomeFwd"
$argList += "-Dloader.path=$nacosHomeFwd/plugins,$nacosHomeFwd/plugins/health,$nacosHomeFwd/plugins/cmdb,$nacosHomeFwd/plugins/selector"
$argList += '-jar'
$argList += $serverJar
$argList += "--spring.config.additional-location=file:$nacosHomeFwd/conf/"
$argList += "--logging.config=$nacosHomeFwd/conf/nacos-logback.xml"
$argList += 'nacos.nacos'

Start-Process -FilePath $javawExe -ArgumentList $argList `
    -WorkingDirectory (Join-Path $nacosHome 'bin') -WindowStyle Hidden | Out-Null

$sw = [Diagnostics.Stopwatch]::StartNew()
while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
    if (Test-PortOpen $mainPort) { break }
    Start-Sleep -Seconds 2
}
$sw.Stop()

if (Test-PortOpen $mainPort) {
    Write-Host ("Nacos is up after {0:N0}s" -f $sw.Elapsed.TotalSeconds) -ForegroundColor Green
    Write-Host "  main port : $mainPort  (microservices: spring.cloud.nacos.server-addr=127.0.0.1:$mainPort)"
    Write-Host "  gRPC      : $grpcPort"
    Write-Host "  console   : http://127.0.0.1:$consolePort/  -> 302 to /next/" -ForegroundColor Green
    Write-Host ""
    Write-Host "Stop with: powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\stop-nacos.ps1" -ForegroundColor Green
    exit 0
}

Write-Host "Nacos did not open port $mainPort within $TimeoutSec s." -ForegroundColor Red

# The console UI writes its own startup error log; surface it if present.
$startupLog = Join-Path $logDir 'start.out'
if (Test-Path $startupLog) {
    Write-Host ""
    Write-Host "--- tail of logs\start.out ---" -ForegroundColor Yellow
    Get-Content $startupLog -Tail 40 | ForEach-Object { "  $_" }
}
if (Test-Path $nacosLog) {
    Write-Host "--- tail of logs\nacos.log ---" -ForegroundColor Yellow
    Get-Content $nacosLog -Tail 40 | ForEach-Object { "  $_" }
}
exit 1
