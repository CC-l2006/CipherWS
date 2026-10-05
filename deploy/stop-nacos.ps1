# Stop the project-local Nacos started by deploy\start-nacos.ps1.
#
# Usage (from the project root):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\stop-nacos.ps1
#
# Nacos runs as a plain java.exe process (no Windows service, no container).
# Strategy, in order:
#   1. whatever process is listening on the Nacos main port (most reliable --
#      command-line matching can miss depending on how the JVM was launched);
#   2. any java.exe whose command line references our deploy\nacos\target jar.
# It never touches unrelated java processes (e.g. the Spring Boot services).
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$nacosHome   = Join-Path $projectRoot 'deploy\nacos'
$jarName     = 'nacos-server.jar'
$mainPort    = 8848

$targets = @{}

# 1) Owner of the Nacos main port (Nacos may run under java.exe OR javaw.exe --
#    start-nacos.ps1 uses javaw so it has no console window that could be closed).
$conn = Get-NetTCPConnection -LocalPort $mainPort -State Listen -ErrorAction SilentlyContinue
foreach ($c in $conn) {
    $proc = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
    if ($proc -and $proc.ProcessName -in @('java', 'javaw')) {
        $targets[$c.OwningProcess] = "listening on $mainPort"
    }
}

# 2) java/javaw process referencing our Nacos jar
$procs = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -like "*$jarName*" }
foreach ($p in $procs) { $targets[$p.ProcessId] = 'command line references ' + $jarName }

if ($targets.Count -eq 0) {
    Write-Host "No running Nacos process found (project: $nacosHome)" -ForegroundColor Yellow
    exit 0
}

foreach ($procId in $targets.Keys) {
    Write-Host ("Stopping Nacos PID {0} ({1}) ..." -f $procId, $targets[$procId]) -ForegroundColor Cyan
    Stop-Process -Id $procId -Force -ErrorAction Continue
}

Start-Sleep -Seconds 5

$left = Get-NetTCPConnection -LocalPort $mainPort -State Listen -ErrorAction SilentlyContinue
if ($left) {
    Write-Host "Port $mainPort is still listening (PID $($left.OwningProcess -join ','))" -ForegroundColor Red
    exit 1
}

Write-Host "Nacos stopped (ports $mainPort/8849/9848 released)." -ForegroundColor Green
