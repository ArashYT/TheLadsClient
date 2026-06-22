Write-Host "Waiting for Ollama to boot..."
for ($i=0; $i -lt 15; $i++) {
    try {
        $res = Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -ErrorAction Stop
        Write-Host "Ollama is UP!"
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}
Write-Host "Starting regenerator..."
.\RegenerateAll13Mods.ps1
