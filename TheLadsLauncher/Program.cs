using Avalonia;
using System;
using System.Reflection;

namespace TheLadsLauncher;

class Program
{
    public static Services.DeepLinkAction? InitialDeepLink { get; set; }

    public static readonly string Version = typeof(Program).Assembly
        .GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion.Split('+')[0]
        ?? typeof(Program).Assembly.GetName().Version?.ToString(3)
        ?? "0.0.0";

    // Initialization code. Don't use any Avalonia, third-party APIs or any
    // SynchronizationContext-reliant code before AppMain is called: things aren't initialized
    // yet and stuff might break.
    [STAThread]
    public static void Main(string[] args)
    {
        // First of all: whatever nothing catches is written to the log before the process goes (it used to just disappear).
        AppDomain.CurrentDomain.UnhandledException += (_, e) => LogUnhandled(e.IsTerminating ? "AppDomain, fatal" : "AppDomain", e.ExceptionObject);
        System.Threading.Tasks.TaskScheduler.UnobservedTaskException += (_, e) => LogUnhandled("task", e.Exception);
        Velopack.VelopackApp.Build().SetAutoApplyOnStartup(false).Run();
        // Safety net: a launcher at High would starve games started from it. Windows passes only Idle/BelowNormal to child processes,
        // so the launcher Update.exe relaunches (raised to High by the bridge) normally starts at Normal anyway.
        // Only High/RealTime come down, so QA's Idle/BelowNormal runs stay as they are.
        try
        {
            using var self = System.Diagnostics.Process.GetCurrentProcess();
            if (self.PriorityClass is System.Diagnostics.ProcessPriorityClass.High or System.Diagnostics.ProcessPriorityClass.RealTime)
                self.PriorityClass = System.Diagnostics.ProcessPriorityClass.Normal;
        }
        catch (Exception) { } // never stops the launcher from starting

        // The in-game import dialog's command: no window, one JSON answer on stdout.
        if (System.Linq.Enumerable.Contains(args, TheLadsLauncher.Services.ClientImportCli.Flag))
        {
            Environment.Exit(TheLadsLauncher.Services.ClientImportCli.Run(args));
            return;
        }

        if (System.Linq.Enumerable.Contains(args, "--test-services"))
        {
            int exitCode = TheLadsLauncher.Services.ServiceTestSuite.RunAllTestsAsync().GetAwaiter().GetResult();
            Environment.Exit(exitCode);
            return;
        }

        // The JVM's -XX:OnError hook (CrashHook): the game just crashed natively. The JVM waits for this command, so it returns at once.
        // A running launcher is watching the game already and is left alone (no window to front); otherwise a normal launcher starts,
        // and on startup it finds the dead game through its running marker.
        if (System.Linq.Enumerable.Contains(args, TheLadsLauncher.Services.CrashHook.Flag))
        {
            bool running = TheLadsLauncher.Services.DeepLinkService.IsInstanceRunningAsync().GetAwaiter().GetResult();
            AppendLog($"[{DateTime.Now:HH:mm:ss}] [Launcher] The game's JVM reported a crash; " + (running ? "a launcher is running and watches it." : "no launcher is running, starting one."));
            if (!running)
            {
                try
                {
                    System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(Environment.ProcessPath!)
                        { UseShellExecute = true, WorkingDirectory = System.IO.Path.GetDirectoryName(Environment.ProcessPath)! })?.Dispose();
                }
                catch (Exception ex) { AppendLog($"[{DateTime.Now:HH:mm:ss}] [Launcher] Could not start the launcher after the game's crash: {ex.Message}"); }
            }
            Environment.Exit(0);
            return;
        }

        // QA captures of the shared-content and Mods UI: only ever against sandbox folders, never the real .minecraft or launcher data.
        foreach (var preview in new[] { "--preview-shared", "--preview-mods", "--preview-worlds", "--preview-servers", "--preview-productivity", "--preview-discovery", "--preview-startup", "--preview-chrome", "--preview-content", "--preview-modpacks", "--preview-skins", "--preview-language", "--preview-page", "--preview-installing" })
        {
            int index = System.Array.IndexOf(args, preview);
            if (index >= 0 && (index + 1 >= args.Length
                || !IsSandboxFolder(Environment.GetEnvironmentVariable("THELADS_DIR"))
                || !IsSandboxFolder(Environment.GetEnvironmentVariable(TheLadsLauncher.Services.SharedContentService.RootEnvironmentVariable))))
            {
                Console.Error.WriteLine($"{preview} <outputDir> runs only with THELADS_DIR and LADS_GLOBAL_MINECRAFT_DIR set to sandbox folders (absolute paths outside %APPDATA%\\.minecraft and %APPDATA%\\.theladsclient). Nothing was started.");
                Environment.Exit(2);
                return;
            }
            // The preview migrates and links every profile's game folder, and a profile's own folder (CustomGameDir) can point anywhere.
            if (index >= 0 && ProfilesOutsideSandbox(preview) is { } refusal)
            {
                Console.Error.WriteLine(refusal);
                Environment.Exit(2);
                return;
            }
        }

