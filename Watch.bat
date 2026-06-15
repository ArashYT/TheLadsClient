@echo off
REM ============================================================
REM  The Lads Client - auto-rebuild (watch mode)
REM  Builds once, then rebuilds automatically whenever you save
REM  a source change. Leave this window open while you work.
REM  Press Ctrl+C (or close the window) to stop.
REM ============================================================
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build-LadsClient.ps1" -Watch %*
echo.
pause
