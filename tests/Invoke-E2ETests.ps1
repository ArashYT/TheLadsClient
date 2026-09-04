<#
.SYNOPSIS
    The Lads Client - E2E Test Suite PowerShell Runner
.DESCRIPTION
    Invokes the master opaque-box E2E test suite (Tiers 1-4) with optional tier,
    feature filtering, verbose reporting, and markdown/JSON output generation.
.PARAMETER Tier
    Tiers to execute: 'all', '1', '2', '3', '4', or comma-separated list like '1,2'.
.PARAMETER Feature
    Optional feature filter (e.g. 'F01', 'F02', 'F11').
.PARAMETER JsonOutput
    Optional file path to output machine-readable test results in JSON format.
.PARAMETER MarkdownOutput
    Optional file path to output human-readable markdown test summary.
.EXAMPLE
    .\tests\Invoke-E2ETests.ps1
    .\tests\Invoke-E2ETests.ps1 -Tier 1
    .\tests\Invoke-E2ETests.ps1 -Feature F01 -Verbose
#>

[CmdletBinding()]
param (
    [string]$Tier = "all",
    [string]$Feature = "",
    [string]$JsonOutput = "",
    [string]$MarkdownOutput = "",
    [switch]$VerboseOutput
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$repoRoot = Split-Path -Parent $scriptDir
$runnerScript = Join-Path $scriptDir "run_tests.py"

if (-not (Test-Path $runnerScript)) {
    Write-Error "Test runner script not found at $runnerScript"
    exit 1
}

$cmdArgs = @($runnerScript, "--tier", $Tier)

if (-not [string]::IsNullOrWhiteSpace($Feature)) {
    $cmdArgs += @("--feature", $Feature)
}

if ($VerboseOutput -or $PSBoundParameters['Verbose']) {
    $cmdArgs += @("--verbose")
}

if (-not [string]::IsNullOrWhiteSpace($JsonOutput)) {
    $cmdArgs += @("--json-output", $JsonOutput)
}

if (-not [string]::IsNullOrWhiteSpace($MarkdownOutput)) {
    $cmdArgs += @("--markdown-output", $MarkdownOutput)
}

Write-Host "Executing The Lads Client E2E Test Suite..." -ForegroundColor Cyan
Write-Host "Runner: python $cmdArgs" -ForegroundColor Gray

& python $cmdArgs
$exitCode = $LASTEXITCODE

if ($exitCode -eq 0) {
    Write-Host "`nAll selected E2E tests completed successfully." -ForegroundColor Green
} else {
    Write-Host "`nE2E tests finished with failures or pending implementations (Exit code: $exitCode)." -ForegroundColor Yellow
}

exit $exitCode
