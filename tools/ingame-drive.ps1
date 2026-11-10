# In-game check driver: sends keys and clicks to a game (or launcher) window and saves screenshots.
# Run it on the PC that has the game, inside the logged-in user's session.
#   .\ingame-drive.ps1 -Do "focus; key T; type hello; key ENTER; wait 500; shot chat"
#   .\ingame-drive.ps1 -Proc TheLadsLauncher -Do "uia LaunchButton"
# Actions (separated by ';'): focus | info | key NAME [holdMs] | keydown NAME | keyup NAME | type TEXT
#   | move FX FY | click [FX FY] | rclick [FX FY] | down L|R | up L|R | wait MS | shot NAME | close | uia ID | waitwin SECONDS
# FX/FY are fractions (0..1) of the window's client area. Screenshots: <Out>\<NAME>.jpg (at most 1280 wide).
param(
    [string]$Do = 'info',
    [string]$Proc = '^javaw?$',
    [string]$Out = (Join-Path $env:TEMP 'lads-drive')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing, UIAutomationClient, UIAutomationTypes
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class LadsDrive {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
    [StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT { public int dx, dy; public uint data, flags, time; public IntPtr extra; }
    [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT { public ushort vk, scan; public uint flags, time; public IntPtr extra; }
    [StructLayout(LayoutKind.Explicit)] public struct UNION { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] public struct INPUT { public uint type; public UNION u; }
    [DllImport("user32.dll")] static extern uint SendInput(uint n, INPUT[] inputs, int size);
    [DllImport("user32.dll")] static extern uint MapVirtualKey(uint code, uint type);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    static void Send(INPUT i) { SendInput(1, new INPUT[] { i }, Marshal.SizeOf(typeof(INPUT))); }
    public static void Key(ushort vk, bool down, bool ext) {
        INPUT i = new INPUT(); i.type = 1;
        i.u.ki.scan = (ushort)MapVirtualKey(vk, 0);
        i.u.ki.flags = 0x8u | (ext ? 0x1u : 0u) | (down ? 0u : 0x2u);
        Send(i);
    }
    public static void Char(char c) {
        INPUT i = new INPUT(); i.type = 1; i.u.ki.scan = c; i.u.ki.flags = 0x4u; Send(i);
        i.u.ki.flags = 0x4u | 0x2u; Send(i);
    }
    public static void Mouse(uint flags) { INPUT i = new INPUT(); i.type = 0; i.u.mi.flags = flags; Send(i); }
}
"@
[LadsDrive]::SetProcessDPIAware() | Out-Null

$keys = @{ ESC = 0x1B; ENTER = 0x0D; TAB = 0x09; SPACE = 0x20; SHIFT = 0x10; CTRL = 0x11; BACK = 0x08; SLASH = 0xBF
    UP = 0x26; DOWN = 0x28; LEFT = 0x25; RIGHT = 0x27 }
1..12 | ForEach-Object { $keys["F$_"] = 0x6F + $_ }
$extended = 'UP', 'DOWN', 'LEFT', 'RIGHT'
function Send-Key([string]$name, [bool]$down) {
    $n = $name.ToUpper()
    $vk = if ($keys.ContainsKey($n)) { $keys[$n] } elseif ($n.Length -eq 1) { [int][char]$n } else { throw "Unknown key '$name'" }
    [LadsDrive]::Key([uint16]$vk, $down, ($extended -contains $n))
}
function Get-Win {
    Get-Process | Where-Object { $_.ProcessName -match $Proc -and $_.MainWindowHandle -ne 0 } | Select-Object -First 1
}
function Get-Area($h) {
    $r = New-Object LadsDrive+RECT; [LadsDrive]::GetClientRect($h, [ref]$r) | Out-Null
    $p = New-Object LadsDrive+POINT; [LadsDrive]::ClientToScreen($h, [ref]$p) | Out-Null
    @{ X = $p.X; Y = $p.Y; W = $r.R - $r.L; H = $r.B - $r.T }
}
function Set-Focus($h) {
    if ([LadsDrive]::IsIconic($h)) { [LadsDrive]::ShowWindow($h, 9) | Out-Null }
    if ([LadsDrive]::GetForegroundWindow() -ne $h) {
        # Windows only lets a process take the foreground right after input: tap ALT first.
        [LadsDrive]::Key(0x12, $true, $false); [LadsDrive]::Key(0x12, $false, $false)
        [LadsDrive]::SetForegroundWindow($h) | Out-Null
        Start-Sleep -Milliseconds 300
    }
    [LadsDrive]::GetForegroundWindow() -eq $h
}
function Move-To($h, [double]$fx, [double]$fy) {
    $a = Get-Area $h
    [LadsDrive]::SetCursorPos([int]($a.X + $fx * $a.W), [int]($a.Y + $fy * $a.H)) | Out-Null
    Start-Sleep -Milliseconds 120
}
function Save-Shot($h, [string]$name) {
    $a = Get-Area $h
    if ($a.W -le 0 -or $a.H -le 0) { "shot $name skipped: window has no area"; return }
    New-Item -ItemType Directory -Force $Out | Out-Null
    $bmp = New-Object System.Drawing.Bitmap $a.W, $a.H
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($a.X, $a.Y, 0, 0, $bmp.Size); $g.Dispose()
    if ($a.W -gt 1280) {
        $small = New-Object System.Drawing.Bitmap $bmp, 1280, ([int]($a.H * 1280 / $a.W))
        $bmp.Dispose(); $bmp = $small
    }
    $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
    $quality = New-Object System.Drawing.Imaging.EncoderParameters 1
    $quality.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), 80L
    $file = Join-Path $Out "$name.jpg"
    $bmp.Save($file, $codec, $quality); $bmp.Dispose()
    "shot $file $($a.W)x$($a.H)"
}

$buttons = @{ L = @(0x2, 0x4); R = @(0x8, 0x10) }
foreach ($step in ($Do -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
    $verb, $rest = $step -split '\s+', 2
    $a = @(if ($rest) { $rest -split '\s+' })
    if ($verb -eq 'wait') { Start-Sleep -Milliseconds ([int]$a[0]); continue }
    if ($verb -eq 'waitwin') {
        $until = (Get-Date).AddSeconds([int]$a[0])
        while (-not (Get-Win) -and (Get-Date) -lt $until) { Start-Sleep -Milliseconds 500 }
    }
    $win = Get-Win
    if (-not $win) { "no window for process '$Proc' (step: $step)"; exit 2 }
    $h = $win.MainWindowHandle
    switch ($verb) {
        'waitwin' { "window: $($win.ProcessName) pid=$($win.Id) '$($win.MainWindowTitle)'" }
        'info' { $r = Get-Area $h; "window: $($win.ProcessName) pid=$($win.Id) '$($win.MainWindowTitle)' $($r.W)x$($r.H) at $($r.X),$($r.Y) foreground=$([LadsDrive]::GetForegroundWindow() -eq $h)" }
        'focus' { "focus=$(Set-Focus $h)" }
        'key' { Send-Key $a[0] $true; Start-Sleep -Milliseconds $(if ($a.Count -gt 1) { [int]$a[1] } else { 60 }); Send-Key $a[0] $false; Start-Sleep -Milliseconds 120 }
        'keydown' { Send-Key $a[0] $true }
        'keyup' { Send-Key $a[0] $false }
        'type' { foreach ($c in $rest.ToCharArray()) { [LadsDrive]::Char($c); Start-Sleep -Milliseconds 20 }; Start-Sleep -Milliseconds 120 }
        'move' { Move-To $h $a[0] $a[1] }
        { $_ -in 'click', 'rclick' } {
            if ($a.Count -ge 2) { Move-To $h $a[0] $a[1] }
            $b = $buttons[$(if ($verb -eq 'click') { 'L' } else { 'R' })]
            [LadsDrive]::Mouse($b[0]); Start-Sleep -Milliseconds 60; [LadsDrive]::Mouse($b[1]); Start-Sleep -Milliseconds 150
        }
        'down' { [LadsDrive]::Mouse($buttons[$a[0].ToUpper()][0]) }
        'up' { [LadsDrive]::Mouse($buttons[$a[0].ToUpper()][1]) }
        'shot' { Save-Shot $h $a[0] }
        'close' { [LadsDrive]::PostMessage($h, 0x10, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null; 'close sent' }
        'uia' {
            $root = [System.Windows.Automation.AutomationElement]::FromHandle($h)
            $cond = New-Object System.Windows.Automation.PropertyCondition ([System.Windows.Automation.AutomationElement]::AutomationIdProperty), $a[0]
            $el = $root.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $cond)
            if (-not $el) { "uia: no element '$($a[0])'"; exit 3 }
            $el.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke()
            "uia: invoked '$($a[0])' ($($el.Current.Name)) enabled=$($el.Current.IsEnabled)"
        }
        default { throw "Unknown action '$verb'" }
    }
}
