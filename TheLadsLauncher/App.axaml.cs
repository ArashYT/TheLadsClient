using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using System;
using System.Linq;

namespace TheLadsLauncher;

public partial class App : Application
{
    public override void Initialize()
    {
        AvaloniaXamlLoader.Load(this);
    }

    public override void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            // The preview exercises the real splash without loading accounts or starting Java.
            if (desktop.Args?.Contains("--preview-game-splash", StringComparer.OrdinalIgnoreCase) == true)
            {
                var splash = new Views.GameStartupSplash();
                int reportOption = Array.FindIndex(desktop.Args, value => value.Equals("--splash-frame-report", StringComparison.OrdinalIgnoreCase));
                if (reportOption >= 0 && reportOption + 1 < desktop.Args.Length)
                    splash.CapturePreviewFrameCadence(desktop.Args[reportOption + 1]);
                splash.SetGameVersion("26.3");
                splash.SetStatus("Starting Minecraft");
                splash.KeyDown += (_, e) =>
                {
                    if (e.Key == Avalonia.Input.Key.Escape) splash.Close();
                };
                desktop.MainWindow = splash;
            }
            else
            {
                desktop.MainWindow = new MainWindow();
            }
        }

        base.OnFrameworkInitializationCompleted();
    }
}
