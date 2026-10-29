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

        if (System.Linq.Enumerable.Contains(args, "--test-services"))
        {
            int exitCode = TheLadsLauncher.Services.ServiceTestSuite.RunAllTestsAsync().GetAwaiter().GetResult();
            Environment.Exit(exitCode);
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

        if (OperatingSystem.IsWindows() && TheLadsLauncher.Services.DeepLinkService.InstalledStub(Environment.ProcessPath) is { } stub
            && TheLadsLauncher.Services.DeepLinkService.NeedsRegistration(TheLadsLauncher.Services.DeepLinkService.RegisteredCommand(), stub))
        {
            TheLadsLauncher.Services.DeepLinkService.RegisterProtocol(stub);
        }

        BuildAvaloniaApp().StartWithClassicDesktopLifetime(args);
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
            string logs = Services.PathService.Instance.LogsDirectory;
            System.IO.Directory.CreateDirectory(logs);
            using (var stream = new System.IO.FileStream(System.IO.Path.Combine(logs, "launcher_debug.txt"), System.IO.FileMode.Append,
                       System.IO.FileAccess.Write, System.IO.FileShare.ReadWrite)) // MainWindow's writer keeps it open too
            using (var writer = new System.IO.StreamWriter(stream))
                writer.WriteLine(text);
            if (!string.Equals(source, "task", StringComparison.OrdinalIgnoreCase))
                System.IO.File.WriteAllText(System.IO.Path.Combine(logs, "crash_ui.txt"), text);
        }
        catch (Exception) { } // the log must never be what ends the launcher
    }

    internal const int MaxUiErrorsPerWindow = 10;
    internal static readonly TimeSpan UiErrorWindow = TimeSpan.FromSeconds(10);
    private static int _uiErrorBurstCount;
    private static DateTime _uiErrorWindowStart;
    private static string? _lastUiError;
    private static DateTime _lastUiErrorAt;

    // An exception that escapes any UI handler (the launcher has 80+ async void ones) would end the process, and with it the exit handling
    // of a running game (options sync-back, crash report). It is logged and shown instead. Only a state the process cannot be trusted in
    // still ends it, logged. The same error again within 30 s (a timer that keeps throwing) is handled quietly, not logged over and over.
    // If a rapid storm of unhandled UI errors exceeds 10 in 10 s, the circuit breaker trips and lets the process terminate.
    internal static void OnUiThreadException(object? sender, Avalonia.Threading.DispatcherUnhandledExceptionEventArgs e)
    {
        if (e.Exception is OutOfMemoryException or AccessViolationException)
        {
            LogUnhandled("UI thread, fatal", e.Exception);
            return;
        }

        DateTime now = DateTime.UtcNow;
        if (now - _uiErrorWindowStart > UiErrorWindow)
        {
            _uiErrorWindowStart = now;
            _uiErrorBurstCount = 0;
        }
        _uiErrorBurstCount++;
        if (_uiErrorBurstCount > MaxUiErrorsPerWindow)
        {
            LogUnhandled("UI thread, fatal (circuit breaker tripped)", e.Exception);
            return;
        }

        e.Handled = true;
        string key = e.Exception.GetType().FullName + e.Exception.Message;
        if (key == _lastUiError && now - _lastUiErrorAt < TimeSpan.FromSeconds(30)) return;
        (_lastUiError, _lastUiErrorAt) = (key, now);
        LogUnhandled("UI thread", e.Exception);
        try
        {
            if ((Application.Current?.ApplicationLifetime as Avalonia.Controls.ApplicationLifetimes.IClassicDesktopStyleApplicationLifetime)?.MainWindow is MainWindow window)
                window.ShowUnhandled(e.Exception);
        }
        catch (Exception) { } // the notice is a courtesy; the log has it
    }
}