        // Every service reads the shared root; an unusable override must be reported here, not as a crash before any window.
        if (TheLadsLauncher.Services.SharedContentService.RootVariableProblem(
                Environment.GetEnvironmentVariable(TheLadsLauncher.Services.SharedContentService.RootEnvironmentVariable)) is { } problem)
        {
            Console.Error.WriteLine(problem);
            System.Windows.Forms.MessageBox.Show(problem, "The Lads Client", System.Windows.Forms.MessageBoxButtons.OK, System.Windows.Forms.MessageBoxIcon.Error);
            Environment.Exit(2);
            return;
        }

        foreach (var arg in args)
        {
            if (arg.StartsWith(TheLadsLauncher.Services.DeepLinkService.ProtocolScheme + "://", StringComparison.OrdinalIgnoreCase))
            {
                if (TheLadsLauncher.Services.DeepLinkService.TryForwardToRunningInstanceAsync(arg).GetAwaiter().GetResult())
                {
                    Environment.Exit(0);
                    return;
                }
                InitialDeepLink = TheLadsLauncher.Services.DeepLinkService.ParseUri(arg);
                break;
            }
        }

        // Opened again (shortcut, Start menu) while it already runs, often hidden in the tray during a game: bring that one back.
        // A second launcher would start the same profile or modpack a second time in the same folder. Starts with arguments
        // (QA, previews, --play-profile) are left alone.
        if (args.Length == 0 && TheLadsLauncher.Services.DeepLinkService.TryForwardToRunningInstanceAsync(
                TheLadsLauncher.Services.DeepLinkService.ProtocolScheme + "://show").GetAwaiter().GetResult())
        {
            Environment.Exit(0);
            return;
        }

        if (OperatingSystem.IsWindows() && TheLadsLauncher.Services.DeepLinkService.InstalledStub(Environment.ProcessPath) is { } stub
            && TheLadsLauncher.Services.DeepLinkService.NeedsRegistration(TheLadsLauncher.Services.DeepLinkService.RegisteredCommand(), stub))
        {
            TheLadsLauncher.Services.DeepLinkService.RegisterProtocol(stub);
        }

