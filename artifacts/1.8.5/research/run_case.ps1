# One Zero SMP QA measurement run through the shared harness (never touches the real instance).
# usage: run_case.ps1 -Name base [-Variants a,b] [-Drop jar1,jar2] [-Add path1,path2] [-Script file] [-Rd 12] [-Sim 8] [-Fps 260] [-Jfr] [-JvmOpts "..."] [-TimeoutSec 1500] [-NoWorld] [-KeepNew]
param(
  [Parameter(Mandatory=$true)][string]$Name,
  [string[]]$Variants = @(), [string[]]$Drop = @(), [string[]]$Add = @(),
  [string]$Script = '', [int]$Rd = 12, [int]$Sim = 8, [int]$Fps = 260,
  [switch]$Jfr, [string]$JvmOpts = '', [int]$TimeoutSec = 1500, [switch]$NoWorld, [switch]$KeepNew, [switch]$SeedWorld, [switch]$Flip
)
$ErrorActionPreference = 'Stop'
$root = 'C:\Users\Arash\AppData\Local\Temp\w185research'
$res  = Join-Path $root 'artifacts\1.8.5\research'
$ver  = Join-Path $root 'artifacts\verification'
$run  = Join-Path $res "runs\$Name"
$sand = Join-Path $ver "zr-$Name"
$src  = Join-Path $ver "zsrc-$Name"
$exe  = Join-Path $root 'TheLadsLauncher.Verification\bin\Debug\net8.0-windows\TheLadsLauncher.Verification.exe'
$core = 'C:\Users\Arash\AppData\Local\Temp\w185harness\TheLadsCore\v1_20_1\build\libs\v1_20_1-1.8.5-mc1.20.1.jar'
$jfrbin = 'C:\Users\Arash\AppData\Roaming\.theladsclient\runtime\java-17\jdk-17.0.20.1+1\bin\jfr.exe'
$spark = 'C:\Users\Arash\AppData\Local\Temp\claude\C--Users-Arash-Desktop-The-Lads-Client-Dev\5b98d405-e92a-45a9-94c7-0bd9c11886de\scratchpad\dl-spark\spark-1.10.53-forge.jar'
New-Item -ItemType Directory -Force $run | Out-Null
if ((& "$res\owner_playing.ps1") -like 'OWNER_PLAYING*') { Write-Host 'owner is playing: not starting'; exit 3 }

# 1. variant source (hard-linked mods, copied config)
$Drop = @($Drop | ForEach-Object { $_ -split ',' }); $Variants = @($Variants | ForEach-Object { $_ -split ',' })
$py = @('make_source.py', $src); foreach ($v in $Variants) { $py += @('--variant', $v) }; foreach ($d in $Drop) { $py += @('--drop', $d) }
foreach ($a in $Add + @($spark)) { $py += @('--add', $a) }; if ($KeepNew) { $py += '--keep-new' }
Push-Location $res; python @py; Pop-Location

# 2. the QA world: a fresh copy of the template (owner's New World, region files only) for every run
$gameDir = Join-Path $sand 'instances\zero-smp-1.20.1\minecraft'
if ($SeedWorld) {
    $w = Join-Path $gameDir 'saves\LadsQA-zero-smp'
    if (Test-Path $w) { Remove-Item $w -Recurse -Force }
    New-Item -ItemType Directory -Force (Split-Path $w) | Out-Null
    Copy-Item (Join-Path $ver 'worlds\NewWorld') $w -Recurse
}
# 3. environment for the game JVM (inherited through the harness): perf options, JFR, JVM variants
$env:LADS_VERIFY_PERF_RD = "$Rd"; $env:LADS_VERIFY_PERF_SIM = "$Sim"; $env:LADS_VERIFY_PERF_FPS = "$Fps"
$jfrfile = Join-Path $run 'game-%p.jfr'
if ($Jfr) { $env:JAVA_TOOL_OPTIONS = "-XX:StartFlightRecording=filename=$jfrfile,settings=profile,dumponexit=true,maxsize=1500M" } else { Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue }
if ($JvmOpts) { $env:_JAVA_OPTIONS = $JvmOpts } else { Remove-Item Env:_JAVA_OPTIONS -ErrorAction SilentlyContinue }
# 4. load monitor
$stop = Join-Path $run 'loadmon.stop'; Remove-Item $stop -ErrorAction SilentlyContinue
$mon = Start-Process powershell -ArgumentList '-NoProfile','-File',"$res\loadmon.ps1",(Join-Path $run 'load.csv'),$stop -PassThru -WindowStyle Hidden
$flipProc = $null
if ($Flip) { $flipProc = Start-Process python -ArgumentList (Join-Path $res 'flipper.py'),$gameDir,$stop -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $run 'flipper.log') }
# 5. the harness
$args2 = @('--zero-smp', $sand, '--source', $src, '--core-jar', $core, '--scenario', "research-$Name", '--timeout-sec', "$TimeoutSec", '--no-build')
if ($Script) { $args2 += @('--script', $Script) }
if ($NoWorld) { $args2 += '--no-world' }
$t0 = Get-Date
"started $t0 ; variants=$($Variants -join ',') drop=$($Drop -join ',') rd=$Rd sim=$Sim fps=$Fps jfr=$Jfr jvm=$JvmOpts" | Out-File (Join-Path $run 'case.txt') -Encoding utf8
Push-Location $root
& $exe @args2 *>&1 | Tee-Object (Join-Path $run 'harness.log') | Select-Object -Last 3
$code = $LASTEXITCODE
Pop-Location
New-Item $stop -ItemType File | Out-Null; Start-Sleep -Seconds 3
Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue; Remove-Item Env:_JAVA_OPTIONS -ErrorAction SilentlyContinue
Remove-Item Env:LADS_VERIFY_PERF_RD, Env:LADS_VERIFY_PERF_SIM, Env:LADS_VERIFY_PERF_FPS -ErrorAction SilentlyContinue
"harness exit $code ; finished $(Get-Date) ; elapsed $([int]((Get-Date)-$t0).TotalSeconds) s" | Out-File (Join-Path $run 'case.txt') -Append -Encoding utf8
# 6. keep the game log and the evidence folder pointer
$latest = Join-Path $gameDir 'logs\latest.log'; if (Test-Path $latest) { Copy-Item $latest (Join-Path $run 'latest.log') -Force }
Write-Host "run $Name exit=$code -> $run"
exit $code
