<#
.SYNOPSIS
    Builds the local LadsClientAPI QA test network under F:\LadsQA\ladsapi-net on ARASH-LEGION.

.DESCRIPTION
    Idempotently sets up the 5-node test network:
      1. Velocity 3.6.0 proxy on port 25700 (modern forwarding)
      2. Paper 26.3 backend "lobby" on port 25701 (Via* plugins + LadsClientAPI + rules-qa.yml)
      3. Paper 26.3 backend "plain" on port 25702 (Via* plugins + PacketLogger, NO LadsClientAPI)
      4. Fabric 26.3 server on port 25703 (Fabric Loader 0.19.5 + Fabric API 0.163.0+26.3 + LadsClientAPI)
      5. Forge 1.20.1-47.4.10 server on port 25704 (LadsClientAPI)

    Jars are sourced primarily from \\Arash-HP\Servers or C:\LadsQA\shared\.qa-cache,
    with fallback to pinned official download URLs.

.PARAMETER Root
    Target directory for the network. Defaults to F:\LadsQA\ladsapi-net.

.PARAMETER Jar
    Optional path to the built LadsClientAPI jar. When provided, copied into:
    - lobby/plugins
    - velocity/plugins
    - fabric/mods
    - forge/mods
    (and explicitly removed/excluded from plain/plugins).

.PARAMETER ForceDownload
    Forces downloading from official URLs even if network shares or local caches are reachable.
