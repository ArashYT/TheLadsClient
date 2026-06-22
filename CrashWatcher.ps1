$folder = "C:\Users\Arash\curseforge\minecraft\Instances\The Lads Client Dev 1.21.11\crash-reports"
if (-not (Test-Path $folder)) { New-Item -ItemType Directory -Path $folder | Out-Null }
$fsw = New-Object IO.FileSystemWatcher $folder, "*.txt"
$fsw.IncludeSubdirectories = $false
$fsw.NotifyFilter = [IO.NotifyFilters]'FileName, LastWrite'
Write-Host "Crash monitor started."
while ($true) {
    $result = $fsw.WaitForChanged([IO.WatcherChangeTypes]::Created)
    $path = $result.Name
    Write-Host "CRASH_DETECTED: $path"
    Start-Sleep -Seconds 2
}
