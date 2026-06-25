# Run-E2ETests.ps1
# Automates execution of the Java integration tests and performs post-run validation checks.

$ErrorActionPreference = "Stop"

Write-Output "=== Step 1: Running Java Integration Tests ==="
Push-Location TheLadsCore
try {
    # Run tests using gradlew wrapper
    .\gradlew.bat test --tests "com.thelads.core.client.IntegrationTests" --tests "com.thelads.core.client.E2EPolishTests"
    Write-Output "Java Integration and E2E Polish tests passed successfully!"
} catch {
    Write-Error "Java tests failed!"
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
$hasImmediatelyFastMixins = $mixinsContent -match "immediatelyfast\."
$hasSkinLayersMixins = $mixinsContent -match "skinlayers\."

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

if ($hasImmediatelyFastMixins) {
    Write-Output "  - ImmediatelyFast Mixins: FOUND"
} else {
    Write-Output "  - ImmediatelyFast Mixins: NOT FOUND (not yet ported/integrated)"
}

if ($hasSkinLayersMixins) {
    Write-Output "  - SkinLayers Mixins: FOUND"
} else {
    Write-Output "  - SkinLayers Mixins: NOT FOUND (not yet ported/integrated)"
}

# 2. gradle.properties version checks
$propertiesPath = "TheLadsCore/gradle.properties"
Write-Output "[Validation] Checking gradle.properties for Minecraft 26.2 target:"
if (Test-Path $propertiesPath) {
    $propertiesContent = Get-Content $propertiesPath -Raw
    if ($propertiesContent -match "minecraft_version\s*=\s*(?<version>\S+)") {
        $mcVersion = $Matches['version']
        if ($mcVersion -eq "26.2") {
            Write-Output "  - Minecraft Version: Target 26.2 (PASS)"
        } else {
            Write-Warning "  - Minecraft Version: target is not 26.2 (currently: $mcVersion)"
        }
    } else {
        Write-Warning "  - Minecraft Version: minecraft_version not found in gradle.properties"
    }
} else {
    Write-Warning "  - gradle.properties not found at $propertiesPath"
}

# 3. Shaded jar verification checks
Write-Output "[Validation] Checking shaded jar statuses in build output:"
$buildLibsDir = "TheLadsCore/build/libs"
$foundJar = $false
if (Test-Path $buildLibsDir) {
    $jars = Get-ChildItem -Path $buildLibsDir -Filter "*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-dev.jar" }
    if ($jars) {
        $jarPath = $jars[0].FullName
        $foundJar = $true
        Write-Output "  - Found build output jar: $($jars[0].Name)"
        
        try {
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
            
            $jeiPresent = $null -ne ($zip.Entries | Where-Object { $_.FullName -eq "mezz/jei/api/IModPlugin.class" })
            $xaeroPresent = $null -ne ($zip.Entries | Where-Object { $_.FullName -eq "xaero/map/WorldMapFabric.class" })
            $starlightPresent = $null -ne ($zip.Entries | Where-Object { $_.FullName -eq "ca/spottedleaf/starlight/common/light/StarLightEngine.class" })
            
            if ($jeiPresent) {
                Write-Output "    - JEI Shaded Class (IModPlugin): FOUND"
            } else {
                Write-Warning "    - JEI Shaded Class (IModPlugin): NOT FOUND in build jar"
            }
            if ($xaeroPresent) {
                Write-Output "    - Xaero World Map Shaded Class: FOUND"
            } else {
                Write-Warning "    - Xaero World Map Shaded Class: NOT FOUND in build jar"
            }
            if ($starlightPresent) {
                Write-Output "    - StarLight Lighting Engine Shaded Class: FOUND"
            } else {
                Write-Warning "    - StarLight Lighting Engine Shaded Class: NOT FOUND in build jar"
            }
            
            $zip.Dispose()
        } catch {
            Write-Warning "    - Failed to inspect built jar entries: $_"
        }
    }
}

if (-not $foundJar) {
    Write-Warning "  - No built output jar found in $buildLibsDir. Run '.\gradlew.bat build' in TheLadsCore to generate it."
}

# 4. Standalone jar files cleanup checks
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
