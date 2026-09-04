# ==============================================================================
# The Lads Launcher - C# Services In-Memory Adversarial Edge Cases
# ==============================================================================

$ErrorActionPreference = "Stop"

$workspaceRoot = "C:\Users\Arash\Desktop\The Lads Client Dev\Lads Client"
$launcherBinDir = Join-Path $workspaceRoot "TheLadsLauncher\bin\Debug\net8.0-windows"
$dllPath = Join-Path $launcherBinDir "TheLadsLauncher.dll"

# Assembly resolver
[System.AppDomain]::CurrentDomain.add_AssemblyResolve({
    param($sender, $args)
    $name = (New-Object System.Reflection.AssemblyName($args.Name)).Name
    $candidate = Join-Path "C:\Users\Arash\Desktop\The Lads Client Dev\Lads Client\TheLadsLauncher\bin\Debug\net8.0-windows" "$name.dll"
    if (Test-Path $candidate) {
        return [System.Reflection.Assembly]::LoadFrom($candidate)
    }
    return $null
})

$asm = [System.Reflection.Assembly]::LoadFrom($dllPath)

$totalTests = 0
$passedTests = 0
$failedTests = 0
$failureDetails = [System.Collections.Generic.List[string]]::new()

function Run-TestCase {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    $script:totalTests++
    Write-Host -NoNewline "[TEST $script:totalTests] $Name ... "
    try {
        & $Action
        Write-Host "PASS" -ForegroundColor Green
        $script:passedTests++
    } catch {
        Write-Host "FAIL: $($_.Exception.Message)" -ForegroundColor Red
        $script:failedTests++
        $script:failureDetails.Add("$Name : $($_.Exception.Message)`n$($_.ScriptStackTrace)")
    }
}

# Sandbox directory
$sandboxBase = Join-Path ([System.IO.Path]::GetTempPath()) ("lads_m2_adv_" + [System.Guid]::NewGuid().ToString("N"))
[System.IO.Directory]::CreateDirectory($sandboxBase) | Out-Null
Write-Host "Sandbox initialized at: $sandboxBase`n"

