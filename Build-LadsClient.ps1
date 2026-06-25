$ErrorActionPreference = "Stop"

$workspace = "C:\Users\Arash\Desktop\Lads Client"
$coreDir   = "C:\Users\Arash\Desktop\Lads Client\TheLadsCore"
$launcherDir = "C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher"
$packwizMods = "C:\Users\Arash\Desktop\Lads Client\The Lads Client Packwiz\mods"
$IndexToml = "C:\Users\Arash\Desktop\Lads Client\The Lads Client Packwiz\index.toml"

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

Write-Host ">>> Copying TheLadsCore to Packwiz..."
$target = "C:\Users\Arash\Desktop\Lads Client\The Lads Client Packwiz\mods\TheLadsCore-$global:newVer.jar"
Copy-Item -Path "build\libs\TheLadsCore-$global:newVer.jar" -Destination $target -Force

if (Test-Path $IndexToml) {
    $rel     = "mods/TheLadsCore-$global:newVer.jar"
    $newHash = (Get-FileHash $target -Algorithm SHA256).Hash.ToLower()
    $idx = [System.IO.File]::ReadAllText($IndexToml)
    $jarPattern = 'file = "mods/TheLadsCore-[^"]+"\r?\nhash = "[0-9a-fA-F]{64}"'
    if ($idx -match $jarPattern) {
        $newStr = "file = `"$rel`"`r`nhash = `"$newHash`""
        $idx = $idx -replace $jarPattern, $newStr
        [System.IO.File]::WriteAllText($IndexToml, $idx)
        Write-Host "    Packwiz index updated!"
        
        # Run packwiz refresh to update pack.toml hash so the Launcher detects the change!
        Set-Location -Path "C:\Users\Arash\Desktop\Lads Client\The Lads Client Packwiz"
        packwiz refresh
        Set-Location -Path $coreDir
    }
}

Write-Host ">>> Building TheLadsLauncher..."
Set-Location -Path $launcherDir
dotnet publish -c Release -r win-x64 --self-contained
if ($LASTEXITCODE -ne 0) {
    Write-Error "Dotnet build failed."
}

Write-Host ">>> Deploying TheLadsLauncher to AppData..."
$deployTargetDir1 = "C:\Users\Arash\AppData\Local\The Lads Client"
$deployTargetDir2 = "C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher_Clean\bin\NewBuild"

Write-Host ">>> Closing any running TheLadsLauncher processes..."
Stop-Process -Name "TheLadsLauncher" -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1.5

Write-Host ">>> Deploying TheLadsLauncher to AppData..."
if (-not (Test-Path $deployTargetDir1)) {
    New-Item -ItemType Directory -Path $deployTargetDir1 -Force | Out-Null
}
Copy-Item -Path "$launcherDir\bin\Release\net8.0-windows\win-x64\publish\*" -Destination $deployTargetDir1 -Recurse -Force

Write-Host ">>> Deploying TheLadsLauncher to Clean NewBuild bin folder..."
if (-not (Test-Path $deployTargetDir2)) {
    New-Item -ItemType Directory -Path $deployTargetDir2 -Force | Out-Null
}
Copy-Item -Path "$launcherDir\bin\Release\net8.0-windows\win-x64\publish\*" -Destination $deployTargetDir2 -Recurse -Force

Write-Host ">>> Done."
