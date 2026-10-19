# Start / stop the CipherWS backend services with the project-local JDK.
#
# Two groups, both registered to the same Nacos:
#   main site : gateway(8080) -> say(8081) / portal(8082)
#   note site : gateway(8083) -> user(8084) / core(8085) / ai(8086) / admin(8087)
#
# Ports are 8000 + index for frontends (main 8000 / train 8001 / note 8002) and
# the backend keeps clear of them: 8080-8082 belong to the main site,
# 8083-8087 to the note site, so the two never collide.
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

# Order = start order. Within a group the business services come first and the
# gateway last, so the gateway has upstreams to route to as soon as it is up.
$services = @(
    @{ Name = 'cipherws-say-service';    Port = 8081; Order = 1; Group = 'main'; Jar = 'cipherws-say-service\target\cipherws-say-service-0.0.1-SNAPSHOT.jar' },
    @{ Name = 'cipherws-portal-service'; Port = 8082; Order = 2; Group = 'main'; Jar = 'cipherws-portal-service\target\cipherws-portal-service-0.0.1-SNAPSHOT.jar' },
    @{ Name = 'cipherws-gateway';        Port = 8080; Order = 3; Group = 'main'; Jar = 'cipherws-gateway\target\cipherws-gateway-0.0.1-SNAPSHOT.jar' },

    @{ Name = 'note-user-service';       Port = 8084; Order = 4; Group = 'note'; Jar = 'note-user-service\target\note-user-service-0.0.1-SNAPSHOT.jar' },
    @{ Name = 'note-core-service';       Port = 8085; Order = 5; Group = 'note'; Jar = 'note-core-service\target\note-core-service-0.0.1-SNAPSHOT.jar' },
    @{ Name = 'note-ai-service';         Port = 8086; Order = 6; Group = 'note'; Jar = 'note-ai-service\target\note-ai-service-0.0.1-SNAPSHOT.jar' },
    @{ Name = 'note-admin-service';      Port = 8087; Order = 7; Group = 'note'; Jar = 'note-admin-service\target\note-admin-service-0.0.1-SNAPSHOT.jar' },
    @{ Name = 'note-gateway';            Port = 8083; Order = 8; Group = 'note'; Jar = 'note-gateway\target\note-gateway-0.0.1-SNAPSHOT.jar' }
)

function Get-ServiceProcs {
    # Match by jar name rather than by a broad "cipherws" substring: the note
    # services live under server\note-*, and a loose match could also hit an
    # unrelated java process that merely mentions one of these words.
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object {
            $cl = $_.CommandLine
            if (-not $cl) { return $false }
            foreach ($s in $services) {
                if ($cl -like ('*' + $s.Name + '-0.0.1-SNAPSHOT.jar*')) { return $true }
            }
            return $false
        }
}

function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

# Read KEY=VALUE lines into this process's environment.
#
# The note services need DB_PASSWORD / NOTE_JWT_SECRET / mail credentials.
# Those must never be committed, so they live in deploy\note.env.local
# (ignored by the "*.local" rule in .gitignore) and are loaded here.
# Children started with Start-Process inherit this process's environment.
function Import-EnvFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line) { return }
        if ($line.StartsWith('#')) { return }
        $i = $line.IndexOf('=')
        if ($i -lt 1) { return }
        $key = $line.Substring(0, $i).Trim()
        $val = $line.Substring($i + 1).Trim()
        if ($key) { Set-Item -Path ('env:' + $key) -Value $val }
    }
    return $true
}

function Show-Ports {
    Write-Host 'Ports:'
    foreach ($s in ($services | Sort-Object { $_.Order })) {
        $state = if (Test-Port $s.Port) { 'UP' } else { 'down' }
        Write-Host ("  {0,-24} {1,-6} {2}" -f $s.Name, $s.Port, $state)
    }
    Write-Host 'Nacos:'
    foreach ($p in 8848, 8849, 9848) {
        Write-Host ("  {0,-24} {1,-6} {2}" -f 'nacos', $p, $(if (Test-Port $p) { 'UP' } else { 'down' }))
    }
}

switch ($Action) {
    'status' {
        Show-Ports
    }

    'stop' {
        $procs = @(Get-ServiceProcs)
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
        # still stream audio; see deploy/music/ and
        # docs/2026-10-5/audio-resources.md.
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
        }

        # Note-site secrets: DB password, JWT secret, SMTP credentials.
        # note.env.local carries the note-specific ones; mail.env.local (if the
        # user already created it, see its own .example) is loaded second so it
        # can override the mail settings without duplicating them.
        $noteEnvPath = Join-Path $projectRoot 'deploy\note.env.local'
        $mailEnvPath = Join-Path $projectRoot 'deploy\mail.env.local'
        $noteEnvLoaded = Import-EnvFile $noteEnvPath
        $mailEnvLoaded = Import-EnvFile $mailEnvPath
        if ($noteEnvLoaded) {
            Write-Host "Note env   : deploy\note.env.local loaded" -ForegroundColor DarkGray
        } else {
            Write-Host "Note env   : deploy\note.env.local not found" -ForegroundColor Yellow
            Write-Host "             note-* services need DB_PASSWORD and NOTE_JWT_SECRET;" -ForegroundColor Yellow
            Write-Host "             copy deploy\note.env.example to deploy\note.env.local and fill it in." -ForegroundColor Yellow
        }
        if ($mailEnvLoaded) {
            Write-Host "Mail env   : deploy\mail.env.local loaded" -ForegroundColor DarkGray
        }
        Write-Host ''

        foreach ($s in ($services | Sort-Object { $_.Order })) {
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
        Write-Host ("Waiting for all {0} ports ..." -f $services.Count) -ForegroundColor Cyan
        $deadline = (Get-Date).AddSeconds(240)
        do {
            Start-Sleep -Seconds 3
            $up = @($services | Where-Object { Test-Port $_.Port }).Count
        } while ($up -lt $services.Count -and (Get-Date) -lt $deadline)

        Write-Host ''
        Show-Ports
        Write-Host ''
        Write-Host "Logs: $logDir"
        Write-Host 'Stop with: powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\run-services.ps1 -Action stop' -ForegroundColor Green
    }
}
