<#
.SYNOPSIS
    Stops the LadsClientAPI QA test network servers on ARASH-LEGION.

.DESCRIPTION
    Terminates the servers started by start-network.ps1 gracefully,
    waits for clean exit, force-kills hung instances if needed, and
    verifies all test network ports (25700-25704) are released.

.PARAMETER Root
    Test network directory. Defaults to F:\LadsQA\ladsapi-net.

.PARAMETER Servers
    Array of server identifiers to stop: 'velocity', 'lobby', 'plain', 'fabric', 'forge'.
    Defaults to all 5 servers.

.PARAMETER TimeoutSeconds
    Maximum seconds to wait for graceful exit before issuing a force kill. Defaults to 20s.
#>
[CmdletBinding()]
param(
    [string]$Root = 'F:\LadsQA\ladsapi-net',
    [string[]]$Servers = @('velocity', 'lobby', 'plain', 'fabric', 'forge'),
    [int]$TimeoutSeconds = 20
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " [LadsQA] Stopping Network Servers" -ForegroundColor Cyan
Write-Host " Root: $Root" -ForegroundColor Cyan
Write-Host " Targets: $($Servers -join ', ')" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$serverDirs = @{
    'velocity' = "$Root\velocity"
    'lobby'    = "$Root\lobby"
    'plain'    = "$Root\plain"
    'fabric'   = "$Root\fabric"
    'forge'    = "$Root\forge"
}

$serverPorts = @{
    'velocity' = 25700
    'lobby'    = 25701
    'plain'    = 25702
    'fabric'   = 25703
    'forge'    = 25704
}

$results = [System.Collections.Generic.List[PSObject]]::new()

foreach ($key in $Servers) {
    if (-not $serverDirs.ContainsKey($key)) { continue }
    $dir = $serverDirs[$key]
    $pidFile = "$dir\.pid"
    $port = $serverPorts[$key]

    $proc = $null
    if (Test-Path $pidFile) {
        $storedPid = Get-Content $pidFile -Raw
        if ($storedPid -match '^\d+$') {
            $proc = Get-Process -Id ([int]$storedPid) -ErrorAction SilentlyContinue
        }
    }

    if (-not $proc -or $proc.HasExited) {
        # Check by port listener in case PID was lost
        $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($conn -and $conn.OwningProcess) {
            $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
        }
    }

    if (-not $proc -or $proc.HasExited) {
        if (Test-Path $pidFile) { Remove-Item $pidFile -Force -ErrorAction SilentlyContinue }
        $results.Add([PSCustomObject]@{
            Server = $key
            Port   = $port
            PID    = $null
            Status = 'NotRunning'
        })
        continue
    }

    $targetPid = $proc.Id
    Write-Host "Stopping $key (PID $targetPid, port $port)..." -NoNewline

    try {
        # Attempt graceful window close first
        $proc.CloseMainWindow() | Out-Null
    } catch {}

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $stopped = $false
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        if ($proc.HasExited) {
            $stopped = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    $sw.Stop()

    if (-not $stopped) {
        Write-Host " FORCING" -ForegroundColor Yellow
        try {
            Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue
            $proc.WaitForExit(3000)
            $stopped = $true
        } catch {}
    } else {
        Write-Host " STOPPED" -ForegroundColor Green
    }

    if (Test-Path $pidFile) { Remove-Item $pidFile -Force -ErrorAction SilentlyContinue }

    $results.Add([PSCustomObject]@{
        Server = $key
        Port   = $port
        PID    = $targetPid
        Status = if ($stopped) { 'Stopped' } else { 'FailedToStop' }
    })
}

# Verify ports are clear
Write-Host "`nVerifying port status:" -ForegroundColor Cyan
foreach ($p in 25700..25704) {
    $conn = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        Write-Warning "Port $p is still held by PID $($conn.OwningProcess)!"
    } else {
        Write-Host "  Port ${p}: FREE" -ForegroundColor Green
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " [LadsQA] Stop Summary" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
$results | Format-Table -AutoSize
