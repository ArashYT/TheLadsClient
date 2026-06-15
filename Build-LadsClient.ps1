<#
  Build-LadsClient.ps1
  -------------------------------------------------------------------
  Build for The Lads Client.

    1. Compiles TheLadsCore (Fabric mod) with Gradle.
    2. Copies the freshly built jar into the Packwiz pack's mods folder.
    3. Refreshes the Packwiz hashes (index.toml + pack.toml) so the
       launcher's packwiz-installer accepts the new jar.
    4. Optionally builds the C# launcher with  -Launcher

  Usage (or just double-click Build.bat):
      .\Build-LadsClient.ps1                 # build mod + deploy to pack
      .\Build-LadsClient.ps1 -Watch          # build, then auto-rebuild on changes
      .\Build-LadsClient.ps1 -Launcher       # also build the launcher
      .\Build-LadsClient.ps1 -NoDeploy       # compile only, don't touch the pack
  -------------------------------------------------------------------
#>
param(
    [switch]$Launcher,   # also build the C# launcher (needs the .NET SDK)
    [switch]$NoDeploy,   # build only; skip copying into the pack
    [switch]$Watch       # keep running and rebuild automatically on source changes
)

$Root      = Split-Path -Parent $MyInvocation.MyCommand.Path
$Core      = Join-Path $Root 'TheLadsCore'
$Pack      = Join-Path $Root 'The Lads Client Packwiz'
$ModsDir   = Join-Path $Pack 'mods'
$IndexToml = Join-Path $Pack 'index.toml'
$PackToml  = Join-Path $Pack 'pack.toml'

function Info($m){ Write-Host "[build] $m" -ForegroundColor Cyan }
function Ok($m)  { Write-Host "[ ok ] $m" -ForegroundColor Green }
function Fail($m){ Write-Host "[fail] $m" -ForegroundColor Red }

