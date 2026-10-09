<#
.SYNOPSIS
    Exports the Zero SMP dev instance as the pack archive fresh installs use (ExperiencesService.PackArchiveUrl).
.DESCRIPTION
    Read-only on the instance. Writes one zip:
      pack.json                 packId, packVersion, loader, appliedPatches, every mod (fileName/size/sha1)
      overrides/mods/*.jar      the enabled mods
      overrides/<dir>/...       config, defaultconfigs, configureddefaults, resourcepacks, shaderpacks (see -IncludeDirs)
      overrides/servers.dat, overrides/icon.png
      patches/update-*.json     every patch next to this script (patches\), for ExperiencesService.ApplyPendingPatchesAsync
    Never exported: saves, options.txt, logs, caches, screenshots, accounts, the player's own files.
    Host the zip, put its https url (and the printed SHA-256) into ExperiencesService.ZeroSmp (PackArchiveUrl, PackArchiveSha256).
    For this PC, copy it to <Lads data>\experiences\zero-smp-1.20.1\pack.zip: the launcher unpacks it on the next install.
.EXAMPLE
    .\export-pack.ps1 -OutputFile D:\release\zero-smp-1.20.1-1.0.1.zip
#>

[CmdletBinding()]
param(
    [string]$InstanceDir = "$env:APPDATA\.theladsclient\instances\zero-smp-1.20.1",
    [string]$OutputFile = "",
    # Default: the newest patch next to this script (the instance is that patch applied), else 1.0.0.
    [string]$PackVersion = "",
    [string]$PatchesDir = "$PSScriptRoot\patches",
    [string[]]$IncludeDirs = @("config", "defaultconfigs", "configureddefaults", "resourcepacks", "shaderpacks"),
    [string[]]$IncludeFiles = @("servers.dat"),
    # Relative to the game folder, '/' separators; a trailing * matches the rest of the path.
    # resourceful-config-web.json holds a per-install web UI password.
    [string[]]$Exclude = @("config/worldedit/.archive-unpack/*", "config/jei/world/*", "config/resourceful-config-web.json", "*.backup", "*.bak", "*.log")
)

Set-StrictMode -Off
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$gameDir = Join-Path $InstanceDir "minecraft"
if (!(Test-Path (Join-Path $gameDir "mods"))) { throw "No mods folder in $gameDir" }
$instanceJson = Get-Content -Raw (Join-Path $InstanceDir "instance.json") | ConvertFrom-Json

$patches = @()
if (Test-Path $PatchesDir) {
    $patches = @(Get-ChildItem $PatchesDir -Filter "update-*.json" | ForEach-Object {
        $p = Get-Content -Raw $_.FullName | ConvertFrom-Json
        [PSCustomObject]@{ File = $_; Version = $p.version; PackId = $p.packId }
    } | Where-Object { $_.PackId -eq $instanceJson.id })
}
function Version-Of([string]$v) { try { [version]$v } catch { [version]"0.0" } }
if (-not $PackVersion) {
    $PackVersion = if ($patches.Count) { ($patches | Sort-Object { Version-Of $_.Version } | Select-Object -Last 1).Version } else { "1.0.0" }
}
$applied = @($patches | Where-Object { (Version-Of $_.Version) -le (Version-Of $PackVersion) } | Sort-Object { Version-Of $_.Version } | ForEach-Object { $_.Version })

if (-not $OutputFile) { $OutputFile = Join-Path $PSScriptRoot "out\$($instanceJson.id)-$PackVersion.zip" }
New-Item -ItemType Directory -Force -Path (Split-Path $OutputFile -Parent) | Out-Null
if (Test-Path $OutputFile) { Remove-Item -LiteralPath $OutputFile -Force }

function Excluded([string]$rel) {
    foreach ($pattern in $Exclude) { if ($rel -like $pattern) { return $true } }
    return $false
}

$zip = [System.IO.Compression.ZipFile]::Open($OutputFile, [System.IO.Compression.ZipArchiveMode]::Create)
$count = 0
$warnings = @()
try {
    function Add-File([string]$path, [string]$entry, $level) {
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $path, $entry, $level)
        $script:count++
    }

    # Mods: enabled jars only (a .jar.disabled is the player's/dev's off switch and is not part of the pack).
    $mods = @()
    foreach ($jar in (Get-ChildItem (Join-Path $gameDir "mods") -Filter "*.jar" -File | Sort-Object Name)) {
        Add-File $jar.FullName "overrides/mods/$($jar.Name)" ([System.IO.Compression.CompressionLevel]::NoCompression) # jars are zips already
        $mods += [ordered]@{ fileName = $jar.Name; size = $jar.Length; sha1 = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA1).Hash.ToLowerInvariant() }
    }

    foreach ($dir in $IncludeDirs) {
        $root = Join-Path $gameDir $dir
        if (!(Test-Path $root)) { continue }
        foreach ($file in (Get-ChildItem $root -Recurse -File -Force)) {
            $rel = $file.FullName.Substring($gameDir.Length).TrimStart('\', '/').Replace('\', '/')
            if (Excluded $rel) { continue }
            Add-File $file.FullName "overrides/$rel" ([System.IO.Compression.CompressionLevel]::Optimal)
            # The archive is meant to be hosted: flag anything in a text config that looks like a credential.
            if ($file.Length -lt 512KB -and $file.Extension -in ".json", ".json5", ".toml", ".cfg", ".properties", ".txt", ".yaml", ".yml") {
                if (Select-String -LiteralPath $file.FullName -Pattern '(?i)(access.?token|password|api.?key|secret)\s*["'']?\s*[=:]\s*["'']?[A-Za-z0-9_\-\.]{10,}' -Quiet) {
                    $warnings += $rel
                }
            }
        }
    }
    foreach ($name in $IncludeFiles) {
        $file = Join-Path $gameDir $name
        if (Test-Path $file) { Add-File $file "overrides/$name" ([System.IO.Compression.CompressionLevel]::Optimal) }
    }
    $icon = Join-Path $InstanceDir "icon.png"
    if (Test-Path $icon) { Add-File $icon "overrides/icon.png" ([System.IO.Compression.CompressionLevel]::Optimal) }
    foreach ($patch in $patches) { Add-File $patch.File.FullName "patches/$($patch.File.Name)" ([System.IO.Compression.CompressionLevel]::Optimal) }

    $packJson = [ordered]@{
        packId = $instanceJson.id
        packVersion = $PackVersion
        createdUtc = (Get-Date).ToUniversalTime().ToString("o")
        mcVersion = $instanceJson.mcVersion
        loader = $instanceJson.loader
        loaderVersion = $instanceJson.loaderVersion
        modCount = $mods.Count
        appliedPatches = $applied
        mods = $mods
    } | ConvertTo-Json -Depth 5
    $entry = $zip.CreateEntry("pack.json", [System.IO.Compression.CompressionLevel]::Optimal)
    $writer = New-Object System.IO.StreamWriter($entry.Open(), (New-Object System.Text.UTF8Encoding($false)))
    try { $writer.Write($packJson) } finally { $writer.Dispose() }
    $count++
} finally {
    $zip.Dispose()
}

$archive = Get-Item -LiteralPath $OutputFile
$sha256 = (Get-FileHash -LiteralPath $OutputFile -Algorithm SHA256).Hash
Write-Host "[OK] $($archive.FullName)" -ForegroundColor Green
Write-Host ("  - {0} files, {1:N0} MB, pack {2}, patches included: {3}" -f $count, ($archive.Length / 1MB), $PackVersion, ($applied -join ", "))
Write-Host "  - SHA-256: $sha256"
foreach ($w in $warnings) { Write-Warning "$w looks like it holds a credential; check it before hosting the archive." }
