@echo off
REM Build check used by the AI's --auto-test loop. Single clean command (no shell
REM operators) so Aider can call it reliably. Fails if the mod OR its test sources
REM don't compile -> the AI keeps fixing until this returns success.
cd /d "%~dp0TheLadsCore"
call gradlew.bat compileJava compileTestJava --console=plain
