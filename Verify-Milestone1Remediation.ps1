<#
.SYNOPSIS
    Automated verification suite for Milestone 1 Remediation (Repo Hygiene & History Purge).
.DESCRIPTION
    Validates zero tracked binaries, untracked dumps/logs, Packwiz 28-pack parity, workspace sanitization,
    git graph integrity, and fresh clone size under 50 MB.
.OUTPUTS
    Exit code 0 if all 7 validation criteria pass. Exit code 1 if any check fails.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "Continue"
$failures = [System.Collections.Generic.List[string]]::new()

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " The Lads Client: Milestone 1 Remediation Verification " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# 1. VC-1: Zero Tracked Binaries & Bytecode
Write-Host "`n[1/7] Testing Tracked Binaries & Bytecode..." -ForegroundColor Yellow
$binExts = @('.exe', '.jar', '.zip', '.mp4', '.gif', '.dll', '.bin', '.iso', '.msi', '.tar', '.gz', '.7z', '.pyc', '.class')
$trackedBins = git ls-files | Where-Object { $binExts -contains [System.IO.Path]::GetExtension($_).ToLower() }
if ($trackedBins.Count -gt 0) {
    $msg = "VC-1 FAILED: Tracked binary files found:`n" + ($trackedBins -join "`n")
    Write-Warning $msg
    $failures.Add($msg)
} else {
    Write-Host "  -> PASS: Zero tracked binaries or bytecode files." -ForegroundColor Green
}

# 2. VC-2: Untracked Dumps, Logs & Scratch Files
Write-Host "`n[2/7] Testing Untracked Dumps, Logs & Scratch Files..." -ForegroundColor Yellow
$dumps = @(
    "TheLadsCore/gui_disasm.txt", "TheLadsCore/search_results.txt", "TheLadsCore/build_output.txt",
    "TheLadsCore/compile_errors.txt", "TheLadsCore/errors.txt", "TheLadsCore/compile_log.txt",
    "TheLadsCore/scoreboard.txt", "TheLadsCore/packwiz_essential_search.txt", "TheLadsCore/reconstructed.txt",
    "TheLadsLauncher/build_out.txt", "TheLadsLauncher/debug_login.txt", "TheLadsLauncher/debug_login2.txt",
    "NodeAgentProgress/analysis.md", "AI_TASK.txt"
)
$stillTracked = $dumps | Where-Object { (git ls-files $_) }
if ($stillTracked.Count -gt 0) {
    $msg = "VC-2 FAILED: Dumps or logs still tracked in Git:`n" + ($stillTracked -join "`n")
    Write-Warning $msg
    $failures.Add($msg)
} else {
    Write-Host "  -> PASS: All decompiler dumps and diagnostic logs untracked." -ForegroundColor Green
}

# 3. VC-3: .gitignore Non-Violation Check
Write-Host "`n[3/7] Testing .gitignore Non-Violations..." -ForegroundColor Yellow
$ignoreViolations = git ls-files | git check-ignore --no-index --stdin -v -n | Where-Object { 
    $_ -notmatch '^::' -and $_ -notmatch '!Packwiz' 
}
if ($ignoreViolations.Count -gt 0) {
    $msg = "VC-3 FAILED: Tracked files violate .gitignore:`n" + ($ignoreViolations -join "`n")
    Write-Warning $msg
    $failures.Add($msg)
} else {
    Write-Host "  -> PASS: Zero tracked files violate .gitignore rules." -ForegroundColor Green
}

# 4. VC-4: Packwiz Modpack Parity & 28 Resource Packs
Write-Host "`n[4/7] Testing Packwiz Parity..." -ForegroundColor Yellow
Push-Location "Packwiz"
try {
    $rpFiles = Get-ChildItem "resourcepacks\*.pw.toml"
    $modFiles = Get-ChildItem "mods\*.pw.toml"
    
    if ($rpFiles.Count -ne 28) {
        $failures.Add("VC-4 FAILED: Resource pack count is $($rpFiles.Count), expected 28")
    }
    if ($modFiles.Count -ne 42) {
        $failures.Add("VC-4 FAILED: Mod count is $($modFiles.Count), expected 42")
    }
    
    $p1 = "resourcepacks\the-lads-network-ui-sounds-v1.0.pw.toml"
    $p2 = "resourcepacks\the-lads-network-ui-and-sounds-v1.0.pw.toml"
    if (-not (Test-Path $p1)) { $failures.Add("VC-4 FAILED: Missing $p1") }
    if (-not (Test-Path $p2)) { $failures.Add("VC-4 FAILED: Missing $p2") }
    
    if ((Test-Path $p1) -and (Test-Path $p2)) {
        $h1 = (Get-Content $p1 | Select-String 'hash\s*=\s*"(.*)"').Matches.Groups[1].Value
        $h2 = (Get-Content $p2 | Select-String 'hash\s*=\s*"(.*)"').Matches.Groups[1].Value
        $expH1 = "51399a48be228891997717d39612fa854114280527ef856092ae06a9eecf667c17d35bbe310307d1365a22c8610e837ed33eeb49c726c5a933feff71f4ab2846"
        $expH2 = "2c4b7b6c4b3e905c2cd76fbfa15c94732e7ef626660d8176bb666b0a7892964f239688961737c06daf723a1a294f51b036e69938305e23d8f64b928682fe9c05"
        if ($h1 -ne $expH1) { $failures.Add("VC-4 FAILED: $p1 hash mismatch") }
        if ($h2 -ne $expH2) { $failures.Add("VC-4 FAILED: $p2 hash mismatch") }
    }

    $null = & "C:\Users\Arash\go\bin\packwiz.exe" refresh
    if ($LASTEXITCODE -ne 0) { $failures.Add("VC-4 FAILED: packwiz refresh exit code $($LASTEXITCODE)") }
    
    $listItems = (& "C:\Users\Arash\go\bin\packwiz.exe" list | Where-Object { $_.Trim() -ne "" }).Count
    if ($listItems -ne 70) {
        $failures.Add("VC-4 FAILED: packwiz list reported $listItems items, expected 70")
    } else {
        Write-Host "  -> PASS: Packwiz parity validated (28 resource packs, 42 mods, 70 indexed items)." -ForegroundColor Green
    }
} finally {
    Pop-Location
}

