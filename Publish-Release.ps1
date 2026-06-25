param (
    [string]$Title = "Launcher Update"
)

$ErrorActionPreference = "Stop"

# 1. Read token
$TokenPath = "C:\Users\Arash\Documents\github_token.txt"
if (-not (Test-Path $TokenPath)) {
    Write-Host "Error: GitHub token not found at $TokenPath"
    exit 1
}
$Token = Get-Content $TokenPath -Raw
$Token = $Token.Trim()

# 2. Extract version from Program.cs
$ProgramCs = "C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher\Program.cs"
$Content = Get-Content $ProgramCs
$Version = ""
$NewContent = @()
foreach ($line in $Content) {
    if ($line -match 'public const string Version = "(.*?)";') {
        $OldVersion = $matches[1]
        $parts = $OldVersion.Split('.')
        $patch = [int]$parts[2] + 1
        $Version = "$($parts[0]).$($parts[1]).$patch"
        $NewContent += $line -replace $OldVersion, $Version
    } else {
        $NewContent += $line
    }
}
if (-not $Version) {
    Write-Host "Could not find version in Program.cs"
    exit 1
}
$NewContent | Set-Content $ProgramCs
$TagName = "v$Version"
Write-Host "Publishing release for $TagName..."

# 3. Publish the app
Write-Host "Building single-file executable..."
Set-Location "C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher"
dotnet publish -c Release -r win-x64 --self-contained

$ExePath = "C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher\bin\Release\net8.0-windows\win-x64\publish\TheLadsLauncher.exe"
if (-not (Test-Path $ExePath)) {
    Write-Host "Error: Exe not found at $ExePath"
    exit 1
}

# 4. Create release via GitHub API
Write-Host "Creating GitHub Release $TagName..."
$Repo = "ArashYT/TheLadsClient"
$Headers = @{
    "Authorization" = "Bearer $Token"
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "TheLadsLauncher-ReleaseScript"
}

$Body = @{
    tag_name = $TagName
    name = $Title
    body = "Automated release of $TagName"
    draft = $false
    prerelease = $false
} | ConvertTo-Json

try {
    $CreateResponse = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases" -Method Post -Headers $Headers -Body $Body
    Write-Host "CreateResponse: $($CreateResponse | ConvertTo-Json -Depth 2)"
} catch {
    Write-Host "Failed to create release. The tag might already exist, or token is invalid."
    Write-Host $_.Exception.Message
    exit 1
}

# 5. Upload Asset
Write-Host "Uploading TheLadsLauncher.exe..."
$UploadUrl = "https://uploads.github.com/repos/$Repo/releases/$($CreateResponse.id)/assets?name=TheLadsLauncher.exe"
$AssetHeaders = @{
    "Authorization" = "Bearer $Token"
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "TheLadsLauncher-ReleaseScript"
    "Content-Type" = "application/octet-stream"
}

try {
    Invoke-RestMethod -Uri $UploadUrl -Method Post -Headers $AssetHeaders -InFile $ExePath
    Write-Host "Release $TagName successfully published to GitHub!"
} catch {
    Write-Host "Failed to upload asset."
    Write-Host $_.Exception.Message
    exit 1
}