try {
    # --------------------------------------------------------------------------
    # SUITE 1: PATH SERVICE ADVERSARIAL TESTS
    # --------------------------------------------------------------------------
    Write-Host "--- SUITE 1: PathService Dynamic Resolution ---" -ForegroundColor Cyan

    Run-TestCase "PathService Custom Base Directory Resolution" {
        $customPath = Join-Path $sandboxBase "custom_base"
        $pathService = [TheLadsLauncher.Services.PathService]::new($customPath)
        
        if ($pathService.BaseDirectory -ne $customPath) {
            throw "BaseDirectory mismatch. Expected $customPath, got $($pathService.BaseDirectory)"
        }
        if ($pathService.SharedDirectory -ne (Join-Path $customPath "shared")) {
            throw "SharedDirectory mismatch"
        }
        if ($pathService.ProfilesDirectory -ne (Join-Path $customPath "profiles")) {
            throw "ProfilesDirectory mismatch"
        }
        if ($pathService.RuntimesDirectory -ne (Join-Path $customPath "runtime")) {
            throw "RuntimesDirectory mismatch"
        }
        if (-not (Test-Path $pathService.SharedDirectory)) {
            throw "SharedDirectory was not auto-created"
        }
    }

    Run-TestCase "PathService Fallback on Null or Whitespace Base Path" {
        $pathService = [TheLadsLauncher.Services.PathService]::new("")
        $expected = Join-Path ([System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::ApplicationData)) ".theladsclient"
        if ($pathService.BaseDirectory -ne $expected) {
            throw "Expected default %APPDATA%/.theladsclient, got $($pathService.BaseDirectory)"
        }
    }

    # --------------------------------------------------------------------------
    # SUITE 2: PROFILE VERSION SWITCHING & CRUD ADVERSARIAL TESTS
    # --------------------------------------------------------------------------
    Write-Host "`n--- SUITE 2: Profile Version Switching & Management ---" -ForegroundColor Cyan

    $pSandbox = Join-Path $sandboxBase "profile_tests"
    $pathSvc = [TheLadsLauncher.Services.PathService]::new($pSandbox)
    $profileSvc = [TheLadsLauncher.Services.ProfileService]::new($pathSvc)

    Run-TestCase "ProfileService Default Catalog Parity (1.21.1, 26.2, Latest Release)" {
        $profiles = $profileSvc.GetProfiles()
        if ($profiles.Count -lt 3) {
            throw "Expected at least 3 default profiles, got $($profiles.Count)"
        }

        $p121 = $profiles | Where-Object { $_.MinecraftVersion -eq "1.21.1" }
        if (-not $p121) { throw "Default profile 1.21.1 missing" }
        if ($p121.JavaMajorVersion -ne 21) { throw "Profile 1.21.1 must use Java 21, got $($p121.JavaMajorVersion)" }
        if ($p121.IsIsolated) { throw "Profile 1.21.1 must NOT be isolated by default" }

        $p262 = $profiles | Where-Object { $_.MinecraftVersion -eq "26.2" }
        if (-not $p262) { throw "Default profile 26.2 missing" }
        if ($p262.JavaMajorVersion -ne 25) { throw "Profile 26.2 must use Java 25, got $($p262.JavaMajorVersion)" }
        if ($p262.IsIsolated) { throw "Profile 26.2 must NOT be isolated by default" }

        $pLatest = $profiles | Where-Object { $_.MinecraftVersion -eq "latest.release" }
        if (-not $pLatest) { throw "Default profile latest.release missing" }
        if ($pLatest.JavaMajorVersion -ne 21) { throw "Profile latest.release must use Java 21" }
    }

    Run-TestCase "ProfileService Active Profile Switching" {
        $profiles = $profileSvc.GetProfiles()
        $p121 = $profiles | Where-Object { $_.MinecraftVersion -eq "1.21.1" }
        $p262 = $profiles | Where-Object { $_.MinecraftVersion -eq "26.2" }

        $profileSvc.SetActiveProfile($p121.Id)
        $active1 = $profileSvc.GetActiveProfile()
        if ($active1.Id -ne $p121.Id) { throw "Failed setting active profile to 1.21.1" }

        $profileSvc.SetActiveProfile($p262.Id)
        $active2 = $profileSvc.GetActiveProfile()
        if ($active2.Id -ne $p262.Id) { throw "Failed setting active profile to 26.2" }
        if ($active2.JavaMajorVersion -ne 25) { throw "Active profile JavaMajorVersion must be 25" }
    }

    Run-TestCase "ProfileService SetActiveProfile Nonexistent ID Graceful No-Op" {
        $activeBefore = $profileSvc.GetActiveProfile()
        $profileSvc.SetActiveProfile("non-existent-guid-999999")
        $activeAfter = $profileSvc.GetActiveProfile()
        if ($activeAfter.Id -ne $activeBefore.Id) {
            throw "SetActiveProfile with invalid ID unexpectedly altered active profile"
        }
    }

    Run-TestCase "ProfileService Dynamic Profile Creation & Persistence" {
        $customName = "Adversarial Profile " + [System.Guid]::NewGuid().ToString("N")
        $created = $profileSvc.CreateProfile($customName, "1.21.1", 21, $false, "0.16.9")
        if (-not $created) { throw "CreateProfile returned null" }
        if ($created.Name -ne $customName) { throw "Name mismatch in created profile" }

        # Verify profile is present in GetProfiles()
        $found = $profileSvc.GetProfiles() | Where-Object { $_.Id -eq $created.Id }
        if (-not $found) { throw "Created profile not found in GetProfiles()" }

        # Verify disk persistence by loading in a new ProfileService instance
        $newSvc = [TheLadsLauncher.Services.ProfileService]::new($pathSvc)
        $foundInNew = $newSvc.GetProfiles() | Where-Object { $_.Id -eq $created.Id }
        if (-not $foundInNew) { throw "Created profile not persisted to profiles.json on disk" }

        # Clean up
        $profileSvc.DeleteProfile($created.Id) | Out-Null
    }

    Run-TestCase "ProfileService Active Profile Deletion Fallback" {
        $tempProfile = $profileSvc.CreateProfile("TempToDelete", "1.21.1", 21, $false)
        $profileSvc.SetActiveProfile($tempProfile.Id)
        if ($profileSvc.GetActiveProfile().Id -ne $tempProfile.Id) { throw "Failed setting active profile to temp" }

        # Delete active profile
        $deleted = $profileSvc.DeleteProfile($tempProfile.Id)
        if (-not $deleted) { throw "DeleteProfile returned false" }

        # Active profile should have fallen back to a valid existing profile
        $fallback = $profileSvc.GetActiveProfile()
        if (-not $fallback) { throw "GetActiveProfile returned null after deleting active profile" }
        if ($fallback.Id -eq $tempProfile.Id) { throw "Active profile still points to deleted profile" }
    }

    Run-TestCase "ProfileService Deletion Guard: Last Profile Cannot Be Deleted" {
        $guardSandbox = Join-Path $sandboxBase "guard_tests"
        $guardPath = [TheLadsLauncher.Services.PathService]::new($guardSandbox)
        $guardSvc = [TheLadsLauncher.Services.ProfileService]::new($guardPath)

        $allProfiles = $guardSvc.GetProfiles()
        for ($i = 0; $i -lt ($allProfiles.Count - 1); $i++) {
            $guardSvc.DeleteProfile($allProfiles[$i].Id) | Out-Null
        }

        $remaining = $guardSvc.GetProfiles()
        if ($remaining.Count -ne 1) {
            throw "Expected exactly 1 profile remaining, got $($remaining.Count)"
        }

        # Attempt to delete the last profile
        $canDeleteLast = $guardSvc.DeleteProfile($remaining[0].Id)
        if ($canDeleteLast) {
            throw "DeleteProfile unexpectedly succeeded when deleting the sole remaining profile"
        }
        if ($guardSvc.GetProfiles().Count -ne 1) {
            throw "Profile count must remain 1 after attempting to delete the last profile"
        }
    }

    Run-TestCase "ProfileService Recovery from Corrupted profiles.json" {
        $corruptSandbox = Join-Path $sandboxBase "corrupt_tests"
        $corruptPath = [TheLadsLauncher.Services.PathService]::new($corruptSandbox)
        $corruptFile = Join-Path $corruptSandbox "profiles.json"
        
        [System.IO.File]::WriteAllText($corruptFile, "{ NOT VALID JSON ::: random garbage 12345 }")

        $recoveredSvc = [TheLadsLauncher.Services.ProfileService]::new($corruptPath)
        $profiles = $recoveredSvc.GetProfiles()
        if ($profiles.Count -lt 3) {
            throw "Expected ProfileService to recreate default profiles after corrupt profiles.json, got $($profiles.Count)"
        }
        $active = $recoveredSvc.GetActiveProfile()
        if (-not $active) { throw "Active profile null after corrupt profiles.json recovery" }
    }

    # --------------------------------------------------------------------------
    # SUITE 3: SHARED OPTIONS SYNCHRONIZATION VS ISOLATED PROFILE BYPASS
    # --------------------------------------------------------------------------
    Write-Host "`n--- SUITE 3: Shared Options Sync vs Isolated Profile Bypass ---" -ForegroundColor Cyan

    $syncSandbox = Join-Path $sandboxBase "sync_tests"
    $syncPath = [TheLadsLauncher.Services.PathService]::new($syncSandbox)
    $syncProfileSvc = [TheLadsLauncher.Services.ProfileService]::new($syncPath)

    Run-TestCase "Shared Sync: Two Profiles Share options.txt and servers.dat" {
        $p1 = $syncProfileSvc.CreateProfile("Profile_1", "1.21.1", 21, $false)
        $p2 = $syncProfileSvc.CreateProfile("Profile_2", "26.2", 25, $false)

        $dir1 = $syncPath.GetProfileDirectory($p1)
        $dir2 = $syncPath.GetProfileDirectory($p2)
        [System.IO.Directory]::CreateDirectory($dir1) | Out-Null
        [System.IO.Directory]::CreateDirectory($dir2) | Out-Null

        # Seed shared options.txt
        $sharedOptions = $syncPath.SharedOptionsFile
        $sharedContent = "key_jump:key.keyboard.space`nkey_sneak:key.keyboard.left.shift`ngamma:1.0"
        [System.IO.File]::WriteAllText($sharedOptions, $sharedContent)

        # Profile 1 prepares environment -> receives shared options.txt
        $syncProfileSvc.PrepareProfileEnvironmentAsync($p1).GetAwaiter().GetResult()
        $p1Options = Join-Path $dir1 "options.txt"
        if (-not (Test-Path $p1Options)) { throw "options.txt not copied to Profile 1" }
        if ([System.IO.File]::ReadAllText($p1Options) -ne $sharedContent) {
            throw "Profile 1 options.txt content mismatch with shared options"
        }

        # Profile 1 changes keybinds in-game
        Start-Sleep -Milliseconds 50
        $updatedOptions = $sharedContent + "`nkey_sprint:key.keyboard.left.control"
        [System.IO.File]::WriteAllText($p1Options, $updatedOptions)

        # Profile 1 process exits -> syncs to shared
        $syncProfileSvc.SyncProfileToSharedAsync($p1).GetAwaiter().GetResult()
        if ([System.IO.File]::ReadAllText($sharedOptions) -ne $updatedOptions) {
            throw "Shared options.txt was not updated after Profile 1 post-game sync"
        }

        # Profile 2 launches -> prepares environment -> receives updated options.txt
        $syncProfileSvc.PrepareProfileEnvironmentAsync($p2).GetAwaiter().GetResult()
        $p2Options = Join-Path $dir2 "options.txt"
        if (-not (Test-Path $p2Options)) { throw "options.txt not copied to Profile 2" }
        if ([System.IO.File]::ReadAllText($p2Options) -ne $updatedOptions) {
            throw "Profile 2 did not receive updated options.txt from shared folder"
        }
    }

    Run-TestCase "Isolated Profile Bypass: Strict Isolation from Shared Folder" {
        $pShared = $syncProfileSvc.CreateProfile("Shared_P", "1.21.1", 21, $false)
        $pIsolated = $syncProfileSvc.CreateProfile("Isolated_P", "1.21.1", 21, $true) # isIsolated = true

        $sharedDir = $syncPath.GetProfileDirectory($pShared)
        $isoDir = $syncPath.GetProfileDirectory($pIsolated)
        [System.IO.Directory]::CreateDirectory($sharedDir) | Out-Null
        [System.IO.Directory]::CreateDirectory($isoDir) | Out-Null

        # Set shared options content
        $sharedOptions = $syncPath.SharedOptionsFile
        $sharedContent = "shared_key:key.keyboard.f"
        [System.IO.File]::WriteAllText($sharedOptions, $sharedContent)

        # Set isolated profile options content
        $isoOptions = Join-Path $isoDir "options.txt"
        $isoContent = "isolated_private_key:key.keyboard.p"
        [System.IO.File]::WriteAllText($isoOptions, $isoContent)

        # 1. PrepareProfileEnvironment on Isolated MUST NOT overwrite isolated options
        $syncProfileSvc.PrepareProfileEnvironmentAsync($pIsolated).GetAwaiter().GetResult()
        $isoCurrent = [System.IO.File]::ReadAllText($isoOptions)
        if ($isoCurrent -ne $isoContent) {
            throw "Isolated profile options.txt was overwritten during PrepareProfileEnvironment! Content: $isoCurrent"
        }

        # 2. Modify isolated options and run SyncProfileToSharedAsync
        Start-Sleep -Milliseconds 50
        $newIsoContent = "isolated_private_key:key.keyboard.q`nisolated_mode:true"
        [System.IO.File]::WriteAllText($isoOptions, $newIsoContent)

        $syncProfileSvc.SyncProfileToSharedAsync($pIsolated).GetAwaiter().GetResult()

        # Shared options MUST NOT have changed
        $sharedCurrent = [System.IO.File]::ReadAllText($sharedOptions)
        if ($sharedCurrent -ne $sharedContent) {
            throw "Isolated profile leaked data into shared options.txt! Shared content: $sharedCurrent"
        }

        # Shared profile preparing MUST NOT see isolated settings
        $syncProfileSvc.PrepareProfileEnvironmentAsync($pShared).GetAwaiter().GetResult()
        $pSharedOptions = Join-Path $sharedDir "options.txt"
        $pSharedContent = [System.IO.File]::ReadAllText($pSharedOptions)
        if ($pSharedContent -ne $sharedContent) {
            throw "Shared profile received leaked data. Content: $pSharedContent"
        }
    }

    Run-TestCase "Shared Sync: Initial Seed from Instance when Shared File Does Not Exist" {
        $seedSandbox = Join-Path $sandboxBase "seed_tests"
        $seedPath = [TheLadsLauncher.Services.PathService]::new($seedSandbox)
        $seedProfileSvc = [TheLadsLauncher.Services.ProfileService]::new($seedPath)

        $pSeed = $seedProfileSvc.CreateProfile("SeedProfile", "1.21.1", 21, $false)
        $pDir = $seedPath.GetProfileDirectory($pSeed)
        [System.IO.Directory]::CreateDirectory($pDir) | Out-Null

        $instanceOptions = Join-Path $pDir "options.txt"
        $content = "initial_seed:true`nsound_master:0.75"
        [System.IO.File]::WriteAllText($instanceOptions, $content)

        if (Test-Path $seedPath.SharedOptionsFile) {
            [System.IO.File]::Delete($seedPath.SharedOptionsFile)
        }

        $seedProfileSvc.PrepareProfileEnvironmentAsync($pSeed).GetAwaiter().GetResult()

        if (-not (Test-Path $seedPath.SharedOptionsFile)) {
            throw "Shared options.txt was not seeded from instance options.txt"
        }
        if ([System.IO.File]::ReadAllText($seedPath.SharedOptionsFile) -ne $content) {
            throw "Seeded shared options.txt content does not match instance"
        }
    }

    Run-TestCase "Shared Sync: Binary File Sync for servers.dat" {
        $binSandbox = Join-Path $sandboxBase "bin_tests"
        $binPath = [TheLadsLauncher.Services.PathService]::new($binSandbox)
        $binProfileSvc = [TheLadsLauncher.Services.ProfileService]::new($binPath)

        $pBin = $binProfileSvc.CreateProfile("BinProfile", "1.21.1", 21, $false)
        $pDir = $binPath.GetProfileDirectory($pBin)
        [System.IO.Directory]::CreateDirectory($pDir) | Out-Null

        $sharedServers = $binPath.SharedServersFile
        $randomBytes = [byte[]]::new(256)
        [System.Random]::new().NextBytes($randomBytes)
        [System.IO.File]::WriteAllBytes($sharedServers, $randomBytes)

        $binProfileSvc.PrepareProfileEnvironmentAsync($pBin).GetAwaiter().GetResult()
        $pServers = Join-Path $pDir "servers.dat"
        if (-not (Test-Path $pServers)) { throw "servers.dat not copied to profile directory" }

        $copiedBytes = [System.IO.File]::ReadAllBytes($pServers)
        if ($copiedBytes.Length -ne $randomBytes.Length) { throw "servers.dat byte length mismatch" }
        for ($b = 0; $b -lt $randomBytes.Length; $b++) {
            if ($copiedBytes[$b] -ne $randomBytes[$b]) {
                throw "servers.dat binary mismatch at byte offset $b"
            }
        }
    }

    # --------------------------------------------------------------------------
    # SUITE 4: JAVA SERVICE ADVERSARIAL TESTS
    # --------------------------------------------------------------------------
    Write-Host "`n--- SUITE 4: Java Runtime Detection ---" -ForegroundColor Cyan

    $javaPathSvc = [TheLadsLauncher.Services.PathService]::new((Join-Path $sandboxBase "java_tests"))
    $javaSvc = [TheLadsLauncher.Services.JavaService]::new($javaPathSvc)

    Run-TestCase "JavaService System Scan Detects Host Java Installations" {
        $javas = $javaSvc.ScanAllSystemJavas()
        if ($javas.Count -eq 0) {
            throw "ScanAllSystemJavas found 0 Java installations on system"
        }
        Write-Host " (Found $($javas.Count) Javas)" -NoNewline
    }

    Run-TestCase "JavaService Version Detection from Mock release Files" {
        $testJdkDir = Join-Path $sandboxBase "mock_jdk_21"
        $testBinDir = Join-Path $testJdkDir "bin"
        [System.IO.Directory]::CreateDirectory($testBinDir) | Out-Null
        $mockJavaExe = Join-Path $testBinDir "java.exe"
        [System.IO.File]::WriteAllText($mockJavaExe, "mock")

        $releaseFile = Join-Path $testJdkDir "release"
        [System.IO.File]::WriteAllText($releaseFile, "JAVA_VERSION=`"21.0.4`"`nOS_NAME=`"Windows`"")

        $detectedVer = $javaSvc.GetJavaMajorVersion($mockJavaExe)
        if ($detectedVer -ne 21) {
            throw "Expected version 21 from mock release file, got $detectedVer"
        }

        # Mock JDK 25
        $testJdk25Dir = Join-Path $sandboxBase "mock_jdk_25"
        $testBin25Dir = Join-Path $testJdk25Dir "bin"
        [System.IO.Directory]::CreateDirectory($testBin25Dir) | Out-Null
        $mockJava25Exe = Join-Path $testBin25Dir "java.exe"
        [System.IO.File]::WriteAllText($mockJava25Exe, "mock")
        [System.IO.File]::WriteAllText((Join-Path $testJdk25Dir "release"), "JAVA_VERSION=`"25.0.2`"")

        $detected25 = $javaSvc.GetJavaMajorVersion($mockJava25Exe)
        if ($detected25 -ne 25) {
            throw "Expected version 25 from mock release file, got $detected25"
        }
    }

} finally {
    try {
        if (Test-Path $sandboxBase) {
            [System.IO.Directory]::Delete($sandboxBase, $true)
        }
    } catch { }
}

Write-Host "`n=============================================================================="
Write-Host " C# SERVICES IN-MEMORY TEST RESULTS"
Write-Host " Total: $totalTests | Passed: $passedTests | Failed: $failedTests"
Write-Host "=============================================================================="

if ($failedTests -gt 0) {
    Write-Host "`nFAILURES ENCOUNTERED:" -ForegroundColor Red
    foreach ($f in $failureDetails) {
        Write-Host "--------------------------------------------------"
        Write-Host $f -ForegroundColor Red
    }
    exit 1
} else {
    Write-Host "`nALL C# SERVICES ADVERSARIAL TESTS PASSED!" -ForegroundColor Green
    exit 0
}
