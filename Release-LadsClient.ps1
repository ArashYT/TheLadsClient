# Release-LadsClient.ps1

Write-Host "Checking local git tags..."
$existingTag = git tag -l "v0.14.6"
if (-not $existingTag) {
    Write-Host "Creating local tag v0.14.6..."
    git tag v0.14.6
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to create local tag v0.14.6."
        exit 1
    }
} else {
    Write-Host "Local tag v0.14.6 already exists."
}

# Push tag to origin
Write-Host "Attempting to push tag v0.14.6 to origin..."
git push origin v0.14.6
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Failed to push tag v0.14.6 to origin. This is expected if network or credentials are restricted."
} else {
    Write-Host "Successfully pushed tag v0.14.6 to origin."
}

# Create GitHub Release
$ghPath = "C:\Program Files\GitHub CLI\gh.exe"
if (-not (Test-Path $ghPath)) {
    $ghPath = "gh"
}

$launcherPath = "TheLadsLauncher\bin\Release\net8.0-windows\win-x64\publish\TheLadsLauncher.exe"
$installerPath = "Output\LadsClient_Installer_BETA_0.14.6.exe"

if (-not (Test-Path $launcherPath)) {
    Write-Error "Launcher executable not found at $launcherPath"
    exit 1
}

if (-not (Test-Path $installerPath)) {
    Write-Error "Installer executable not found at $installerPath"
    exit 1
}

Write-Host "Attempting to create GitHub Release v0.14.6..."
& $ghPath release create v0.14.6 --title "The Lads Client BETA 0.14.6" --notes "Release notes for BETA 0.14.6"
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Failed to create GitHub release v0.14.6. This is expected if token/network is invalid."
} else {
    Write-Host "Successfully created GitHub release v0.14.6."
}

Write-Host "Attempting to upload assets..."
& $ghPath release upload v0.14.6 $launcherPath $installerPath
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Failed to upload assets to GitHub release. This is expected if release creation failed."
} else {
    Write-Host "Successfully uploaded assets to GitHub release."
}

Write-Host "Release script finished."
