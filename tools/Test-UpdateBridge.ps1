#Requires -Version 7.0
# Runs LadsUpdateBridge.exe against a fake Update.exe (a PING copy) in a temp root with spaces and a non-ASCII name.
# Never touches the real %TEMP%\TheLadsClient-update, %LOCALAPPDATA%\TheLadsClient or .theladsclient: TEMP/TMP point into the root.
# The bridge is a normal window (never WS_EX_TOPMOST); case 5 checks it only keeps itself above a window titled like the launcher.
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$root = Join-Path $repoRoot "artifacts/bridge-test/bridge test J$([char]0xF6)ns"
$tmp = Join-Path $root 'tmp'
$log = Join-Path $tmp 'TheLadsClient-update/bridge.log'
$updateExe = Join-Path $root 'Update.exe'
$relaunch = Join-Path $root 'The Lads Client.exe'
$bridgeExe = Join-Path $tmp 'TheLadsClient-update/LadsUpdateBridge.exe'
$png = Join-Path $root 'snapshot.png'
$inv = [cultureinfo]::InvariantCulture
$script:failures = 0

function Assert([bool]$ok, [string]$what) {
    if ($ok) { Write-Host "PASS $what" } else { Write-Host "FAIL $what"; $script:failures++ }
}

function Format-Rect([double[]]$r) { [string]::Join(',', [string[]]($r | ForEach-Object { $_.ToString($inv) })) }

function Start-Hidden([string]$exe, [string[]]$arguments) {
    $psi = [Diagnostics.ProcessStartInfo]::new($exe)
    foreach ($a in $arguments) { $psi.ArgumentList.Add($a) }
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.Environment['TEMP'] = $tmp
    $psi.Environment['TMP'] = $tmp
    [Diagnostics.Process]::Start($psi)
}

function Start-Bridge([double[]]$client, [double[]]$bar, [double]$grace, [string[]]$extra = @()) {
    Start-Hidden $bridgeExe (@('--image', $png, '--client', (Format-Rect $client), '--bar', (Format-Rect $bar),
        '--update-exe', $updateExe, '--relaunch', $relaunch, '--grace', $grace.ToString($inv)) + $extra)
}

function Read-Log { if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Raw } else { '' } }

function Stop-All([Diagnostics.Process[]]$processes) {
    foreach ($p in $processes) { if ($p -and -not $p.HasExited) { $p.Kill(); $p.WaitForExit(2000) | Out-Null } }
}

