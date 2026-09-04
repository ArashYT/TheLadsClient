using Avalonia;
using System;

namespace TheLadsLauncher;

class Program
{
    public const string Version = "1.0.20";

    // Initialization code. Don't use any Avalonia, third-party APIs or any
    // SynchronizationContext-reliant code before AppMain is called: things aren't initialized
    // yet and stuff might break.
    [STAThread]
    public static void Main(string[] args)
    {
        if (System.Linq.Enumerable.Contains(args, "--test-services"))
        {
            int exitCode = TheLadsLauncher.Services.ServiceTestSuite.RunAllTestsAsync().GetAwaiter().GetResult();
            Environment.Exit(exitCode);
            return;
        }

        BuildAvaloniaApp().StartWithClassicDesktopLifetime(args);
    }

    // Avalonia configuration, don't remove; also used by visual designer.
    public static AppBuilder BuildAvaloniaApp()
        => AppBuilder.Configure<App>()
            .UsePlatformDetect()
            .WithInterFont()
            .LogToTrace();
}
