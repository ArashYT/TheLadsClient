# Local AI coding assistant — Aider + Ollama (qwen2.5-coder:14b)
# Run this when Claude's rate limit hits to keep coding autonomously.

# Fix Windows encoding so Aider's Unicode output renders correctly
$env:PYTHONUTF8 = "1"
$env:PYTHONIOENCODING = "utf-8"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding  = [System.Text.Encoding]::UTF8
chcp 65001 | Out-Null

$model = "qwen2.5-coder:14b"
$ollamaUrl = "http://localhost:11434"
$env:OLLAMA_API_BASE = $ollamaUrl

Write-Host "Checking Ollama..." -ForegroundColor Cyan

# Check if Ollama server is responding
try {
    $response = Invoke-RestMethod -Uri "$ollamaUrl/api/tags" -TimeoutSec 3 -ErrorAction Stop
    Write-Host "Ollama is running." -ForegroundColor Green
} catch {
    Write-Host "Starting Ollama..." -ForegroundColor Yellow
    Start-Process "ollama" -ArgumentList "serve" -WindowStyle Hidden
    Write-Host "Waiting for Ollama to be ready..." -ForegroundColor Yellow
    $attempts = 0
    do {
        Start-Sleep -Seconds 2
        $attempts++
        try {
            Invoke-RestMethod -Uri "$ollamaUrl/api/tags" -TimeoutSec 2 -ErrorAction Stop | Out-Null
            $ready = $true
        } catch {
            $ready = $false
        }
    } while (-not $ready -and $attempts -lt 15)

    if (-not $ready) {
        Write-Host "Ollama didn't start in time. Open the Ollama app manually and retry." -ForegroundColor Red
        exit 1
    }
    Write-Host "Ollama ready." -ForegroundColor Green
}

# Confirm the model is available
$models = (Invoke-RestMethod -Uri "$ollamaUrl/api/tags").models.name
if ($model -notin $models) {
    Write-Host "Model '$model' not found. Pulling it now (this may take a while)..." -ForegroundColor Yellow
    ollama pull $model
}

Write-Host ""

# If a task was passed as an argument, run the smart agent
# Usage: .\Start-AI.ps1 "add a ding sound to kill banner and rebuild"
if ($args.Count -gt 0) {
    $task = $args -join " "
    python.exe ai.py $task
} else {
    # No task given — drop into interactive Aider
    Write-Host "Starting interactive Aider ($model)..." -ForegroundColor Cyan
    Write-Host "  Tip: use /help to see commands" -ForegroundColor DarkGray
    Write-Host "  Tip: for autonomous mode, run: .\Start-AI.ps1 `"describe your task here`"" -ForegroundColor DarkGray
    Write-Host ""
    python.exe -m aider
}
