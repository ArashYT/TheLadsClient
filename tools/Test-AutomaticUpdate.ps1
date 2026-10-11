[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$probeRoot = Join-Path $repoRoot ('artifacts/update-probe/' + [guid]::NewGuid().ToString('N'))
$oldProbeRoot = $env:LADS_UPDATE_PROBE_ROOT
$oldCorrupt = $env:LADS_UPDATE_PROBE_CORRUPT
Push-Location -LiteralPath $repoRoot
try {
    New-Item -ItemType Directory -Path $probeRoot -Force | Out-Null
    & dotnet tool restore
    if ($LASTEXITCODE -ne 0) { throw 'Tool restore failed.' }
    foreach ($version in @('0.0.1', '0.0.2')) {
        $app = Join-Path $probeRoot $version
        $feed = Join-Path $probeRoot $(if ($version -eq '0.0.1') { 'old-feed' } else { 'feed' })
        & dotnet publish tools/UpdateProbe/UpdateProbe.csproj -c Release -r win-x64 --self-contained true "-p:Version=$version" -o $app
        if ($LASTEXITCODE -ne 0) { throw 'Probe publish failed.' }
        New-Item -ItemType Directory -Path (Join-Path $app 'release-notes') -Force | Out-Null
        [IO.File]::WriteAllText((Join-Path $app 'release-notes/0.0.1.md'), 'First test release.')
        if ($version -eq '0.0.1') {
            [IO.File]::WriteAllText((Join-Path $app 'payload.txt'), 'first package')
            [IO.File]::WriteAllText((Join-Path $app 'obsolete.txt'), 'removed in second package')
        } else {
            [IO.File]::WriteAllText((Join-Path $app 'payload.txt'), 'second package')
            [IO.File]::WriteAllText((Join-Path $app 'release-notes/0.0.2.md'), 'Second test release.')
        }
        & dotnet vpk pack -u LadsUpdateProbe -v $version -p $app -e UpdateProbe.exe --channel win -r win-x64 --releaseNotes (Join-Path $app "release-notes/$version.md") -o $feed
        if ($LASTEXITCODE -ne 0) { throw 'Probe pack failed.' }
    }
    # Portable Velopack layout exercises the real external updater without registering
    # an installed app or touching desktop shortcuts/the user's client installation.
    $portable = Join-Path $probeRoot 'portable'
    Expand-Archive -LiteralPath (Join-Path $probeRoot 'old-feed/LadsUpdateProbe-win-Portable.zip') -DestinationPath $portable
    [IO.File]::WriteAllText((Join-Path $probeRoot 'user-settings.json'), 'preserve me')
    $env:LADS_UPDATE_PROBE_ROOT = $probeRoot
    $exe = Get-ChildItem -LiteralPath $portable -Recurse -Filter UpdateProbe.exe | Select-Object -First 1
    if (-not $exe) { throw 'Packaged probe executable missing.' }
    # Sign 0.0.2 as tools/Sign-Release.ps1 does, with a throwaway key whose public half the probe trusts.
    $probeKey = [Security.Cryptography.ECDsa]::Create([Security.Cryptography.ECCurve+NamedCurves]::nistP256)
    $fullPackage = Get-ChildItem -LiteralPath (Join-Path $probeRoot 'feed') -Filter '*-0.0.2-full.nupkg' | Select-Object -First 1
    $sha = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([IO.File]::ReadAllBytes($fullPackage.FullName)))
    $manifest = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -Compress ([ordered]@{ version = '0.0.2'; package = $fullPackage.Name; sha256 = $sha })))
    [IO.File]::WriteAllBytes((Join-Path $probeRoot 'feed/lads-update.json'), $manifest)
    [IO.File]::WriteAllBytes((Join-Path $probeRoot 'feed/lads-update.json.sig'), $probeKey.SignData($manifest, [Security.Cryptography.HashAlgorithmName]::SHA256))
    $oldKey = $env:LADS_UPDATE_PROBE_KEY
    # A release signed by someone else's key (here: the probe trusts a different one) must be refused and its package removed.
    $env:LADS_UPDATE_PROBE_KEY = [Convert]::ToBase64String([Security.Cryptography.ECDsa]::Create([Security.Cryptography.ECCurve+NamedCurves]::nistP256).ExportSubjectPublicKeyInfo())
    $env:LADS_UPDATE_PROBE_CORRUPT = 'unsigned'
    $unsignedProcess = Start-Process -FilePath $exe.FullName -WorkingDirectory $exe.DirectoryName -WindowStyle Hidden -PassThru
    if (-not $unsignedProcess.WaitForExit(30000)) { throw "Unsigned-package probe timed out: $probeRoot" }
    $unsignedResult = Join-Path $probeRoot 'corrupt-unsigned-result.json'
    if (-not (Test-Path -LiteralPath $unsignedResult) -or -not (Get-Content -LiteralPath $unsignedResult -Raw | ConvertFrom-Json).passed) { throw "Wrongly signed package was not safely rejected: $probeRoot" }
    $env:LADS_UPDATE_PROBE_KEY = [Convert]::ToBase64String($probeKey.ExportSubjectPublicKeyInfo())
    $badFeed = Join-Path $probeRoot 'bad-feed'
    Copy-Item -LiteralPath (Join-Path $probeRoot 'feed') -Destination $badFeed -Recurse
    $badPackage = Get-ChildItem -LiteralPath $badFeed -Filter '*-full.nupkg' | Select-Object -First 1
    $stream = [IO.File]::OpenWrite($badPackage.FullName)
    try { $stream.WriteByte(0) } finally { $stream.Dispose() }
    $env:LADS_UPDATE_PROBE_CORRUPT = '1'
    $badProcess = Start-Process -FilePath $exe.FullName -WorkingDirectory $exe.DirectoryName -WindowStyle Hidden -PassThru
    if (-not $badProcess.WaitForExit(30000)) { throw "Corrupt-package probe timed out: $probeRoot" }
    $badResult = Join-Path $probeRoot 'corrupt-1-result.json'
    if (-not (Test-Path -LiteralPath $badResult) -or -not (Get-Content -LiteralPath $badResult -Raw | ConvertFrom-Json).passed) { throw "Corrupt package was not safely rejected: $probeRoot" }
    $env:LADS_UPDATE_PROBE_CORRUPT = $null
    $process = Start-Process -FilePath $exe.FullName -WorkingDirectory $exe.DirectoryName -WindowStyle Hidden -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    $resultFile = Join-Path $probeRoot 'result.json'
    while (-not (Test-Path -LiteralPath $resultFile) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 500 }
    if (-not (Test-Path -LiteralPath $resultFile)) { throw "Update did not finish. Inspect $probeRoot" }
    $result = Get-Content -LiteralPath $resultFile -Raw | ConvertFrom-Json
    if (-not $result.passed -or $result.pid -eq $process.Id) { throw "Automatic update failed: $(Get-Content -LiteralPath $resultFile -Raw)" }
    if (-not (Test-Path -LiteralPath (Join-Path $probeRoot 'deferred.txt'))) { throw 'Idle deferral was not exercised.' }
    Write-Output "PASS: corrupt-package rejection, wrongly signed package rejection, real full-package replacement, deferred install, automatic restart, file removal, preserved user data, and offline notes. Evidence: $resultFile"
} finally {
    $env:LADS_UPDATE_PROBE_ROOT = $oldProbeRoot
    $env:LADS_UPDATE_PROBE_CORRUPT = $oldCorrupt
    $env:LADS_UPDATE_PROBE_KEY = $oldKey
    Pop-Location
}
