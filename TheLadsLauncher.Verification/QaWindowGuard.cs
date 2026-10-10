using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

/// <summary>
/// Places QA games for the owner (2026-10-08: "use the main monitor and you can do 3 instances at a time"): every game window goes
/// to a tile of the PRIMARY monitor's work area (2 x 2 grid; slot 1 top left, 2 top right, 3 bottom left, so three games never
/// overlap), resized to the tile unless LADS_VERIFY_NO_RESIZE=1, never activated, never left minimized. The slot comes from
/// QaShared.AcquireAsync (.qa-slot-1..5). Games run at High priority (owner: "do high priority QA Tests"). When a new game window
/// takes focus while the owner is using the PC, focus goes back to the window they were in. Runs that need the game in front
/// (synthetic input, raw mouse, F11/borderless captures) set LADS_VERIFY_FOCUS=1: no placement or focus give-back.
/// LADS_VERIFY_PLAYING=1 (the owner is playing and streaming, 2026-10-05): below-normal priority the whole run, no monitor
/// claim, and every game window is parked far off every screen without being activated (minimized, a game stops
/// rendering or its framebuffer shrinks, and captures come out empty).
/// </summary>
static class QaWindowGuard
{
    static QaWindowGuard() => SetProcessDPIAware(); // real pixels: a scaled primary monitor would otherwise report a smaller work area

    /// <summary>The primary monitor split 2 x 2: tile 1 top left, 2 top right, 3 bottom left, 4 bottom right. The whole screen, not its
    /// work area: half of a 1080 p work area (516 px) leaves a client height under 480 px, where Minecraft caps GUI scale 2 down to 1; the bottom
    /// tiles reach 48 px under the always-on-top taskbar, which does not matter to the game's framebuffer.</summary>
    public static Rectangle Tile(int slot)
    {
        var area = Screen.PrimaryScreen!.Bounds;
        int w = area.Width / 2, h = area.Height / 2;
        if (slot >= 5) return new Rectangle(area.X + w / 2, area.Y + h / 2, w, h); // 5th game: centred over the 2 x 2 grid (occluded windows still render)
        int i = Math.Clamp(slot, 1, 4) - 1;
        return new Rectangle(area.X + i % 2 * w, area.Y + i / 2 * h, w, h);
    }

    /// <summary>The game window's client size that fits <see cref="Tile"/> with its frame (what a game is asked to open at).</summary>
    public static Size TileClient(int slot) => new(Tile(slot).Width - 16, Tile(slot).Height - 39);

    public static void Watch(Process game, int slot = 1)
    {
        bool focus = Environment.GetEnvironmentVariable("LADS_VERIFY_FOCUS") == "1";
        bool playing = Environment.GetEnvironmentVariable("LADS_VERIFY_PLAYING") == "1";
        new Thread(() => Loop(game, focus, playing, slot)) { IsBackground = true, Name = "QA window guard" }.Start();
    }

    private static void Loop(Process game, bool focus, bool playing, int slot)
    {
        bool resize = Environment.GetEnvironmentVariable("LADS_VERIFY_NO_RESIZE") != "1";
        Rectangle target = focus || playing ? Rectangle.Empty : Tile(slot);
        ProcessPriorityClass wanted = playing ? ProcessPriorityClass.BelowNormal : ProcessPriorityClass.High;
        var firstSeen = new Dictionary<IntPtr, long>();
        IntPtr owners = GameWindow(GetForegroundWindow()) ? IntPtr.Zero : GetForegroundWindow();
        try
        {
            while (!game.HasExited)
            {
                // GameSession sets Realtime for startup, then Normal: a QA game runs at High (below normal while the owner plays) the whole time.
                if (game.PriorityClass != wanted) game.PriorityClass = wanted;
                if (focus) { Thread.Sleep(250); continue; }
                foreach (IntPtr window in Windows(game.Id))
                {
                    if (playing)
                    {
                        // Parked far off every screen, never minimized: a minimized game's framebuffer can shrink to a pixel
                        // (1.8.9 did, mid-capture), while an off-screen window keeps its size and keeps rendering.
                        firstSeen.TryAdd(window, Environment.TickCount64);
                        if (GetWindowRect(window, out var at) && at.Left > -20000)
                            SetWindowPos(window, IntPtr.Zero, -30000, 0, 0, 0, SwpNoSize | SwpNoZOrder | SwpNoActivate);
                        continue;
                    }
                    if (IsIconic(window)) ShowWindow(window, SwShowNoActivate); // never left minimized, and never activated
                    if (firstSeen.ContainsKey(window)) continue;
                    firstSeen[window] = Environment.TickCount64;
                    // A window as big as a screen is fullscreen/borderless under test: leave it where the game put it.
                    if (GetWindowRect(window, out var r) && !CoversScreen(r))
                        SetWindowPos(window, IntPtr.Zero, target.X, target.Y, resize ? target.Width : 0, resize ? target.Height : 0,
                            (resize ? 0u : SwpNoSize) | SwpNoZOrder | SwpNoActivate);
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

    private static bool CoversScreen(RectNative r) =>
        Screen.AllScreens.Any(s => r.Right - r.Left >= s.Bounds.Width && r.Bottom - r.Top >= s.Bounds.Height);

    /// <summary>A 1.8.9 (LWJGL 2) game window: it keeps rendering minimized.</summary>
    private static bool Lwjgl2(IntPtr window)
    {
        var name = new StringBuilder(64);
        GetClassName(window, name, name.Capacity);
        return name.ToString() == "LWJGL";
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
    [DllImport("user32.dll")] private static extern bool IsIconic(IntPtr window);
    [DllImport("user32.dll")] private static extern bool ShowWindow(IntPtr window, int command);
    private const int SwShowMinNoActive = 7, SwShowNoActivate = 4;
    [DllImport("user32.dll")] private static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr window);
    [DllImport("user32.dll")] private static extern bool GetWindowRect(IntPtr window, out RectNative rect);
    [DllImport("user32.dll")] private static extern bool SetWindowPos(IntPtr window, IntPtr after, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll")] private static extern bool AttachThreadInput(uint attach, uint to, bool join);
    [DllImport("user32.dll")] private static extern bool GetLastInputInfo(ref LastInputInfo info);
    [DllImport("kernel32.dll")] private static extern uint GetCurrentThreadId();
}
