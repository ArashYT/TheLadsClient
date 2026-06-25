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

# 2. Extract version from Program.cs and Bump it
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

# Bump version in installer.iss
$InstallerIss = "C:\Users\Arash\Desktop\Lads Client\installer.iss"
$IssContent = Get-Content $InstallerIss
$NewIssContent = @()
foreach ($line in $IssContent) {
    if ($line -match '#define MyAppVersion "(.*?)"') {
        $NewIssContent += '#define MyAppVersion "' + $Version + '"'
    } else {
        $NewIssContent += $line
    }
}
$NewIssContent | Set-Content $InstallerIss

$TagName = "v$Version"
Write-Host "Publishing release for $TagName..."

# Backup previous build
$OldExePath = "C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher\bin\Release\net8.0-windows\win-x64\publish\TheLadsLauncher.exe"
if (Test-Path $OldExePath) {
    $BackupDir = "C:\Users\Arash\Desktop\lads client backups\v$OldVersion"
    Write-Host "Backing up previous build to $BackupDir"
    if (-not (Test-Path $BackupDir)) {
        New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
    }
    Copy-Item $OldExePath -Destination "$BackupDir\TheLadsLauncher.exe" -Force
}

# 3. Publish the app (Launcher)
Write-Host "Building single-file executable..."
Set-Location "C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher"
Write-Host "Cleaning previous build..."
dotnet clean
dotnet publish -c Release -r win-x64 --self-contained
Set-Location "C:\Users\Arash\Desktop\Lads Client"

# 4. Build the Installer
Write-Host "Building Installer..."
$ISCCPath = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if (Test-Path $ISCCPath) {
    & $ISCCPath "installer.iss"
} else {
    Write-Host "Warning: Inno Setup compiler not found. Installer will not be built or uploaded."
}

$ExePath = "C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher\bin\Release\net8.0-windows\win-x64\publish\TheLadsLauncher.exe"
$InstallerExePath = "C:\Users\Arash\Desktop\Lads Client\Output\LadsClient_Installer_BETA_$Version.exe"

if (-not (Test-Path $ExePath)) {
    Write-Host "Error: Launcher Exe not found at $ExePath"
    exit 1
}

# 5. Create release via GitHub API
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

# 6. Upload Asset (Launcher)
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
    Write-Host "Launcher successfully uploaded."
} catch {
    Write-Host "Failed to upload launcher asset."
    Write-Host $_.Exception.Message
}

# 7. Upload Asset (Installer)
if (Test-Path $InstallerExePath) {
    Write-Host "Uploading Installer..."
    $UploadUrlInstaller = "https://uploads.github.com/repos/$Repo/releases/$($CreateResponse.id)/assets?name=LadsClient_Installer_BETA_$Version.exe"
    try {
        Invoke-RestMethod -Uri $UploadUrlInstaller -Method Post -Headers $AssetHeaders -InFile $InstallerExePath
        Write-Host "Installer successfully uploaded."
    } catch {
        Write-Host "Failed to upload installer asset."
        Write-Host $_.Exception.Message
    }
}

Write-Host "Release $TagName successfully published to GitHub!"
