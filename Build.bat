@echo off
REM ============================================================
REM  The Lads Client - one-click build
REM  Double-click to build TheLadsCore and deploy it into the pack.
REM    Build.bat -Launcher   also builds the launcher
REM ============================================================
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build-LadsClient.ps1" %*
echo.
pause
