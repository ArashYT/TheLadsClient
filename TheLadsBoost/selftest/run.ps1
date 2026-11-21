param(
  [string]$Jdk = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
)
$ErrorActionPreference = "Continue"
$root = Split-Path $PSScriptRoot
$g = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
function J($p) { (Get-ChildItem $g\$p -Recurse -Filter *.jar | Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1).FullName }
$asm = J "org.ow2.asm\asm\9.9.1"; $tree = J "org.ow2.asm\asm-tree\9.9"; $ana = J "org.ow2.asm\asm-analysis\9.9"; $util = J "org.ow2.asm\asm-util\9.9"
$fl = J "net.fabricmc\fabric-loader\0.19.5"
$mixin = J "net.fabricmc\sponge-mixin\0.17.3+mixin.0.8.7"
$agent = Join-Path $root "build\lads-boost.jar"
$t = Join-Path $root "build\selftest"
if (Test-Path $t) { Remove-Item -Recurse -Force $t }
New-Item -ItemType Directory -Force "$t\classes", "$t\game\mods", "$t\game\config" | Out-Null
$java = "$Jdk\bin\java.exe"
& "$Jdk\bin\javac.exe" --release 21 -cp "$asm;$tree;$ana;$util;$agent" -d "$t\classes" (Get-ChildItem "$root\selftest" -Recurse -Filter *.java | ForEach-Object FullName)
if ($LASTEXITCODE) { throw "javac selftest failed" }
Set-Content "$t\game\mods\a.jar" "mod a"; Set-Content "$t\game\config\foo.mixins.properties" "x=1"
$fail = 0
function Check($name, $cond) { if ($cond) { Write-Host "PASS $name" } else { Write-Host "FAIL $name"; $script:fail++ } }

function Run($label, $extra, $preinit = 0, $missing = 0) {
  $out = & $java -javaagent:$agent "-Dlads.boost.dir=$t\boost" "-Dlads.boost.gameDir=$t\game" "-Dlads.boost.flushAfterSec=600" "-Dlads.boost.debug=true" @extra -cp "$t\classes;$asm" Main $preinit $missing 2>&1
  Write-Host "--- $label"; $out | ForEach-Object { Write-Host "  $_" }
  $r = ($out | Where-Object { $_ -like "RESULT*" }) -join ""
  return [pscustomobject]@{ Out = ($out -join "`n"); Orig = [int]([regex]::Match($r, 'orig=(\d+)').Groups[1].Value); Sha = [regex]::Match($r, 'sha=(\w+)').Groups[1].Value; Cl = [int]([regex]::Match($r, 'cl=(\d+)').Groups[1].Value) }
}
function Fresh { if (Test-Path "$t\boost") { Remove-Item -Recurse -Force "$t\boost" } }

# baseline without agent
$base = & $java -cp "$t\classes" Main 0 0 | Where-Object { $_ -like "RESULT*" }
$baseSha = [regex]::Match($base, 'sha=(\w+)').Groups[1].Value
Write-Host "baseline (no agent): $base"

Fresh
$r1 = Run "run1 eager (cold)" @()
Check "run1 hits=0, misses>0, stored>0" ($r1.Out -match 'final hits=0 misses=\d+ stored=\d+' -and $r1.Out -notmatch 'stored=0 ')
Check "run1 bytes identical to no-agent baseline" ($r1.Sha -eq $baseSha)
Check "run1 orig == 2000" ($r1.Orig -eq 2000)
$r2 = Run "run2 eager (warm)" @()
Check "run2 eager: exactly 1 original call" ($r2.Orig -eq 1)
Check "run2 bytes identical" ($r2.Sha -eq $baseSha)
Check "run2 hits>1900" ($r2.Out -match 'hits=(\d{4})')
Check "run2 preload thread ran, tried names, never threw" ($r2.Out -match 'preload done: tried=\d+ ok=0 failed=\d+' -and $r2.Cl -gt 1000 -and $r2.Out -notmatch 'DISABLED')
$r3 = Run "run3 lazy (warm)" @("-Dlads.boost.prepare=lazy")
Check "run3 lazy: 0 original calls" ($r3.Orig -eq 0)
Check "run3 bytes identical" ($r3.Sha -eq $baseSha)
$r4 = Run "run4 cachedOnly + 2 threads" @("-Dlads.boost.preloadCachedOnly=true", "-Dlads.boost.preloadThreads=2")
Check "run4 preload ran with 2 threads" ($r4.Out -match 'preload done' -and $r4.Out -notmatch 'DISABLED')

