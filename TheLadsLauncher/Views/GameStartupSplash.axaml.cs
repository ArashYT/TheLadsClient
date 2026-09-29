using System;
using System.Diagnostics;
using Avalonia.Controls;
using Avalonia.Media;
using Avalonia.Threading;

namespace TheLadsLauncher.Views;

/// <summary>
/// Remains visible from process launch until the launcher detects the game window.
/// Motion follows Avalonia's display animation clock, without a 30 FPS UI timer.
/// </summary>
public partial class GameStartupSplash : Window
{
    private readonly Stopwatch _elapsed = new();
    private readonly Action<TimeSpan> _animateFrame;
    private bool _animating;
    private int _lastSecond = -1;
    private SplashFrameCadence? _previewCadence;

    public GameStartupSplash()
    {
        InitializeComponent();
        _animateFrame = Animate;
        Opened += (_, _) =>
        {
            _animating = true;
            _elapsed.Restart();
            RequestAnimationFrame(_animateFrame);
        };
        Closed += (_, _) =>
        {
            _animating = false;
            _elapsed.Stop();
            _previewCadence?.WriteReport();
        };
        PointerPressed += (_, e) =>
        {
            if (e.GetCurrentPoint(this).Properties.IsLeftButtonPressed)
                BeginMoveDrag(e);
        };
    }

    // Enabled only by the explicit preview command; production animation has no sampling allocations.
    public void CapturePreviewFrameCadence(string outputPath)
        => _previewCadence = new SplashFrameCadence(outputPath);

    public void SetGameVersion(string gameVersion)
    {
        if (!Dispatcher.UIThread.CheckAccess())
        {
            Dispatcher.UIThread.Post(() => SetGameVersion(gameVersion));
            return;
        }
        GameVersionText.Text = $"MINECRAFT {gameVersion}";
    }

    /// <summary>Allows launch progress events to report an observed stage.</summary>
    public void SetStatus(string status)
    {
        if (!Dispatcher.UIThread.CheckAccess())
        {
            Dispatcher.UIThread.Post(() => SetStatus(status));
            return;
        }
        SplashStatusText.Text = status;
    }

    private void Animate(TimeSpan frameTime)
    {
        if (!_animating) return;
        double seconds = _elapsed.Elapsed.TotalSeconds;
        _previewCadence?.Sample(seconds);

        // Animate only render transforms and opacity; no per-frame layout/Width changes.
        double entrance = 1 - Math.Pow(1 - Math.Min(1, seconds / 0.42), 3);
        SplashContent.Opacity = entrance;
        if (SplashContent.RenderTransform is TranslateTransform content)
            content.Y = 8 * (1 - entrance);

        if (SweepFill.RenderTransform is TranslateTransform sweep)
        {
            double phase = (seconds % 1.8) / 1.8;
            double eased = phase * phase * (3 - 2 * phase);
            sweep.X = -SweepFill.Width + eased * (ProgressTrack.Bounds.Width + SweepFill.Width);
        }

        if (ArtworkCard.RenderTransform is TranslateTransform artwork)
            artwork.Y = Math.Sin(seconds * Math.PI / 2.4) * 2;
        ActivityDot.Opacity = 0.6 + 0.4 * (0.5 + 0.5 * Math.Sin(seconds * Math.PI));

        int second = (int)seconds;
        if (second != _lastSecond)
        {
            _lastSecond = second;
            ElapsedText.Text = $"{second / 60}:{second % 60:00}";
        }
        RequestAnimationFrame(_animateFrame);
    }
}