# Cases 1 and 3: shown -> window at --client -> Update.exe High -> LauncherShown -> exit, no relaunch.
function Invoke-ShownCase([string]$name, [double[]]$client, [double[]]$bar, [string]$appId) {
    Remove-Item -LiteralPath $log -ErrorAction SilentlyContinue
    $shown = [Threading.EventWaitHandle]::new($false, [Threading.EventResetMode]::ManualReset, 'Local\TheLadsClient.UpdateBridgeShown')
    $update = Start-Hidden $updateExe @('-n', '60', '127.0.0.1')
    $bridge = $null
    try {
        $bridge = Start-Bridge $client $bar 15 @(if ($appId) { '--app-id'; $appId })
        Assert ($shown.WaitOne(2000)) "${name}: shown event within 2 s"
        if ($appId) { Assert ((Read-Log) -match "(?m) app id $([regex]::Escape($appId))\r?$") "${name}: AppUserModelID $appId set before the window" }
        $bridge.Refresh()
        Assert ($bridge.MainWindowTitle -eq 'The Lads Client') "${name}: window titled 'The Lads Client' (got '$($bridge.MainWindowTitle)')"
        $rect = if ($bridge.MainWindowHandle -ne 0) { [BridgeTestWin32]::Rect($bridge.MainWindowHandle) -join ',' } else { 'no window' }
        Assert ($rect -eq ($client -join ',')) "${name}: window at $($client -join ',') (got $rect)"
        Assert ($bridge.MainWindowHandle -ne 0 -and -not [BridgeTestWin32]::TopMost($bridge.MainWindowHandle)) "${name}: window is NOT WS_EX_TOPMOST"
        $deadline = [DateTime]::UtcNow.AddSeconds(1)
        while ($update.PriorityClass -ne 'High' -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 50; $update.Refresh() }
        Assert ($update.PriorityClass -eq 'High') "${name}: fake Update.exe raised to High within 1 s (got $($update.PriorityClass))"
        $launcherShown = $null
        if ([Threading.EventWaitHandle]::TryOpenExisting('Local\TheLadsClient.LauncherShown', [ref]$launcherShown)) {
            $launcherShown.Set() | Out-Null
            $launcherShown.Dispose()
        }
        Assert ($bridge.WaitForExit(1500)) "${name}: bridge exits within 1.5 s of LauncherShown (350 ms hold + 150 ms fade)"
        Assert ((Read-Log) -notmatch 'relaunch') "${name}: log has no relaunch"
    } finally {
        Stop-All @($bridge, $update)
        $shown.Dispose()
    }
}

Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path (Split-Path $bridgeExe) -Force | Out-Null
& dotnet build (Join-Path $repoRoot 'TheLadsLauncher.UpdateBridge/TheLadsLauncher.UpdateBridge.csproj') -c Release -o (Join-Path $root 'build') -nologo -v q
$built = Join-Path $root 'build/LadsUpdateBridge.exe'
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $built)) { Write-Host "FAIL bridge build: $built missing"; exit 1 }
# The launcher runs the bridge from a %TEMP% copy (current\ gets replaced); so does this harness.
Copy-Item -LiteralPath $built -Destination $bridgeExe
Copy-Item -LiteralPath "$env:SystemRoot\System32\PING.EXE" -Destination $updateExe
Copy-Item -LiteralPath "$env:SystemRoot\System32\PING.EXE" -Destination $relaunch

