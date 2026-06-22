@echo off
REM ============================================================
REM  The Lads Client - one-click build
REM  Double-click to build TheLadsCore and deploy it into the pack.
REM    Build.bat -Launcher   also builds the launcher
REM    Build.bat -Watch      auto-rebuilds whenever you save a change
REM  (or just double-click Watch.bat for auto-rebuild)
REM ============================================================
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build-LadsClient.ps1" %*
echo.
pause
