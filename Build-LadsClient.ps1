$ErrorActionPreference = "Stop"

$workspace = "C:\Users\Arash\Desktop\The Lads Client Dev\Lads Client"
$coreDir   = "C:\Users\Arash\Desktop\The Lads Client Dev\Lads Client\TheLadsCore"
$launcherDir = "C:\Users\Arash\Desktop\The Lads Client Dev\Lads Client\TheLadsLauncher"
$packwizMods = "C:\Users\Arash\Desktop\The Lads Client Dev\Lads Client\Packwiz\mods"
$IndexToml = "C:\Users\Arash\Desktop\The Lads Client Dev\Lads Client\Packwiz\index.toml"

Write-Host ">>> Updating TheLadsCore Version..."
Set-Location -Path $coreDir
$propsFile = "gradle.properties"
$content = Get-Content $propsFile
$newVer = "1.0.0"

for ($i = 0; $i -lt $content.Length; $i++) {
    if ($content[$i] -match '^mod_version=(.*)') {
        $oldVer = $matches[1]
        $parts = $oldVer.Split('.')
        $last = [int]$parts[-1] + 1
        $parts[-1] = $last.ToString()
        $global:newVer = $parts -join '.'
        $content[$i] = "mod_version=$global:newVer"
        Write-Host "    Bumped mod_version from $oldVer to $global:newVer"
        break
    }
}
Set-Content -Path $propsFile -Value $content

Write-Host ">>> Building TheLadsCore..."
$gradlew = ".\gradlew.bat"
cmd.exe /c "$gradlew build -x test"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed."
}

Write-Host ">>> Packwiz is metadata-driven (.pw.toml); skipping local jar copy."
Set-Location -Path "C:\Users\Arash\Desktop\The Lads Client Dev\Lads Client\Packwiz"
packwiz refresh
Set-Location -Path $coreDir

Write-Host ">>> Closing any running TheLadsLauncher processes..."
Stop-Process -Name "TheLadsLauncher" -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1.5

Write-Host ">>> Building TheLadsLauncher..."
Set-Location -Path $launcherDir
dotnet publish -c Release -r win-x64 --self-contained
if ($LASTEXITCODE -ne 0) {
    Write-Error "Dotnet build failed."
}

Write-Host ">>> Deploying TheLadsLauncher to AppData..."
$deployTargetDir1 = "C:\Users\Arash\AppData\Local\The Lads Client"

if (-not (Test-Path $deployTargetDir1)) {
    New-Item -ItemType Directory -Path $deployTargetDir1 -Force | Out-Null
}
if (Test-Path "$deployTargetDir1\settings.json") {
    Get-ChildItem "$launcherDir\bin\Release\net8.0-windows\win-x64\publish\*" -Exclude "settings.json" | Copy-Item -Destination $deployTargetDir1 -Recurse -Force
} else {
    Copy-Item -Path "$launcherDir\bin\Release\net8.0-windows\win-x64\publish\*" -Destination $deployTargetDir1 -Recurse -Force
}

Write-Host ">>> Done."

