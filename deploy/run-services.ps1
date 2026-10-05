# Start / stop the three CipherWS backend services with the project-local JDK.
# Used to verify the full chain: gateway(8080) -> say(8081) / portal(8082) via Nacos.
#
# Usage (from the project root):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\run-services.ps1 -Action start
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\run-services.ps1 -Action stop
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\run-services.ps1 -Action status
param(
    [ValidateSet('start', 'stop', 'status')]
    [string]$Action = 'status'
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$serverDir   = Join-Path $projectRoot 'server'
$jdkHome     = Join-Path $projectRoot 'deploy\.tools\jdk-17'
$logDir      = Join-Path $projectRoot 'deploy\.logs'

$services = @(
    @{ Name = 'cipherws-say-service';    Port = 8081; Jar = 'cipherws-say-service\target\cipherws-say-service-0.0.1-SNAPSHOT.jar' },
    @{ Name = 'cipherws-portal-service'; Port = 8082; Jar = 'cipherws-portal-service\target\cipherws-portal-service-0.0.1-SNAPSHOT.jar' },
    @{ Name = 'cipherws-gateway';        Port = 8080; Jar = 'cipherws-gateway\target\cipherws-gateway-0.0.1-SNAPSHOT.jar' }
)

function Get-ServiceProcs {
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -like '*cipherws*' }
}

function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

switch ($Action) {
    'status' {
        Write-Host 'Ports:'
        foreach ($s in $services) {
            Write-Host ("  {0,-24} {1}  {2}" -f $s.Name, $s.Port, $(if (Test-Port $s.Port) { 'UP' } else { 'down' }))
        }
        Write-Host 'Nacos:'
        foreach ($p in 8848, 8849, 9848) {
            Write-Host ("  {0,-24} {1}  {2}" -f 'nacos', $p, $(if (Test-Port $p) { 'UP' } else { 'down' }))
        }
    }

    'stop' {
        $procs = Get-ServiceProcs
        if (-not $procs) { Write-Host 'No CipherWS service process found.' -ForegroundColor Yellow; exit 0 }
        foreach ($p in $procs) {
            Write-Host ("Stopping PID {0} ..." -f $p.ProcessId) -ForegroundColor Cyan
            Stop-Process -Id $p.ProcessId -Force -ErrorAction Continue
        }
        Start-Sleep -Seconds 4
        Write-Host 'Stopped.' -ForegroundColor Green
    }

    'start' {
        $javaExe = Join-Path $jdkHome 'bin\java.exe'
        if (-not (Test-Path $javaExe)) { throw "Project-local JDK not found: $javaExe" }
        if (-not (Test-Port 8848)) {
            Write-Host 'Nacos is not listening on 8848 -- start it first: .\deploy\start-nacos.ps1' -ForegroundColor Red
            exit 1
        }

        New-Item -ItemType Directory -Path $logDir -Force | Out-Null

        # Audio lives outside the repository now (131 MB of music files are
        # .gitignore'd). Point say-service at the on-disk directory so it can
        # still stream audio; see deploy/music/ and deploy/README.md.
        $musicDir = Join-Path $projectRoot 'deploy\music'
        if (Test-Path $musicDir) {
            if (-not $env:AUDIO_MUSIC_PATH) {
                $env:AUDIO_MUSIC_PATH = 'file:' + $musicDir.Replace('\', '/') + '/'
                Write-Host "Audio dir  : $env:AUDIO_MUSIC_PATH" -ForegroundColor DarkGray
            } else {
                Write-Host "Audio dir  : $env:AUDIO_MUSIC_PATH (from environment)" -ForegroundColor DarkGray
            }
        } else {
            Write-Host "Audio dir  : $musicDir not found -- audio endpoints will 404" -ForegroundColor Yellow
            Write-Host "             see server\cipherws-say-service\src\main\resources\music\README.md" -ForegroundColor Yellow
        }
        Write-Host ''

        # Order matters only for readability; registration retries, so any order works.
        foreach ($s in ($services | Sort-Object Port)) {
            if (Test-Port $s.Port) { Write-Host ("{0} already listening on {1}" -f $s.Name, $s.Port) -ForegroundColor Yellow; continue }
            $jar = Join-Path $serverDir $s.Jar
            if (-not (Test-Path $jar)) { throw "Missing jar: $jar (run mvnw.cmd clean package first)" }
            $out = Join-Path $logDir ($s.Name + '.out.log')
            $err = Join-Path $logDir ($s.Name + '.err.log')
            Start-Process -FilePath $javaExe -ArgumentList '-jar', $jar -WorkingDirectory $serverDir `
                -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Hidden
            Write-Host ("Started {0} (port {1})" -f $s.Name, $s.Port) -ForegroundColor Cyan
        }

        Write-Host ''
        Write-Host 'Waiting for all three ports ...' -ForegroundColor Cyan
        $deadline = (Get-Date).AddSeconds(180)
        do {
            Start-Sleep -Seconds 3
            $up = @($services | Where-Object { Test-Port $_.Port }).Count
        } while ($up -lt $services.Count -and (Get-Date) -lt $deadline)

        foreach ($s in $services) {
            Write-Host ("  {0,-24} {1}  {2}" -f $s.Name, $s.Port, $(if (Test-Port $s.Port) { 'UP' } else { 'down' }))
        }
        Write-Host ''
        Write-Host "Logs: $logDir"
        Write-Host 'Stop with: powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\run-services.ps1 -Action stop' -ForegroundColor Green
    }
}
