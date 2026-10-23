# Signs a CI-built release draft so launchers install it (TheLadsLauncher/UpdateSignature.cs). Run on the owner's PC after
# verifying the draft and before publishing it: the private key never goes to GitHub, so a release made by anyone else
# (a stolen GitHub login, a compromised CI action) has no valid signature and every launcher refuses it.
# Also refuses to sign a draft whose -delta.nupkg does not rebuild (from the previous release) into the signed full package.
#   ./tools/Sign-Release.ps1 -Version 1.9.10 [-Key $HOME\Documents\TheLads-release-signing-key.pem]
param(
    [Parameter(Mandatory)] [string] $Version,
    [string] $Key = $(if ($env:LADS_SIGNING_KEY_PATH) { $env:LADS_SIGNING_KEY_PATH } else { Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'TheLads-release-signing-key.pem' }),
    [string] $Repo = 'ArashYT/TheLadsClient'
)
$ErrorActionPreference = 'Stop'
$tag = "v$Version"
$work = Join-Path ([IO.Path]::GetTempPath()) ("lads-sign-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $work | Out-Null
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root   # `dotnet vpk` is a local tool: it needs the repo's .config/dotnet-tools.json
try {
    $package = "TheLadsClient-$Version-full.nupkg"
    gh release download $tag --repo $Repo --pattern $package --dir $work
    if ($LASTEXITCODE -ne 0) { throw "Could not download $package from the $tag release." }
    $path = Join-Path $work $package
    $sha = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([IO.File]::ReadAllBytes($path)))
    $contentSha = & (Join-Path $PSScriptRoot 'Get-PackageContentHash.ps1') -Path $path
    $deltaName = "TheLadsClient-$Version-delta.nupkg"
    $assets = gh release view $tag --repo $Repo --json assets --jq '.assets[].name'
    if ($LASTEXITCODE -ne 0) { throw "Could not list the assets of the $tag release." }
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
        $rebuilt = Join-Path $work 'rebuilt.nupkg'
        dotnet vpk delta patch --base $base --patch (Join-Path $work $deltaName) --output $rebuilt
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $rebuilt)) { throw $refuse }
        if ((& (Join-Path $PSScriptRoot 'Get-PackageContentHash.ps1') -Path $rebuilt) -ne $contentSha) { throw $refuse }
    }
    $manifest = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -Compress ([ordered]@{ version = $Version; package = $package; sha256 = $sha; contentSha256 = $contentSha })))
    $ecdsa = [Security.Cryptography.ECDsa]::Create()
    try {
        $ecdsa.ImportFromPem([IO.File]::ReadAllText($Key))
        $signature = $ecdsa.SignData($manifest, [Security.Cryptography.HashAlgorithmName]::SHA256)
        if (-not $ecdsa.VerifyData($manifest, $signature, [Security.Cryptography.HashAlgorithmName]::SHA256)) { throw 'Signature self-check failed.' }
    } finally { $ecdsa.Dispose() }
    [IO.File]::WriteAllBytes((Join-Path $work 'lads-update.json'), $manifest)
    [IO.File]::WriteAllBytes((Join-Path $work 'lads-update.json.sig'), $signature)
    gh release upload $tag (Join-Path $work 'lads-update.json') (Join-Path $work 'lads-update.json.sig') --repo $Repo --clobber
    if ($LASTEXITCODE -ne 0) { throw 'Upload failed.' }
    "Signed $tag : $package sha256 $sha contentSha256 $contentSha"
} finally { Pop-Location; Remove-Item -LiteralPath $work -Recurse -Force }
