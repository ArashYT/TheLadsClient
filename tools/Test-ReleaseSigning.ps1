# Tests Sign-Release.ps1 and Assert-ReleaseSignature (from Publish-Release.ps1)
# with a throwaway ECDSA P-256 key pair and a stubbed gh command on local fixture files.
# Exercises: valid signing/assertion, wrong key, wrong version, missing .sig,
# tampered package, tampered SHA256SUMS, attestation verification, and -WhatIf dry run.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location -LiteralPath $repoRoot
$testTag = 'v9.99.99'
$testVersion = '9.99.99'
$testWork = Join-Path ([IO.Path]::GetTempPath()) ("lads-test-rel-" + [guid]::NewGuid().ToString('N'))
$mockRelease = Join-Path $testWork 'mock-release'
$origKeyEnv = $env:LADS_SIGNING_KEY_PATH
$origSigEnv = $env:LADS_UPDATE_SIGNATURE_PATH
$tagCreated = $false

try {
    New-Item -ItemType Directory -Path $testWork, $mockRelease -Force -WhatIf:$false | Out-Null

    # 1. Generate throwaway ECDSA P-256 keys
    $ephemeralKey = [Security.Cryptography.ECDsa]::Create([Security.Cryptography.ECCurve+NamedCurves]::nistP256)
    $keyPem = $ephemeralKey.ExportECPrivateKeyPem()
    $keyPemPath = Join-Path $testWork 'test-signing-key.pem'
    [IO.File]::WriteAllText($keyPemPath, $keyPem)

    $pubBytes = $ephemeralKey.ExportSubjectPublicKeyInfo()
    $pubBase64 = [Convert]::ToBase64String($pubBytes)

    # Generate a wrong key for negative testing
    $wrongKey = [Security.Cryptography.ECDsa]::Create([Security.Cryptography.ECCurve+NamedCurves]::nistP256)
    $wrongPubBase64 = [Convert]::ToBase64String($wrongKey.ExportSubjectPublicKeyInfo())

    # Create test UpdateSignature.cs files
    $sigCsValid = @"
namespace TheLadsLauncher;
public static class UpdateSignature {
    public static System.Collections.Generic.IReadOnlyList<string> ReleasePublicKeys { get; } = new[] {
        "$pubBase64",
    };
}
"@
    $sigCsValidPath = Join-Path $testWork 'UpdateSignature.Valid.cs'
    [IO.File]::WriteAllText($sigCsValidPath, $sigCsValid)

    $sigCsWrong = @"
namespace TheLadsLauncher;
public static class UpdateSignature {
    public static System.Collections.Generic.IReadOnlyList<string> ReleasePublicKeys { get; } = new[] {
        "$wrongPubBase64",
    };
}
"@
    $sigCsWrongPath = Join-Path $testWork 'UpdateSignature.Wrong.cs'
    [IO.File]::WriteAllText($sigCsWrongPath, $sigCsWrong)

    $env:LADS_SIGNING_KEY_PATH = $keyPemPath
    $env:LADS_UPDATE_SIGNATURE_PATH = $sigCsValidPath

    # Create local git tag for the test version
    $headCommit = (git rev-parse HEAD).Trim()
    git tag -f $testTag HEAD 2>&1 | Out-Null
    $tagCreated = $true

    # Mock state variables for stubbed gh
    $global:mockReleaseDir = $mockRelease
    $global:mockAttestationCommit = $headCommit
    $global:mockAttestationFails = $false
    $global:mockReleasePublished = $false

    function script:gh {
        param([Parameter(ValueFromRemainingArguments)]$ArgsList)
        $cmd = $ArgsList -join ' '
        $global:LASTEXITCODE = 0

        if ($ArgsList[0] -eq 'release') {
            $sub = $ArgsList[1]
            if ($sub -eq 'download') {
                # gh release download <tag> --repo <repo> --pattern <pat> --dir <dir>
                $pat = $null
                $dir = $null
                for ($i = 2; $i -lt $ArgsList.Count; $i++) {
                    if ($ArgsList[$i] -eq '--pattern' -and $i + 1 -lt $ArgsList.Count) { $pat = $ArgsList[$i+1] }
                    if ($ArgsList[$i] -eq '--dir' -and $i + 1 -lt $ArgsList.Count) { $dir = $ArgsList[$i+1] }
                }
                if (-not $dir) { $dir = (Get-Location).Path }
                if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force -WhatIf:$false | Out-Null }
                $searchPattern = if ($pat) { $pat } else { '*' }
                $matched = @(Get-ChildItem -LiteralPath $global:mockReleaseDir -Filter $searchPattern -File)
                if ($matched.Count -eq 0 -and $pat -and -not $pat.Contains('*')) {
                    $global:LASTEXITCODE = 1
                    return
                }
                foreach ($f in $matched) {
                    Copy-Item -LiteralPath $f.FullName -Destination (Join-Path $dir $f.Name) -Force -WhatIf:$false
                }
                return
            }
            if ($sub -eq 'view') {
                # gh release view [<tag>] --repo <repo> --json ...
                $hasJqAssets = $cmd -match '--jq\s+\.assets\[\]\.name'
                $hasJqTag = $cmd -match '--jq\s+\.tagName'
                $isAssetsJson = $cmd -match '--json\s+assets'
                $isDraftJson = $cmd -match '--json\s+isDraft,assets'

                if ($hasJqAssets) {
                    $files = Get-ChildItem -LiteralPath $global:mockReleaseDir -File
                    foreach ($f in $files) { Write-Output $f.Name }
                    return
                }
                if ($hasJqTag) {
                    Write-Output ''
                    return
                }
                if ($isDraftJson) {
                    $files = @(Get-ChildItem -LiteralPath $global:mockReleaseDir -File | ForEach-Object { @{ name = $_.Name; size = $_.Length } })
                    Write-Output (ConvertTo-Json -Compress @{ isDraft = $true; assets = $files })
                    return
                }
                if ($isAssetsJson) {
                    $files = @(Get-ChildItem -LiteralPath $global:mockReleaseDir -File | ForEach-Object { @{ name = $_.Name; size = $_.Length } })
                    Write-Output (ConvertTo-Json -Compress @{ assets = $files })
                    return
                }
                return
            }
            if ($sub -eq 'upload') {
                # gh release upload <tag> <files...> --repo <repo> --clobber
                for ($i = 2; $i -lt $ArgsList.Count; $i++) {
                    $arg = $ArgsList[$i]
                    if ($arg -match '^[A-Za-z0-9\._\\/:-]+$' -and (Test-Path -LiteralPath $arg -PathType Leaf)) {
                        Copy-Item -LiteralPath $arg -Destination (Join-Path $global:mockReleaseDir (Split-Path -Leaf $arg)) -Force -WhatIf:$false
                    }
                }
                return
            }
            if ($sub -eq 'edit') {
                # gh release edit <tag> --repo <repo> --draft=false --latest
                $global:mockReleasePublished = $true
                return
            }
        }
        if ($ArgsList[0] -eq 'attestation' -and $ArgsList[1] -eq 'verify') {
            if ($global:mockAttestationFails) {
                $global:LASTEXITCODE = 1
                Write-Error "Attestation verification failed by mock rule."
                return
            }
            # Output valid attestation JSON matching GitHub CLI schema
            $attestationJson = @"
[
  {
    "attestation": {},
    "verificationResult": {
      "signature": {
        "certificate": {
          "sourceRepositoryDigest": "$($global:mockAttestationCommit)",
          "sourceRepositoryRef": "refs/tags/$testTag"
        }
      }
    }
  }
]
"@
            Write-Output $attestationJson
            return
        }
    }

    # Extract Assert-ReleaseSignature AST from Publish-Release.ps1
    $pubCode = Get-Content -LiteralPath (Join-Path $repoRoot 'Publish-Release.ps1') -Raw
    $ast = [System.Management.Automation.Language.Parser]::ParseInput($pubCode, [ref]$null, [ref]$null)
    $funcAst = $ast.Find({ $args[0] -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $args[0].Name -eq 'Assert-ReleaseSignature' }, $true)
    if (-not $funcAst) { throw 'Assert-ReleaseSignature function not found in Publish-Release.ps1.' }
    . ([scriptblock]::Create($funcAst.Extent.Text))

    # Helper to generate fresh mock release fixture assets
    function Reset-MockReleaseFixtures() {
        Get-ChildItem -LiteralPath $mockRelease -File | Remove-Item -Force -WhatIf:$false

        $fullPkgName = "TheLadsClient-$testVersion-full.nupkg"
        $pkgPath = Join-Path $mockRelease $fullPkgName
        $zipTemp = Join-Path $testWork 'zip-temp'
        New-Item -ItemType Directory -Path $zipTemp -Force -WhatIf:$false | Out-Null
        [IO.File]::WriteAllText((Join-Path $zipTemp 'test-entry.txt'), 'Hello Lads Release')
        if (Test-Path -LiteralPath $pkgPath) { Remove-Item -LiteralPath $pkgPath -Force -WhatIf:$false }
        [IO.Compression.ZipFile]::CreateFromDirectory($zipTemp, $pkgPath)
        Remove-Item -LiteralPath $zipTemp -Recurse -Force -WhatIf:$false

        $setupName = 'TheLadsClient-win-Setup.exe'
        $setupPath = Join-Path $mockRelease $setupName
        [IO.File]::WriteAllBytes($setupPath, [Text.Encoding]::UTF8.GetBytes('MZ-Setup-Mock-Payload'))

        $portableName = 'TheLadsClient-win-Portable.zip'
        $portablePath = Join-Path $mockRelease $portableName
        $pTemp = Join-Path $testWork 'p-temp'
        New-Item -ItemType Directory -Path $pTemp -Force -WhatIf:$false | Out-Null
        [IO.File]::WriteAllText((Join-Path $pTemp 'portable.txt'), 'Portable Payload')
        if (Test-Path -LiteralPath $portablePath) { Remove-Item -LiteralPath $portablePath -Force -WhatIf:$false }
        [IO.Compression.ZipFile]::CreateFromDirectory($pTemp, $portablePath)
        Remove-Item -LiteralPath $pTemp -Recurse -Force -WhatIf:$false

        $pkgSha = (Get-FileHash -LiteralPath $pkgPath -Algorithm SHA256).Hash.ToLowerInvariant()
        $setupSha = (Get-FileHash -LiteralPath $setupPath -Algorithm SHA256).Hash.ToLowerInvariant()
        $portableSha = (Get-FileHash -LiteralPath $portablePath -Algorithm SHA256).Hash.ToLowerInvariant()

        $hashes = [ordered]@{
            $fullPkgName = $pkgSha
            $setupName = $setupSha
            $portableName = $portableSha
        }

        $buildJson = [ordered]@{
            version = $testVersion
            commit = $headCommit
            localChanges = $false
            coreBuilt = $true
            verifiesContentHash = $true
            hashes = $hashes
        }
        [IO.File]::WriteAllText((Join-Path $mockRelease 'release-build.json'), (ConvertTo-Json $buildJson -Depth 5))

        $sumsLines = @(
            "$pkgSha  $fullPkgName",
            "$setupSha  $setupName",
            "$portableSha  $portableName",
            "$((Get-FileHash -LiteralPath (Join-Path $mockRelease 'release-build.json') -Algorithm SHA256).Hash.ToLowerInvariant())  release-build.json"
        )
        [IO.File]::WriteAllLines((Join-Path $mockRelease 'SHA256SUMS.txt'), $sumsLines)

        $global:mockAttestationCommit = $headCommit
        $global:mockAttestationFails = $false
    }

    Write-Host "=== TEST 1: Valid Sign-Release.ps1 execution ==="
    Reset-MockReleaseFixtures
    $signScript = Join-Path $repoRoot 'tools/Sign-Release.ps1'
    $signOutput = & $signScript -Version $testVersion -Key $keyPemPath -Repo 'test/repo'
    Write-Host "Sign output: $signOutput"
    if (-not (Test-Path -LiteralPath (Join-Path $mockRelease 'lads-update.json'))) { throw 'Test 1 failed: lads-update.json missing.' }
    if (-not (Test-Path -LiteralPath (Join-Path $mockRelease 'lads-update.json.sig'))) { throw 'Test 1 failed: lads-update.json.sig missing.' }
    if (-not (Test-Path -LiteralPath (Join-Path $mockRelease 'SHA256SUMS.txt.sig'))) { throw 'Test 1 failed: SHA256SUMS.txt.sig missing.' }
    Write-Host "PASS: Test 1 (Valid signing succeeded)"

    Write-Host "`n=== TEST 2: Valid Assert-ReleaseSignature verification ==="
    $verifyWork = Join-Path $testWork 'verify-work'
    New-Item -ItemType Directory -Path $verifyWork -Force -WhatIf:$false | Out-Null
    Assert-ReleaseSignature -Tag $testTag -Repo 'test/repo' -Version $testVersion -WorkDir $verifyWork
    Write-Host "PASS: Test 2 (Assert-ReleaseSignature passed on valid signatures)"

    Write-Host "`n=== TEST 3: Sign-Release.ps1 -WhatIf dry run ==="
    Reset-MockReleaseFixtures
    & $signScript -Version $testVersion -Key $keyPemPath -Repo 'test/repo' -WhatIf
    if (Test-Path -LiteralPath (Join-Path $mockRelease 'lads-update.json')) { throw 'Test 3 failed: -WhatIf uploaded lads-update.json' }
    if (Test-Path -LiteralPath (Join-Path $mockRelease 'SHA256SUMS.txt.sig')) { throw 'Test 3 failed: -WhatIf uploaded SHA256SUMS.txt.sig' }
    Write-Host "PASS: Test 3 (WhatIf verified draft without loading key or uploading)"

    Write-Host "`n=== TEST 4: Wrong key rejection ==="
    Reset-MockReleaseFixtures
    & $signScript -Version $testVersion -Key $keyPemPath -Repo 'test/repo' | Out-Null
    $env:LADS_UPDATE_SIGNATURE_PATH = $sigCsWrongPath
    $wrongKeyThrew = $false
    try {
        Assert-ReleaseSignature -Tag $testTag -Repo 'test/repo' -Version $testVersion -WorkDir $verifyWork
    } catch {
        $wrongKeyThrew = $true
        Write-Host "Caught expected error: $($_.Exception.Message)"
        if ($_.Exception.Message -notmatch 'does not verify against any pinned public key') {
            throw "Unexpected error message for wrong key: $($_.Exception.Message)"
        }
    }
    if (-not $wrongKeyThrew) { throw 'Test 4 failed: Assert-ReleaseSignature did not reject wrong key.' }
    $env:LADS_UPDATE_SIGNATURE_PATH = $sigCsValidPath
    Write-Host "PASS: Test 4 (Wrong key rejected)"

    Write-Host "`n=== TEST 5: Wrong version in signature manifest rejection ==="
    Reset-MockReleaseFixtures
    & $signScript -Version $testVersion -Key $keyPemPath -Repo 'test/repo' | Out-Null
    # Forge a manifest with version mismatch and sign it with the valid key
    $fullPkgName = "TheLadsClient-$testVersion-full.nupkg"
    $pkgSha = (Get-FileHash -LiteralPath (Join-Path $mockRelease $fullPkgName) -Algorithm SHA256).Hash.ToLowerInvariant()
    $badManifest = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -Compress ([ordered]@{ version = '1.0.0'; package = $fullPkgName; sha256 = $pkgSha })))
    $badSig = $ephemeralKey.SignData($badManifest, [Security.Cryptography.HashAlgorithmName]::SHA256)
    [IO.File]::WriteAllBytes((Join-Path $mockRelease 'lads-update.json'), $badManifest)
    [IO.File]::WriteAllBytes((Join-Path $mockRelease 'lads-update.json.sig'), $badSig)
    $versionThrew = $false
    try {
        Assert-ReleaseSignature -Tag $testTag -Repo 'test/repo' -Version $testVersion -WorkDir $verifyWork
    } catch {
        $versionThrew = $true
        Write-Host "Caught expected error: $($_.Exception.Message)"
        if ($_.Exception.Message -notmatch 'signature manifest is for version 1.0.0, expected 9.99.99') {
            throw "Unexpected error message: $($_.Exception.Message)"
        }
    }
    if (-not $versionThrew) { throw 'Test 5 failed: Assert-ReleaseSignature did not reject version mismatch.' }
    Write-Host "PASS: Test 5 (Wrong version rejected)"

    Write-Host "`n=== TEST 6: Missing signature file rejection ==="
    Reset-MockReleaseFixtures
    & $signScript -Version $testVersion -Key $keyPemPath -Repo 'test/repo' | Out-Null
    # 6a: Missing lads-update.json.sig
    Remove-Item -LiteralPath (Join-Path $mockRelease 'lads-update.json.sig') -Force
    $missingSigThrew = $false
    try {
        Assert-ReleaseSignature -Tag $testTag -Repo 'test/repo' -Version $testVersion -WorkDir $verifyWork
    } catch {
        $missingSigThrew = $true
        Write-Host "Caught expected error (6a): $($_.Exception.Message)"
    }
    if (-not $missingSigThrew) { throw 'Test 6a failed: missing lads-update.json.sig did not throw.' }

    # 6b: Missing SHA256SUMS.txt.sig
    Reset-MockReleaseFixtures
    & $signScript -Version $testVersion -Key $keyPemPath -Repo 'test/repo' | Out-Null
    Remove-Item -LiteralPath (Join-Path $mockRelease 'SHA256SUMS.txt.sig') -Force
    $missingSumsSigThrew = $false
    try {
        Assert-ReleaseSignature -Tag $testTag -Repo 'test/repo' -Version $testVersion -WorkDir $verifyWork
    } catch {
        $missingSumsSigThrew = $true
        Write-Host "Caught expected error (6b): $($_.Exception.Message)"
        if ($_.Exception.Message -notmatch 'SHA256SUMS.txt and SHA256SUMS.txt.sig are mandatory release assets') {
            throw "Unexpected error message: $($_.Exception.Message)"
        }
    }
    if (-not $missingSumsSigThrew) { throw 'Test 6b failed: missing SHA256SUMS.txt.sig did not throw.' }
    Write-Host "PASS: Test 6 (Missing signature files rejected)"

    Write-Host "`n=== TEST 7: Tampered package rejection in Sign-Release ==="
    Reset-MockReleaseFixtures
    # Tamper with the package bytes
    $fullPkgName = "TheLadsClient-$testVersion-full.nupkg"
    [IO.File]::WriteAllBytes((Join-Path $mockRelease $fullPkgName), [Text.Encoding]::UTF8.GetBytes('TAMPERED PACKAGE BYTES'))
    $tamperedPkgThrew = $false
    try {
        & $signScript -Version $testVersion -Key $keyPemPath -Repo 'test/repo' | Out-Null
    } catch {
        $tamperedPkgThrew = $true
        Write-Host "Caught expected error: $($_.Exception.Message)"
        if ($_.Exception.Message -notmatch 'does not match build record hash') {
            throw "Unexpected error message: $($_.Exception.Message)"
        }
    }
    if (-not $tamperedPkgThrew) { throw 'Test 7 failed: Sign-Release signed a tampered package.' }
    Write-Host "PASS: Test 7 (Tampered package rejected before signing)"

    Write-Host "`n=== TEST 8: Tampered SHA256SUMS.txt rejection ==="
    # 8a: Tampered before signing (mismatching release-build.json)
    Reset-MockReleaseFixtures
    [IO.File]::WriteAllText((Join-Path $mockRelease 'SHA256SUMS.txt'), "0000000000000000000000000000000000000000000000000000000000000000  TheLadsClient-$testVersion-full.nupkg`n")
    $tamperedSumsPreThrew = $false
    try {
        & $signScript -Version $testVersion -Key $keyPemPath -Repo 'test/repo' | Out-Null
    } catch {
        $tamperedSumsPreThrew = $true
        Write-Host "Caught expected error (8a): $($_.Exception.Message)"
        if ($_.Exception.Message -notmatch 'does not match downloaded package hash') {
            throw "Unexpected error message: $($_.Exception.Message)"
        }
    }
    if (-not $tamperedSumsPreThrew) { throw 'Test 8a failed: Sign-Release signed tampered SHA256SUMS.txt.' }

    # 8b: Tampered after signing (signature verification fails)
    Reset-MockReleaseFixtures
    & $signScript -Version $testVersion -Key $keyPemPath -Repo 'test/repo' | Out-Null
    [IO.File]::AppendAllText((Join-Path $mockRelease 'SHA256SUMS.txt'), "`n1111111111111111111111111111111111111111111111111111111111111111  evil.exe")
    $tamperedSumsPostThrew = $false
    try {
        Assert-ReleaseSignature -Tag $testTag -Repo 'test/repo' -Version $testVersion -WorkDir $verifyWork
    } catch {
        $tamperedSumsPostThrew = $true
        Write-Host "Caught expected error (8b): $($_.Exception.Message)"
        if ($_.Exception.Message -notmatch 'SHA256SUMS.txt signature does not verify against any pinned public key') {
            throw "Unexpected error message: $($_.Exception.Message)"
        }
    }
    if (-not $tamperedSumsPostThrew) { throw 'Test 8b failed: Assert-ReleaseSignature passed on tampered SHA256SUMS.txt.' }
    Write-Host "PASS: Test 8 (Tampered SHA256SUMS rejected both pre-signing and post-signing)"

    Write-Host "`n=== TEST 9: Attestation verification failure rejection ==="
    Reset-MockReleaseFixtures
    # 9a: Attested commit mismatch
    $global:mockAttestationCommit = '0123456789abcdef0123456789abcdef01234567'
    $attestMismatchThrew = $false
    try {
        & $signScript -Version $testVersion -Key $keyPemPath -Repo 'test/repo' | Out-Null
    } catch {
        $attestMismatchThrew = $true
        Write-Host "Caught expected error (9a): $($_.Exception.Message)"
        if ($_.Exception.Message -notmatch 'does not match expected tag commit') {
            throw "Unexpected error message: $($_.Exception.Message)"
        }
    }
    if (-not $attestMismatchThrew) { throw 'Test 9a failed: Sign-Release did not reject attestation commit mismatch.' }

    # 9b: Attestation verify command failure
    Reset-MockReleaseFixtures
    $global:mockAttestationFails = $true
    $attestFailThrew = $false
    try {
        & $signScript -Version $testVersion -Key $keyPemPath -Repo 'test/repo' | Out-Null
    } catch {
        $attestFailThrew = $true
        Write-Host "Caught expected error (9b): $($_.Exception.Message)"
        if ($_.Exception.Message -notmatch 'Attestation verification failed') {
            throw "Unexpected error message: $($_.Exception.Message)"
        }
    }
    if (-not $attestFailThrew) { throw 'Test 9b failed: Sign-Release did not reject failed attestation.' }
    Write-Host "PASS: Test 9 (Attestation mismatch and failure correctly rejected)"

    Write-Host "`n=== TEST 10: Mandatory hashes map in release-build.json ==="
    Reset-MockReleaseFixtures
    $noHashesJson = [ordered]@{
        version = $testVersion
        commit = $headCommit
        localChanges = $false
        coreBuilt = $true
        verifiesContentHash = $true
    }
    [IO.File]::WriteAllText((Join-Path $mockRelease 'release-build.json'), (ConvertTo-Json $noHashesJson -Depth 5))
    $missingHashesThrew = $false
    try {
        & $signScript -Version $testVersion -Key $keyPemPath -Repo 'test/repo' | Out-Null
    } catch {
        $missingHashesThrew = $true
        Write-Host "Caught expected error: $($_.Exception.Message)"
        if ($_.Exception.Message -notmatch "missing the mandatory 'hashes' map") {
            throw "Unexpected error message: $($_.Exception.Message)"
        }
    }
    if (-not $missingHashesThrew) { throw 'Test 10 failed: Sign-Release did not enforce mandatory hashes.' }
    Write-Host "PASS: Test 10 (Mandatory hashes map enforced)"

    Write-Host "`n=== ALL 10 RELEASE SIGNING AND ASSERTION TESTS PASSED ==="
} finally {
    if ($tagCreated) {
        git tag -d $testTag 2>&1 | Out-Null
    }
    $env:LADS_SIGNING_KEY_PATH = $origKeyEnv
    $env:LADS_UPDATE_SIGNATURE_PATH = $origSigEnv
    if (Test-Path -LiteralPath $testWork) {
        Remove-Item -LiteralPath $testWork -Recurse -Force -WhatIf:$false -ErrorAction SilentlyContinue
    }
    Pop-Location
}
