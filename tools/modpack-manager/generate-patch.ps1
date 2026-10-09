<#
.SYNOPSIS
    Generates a surgical update patch (update-<version>.json) from changes made in the Dev instance.
.DESCRIPTION
    Compares the active Dev instance against the baseline and compiles:
    - Added and removed mod JARs
    - New keybinds as "defaults" (safe for players)
    - Forced keybind overrides (if specified)
    - Granular config patches (JSON / TOML / CFG)
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$NewVersion,

    [string]$Description = "Surgical update patch",
    [string]$InstanceGameDir = "$env:APPDATA\.theladsclient\instances\zero-smp-1.20.1\minecraft",
    [string]$BaselineDir = "$PSScriptRoot\baseline-1.0.0",
    [string]$OutputDir = "$PSScriptRoot\patches"
)

Set-StrictMode -Off
$ErrorActionPreference = "Stop"

if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

Write-Host "Compiling patch $NewVersion from Dev Instance..." -ForegroundColor Cyan

# 1. Mod JARs Diff
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

$addMods = @()
$removeMods = @()

foreach ($name in $currentMods.Keys) {
    if (-not $baselineMods.ContainsKey($name)) {
        $addMods += [PSCustomObject]@{
            fileName = $name
            sha1 = $currentMods[$name]
        }
    }
}

foreach ($name in $baselineMods.Keys) {
    if (-not $currentMods.ContainsKey($name)) {
        $removeMods += $name
    }
}

# 2. Keybinds Diff
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

$defaultKeybinds = [ordered]@{}
$forceKeybinds = [ordered]@{}

foreach ($k in $currentOptions.Keys) {
    if ($k.StartsWith("key_")) {
        $val = $currentOptions[$k]
        if (-not $baselineOptions.Contains($k)) {
            # Brand new keybind from a newly added mod -> assign as safe default!
            $defaultKeybinds[$k] = $val
        }
    }
}

# 3. Config Diffs
$baselineConfigDir = Join-Path $BaselineDir "config"
$currentConfigDir = Join-Path $InstanceGameDir "config"
$configPatches = @()

if (Test-Path $currentConfigDir) {
    $currentConfigFiles = Get-ChildItem $currentConfigDir -Recurse -File
    foreach ($file in $currentConfigFiles) {
        $relPath = $file.FullName.Substring($currentConfigDir.Length).TrimStart('\', '/')
        $baselineFilePath = Join-Path $baselineConfigDir $relPath

        if (Test-Path $baselineFilePath) {
            $hash1 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
            $hash2 = (Get-FileHash -LiteralPath $baselineFilePath -Algorithm SHA256).Hash
            if ($hash1 -ne $hash2) {
                $ext = $file.Extension.ToLowerInvariant()
                if ($ext -eq ".json") {
                    $configPatches += [PSCustomObject]@{
                        file = "config/$($relPath.Replace('\', '/'))"
                        format = "json"
                        set = @{} # Populated by reviewer
                    }
                } else {
                    $configPatches += [PSCustomObject]@{
                        file = "config/$($relPath.Replace('\', '/'))"
                        format = $ext.TrimStart('.')
                        keyValues = @{} # Populated by reviewer
                    }
                }
            }
        }
    }
}

$patch = [PSCustomObject]@{
    packId = "zero-smp-1.20.1"
    version = $NewVersion
    description = $Description
    mods = [PSCustomObject]@{
        add = $addMods
        remove = $removeMods
    }
    keybinds = [PSCustomObject]@{
        defaults = $defaultKeybinds
        force = $forceKeybinds
    }
    configPatches = $configPatches
}

$patchFile = Join-Path $OutputDir "update-$NewVersion.json"
$patch | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $patchFile -Encoding UTF8

Write-Host "`n[OK] Successfully generated patch template: $patchFile" -ForegroundColor Green
Write-Host "  - Added Mods:    $($addMods.Count)"
Write-Host "  - Removed Mods:  $($removeMods.Count)"
Write-Host "  - New Keybinds:  $($defaultKeybinds.Count)"
Write-Host "  - Config Files:  $($configPatches.Count)"
