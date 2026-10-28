using System.Reflection;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class UnhandledExceptionLogTests
{
    // Program is internal; the handlers in Main/BuildAvaloniaApp are what call this.
    private static readonly MethodInfo LogUnhandled = typeof(PathService).Assembly.GetType("TheLadsLauncher.Program")!
        .GetMethod("LogUnhandled", BindingFlags.NonPublic | BindingFlags.Static)!;

    [Fact]
    public void AnUnhandledExceptionIsWrittenToTheLauncherLogAndTheCrashFile()
    {
        string logs = PathService.Instance.LogsDirectory;
        var error = new InvalidOperationException("boom from an async void handler");

        LogUnhandled.Invoke(null, new object?[] { "UI thread", error });

        string debug = File.ReadAllText(Path.Combine(logs, "launcher_debug.txt"));
        Assert.Contains("[UNHANDLED UI thread]", debug);
        Assert.Contains("boom from an async void handler", debug);
        string crash = File.ReadAllText(Path.Combine(logs, "crash_ui.txt"));
        Assert.Contains("InvalidOperationException", crash); // the full ToString(): type, message and stack
        Assert.Contains("boom from an async void handler", crash);

        // The next one replaces crash_ui.txt (the latest, in full) and is appended to the log.
        LogUnhandled.Invoke(null, new object?[] { "AppDomain, fatal", new Exception("second failure") });
        Assert.DoesNotContain("boom from an async void handler", File.ReadAllText(Path.Combine(logs, "crash_ui.txt")));
        Assert.Contains("second failure", File.ReadAllText(Path.Combine(logs, "crash_ui.txt")));
        string both = File.ReadAllText(Path.Combine(logs, "launcher_debug.txt"));
        Assert.Contains("boom from an async void handler", both);
        Assert.Contains("[UNHANDLED AppDomain, fatal]", both);
    }

    [Fact]
    public void ALogThatCannotBeWrittenNeverThrows()
    {
        string logs = PathService.Instance.LogsDirectory;
        Directory.CreateDirectory(logs);
        string crash = Path.Combine(logs, "crash_ui.txt");
        File.WriteAllText(crash, "kept");
        using var held = new FileStream(crash, FileMode.Open, FileAccess.Read, FileShare.None); // another process has the file open

        LogUnhandled.Invoke(null, new object?[] { "task", new Exception("while the file is locked") });
        LogUnhandled.Invoke(null, new object?[] { "task", null });
    }
}
