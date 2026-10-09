<#
.SYNOPSIS
    Checks all changes between the Zero SMP Dev instance and the Baseline release.
.DESCRIPTION
    Scans for:
    - Added, removed, or updated mod JARs
    - New or modified keybinds in options.txt
    - Keybind conflicts across all mods
    - Modified configuration files (JSON, TOML, CFG)
#>

[CmdletBinding()]
param(
    [string]$InstanceGameDir = "$env:APPDATA\.theladsclient\instances\zero-smp-1.20.1\minecraft",
    [string]$BaselineDir = "$PSScriptRoot\baseline-1.0.0",
    [switch]$GeneratePatchTemplate
)

Set-StrictMode -Off
$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "       The Lads Client - Modpack Change Inspector         " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Dev Instance: $InstanceGameDir"
Write-Host "Baseline:     $BaselineDir`n"

if (!(Test-Path $InstanceGameDir)) {
    throw "Instance game directory not found: $InstanceGameDir"
}
if (!(Test-Path $BaselineDir)) {
    throw "Baseline directory not found: $BaselineDir"
}

# -----------------------------------------------------------------------------
# 1. MODS DIFF
# -----------------------------------------------------------------------------
Write-Host "[1/3] Scanning Mod JARs..." -ForegroundColor Yellow

$baselineManifestPath = Join-Path $BaselineDir "manifest.json"
$baselineMods = @{}
if (Test-Path $baselineManifestPath) {
    $manifest = Get-Content -Raw $baselineManifestPath | ConvertFrom-Json
    foreach ($m in $manifest.mods) {
        $baselineMods[$m.fileName] = $m.sha1
    }
}

$currentMods = @{}
$currentModFiles = Get-ChildItem (Join-Path $InstanceGameDir "mods") -Filter "*.jar" -ErrorAction SilentlyContinue
foreach ($jar in $currentModFiles) {
    $sha1 = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA1).Hash.ToLowerInvariant()
    $currentMods[$jar.Name] = $sha1
}

$addedMods = @()
$removedMods = @()
$updatedMods = @()

foreach ($name in $currentMods.Keys) {
    if (-not $baselineMods.ContainsKey($name)) {
        # Check if it's a version replacement
        $stem = ($name -split "[-_]\d")[0]
        $match = $baselineMods.Keys | Where-Object { ($_ -split "[-_]\d")[0] -eq $stem }
        if ($match) {
            $updatedMods += [PSCustomObject]@{ Old = $match; New = $name }
        } else {
            $addedMods += $name
        }
    } elseif ($baselineMods[$name] -ne $currentMods[$name]) {
        $updatedMods += [PSCustomObject]@{ Old = "$name (modified hash)"; New = $name }
    }
}

foreach ($name in $baselineMods.Keys) {
    if (-not $currentMods.ContainsKey($name) -and -not ($updatedMods | Where-Object { $_.Old -eq $name })) {
        $removedMods += $name
    }
}

if ($addedMods.Count -eq 0 -and $removedMods.Count -eq 0 -and $updatedMods.Count -eq 0) {
    Write-Host "  [OK] Mod JARs: No changes (in sync with baseline)" -ForegroundColor Green
} else {
    if ($addedMods.Count -gt 0) {
        Write-Host "  [+] Added Mods ($($addedMods.Count)):" -ForegroundColor Green
        foreach ($m in $addedMods) { Write-Host "      + $m" -ForegroundColor Green }
    }
    if ($removedMods.Count -gt 0) {
        Write-Host "  [-] Removed Mods ($($removedMods.Count)):" -ForegroundColor Red
        foreach ($m in $removedMods) { Write-Host "      - $m" -ForegroundColor Red }
    }
    if ($updatedMods.Count -gt 0) {
        Write-Host "  [~] Updated Mods ($($updatedMods.Count)):" -ForegroundColor Cyan
        foreach ($m in $updatedMods) { Write-Host "      ~ $($m.Old) -> $($m.New)" -ForegroundColor Cyan }
    }
}

# -----------------------------------------------------------------------------
# 2. KEYBINDS & CONFLICT ANALYSIS
# -----------------------------------------------------------------------------
Write-Host "`n[2/3] Analyzing Keybinds in options.txt..." -ForegroundColor Yellow

function Parse-OptionsFile([string]$path) {
    $map = [ordered]@{}
    if (Test-Path $path) {
        foreach ($line in Get-Content $path) {
            $line = $line.Trim()
            if ($line -match "^([^:]+):(.*)$") {
                $map[$matches[1]] = $matches[2]
            }
        }
    }
    return $map
}

