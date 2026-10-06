# Runs a command at below-normal priority (its children inherit it), so measurement batches and builds leave the
# owner's game and stream alone. Usage: powershell -File lowprio.ps1 <exe> <args...>
param([Parameter(Mandatory = $true)][string]$Exe, [Parameter(ValueFromRemainingArguments = $true)][string[]]$Rest)
$p = Start-Process -FilePath $Exe -ArgumentList $Rest -NoNewWindow -PassThru
try { $p.PriorityClass = 'BelowNormal' } catch {}
$p.WaitForExit()
exit $p.ExitCode
