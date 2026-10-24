[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$probeRoot = Join-Path $repoRoot ('artifacts/update-probe/' + [guid]::NewGuid().ToString('N'))
$oldProbeRoot = $env:LADS_UPDATE_PROBE_ROOT
$oldCorrupt = $env:LADS_UPDATE_PROBE_CORRUPT
$oldKey = $env:LADS_UPDATE_PROBE_KEY
$oldStage = $env:LADS_UPDATE_PROBE_STAGE
# Signs a manifest as tools/Sign-Release.ps1 does, with the throwaway key whose public half the probe trusts.
function Write-SignedManifest([string] $feedDir, $fields) {
    $manifest = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -Compress $fields))
    [IO.File]::WriteAllBytes((Join-Path $feedDir 'lads-update.json'), $manifest)
    [IO.File]::WriteAllBytes((Join-Path $feedDir 'lads-update.json.sig'), $probeKey.SignData($manifest, [Security.Cryptography.HashAlgorithmName]::SHA256))
}
# Starts the packaged probe of a portable layout with LADS_UPDATE_PROBE_STAGE set and returns its <stage>-result.json.
function Invoke-ProbeStage([string] $layout, [string] $stage, [int] $seconds) {
    $env:LADS_UPDATE_PROBE_STAGE = $stage
    $stageExe = Get-ChildItem -LiteralPath $layout -Recurse -Filter UpdateProbe.exe | Select-Object -First 1
    Start-Process -FilePath $stageExe.FullName -WorkingDirectory $stageExe.DirectoryName -WindowStyle Hidden | Out-Null
    $env:LADS_UPDATE_PROBE_STAGE = $null   # the probe (and the version it restarts into) already has it
    $file = Join-Path $probeRoot "$stage-result.json"
    $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
    $stageResult = $null
    while (-not $stageResult -and [DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 500
        try { $stageResult = Get-Content -LiteralPath $file -Raw -ErrorAction Stop | ConvertFrom-Json } catch { }   # missing or half-written
    }
    if (-not $stageResult) { throw "Stage $stage did not finish. Inspect $probeRoot" }
    if (-not $stageResult.passed) { throw "Stage $stage failed: $(Get-Content -LiteralPath $file -Raw)" }
    $stageResult
}
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
    # No contentSha256: manifests signed before content hashes existed must keep verifying.
    Write-SignedManifest (Join-Path $probeRoot 'feed') ([ordered]@{ version = '0.0.2'; package = $fullPackage.Name; sha256 = $sha })
    # 0.0.3 and a tampered "evil" 0.0.3, each packed next to 0.0.2's full package and feed so vpk builds a 0.0.2 -> 0.0.3 delta.
    $app3 = Join-Path $probeRoot '0.0.3'
    & dotnet publish tools/UpdateProbe/UpdateProbe.csproj -c Release -r win-x64 --self-contained true '-p:Version=0.0.3' -o $app3
    if ($LASTEXITCODE -ne 0) { throw 'Probe publish failed.' }
    New-Item -ItemType Directory -Path (Join-Path $app3 'release-notes') -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $app3 'release-notes/0.0.3.md'), 'Third test release.')
    $evilApp = Join-Path $probeRoot '0.0.3-evil'
    Copy-Item -LiteralPath $app3 -Destination $evilApp -Recurse
    [IO.File]::WriteAllText((Join-Path $app3 'payload.txt'), 'third package')
    [IO.File]::WriteAllText((Join-Path $evilApp 'payload.txt'), 'evil')
    $feed3 = Join-Path $probeRoot 'feed3'
    $evilBuild = Join-Path $probeRoot 'evil-build'
    $deltaName = 'LadsUpdateProbe-0.0.3-delta.nupkg'
    $fullName3 = 'LadsUpdateProbe-0.0.3-full.nupkg'
    foreach ($pack in @(@($app3, $feed3), @($evilApp, $evilBuild))) {
        New-Item -ItemType Directory -Path $pack[1] | Out-Null
        Copy-Item -LiteralPath $fullPackage.FullName, (Join-Path $probeRoot 'feed/releases.win.json') -Destination $pack[1]
        & dotnet vpk pack -u LadsUpdateProbe -v 0.0.3 -p $pack[0] -e UpdateProbe.exe --channel win -r win-x64 --releaseNotes (Join-Path $pack[0] 'release-notes/0.0.3.md') -o $pack[1]
        if ($LASTEXITCODE -ne 0) { throw 'Probe pack failed.' }
        if (-not (Test-Path -LiteralPath (Join-Path $pack[1] $deltaName))) { throw "vpk built no 0.0.3 delta in $($pack[1])." }
    }
    # Trim feed3 the way Publish-Release.ps1 trims a real release (launchers read only the latest release's files): only
    # 0.0.3's own packages and entries, so the delta is applied from a feed that no longer lists or holds 0.0.2.
    $own = '-0.0.3-'
    Get-ChildItem -LiteralPath $feed3 -File | Where-Object { $_.Name -match '-(full|delta)\.nupkg$' -and -not $_.Name.Contains($own) } | Remove-Item -Force
    $noBom = New-Object Text.UTF8Encoding $false
    $feedPath = Join-Path $feed3 'releases.win.json'
    $feedJson = [IO.File]::ReadAllText($feedPath) | ConvertFrom-Json
    $feedJson.Assets = @($feedJson.Assets | Where-Object { $_.Version -eq '0.0.3' })
    if ($feedJson.Assets.Count -ne 2) { throw "feed3's releases.win.json must list exactly 0.0.3's full package and delta." }
    [IO.File]::WriteAllText($feedPath, (ConvertTo-Json $feedJson -Depth 10 -Compress), $noBom)
    $legacyPath = Join-Path $feed3 'RELEASES'
    if (Test-Path -LiteralPath $legacyPath) {
        $legacy = @([IO.File]::ReadAllText($legacyPath) -split '(?<=\n)' | Where-Object { $_.Contains($own) })
        if ($legacy.Count -ne 1 -or -not $legacy[0].Contains($fullName3)) { throw "feed3's RELEASES must list exactly 0.0.3's full package." }
        [IO.File]::WriteAllText($legacyPath, (-join $legacy), (New-Object Text.UTF8Encoding $true))
    }
    $full3 = Join-Path $feed3 $fullName3
    $sha3 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([IO.File]::ReadAllBytes($full3)))
    $contentSha3 = & (Join-Path $PSScriptRoot 'Get-PackageContentHash.ps1') -Path $full3
    Write-SignedManifest $feed3 ([ordered]@{ version = '0.0.3'; package = $fullName3; sha256 = $sha3; contentSha256 = $contentSha3 })
    # feed3-evil: someone who can upload release files swaps in a delta that rebuilds "evil" 0.0.3, with a consistent feed entry;
    # the genuine full package and signed manifest stay. feed3-delta: no full package at all, so only the delta can work.
    $evilFeed = Join-Path $probeRoot 'feed3-evil'
    Copy-Item -LiteralPath $feed3 -Destination $evilFeed -Recurse
    Copy-Item -LiteralPath (Join-Path $evilBuild $deltaName) -Destination (Join-Path $evilFeed $deltaName) -Force
    $evilBytes = [IO.File]::ReadAllBytes((Join-Path $evilFeed $deltaName))
    $evilJsonPath = Join-Path $evilFeed 'releases.win.json'
    $evilJson = [IO.File]::ReadAllText($evilJsonPath) | ConvertFrom-Json
    $evilEntry = $evilJson.Assets | Where-Object { $_.FileName -eq $deltaName }
    $evilEntry.SHA1 = [Convert]::ToHexString([Security.Cryptography.SHA1]::HashData($evilBytes))
    $evilEntry.SHA256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($evilBytes))
    $evilEntry.Size = $evilBytes.Length
    [IO.File]::WriteAllText($evilJsonPath, (ConvertTo-Json $evilJson -Depth 10 -Compress), $noBom)
    $deltaFeed = Join-Path $probeRoot 'feed3-delta'
    Copy-Item -LiteralPath $feed3 -Destination $deltaFeed -Recurse
    Remove-Item -LiteralPath (Join-Path $deltaFeed $fullName3)
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
    Wait-Process -Id $result.pid -Timeout 30 -ErrorAction SilentlyContinue   # 0.0.2 has written its result; let it let go of its files
    $portableEvil = Join-Path $probeRoot 'portable-evil'
    Copy-Item -LiteralPath $portable -Destination $portableEvil -Recurse
    $evil = Invoke-ProbeStage $portableEvil 'evil-delta' 90
    if ($evil.version -ne '0.0.3' -or $evil.delta) { throw "Tampered delta did not end on the genuine full package: $probeRoot" }
    $portableOld = Join-Path $probeRoot 'portable-old'
    Expand-Archive -LiteralPath (Join-Path $probeRoot 'old-feed/LadsUpdateProbe-win-Portable.zip') -DestinationPath $portableOld
    $old = Invoke-ProbeStage $portableOld 'old-client' 90
    if ($old.version -ne '0.0.3' -or $old.delta) { throw "Old client did not take the full package: $probeRoot" }
    $delta = Invoke-ProbeStage $portable 'delta' 120
    if ($delta.version -ne '0.0.3' -or -not $delta.delta) { throw "Delta update failed: $probeRoot" }
    $sizes = '{0:N0} KB delta vs {1:N0} KB full' -f ((Get-Item -LiteralPath (Join-Path $feed3 $deltaName)).Length / 1KB), ((Get-Item -LiteralPath $full3).Length / 1KB)
    Write-Output "PASS: corrupt-package rejection, wrongly signed package rejection, real full-package replacement, deferred install, automatic restart, file removal, preserved user data, offline notes, delta update ($sizes), tampered delta fell back to full, old client used full. Evidence: $probeRoot"
} finally {
    $env:LADS_UPDATE_PROBE_ROOT = $oldProbeRoot
    $env:LADS_UPDATE_PROBE_CORRUPT = $oldCorrupt
    $env:LADS_UPDATE_PROBE_KEY = $oldKey
    $env:LADS_UPDATE_PROBE_STAGE = $oldStage
    Pop-Location
}
