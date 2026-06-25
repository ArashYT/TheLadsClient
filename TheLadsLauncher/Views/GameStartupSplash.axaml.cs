using System;
using Avalonia.Controls;
using Avalonia.Media;
using Avalonia.Threading;

namespace TheLadsLauncher.Views;

/// <summary>
/// Borderless black splash shown the instant the game process starts, so
/// there is never a 5-10 second stretch of "nothing happening" before the
/// Minecraft window appears. Closed by MainWindow once the game window exists.
/// </summary>
public partial class GameStartupSplash : Window
{
    private readonly DispatcherTimer _timer;
    private readonly DateTime _opened = DateTime.UtcNow;

    private static readonly (double Seconds, string Text)[] Stages =
    {
        (0,  "Starting Java..."),
        (3,  "Loading mods..."),
        (9,  "Building the game..."),
        (16, "Almost there..."),
        (30, "Still loading (big modpack)..."),
    };

    public GameStartupSplash()
    {
        InitializeComponent();

        _timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(33) };
        _timer.Tick += (_, _) => Animate();
        _timer.Start();
        Closed += (_, _) => _timer.Stop();
    }

    private void Animate()
    {
        double elapsed = (DateTime.UtcNow - _opened).TotalSeconds;

        // Sweeping indeterminate bar: -90 .. 300 px, 1.4s cycle.
        // (x:Name on a Transform doesn't generate a field — go through the Border.)
        double t = elapsed % 1.4 / 1.4;
        if (SweepFill.RenderTransform is TranslateTransform sweep)
            sweep.X = -90 + t * 390;

        // Status text by elapsed time.
        string text = Stages[0].Text;
        foreach (var stage in Stages)
        {
            if (elapsed >= stage.Seconds) text = stage.Text;
        }
        if (SplashStatusText.Text != text) SplashStatusText.Text = text;
    }
}