Add-Type -AssemblyName System.Drawing
$bmp = [Drawing.Bitmap]::new(400, 300)
$g = [Drawing.Graphics]::FromImage($bmp)
$g.Clear([Drawing.Color]::FromArgb(0x17, 0x18, 0x1B))
$g.FillRectangle([Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(0xE0, 0x52, 0x5B)), 20, 200, 360, 18)
$g.Dispose()
$bmp.Save($png, [Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class BridgeTestWin32 {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] static extern IntPtr SetThreadDpiAwarenessContext(IntPtr context);
    [DllImport("user32.dll")] static extern int GetWindowLong(IntPtr hWnd, int index);
    [DllImport("user32.dll")] static extern IntPtr GetWindow(IntPtr hWnd, uint cmd);
    [DllImport("user32.dll")] static extern bool SetWindowPos(IntPtr hWnd, IntPtr after, int x, int y, int cx, int cy, uint flags);
    // True when a is above b in z-order (a is found walking GW_HWNDPREV from b).
    public static bool Above(IntPtr a, IntPtr b) {
        for (IntPtr w = GetWindow(b, 3); w != IntPtr.Zero; w = GetWindow(w, 3)) if (w == a) return true;
        return false;
    }
    // HWND_TOP without activating (NOMOVE | NOSIZE | NOACTIVATE).
    public static void Raise(IntPtr hWnd) { SetWindowPos(hWnd, IntPtr.Zero, 0, 0, 0, 0, 0x13); }
    [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    // A background process may not raise above the foreground window; the Alt tap lets this one take the foreground.
    public static void Foreground(IntPtr hWnd) { keybd_event(0x12, 0, 0, UIntPtr.Zero); keybd_event(0x12, 0, 2, UIntPtr.Zero); SetForegroundWindow(hWnd); }
    public static bool TopMost(IntPtr hWnd) { return (GetWindowLong(hWnd, -20) & 0x8) != 0; }   // GWL_EXSTYLE, WS_EX_TOPMOST
    // Physical pixels whatever this shell's own DPI awareness is (-4 = per-monitor v2).
    public static int[] Rect(IntPtr hWnd) {
        IntPtr old = SetThreadDpiAwarenessContext(new IntPtr(-4));
        try { RECT r; GetWindowRect(hWnd, out r); return new[] { r.Left, r.Top, r.Right - r.Left, r.Bottom - r.Top }; }
        finally { SetThreadDpiAwarenessContext(old); }
    }
}
'@

# Case 1: normal handoff, with the launcher's AppUserModelID so the bridge shares its taskbar button.
Invoke-ShownCase 'case 1' @(120, 140, 400, 300) @(140, 340, 360, 18) 'velopack.TheLadsClientBridgeTest'

# Case 2: Update.exe still running past the grace: no relaunch until it ends, then relaunch.
Remove-Item -LiteralPath $log -ErrorAction SilentlyContinue
$shown = [Threading.EventWaitHandle]::new($false, [Threading.EventResetMode]::ManualReset, 'Local\TheLadsClient.UpdateBridgeShown')
$update = Start-Hidden $updateExe @('-n', '7', '127.0.0.1')   # alive ~6 s
$bridge = $null
try {
    $started = [Diagnostics.Stopwatch]::StartNew()
    $bridge = Start-Bridge @(120, 140, 400, 300) @(140, 340, 360, 18) 2
    Assert ($shown.WaitOne(2000)) 'case 2: shown event within 2 s'
    Start-Sleep -Milliseconds ([Math]::Max(0, 4000 - $started.ElapsedMilliseconds))
    Assert (-not $bridge.HasExited) 'case 2: bridge still running at 4 s (Update.exe alive, grace 2 s)'
    $update.WaitForExit(10000) | Out-Null
    $ended = [Diagnostics.Stopwatch]::StartNew()
    while ((Read-Log) -notmatch '(?m)^\S+ \S+ relaunch ' -and $ended.ElapsedMilliseconds -lt 3000) { Start-Sleep -Milliseconds 50 }
    Assert ((Read-Log) -match '(?m)^\S+ \S+ relaunch ') "case 2: relaunch within 3 s of Update.exe ending ($($ended.ElapsedMilliseconds) ms)"
    Assert ($bridge.WaitForExit(1000)) 'case 2: bridge exits after relaunch'
} finally {
    Stop-All @($bridge, $update)
    $shown.Dispose()
}

# Case 3: the caller runs under de-DE (decimal comma); arguments are formatted invariant and still parse.
$culture = [cultureinfo]::CurrentCulture
try {
    [cultureinfo]::CurrentCulture = 'de-DE'
    Invoke-ShownCase 'case 3 (de-DE)' @(10, 20, 400, 300) @(30, 220.5, 360, 18)
} finally {
    [cultureinfo]::CurrentCulture = $culture
}

# Case 4: bad input ends the bridge with exit code 1 and a 'failed:' log line, without ever showing a window.
function Invoke-FailCase([string]$name, [string[]]$arguments) {
    Remove-Item -LiteralPath $log -ErrorAction SilentlyContinue
    $shown = [Threading.EventWaitHandle]::new($false, [Threading.EventResetMode]::ManualReset, 'Local\TheLadsClient.UpdateBridgeShown')
    $bridge = Start-Hidden $bridgeExe $arguments
    try {
        $exited = $bridge.WaitForExit(2000)
        Assert ($exited -and $bridge.ExitCode -eq 1) "${name}: exits with code 1 within 2 s (exited $exited$(if ($exited) { ", code $($bridge.ExitCode)" }))"
        Assert (-not $shown.WaitOne(0) -and (Read-Log) -notmatch '(?m) shown\r?$') "${name}: no window shown"
        Assert ((Read-Log) -match 'failed: ') "${name}: log has a 'failed:' line"
    } finally {
        Stop-All @($bridge)
        $shown.Dispose()
    }
}
Invoke-FailCase 'case 4a (malformed --client)' @('--image', $png, '--client', '10,20,400', '--bar', '30,220,360,18',
    '--update-exe', $updateExe, '--relaunch', $relaunch, '--grace', '15')
Invoke-FailCase 'case 4b (missing PNG)' @('--image', (Join-Path $root 'missing.png'), '--client', '10,20,400,300', '--bar', '30,220,360,18',
    '--update-exe', $updateExe, '--relaunch', $relaunch, '--grace', '15')

# Case 5: the bridge is not topmost; it only keeps itself directly above a visible window of another process titled like the launcher.
function Wait-Until([scriptblock]$cond, [int]$ms) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while (-not (& $cond) -and $sw.ElapsedMilliseconds -lt $ms) { [Windows.Forms.Application]::DoEvents(); Start-Sleep -Milliseconds 10 }
    $sw.ElapsedMilliseconds
}
Add-Type -AssemblyName System.Windows.Forms
Remove-Item -LiteralPath $log -ErrorAction SilentlyContinue
$shown = [Threading.EventWaitHandle]::new($false, [Threading.EventResetMode]::ManualReset, 'Local\TheLadsClient.UpdateBridgeShown')
$update = Start-Hidden $updateExe @('-n', '60', '127.0.0.1')
$bridge = $null
$fakeLauncher = $null
$other = $null
try {
    $bridge = Start-Bridge @(120, 140, 400, 300) @(140, 340, 360, 18) 15
    Assert ($shown.WaitOne(2000)) 'case 5: shown event within 2 s'
    $bridge.Refresh()
    $hwnd = $bridge.MainWindowHandle
    Assert ($hwnd -ne 0 -and -not [BridgeTestWin32]::TopMost($hwnd)) 'case 5: bridge is not WS_EX_TOPMOST'
    $fakeLauncher = [Windows.Forms.Form]::new()
    $fakeLauncher.Text = 'The Lads Client'
    $fakeLauncher.StartPosition = 'Manual'
    $fakeLauncher.Bounds = [Drawing.Rectangle]::new(120, 140, 400, 300)
    $fakeLauncher.ShowInTaskbar = $false
    $fakeLauncher.Show()
    [BridgeTestWin32]::Foreground($fakeLauncher.Handle)
    [BridgeTestWin32]::Raise($fakeLauncher.Handle)
    [Windows.Forms.Application]::DoEvents()
    $ms = Wait-Until { [BridgeTestWin32]::Above($hwnd, $fakeLauncher.Handle) } 500
    Assert ([BridgeTestWin32]::Above($hwnd, $fakeLauncher.Handle)) "case 5: bridge placed above the launcher-titled window within 500 ms ($ms ms)"
    Assert (-not [BridgeTestWin32]::TopMost($hwnd)) 'case 5: bridge still not WS_EX_TOPMOST after placing'
    Assert ((Read-Log) -match 'placed above the launcher window') "case 5: log has 'placed above the launcher window'"
    $other = [Windows.Forms.Form]::new()
    $other.Text = 'bridge test unrelated window'
    $other.StartPosition = 'Manual'
    $other.Bounds = [Drawing.Rectangle]::new(120, 140, 400, 300)
    $other.ShowInTaskbar = $false
    $other.Show()
    [BridgeTestWin32]::Foreground($other.Handle)
    [BridgeTestWin32]::Raise($other.Handle)
    Wait-Until { $false } 1000 | Out-Null
    Assert ([BridgeTestWin32]::Above($other.Handle, $hwnd)) 'case 5: an unrelated window raised afterwards stays above the bridge for 1 s'
    Assert ([BridgeTestWin32]::Above($hwnd, $fakeLauncher.Handle)) 'case 5: bridge still above the launcher-titled window'
    $launcherShown = $null
    if ([Threading.EventWaitHandle]::TryOpenExisting('Local\TheLadsClient.LauncherShown', [ref]$launcherShown)) {
        $launcherShown.Set() | Out-Null
        $launcherShown.Dispose()
    }
    $ms = Wait-Until { $bridge.HasExited } 1500
    Assert ($bridge.HasExited) "case 5: bridge exits within 1.5 s of LauncherShown ($ms ms)"
    Assert ((Read-Log) -notmatch 'relaunch') 'case 5: log has no relaunch'
} finally {
    foreach ($f in @($fakeLauncher, $other)) { if ($f) { $f.Close(); $f.Dispose() } }
    Stop-All @($bridge, $update)
    $shown.Dispose()
}

if ($script:failures) { Write-Host "Test-UpdateBridge: $($script:failures) FAILED (kept $root)"; exit 1 }
Remove-Item -LiteralPath $root -Recurse -Force
Write-Host 'Test-UpdateBridge: all cases PASS'
