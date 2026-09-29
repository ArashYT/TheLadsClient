[CmdletBinding()]
param(
    [switch]$Launcher,
    [switch]$Install,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'artifacts/client')
)
$ErrorActionPreference = 'Stop'
$coreDirectory = Join-Path $PSScriptRoot 'TheLadsCore'
$launcherProject = Join-Path $PSScriptRoot 'TheLadsLauncher/TheLadsLauncher.csproj'
$publishDirectory = [IO.Path]::GetFullPath($OutputDirectory)

Push-Location -LiteralPath $coreDirectory
try {
    & .\gradlew.bat build deploy -x test --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Core build/deploy failed. The previous release was not launched.' }
}
finally { Pop-Location }

if ($Launcher -or $Install) {
    & dotnet publish $launcherProject -c Release -r win-x64 --self-contained true -o $publishDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Launcher publication failed.' }
    foreach ($version in @('1.21.1', '1.21.11', '26.2', '26.3')) {
        $artifact = Join-Path $publishDirectory "game-mods/$version/theladscore.jar"
        if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) { throw "Missing production core for $version." }
        $manifestPath = Join-Path $publishDirectory "game-mods/$version/client-mods.json"
        if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Missing client mod manifest for $version." }
    }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'docs/MICROSOFT_LOGIN_SETUP.md') -Destination $publishDirectory -Force
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'docs/REPAIR_STATUS.md') -Destination $publishDirectory -Force
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'docs/CLIENT_MODS.md') -Destination $publishDirectory -Force
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'docs/INTEGRATED_MODULES.md') -Destination $publishDirectory -Force
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'docs/HUD_PERFORMANCE.md') -Destination $publishDirectory -Force
    foreach ($document in @('CLIENT_26_3.md', 'MOD_COVERAGE.md', 'mod-coverage.json', 'NATIVE_MODULES_26_2.md', 'STANDALONE_ACCOUNTS.md', 'DEVELOPMENT_BUILD_26_2.md', 'REFERENCE_ENGINES_26_2.md', 'SHULKER_BOX_UTILS_26_2.md', 'RENDER_SCALE_26_2.md', 'DISCORD_RPC_SETUP.md', 'NATIVE_FOOD_26_2.md', 'NATIVE_RAISED_26_2.md', 'NATIVE_PAPER_DOLL_26_2.md', 'AUTO_RECONNECT_26_2.md', 'DYNAMIC_FPS_26_2.md', 'NATIVE_PARITY_AUDIT_26_2.md', 'AUTO_WORLD_QA_26_2.md', 'NATIVE_RUNTIME_CHECKPOINT_26_2.md', 'SIGNAL_LOSS_26_2.md', 'DURABILITY_TOOLTIP_26_2.md', 'NATIVE_TAB_TWEAKS_26_2.md', 'NATIVE_CROSSHAIR_26_2.md', 'NATIVE_SCREENSHOTS_26_2.md', 'NATIVE_CLUMPS_26_2.md', 'NATIVE_NARRATOR_26_2.md')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot "docs/$document") -Destination $publishDirectory -Force
    }
    Write-Host "Launcher ready: $(Join-Path $publishDirectory 'TheLadsLauncher.exe')"
}

if ($Install) {
    & (Join-Path $PSScriptRoot 'tools/Install-LadsRelease.ps1') -SourceDirectory $publishDirectory
}
