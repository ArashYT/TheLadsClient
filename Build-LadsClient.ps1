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

    # --- 2b) Patch the access widener into the jar ---
    # Fabric Loom caches the embedded access widener and does NOT re-embed edits to
    # scalablelux.accesswidener (even after `clean`), so the built jar ships a stale
    # copy missing the entries merged from shaded mods (cursors_extended CursorType,
    # JEI, raised, respackopts, etc.). That causes IllegalAccessError crashes at
    # runtime. Re-embed the current source AW directly so the jar is always correct.
    $awSrc = Join-Path $Core 'scalablelux.accesswidener'
    if (Test-Path $awSrc) {
        Add-Type -AssemblyName System.IO.Compression
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $awText = [System.IO.File]::ReadAllText($awSrc)
        $zip = [System.IO.Compression.ZipFile]::Open($jar.FullName, [System.IO.Compression.ZipArchiveMode]::Update)
        $entry = $zip.GetEntry('scalablelux.accesswidener')
        if ($entry) { $entry.Delete() }
        $ne = $zip.CreateEntry('scalablelux.accesswidener')
        $sw = New-Object System.IO.StreamWriter($ne.Open()); $sw.Write($awText); $sw.Dispose()
        $zip.Dispose()
        Ok "Access widener re-embedded into jar ($((($awText -split "`n" | Where-Object { $_ -match '^(accessible|extendable|mutable)\s' }).Count)) entries)."
    }

    if ($NoDeploy) { Ok "Build complete (deploy skipped)."; return }

    # --- 3) deploy ---
    $target = Join-Path $ModsDir "TheLadsCore-$ver.jar"
    Copy-Item $jar.FullName $target -Force
    Ok "Deployed -> $target"

    # Clean up old standalone and old version jars from Packwiz mods folder
    # These mods are shaded into TheLadsCore — standalone copies cause duplicate mod ID conflicts
    Info "Cleaning up old standalone jars from Packwiz mods..."
    $shadedModPatterns = @(
        "capes-*.jar",
        "render scale *.jar",
        # Mods source-ported into TheLadsCore (alwayson.*) — standalone copies
        # double-apply the same mixins and crash (e.g. ImmediatelyFast GlCommandEncoder,
        # rsls SoundSystem, HyperLaunch/ViaFabricPlus executor cast).
        "ImmediatelyFast-*.jar",
        "entityculling-*.jar",
        "skinlayers3d-*.jar",
        "SmoothScrollingRefurbished*.jar",
        "BetterRenderDistance-*.jar",
        "betterstats-*.jar",
        "advancements_reloaded-*.jar",
        "vmp-fabric-*.jar",
        "hyperlaunch-*.jar",
        "rsls-*.jar",
        "xaerominimap-*.jar",
        "xaeroworldmap-*.jar",
        "ScalableLux-*.jar",
        "clientsort-*.jar",
        "serverpingerfixer*.jar",
        "raised-*.jar",
        "quick-pack-*.jar",
        "passiveshield-*.jar",
        "immersive-hotbar-*.jar",
        "screenfx-*.jar",
        "fancy-door-anim-*.jar",
        "Threads-*.jar",
        "waveycapes-*.jar",
        "waveycapes-fabric-*.jar",
        "appleskin-*.jar",
        "entity-view-distance-*.jar",
        "notenoughanimations-*.jar",
        "clienttweaks-*.jar",
        "cursors_extended-*.jar",
        "respackopts-*.jar",
        "Ixeris-*.jar",
        "betterf1-*.jar",
        "EnhancedTooltips-*.jar",
        "ExtremeSoundMuffler-*.jar",
        "jei-*.jar"
    )
    foreach ($pattern in $shadedModPatterns) {
        Get-ChildItem -Path $ModsDir -Filter $pattern -ErrorAction SilentlyContinue | Remove-Item -Force
    }
    Get-ChildItem -Path $ModsDir -Filter "TheLadsCore-*.jar" | Where-Object { $_.Name -ne "TheLadsCore-$ver.jar" } | Remove-Item -Force

    # Clean up index.toml entries for deleted standalone mods
    if (Test-Path $IndexToml) {
        $idxContent = [System.IO.File]::ReadAllText($IndexToml)
        $parts = $idxContent -split '\[\[files\]\]'
        $newParts = @()
        $newParts += $parts[0] # The header

        $excludePatterns = @(
            'file = "mods/ImmediatelyFast-.*"',
            'file = "mods/entityculling-.*"',
            'file = "mods/skinlayers3d-.*"',
            'file = "mods/SmoothScrollingRefurbished.*"',
            'file = "mods/BetterRenderDistance-.*"',
            'file = "mods/betterstats-.*"',
            'file = "mods/advancements_reloaded-.*"',
            'file = "mods/vmp-fabric-.*"',
            'file = "mods/hyperlaunch-.*"',
            'file = "mods/rsls-.*"',
            'file = "mods/xaerominimap-.*"',
            'file = "mods/xaeroworldmap-.*"',
            'file = "mods/ScalableLux-.*"',
            'file = "mods/capes-.*"',
            'file = "mods/render scale .*"',
            'file = "mods/clientsort-.*"',
            'file = "mods/serverpingerfixer.*"',
            'file = "mods/raised-.*"',
            'file = "mods/quick-pack-.*"',
            'file = "mods/passiveshield-.*"',
            'file = "mods/immersive-hotbar-.*"',
            'file = "mods/screenfx-.*"',
            'file = "mods/fancy-door-anim-.*"',
            'file = "mods/Threads-.*"',
            'file = "mods/waveycapes-.*"',
            'file = "mods/appleskin-.*"',
            'file = "mods/entity-view-distance-.*"',
            'file = "mods/notenoughanimations-.*"',
            'file = "mods/clienttweaks-.*"',
            'file = "mods/cursors_extended-.*"',
            'file = "mods/respackopts-.*"',
            'file = "mods/Ixeris-.*"',
            'file = "mods/betterf1-.*"',
            'file = "mods/EnhancedTooltips-.*"',
            'file = "mods/ExtremeSoundMuffler-.*"',
            'file = "mods/jei-.*"'
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
                # Clean up standalone jars for mods shaded into TheLadsCore (prevents duplicate mod ID conflicts)
                foreach ($pattern in $shadedModPatterns) {
                    Get-ChildItem -Path $instMods -Filter $pattern -ErrorAction SilentlyContinue | Remove-Item -Force
                }
                
                $instTarget = Join-Path $instMods "TheLadsCore-$ver.jar"
                Copy-Item $jar.FullName $instTarget -Force
                Ok "Copied to CurseForge instance -> $instTarget"
            }
        }
    }

    # Copy to The Lads Client instance if it exists
    $ladsClientPath = "C:\The Lads Client\mods"
    if (Test-Path $ladsClientPath) {
        # Clean up old versions of TheLadsCore from this instance mods folder first
        Get-ChildItem -Path $ladsClientPath -Filter "TheLadsCore-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
        # Clean up standalone jars for mods shaded into TheLadsCore (prevents duplicate mod ID conflicts)
        foreach ($pattern in $shadedModPatterns) {
            Get-ChildItem -Path $ladsClientPath -Filter $pattern -ErrorAction SilentlyContinue | Remove-Item -Force
        }
        
        $ladsTarget = Join-Path $ladsClientPath "TheLadsCore-$ver.jar"
        Copy-Item $jar.FullName $ladsTarget -Force
        Ok "Copied to Lads Client instance -> $ladsTarget"
    }

    # --- 4) refresh Packwiz hashes ---
    if (Test-Path $IndexToml) {
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
    } else {
        Info "index.toml not found; skipped Packwiz hash refresh."
    }

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
