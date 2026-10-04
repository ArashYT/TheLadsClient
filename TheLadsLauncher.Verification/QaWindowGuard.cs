using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

/// <summary>
/// Keeps QA games out of the owner's way: every new game window goes to the monitor OBS is on (else LADS_VERIFY_MONITOR,
/// a 1-based screen number, else the first secondary screen) without being activated, and when a new game window takes
/// focus while the owner is using the PC, focus goes back to the window they were in. Runs that need the game in front
/// (synthetic input, raw mouse, F11/borderless captures) set LADS_VERIFY_FOCUS=1 to keep today's behaviour.
/// Not minimized: a minimized 26.x game stops rendering, so its captures would be empty.
/// </summary>
static class QaWindowGuard
{
    public static void Watch(Process game)
    {
        if (Environment.GetEnvironmentVariable("LADS_VERIFY_FOCUS") == "1") return;
        new Thread(() => Loop(game)) { IsBackground = true, Name = "QA window guard" }.Start();
    }

    private static void Loop(Process game)
    {
        Rectangle target = TargetArea();
        var firstSeen = new Dictionary<IntPtr, long>();
        IntPtr owners = GameWindow(GetForegroundWindow()) ? IntPtr.Zero : GetForegroundWindow();
        try
        {
            while (!game.HasExited)
            {
                foreach (IntPtr window in Windows(game.Id))
                {
                    if (firstSeen.ContainsKey(window)) continue;
                    firstSeen[window] = Environment.TickCount64;
                    // A window as big as a screen is fullscreen/borderless under test: leave it where the game put it.
                    if (GetWindowRect(window, out var r) && !CoversScreen(r))
                        SetWindowPos(window, IntPtr.Zero, target.X + 40, target.Y + 40, 0, 0, SwpNoSize | SwpNoZOrder | SwpNoActivate);
                }
                IntPtr front = GetForegroundWindow();
                if (front != IntPtr.Zero && !GameWindow(front)) owners = front;
                else if (front != IntPtr.Zero && firstSeen.TryGetValue(front, out long seen) && Environment.TickCount64 - seen < 20_000
                    && owners != IntPtr.Zero && IsWindow(owners) && OwnerActiveWithin(60_000))
                    GiveBack(front, owners);
                Thread.Sleep(25);
            }
        }
        catch (InvalidOperationException) { } // the game process went away mid-check
    }

    private static Rectangle TargetArea()
    {
        Screen[] screens = Screen.AllScreens;
        IntPtr obs = Process.GetProcessesByName("obs64").Select(p => p.MainWindowHandle).FirstOrDefault(h => h != IntPtr.Zero);
        if (obs != IntPtr.Zero) return Screen.FromHandle(obs).WorkingArea;
        if (int.TryParse(Environment.GetEnvironmentVariable("LADS_VERIFY_MONITOR"), out int n) && n >= 1 && n <= screens.Length)
            return screens[n - 1].WorkingArea;
        return (screens.FirstOrDefault(s => !s.Primary) ?? Screen.PrimaryScreen ?? screens[0]).WorkingArea;
    }

    private static bool CoversScreen(RectNative r) =>
        Screen.AllScreens.Any(s => r.Right - r.Left >= s.Bounds.Width && r.Bottom - r.Top >= s.Bounds.Height);

    /// <summary>LWJGL 2 (1.8.9), GLFW (26.2) and SDL (26.3) game windows, so other QA games never count as the owner's window.</summary>
    private static bool GameWindow(IntPtr window)
    {
        var name = new StringBuilder(64);
        GetClassName(window, name, name.Capacity);
        string c = name.ToString();
        return c == "LWJGL" || c.StartsWith("GLFW", StringComparison.Ordinal) || c.StartsWith("SDL", StringComparison.Ordinal);
    }

    private static List<IntPtr> Windows(int processId)
    {
        var found = new List<IntPtr>();
        EnumWindows((window, _) =>
        {
            GetWindowThreadProcessId(window, out uint pid);
            if (pid == processId && IsWindowVisible(window)) found.Add(window);
            return true;
        }, IntPtr.Zero);
        return found;
    }

    private static bool OwnerActiveWithin(uint milliseconds)
    {
        var info = new LastInputInfo { Size = (uint)Marshal.SizeOf<LastInputInfo>() };
        return GetLastInputInfo(ref info) && (uint)Environment.TickCount - info.Time < milliseconds;
    }

    /// <summary>Windows lets only the foreground thread hand focus on, so borrow its input queue for the call.</summary>
    private static void GiveBack(IntPtr game, IntPtr owners)
    {
        uint gameThread = GetWindowThreadProcessId(game, out _), self = GetCurrentThreadId();
        AttachThreadInput(self, gameThread, true);
        try { SetForegroundWindow(owners); }
        finally { AttachThreadInput(self, gameThread, false); }
    }

    private const uint SwpNoSize = 0x1, SwpNoZOrder = 0x4, SwpNoActivate = 0x10;
    [StructLayout(LayoutKind.Sequential)] private struct RectNative { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] private struct LastInputInfo { public uint Size, Time; }
    private delegate bool EnumProc(IntPtr window, IntPtr parameter);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumProc callback, IntPtr parameter);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr window);
    [DllImport("user32.dll")] private static extern bool IsWindow(IntPtr window);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetClassName(IntPtr window, StringBuilder name, int capacity);
    [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr window);
    [DllImport("user32.dll")] private static extern bool GetWindowRect(IntPtr window, out RectNative rect);
    [DllImport("user32.dll")] private static extern bool SetWindowPos(IntPtr window, IntPtr after, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll")] private static extern bool AttachThreadInput(uint attach, uint to, bool join);
    [DllImport("user32.dll")] private static extern bool GetLastInputInfo(ref LastInputInfo info);
    [DllImport("kernel32.dll")] private static extern uint GetCurrentThreadId();
}
