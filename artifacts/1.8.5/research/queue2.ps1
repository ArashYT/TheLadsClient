# waits for queue1 to finish, then runs restart-class grass variants (the live flips did not take effect) and the combined recommended set
$res = 'C:\Users\Arash\AppData\Local\Temp\w185research\artifacts\1.8.5\research'
while (Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" | Where-Object { $_.CommandLine -like '*queue1.ps1*' }) { Start-Sleep -Seconds 15 }
$scn = Join-Path $res 'scenarios\short.txt'
$runs = @(
  @{ n='gr64'; a=@('-Variants','grass_r64') },
  @{ n='gr32'; a=@('-Variants','grass_r32') },
  @{ n='allc'; a=@('-Variants','all_cfg') }
)
foreach ($r in $runs) {
  & powershell -NoProfile -File (Join-Path $res 'run_case.ps1') -Name $r.n -Script $scn -Jfr @($r.a) 2>&1 | Out-File (Join-Path $res "runs\$($r.n).out") -Encoding utf8
  Write-Host "done $($r.n) $(Get-Date)"
}
