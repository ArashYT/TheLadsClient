# LADS_VERIFY_PERF_BENCH1912 (marker: the Legion runner runs jobs containing LADS_VERIFY_PERF_ alone)
# 1.9.12 perf lane FPS bench on ARASH-LEGION. Runs C:\LadsQA\perf1912\sbx\<config> sandboxes one game at a time, configs interleaved per
# round (drift/thermals hit all equally). Each run: fresh copy of the bench world, forced bench options, the ladsbench mod drives the
# scenes and writes result.json; the game exits by itself (killed after 15 min).
#   bench.ps1 -Configs lads263,oc263 [-Rounds 3] [-Seconds 20] [-Tag base] [-Scenes settle,spawn,hudoff,entities,fly] [-Jfr] [-Prep]
#             [-JvmExtra '<flags>'] [-Shots] (one screenshot per scene before measuring: evidence runs only)
# -Prep: creates templates\<sandbox>\ladsbench for each config (fixed seed, flight path pre-generated) instead of benchmarking; a run
# uses its sandbox's own template (a world saved by another mod set can stop at a warning screen), else templates\<mc>.
# A config may name a variant, <sandbox>~<variant>: variants\<variant>.txt lines (applied for that run only, undone after it):
#   java=<major>   jvm=<extra JVM flags>   remove=<mods file glob>   add=<file under mods\ of this folder>   core=<theladscore jar under mods\>
#   module=<Lads module>:<true|false>   option=<options.txt line>   nojvm=<java.args line to drop>   config=<file under variants\files\>><game-relative path> (replaced for the run)
# Results: out\<tag>\<config>-r<i>.json/.log/.out (+ .jfr with -Jfr).
param([string] $Configs, [int] $Rounds = 3, [int] $Seconds = 20, [string] $Tag = 'run', [string] $Scenes = 'settle,spawn,hudoff,entities,fly',
      [switch] $Jfr, [switch] $Prep, [switch] $Shots, [string] $JvmExtra = '')
$ErrorActionPreference = 'Stop'
$R = $PSScriptRoot
$O = "$R\out\$Tag"; New-Item -ItemType Directory -Force $O | Out-Null
function Log($s) { $l = "$((Get-Date).ToString('HH:mm:ss')) $s"; Add-Content "$O\bench.log" $l -Encoding utf8; $l }
trap { Log "ERROR line $($_.InvocationInfo.ScriptLineNumber): $_"; break }   # the runner loses a terminating error otherwise
$force = [ordered]@{ renderDistance = '12'; simulationDistance = '8'; maxFps = '260'; enableVsync = 'false'; fullscreen = 'false'; guiScale = '2'
    pauseOnLostFocus = 'false'; soundCategory_master = '0.1'; soundCategory_music = '0.0'; inactivityFpsLimit = 'minimized'
    onboardAccessibility = 'false'; tutorialStep = 'none'; skipMultiplayerWarning = 'true'; joinedFirstServer = 'true' }