#>
[CmdletBinding()]
param(
    [string]$Root = 'F:\LadsQA\ladsapi-net',
    [string]$Jar = '',
    [switch]$ForceDownload
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " [LadsQA] Setting up LadsClientAPI Test Network" -ForegroundColor Cyan
Write-Host " Target root: $Root" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Pinned official URLs & build identifiers (used if UNC shares/local caches are unreachable)
$PINNED = @{
    PaperVersion        = '26.3'
    PaperBuild          = '200'
    PaperUrl            = 'https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/130/downloads/paper-1.21.1-130.jar' # fallback
    VelocityVersion      = '3.6.0-SNAPSHOT'
    VelocityBuild        = '613'
    VelocityJarName     = 'velocity-3.6.0-SNAPSHOT-613.jar'
    VelocityUrl         = 'https://api.papermc.io/v2/projects/velocity/versions/3.3.0-SNAPSHOT/builds/440/downloads/velocity-3.3.0-SNAPSHOT-440.jar'
    ViaVersionUrl       = 'https://github.com/ViaVersion/ViaVersion/releases/download/5.2.1/ViaVersion-5.2.1.jar'
    ViaBackwardsUrl     = 'https://github.com/ViaVersion/ViaBackwards/releases/download/5.2.1/ViaBackwards-5.2.1.jar'
    ViaRewindUrl        = 'https://github.com/ViaVersion/ViaRewind/releases/download/4.0.2/ViaRewind-4.0.2.jar'
    FabricInstallerUrl  = 'https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar'
    FabricApiUrl        = 'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.163.0+26.3/fabric-api-0.163.0+26.3.jar'
    ForgeInstallerUrl   = 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-installer.jar'
}

# Local & UNC candidate source paths
$UNC_ARASH_HP = '\\Arash-HP\Servers'
$LEGION_SHARED = 'C:\LadsQA\shared'
$LEGION_CACHE  = 'C:\LadsQA\shared\.qa-cache'
$FORWARDING_SECRET = 'XG3HSQwrmgXZ'

# 1. Ensure Directories
$dirs = @(
    "$Root\velocity\plugins",
    "$Root\lobby\plugins",
    "$Root\lobby\config",
    "$Root\lobby\plugins\LadsClientAPI",
    "$Root\plain\plugins",
    "$Root\plain\config",
    "$Root\fabric\mods",
    "$Root\fabric\config\ladsclientapi",
    "$Root\forge\mods",
    "$Root\forge\config\ladsclientapi"
)
foreach ($d in $dirs) {
    if (-not (Test-Path $d)) {
        New-Item -ItemType Directory -Path $d -Force | Out-Null
    }
}

# Helper to locate or download a file
function Get-OrDownloadFile {
    param(
        [string]$Destination,
        [string[]]$CandidatePaths,
        [string]$DownloadUrl,
        [string]$Description
    )

    if (Test-Path $Destination) {
        Write-Host "  [OK] $Description already present: $Destination" -ForegroundColor Green
        return
    }

    if (-not $ForceDownload) {
        foreach ($cand in $CandidatePaths) {
            if (-not [string]::IsNullOrWhiteSpace($cand) -and (Test-Path $cand)) {
                Write-Host "  -> Copying $Description from $cand" -ForegroundColor Gray
                Copy-Item $cand -Destination $Destination -Force
                return
            }
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($DownloadUrl)) {
        Write-Host "  -> Downloading $Description from $DownloadUrl" -ForegroundColor Yellow
        $parent = Split-Path $Destination -Parent
        if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
        Invoke-WebRequest -Uri $DownloadUrl -OutFile $Destination -UseBasicParsing
        return
    }

    throw "Could not find or download required asset: $Description"
}

# Helper to find Java executable
function Resolve-Java {
    param([int]$TargetMajor)
    $candidates = @()
    if ($TargetMajor -ge 21) {
        $candidates += "$LEGION_CACHE\runtime-data\runtime\java-25\*\bin\java.exe"
        $candidates += "\\192.168.2.22\C\LadsQA\shared\.qa-cache\runtime-data\runtime\java-25\*\bin\java.exe"
        if ($env:JAVA_HOME_25) { $candidates += "$env:JAVA_HOME_25\bin\java.exe" }
        if ($env:JAVA_HOME_21) { $candidates += "$env:JAVA_HOME_21\bin\java.exe" }
    } else {
        $candidates += "$LEGION_CACHE\runtime-data\runtime\java-17\*\bin\java.exe"
        $candidates += "\\192.168.2.22\C\LadsQA\shared\.qa-cache\runtime-data\runtime\java-17\*\bin\java.exe"
        if ($env:JAVA_HOME_17) { $candidates += "$env:JAVA_HOME_17\bin\java.exe" }
    }
    if ($env:JAVA_HOME) { $candidates += "$env:JAVA_HOME\bin\java.exe" }
    $candidates += (Get-Command java.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)

    foreach ($pattern in $candidates) {
        if (-not $pattern) { continue }
        $resolved = Resolve-Path $pattern -ErrorAction SilentlyContinue
        if ($resolved) {
            $path = $resolved[0].Path
            if (Test-Path $path) { return $path }
        }
    }
    return "java" # fallback to PATH
}

$java25 = Resolve-Java 25
$java17 = Resolve-Java 17
Write-Host "  Java 25/21 path: $java25" -ForegroundColor DarkGray
Write-Host "  Java 17 path:    $java17" -ForegroundColor DarkGray

# 2. Paper 26.3 Jars
Write-Host "`n[1/5] Setting up Paper 26.3 jars..." -ForegroundColor Cyan
$paperCandidates = @(
    "$UNC_ARASH_HP\The Lads Network Lobby - Paper 1.21.11\versions\26.3\paper-26.3.jar",
    "$UNC_ARASH_HP\The Lads Network Lobby - Paper 1.21.11\paper-26.3-200.jar",
    "$LEGION_CACHE\modpack-cache\versions\26.3\26.3.jar"
)
Get-OrDownloadFile -Destination "$Root\lobby\paper-26.3.jar" -CandidatePaths $paperCandidates -DownloadUrl $PINNED.PaperUrl -Description "Paper 26.3 server jar (lobby)"
Get-OrDownloadFile -Destination "$Root\plain\paper-26.3.jar" -CandidatePaths $paperCandidates -DownloadUrl $PINNED.PaperUrl -Description "Paper 26.3 server jar (plain)"

# 3. Velocity 3.6.0 Jar
Write-Host "`n[2/5] Setting up Velocity 3.6.0 jar..." -ForegroundColor Cyan
$velocityCandidates = @(
    "$UNC_ARASH_HP\The Lads Network - Velocity\$($PINNED.VelocityJarName)",
    "$UNC_ARASH_HP\The Lads Network - Velocity\velocity-3.6.0-SNAPSHOT-613.jar",
    "$UNC_ARASH_HP\The Lads Network - Velocity\velocity.jar"
)
Get-OrDownloadFile -Destination "$Root\velocity\velocity.jar" -CandidatePaths $velocityCandidates -DownloadUrl $PINNED.VelocityUrl -Description "Velocity 3.6.0 jar"

# 4. ViaVersion Plugins (for 1.8.9 / 1.20.1 backward protocol support on 26.3)
Write-Host "`n[3/5] Setting up ViaVersion plugins..." -ForegroundColor Cyan
$viaPlugins = @(
    @{ Name = 'ViaVersion.jar';               Cand = "$UNC_ARASH_HP\The Lads Network Lobby - Paper 1.21.11\plugins\ViaVersion.jar";               Url = $PINNED.ViaVersionUrl },
    @{ Name = 'ViaBackwards.jar';             Cand = "$UNC_ARASH_HP\The Lads Network Lobby - Paper 1.21.11\plugins\ViaBackwards.jar";             Url = $PINNED.ViaBackwardsUrl },
    @{ Name = 'ViaRewind.jar';                Cand = "$UNC_ARASH_HP\The Lads Network Lobby - Paper 1.21.11\plugins\ViaRewind.jar";                Url = $PINNED.ViaRewindUrl },
    @{ Name = 'ViaRewind-Legacy-Support.jar'; Cand = "$UNC_ARASH_HP\The Lads Network Lobby - Paper 1.21.11\plugins\ViaRewind-Legacy-Support.jar"; Url = '' }
)
foreach ($vp in $viaPlugins) {
    Get-OrDownloadFile -Destination "$Root\lobby\plugins\$($vp.Name)" -CandidatePaths @($vp.Cand) -DownloadUrl $vp.Url -Description "Plugin $($vp.Name) (lobby)"
    Get-OrDownloadFile -Destination "$Root\plain\plugins\$($vp.Name)" -CandidatePaths @($vp.Cand) -DownloadUrl $vp.Url -Description "Plugin $($vp.Name) (plain)"
}

# 5. Fabric 26.3 Server (Loader 0.19.5 + Fabric API 0.163.0+26.3)
Write-Host "`n[4/5] Setting up Fabric 26.3 server..." -ForegroundColor Cyan
$fabricInstaller = "$Root\fabric\fabric-installer.jar"
$fabricInstallerCands = @(
    "$LEGION_CACHE\modpack-cache\installers\fabric-installer-1.1.2.jar",
    "$LEGION_CACHE\modpack-cache\installers\fabric-installer.jar"
)
Get-OrDownloadFile -Destination $fabricInstaller -CandidatePaths $fabricInstallerCands -DownloadUrl $PINNED.FabricInstallerUrl -Description "Fabric Installer"

if (-not (Test-Path "$Root\fabric\fabric-server-launch.jar")) {
    Write-Host "  -> Running Fabric Server installer (Loader 0.19.5, MC 26.3)..." -ForegroundColor Yellow
    Push-Location "$Root\fabric"
    try {
        & $java25 -jar $fabricInstaller server -mcversion 26.3 -loader 0.19.5 -downloadMinecraft | Out-Null
    } finally {
        Pop-Location
    }
}
$fabricApiCands = @(
    "$LEGION_CACHE\mod-jars\26.3\fabric-api-0.163.0+26.3.jar",
    "$LEGION_CACHE\fabric-api-0.163.0+26.3.jar"
)
Get-OrDownloadFile -Destination "$Root\fabric\mods\fabric-api-0.163.0+26.3.jar" -CandidatePaths $fabricApiCands -DownloadUrl $PINNED.FabricApiUrl -Description "Fabric API 0.163.0+26.3"

# 6. Forge 1.20.1-47.4.10 Server
Write-Host "`n[5/5] Setting up Forge 1.20.1 server..." -ForegroundColor Cyan
$forgeInstaller = "$Root\forge\forge-1.20.1-47.4.10-installer.jar"
$forgeInstallerCands = @(
    "$LEGION_CACHE\modpack-cache\installers\forge-1.20.1-47.4.10-installer.jar",
    "$LEGION_SHARED\.qa-cache\modpack-cache\installers\forge-1.20.1-47.4.10-installer.jar"
)
Get-OrDownloadFile -Destination $forgeInstaller -CandidatePaths $forgeInstallerCands -DownloadUrl $PINNED.ForgeInstallerUrl -Description "Forge 1.20.1-47.4.10 Installer"

if (-not (Test-Path "$Root\forge\libraries") -or -not (Test-Path "$Root\forge\run.bat")) {
    Write-Host "  -> Installing Forge 1.20.1-47.4.10 server..." -ForegroundColor Yellow
    Push-Location "$Root\forge"
    try {
        & $java17 -jar $forgeInstaller --installServer | Out-Null
    } finally {
        Pop-Location
    }
}

# 7. Write Configurations
Write-Host "`n[Config] Writing configuration files..." -ForegroundColor Cyan

# EULA for all Minecraft servers
@("$Root\lobby", "$Root\plain", "$Root\fabric", "$Root\forge") | ForEach-Object {
    Set-Content -Path "$_\eula.txt" -Value "eula=true`r`n" -Encoding ASCII -Force
}

# Velocity forwarding secret
Set-Content -Path "$Root\velocity\forwarding.secret" -Value "$FORWARDING_SECRET`r`n" -Encoding ASCII -Force

# Velocity configuration (velocity.toml)
$velocityToml = @"
config-version = "2.9"
bind = "0.0.0.0:25700"
motd = "<#09add3>Lads QA Velocity"
show-max-players = 100
online-mode = false
force-key-authentication = false
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"
announce-forge = true

[servers]
lobby = "127.0.0.1:25701"
plain = "127.0.0.1:25702"
fabric = "127.0.0.1:25703"
forge = "127.0.0.1:25704"
try = ["lobby"]

[forced-hosts]
"@
Set-Content -Path "$Root\velocity\velocity.toml" -Value $velocityToml -Encoding UTF8 -Force

# Paper global configuration for Velocity modern forwarding
$paperGlobalYml = @"
_version: 31
proxies:
  bungee-cord:
    online-mode: false
  proxy-protocol: false
  velocity:
    enabled: true
    online-mode: false
    secret: $FORWARDING_SECRET
chunk-loading-basic:
  player-max-chunk-generate-rate: -1.0
  player-max-chunk-load-rate: 100.0
  player-max-chunk-send-rate: 45.0
misc:
  enable-nether: false
watchdog:
  early-warning-delay: 60000
  early-warning-every: 30000
"@
Set-Content -Path "$Root\lobby\config\paper-global.yml" -Value $paperGlobalYml -Encoding UTF8 -Force
Set-Content -Path "$Root\plain\config\paper-global.yml" -Value $paperGlobalYml -Encoding UTF8 -Force

# Server.properties helper
function Write-ServerProperties {
    param([string]$Path, [int]$Port, [string]$Motd)
    $props = @"
server-port=$Port
server-ip=127.0.0.1
online-mode=false
view-distance=4
simulation-distance=4
max-players=20
motd=$Motd
difficulty=peaceful
spawn-protection=0
generate-structures=false
allow-nether=false
sync-chunk-writes=false
"@
    Set-Content -Path $Path -Value $props -Encoding ASCII -Force
}

Write-ServerProperties -Path "$Root\lobby\server.properties" -Port 25701 -Motd "Lads QA Lobby"
Write-ServerProperties -Path "$Root\plain\server.properties" -Port 25702 -Motd "Lads QA Plain"
Write-ServerProperties -Path "$Root\fabric\server.properties" -Port 25703 -Motd "Lads QA Fabric"
Write-ServerProperties -Path "$Root\forge\server.properties"  -Port 25704 -Motd "Lads QA Forge"

# 8. Deploy rules-qa.yml
$rulesSource = Join-Path $PSScriptRoot "rules-qa.yml"
if (Test-Path $rulesSource) {
    Copy-Item $rulesSource -Destination "$Root\lobby\plugins\LadsClientAPI\rules.yml" -Force
    Copy-Item $rulesSource -Destination "$Root\fabric\config\ladsclientapi\rules.yml" -Force
    Copy-Item $rulesSource -Destination "$Root\forge\config\ladsclientapi\rules.yml" -Force
    Copy-Item $rulesSource -Destination "$Root\velocity\plugins\ladsclientapi\rules.yml" -Force
    Write-Host "  -> Deployed rules-qa.yml to lobby, fabric, forge, and velocity configs" -ForegroundColor Green
}

# 9. Deploy PacketLogger plugin to plain server
$packetLoggerSource = Join-Path $PSScriptRoot "packet-logger\packet-logger.jar"
$packetLoggerSrcJava = Join-Path $PSScriptRoot "packet-logger\src\com\thelads\qa\PacketLoggerPlugin.java"
$packetLoggerYml = Join-Path $PSScriptRoot "packet-logger\src\plugin.yml"

if (-not (Test-Path $packetLoggerSource) -and (Test-Path $packetLoggerSrcJava)) {
    # Compile PacketLogger plugin if jar isn't present
    $compileDir = Join-Path $PSScriptRoot "packet-logger\build-tmp"
    if (Test-Path $compileDir) { Remove-Item $compileDir -Recurse -Force }
    New-Item -ItemType Directory -Path $compileDir -Force | Out-Null

    $paperApiCand = Resolve-Path "$UNC_ARASH_HP\The Lads Network Lobby - Paper 1.21.11\libraries\io\papermc\paper\paper-api\26.3*\*.jar" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Path -First 1
    $advApiCand   = Resolve-Path "$UNC_ARASH_HP\The Lads Network Lobby - Paper 1.21.11\libraries\net\kyori\adventure-api\5.2.0\*.jar" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Path -First 1
    $advKeyCand   = Resolve-Path "$UNC_ARASH_HP\The Lads Network Lobby - Paper 1.21.11\libraries\net\kyori\adventure-key\5.2.0\*.jar" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Path -First 1
    $cp = @($paperApiCand, $advApiCand, $advKeyCand) -join ';'

    $javacCmd = Get-Command javac.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    $jarCmd   = Get-Command jar.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if ($javacCmd -and $jarCmd -and $paperApiCand) {
        Write-Host "  -> Compiling PacketLogger plugin..." -ForegroundColor Yellow
        & $javacCmd -cp $cp -d $compileDir $packetLoggerSrcJava
        Copy-Item $packetLoggerYml -Destination (Join-Path $compileDir "plugin.yml")
        & $jarCmd -cf $packetLoggerSource -C $compileDir .
        Remove-Item $compileDir -Recurse -Force
    }
}

if (Test-Path $packetLoggerSource) {
    Copy-Item $packetLoggerSource -Destination "$Root\plain\plugins\packet-logger.jar" -Force
    Write-Host "  -> Deployed PacketLogger plugin to plain server" -ForegroundColor Green
}

# 10. Copy LadsClientAPI Jar (if provided)
if (-not [string]::IsNullOrWhiteSpace($Jar)) {
    if (-not (Test-Path $Jar)) {
        Write-Warning "Supplied -Jar parameter does not exist: $Jar"
    } else {
        Write-Host "`n[Jar] Deploying LadsClientAPI jar from $Jar..." -ForegroundColor Yellow
        Copy-Item $Jar -Destination "$Root\lobby\plugins\LadsClientAPI.jar" -Force
        Copy-Item $Jar -Destination "$Root\velocity\plugins\LadsClientAPI.jar" -Force
        Copy-Item $Jar -Destination "$Root\fabric\mods\LadsClientAPI.jar" -Force
        Copy-Item $Jar -Destination "$Root\forge\mods\LadsClientAPI.jar" -Force

        # Ensure PLAIN backend never receives the jar (proves zero leak)
        $plainJar = "$Root\plain\plugins\LadsClientAPI.jar"
        if (Test-Path $plainJar) {
            Remove-Item $plainJar -Force
        }
        Write-Host "  -> Deployed to lobby, velocity, fabric, and forge (plain left CLEAN)" -ForegroundColor Green
    }
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " [LadsQA] Network setup complete at $Root" -ForegroundColor Green
Write-Host " To start: .\start-network.ps1 -Root `"$Root`"" -ForegroundColor Green
Write-Host " To stop:  .\stop-network.ps1 -Root `"$Root`"" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
