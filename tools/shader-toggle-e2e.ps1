# End-to-end QA of the in-game shader toggle on a regular profile, in a Verification sandbox (artifacts\verification\<dir>):
#   run 1  shaders off (Iris jar suspended) -> video settings show the button -> confirm screen -> "Restart with shaders" (game quits)
#   run 2  the launcher step (Verification: LADS_VERIFY_SHADERS=1 = TakeRelaunch + Preference + PrepareAsync + installer) brings the game back
#          into the same world with Iris loaded -> "Turn shaders off" / "Shaders are off" -> restart (26.3: through Iris's pack screen)
#   run 3  back with Iris suspended again.
# 1.20.1 is driven by the Verification script language (README-1201.md), 26.3 by LADS_QA_SHADERTOGGLE (ShaderToggleQa26). Needs the Core jars
# built from this worktree: TheLadsCore\gradlew :v1_20_1:remapJar (the harness rebuilds it) / :v26_3:build, copied to TheLadsLauncher\game-mods\26.3\theladscore.jar.
param([ValidateSet('1.20.1', '26.3')] [string] $Version = '1.20.1', [string] $Root = (Resolve-Path "$PSScriptRoot\..").Path, [switch] $Fresh,
    [string] $Harness = '')
$ErrorActionPreference = 'Stop'
$h = if ($Harness) { $Harness } else { "$Root\TheLadsLauncher.Verification\bin\Debug\net8.0-windows\TheLadsLauncher.Verification.exe" }
$dir = "shaders-$($Version.Replace('.', ''))"
$game = "$Root\artifacts\verification\$dir"
$out = "$Root\artifacts\verification\$dir-e2e"; New-Item -ItemType Directory -Force $out | Out-Null
if ($Fresh -and (Test-Path $game)) { Remove-Item -Recurse -Force $game }
$env:LADS_VERIFY_SHADERS = '1'; $env:LADS_VERIFY_TIMEOUT_SEC = '900'; $env:LADS_VERIFY_TRIPWIRE = ''

function Report([string] $label) {
    $log = if (Test-Path "$game\logs\latest.log") { Get-Content "$game\logs\latest.log" } else { @() }
    Copy-Item "$game\logs\latest.log" "$out\$label-latest.log" -ErrorAction SilentlyContinue
    $mods = (Get-ChildItem "$game\mods" -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^(iris|EuphoriaPatcher|euphoria)' } | ForEach-Object Name) -join ', '
    "== $label"
    "   iris/euphoria files   : $mods"
    "   iris loaded (mod list): $([bool]($log | Select-String '^\s+- iris ' -Quiet))"
    $backend = $log | Select-String 'Using graphics backend (\w+)' | Select-Object -First 1
    "   graphics backend      : $(if ($backend) { $backend.Matches.Groups[1].Value } else { '(no such line: 1.20.1 is OpenGL)' })"
    "   Lads shaders lines    : $((($log | Select-String 'Lads shaders|Lads qa shaders') | ForEach-Object { $_.Line -replace '^\[[^\]]*\] \[[^\]]*\] ', '' }) -join ' | ')"
    foreach ($f in 'lads-renderer-choice.json', 'lads-relaunch.json', 'lads-renderer.json') {
        if (Test-Path "$game\$f") { "   $f = $((Get-Content "$game\$f" -Raw) -replace '\s+', ' ')" }
    }
}

if ($Version -eq '1.20.1') {
    # Script files (one step per line): quotes group a token.
    Set-Content "$out\s1.txt" @('wait 40', 'screen options', 'wait 20', 'press "Video Settings"', 'wait 40', 'shot s1-video-off', 'press "Shader packs..."', 'wait 30', 'shot s1-confirm', 'wait 10', 'press "Restart with shaders"')
    Set-Content "$out\s2.txt" @('wait 20', 'screen options', 'wait 20', 'press "Video Settings"', 'wait 40', 'shot s2-video-on', 'press "Turn shaders off (restart)"', 'wait 30', 'shot s2-confirm-off', 'wait 10', 'press "Restart without shaders"')
    Set-Content "$out\s3.txt" @('wait 20', 'screen options', 'wait 20', 'press "Video Settings"', 'wait 40', 'shot s3-video-off', 'quit')
    & $h --mc1201 $Root --dir $dir --fresh-world --no-build --script "$out\s1.txt" *> "$out\run1.txt"; Report 'run1-shaders-off'
    & $h --mc1201 $Root --dir $dir --no-world --no-build --script "$out\s2.txt" *> "$out\run2.txt"; Report 'run2-shaders-on'
    & $h --mc1201 $Root --dir $dir --no-world --no-build --script "$out\s3.txt" *> "$out\run3.txt"; Report 'run3-shaders-off-again'
} else {
    $env:LADS_QA_SHADERTOGGLE_TAG = 'r1'; $env:LADS_QA_SHADERTOGGLE = 'world,video,page,restart'
    & $h 26.3 $Root --title --dir $dir *> "$out\run1.txt"; Report 'run1-vulkan-shaders-off'
    $env:LADS_QA_SHADERTOGGLE_TAG = 'r2'; $env:LADS_QA_SHADERTOGGLE = 'ready,iris,packscreen,warn,restart'
    & $h 26.3 $Root --title --dir $dir *> "$out\run2.txt"; Report 'run2-opengl-iris'
    $env:LADS_QA_SHADERTOGGLE_TAG = 'r3'; $env:LADS_QA_SHADERTOGGLE = 'ready,iris,video,quit'
    & $h 26.3 $Root --title --dir $dir *> "$out\run3.txt"; Report 'run3-vulkan-again'
}
"screenshots: $game\screenshots ; logs: $out"
