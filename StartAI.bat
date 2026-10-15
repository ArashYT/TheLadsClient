@echo off
powershell -NoExit -ExecutionPolicy Bypass -Command "Set-Location '%~dp0'; & '.\Start-AI.ps1'"
