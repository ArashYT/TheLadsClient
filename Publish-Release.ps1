[CmdletBinding()]
param(
    [switch]$PrepareOnly,
    [switch]$SkipCoreBuild,
    [switch]$Draft
)
$ErrorActionPreference = 'Stop'
$repo = 'ArashYT/TheLadsClient'
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
        & gh release view $tag --repo $repo *> $null
        if ($LASTEXITCODE -eq 0) { throw "Release $tag already exists. Choose a new version; never replace published packages." }
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
    foreach ($gameVersion in @('1.8.9','1.21.11','26.2','26.3')) {
        foreach ($file in @('theladscore.jar','client-mods.json')) {
            if (-not (Test-Path -LiteralPath (Join-Path $publish "game-mods/$gameVersion/$file"))) { throw "Missing $gameVersion/$file" }
        }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $publish "release-notes/$version.md"))) { throw 'Current release notes were not bundled.' }
    @{version=$version; commit=$commit; localChanges=($dirty.Count -gt 0); coreBuilt=(-not $SkipCoreBuild)} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $publish 'release-build.json')
    & dotnet tool restore
    if ($LASTEXITCODE -ne 0) { throw 'Cannot restore pinned packaging tool.' }
    & dotnet vpk pack --packId TheLadsClient --packVersion $version --packDir $publish --mainExe TheLadsLauncher.exe --packTitle 'The Lads Client' --packAuthors ArashYT --channel win --runtime win-x64 --icon TheLadsLauncher/Assets/icon.ico --releaseNotes $notes --outputDir $packages
    if ($LASTEXITCODE -ne 0) { throw 'Installer packaging failed.' }
    foreach ($required in @('TheLadsClient-win-Setup.exe','releases.win.json',"TheLadsClient-$version-full.nupkg")) {
        if (-not (Test-Path -LiteralPath (Join-Path $packages $required))) { throw "Packaging did not produce $required" }
    }
    Copy-Item -LiteralPath $notes -Destination (Join-Path $packages 'RELEASE_NOTES.md')
    Copy-Item -LiteralPath (Join-Path $publish 'release-build.json') -Destination $packages
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
    if ($Draft) {
        Write-Host "Draft ready: https://github.com/$repo/releases"
        Write-Host "Review notes and assets, then publish $tag as the latest stable release. Installed clients will update automatically."
    } else {
        & gh release edit $tag --repo $repo --draft=false --latest
        if ($LASTEXITCODE -ne 0) { throw "Failed to publish release $tag." }
        Write-Host "Release $tag published to https://github.com/$repo/releases"
        Write-Host "Installed clients will update automatically."
    }
} finally { Pop-Location }