# 5. VC-5: Workspace Hygiene & Residual Purge
Write-Host "`n[5/7] Testing Workspace Hygiene & Residual Cleanliness..." -ForegroundColor Yellow
$residuals = @(
    "Heartbeat", "TheLadsClient", "NodeAgentProgress", "com", "TheLadsCore\ Bronze",
    "TheLadsCore\bin", "TheLadsCore\org", "TheLadsLauncher\tests\__pycache__",
    ".aider.chat.history.md", ".aider.conf.yml", ".aider.input.history", ".aider.model.settings.yml", ".aiderignore", "ai.py",
    "TheLadsCore\gui_disasm.txt", "TheLadsCore\search_results.txt", "TheLadsCore\build_output.txt",
    "TheLadsCore\compile_errors.txt", "TheLadsCore\errors.txt", "TheLadsCore\compile_log.txt",
    "TheLadsCore\scoreboard.txt", "TheLadsCore\packwiz_essential_search.txt", "TheLadsCore\reconstructed.txt",
    "TheLadsLauncher\build_out.txt", "TheLadsLauncher\debug_login.txt", "TheLadsLauncher\debug_login2.txt", "AI_TASK.txt"
)
$foundRes = $residuals | Where-Object { Test-Path -LiteralPath $_ }
$looseCls = Get-ChildItem -Path "TheLadsCore\*.class" -ErrorAction SilentlyContinue
$looseLog = Get-ChildItem -Path "TheLadsCore\*.log" -ErrorAction SilentlyContinue
$srcLog = Get-ChildItem -Path "TheLadsCore\src\main\java\com\thelads\core\recreate_local.log" -ErrorAction SilentlyContinue

if ($foundRes.Count -gt 0) { $failures.Add("VC-5 FAILED: Residual items exist: " + ($foundRes -join ", ")) }
if ($looseCls.Count -gt 0) { $failures.Add("VC-5 FAILED: Loose class files in TheLadsCore: " + ($looseCls.Name -join ", ")) }
if ($looseLog.Count -gt 0) { $failures.Add("VC-5 FAILED: Loose logs in TheLadsCore root: " + ($looseLog.Name -join ", ")) }
if ($srcLog) { $failures.Add("VC-5 FAILED: Loose recreate_local.log in TheLadsCore src") }

if (-not ($foundRes -or $looseCls -or $looseLog -or $srcLog)) {
    Write-Host "  -> PASS: Workspace completely sanitized." -ForegroundColor Green
}

# 6. VC-6: Git Ref Cleanliness
Write-Host "`n[6/7] Testing Git Refs & Commit Graph Cleanliness..." -ForegroundColor Yellow
$legacyRef = git show-ref refs/original/refs/heads/main
if ($legacyRef) {
    $failures.Add("VC-6 FAILED: Stale filter ref refs/original/refs/heads/main exists")
} else {
    Write-Host "  -> PASS: Zero stale filter refs found." -ForegroundColor Green
}

# 7. VC-7: Clean Clone & Repository Size (< 50 MB)
Write-Host "`n[7/7] Testing Fresh Clone & Repository Size (<50 MB)..." -ForegroundColor Yellow
$tempClone = Join-Path $env:TEMP ("lads_m1_run_" + [System.Guid]::NewGuid().ToString("N"))
try {
    git clone . $tempClone | Out-Null
    Push-Location $tempClone
    $fsck = git fsck --full --strict 2>&1
    $fsckCode = $LASTEXITCODE
    Pop-Location

    if ($fsckCode -ne 0 -or ($fsck | Out-String) -match "dangling") {
        $failures.Add("VC-6 FAILED: Cloned repo fsck produced warnings/errors: $fsck")
    }

    $gitBytes = (Get-ChildItem -LiteralPath "$tempClone\.git" -Recurse -Force | Measure-Object -Property Length -Sum).Sum
    $gitMB = [Math]::Round($gitBytes / 1MB, 2)
    if ($gitMB -ge 50.0) {
        $failures.Add("VC-7 FAILED: Cloned .git size is $gitMB MB (must be < 50.0 MB)")
    } else {
        Write-Host "  -> PASS: Cloned .git size is $gitMB MB (< 50.0 MB threshold)." -ForegroundColor Green
    }
} finally {
    if (Test-Path $tempClone) {
        Remove-Item -LiteralPath $tempClone -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# Summary
Write-Host "`n========================================================" -ForegroundColor Cyan
if ($failures.Count -eq 0) {
    Write-Host " ALL VALIDATION CRITERIA PASSED (7/7)" -ForegroundColor Green
    Write-Host " Milestone 1 Gate is READY for APPROVAL" -ForegroundColor Green
    Write-Host "========================================================" -ForegroundColor Cyan
    exit 0
} else {
    Write-Host " VERIFICATION FAILED with $($failures.Count) error(s):" -ForegroundColor Red
    foreach ($f in $failures) {
        Write-Host "   - $f" -ForegroundColor Red
    }
    Write-Host "========================================================" -ForegroundColor Cyan
    exit 1
}
