@echo off
REM Build check run by RunAITask.bat AFTER the AI finishes editing.
REM Lives at the repo root; gradlew is inside TheLadsCore.
cd /d "%~dp0TheLadsCore"
call gradlew.bat compileJava compileTestJava --console=plain
