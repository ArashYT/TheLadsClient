using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

/// <summary>
/// Places QA games for the owner (2026-10-05: "dont do minimized sessions do 1 on each monitor"): each game claims a free monitor
/// (a machine-wide claim, shared by every worktree's harness) and its window goes there without being activated; with every
/// monitor taken it shares the last one. Games run at High priority (owner: "do high priority QA Tests"). When a new game window
/// takes focus while the owner is using the PC, focus goes back to the window they were in. Runs that need the game in front
/// (synthetic input, raw mouse, F11/borderless captures) set LADS_VERIFY_FOCUS=1: no placement or focus give-back.
/// </summary>
static class QaWindowGuard
{
    public static void Watch(Process game)
    {
        bool focus = Environment.GetEnvironmentVariable("LADS_VERIFY_FOCUS") == "1";
        new Thread(() => Loop(game, focus)) { IsBackground = true, Name = "QA window guard" }.Start();
    }

    private static void Loop(Process game, bool focus)
    {
        string? claim = null;
        Rectangle target = focus ? Rectangle.Empty : ClaimMonitor(game.Id, out claim);
        var firstSeen = new Dictionary<IntPtr, long>();
        IntPtr owners = GameWindow(GetForegroundWindow()) ? IntPtr.Zero : GetForegroundWindow();
        try
        {
            while (!game.HasExited)
            {
                // GameSession sets Realtime for startup, then Normal: a QA game runs at High the whole time.
                if (game.PriorityClass != ProcessPriorityClass.High) game.PriorityClass = ProcessPriorityClass.High;
                if (focus) { Thread.Sleep(250); continue; }
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
        catch (Exception e) when (e is InvalidOperationException or System.ComponentModel.Win32Exception) { } // the game went away mid-check
        finally { if (claim != null) try { Directory.Delete(claim, true); } catch (IOException) { } }
    }

    /// <summary>Claims the first monitor no live QA game holds (%TEMP%\lads-qa-monitors\N holds the game's PID).</summary>
    private static Rectangle ClaimMonitor(int pid, out string? claim)
    {
        Screen[] screens = Screen.AllScreens.OrderBy(s => s.Bounds.X).ThenBy(s => s.Bounds.Y).ToArray();
        string root = Path.Combine(Path.GetTempPath(), "lads-qa-monitors");
        Directory.CreateDirectory(root);
        for (int i = 0; i < screens.Length; i++)
        {
            string dir = Path.Combine(root, (i + 1).ToString());
            string owner = Path.Combine(dir, "pid");
            if (Directory.Exists(dir) && !(File.Exists(owner) && int.TryParse(File.ReadAllText(owner), out int held) && Alive(held)))
                try { Directory.Delete(dir, true); } catch (IOException) { continue; } // stale claim of a game that is gone
            try
            {
                if (Directory.Exists(dir)) continue;
                Directory.CreateDirectory(dir); // not atomic between harnesses: the PID check below settles a race
                File.WriteAllText(owner, pid.ToString());
                Thread.Sleep(50);
                if (File.ReadAllText(owner) != pid.ToString()) continue;
                claim = dir;
                return screens[i].WorkingArea;
            }
            catch (IOException) { }
        }
        claim = null;
        return screens[^1].WorkingArea;
    }

    private static bool Alive(int pid)
    {
        try { return !Process.GetProcessById(pid).HasExited; }
        catch (ArgumentException) { return false; }
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
