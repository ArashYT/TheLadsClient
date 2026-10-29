[CmdletBinding(SupportsShouldProcess)]
param(
    [switch]$PrepareOnly,
    [switch]$SkipCoreBuild,
    [switch]$Publish,
    [switch]$Draft
)
$ErrorActionPreference = 'Stop'
$repo = 'ArashYT/TheLadsClient'

function Assert-ReleaseSignature {
    param(
        [string]$Tag,
        [string]$Repo,
        [string]$Version,
        [string]$WorkDir
    )
    $remote = & gh release view $Tag --repo $Repo --json assets | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw "Unable to verify assets of release $Tag." }
    $assetNames = @($remote.assets | ForEach-Object { $_.name })
    if ($assetNames -notcontains 'lads-update.json' -or $assetNames -notcontains 'lads-update.json.sig') {
        throw "Cannot publish ${Tag}: signature assets (lads-update.json, lads-update.json.sig) are missing. Run tools/Sign-Release.ps1 first."
    }
    $sigDir = Join-Path $WorkDir 'sig-verify'
    New-Item -ItemType Directory -Path $sigDir -Force | Out-Null
    & gh release download $Tag --repo $Repo --pattern 'lads-update.json*' --dir $sigDir
    if ($LASTEXITCODE -ne 0) { throw "Failed to download signature assets for $Tag." }

    $manifestBytes = [IO.File]::ReadAllBytes((Join-Path $sigDir 'lads-update.json'))
    $sigBytes = [IO.File]::ReadAllBytes((Join-Path $sigDir 'lads-update.json.sig'))

    $updateSigPath = Join-Path $PSScriptRoot 'TheLadsLauncher/UpdateSignature.cs'
    $sigCs = Get-Content -LiteralPath $updateSigPath -Raw
    $m = [regex]::Match($sigCs, '(?s)ReleasePublicKeys.*?=\s*new\[\]\s*\{(.*?)\};')
    $pinnedKeys = @([regex]::Matches($m.Groups[1].Value, '"([A-Za-z0-9+/=]{40,})"') | ForEach-Object { $_.Groups[1].Value })
    if ($pinnedKeys.Count -eq 0) { throw 'No pinned public keys found in UpdateSignature.cs.' }

    $verified = $false
    foreach ($pubKey in $pinnedKeys) {
        $ecdsa = [Security.Cryptography.ECDsa]::Create()
        try {
            $read = 0
            $ecdsa.ImportSubjectPublicKeyInfo([Convert]::FromBase64String($pubKey), [ref]$read)
            if ($ecdsa.VerifyData($manifestBytes, $sigBytes, [Security.Cryptography.HashAlgorithmName]::SHA256)) {
                $verified = $true
                break
            }
        } finally {
            $ecdsa.Dispose()
        }
    }
    if (-not $verified) {
        throw "Cannot publish ${Tag}: lads-update.json signature does not verify against any pinned public key."
    }

    $statement = [Text.Encoding]::UTF8.GetString($manifestBytes) | ConvertFrom-Json
    if ($statement.version -ne $Version) {
        throw "Cannot publish ${Tag}: signature manifest is for version $($statement.version), expected $Version."
    }
    $expectedPackage = "TheLadsClient-$Version-full.nupkg"
    if ($statement.package -ne $expectedPackage) {
        throw "Cannot publish ${Tag}: signature manifest is for package $($statement.package), expected $expectedPackage."
    }
    if ($assetNames -notcontains 'SHA256SUMS.txt' -or $assetNames -notcontains 'SHA256SUMS.txt.sig') {
        throw "Cannot publish ${Tag}: SHA256SUMS.txt and SHA256SUMS.txt.sig are mandatory release assets. Run tools/Sign-Release.ps1 first."
    }
    & gh release download $Tag --repo $Repo --pattern 'SHA256SUMS.txt*' --dir $sigDir
    if ($LASTEXITCODE -ne 0) { throw "Failed to download SHA256SUMS assets for $Tag." }
    $sumsBytes = [IO.File]::ReadAllBytes((Join-Path $sigDir 'SHA256SUMS.txt'))
    $sumsSigBytes = [IO.File]::ReadAllBytes((Join-Path $sigDir 'SHA256SUMS.txt.sig'))
    $sumsVerified = $false
    foreach ($pubKey in $pinnedKeys) {
        $ecdsa = [Security.Cryptography.ECDsa]::Create()
        try {
            $read = 0
            $ecdsa.ImportSubjectPublicKeyInfo([Convert]::FromBase64String($pubKey), [ref]$read)
            if ($ecdsa.VerifyData($sumsBytes, $sumsSigBytes, [Security.Cryptography.HashAlgorithmName]::SHA256)) {
                $sumsVerified = $true
                break
            }
        } finally {
            $ecdsa.Dispose()
        }
    }
    if (-not $sumsVerified) {
        throw "Cannot publish ${Tag}: SHA256SUMS.txt signature does not verify against any pinned public key."
    }
    Write-Host "Verified release signature for $Tag against pinned public key."
}
Push-Location -LiteralPath $PSScriptRoot
try {
    [xml]$project = Get-Content -LiteralPath 'TheLadsLauncher/TheLadsLauncher.csproj' -Raw
    $version = [string]$project.Project.PropertyGroup.Version
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Use a stable major.minor.patch version in the launcher csproj.' }
    $tag = "v$version"
    $notes = Join-Path $PSScriptRoot "docs/releases/$version.md"
    if (-not (Test-Path -LiteralPath $notes)) { throw "Missing reviewed release notes: $notes" }
    $body = Get-Content -LiteralPath $notes -Raw
    if ($body.Length -lt 120 -or $body -match '(?im)\b(TODO|TBD|PLACEHOLDER)\b' -or $body -notmatch '(?m)^## (Added|Changed|Fixed)') {
        throw 'Release notes must describe real changes, use section headings, and contain no placeholders.'
    }
    if ($SkipCoreBuild -and -not $PrepareOnly) { throw 'Skipping the core build is only allowed for local preparation.' }
    $commit = git rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Unable to resolve release commit.' }
    $dirty = @(git status --porcelain)
    if (-not $PrepareOnly) {
        if ($dirty.Count -gt 0) { throw 'Commit and push the intended release source first. The worktree must be clean.' }
        & gh auth status
        if ($LASTEXITCODE -ne 0) { throw 'Sign in with gh auth login first.' }
        $tagCommit = git rev-list -n 1 $tag
        if ($LASTEXITCODE -ne 0 -or $tagCommit -ne $commit) { throw "Create $tag on the current commit and push it before releasing." }
        $remoteTag = @(git ls-remote origin "refs/tags/$tag" "refs/tags/$tag^{}")
        if ($LASTEXITCODE -ne 0 -or -not ($remoteTag | Where-Object { $_ -match "^$commit\s" })) { throw 'The release tag must point to this commit on origin.' }
        $viewOutput = & gh release view $tag --repo $repo --json isDraft,assets 2>$null
        if ($LASTEXITCODE -eq 0 -and $viewOutput) {
            $existing = $viewOutput | ConvertFrom-Json
            if (-not $existing.isDraft) {
                throw "Release $tag already exists and is published. Choose a new version; never replace published packages."
            }
            if ($Publish) {
                Write-Host "Draft $tag already exists on GitHub. Verifying signatures before publishing..."
                $verifyWork = Join-Path ([IO.Path]::GetTempPath()) ("lads-pub-" + [guid]::NewGuid().ToString('N'))
                New-Item -ItemType Directory -Path $verifyWork -Force | Out-Null
                try {
                    Assert-ReleaseSignature -Tag $tag -Repo $repo -Version $version -WorkDir $verifyWork
                    if ($PSCmdlet.ShouldProcess($tag, "Publish release and mark latest")) {
                        & gh release edit $tag --repo $repo --draft=false --latest
                        if ($LASTEXITCODE -ne 0) { throw "Failed to publish release $tag." }
                    }
                    Write-Host "Release $tag published to https://github.com/$repo/releases"
                    Write-Host "Installed clients will update automatically."
                    return
                } finally {
                    Remove-Item -LiteralPath $verifyWork -Recurse -Force -ErrorAction SilentlyContinue
                }
            } else {
                throw "Release draft $tag already exists. Choose a new version, or publish the signed draft with -Publish."
            }
        }
    }

    $work = Join-Path $PSScriptRoot ('artifacts/releases/' + $version + '-' + [guid]::NewGuid().ToString('N'))
    $publish = Join-Path $work 'app'
    $packages = Join-Path $work 'packages'
    New-Item -ItemType Directory -Path $publish,$packages -Force | Out-Null
    if (-not $SkipCoreBuild) {
        Push-Location 'TheLadsCore'
        try {
            & .\gradlew.bat :common:test --console=plain
            if ($LASTEXITCODE -ne 0) { throw 'Core common tests failed.' }
            & .\gradlew.bat build deploy -x test --console=plain
            if ($LASTEXITCODE -ne 0) { throw 'Core build/deploy failed.' }
        } finally { Pop-Location }
    }
    & dotnet test TheLadsLauncher.Tests/TheLadsLauncher.Tests.csproj -c Release --nologo
    if ($LASTEXITCODE -ne 0) { throw 'Launcher tests failed.' }
    & dotnet publish TheLadsLauncher/TheLadsLauncher.csproj -c Release -r win-x64 --self-contained true -o $publish
    if ($LASTEXITCODE -ne 0) { throw 'Launcher publish failed.' }
    # Holds the "Installing update" screen while Update.exe swaps the launcher (UpdateBridge.cs); without it updates show nothing.
    if (-not (Test-Path -LiteralPath (Join-Path $publish 'LadsUpdateBridge.exe'))) { throw 'Missing LadsUpdateBridge.exe' }
    foreach ($gameVersion in @('1.8.9','1.20.1','1.21.11','26.2','26.3')) {
        foreach ($file in @('theladscore.jar','client-mods.json')) {
            if (-not (Test-Path -LiteralPath (Join-Path $publish "game-mods/$gameVersion/$file"))) { throw "Missing $gameVersion/$file" }
        }
    }
    # Forge 1.20.1 packs' Lads early loading window; without it players silently get Forge's (ModpackLauncher.UseEarlyWindow).
    if (-not (Test-Path -LiteralPath (Join-Path $publish 'game-mods/1.20.1/lads-earlywindow.jar'))) { throw 'Missing 1.20.1/lads-earlywindow.jar' }
    if (-not (Test-Path -LiteralPath (Join-Path $publish "release-notes/$version.md"))) { throw 'Current release notes were not bundled.' }
    # Zero SMP pack patches: installed packs only get them through the launcher (ExperiencesService.BundledPatchesDirectory).
    foreach ($patch in Get-ChildItem -LiteralPath 'tools/modpack-manager/patches' -Filter 'update-*.json') {
        if (-not (Test-Path -LiteralPath (Join-Path $publish "modpack-patches/$($patch.Name)"))) { throw "Missing modpack-patches/$($patch.Name)" }
    }
    # verifiesContentHash: this launcher checks a delta-rebuilt package by content hash (TheLadsLauncher/UpdateSignature.cs), so the
    # next release may ship a delta to it. Keep it true only while the launcher code in this commit does that.
    @{version=$version; commit=$commit; localChanges=($dirty.Count -gt 0); coreBuilt=(-not $SkipCoreBuild); verifiesContentHash=$true} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $publish 'release-build.json')
    & dotnet tool restore
    if ($LASTEXITCODE -ne 0) { throw 'Cannot restore pinned packaging tool.' }
    # A delta package only works for launchers that verify a rebuilt package by content hash. The marker is the release-build.json
    # written from the same commit as the launcher (verifiesContentHash above), not the signature manifest: Sign-Release.ps1 can be
    # run from any branch. So this release builds a delta only when the latest published release's launcher verifies it; until
    # then every update is the full package.
    $latest = & gh release view --repo $repo --json tagName,assets | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or -not $latest.tagName) { throw 'Cannot read the latest published release (needed to decide on a delta package).' }
    $deltaAllowed = $false
    if ($latest.assets.name -contains 'release-build.json') {
        $previousDir = Join-Path $work 'previous-release'
        & gh release download $latest.tagName --repo $repo --pattern 'release-build.json' --dir $previousDir
        if ($LASTEXITCODE -ne 0) { throw "Cannot download release-build.json of $($latest.tagName)." }
        $deltaAllowed = (ConvertFrom-Json ([IO.File]::ReadAllText((Join-Path $previousDir 'release-build.json')))).verifiesContentHash -eq $true
    }
    if ($deltaAllowed) {
        # Seed the output folder with the previous release's feed and full package: vpk builds the delta from them. Through gh
        # (authenticated, and the same release tools/Sign-Release.ps1 checks the delta against): `vpk download github` is anonymous
        # and fails with GitHub's 403 rate limit (60 requests per hour per IP) when runs from one IP or runner add up.
        $previousFull = "TheLadsClient-$($latest.tagName -replace '^v','')-full.nupkg"
        & gh release download $latest.tagName --repo $repo --pattern 'releases.win.json' --pattern $previousFull --dir $packages
        if ($LASTEXITCODE -ne 0) { throw "Cannot download releases.win.json and $previousFull of $($latest.tagName) to build the delta from." }
        foreach ($name in @('releases.win.json', $previousFull)) {
            if (-not (Test-Path -LiteralPath (Join-Path $packages $name))) { throw "$($latest.tagName) has no $name to build the delta from." }
        }
    }
    Write-Host "Delta package: $(if ($deltaAllowed) { "yes, from $($latest.tagName)" } else { "no, the launcher of $($latest.tagName) does not verify delta packages (release-build.json has no verifiesContentHash)" })"
    $deltaMode = if ($deltaAllowed) { 'BestSpeed' } else { 'None' }
    # The splash shows the Lads logo while Setup installs; its progress colour is LadsAccent (Styles/LauncherTheme.axaml).
    & dotnet vpk pack --packId TheLadsClient --packVersion $version --packDir $publish --mainExe TheLadsLauncher.exe --packTitle 'The Lads Client' --packAuthors ArashYT --channel win --runtime win-x64 --icon TheLadsLauncher/Assets/icon.ico --releaseNotes $notes --outputDir $packages --delta $deltaMode --splashImage TheLadsLauncher/Assets/installer-splash.png --splashProgressColor '#C44343'
    if ($LASTEXITCODE -ne 0) { throw 'Installer packaging failed.' }
    $required = @('TheLadsClient-win-Setup.exe','releases.win.json','assets.win.json',"TheLadsClient-$version-full.nupkg")
    if ($deltaAllowed) { $required += "TheLadsClient-$version-delta.nupkg" }
    foreach ($name in $required) {
        if (-not (Test-Path -LiteralPath (Join-Path $packages $name))) { throw "Packaging did not produce $name" }
    }
    # vpk lists the seeded previous release too. A release carries only its own packages: clients that are further behind
    # take the full package, and the previous full package is not a file of this release.
    $own = "-$version-"
    Get-ChildItem -LiteralPath $packages -File | Where-Object { $_.Name -match '-(full|delta)\.nupkg$' -and -not $_.Name.Contains($own) } | Remove-Item -Force
    $noBom = New-Object Text.UTF8Encoding $false
    $feedPath = Join-Path $packages 'releases.win.json'
    $feed = [IO.File]::ReadAllText($feedPath) | ConvertFrom-Json
    $feed.Assets = @($feed.Assets | Where-Object { $_.Version -eq $version })
    if ($feed.Assets.Count -ne $(if ($deltaAllowed) { 2 } else { 1 })) { throw "releases.win.json must list exactly this version's full package$(if ($deltaAllowed) { ' and delta' })." }
    [IO.File]::WriteAllText($feedPath, (ConvertTo-Json $feed -Depth 10 -Compress), $noBom)
    $legacyPath = Join-Path $packages 'RELEASES'
    if (Test-Path -LiteralPath $legacyPath) {
        $legacy = @([IO.File]::ReadAllText($legacyPath) -split '(?<=\n)' | Where-Object { $_.Contains($own) })
        if ($legacy.Count -ne 1 -or -not $legacy[0].Contains("TheLadsClient-$version-full.nupkg")) { throw "RELEASES must list exactly this version's full package (vpk lists only full packages there)." }
        [IO.File]::WriteAllText($legacyPath, (-join $legacy), (New-Object Text.UTF8Encoding $true))
    }
    $listed = [IO.File]::ReadAllText((Join-Path $packages 'assets.win.json')) | ConvertFrom-Json
    if (@($listed | Where-Object { $_.RelativeFileName -like '*.nupkg' -and -not $_.RelativeFileName.Contains($own) }).Count -gt 0) { throw 'assets.win.json names a package of another version.' }
    Copy-Item -LiteralPath $notes -Destination (Join-Path $packages 'RELEASE_NOTES.md')
    $buildInfo = Get-Content -LiteralPath (Join-Path $publish 'release-build.json') -Raw | ConvertFrom-Json
    $pkgHashes = [ordered]@{}
    Get-ChildItem -LiteralPath $packages -File | Sort-Object Name | ForEach-Object {
        $pkgHashes[$_.Name] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $buildInfo | Add-Member -NotePropertyName 'hashes' -NotePropertyValue $pkgHashes -Force
    [IO.File]::WriteAllText((Join-Path $packages 'release-build.json'), (ConvertTo-Json $buildInfo -Depth 5))
    Get-ChildItem -LiteralPath $packages -File | Sort-Object Name | ForEach-Object {
        '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $_.Name
    } | Set-Content -LiteralPath (Join-Path $packages 'SHA256SUMS.txt')
    if ($PrepareOnly) {
        Write-Host "Prepared local packages: $packages"
        Write-Host 'Nothing was uploaded. Local preparation is not a published release.'
        return
    }
    $assets = @(Get-ChildItem -LiteralPath $packages -File | Select-Object -ExpandProperty FullName)
    & gh release create $tag @assets --repo $repo --verify-tag --draft --title "The Lads Client $version" --notes-file $notes
    if ($LASTEXITCODE -ne 0) { throw 'Draft release/upload failed. Inspect the draft before retrying; do not publish incomplete assets.' }
    # Verify that the draft contains every expected file before declaring it ready.
    $remote = & gh release view $tag --repo $repo --json assets | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Unable to verify uploaded draft assets.' }
    foreach ($asset in Get-ChildItem -LiteralPath $packages -File) {
        if (-not ($remote.assets | Where-Object { $_.name -eq $asset.Name -and $_.size -eq $asset.Length })) { throw "Draft asset missing or wrong size: $($asset.Name)" }
    }
    if (-not $Publish) {
        Write-Host "Draft ready: https://github.com/$repo/releases"
        Write-Host "Review notes and assets, then sign the release with tools/Sign-Release.ps1 -Version $version."
        Write-Host "After signing, publish with: ./Publish-Release.ps1 -Publish"
    } else {
        Assert-ReleaseSignature -Tag $tag -Repo $repo -Version $version -WorkDir $work
        if ($PSCmdlet.ShouldProcess($tag, "Publish release and mark latest")) {
            & gh release edit $tag --repo $repo --draft=false --latest
            if ($LASTEXITCODE -ne 0) { throw "Failed to publish release $tag." }
        }
        Write-Host "Release $tag published to https://github.com/$repo/releases"
        Write-Host "Installed clients will update automatically."
    }
} finally { Pop-Location }
