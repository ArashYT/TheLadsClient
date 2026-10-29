$botDir = "C:\Users\Administrator\Desktop\Servers\TheLadsBot"
Set-Location $botDir
$python = "C:\Users\Administrator\AppData\Local\Programs\Python\Python312\python.exe"
$logFile = "$botDir\bot_output.log"
# P2P relay (social.py): Cloudflare Realtime TURN credentials for the bot process only (never logged)
$turnFile = "C:\Users\Administrator\Documents\lads_turn_credentials.json"
if (Test-Path $turnFile) { $turn = Get-Content $turnFile -Raw | ConvertFrom-Json; $env:CF_TURN_KEY_ID = $turn.CF_TURN_KEY_ID; $env:CF_TURN_API_TOKEN = $turn.CF_TURN_API_TOKEN }

$Host.UI.RawUI.WindowTitle = "The Lads Discord Bot (24/7 Service)"

while ($true) {
    if (Test-Path $logFile) {
        $logSize = (Get-Item $logFile).Length
        if ($logSize -gt 10MB) {
            Move-Item -Path $logFile -Destination "$logFile.old" -Force
        }
    }
    
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Add-Content -Path $logFile -Value "[$timestamp] [Service] Launching The Lads Discord Bot..."
    
    try {
        & $python -u bot.py *>> $logFile
        $exitCode = $LASTEXITCODE
    } catch {
        $exitCode = -1
        Add-Content -Path $logFile -Value "[$timestamp] [Service Error] $_"
    }

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Add-Content -Path $logFile -Value "[$timestamp] [Service] Bot process exited with code $exitCode. Restarting in 5 seconds..."
    Start-Sleep -Seconds 5
}
