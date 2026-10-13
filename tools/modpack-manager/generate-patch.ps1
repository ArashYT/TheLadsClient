<#
.SYNOPSIS
    Generates a surgical update patch (update-<version>.json) from changes made in the Dev instance.
.DESCRIPTION
    Compares the active Dev instance against the baseline and compiles:
    - Added and removed mod JARs (added jars get a download url: Modrinth by hash, else -FallbackUrlBase)
    - New keybinds as "defaults" (safe for players: applied only when the player has no value yet)
    - Keybinds that have to be set although the dev options.txt does not know them yet (-ExtraKeybindDefaults)
    - Forced keybind overrides (-ForceKeybinds)
    - Config patch templates (only with -IncludeConfigTemplates; an empty template changes nothing)
    theladscore.jar is ignored: the launcher keeps every Lads Core in a modpack at its newest bundled build by itself.
    Server guard: before writing, the removed jars are checked against a status ping of the Zero SMP server
    (check-server-compat.py, no login). A jar whose mod owns a channel the server requires, or Corgilib / Data Anchor
    (patch 1.1.1 removed them and nobody could join), stops the script; mods the server merely lists give warnings.
.EXAMPLE
    .\generate-patch.ps1 -NewVersion 1.0.1 -Description "Adds c2mef, lazyyyyy, crawl-on-demand" `
        -ExtraKeybindDefaults @{ 'key_key.crawlondemand.toggle_crawl' = 'key.keyboard.j' }
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$NewVersion,

    [string]$Description = "Surgical update patch",
    [string]$InstanceGameDir = "$env:APPDATA\.theladsclient\instances\zero-smp-1.20.1\minecraft",
    [string]$BaselineDir = "$PSScriptRoot\baseline-1.0.0",
    [string]$OutputDir = "$PSScriptRoot\patches",
    [string]$OutputFile = "",

    # options.txt keys a mod adds only once it has run (so the dev options.txt lacks them): key_<id> -> key.keyboard.x
    [hashtable]$ExtraKeybindDefaults = @{},
    [hashtable]$ForceKeybinds = @{},
    # Extra jar names the patch removes besides the ones missing from the dev instance (e.g. duplicates Lads Core replaces)
    [string[]]$ExtraRemoveMods = @(),
    # url = <base>/<escaped file name> for added jars Modrinth does not host
    [string]$FallbackUrlBase = "",
    [switch]$SkipUrlLookup,
    [switch]$IncludeConfigTemplates,

    # Zero SMP server pinged (status only) to guard removals; -SkipServerCheck turns the guard off, -AllowServerConflicts
    # lets a patch through that the guard rejects (a join test has to prove it first).
    [string]$ServerHost = "nikzerosmp.my.pebble.host",
    [int]$ServerPort = 25565,
    [switch]$SkipServerCheck,
    [switch]$AllowServerConflicts
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
    if ($jar.Name -like "theladscore*.jar" -or $jar.Name -like "*TheLadsCore*.jar") { continue }
    $sha1 = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA1).Hash.ToLowerInvariant()
    $currentMods[$jar.Name] = $sha1
}

# Modrinth hash -> download url for the added jars (one request).
$modrinthUrls = @{}
$newNames = @($currentMods.Keys | Where-Object { -not $baselineMods.ContainsKey($_) })
if ($newNames.Count -gt 0 -and -not $SkipUrlLookup) {
    try {
        $body = @{ hashes = @($newNames | ForEach-Object { $currentMods[$_] }); algorithm = "sha1" } | ConvertTo-Json -Compress
        $found = Invoke-RestMethod -Method Post -Uri "https://api.modrinth.com/v2/version_files" -Body $body `
            -ContentType "application/json" -UserAgent "TheLadsClient-modpack-manager/1.9.3"
        foreach ($prop in $found.PSObject.Properties) {
            $file = @($prop.Value.files | Where-Object { $_.hashes.sha1 -eq $prop.Name })[0]
            if ($file) { $modrinthUrls[$prop.Name] = $file.url }
        }
    } catch {
        Write-Warning "Modrinth lookup failed ($($_.Exception.Message)); added jars get no url unless -FallbackUrlBase is set."
    }
}

$addMods = @()
$removeMods = @()