$list = @($Configs -split '[,\s]+' | Where-Object { $_ })   # ',' joined by the caller, or an array that PowerShell bound as 'a b c'
# Shared bench lock (coordinator 2026-10-18): wait while another lane holds C:\LadsQA\BENCH-LOCK, hold it ("perf1912 until HH:MM") for the
# whole job, delete it at the end. Nobody starts a game or a build on this machine while it exists.
$lock = Join-Path (Split-Path $R) 'BENCH-LOCK'
while ((Test-Path $lock) -and -not ((Get-Content $lock -Raw) -like 'perf1912 *')) { Log "waiting for bench lock: $(Get-Content $lock -Raw)"; Start-Sleep 30 }
$runs = $list.Count * $(if ($Prep) { 1 } else { $Rounds })
Set-Content $lock "perf1912 until $((Get-Date).AddMinutes(5 * $runs + 5).ToString('HH:mm'))"
Log "start $env:COMPUTERNAME configs=$Configs rounds=$Rounds seconds=$Seconds prep=$Prep jfr=$Jfr extra='$JvmExtra' lock='$(Get-Content $lock -Raw)'"
try {
for ($i = 1; $i -le ($(if ($Prep) { 1 } else { $Rounds })); $i++) { foreach ($c in $list) {
    $label = "$($c -replace '~', '@')-r$i"
    $vjvm = @(); $vopt = @(); $undo = @(); $drop = @(); $vmod = @()
    try {
    $sb, $variant = $c -split '~', 2
    $S = "$R\sbx\$sb"; $G = "$S\game"; $argsFile = "$S\java.args"
    $major, $mc = Get-Content "$S\java.txt"
    if ($variant) {
        foreach ($line in Get-Content "$R\variants\$variant.txt" | Where-Object { $_ -match '^\w+=' }) {
            $k, $v = $line -split '=', 2
            switch ($k) {
                'java' { $major = $v }
                'jvm' { $vjvm += $v -split ' ' }
                'option' { $vopt += $v }
                'nojvm' { $drop += $v }
                'module' { $vmod += $v }
                'config' { $src, $dst = $v -split '>', 2; $dst = "$G\$dst"
                    if (Test-Path $dst) { Copy-Item $dst "$dst.vbak" -Force; $undo += ,@('restore', $dst) } else { $undo += ,@('delpath', $dst) }
                    New-Item -ItemType Directory -Force (Split-Path $dst) | Out-Null; Copy-Item "$R\variants\files\$src" $dst -Force }
                'remove' { New-Item -ItemType Directory -Force "$S\off-v" | Out-Null
                    foreach ($f in Get-ChildItem "$G\mods" -Filter $v) { Move-Item $f.FullName "$S\off-v\"; $undo += ,@('back', $f.Name) } }
                'add' { Copy-Item "$R\mods\$v" "$G\mods\"; $undo += ,@('del', (Split-Path $v -Leaf)) }
                'core' { New-Item -ItemType Directory -Force "$S\off-v" | Out-Null; Move-Item "$G\mods\theladscore.jar" "$S\off-v\"
                    Copy-Item "$R\mods\$v" "$G\mods\theladscore.jar"; $undo += ,@('core', '') }
            }
        }
    }
    if ($drop) { $argsFile = "$S\java-variant.args"; Get-Content "$S\java.args" | Where-Object { $drop -notcontains $_ } | Set-Content $argsFile }
    # RAM gate: other lanes may run games here (max 2 per machine); wait for 6 GB free
    while (((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB) -lt 6) { Log "waiting for free RAM"; Start-Sleep 30 }
    $opts = @()
    if (Test-Path "$G\options.txt") { $opts = @(Get-Content "$G\options.txt") }
    $extra = @(Get-Content "$S\options.force" -ErrorAction SilentlyContinue | Where-Object { $_ }) + $vopt
    $keys = @($force.Keys) + @($extra | ForEach-Object { ($_ -split ':', 2)[0] })
    $opts = @($opts | Where-Object { $keys -notcontains ($_ -split ':', 2)[0] }) + @($force.GetEnumerator() | ForEach-Object { "$($_.Key):$($_.Value)" }) + $extra
    Set-Content "$G\options.txt" $opts
    New-Item -ItemType Directory -Force "$G\ladsbench", "$S\shared-mc", "$S\no-lunar" | Out-Null
    Remove-Item "$G\ladsbench\result.json" -ErrorAction SilentlyContinue
    Set-Content "$G\ladsbench\bench.properties" "mode=$(if ($Prep) { 'prep' } else { 'run' })`nlabel=$label`nseconds=$Seconds`nscenes=$Scenes`nworld=ladsbench`nseed=1912`nshots=$(if ($Shots) { 'true' } else { 'false' })"
    if (Test-Path "$G\saves\ladsbench") { Remove-Item "$G\saves\ladsbench" -Recurse -Force }
    $tpl = if (Test-Path "$R\templates\$sb\ladsbench") { "$R\templates\$sb\ladsbench" } else { "$R\templates\$mc\ladsbench" }
    if (-not $Prep) { New-Item -ItemType Directory -Force "$G\saves" | Out-Null; Copy-Item $tpl "$G\saves\ladsbench" -Recurse }
    if (Test-Path "$G\shared\thelads_config.json") { Remove-Item "$G\shared\thelads_config.json" }   # as the harness: re-seed from the game copy
    # QA rule (QaDiscord.ForceOff): Lads Dynamic FPS (unfocused cap 60) and Discord RPC off. Core rewrites a partial config with its
    # defaults on first start, so this is applied to its full config before every run.
    if (Test-Path "$G\thelads_config.json") {
        if ($vmod) { Copy-Item "$G\thelads_config.json" "$G\thelads_config.json.vbak" -Force; $undo += ,@('restore', "$G\thelads_config.json") }
        $cfg = Get-Content "$G\thelads_config.json" -Raw | ConvertFrom-Json
        foreach ($m in 'DynamicFPS', 'DiscordRPC') { if ($cfg.modules.$m) { $cfg.modules.$m.enabled = $false } }
        foreach ($m in $vmod) { $n, $b = $m -split ':', 2; if ($cfg.modules.$n) { $cfg.modules.$n.enabled = [bool]::Parse($b) } else { Log "WARN no module '$n'" } }
        $cfg | ConvertTo-Json -Depth 64 | Set-Content "$G\thelads_config.json" -Encoding utf8
    }
    $env:THELADS_DIR = $G; $env:THELADS_GLOBAL_DIR = $G; $env:LADS_GLOBAL_MINECRAFT_DIR = "$S\shared-mc"; $env:LADS_LUNAR_DIR = "$S\no-lunar"
    Remove-Item Env:_JAVA_OPTIONS, Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    $pre = @($vjvm)
    if ($JvmExtra) { $pre += $JvmExtra -split ' ' }
    if ($Jfr) { $pre += "-XX:StartFlightRecording=filename=$O\$label.jfr,settings=profile,dumponexit=true" }
    $t0 = Get-Date
    $p = Start-Process "$R\jdk$major\bin\java.exe" -ArgumentList ($pre + "@`"$argsFile`"") -WorkingDirectory $G -PassThru `
        -RedirectStandardOutput "$O\$label.out" -RedirectStandardError "$O\$label.err"
    if (-not $p.WaitForExit(900000)) { Stop-Process -Id $p.Id -Force; $p.WaitForExit(); Log "$label TIMEOUT killed" }
    $secs = [int]((Get-Date) - $t0).TotalSeconds
    Copy-Item "$G\logs\latest.log" "$O\$label.log" -ErrorAction SilentlyContinue
    if (Test-Path "$G\ladsbench\result.json") { Copy-Item "$G\ladsbench\result.json" "$O\$label.json"; Log "$label exit $($p.ExitCode) ${secs}s $(Get-Content "$G\ladsbench\result.json" -Raw)" }
    else { Log "$label exit $($p.ExitCode) ${secs}s NO RESULT" }
    if ($Prep) {
        $T = "$R\templates\$sb"; New-Item -ItemType Directory -Force $T | Out-Null
        if (Test-Path "$T\ladsbench") { Remove-Item "$T\ladsbench" -Recurse -Force }
        Copy-Item "$G\saves\ladsbench" "$T\ladsbench" -Recurse; Remove-Item "$T\ladsbench\session.lock" -ErrorAction SilentlyContinue
        Log "template $sb saved ($([int]((Get-ChildItem "$T\ladsbench" -Recurse | Measure-Object Length -Sum).Sum / 1MB)) MB)"
    }
    } catch { Log "ERROR $label line $($_.InvocationInfo.ScriptLineNumber): $_" } finally {
        foreach ($u in $undo) {
            switch ($u[0]) {
                'back' { Move-Item "$S\off-v\$($u[1])" "$G\mods\" -Force }
                'del' { Remove-Item "$G\mods\$($u[1])" }
                'core' { Move-Item "$S\off-v\theladscore.jar" "$G\mods\theladscore.jar" -Force }
                'restore' { Move-Item "$($u[1]).vbak" $u[1] -Force }
                'delpath' { Remove-Item $u[1] -ErrorAction SilentlyContinue }
            }
        }
    }
} }
} finally { if ((Get-Content $lock -Raw -ErrorAction SilentlyContinue) -like 'perf1912 *') { Remove-Item $lock } }
Log "end"
