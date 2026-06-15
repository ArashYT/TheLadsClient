using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Media;
using Avalonia.Threading;
using System;
using System.Diagnostics;
using System.Threading.Tasks;

namespace TheLadsLauncher.Views;

public partial class StartupSplashView : UserControl
{
    public event EventHandler? StartupCompleted;

    private readonly double startupDurationMs = 2200.0;
    private readonly double startupBarWidth = 320.0;
    private Stopwatch? startupSw;
    private DispatcherTimer? startupAnimTimer;
    private int dots = 0;
    private double dotAccumulatorMs = 0;

    public StartupSplashView()
    {
        InitializeComponent();

        startupSw = Stopwatch.StartNew();
        startupAnimTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(16) };
        startupAnimTimer.Tick += OnAnimationTick;
        startupAnimTimer.Start();

        this.Loaded += OnLoaded;
    }

    private void OnAnimationTick(object? sender, EventArgs e)
    {
        if (startupSw == null) return;
        
        double t = startupSw.Elapsed.TotalMilliseconds;

        // Subtle breathing pulse on the logo
        double pulse = 1.0 + 0.04 * Math.Sin(t / 300.0);
        if (StartupLogoImage?.RenderTransform is ScaleTransform logoScale)
        {
            logoScale.ScaleX = pulse;
            logoScale.ScaleY = pulse;
        }

        // Eased progress fill (easeOutCubic)
        double p = Math.Min(1.0, t / startupDurationMs);
        double eased = 1.0 - Math.Pow(1.0 - p, 3);
        if (StartupProgressFill != null)
        {
            StartupProgressFill.Width = startupBarWidth * eased;
        }

        // Animated trailing dots
        dotAccumulatorMs += 16;
        if (dotAccumulatorMs >= 350)
        {
            dotAccumulatorMs = 0;
            dots = (dots + 1) % 4;
            if (LoadingDots != null)
            {
                LoadingDots.Text = new string('.', dots);
            }
        }
    }

    private async void OnLoaded(object? sender, RoutedEventArgs e)
    {
        await Task.Delay((int)startupDurationMs + 150);
        startupAnimTimer?.Stop();
        
        if (StartupProgressFill != null) 
        {
            StartupProgressFill.Width = startupBarWidth;
        }
        
        StartupCompleted?.Invoke(this, EventArgs.Empty);
    }
}
