@echo off
REM ============================================================
REM  Local AI coding agent for Lads Client (Aider + Ollama)
REM  Model: qwen2.5-coder:14b (best quality, local)
REM  Just run this file, then type what you want done.
REM  Type /help inside for commands, Ctrl-C to quit.
REM ============================================================
cd /d "%~dp0"

REM Point Aider at the local Ollama server
set OLLAMA_API_BASE=http://127.0.0.1:11434

REM Make sure Ollama is running (no-op if already up)
start "" /b ollama serve >nul 2>&1

echo Starting local AI agent (Aider + Ollama)...
echo Model: qwen-coder-14b-stable (freeze-safe)  ^|  /help for commands, Ctrl-C to quit
echo.

REM qwen-coder-14b-stable = 8k context, auto GPU offload (leaves VRAM headroom, won't freeze).
REM --yes-always auto-confirms edits so it works autonomously.
REM Remove --yes-always if you want to approve each change first.
python -m aider --model ollama_chat/qwen-coder-14b-stable --no-show-model-warnings --yes-always
