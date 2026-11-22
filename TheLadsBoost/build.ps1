param(
  [string]$Jdk = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot",
  [string]$Asm = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.9.1" -Recurse -Filter asm-9.9.1.jar | Select-Object -First 1 -ExpandProperty FullName)
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$out = Join-Path $root "build\classes"
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force $out | Out-Null
$src = Get-ChildItem (Join-Path $root "src") -Recurse -Filter *.java | ForEach-Object FullName
& "$Jdk\bin\javac.exe" --release 17 -Xlint:all -cp $Asm -d $out $src
if ($LASTEXITCODE) { throw "javac failed" }
$mf = Join-Path $root "build\manifest.txt"
"Premain-Class: lads.boost.BoostAgent`nCan-Retransform-Classes: false`nCan-Redefine-Classes: false`n" | Set-Content -NoNewline -Encoding ascii $mf
& "$Jdk\bin\jar.exe" --create --file (Join-Path $root "build\lads-boost.jar") --manifest $mf -C $out .
if ($LASTEXITCODE) { throw "jar failed" }
Get-Item (Join-Path $root "build\lads-boost.jar") | Select-Object FullName, Length
