# Research helper: watches the first java process whose command line contains -Match (the QA sandbox path) and logs its
# top-level windows every 0.5 s (title, class, hung state, ghost windows), photographing the game window (PrintWindow, so covering windows do not matter) every -ShotEvery s.
# Read-only: never focuses, moves or clicks anything.  Usage: watch-window.ps1 -Match <text> -Out <dir> [-Seconds 600]
param([Parameter(Mandatory)] [string]$Match, [Parameter(Mandatory)] [string]$Out, [int]$Seconds = 600, [double]$ShotEvery = 2)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System; using System.Text; using System.Collections.Generic; using System.Runtime.InteropServices;
public static class W {
  public delegate bool P(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(P p, IntPtr l);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern bool IsHungAppWindow(IntPtr h);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetClassName(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out R r);
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
  [StructLayout(LayoutKind.Sequential)] public struct R { public int L, T, Rt, B; }
  public static List<object[]> All() {
    var list = new List<object[]>();
    EnumWindows((h, l) => { if (!IsWindowVisible(h)) return true; uint pid; GetWindowThreadProcessId(h, out pid);
      var t = new StringBuilder(256); GetWindowText(h, t, 256); var c = new StringBuilder(256); GetClassName(h, c, 256); R r; GetWindowRect(h, out r);
      list.Add(new object[] { h, (int)pid, t.ToString(), c.ToString(), IsHungAppWindow(h), r }); return true; }, IntPtr.Zero);
    return list;
  }
}
'@
[W]::SetProcessDPIAware() | Out-Null
New-Item -ItemType Directory -Force $Out | Out-Null
$log = Join-Path $Out 'window-log.tsv'
"seconds`tpid`ttitle`tclass`thung`trect" | Set-Content $log
$clock = [Diagnostics.Stopwatch]::StartNew(); $game = $null; $lastShot = -99.0; $shots = 0
while ($clock.Elapsed.TotalSeconds -lt $Seconds) {
  if (-not $game) {
    $game = Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" | Where-Object { $_.CommandLine -like "*$Match*" } | Select-Object -First 1
    if ($game) { "{0:F1}`tSTART pid {1}" -f $clock.Elapsed.TotalSeconds, $game.ProcessId | Add-Content $log; $clock.Restart() }
    Start-Sleep -Milliseconds 250; continue
  }
  if (-not (Get-Process -Id $game.ProcessId -ErrorAction SilentlyContinue)) { "{0:F1}`tEXIT" -f $clock.Elapsed.TotalSeconds | Add-Content $log; break }
  $t = $clock.Elapsed.TotalSeconds
  foreach ($w in [W]::All()) {
    $mine = $w[1] -eq $game.ProcessId
    $ghost = $w[3] -eq 'Ghost' -and ($w[2] -match 'Minecraft|Lads|Forge')
    if (-not ($mine -or $ghost)) { continue }
    $r = $w[5]
    "{0:F1}`t{1}`t{2}`t{3}`t{4}`t{5},{6},{7},{8}" -f $t, $w[1], $w[2], $w[3], $w[4], $r.L, $r.T, $r.Rt, $r.B | Add-Content $log
    if ($mine -and $w[3] -like 'GLFW*' -and ($t - $lastShot) -ge $ShotEvery -and ($r.Rt - $r.L) -gt 100) {
      $bmp = New-Object Drawing.Bitmap ($r.Rt - $r.L), ($r.B - $r.T)
      # PrintWindow(PW_RENDERFULLCONTENT) reads DWM's copy of the window: right even when other windows cover it.
      $g = [Drawing.Graphics]::FromImage($bmp); $hdc = $g.GetHdc(); [W]::PrintWindow($w[0], $hdc, 2) | Out-Null; $g.ReleaseHdc($hdc); $g.Dispose()
      $bmp.Save((Join-Path $Out ("shot-{0:000.0}s.png" -f $t)), [Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
      $lastShot = $t; $shots++
    }
  }
  Start-Sleep -Milliseconds 500
}
"done, $shots shots" | Add-Content $log
