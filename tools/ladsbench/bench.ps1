# LADS_VERIFY_PERF_BENCH1912 (marker: the Legion runner runs jobs containing LADS_VERIFY_PERF_ alone)
# 1.9.12 perf lane FPS bench on ARASH-LEGION. Runs C:\LadsQA\perf1912\sbx\<config> sandboxes one game at a time, configs interleaved per
# round (drift/thermals hit all equally). Each run: fresh copy of the bench world, forced bench options, the ladsbench mod drives the
# scenes and writes result.json; the game exits by itself (killed after 15 min).
#   bench.ps1 -Configs lads263,oc263 [-Rounds 3] [-Seconds 20] [-Tag base] [-Scenes settle,spawn,hudoff,entities,fly] [-Jfr] [-Prep]
#             [-JvmExtra '<flags>'] [-Shots] (one screenshot per scene before measuring: evidence runs only)
# -Prep: creates templates\<mc>\ladsbench from the first config (fixed seed, flight path pre-generated) instead of benchmarking.
# Results: out\<tag>\<config>-r<i>.json/.log/.out (+ .jfr with -Jfr).
param([string] $Configs, [int] $Rounds = 3, [int] $Seconds = 20, [string] $Tag = 'run', [string] $Scenes = 'settle,spawn,hudoff,entities,fly',
      [switch] $Jfr, [switch] $Prep, [switch] $Shots, [string] $JvmExtra = '')
$ErrorActionPreference = 'Stop'
$R = $PSScriptRoot
$O = "$R\out\$Tag"; New-Item -ItemType Directory -Force $O | Out-Null
function Log($s) { $l = "$((Get-Date).ToString('HH:mm:ss')) $s"; Add-Content "$O\bench.log" $l -Encoding utf8; $l }
$force = [ordered]@{ renderDistance = '12'; simulationDistance = '8'; maxFps = '260'; enableVsync = 'false'; fullscreen = 'false'; guiScale = '2'
    pauseOnLostFocus = 'false'; soundCategory_master = '0.1'; soundCategory_music = '0.0'; inactivityFpsLimit = 'minimized'
    onboardAccessibility = 'false'; tutorialStep = 'none'; skipMultiplayerWarning = 'true'; joinedFirstServer = 'true' }
$list = $Configs -split ','
# Shared bench lock (coordinator 2026-10-18): wait while another lane holds C:\LadsQA\BENCH-LOCK, hold it ("perf1912 until HH:MM") for the
# whole job, delete it at the end. Nobody starts a game or a build on this machine while it exists.
$lock = Join-Path (Split-Path $R) 'BENCH-LOCK'
while ((Test-Path $lock) -and -not ((Get-Content $lock -Raw) -like 'perf1912 *')) { Log "waiting for bench lock: $(Get-Content $lock -Raw)"; Start-Sleep 30 }
$runs = $list.Count * $(if ($Prep) { 1 } else { $Rounds })
Set-Content $lock "perf1912 until $((Get-Date).AddMinutes(5 * $runs + 5).ToString('HH:mm'))"
Log "start $env:COMPUTERNAME configs=$Configs rounds=$Rounds seconds=$Seconds prep=$Prep jfr=$Jfr extra='$JvmExtra' lock='$(Get-Content $lock -Raw)'"
try {
for ($i = 1; $i -le ($(if ($Prep) { 1 } else { $Rounds })); $i++) { foreach ($c in $list) {
    $S = "$R\sbx\$c"; $G = "$S\game"
    $major, $mc = Get-Content "$S\java.txt"
    $label = "$c-r$i"
    # RAM gate: other lanes may run games here (max 2 per machine); wait for 6 GB free
    while (((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB) -lt 6) { Log "waiting for free RAM"; Start-Sleep 30 }
    $opts = @()
    if (Test-Path "$G\options.txt") { $opts = @(Get-Content "$G\options.txt") }
    $extra = @(Get-Content "$S\options.force" -ErrorAction SilentlyContinue | Where-Object { $_ })
    $keys = @($force.Keys) + @($extra | ForEach-Object { ($_ -split ':', 2)[0] })
    $opts = @($opts | Where-Object { $keys -notcontains ($_ -split ':', 2)[0] }) + @($force.GetEnumerator() | ForEach-Object { "$($_.Key):$($_.Value)" }) + $extra
    Set-Content "$G\options.txt" $opts
    New-Item -ItemType Directory -Force "$G\ladsbench", "$S\shared-mc", "$S\no-lunar" | Out-Null
    Remove-Item "$G\ladsbench\result.json" -ErrorAction SilentlyContinue
    Set-Content "$G\ladsbench\bench.properties" "mode=$(if ($Prep) { 'prep' } else { 'run' })`nlabel=$label`nseconds=$Seconds`nscenes=$Scenes`nworld=ladsbench`nseed=1912`nshots=$(if ($Shots) { 'true' } else { 'false' })"
    if (Test-Path "$G\saves\ladsbench") { Remove-Item "$G\saves\ladsbench" -Recurse -Force }
    if (-not $Prep) { Copy-Item "$R\templates\$mc\ladsbench" "$G\saves\ladsbench" -Recurse }
    if (Test-Path "$G\shared\thelads_config.json") { Remove-Item "$G\shared\thelads_config.json" }   # as the harness: re-seed from the game copy
    $env:THELADS_DIR = $G; $env:THELADS_GLOBAL_DIR = $G; $env:LADS_GLOBAL_MINECRAFT_DIR = "$S\shared-mc"; $env:LADS_LUNAR_DIR = "$S\no-lunar"
    Remove-Item Env:_JAVA_OPTIONS, Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    $pre = @()
    if ($JvmExtra) { $pre += $JvmExtra -split ' ' }
    if ($Jfr) { $pre += "-XX:StartFlightRecording=filename=$O\$label.jfr,settings=profile,dumponexit=true" }
    $t0 = Get-Date
    $p = Start-Process "$R\jdk$major\bin\java.exe" -ArgumentList ($pre + "@`"$S\java.args`"") -WorkingDirectory $G -PassThru `
        -RedirectStandardOutput "$O\$label.out" -RedirectStandardError "$O\$label.err"
    if (-not $p.WaitForExit(900000)) { Stop-Process -Id $p.Id -Force; $p.WaitForExit(); Log "$label TIMEOUT killed" }
    $secs = [int]((Get-Date) - $t0).TotalSeconds
    Copy-Item "$G\logs\latest.log" "$O\$label.log" -ErrorAction SilentlyContinue
    if (Test-Path "$G\ladsbench\result.json") { Copy-Item "$G\ladsbench\result.json" "$O\$label.json"; Log "$label exit $($p.ExitCode) ${secs}s $(Get-Content "$G\ladsbench\result.json" -Raw)" }
    else { Log "$label exit $($p.ExitCode) ${secs}s NO RESULT" }
    if ($Prep) {
        $T = "$R\templates\$mc"; New-Item -ItemType Directory -Force $T | Out-Null
        if (Test-Path "$T\ladsbench") { Remove-Item "$T\ladsbench" -Recurse -Force }
        Copy-Item "$G\saves\ladsbench" "$T\ladsbench" -Recurse; Remove-Item "$T\ladsbench\session.lock" -ErrorAction SilentlyContinue
        Log "template $mc saved ($([int]((Get-ChildItem "$T\ladsbench" -Recurse | Measure-Object Length -Sum).Sum / 1MB)) MB)"
    }
} }
} finally { if ((Get-Content $lock -Raw -ErrorAction SilentlyContinue) -like 'perf1912 *') { Remove-Item $lock } }
Log "end"
