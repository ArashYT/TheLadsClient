param([string]$Match, [string]$OutDir, [int]$Seconds = 60, [int]$IntervalMs = 500, [string]$OnlyNot = "")
# Captures the QA game's own window (class LWJGL, process command line containing $Match) with PrintWindow
# (PW_RENDERFULLCONTENT): what the compositor shows for that window, as a window-capture tool sees it. Read-only.
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System; using System.Runtime.InteropServices; using System.Text; using System.Drawing;
public static class Cap {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc p, IntPtr l);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern int GetClassName(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint f);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  public static IntPtr Find(uint pid) {
    IntPtr found = IntPtr.Zero;
    EnumWindows((h, l) => { uint p; GetWindowThreadProcessId(h, out p); var s = new StringBuilder(64); GetClassName(h, s, 64);
      if (p == pid && s.ToString() == "LWJGL" && IsWindowVisible(h)) { found = h; return false; } return true; }, IntPtr.Zero);
    return found;
  }
  public static string skip;
  public static string Shot(IntPtr h, string file) {
    RECT r; GetWindowRect(h, out r); int w = r.R - r.L, hh = r.B - r.T; if (w <= 0 || hh <= 0) return null;
    if (skip != null && skip == w + "x" + hh) return null;
    using (var b = new Bitmap(w, hh)) { using (var g = Graphics.FromImage(b)) { var dc = g.GetHdc(); PrintWindow(h, dc, 2); g.ReleaseHdc(dc); } b.Save(file); }
    return w + "x" + hh + " at " + r.L + "," + r.T;
  }
}
"@
New-Item -ItemType Directory -Force $OutDir | Out-Null
if ($OnlyNot) { [Cap]::skip = $OnlyNot }
$end = (Get-Date).AddSeconds($Seconds); $n = 0; $start = Get-Date; $proc = $null
while ((Get-Date) -lt $end) {
  if (-not $proc) { $proc = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" | Where-Object { $_.CommandLine -like "*$Match*" } | Select-Object -First 1 }
  if ($proc) {
    $h = [Cap]::Find([uint32]$proc.ProcessId)
    if ($h -ne [IntPtr]::Zero) {
      $n++; $t = [int]((Get-Date) - $start).TotalMilliseconds
      $file = Join-Path $OutDir ("window-{0:D3}-{1}ms.png" -f $n, $t)
      $info = [Cap]::Shot($h, $file)
      if ($info) { "$t ms: $file $info" }
    }
  }
  Start-Sleep -Milliseconds $IntervalMs
}
