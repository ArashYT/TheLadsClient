# Runs a command so it leaves the owner's game and stream alone: idle priority, and only the last 4 CPU threads, for the
# command and every process it starts (pool workers, Gradle daemons started by it), re-applied every 2 seconds.
# Usage: powershell -File lowprio.ps1 <exe> <args...>
param([Parameter(Mandatory = $true)][string]$Exe, [Parameter(ValueFromRemainingArguments = $true)][string[]]$Rest)
$n = [Environment]::ProcessorCount
$mask = 0
for ($i = [Math]::Max(0, $n - 4); $i -lt $n; $i++) { $mask = $mask -bor (1 -shl $i) }
function Calm($p) {
    try { if ($p.PriorityClass -ne 'Idle') { $p.PriorityClass = 'Idle' } } catch {}
    try { if ($p.ProcessorAffinity -ne [IntPtr]$mask) { $p.ProcessorAffinity = [IntPtr]$mask } } catch {}
}
$env:OMP_NUM_THREADS = '1'; $env:OPENBLAS_NUM_THREADS = '1'; $env:MKL_NUM_THREADS = '1'
$root = Start-Process -FilePath $Exe -ArgumentList $Rest -NoNewWindow -PassThru
Calm $root
while (-not $root.HasExited) {
    $ids = @($root.Id)
    $all = Get-CimInstance Win32_Process -Property ProcessId, ParentProcessId
    $grew = $true
    while ($grew) {
        $grew = $false
        foreach ($proc in $all) {
            if ($ids -contains [int]$proc.ParentProcessId -and -not ($ids -contains [int]$proc.ProcessId)) { $ids += [int]$proc.ProcessId; $grew = $true }
        }
    }
    foreach ($id in $ids) { $p = Get-Process -Id $id -ErrorAction SilentlyContinue; if ($p) { Calm $p } }
    Start-Sleep -Seconds 2
}
exit $root.ExitCode
