@echo off
REM One-click re-check: is the MC 26.2 migration unblocked yet?
REM Queries Modrinth for 26.2 Fabric builds of the mods that currently block the migration.
REM When the source-ported blockers all show READY, the migration becomes possible.
powershell -NoProfile -Command ^
  "$mods=@('sodium','immediatelyfast','3dskinlayers','jei','betterstats','advancements-reloaded','appleskin','not-enough-animations','xaeros-world-map','raised','enhanced-tooltips');" ^
  "$blocked=0;" ^
  "foreach($m in $mods){" ^
  "  try{$r=Invoke-RestMethod -UseBasicParsing -Headers @{'User-Agent'='ladsclient-check'} -Uri ('https://api.modrinth.com/v2/project/'+$m+'/version?game_versions=%5B%2226.2%22%5D&loaders=%5B%22fabric%22%5D');}catch{$r=@()};" ^
  "  if($r.Count -gt 0){Write-Host ('  READY    ' + $m) -ForegroundColor Green}else{Write-Host ('  NO 26.2  ' + $m) -ForegroundColor Red; $blocked++}" ^
  "}" ^
  "Write-Host '';" ^
  "if($blocked -eq 0){Write-Host 'ALL CLEAR - 26.2 migration is now POSSIBLE.' -ForegroundColor Green}else{Write-Host ($blocked.ToString()+' mod(s) still have no 26.2 build - migration still BLOCKED.') -ForegroundColor Yellow}"
echo.
pause
