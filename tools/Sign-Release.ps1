# Signs a CI-built release draft so launchers install it (TheLadsLauncher/UpdateSignature.cs). Run on the owner's PC after
# verifying the draft and before publishing it: the private key never goes to GitHub, so a release made by anyone else
# (a stolen GitHub login, a compromised CI action) has no valid signature and every launcher refuses it.
# Also refuses to sign a draft whose -delta.nupkg does not rebuild (from the previous release) into the signed full package.
#   ./tools/Sign-Release.ps1 -Version 1.9.10 [-Key $HOME\Documents\TheLads-release-signing-key.pem]
[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)] [string] $Version,
    [string] $Key = $(if ($env:LADS_SIGNING_KEY_PATH) { $env:LADS_SIGNING_KEY_PATH } else { Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'TheLads-release-signing-key.pem' }),
    [string] $Repo = 'ArashYT/TheLadsClient'
)
$ErrorActionPreference = 'Stop'
$tag = "v$Version"
$work = Join-Path ([IO.Path]::GetTempPath()) ("lads-sign-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work -Force -WhatIf:$false | Out-Null
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root   # `dotnet vpk` is a local tool: it needs the repo's .config/dotnet-tools.json
try {
    # 1. Expected tag check against local git
    $tagCommit = & git rev-parse --verify "refs/tags/${tag}^{commit}" 2>$null
    if (-not $tagCommit) {
        $tagCommit = & git rev-parse --verify "${tag}^{commit}" 2>$null
    }
    if ($LASTEXITCODE -ne 0 -or -not $tagCommit -or $tagCommit -notmatch '^[0-9a-f]{40}$') {
        throw "Expected tag $tag was not found in the local repository."
    }

    # 2. Download CI build record and verify commit and version
    gh release download $tag --repo $Repo --pattern 'release-build.json' --dir $work
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $work 'release-build.json'))) {
        throw "Could not download release-build.json from the $tag release draft."
    }
    $buildRecord = Get-Content -LiteralPath (Join-Path $work 'release-build.json') -Raw | ConvertFrom-Json
    if ($buildRecord.commit -ne $tagCommit) {
        throw "Build record commit ($($buildRecord.commit)) does not match expected tag $tag commit ($tagCommit)."
    }
    if ($buildRecord.version -ne $Version) {
        throw "Build record version ($($buildRecord.version)) does not match expected version $Version."
    }
    if ($buildRecord.localChanges) {
        throw "Build record indicates the release was built from a dirty working tree."
    }
    if ($buildRecord.PSObject.Properties['coreBuilt'] -and -not $buildRecord.coreBuilt) {
        throw "Build record indicates the core build was skipped."
    }

    $package = "TheLadsClient-$Version-full.nupkg"
    gh release download $tag --repo $Repo --pattern $package --dir $work
    if ($LASTEXITCODE -ne 0) { throw "Could not download $package from the $tag release." }
    $path = Join-Path $work $package
    $sha = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([IO.File]::ReadAllBytes($path)))

    # 3. Verify downloaded assets against build record hashes
    if ($buildRecord.hashes) {
        $expectedHash = $buildRecord.hashes.PSObject.Properties[$package]?.Value
        if (-not $expectedHash) {
            throw "Build record hashes do not list $package."
        }
        if ($sha.ToLowerInvariant() -ne $expectedHash.ToLowerInvariant()) {
            throw "Downloaded package $package hash ($sha) does not match build record hash ($expectedHash)."
        }
    }

    $contentSha = & (Join-Path $PSScriptRoot 'Get-PackageContentHash.ps1') -Path $path
    $deltaName = "TheLadsClient-$Version-delta.nupkg"
    $assets = gh release view $tag --repo $Repo --json assets --jq '.assets[].name'
    if ($LASTEXITCODE -ne 0) { throw "Could not list the assets of the $tag release." }
    if ($assets -notcontains $package) { throw "Asset list of $tag lacks $package; cannot decide whether a delta exists." }
    if ($assets -contains $deltaName) {
        # Launchers rebuild the full package from the previous version plus this delta: it must come out as the package signed here.
        $refuse = "The v$Version delta does not rebuild the signed package; not signed."
        $prev = (gh release view --repo $Repo --json tagName --jq .tagName)
        if ($LASTEXITCODE -ne 0 -or -not $prev) { throw 'Could not find the latest published release (the delta base).' }
        $prev = $prev -replace '^v', ''
        $prevPackage = "TheLadsClient-$prev-full.nupkg"
        $base = Join-Path $root "artifacts/release-check-$prev/$prevPackage"
        if (-not (Test-Path -LiteralPath $base)) {
            gh release download "v$prev" --repo $Repo --pattern $prevPackage --dir $work
            if ($LASTEXITCODE -ne 0) { throw "Could not download $prevPackage from the v$prev release." }
            $base = Join-Path $work $prevPackage
        }
        gh release download $tag --repo $Repo --pattern $deltaName --dir $work
        if ($LASTEXITCODE -ne 0) { throw "Could not download $deltaName from the $tag release." }
        if ($buildRecord.hashes -and $buildRecord.hashes.PSObject.Properties[$deltaName]) {
            $deltaSha = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([IO.File]::ReadAllBytes((Join-Path $work $deltaName))))
            $expectedDeltaHash = $buildRecord.hashes.PSObject.Properties[$deltaName].Value
            if ($deltaSha.ToLowerInvariant() -ne $expectedDeltaHash.ToLowerInvariant()) {
                throw "Downloaded delta $deltaName hash ($deltaSha) does not match build record hash ($expectedDeltaHash)."
            }
        }
        $rebuilt = Join-Path $work 'rebuilt.nupkg'
        dotnet vpk delta patch --base $base --patch (Join-Path $work $deltaName) --output $rebuilt
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $rebuilt)) { throw $refuse }
        if ((& (Join-Path $PSScriptRoot 'Get-PackageContentHash.ps1') -Path $rebuilt) -ne $contentSha) { throw $refuse }
    }

    # Print summary of commits in this release for confirmation
    $prevTag = git describe --tags --abbrev=0 "${tag}^" 2>$null
    if ($prevTag) {
        Write-Host "Commits in $tag since ${prevTag}:"
        git log "$prevTag..$tag" --oneline
    }

    $manifest = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -Compress ([ordered]@{ version = $Version; package = $package; sha256 = $sha; contentSha256 = $contentSha })))
    $ecdsa = [Security.Cryptography.ECDsa]::Create()
    try {
        $ecdsa.ImportFromPem([IO.File]::ReadAllText($Key))
        $signature = $ecdsa.SignData($manifest, [Security.Cryptography.HashAlgorithmName]::SHA256)
        if (-not $ecdsa.VerifyData($manifest, $signature, [Security.Cryptography.HashAlgorithmName]::SHA256)) { throw 'Signature self-check failed.' }

        # Download and verify SHA256SUMS.txt, then sign it
        $sumsName = 'SHA256SUMS.txt'
        gh release download $tag --repo $Repo --pattern $sumsName --dir $work
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $work $sumsName))) {
            throw "Could not download $sumsName from the $tag release draft."
        }
        $sumsPath = Join-Path $work $sumsName
        $sumsLines = Get-Content -LiteralPath $sumsPath
        $sumsMap = @{}
        foreach ($line in $sumsLines) {
            if ($line -match '^([a-fA-F0-9]{64})\s+(.+)$') {
                $sumsMap[$Matches[2].Trim()] = $Matches[1].ToLowerInvariant()
            }
        }
        if (-not $sumsMap.ContainsKey($package)) {
            throw "$sumsName does not list $package."
        }
        if ($sumsMap[$package] -ne $sha.ToLowerInvariant()) {
            throw "Hash of $package in $sumsName ($($sumsMap[$package])) does not match downloaded package hash ($sha)."
        }
        if ($buildRecord.hashes) {
            foreach ($prop in $buildRecord.hashes.PSObject.Properties) {
                $fName = $prop.Name
                $expectedH = $prop.Value.ToLowerInvariant()
                if ($sumsMap.ContainsKey($fName) -and $sumsMap[$fName] -ne $expectedH) {
                    throw "Hash mismatch for $fName between release-build.json ($expectedH) and $sumsName ($($sumsMap[$fName]))."
                }
            }
        }
        $sumsBytes = [IO.File]::ReadAllBytes($sumsPath)
        $sumsSig = $ecdsa.SignData($sumsBytes, [Security.Cryptography.HashAlgorithmName]::SHA256)
        if (-not $ecdsa.VerifyData($sumsBytes, $sumsSig, [Security.Cryptography.HashAlgorithmName]::SHA256)) {
            throw "$sumsName signature self-check failed."
        }
        [IO.File]::WriteAllBytes((Join-Path $work "$sumsName.sig"), $sumsSig)
    } finally { $ecdsa.Dispose() }

    [IO.File]::WriteAllBytes((Join-Path $work 'lads-update.json'), $manifest)
    [IO.File]::WriteAllBytes((Join-Path $work 'lads-update.json.sig'), $signature)

    if ($PSCmdlet.ShouldProcess($tag, "Upload signatures to release")) {
        gh release upload $tag (Join-Path $work 'lads-update.json') (Join-Path $work 'lads-update.json.sig') (Join-Path $work 'SHA256SUMS.txt.sig') --repo $Repo --clobber
        if ($LASTEXITCODE -ne 0) { throw 'Upload failed.' }
    }
    "Signed $tag : $package sha256 $sha contentSha256 $contentSha and SHA256SUMS.txt"
} finally { Pop-Location; if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force -WhatIf:$false } }