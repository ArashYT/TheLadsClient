$TokenPath = "C:\Users\Arash\Documents\github_token.txt"
$Token = (Get-Content $TokenPath -Raw).Trim()
$Repo = "ArashYT/TheLadsClient"
$Headers = @{
    "Authorization" = "Bearer $Token"
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "TheLadsLauncher-ReleaseScript"
}

# 1. Delete all messy v0.14.* tags/releases
$releases = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases" -Headers $Headers
foreach ($r in $releases) {
    if ($r.tag_name -like "v0.14.*" -or $r.tag_name -eq "BETA-0.14") {
        Write-Host "Deleting release $($r.tag_name)..."
        Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/$($r.id)" -Method Delete -Headers $Headers -ErrorAction SilentlyContinue
        Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/git/refs/tags/$($r.tag_name)" -Method Delete -Headers $Headers -ErrorAction SilentlyContinue
    }
}

# 2. Create the clean release
$TagName = "v0.14"
Write-Host "Creating fresh release $TagName..."
$Body = @{
    tag_name = $TagName
    name = "The Lads Client - BETA 0.14"
    body = "The first official BETA release of The Lads Client! Includes the brand new Auto-Updater and Installer."
    draft = $false
    prerelease = $true
} | ConvertTo-Json

$CreateResponse = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases" -Method Post -Headers $Headers -Body $Body

# 3. Upload assets
$AssetHeaders = @{
    "Authorization" = "Bearer $Token"
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "TheLadsLauncher-ReleaseScript"
    "Content-Type" = "application/octet-stream"
}

$Files = @(
    @{ Name = "LadsClient_Installer_BETA_0.14.exe"; Path = "C:\Users\Arash\Desktop\Lads Client\LadsClient_Installer_BETA_0.14.exe" },
    @{ Name = "TheLadsLauncher.exe"; Path = "C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher\bin\Release\net8.0-windows\win-x64\publish\TheLadsLauncher.exe" }
)

foreach ($f in $Files) {
    Write-Host "Uploading $($f.Name)..."
    $UploadUrl = "https://uploads.github.com/repos/$Repo/releases/$($CreateResponse.id)/assets?name=$($f.Name)"
    Invoke-RestMethod -Uri $UploadUrl -Method Post -Headers $AssetHeaders -InFile $f.Path
}

Write-Host "Cleanup and fresh release complete!"
