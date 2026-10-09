# usage: loadmon.ps1 <out.csv> <stopfile> : samples machine CPU %, free RAM GB, other java processes every 3 s until <stopfile> exists
param([string]$Out, [string]$Stop)
"time,cpu_pct,free_gb,java_procs,gradle_daemons" | Out-File $Out -Encoding ascii
while (-not (Test-Path $Stop)) {
    $cpu = (Get-Counter '\Processor(_Total)\% Processor Time' -ErrorAction SilentlyContinue).CounterSamples[0].CookedValue
    $free = (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB
    $j = @(Get-Process java -ErrorAction SilentlyContinue)
    "{0},{1:N1},{2:N2},{3},{4}" -f (Get-Date -Format 'HH:mm:ss'), $cpu, $free, $j.Count, 0 | Out-File $Out -Append -Encoding ascii
    Start-Sleep -Seconds 2
}
