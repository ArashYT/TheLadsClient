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
if exist "%LOCALAPPDATA%\The Lads Client\TheLadsLauncher.exe" (
    start "" "%LOCALAPPDATA%\The Lads Client\TheLadsLauncher.exe"
) else (
    start "" "%~dp0TheLadsLauncher\bin\Release\net8.0-windows\win-x64\publish\TheLadsLauncher.exe"
)
