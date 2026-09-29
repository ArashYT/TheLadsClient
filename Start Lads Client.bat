@echo off
setlocal
set "launcher=%~dp0artifacts\client\TheLadsLauncher.exe"
if not exist "%launcher%" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build-LadsClient.ps1" -Launcher
    if errorlevel 1 (
        echo Build failed. See the error above.
        pause
        exit /b 1
    )
)
start "" "%launcher%"
