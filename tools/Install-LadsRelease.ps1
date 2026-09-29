[CmdletBinding()]
param(
    [string]$SourceDirectory = (Join-Path $PSScriptRoot '../artifacts/client'),
    [string]$InstallDirectory = (Join-Path $env:LOCALAPPDATA 'The Lads Client')
)
$ErrorActionPreference = 'Stop'
$releaseRoot = [IO.Path]::GetFullPath($SourceDirectory)
$clientInstallRoot = [IO.Path]::GetFullPath($InstallDirectory)
if ($clientInstallRoot.TrimEnd('\','/') -eq [IO.Path]::GetPathRoot($clientInstallRoot).TrimEnd('\','/')) { throw 'The installation must be a named directory.' }
if (-not (Test-Path -LiteralPath (Join-Path $releaseRoot 'TheLadsLauncher.exe') -PathType Leaf)) { throw 'No published launcher found.' }
foreach ($gameVersion in @('1.21.11', '26.2')) {
    if (-not (Test-Path -LiteralPath (Join-Path $releaseRoot "game-mods/$gameVersion/theladscore.jar") -PathType Leaf)) { throw "Missing $gameVersion client jar." }
}
New-Item -ItemType Directory -Path $clientInstallRoot -Force | Out-Null
$updateRoot = Join-Path $clientInstallRoot ('.update-backups/' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N'))
$changes = [Collections.Generic.List[object]]::new()
foreach ($releaseFile in Get-ChildItem -LiteralPath $releaseRoot -Recurse -File) {
    $relativePath = [IO.Path]::GetRelativePath($releaseRoot, $releaseFile.FullName)
    if ($relativePath -eq 'settings.json') { continue }
    $target = [IO.Path]::GetFullPath((Join-Path $clientInstallRoot $relativePath))
    $backup = [IO.Path]::GetFullPath((Join-Path $updateRoot ('original/' + $relativePath)))
    $stage = [IO.Path]::GetFullPath((Join-Path $updateRoot ('staged/' + $relativePath)))
    foreach ($candidate in @($target, $backup, $stage)) {
        if (-not $candidate.StartsWith($clientInstallRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Update path escaped the installation directory.' }
    }
    # Verify every existing ancestor before any file move. Never traverse installation junctions.
    foreach ($validatedPath in @($target, $backup, $stage)) {
        $ancestor = $validatedPath
        while ($ancestor.Length -ge $clientInstallRoot.Length) {
            if (Test-Path -LiteralPath $ancestor) {
                if ((Get-Item -LiteralPath $ancestor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Installation path redirects: $ancestor" }
            }
            $ancestor = Split-Path -Path $ancestor -Parent
        }
    }
    $expectedHash = (Get-FileHash -LiteralPath $releaseFile.FullName -Algorithm SHA256).Hash
    $exists = Test-Path -LiteralPath $target -PathType Leaf
    if ($exists -and (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq $expectedHash) { continue }
    New-Item -ItemType Directory -Path (Split-Path -Path $stage -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $releaseFile.FullName -Destination $stage
    if ((Get-FileHash -LiteralPath $stage -Algorithm SHA256).Hash -ne $expectedHash) { throw 'Staged release did not match the source.' }
    $changes.Add([pscustomobject]@{Target=$target;Backup=$backup;Stage=$stage;Hash=$expectedHash;Existed=$exists;Moved=$false;Installed=$false})
}
try {
    foreach ($change in $changes) {
        New-Item -ItemType Directory -Path (Split-Path -Path $change.Target -Parent) -Force | Out-Null
        if ($change.Existed) {
            New-Item -ItemType Directory -Path (Split-Path -Path $change.Backup -Parent) -Force | Out-Null
            # Renaming a loaded Windows image preserves the running process's file mapping.
            # If Windows denies the move, abort and restore earlier files; never stop a user's game.
            Move-Item -LiteralPath $change.Target -Destination $change.Backup
            $change.Moved = $true
        }
        Move-Item -LiteralPath $change.Stage -Destination $change.Target
        $change.Installed = $true
    }
    foreach ($change in $changes) {
        if ((Get-FileHash -LiteralPath $change.Target -Algorithm SHA256).Hash -ne $change.Hash) { throw 'Installed release verification failed.' }
    }
} catch {
    $installFailure = $_
    for ($index = $changes.Count - 1; $index -ge 0; $index--) {
        $change = $changes[$index]
        if ($change.Installed) { Move-Item -LiteralPath $change.Target -Destination $change.Stage }
        if ($change.Moved) { Move-Item -LiteralPath $change.Backup -Destination $change.Target }
    }
    throw $installFailure
}
Write-Output "Installed and verified $($changes.Count) changed files in $clientInstallRoot"
Write-Output "Previous files retained at $updateRoot"