        // A launcher that is killed or crashes leaves no line; every on-purpose exit does (ExitBreadcrumb), this catches the other normal ones.
        // Environment.Exit paths (update restart, previews) and fatal exceptions end here without closing the window: the tray icon must go too,
        // or Windows keeps a ghost icon of the dead process in the tray until the mouse passes over it.
        AppDomain.CurrentDomain.ProcessExit += (_, _) => { ExitBreadcrumb("process exit (no other reason was logged)"); MainWindow.RemoveTrayIcon(); };
        AppDomain.CurrentDomain.UnhandledException += (_, _) => MainWindow.RemoveTrayIcon();
        BuildAvaloniaApp().StartWithClassicDesktopLifetime(args);
    }

    private static int _exitLogged;

    /// <summary>Writes, synchronously and flushed, why the launcher ends on purpose (the first reason wins). Its log is queued and written in the
    /// background, so a line from here is the one that is sure to be there: a launcher that vanishes WITHOUT it was killed or crashed.</summary>
    internal static void ExitBreadcrumb(string reason)
    {
        if (System.Threading.Interlocked.Exchange(ref _exitLogged, 1) != 0) return;
        AppendLog($"[{DateTime.Now:HH:mm:ss}] [Launcher] Exiting: {reason}.");
    }

    // Why the preview must not run because a profile's game folder (links resolved) is outside THELADS_DIR, or null.
    private static string? ProfilesOutsideSandbox(string preview)
    {
        try
        {
            var outside = TheLadsLauncher.Services.ProfileService.Instance.ProfilesOutside(Environment.GetEnvironmentVariable("THELADS_DIR")!);
            return outside.Count == 0 ? null : $"{preview} refuses to run: the game folder of " + string.Join(", ", System.Linq.Enumerable.Select(outside,
                p => $"'{p.Name}' ('{TheLadsLauncher.Services.PathService.Instance.GetProfileDirectory(p)}')")) + " is outside THELADS_DIR. Nothing was started.";
        }
        catch (Exception e) when (e is System.IO.IOException or UnauthorizedAccessException or System.IO.InvalidDataException or ArgumentException)
        {
            return $"{preview} could not check the profiles' game folders ({e.Message}). Nothing was started.";
        }
    }

    // Set, absolute, and (links resolved) neither the real .minecraft nor the real launcher folder, nor inside them.
    private static bool IsSandboxFolder(string? dir)
    {
        if (string.IsNullOrWhiteSpace(dir) || !System.IO.Path.IsPathFullyQualified(dir)) return false;
        var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        try
        {
            var real = TheLadsLauncher.Services.SafeFileOps.GetFinalPath(dir);
            return !System.Linq.Enumerable.Any(new[] { ".minecraft", ".theladsclient" }, name => TheLadsLauncher.Services.SafeFileOps.IsSameOrInside(
                real, TheLadsLauncher.Services.SafeFileOps.GetFinalPath(System.IO.Path.Combine(appData, name))));
        }
        catch (Exception e) when (e is System.IO.IOException or UnauthorizedAccessException or ArgumentException or NotSupportedException)
        {
            Console.Error.WriteLine($"Could not check '{dir}': {e.Message}");
            return false; // not provably a sandbox: refused
        }
    }

    // Avalonia configuration, don't remove; also used by visual designer.
    public static AppBuilder BuildAvaloniaApp()
        => AppBuilder.Configure<App>()
            .UsePlatformDetect()
            .WithInterFont()
            .LogToTrace()
            .AfterSetup(_ => Avalonia.Threading.Dispatcher.UIThread.UnhandledException += OnUiThreadException);

    /// <summary>
    /// Writes an exception that nothing caught to logs\launcher_debug.txt (where MainWindow.Log writes) and, for non-task exceptions,
    /// in full as the latest one to logs\crash_ui.txt. Synchronous, because the process may be going: the log is the only trace a fatal one leaves. Never throws.
    /// </summary>
    internal static void LogUnhandled(string source, object? error)
    {
        try
        {
            string text = $"[{DateTime.Now:HH:mm:ss}] [UNHANDLED {source}] {error}";
            AppendLog(text);
            if (!string.Equals(source, "task", StringComparison.OrdinalIgnoreCase))
                System.IO.File.WriteAllText(System.IO.Path.Combine(Services.PathService.Instance.LogsDirectory, "crash_ui.txt"), text);
        }
        catch (Exception) { } // the log must never be what ends the launcher
    }

    // One line appended to logs\launcher_debug.txt, written before it returns. Never throws.
    private static void AppendLog(string text)
    {
        try
        {
            string logs = Services.PathService.Instance.LogsDirectory;
            System.IO.Directory.CreateDirectory(logs);
            using var stream = new System.IO.FileStream(System.IO.Path.Combine(logs, "launcher_debug.txt"), System.IO.FileMode.Append,
                System.IO.FileAccess.Write, System.IO.FileShare.ReadWrite); // MainWindow's writer opens it too
            using var writer = new System.IO.StreamWriter(stream);
            writer.WriteLine(text);
        }
        catch (Exception) { } // the log must never be what ends the launcher
    }

    private static string? _lastUiError;
    private static DateTime _lastUiErrorAt;

    // An exception that escapes any UI handler (the launcher has 80+ async void ones) would end the process, and with it the exit handling
    // of a running game (options sync-back, crash report). It is logged and shown instead. Only a state the process cannot be trusted in
    // still ends it, logged. The same error again within 30 s (a timer that keeps throwing) is handled quietly, not logged over and over.
    private static void OnUiThreadException(object? sender, Avalonia.Threading.DispatcherUnhandledExceptionEventArgs e)
    {
        if (e.Exception is OutOfMemoryException or AccessViolationException)
        {
            LogUnhandled("UI thread, fatal", e.Exception);
            return;
        }
        e.Handled = true;
        string key = e.Exception.GetType().FullName + e.Exception.Message;
        if (key == _lastUiError && DateTime.UtcNow - _lastUiErrorAt < TimeSpan.FromSeconds(30)) return;
        (_lastUiError, _lastUiErrorAt) = (key, DateTime.UtcNow);
        LogUnhandled("UI thread", e.Exception);
        try
        {
            if ((Application.Current?.ApplicationLifetime as Avalonia.Controls.ApplicationLifetimes.IClassicDesktopStyleApplicationLifetime)?.MainWindow is MainWindow window)
                window.ShowUnhandled(e.Exception);
        }
        catch (Exception) { } // the notice is a courtesy; the log has it
    }
}
