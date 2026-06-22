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
$process = Start-Process -FilePath $gradlew -ArgumentList "build", "-x", "test" -PassThru -NoNewWindow -Wait
if ($process.ExitCode -ne 0) {
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
        $newStr = "file = ""
hash = """
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
$process = Start-Process -FilePath "dotnet" -ArgumentList "publish", "-c", "Release", "-r", "win-x64", "--self-contained" -PassThru -NoNewWindow -Wait
if ($process.ExitCode -ne 0) {
    Write-Error "Dotnet build failed."
}

Write-Host ">>> Done."
