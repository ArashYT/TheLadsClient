# SHA-256 over every entry of a Velopack .nupkg (name + content), ignoring zip container bytes: the same package rebuilt from a
# delta has different bytes but the same entries. Must stay byte-identical to UpdateSignature.ContentHash in the launcher.
#   ./tools/Get-PackageContentHash.ps1 -Path TheLadsClient-1.9.17-full.nupkg
param([Parameter(Mandatory)] [string] $Path)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$file = (Resolve-Path -LiteralPath $Path).ProviderPath
$zip = [IO.Compression.ZipFile]::OpenRead($file)
try {
    $lines = [Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal)
    foreach ($entry in $zip.Entries) {
        if ($lines.ContainsKey($entry.FullName)) { throw [IO.InvalidDataException]::new("Duplicate entry $($entry.FullName) in $([IO.Path]::GetFileName($file)).") }
        $stream = $entry.Open()
        try { $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant() } finally { $stream.Dispose() }
        $lines[$entry.FullName] = "$($entry.FullName)`t$hash`n"
    }
    $names = [string[]]$lines.Keys
    [Array]::Sort($names, [StringComparer]::Ordinal)
    $text = -join ($names | ForEach-Object { $lines[$_] })
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.UTF8Encoding]::new($false).GetBytes($text)))
} finally { $zip.Dispose() }
