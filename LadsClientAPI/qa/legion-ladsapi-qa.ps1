# LadsClientAPI full QA on ARASH-LEGION (queued through C:\LadsQA\inbox; runs unattended).
# Network (setup-network.ps1) -> debug configs -> start -> 26.3, 1.20.1, 1.8.9 clients join the proxy :25700 and run
# qa\scripts\ladsapi-<ver>.txt (CHK-01..10) -> evidence + server logs to F:\LadsQA\work\ladsapi-results -> stop.
$ErrorActionPreference = 'Continue'
$qa = 'F:\LadsQA\work\ladsapi-qa'; $net = 'F:\LadsQA\work\ladsapi-net'; $wt = 'F:\LadsQA\work\wladsapi'
$res = 'F:\LadsQA\work\ladsapi-results'
if (Test-Path $res) { Remove-Item $res -Recurse -Force }
New-Item -ItemType Directory -Force $res | Out-Null
function Stamp([string] $what) { "== $what $(Get-Date -Format HH:mm:ss)" }

Stamp 'setup'
& "$qa\setup-network.ps1" -Root $net -Jar "$qa\LadsClientAPI.jar"

# debug: true so every hello/ack shows in the server log; one notice for CHK-02.
$config = @"
enabled: true
server-name: "Lads QA"
unlisted-modules: inherit
notify-players: true
let-players-refuse-changes: true
require-lads-client: false
min-client-version: ""
notice: "Lads QA Lobby: Fullbright capped at 10%"
badge:
  presence: true
  placeholder: ""
debug: true
"@
foreach ($dir in "$net\lobby\plugins\LadsClientAPI", "$net\fabric\config\ladsclientapi", "$net\forge\config\ladsclientapi") {
    New-Item -ItemType Directory -Force $dir | Out-Null
    Set-Content -Path "$dir\config.yml" -Value $config -Encoding UTF8
}

Stamp 'start'
& "$qa\start-network.ps1" -Root $net
Start-Sleep -Seconds 20

$runs = @(
    @{ Ver = '26.3';   Args = @('26.3', '.', '--title') },
    @{ Ver = '1.20.1'; Args = @('--mc1201', '.', '--no-build') },
    @{ Ver = '1.8.9';  Args = @('1.8.9', '.', '--title') }
)
$exe = "$wt\TheLadsLauncher.Verification\bin\Debug\net8.0-windows\TheLadsLauncher.Verification.exe"
$summary = @()
foreach ($run in $runs) {
    $v = $run.Ver
    Stamp "client $v"
    $env:LADS_QA_SHARED_ROOT = 'C:\LadsQA\shared'
    $env:THELADS_DIR = "$wt\artifacts\verification\thelads-home"
    $env:LADS_GLOBAL_MINECRAFT_DIR = "$wt\artifacts\verification\mc-home"
    $env:LADS_VERIFY_TIMEOUT_SEC = '900'
    Set-Location $wt
    & $exe @($run.Args) --join '127.0.0.1:25700' --script "$wt\LadsClientAPI\qa\scripts\ladsapi-$v.txt" --dir "ladsapi-$v"
    $code = $LASTEXITCODE
    $summary += "$v exit=$code"
    "client $v exit=$code"
    $evidence = "$res\client-$v"
    New-Item -ItemType Directory -Force $evidence | Out-Null
    foreach ($src in "$wt\artifacts\verification\ladsapi-$v", "$wt\artifacts\verification\ladsapi") {
        if (Test-Path $src) { robocopy $src "$evidence\$(Split-Path $src -Leaf)" /E /NFL /NDL /NJH /NJS /NP | Out-Null }
    }
    Start-Sleep -Seconds 5
}

Stamp 'stop'
& "$qa\stop-network.ps1" -Root $net
foreach ($s in 'velocity', 'lobby', 'plain', 'fabric', 'forge') {
    New-Item -ItemType Directory -Force "$res\server-$s" | Out-Null
    Copy-Item "$net\$s\logs\latest.log" "$res\server-$s\" -ErrorAction SilentlyContinue
    Get-ChildItem "$net\$s" -Recurse -Filter 'packets.log' -ErrorAction SilentlyContinue | Copy-Item -Destination "$res\server-$s\" -ErrorAction SilentlyContinue
    Get-ChildItem "$net\$s" -Recurse -Include 'config.yml', 'rules.yml' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match 'ladsclientapi' } | Copy-Item -Destination "$res\server-$s\" -ErrorAction SilentlyContinue
}
Set-Content "$res\SUMMARY.txt" ($summary -join "`r`n")
Stamp 'done'
$summary
exit 0
