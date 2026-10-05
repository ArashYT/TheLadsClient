using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

/// <summary>
/// Keeps QA games out of the owner's way (owner, 2026-10-05: "I want to play"): every QA game runs at below-normal priority,
/// and every game window is minimized without being activated and kept minimized; when a new game window takes focus while
/// the owner is using the PC, focus goes back to the window they were in. LADS_VERIFY_FOCUS=1 (runs that need the game in
/// front) skips the window handling but not the priority; the owner asked that such runs wait until they say so.
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
        var firstSeen = new Dictionary<IntPtr, long>();
        IntPtr owners = GameWindow(GetForegroundWindow()) ? IntPtr.Zero : GetForegroundWindow();
        try
        {
            while (!game.HasExited)
            {
                // GameSession raises startup priority and later sets Normal: a QA game stays below normal the whole run.
                if (game.PriorityClass != ProcessPriorityClass.BelowNormal) game.PriorityClass = ProcessPriorityClass.BelowNormal;
                if (focus) { Thread.Sleep(250); continue; }
                foreach (IntPtr window in Windows(game.Id))
                {
                    firstSeen.TryAdd(window, Environment.TickCount64);
                    if (!IsIconic(window)) ShowWindow(window, SwShowMinNoActive);
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
    }

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

    private const int SwShowMinNoActive = 7;
    [StructLayout(LayoutKind.Sequential)] private struct LastInputInfo { public uint Size, Time; }
    private delegate bool EnumProc(IntPtr window, IntPtr parameter);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumProc callback, IntPtr parameter);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr window);
    [DllImport("user32.dll")] private static extern bool IsWindow(IntPtr window);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetClassName(IntPtr window, StringBuilder name, int capacity);
    [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr window);
    [DllImport("user32.dll")] private static extern bool IsIconic(IntPtr window);
    [DllImport("user32.dll")] private static extern bool ShowWindow(IntPtr window, int command);
    [DllImport("user32.dll")] private static extern bool AttachThreadInput(uint attach, uint to, bool join);
    [DllImport("user32.dll")] private static extern bool GetLastInputInfo(ref LastInputInfo info);
    [DllImport("kernel32.dll")] private static extern uint GetCurrentThreadId();
}
