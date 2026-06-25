# Run-Benchmark.ps1
# Automated benchmarking framework for the Minecraft client.

$savesDir = "C:\The Lads Client\saves"
$benchmarkWorld = Join-Path $savesDir "benchmark_world"
$templateWorld = Join-Path $savesDir "New World"

$runSavesDir = "TheLadsCore/run/saves"
$runBenchmarkWorld = Join-Path $runSavesDir "benchmark_world"

# 1. Clear existing benchmark worlds and copy the template
if (Test-Path $benchmarkWorld) {
    Write-Host "Clearing existing global benchmark world..." -ForegroundColor Yellow
    Remove-Item -Path $benchmarkWorld -Recurse -Force -ErrorAction SilentlyContinue
}
if (Test-Path $runBenchmarkWorld) {
    Write-Host "Clearing existing execution benchmark world..." -ForegroundColor Yellow
    Remove-Item -Path $runBenchmarkWorld -Recurse -Force -ErrorAction SilentlyContinue
}

if (Test-Path $templateWorld) {
    Write-Host "Copying template world 'New World' to global 'benchmark_world'..." -ForegroundColor Green
    Copy-Item -Path $templateWorld -Destination $benchmarkWorld -Recurse -Force
    Write-Host "Copying template world 'New World' to execution 'benchmark_world'..." -ForegroundColor Green
    Copy-Item -Path $templateWorld -Destination $runBenchmarkWorld -Recurse -Force
} else {
    Write-Error "Template world not found at $templateWorld"
    exit 1
}

# 2. Standardize options.txt settings
$optionsPath = "TheLadsCore/run/options.txt"
if (Test-Path $optionsPath) {
    Write-Host "Standardizing options.txt settings..." -ForegroundColor Green
    $content = Get-Content $optionsPath
    $newContent = @()
    $keysToSet = @{
        "enableVsync" = "false"
        "maxFps" = "260"
        "graphicsMode" = "1"
        "graphicsPreset" = "`"fancy`""
        "renderDistance" = "12"
        "simulationDistance" = "12"
        "mipmapLevels" = "0"
    }
    
    $updatedKeys = @{}
    
    foreach ($line in $content) {
        if ($line -match "^([^:]+):(.*)$") {
            $key = $Matches[1].Trim()
            if ($keysToSet.ContainsKey($key)) {
                $newContent += "$($key):$($keysToSet[$key])"
                $updatedKeys[$key] = $true
            } else {
                $newContent += $line
            }
        } else {
            $newContent += $line
        }
    }
    
    # Add any keys that weren't present
    foreach ($key in $keysToSet.Keys) {
        if (-not $updatedKeys.ContainsKey($key)) {
            $newContent += "$($key):$($keysToSet[$key])"
        }
    }
    
    $newContent | Set-Content $optionsPath -Force
    Write-Host "options.txt standardized successfully." -ForegroundColor Green
} else {
    Write-Host "options.txt not found at $optionsPath, creating basic options file." -ForegroundColor Yellow
    $optionsDir = Split-Path $optionsPath
    if (-not (Test-Path $optionsDir)) {
        New-Item -ItemType Directory -Path $optionsDir -Force | Out-Null
    }
    $basicOptions = @(
        "enableVsync:false",
        "maxFps:260",
        "graphicsMode:1",
        "graphicsPreset:`"fancy`"",
        "renderDistance:12",
        "simulationDistance:12",
        "mipmapLevels:0"
    )
    $basicOptions | Set-Content $optionsPath -Force
}

# 3. Temporarily modify fabric.mod.json to remove Xaero references to prevent refmap crashes in dev environment
$fabricModJson = "TheLadsCore/src/main/resources/fabric.mod.json"
$fabricModJsonBak = "TheLadsCore/src/main/resources/fabric.mod.json.bak"

if (Test-Path $fabricModJson) {
    Write-Host "Backing up and temporarily removing Xaero references from fabric.mod.json..." -ForegroundColor Green
    Copy-Item -Path $fabricModJson -Destination $fabricModJsonBak -Force
    $jsonText = Get-Content $fabricModJson -Raw
    
    # Remove entrypoints and handle commas
    $jsonText = $jsonText -replace ',\s*"xaero\.lib\.XaeroLibFabric"', ''
    $jsonText = $jsonText -replace '"xaero\.lib\.XaeroLibFabric"\s*,', ''
    $jsonText = $jsonText -replace ',\s*"xaero\.minimap\.XaeroMinimapFabric"', ''
    $jsonText = $jsonText -replace '"xaero\.minimap\.XaeroMinimapFabric"\s*,', ''
    
    # Remove mixins and handle commas
    $jsonText = $jsonText -replace '"xaerolib\.mixins\.json"\s*,', ''
    $jsonText = $jsonText -replace '"xaerolib\.fabric\.mixins\.json"\s*,', ''
    $jsonText = $jsonText -replace '"xaerohud\.mixins\.json"\s*,', ''
    $jsonText = $jsonText -replace '"xaerohud\.fabric\.mixins\.json"\s*,', ''
    $jsonText = $jsonText -replace '"xaerominimap\.mixins\.json"\s*,', ''
    $jsonText = $jsonText -replace '"xaerominimap\.fabric\.mixins\.json"\s*,', ''
    
    # Fallback to remove raw strings if they remain
    $jsonText = $jsonText -replace '"xaerolib\.mixins\.json"', ''
    $jsonText = $jsonText -replace '"xaerolib\.fabric\.mixins\.json"', ''
    $jsonText = $jsonText -replace '"xaerohud\.mixins\.json"', ''
    $jsonText = $jsonText -replace '"xaerohud\.fabric\.mixins\.json"', ''
    $jsonText = $jsonText -replace '"xaerominimap\.mixins\.json"', ''
    $jsonText = $jsonText -replace '"xaerominimap\.fabric\.mixins\.json"', ''
    
    $jsonText | Set-Content $fabricModJson -Force
}

# Execute game benchmark via Gradle client runner
Write-Host "Executing game benchmark via Gradle runClient..." -ForegroundColor Green
Push-Location TheLadsCore

try {
    # Run client with system property and quickPlaySingleplayer argument
    .\gradlew.bat runClient --args="--quickPlaySingleplayer benchmark_world" "-Dthelads.benchmark=true" "-Pthelads.benchmark=true" "-Dfabric.skipDependencyCheck=true" "-Dfabric.skipDependencyChecks=true"
} finally {
    Pop-Location
    # Restore fabric.mod.json
    if (Test-Path $fabricModJsonBak) {
        Write-Host "Restoring original fabric.mod.json..." -ForegroundColor Green
        Copy-Item -Path $fabricModJsonBak -Destination $fabricModJson -Force
        Remove-Item -Path $fabricModJsonBak -Force
    }
}

# 4. Wait for results and print them
$resultsPath = "TheLadsCore/run/benchmark_results.json"
$globalResultsPath = "C:\The Lads Client\benchmark_results.json"

if (Test-Path $resultsPath) {
    Write-Host "`n=== BENCHMARK RESULTS (Execution Dir) ===" -ForegroundColor Cyan
    Get-Content $resultsPath | Write-Host -ForegroundColor Cyan
} elseif (Test-Path $globalResultsPath) {
    Write-Host "`n=== BENCHMARK RESULTS (Global Dir) ===" -ForegroundColor Cyan
    Get-Content $globalResultsPath | Write-Host -ForegroundColor Cyan
} else {
    Write-Error "Benchmark completed but benchmark_results.json was not found!"
}
