# Sequential variant runs (one game at a time) after the baseline/flip runs. Each uses scenarios/short.txt (phases S,A,B,C,D,Z).
$res = 'C:\Users\Arash\AppData\Local\Temp\w185research\artifacts\1.8.5\research'
$scn = Join-Path $res 'scenarios\short.txt'
$runs = @(
  @{ n='mfdr'; a=@('-Variants','mf_dynres') },
  @{ n='orph'; a=@('-Drop','Corgilib,Data_Anchor,player-animation,cullleaves,midnightlib') },
  @{ n='shen'; a=@('-JvmOpts','-XX:-UseG1GC -XX:+UseShenandoahGC -Xms4G') },
  @{ n='g1t';  a=@('-JvmOpts','-XX:MaxGCPauseMillis=100 -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:+PerfDisableSharedMem -Xms4G') },
  @{ n='ctrl'; a=@() }
)
foreach ($r in $runs) {
  & powershell -NoProfile -File (Join-Path $res 'run_case.ps1') -Name $r.n -Script $scn -Jfr @($r.a) 2>&1 | Out-File (Join-Path $res "runs\$($r.n).out") -Encoding utf8
  Write-Host "done $($r.n) $(Get-Date)"
}
