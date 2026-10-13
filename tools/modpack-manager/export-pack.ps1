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
    # Default: the newest patch next to this script, else 1.0.0. Patches the instance does not reflect stay pending (see appliedPatches).
    [string]$PackVersion = "",
    [string]$PatchesDir = "$PSScriptRoot\patches",
    [string]$CoreJar = "",
    [string[]]$IncludeDirs = @("config", "defaultconfigs", "configureddefaults", "resourcepacks", "shaderpacks"),
    [string[]]$IncludeFiles = @("servers.dat"),
    # Relative to the game folder, '/' separators; a trailing * matches the rest of the path.
    # resourceful-config-web.json holds a per-install web UI password.
    [string[]]$Exclude = @("config/worldedit/.archive-unpack/*", "config/jei/world/*", "config/resourceful-config-web.json", "config/voicechat/username-cache.json", "*username-cache*", "*hs_err_pid*", "*.mixin.out*", "*.backup", "*.bak", "*.log")
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
        [PSCustomObject]@{ File = $_; Version = $p.version; PackId = $p.packId; Patch = $p }
    } | Where-Object { $_.PackId -eq $instanceJson.id })
}
function Version-Of([string]$v) { try { [version]$v } catch { [version]"0.0" } }
if (-not $PackVersion) {
    $PackVersion = if ($patches.Count) { ($patches | Sort-Object { Version-Of $_.Version } | Select-Object -Last 1).Version } else { "1.0.0" }
}

# A key's value the way ModpackPatchEngine finds it: a TOML "table.path.key" only inside [table.path], any other key on its first line.
function Key-Value([string[]]$lines, [string]$key, [bool]$toml) {
    $from = 0; $to = $lines.Count; $name = $key; $dot = $key.LastIndexOf('.')
    if ($toml -and $dot -gt 0) {
        $header = "[" + $key.Substring(0, $dot) + "]"; $name = $key.Substring($dot + 1)
        $from = [array]::IndexOf(@($lines | ForEach-Object { $_.Trim() }), $header) + 1
        if ($from -eq 0) { return $null }
        for ($to = $from; $to -lt $lines.Count -and -not $lines[$to].TrimStart().StartsWith('['); $to++) { }
    }
    for ($i = $from; $i -lt $to; $i++) { if ($lines[$i] -match "^\s*$([regex]::Escape($name))\s*[=:]\s*(.*)$") { return $matches[1].Trim() } }
    return $null
}

# Why a patch's config is not in the instance yet ($null when it is), checked the way ModpackPatchEngine applies it (Key-Value;
# dotted json paths). A key a later patch sets again is checked there. Mods are not checked: the archive carries the instance's jars, which are the pack's. Keybinds
# neither: options.txt is never exported.
function Patch-Missing($patch, $lastSetter) {
    foreach ($c in @($patch.configPatches)) {
        if (-not $c) { continue }
        $file = Join-Path $gameDir $c.file
        if (!(Test-Path -LiteralPath $file)) { return "$($c.file) does not exist" }
        if ($c.keyValues) {
            $lines = @(Get-Content -LiteralPath $file)
            foreach ($kv in $c.keyValues.PSObject.Properties) {
                if ($lastSetter["$($c.file)|$($kv.Name)"] -ne $patch.version) { continue }
                $value = Key-Value $lines $kv.Name ($c.format -eq "toml")
                if ($value -ne $kv.Value) { return "$($c.file): $($kv.Name) is '$value', the patch sets '$($kv.Value)'" }
            }
        }
        if ($c.set) {
            $json = Get-Content -Raw -LiteralPath $file | ConvertFrom-Json
            foreach ($kv in $c.set.PSObject.Properties) {
                if ($lastSetter["$($c.file)|$($kv.Name)"] -ne $patch.version) { continue }
                $node = $json
                foreach ($part in $kv.Name.Split('.')) { $node = if ($null -ne $node) { $node.$part } }
                if ((ConvertTo-Json $node -Compress -Depth 10) -ne (ConvertTo-Json $kv.Value -Compress -Depth 10)) { return "$($c.file): $($kv.Name) differs" }
            }
        }
    }
    return $null
}

# appliedPatches: the oldest patches the instance already has, in order. The first one it lacks (the dev keeps their own settings,
# e.g. Distant Horizons off) and every later one stay pending: the launcher applies them, oldest first, before the first launch.
$inPack = @($patches | Where-Object { (Version-Of $_.Version) -le (Version-Of $PackVersion) } | Sort-Object { Version-Of $_.Version })
$lastSetter = @{}
foreach ($p in $inPack) {
    foreach ($c in @($p.Patch.configPatches)) {
        foreach ($kv in @($c.keyValues.PSObject.Properties) + @($c.set.PSObject.Properties)) { if ($kv) { $lastSetter["$($c.file)|$($kv.Name)"] = $p.Version } }
    }
}
$applied = @()
foreach ($p in $inPack) {
    $missing = Patch-Missing $p.Patch $lastSetter
    if ($missing) { Write-Warning "Patch $($p.Version) is not in the instance ($missing): it and every later patch stay pending for the launcher."; break }
    $applied += $p.Version
}

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
        $sourcePath = $jar.FullName
        if ($CoreJar -and $jar.Name -eq "theladscore.jar" -and (Test-Path -LiteralPath $CoreJar)) {
            $sourcePath = (Get-Item -LiteralPath $CoreJar).FullName
        }
        $fileObj = Get-Item -LiteralPath $sourcePath
        Add-File $fileObj.FullName "overrides/mods/$($jar.Name)" ([System.IO.Compression.CompressionLevel]::NoCompression) # jars are zips already
        $mods += [ordered]@{ fileName = $jar.Name; size = $fileObj.Length; sha1 = (Get-FileHash -LiteralPath $fileObj.FullName -Algorithm SHA1).Hash.ToLowerInvariant() }
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
        if ([string]::IsNullOrWhiteSpace($name)) { continue }
        $file = Join-Path $gameDir $name
        if (Test-Path -LiteralPath $file -PathType Leaf) { Add-File $file "overrides/$name" ([System.IO.Compression.CompressionLevel]::Optimal) }
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