foreach ($name in ($currentMods.Keys | Sort-Object)) {
    if (-not $baselineMods.ContainsKey($name)) {
        $sha1 = $currentMods[$name]
        $url = $modrinthUrls[$sha1]
        if (-not $url -and $FallbackUrlBase) { $url = $FallbackUrlBase.TrimEnd('/') + "/" + [uri]::EscapeDataString($name) }
        if (-not $url) { Write-Warning "No download url for ${name}: the patch cannot fetch it (pack archives still carry it)." }
        $entry = [ordered]@{ fileName = $name; sha1 = $sha1 }
        if ($url) { $entry["url"] = $url }
        $addMods += [PSCustomObject]$entry
    }
}

foreach ($name in ($baselineMods.Keys | Sort-Object)) {
    if ($name -like "theladscore*.jar") { continue }
    if (-not $currentMods.ContainsKey($name)) {
        $removeMods += $name
    }
}
foreach ($name in $ExtraRemoveMods) {
    if ($removeMods -notcontains $name) { $removeMods += $name }
}

# 1b. Server guard: would the server still let a player with this pack join?
if ($removeMods.Count -gt 0 -and -not $SkipServerCheck) {
    $python = Get-Command python -ErrorAction SilentlyContinue
    if (-not $python) { $python = Get-Command py -ErrorAction SilentlyContinue }
    if (-not $python) {
        Write-Warning "Python not found: the server guard (check-server-compat.py) was skipped. Removed jars: $($removeMods -join ', ')"
    } else {
        $guardOutput = & $python.Source -I (Join-Path $PSScriptRoot "check-server-compat.py") $ServerHost $ServerPort `
            (Join-Path $InstanceGameDir "mods") --remove ($removeMods -join ",") 2>&1
        $guardExit = $LASTEXITCODE
        $guardOutput | ForEach-Object { Write-Host "  [server] $_" }
        if ($guardExit -eq 1 -and -not $AllowServerConflicts) {
            throw "The patch would remove mods the Zero SMP server needs (see [server] ERROR lines). Fix the removal, or pass -AllowServerConflicts after a join test."
        } elseif ($guardExit -eq 1) {
            Write-Warning "Server guard ERRORs ignored (-AllowServerConflicts)."
        } elseif ($guardExit -ne 0) {
            Write-Warning "Server guard could not run (exit ${guardExit}: server unreachable?); the removals are unchecked."
        }
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
        # An unbound key is what Minecraft assigns anyway; shipping it would only be noise.
        if (-not $baselineOptions.Contains($k) -and $val -ne "key.keyboard.unknown") {
            # Brand new keybind from a newly added mod -> assign as safe default!
            $defaultKeybinds[$k] = $val
        }
    }
}
foreach ($k in $ExtraKeybindDefaults.Keys) { $defaultKeybinds[$k] = [string]$ExtraKeybindDefaults[$k] }
foreach ($k in $ForceKeybinds.Keys) { $forceKeybinds[$k] = [string]$ForceKeybinds[$k] }

# 3. Config Diffs
$baselineConfigDir = Join-Path $BaselineDir "config"
$currentConfigDir = Join-Path $InstanceGameDir "config"
$configPatches = @()

if ($IncludeConfigTemplates -and (Test-Path $currentConfigDir)) {
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
        add = @($addMods)
        remove = @($removeMods)
    }
    keybinds = [PSCustomObject]@{
        defaults = $defaultKeybinds
        force = $forceKeybinds
    }
    configPatches = @($configPatches)
}

$patchFile = if ($OutputFile) { $OutputFile } else { Join-Path $OutputDir "update-$NewVersion.json" }
$json = $patch | ConvertTo-Json -Depth 6
# UTF-8 without BOM on both Windows PowerShell 5.1 and 7.
[System.IO.File]::WriteAllText($patchFile, $json, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "`n[OK] Successfully generated patch: $patchFile" -ForegroundColor Green
Write-Host "  - Added Mods:    $($addMods.Count)"
Write-Host "  - Removed Mods:  $($removeMods.Count)"
Write-Host "  - New Keybinds:  $($defaultKeybinds.Count)"
Write-Host "  - Config Files:  $($configPatches.Count)"