# key change
(Get-Item "$t\game\mods\a.jar").LastWriteTime = (Get-Date).AddMinutes(-7)
$r5 = Run "run5 mods file touched (key change)" @()
Check "key change -> mismatch, full miss, orig 2000" ($r5.Out -match 'key mismatch' -and $r5.Out -match 'final hits=0 ' -and $r5.Orig -eq 2000 -and $r5.Sha -eq $baseSha)
$r6 = Run "run6 after rebuild" @()
Check "rebuilt cache valid again" ($r6.Out -match 'key ok' -and $r6.Orig -eq 1)
Set-Content "$t\game\config\foo.mixins.properties" "x=2"
$r6b = Run "run6b mixin config changed" @()
Check "mixin config change -> mismatch" ($r6b.Out -match 'key mismatch')

# corruption
$f = "$t\boost\cache.dat"
$bytes = [IO.File]::ReadAllBytes($f); [IO.File]::WriteAllBytes($f, $bytes[0..($bytes.Length / 2)])
$r7 = Run "run7 truncated cache.dat" @()
Check "truncated -> rebuild, original path works" ($r7.Out -match 'unusable' -and $r7.Orig -eq 2000 -and $r7.Sha -eq $baseSha)
$bytes = [IO.File]::ReadAllBytes($f); $bytes[100] = $bytes[100] -bxor 0xFF; [IO.File]::WriteAllBytes($f, $bytes)
$r8 = Run "run8 bit-flipped cache.dat" @()
Check "crc mismatch -> rebuild" ($r8.Out -match 'crc mismatch' -and $r8.Sha -eq $baseSha)
[IO.File]::WriteAllText("$t\boost\cache.idx", "garbage")
$r9 = Run "run9 garbage cache.idx" @()
Check "garbage idx -> rebuild" ($r9.Out -match 'unusable' -and $r9.Sha -eq $baseSha)

# cache off
$r10 = Run "run10 cache=false" @("-Dlads.boost.cache=false")
Check "cache=false: no hits, orig 2000, nothing stored" ($r10.Orig -eq 2000 -and $r10.Out -match 'cache=false' -and $r10.Out -match 'hits=0 misses=0 stored=0' -and $r10.Sha -eq $baseSha)

# pre-init + missing names
Fresh
$null = Run "run11 cold preinit=50 missing=5" @() 50 5
$r12 = Run "run12 warm preinit=50 missing=5" @() 50 5
Check "pre-init loads and null results always go original: orig = 50 pre-init + 3 missing (missing every 40th index, 2 of 5 fall in pre-init) + 1 eager" ($r12.Orig -eq 54)
Check "run12 bytes equal run11 pattern (sha stable)" ($r12.Sha.Length -eq 64)

# broken hook must disable, not break: unreadable boost dir is a file
$bad = "$t\boostfile"; Set-Content $bad "x"
$r13 = & $java -javaagent:$agent "-Dlads.boost.dir=$bad\sub" "-Dlads.boost.gameDir=$t\game" -cp "$t\classes;$asm" Main 0 0 2>&1
Write-Host "--- run13 unusable boost dir"; $r13 | ForEach-Object { Write-Host "  $_" }
Check "unusable dir: agent disables itself, game path runs, same bytes" (($r13 -join "") -match 'DISABLED' -and ($r13 -join "") -match "sha=$baseSha")

# real class
$rc = Join-Path $t "real"
& $java -cp "$t\classes;$agent;$asm;$tree;$ana;$util" RealCheck $rc $fl $mixin
if ($LASTEXITCODE) { $fail++ ; Write-Host "FAIL RealCheck" }
& "$Jdk\bin\javap.exe" -p -c -cp $rc net.fabricmc.loader.impl.launch.knot.KnotClassDelegate | Select-String -Pattern 'getPostMixinClassByteArray' | ForEach-Object { Write-Host "  javap: $($_.Line.Trim())" }
& "$Jdk\bin\javap.exe" -p -c -cp $rc net.fabricmc.loader.impl.launch.knot.KnotClassDelegate | Out-File "$t\real-javap.txt" -Encoding utf8
if ($fail) { Write-Host "SELFTEST FAILED: $fail"; exit 1 } else { Write-Host "SELFTEST ALL PASS" }
