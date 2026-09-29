# Compatibility entry point. The maintained script prepares a complete draft release.
[CmdletBinding()]
param([switch]$PrepareOnly, [switch]$SkipCoreBuild)
& (Join-Path $PSScriptRoot 'Publish-Release.ps1') @PSBoundParameters
