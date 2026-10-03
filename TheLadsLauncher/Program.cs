using Avalonia;
using System;
using System.Reflection;

namespace TheLadsLauncher;

class Program
{
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
        Velopack.VelopackApp.Build().SetAutoApplyOnStartup(false).Run();

        if (System.Linq.Enumerable.Contains(args, "--test-services"))
        {
            int exitCode = TheLadsLauncher.Services.ServiceTestSuite.RunAllTestsAsync().GetAwaiter().GetResult();
            Environment.Exit(exitCode);
            return;
        }

        // QA captures of the shared-content and Mods UI: only ever against sandbox folders, never the real .minecraft or launcher data.
        foreach (var preview in new[] { "--preview-shared", "--preview-mods", "--preview-worlds", "--preview-productivity", "--preview-discovery", "--preview-startup", "--preview-content" })
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
            .LogToTrace();
}
