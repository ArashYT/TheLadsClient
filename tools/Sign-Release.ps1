# Signs a CI-built release draft so launchers install it (TheLadsLauncher/UpdateSignature.cs). Run on the owner's PC after
# verifying the draft and before publishing it: the private key never goes to GitHub, so a release made by anyone else
# (a stolen GitHub login, a compromised CI action) has no valid signature and every launcher refuses it.
#   ./tools/Sign-Release.ps1 -Version 1.9.3 [-Key $HOME\Documents\TheLads-release-signing-key.pem]
param(
    [Parameter(Mandatory)] [string] $Version,
    [string] $Key = (Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'TheLads-release-signing-key.pem'),
    [string] $Repo = 'ArashYT/TheLadsClient'
)
$ErrorActionPreference = 'Stop'
$tag = "v$Version"
$work = Join-Path ([IO.Path]::GetTempPath()) ("lads-sign-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $work | Out-Null
try {
    $package = "TheLadsClient-$Version-full.nupkg"
    gh release download $tag --repo $Repo --pattern $package --dir $work
    if ($LASTEXITCODE -ne 0) { throw "Could not download $package from the $tag release." }
    $path = Join-Path $work $package
    $sha = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([IO.File]::ReadAllBytes($path)))
    $manifest = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -Compress ([ordered]@{ version = $Version; package = $package; sha256 = $sha })))
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
    "Signed $tag : $package sha256 $sha"
} finally { Remove-Item -LiteralPath $work -Recurse -Force }
