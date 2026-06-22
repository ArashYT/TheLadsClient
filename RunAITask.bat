@echo off
REM ============================================================
REM  Autonomous local-AI task runner (freeze/lag-safe)
REM  - Reads AI_CONVENTIONS.md (standing rules) + AI_TASK.txt (the job)
REM  - The AI edits the TARGET file(s) listed on the python line
REM  - Runs at BELOW-NORMAL priority so it never freezes your PC
REM  - After the AI finishes, this script runs the build check itself
REM    (we do NOT use Aider's --auto-test: the small model tends to
REM     loop and rewrite the verify script instead of the real fix)
REM
REM  To give it a NEW job later: edit AI_TASK.txt, change the TARGET
REM  file(s) on the python line below, then run this file again.
REM ============================================================
cd /d "%~dp0"

REM --- Freeze/lag-safe Ollama settings ---
set OLLAMA_API_BASE=http://127.0.0.1:11434
set OLLAMA_MAX_LOADED_MODELS=1
set OLLAMA_NUM_PARALLEL=1
set OLLAMA_KEEP_ALIVE=5m

REM Make sure Ollama is running (no-op if already up)
start "" /b ollama serve >nul 2>&1

echo ================================================================
echo  Local AI is working on AI_TASK.txt (model: qwen-coder-14b-stable)
echo  Running at low priority - your PC stays responsive. Please wait.
echo ================================================================

REM TARGET FILE(S) the AI may edit are listed at the end of the command.
start "" /belownormal /b /wait python -m aider ^
  --model ollama_chat/qwen-coder-14b-stable ^
  --no-show-model-warnings ^
  --yes-always ^
  --no-auto-commits ^
  --map-tokens 0 ^
  --read AI_CONVENTIONS.md ^
  --message-file AI_TASK.txt ^
  "TheLadsCore\src\main\resources\fabric.mod.json" ^
  "TheLadsCore\src\main\java\net\raphimc\immediatelyfast\feature\core\BatchableBufferSource.java"

echo.
echo ================================================================
echo  AI finished editing. Now verifying the build...
echo ================================================================
call ai_verify.bat
if errorlevel 1 (
  echo.
  echo  ^>^>^> BUILD FAILED. Review the changes above ^(or run: git diff^).
) else (
  echo.
  echo  ^>^>^> BUILD OK. Review and commit if happy:  git add -p
)
echo.
pause
