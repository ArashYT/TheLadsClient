# Prints OWNER_PLAYING if a Minecraft JVM whose --gameDir is the REAL Zero SMP instance is running (never prints the command line: it holds the access token).
$real = Join-Path $env:APPDATA '.theladsclient\instances\zero-smp-1.20.1\minecraft'
$hit = Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" | Where-Object {
    $_.CommandLine -and $_.CommandLine -match 'bootstraplauncher' -and $_.CommandLine.Contains($real)
}
if ($hit) { 'OWNER_PLAYING pid=' + ($hit | ForEach-Object { $_.ProcessId }) } else { 'OWNER_AWAY' }