$baselineOptions = Parse-OptionsFile (Join-Path $BaselineDir "options.txt")
$currentOptions = Parse-OptionsFile (Join-Path $InstanceGameDir "options.txt")

$newKeybinds = @{}
$changedKeybinds = @{}
$currentKeybindings = @{}

foreach ($k in $currentOptions.Keys) {
    $val = $currentOptions[$k]
    if ($k.StartsWith("key_")) {
        $currentKeybindings[$k] = $val
        if (-not $baselineOptions.Contains($k)) {
            $newKeybinds[$k] = $val
        } elseif ($baselineOptions[$k] -ne $val) {
            $changedKeybinds[$k] = [PSCustomObject]@{ Old = $baselineOptions[$k]; New = $val }
        }
    }
}

if ($newKeybinds.Count -eq 0 -and $changedKeybinds.Count -eq 0) {
    Write-Host "  [OK] Keybindings: Identical to baseline" -ForegroundColor Green
} else {
    if ($newKeybinds.Count -gt 0) {
        Write-Host "  [+] New Keybinds Detected ($($newKeybinds.Count)):" -ForegroundColor Green
        foreach ($k in $newKeybinds.Keys) {
            Write-Host "      + $k = $($newKeybinds[$k])" -ForegroundColor Green
        }
    }
    if ($changedKeybinds.Count -gt 0) {
        Write-Host "  [~] Modified Keybinds ($($changedKeybinds.Count)):" -ForegroundColor Cyan
        foreach ($k in $changedKeybinds.Keys) {
            Write-Host "      ~ ${k}: '$($changedKeybinds[$k].Old)' -> '$($changedKeybinds[$k].New)'" -ForegroundColor Cyan
        }
    }
}

# Conflict Checker
$keyGroups = @{}
foreach ($k in $currentKeybindings.Keys) {
    $assigned = $currentKeybindings[$k]
    if ($assigned -and $assigned -ne "key.keyboard.unknown" -and $assigned -ne "-1") {
        if (-not $keyGroups.ContainsKey($assigned)) {
            $keyGroups[$assigned] = @()
        }
        $keyGroups[$assigned] += $k
    }
}

$conflicts = $keyGroups.Keys | Where-Object { $keyGroups[$_].Count -gt 1 }
if ($conflicts.Count -gt 0) {
    Write-Host "`n  [!] KEYBIND CONFLICTS DETECTED ($($conflicts.Count)):" -ForegroundColor Magenta
    foreach ($code in $conflicts) {
        $keys = $keyGroups[$code] -join ", "
        Write-Host "      Conflict on [$code]: $keys" -ForegroundColor Magenta
    }
} else {
    Write-Host "  [OK] No overlapping keybind conflicts found." -ForegroundColor Green
}

# -----------------------------------------------------------------------------
# 3. CONFIGURATION FILE DIFFS
# -----------------------------------------------------------------------------
Write-Host "`n[3/3] Inspecting Config Files (config/)..." -ForegroundColor Yellow

$baselineConfigDir = Join-Path $BaselineDir "config"
$currentConfigDir = Join-Path $InstanceGameDir "config"

$modifiedConfigs = @()
$newConfigs = @()

if (Test-Path $currentConfigDir) {
    $currentConfigFiles = Get-ChildItem $currentConfigDir -Recurse -File
    foreach ($file in $currentConfigFiles) {
        $relPath = $file.FullName.Substring($currentConfigDir.Length).TrimStart('\', '/')
        $baselineFilePath = Join-Path $baselineConfigDir $relPath

        if (-not (Test-Path $baselineFilePath)) {
            $newConfigs += $relPath
        } else {
            # Fast check with hash
            $hash1 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
            $hash2 = (Get-FileHash -LiteralPath $baselineFilePath -Algorithm SHA256).Hash
            if ($hash1 -ne $hash2) {
                $modifiedConfigs += $relPath
            }
        }
    }
}

if ($modifiedConfigs.Count -eq 0 -and $newConfigs.Count -eq 0) {
    Write-Host "  [OK] Config Files: All configs match baseline" -ForegroundColor Green
} else {
    if ($newConfigs.Count -gt 0) {
        Write-Host "  [+] New Config Files ($($newConfigs.Count)):" -ForegroundColor Green
        foreach ($c in $newConfigs) { Write-Host "      + $c" -ForegroundColor Green }
    }
    if ($modifiedConfigs.Count -gt 0) {
        Write-Host "  [~] Modified Config Files ($($modifiedConfigs.Count)):" -ForegroundColor Cyan
        foreach ($c in $modifiedConfigs) {
            Write-Host "      ~ $c" -ForegroundColor Cyan
        }
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "                 Review Summary Complete                  " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