# Runs one full build+deploy. Throws on failure so the watch loop can recover.
function Invoke-Build {
    # --- read mod version ---
    $verLine = Select-String -Path (Join-Path $Core 'gradle.properties') -Pattern '^\s*mod_version\s*=\s*(.+)$'
    if (-not $verLine) { throw "mod_version not found in gradle.properties" }
    $ver = $verLine.Matches[0].Groups[1].Value.Trim()
    Info "TheLadsCore version: $ver"

    # --- 1) compile ---
    Info "Running Gradle build (first run downloads Minecraft + mappings, be patient)..."
    Push-Location $Core
    try {
        & (Join-Path $Core 'gradlew.bat') build -x test --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit code $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
    Ok "Mod compiled."

    # --- 2) find jar ---
    $jar = Get-ChildItem (Join-Path $Core 'build\libs') -Filter '*.jar' -ErrorAction Stop |
           Where-Object { $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-dev.jar' } |
           Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) { throw "No output jar found in build\libs" }
    Info "Built jar: $($jar.Name)"

    if ($NoDeploy) { Ok "Build complete (deploy skipped)."; return }

    # --- 3) deploy ---
    $target = Join-Path $ModsDir "TheLadsCore-$ver.jar"
    Copy-Item $jar.FullName $target -Force
    Ok "Deployed -> $target"

    # Clean up old standalone and old version jars from Packwiz mods folder
    Info "Cleaning up old standalone jars from Packwiz mods..."
    Get-ChildItem -Path $ModsDir -Filter "capes-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem -Path $ModsDir -Filter "render scale *.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem -Path $ModsDir -Filter "xaerominimap-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem -Path $ModsDir -Filter "xaeroworldmap-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem -Path $ModsDir -Filter "ScalableLux-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem -Path $ModsDir -Filter "TheLadsCore-*.jar" | Where-Object { $_.Name -ne "TheLadsCore-$ver.jar" } | Remove-Item -Force

    # Clean up index.toml entries for deleted standalone mods
    if (Test-Path $IndexToml) {
        $idxContent = [System.IO.File]::ReadAllText($IndexToml)
        $parts = $idxContent -split '\[\[files\]\]'
        $newParts = @()
        $newParts += $parts[0] # The header

        $excludePatterns = @(
            'file = "mods/xaerominimap-.*"',
            'file = "mods/xaeroworldmap-.*"',
            'file = "mods/ScalableLux-.*"',
            'file = "mods/capes-.*"',
            'file = "mods/render scale .*"'
        )

        for ($i = 1; $i -lt $parts.Length; $i++) {
            $part = $parts[$i]
            $exclude = $false
            foreach ($pat in $excludePatterns) {
                if ($part -match $pat) {
                    $exclude = $true
                    break
                }
            }
            if (-not $exclude) {
                $newParts += "[[files]]" + $part
            }
        }
        $newIdxContent = $newParts -join ""
        [System.IO.File]::WriteAllText($IndexToml, $newIdxContent)
        Ok "Cleaned up standalone entries from index.toml."
    }

    # Copy to CurseForge instances if they exist
    $instancesPath = "C:\Users\Arash\curseforge\minecraft\Instances"
    if (Test-Path $instancesPath) {
        $devInstances = Get-ChildItem -Path $instancesPath -Directory | Where-Object { $_.Name -like "The Lads Client Dev*" -or $_.Name -eq "The Lads Fort" }
        foreach ($inst in $devInstances) {
            $instMods = Join-Path $inst.FullName "mods"
            if (Test-Path $instMods) {
                # Clean up old versions of TheLadsCore from this instance mods folder first
                Get-ChildItem -Path $instMods -Filter "TheLadsCore-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
                # Clean up old standalone jars from the instance mods folder
                Get-ChildItem -Path $instMods -Filter "capes-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
                Get-ChildItem -Path $instMods -Filter "render scale *.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
                Get-ChildItem -Path $instMods -Filter "xaerominimap-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
                Get-ChildItem -Path $instMods -Filter "xaeroworldmap-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
                Get-ChildItem -Path $instMods -Filter "ScalableLux-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
                
                $instTarget = Join-Path $instMods "TheLadsCore-$ver.jar"
                Copy-Item $jar.FullName $instTarget -Force
                Ok "Copied to CurseForge instance -> $instTarget"
            }
        }
    }

    # --- 4) refresh Packwiz hashes ---
    $rel     = "mods/TheLadsCore-$ver.jar"
    $newHash = (Get-FileHash $target -Algorithm SHA256).Hash.ToLower()

    $idx = [System.IO.File]::ReadAllText($IndexToml)
    $jarPattern = 'file = "mods/TheLadsCore-[^`"]+"\r?\nhash = "[0-9a-fA-F]{64}"'
    if (-not ($idx -match $jarPattern)) { throw "Entry for mods/TheLadsCore-*.jar not found in index.toml" }
    
    # Standardize newlines to handle replacement cleanly
    $replacement = "file = `"$rel`"`r`nhash = `"$newHash`""
    $idx = [regex]::Replace($idx, 'file = "mods/TheLadsCore-[^`"]+"\r?\nhash = "[0-9a-fA-F]{64}"', $replacement)
    [System.IO.File]::WriteAllText($IndexToml, $idx)

    $idxHash = (Get-FileHash $IndexToml -Algorithm SHA256).Hash.ToLower()
    $pack = [System.IO.File]::ReadAllText($PackToml)
    $pack = [regex]::Replace($pack, '(\[index\][\s\S]*?hash = ")[0-9a-fA-F]{64}(")', ('${1}' + $idxHash + '${2}'))
    [System.IO.File]::WriteAllText($PackToml, $pack)
    Ok "Packwiz hashes refreshed (jar + index)."

    # --- 5) optional launcher ---
    if ($Launcher) {
        if (Get-Command dotnet -ErrorAction SilentlyContinue) {
            Info "Building launcher (dotnet build -c Release)..."
            dotnet build (Join-Path $Root 'TheLadsLauncher_Clean\TheLadsLauncher.csproj') -c Release
            if ($LASTEXITCODE -ne 0) { throw "Launcher build failed (exit code $LASTEXITCODE)" }
            Ok "Launcher built."
        } else {
            Fail "dotnet SDK not found on PATH; skipped launcher build."
        }
    }

    Ok "Build finished."
}

# Latest modification time across the things worth rebuilding for.
function Get-SourceStamp {
    $paths = @((Join-Path $Core 'src'), (Join-Path $Core 'build.gradle'), (Join-Path $Core 'gradle.properties'))
    (Get-ChildItem -Path $paths -Recurse -File -ErrorAction SilentlyContinue |
        Measure-Object -Property LastWriteTimeUtc -Maximum).Maximum
}

if ($Watch) {
    Info "Watch mode: building now, then auto-rebuilding when you save source changes."
    try { Invoke-Build } catch { Fail $_.Exception.Message }
    $last = Get-SourceStamp
    Info "Watching $Core\src for changes... (press Ctrl+C to stop)"
    while ($true) {
        Start-Sleep -Seconds 2
        $cur = Get-SourceStamp
        if ($cur -ne $null -and $cur -gt $last) {
            Start-Sleep -Milliseconds 600   # debounce multi-file saves
            $last = Get-SourceStamp
            Write-Host ""
            Info "Change detected - rebuilding ($(Get-Date -Format HH:mm:ss))..."
            try { Invoke-Build } catch { Fail $_.Exception.Message }
            Info "Watching for changes... (press Ctrl+C to stop)"
        }
    }
} else {
    try {
        Invoke-Build
    } catch {
        Fail $_.Exception.Message
        exit 1
    }
}
