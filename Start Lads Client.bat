@echo off
echo ===================================================
echo [The Lads Client] Auto-Sync & Launch Engine
echo ===================================================
echo.
echo Automatically checking for updates and syncing build...

REM Run the build script to compile the core mod and copy the launcher
powershell -ExecutionPolicy Bypass -File "%~dp0Build-LadsClient.ps1" -Launcher

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Build synchronization failed!
    echo Press any key to launch anyway, or close this window.
    pause
)

echo.
echo Launching The Lads Client...
start "" "%~dp0TheLadsLauncher_Clean\bin\NewBuild\TheLadsLauncher.exe"
