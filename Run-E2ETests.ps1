# Run-E2ETests.ps1
# Automates execution of the Java integration tests and performs post-run validation checks.

$ErrorActionPreference = "Stop"

Write-Output "=== Step 1: Running Java Integration Tests ==="
Push-Location TheLadsCore
try {
    # Run tests using gradlew wrapper
    .\gradlew.bat test --tests "com.thelads.core.client.IntegrationTests"
    Write-Output "Java IntegrationTests passed successfully!"
} catch {
    Write-Error "Java IntegrationTests failed!"
    Pop-Location
    exit 1
}
Pop-Location

Write-Output "=== Step 2: Post-Run Validation Checks ==="

# 1. Mixin configuration checks
$mixinsPath = "TheLadsCore/src/main/resources/theladscore.mixins.json"
if (-not (Test-Path $mixinsPath)) {
    Write-Error "Mixin configuration file not found at $mixinsPath"
    exit 1
}
$mixinsContent = Get-Content $mixinsPath -Raw
$hasCapesMixins = $mixinsContent -match "capes\."
$hasRenderScaleMixins = $mixinsContent -match "renderscale\."

Write-Output "[Validation] Checking theladscore.mixins.json:"
if ($hasCapesMixins) {
    Write-Output "  - Capes Mixins: FOUND"
} else {
    Write-Output "  - Capes Mixins: NOT FOUND (not yet ported/integrated)"
}

if ($hasRenderScaleMixins) {
    Write-Output "  - Render Scale Mixins: FOUND"
} else {
    Write-Output "  - Render Scale Mixins: NOT FOUND (not yet ported/integrated)"
}

# 2. Standalone jar files cleanup checks
$oldCapesPattern = "capes-*.jar"
$oldRenderScalePattern = "render scale *.jar"
$packwizModsPath = "The Lads Client Packwiz/mods"
$curseforgeModsPaths = "C:\Users\Arash\curseforge\minecraft\Instances\The Lads Client Dev *\mods"

Write-Output "[Validation] Checking for old standalone jar files:"
$foundOldJars = $false

# Check Packwiz mods folder
if (Test-Path $packwizModsPath) {
    $oldCapesInPackwiz = Get-ChildItem -Path $packwizModsPath -Filter $oldCapesPattern -ErrorAction SilentlyContinue
    $oldRenderInPackwiz = Get-ChildItem -Path $packwizModsPath -Filter $oldRenderScalePattern -ErrorAction SilentlyContinue

    foreach ($file in $oldCapesInPackwiz) {
        Write-Warning "Old Capes jar found in Packwiz: $($file.FullName)"
        $foundOldJars = $true
    }
    foreach ($file in $oldRenderInPackwiz) {
        Write-Warning "Old Render Scale jar found in Packwiz: $($file.FullName)"
        $foundOldJars = $true
    }
}

# Check CurseForge mods folder
$oldCapesInCurseforge = Get-ChildItem -Path $curseforgeModsPaths -ErrorAction SilentlyContinue | Where-Object { $_.Name -like $oldCapesPattern }
$oldRenderInCurseforge = Get-ChildItem -Path $curseforgeModsPaths -ErrorAction SilentlyContinue | Where-Object { $_.Name -like $oldRenderScalePattern }

foreach ($file in $oldCapesInCurseforge) {
    Write-Warning "Old Capes jar found in CurseForge instance: $($file.FullName)"
    $foundOldJars = $true
}
foreach ($file in $oldRenderInCurseforge) {
    Write-Warning "Old Render Scale jar found in CurseForge instance: $($file.FullName)"
    $foundOldJars = $true
}

if (-not $foundOldJars) {
    Write-Output "  - Standalone Mod Cleanup: PASS (No old jar files detected)"
} else {
    Write-Error "  - Standalone Mod Cleanup: FAIL (Old standalone jar files should be removed)"
    exit 1
}

Write-Output "=== E2E Test Runner completed successfully! ==="
exit 0
