<#
.SYNOPSIS
    Starts the LadsClientAPI QA test network servers on ARASH-LEGION.

.DESCRIPTION
    Starts the configured servers minimised at BelowNormal priority,
    waits for each server to signal "Done" (or "Listening on" for Velocity),
    and reports process IDs and log file locations.

.PARAMETER Root
    Test network directory. Defaults to F:\LadsQA\ladsapi-net.

.PARAMETER Servers
    Array of server identifiers to start: 'velocity', 'lobby', 'plain', 'fabric', 'forge'.
    Defaults to all 5 servers.

.PARAMETER TimeoutSeconds
    Maximum seconds to wait for each server to reach ready state. Defaults to 120s.
#>
[CmdletBinding()]
param(
    [string]$Root = 'F:\LadsQA\ladsapi-net',
    [string[]]$Servers = @('velocity', 'lobby', 'plain', 'fabric', 'forge'),
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " [LadsQA] Starting Network Servers" -ForegroundColor Cyan
Write-Host " Root: $Root" -ForegroundColor Cyan
Write-Host " Targets: $($Servers -join ', ')" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Locate Java runtimes
$LEGION_CACHE = 'C:\LadsQA\shared\.qa-cache'
function Resolve-Java {
    param([int]$TargetMajor)
    $candidates = @()
    if ($TargetMajor -ge 21) {
        $candidates += "$LEGION_CACHE\runtime-data\runtime\java-25\*\bin\java.exe"
        $candidates += "\\192.168.2.22\C\LadsQA\shared\.qa-cache\runtime-data\runtime\java-25\*\bin\java.exe"
        if ($env:JAVA_HOME_25) { $candidates += "$env:JAVA_HOME_25\bin\java.exe" }
        if ($env:JAVA_HOME_21) { $candidates += "$env:JAVA_HOME_21\bin\java.exe" }
    } else {
        $candidates += "$LEGION_CACHE\runtime-data\runtime\java-17\*\bin\java.exe"
        $candidates += "\\192.168.2.22\C\LadsQA\shared\.qa-cache\runtime-data\runtime\java-17\*\bin\java.exe"
        if ($env:JAVA_HOME_17) { $candidates += "$env:JAVA_HOME_17\bin\java.exe" }
    }
    if ($env:JAVA_HOME) { $candidates += "$env:JAVA_HOME\bin\java.exe" }
    $candidates += (Get-Command java.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)

    foreach ($pattern in $candidates) {
        if (-not $pattern) { continue }
        $resolved = Resolve-Path $pattern -ErrorAction SilentlyContinue
        if ($resolved) {
            $path = $resolved[0].Path
            if (Test-Path $path) { return $path }
        }
    }
    return "java"
}

$java25 = Resolve-Java 25
$java17 = Resolve-Java 17

$serverSpecs = @{
    'velocity' = @{
        Port       = 25700
        Dir        = "$Root\velocity"
        Exe        = $java17
        Args       = @('-Xms512M', '-Xmx1G', '-jar', 'velocity.jar')
        LogPath    = "$Root\velocity\logs\velocity.log"
        AltLog     = "$Root\velocity\logs\latest.log"
        ReadyMatch = '(Listening on|Connections are now accepted)'
    }
    'lobby' = @{
        Port       = 25701
        Dir        = "$Root\lobby"
        Exe        = $java25
        Args       = @('-Xms1G', '-Xmx2G', '-jar', 'paper-26.3.jar', '--nogui')
        LogPath    = "$Root\lobby\logs\latest.log"
        AltLog     = "$Root\lobby\logs\latest.log"
        ReadyMatch = 'Done \([0-9.]+s\)! For help'
    }
    'plain' = @{
        Port       = 25702
        Dir        = "$Root\plain"
        Exe        = $java25
        Args       = @('-Xms1G', '-Xmx2G', '-jar', 'paper-26.3.jar', '--nogui')
        LogPath    = "$Root\plain\logs\latest.log"
        AltLog     = "$Root\plain\logs\latest.log"
        ReadyMatch = 'Done \([0-9.]+s\)! For help'
    }
    'fabric' = @{
        Port       = 25703
        Dir        = "$Root\fabric"
        Exe        = $java25
        Args       = @('-Xms1G', '-Xmx2G', '-jar', 'fabric-server-launch.jar', 'nogui')
        LogPath    = "$Root\fabric\logs\latest.log"
        AltLog     = "$Root\fabric\logs\latest.log"
        ReadyMatch = 'Done \([0-9.]+s\)! For help'
    }
    'forge' = @{
        Port       = 25704
        Dir        = "$Root\forge"
        Exe        = $java17
        Args       = @('-Xms1G', '-Xmx2G', '@user_jvm_args.txt', '@libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt', 'nogui')
        LogPath    = "$Root\forge\logs\latest.log"
        AltLog     = "$Root\forge\logs\latest.log"
        ReadyMatch = 'Done \([0-9.]+s\)! For help'
    }
}

$results = [System.Collections.Generic.List[PSObject]]::new()

foreach ($key in $Servers) {
    if (-not $serverSpecs.ContainsKey($key)) {
        Write-Warning "Unknown server key '$key', skipping."
        continue
    }
    $spec = $serverSpecs[$key]
    $dir = $spec.Dir

    if (-not (Test-Path $dir)) {
        Write-Warning "Directory for $key does not exist: $dir. Run setup-network.ps1 first."
        continue
    }

    # Clean existing pid if any
    $pidFile = "$dir\.pid"
    if (Test-Path $pidFile) {
        $oldPid = Get-Content $pidFile -Raw
        if ($oldPid -match '^\d+$') {
            $p = Get-Process -Id ([int]$oldPid) -ErrorAction SilentlyContinue
            if ($p -and -not $p.HasExited) {
                Write-Host "  -> Server $key is already running (PID $oldPid)" -ForegroundColor Yellow
                $results.Add([PSCustomObject]@{
                    Server  = $key
                    Port    = $spec.Port
                    PID     = [int]$oldPid
                    LogPath = $spec.LogPath
                    Status  = 'AlreadyRunning'
                })
                continue
            }
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }

    # Ensure logs folder exists and archive previous log
    $logsDir = Split-Path $spec.LogPath -Parent
    if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir -Force | Out-Null }
    if (Test-Path $spec.LogPath) {
        $backup = "$($spec.LogPath).old"
        Move-Item $spec.LogPath $backup -Force -ErrorAction SilentlyContinue
    }

    Write-Host "Starting $key (port $($spec.Port)) at BelowNormal priority..." -ForegroundColor Yellow

    # Launch process minimized
    $proc = Start-Process -FilePath $spec.Exe `
                          -ArgumentList $spec.Args `
                          -WorkingDirectory $dir `
                          -WindowStyle Minimized `
                          -PassThru

    try {
        $proc.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::BelowNormal
    } catch {
        # Process might take a moment to be addressable
    }

    Set-Content -Path $pidFile -Value "$($proc.Id)" -Encoding ASCII -Force
    Write-Host "  -> Process launched: PID $($proc.Id)" -ForegroundColor DarkGray

    # Wait for readiness
    Write-Host "  -> Waiting for $key readiness..." -NoNewline
    $ready = $false
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    while ($stopwatch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        if ($proc.HasExited) {
            Write-Host " CRASHED" -ForegroundColor Red
            break
        }

        # Check log file
        $activeLog = if (Test-Path $spec.LogPath) { $spec.LogPath } elseif (Test-Path $spec.AltLog) { $spec.AltLog } else { $null }
        if ($activeLog) {
            try {
                $lines = Get-Content $activeLog -Tail 20 -ErrorAction SilentlyContinue
                if ($lines -match $spec.ReadyMatch) {
                    $ready = $true
                    break
                }
            } catch {}
        }
        Start-Sleep -Milliseconds 1500
        Write-Host "." -NoNewline
    }
    $stopwatch.Stop()

    if ($ready) {
        Write-Host " READY ($([math]::Round($stopwatch.Elapsed.TotalSeconds, 1))s)" -ForegroundColor Green
        $results.Add([PSCustomObject]@{
            Server  = $key
            Port    = $spec.Port
            PID     = $proc.Id
            LogPath = $spec.LogPath
            Status  = 'Ready'
        })
    } elseif ($proc.HasExited) {
        $results.Add([PSCustomObject]@{
            Server  = $key
            Port    = $spec.Port
            PID     = $proc.Id
            LogPath = $spec.LogPath
            Status  = "Exited (code $($proc.ExitCode))"
        })
    } else {
        Write-Host " TIMEOUT" -ForegroundColor Yellow
        $results.Add([PSCustomObject]@{
            Server  = $key
            Port    = $spec.Port
            PID     = $proc.Id
            LogPath = $spec.LogPath
            Status  = 'TimeoutWaitingForReady'
        })
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " [LadsQA] Network Status Summary" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
$results | Format-Table -AutoSize
