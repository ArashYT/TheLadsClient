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

    /// <summary>The Cancel button: the launcher stops the starting game. The splash closes either way.</summary>
    public event Action? CancelRequested;

    private void CancelLaunch_Click(object? sender, Avalonia.Interactivity.RoutedEventArgs e)
    {
        CancelRequested?.Invoke();
        Close();
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

    public void SetDetail(string detail)
    {
        if (!Dispatcher.UIThread.CheckAccess())
        {
            Dispatcher.UIThread.Post(() => SetDetail(detail));
            return;
        }
        SplashDetailText.Text = detail;
    }

    private volatile bool _hasObservedLogActivity;
    private long _lastOutputUpdateTicks;

    public void ReportOutputLine(string? rawLine)
    {
        if (string.IsNullOrWhiteSpace(rawLine)) return;
        string clean = CleanLogPrefix(rawLine);
        if (string.IsNullOrWhiteSpace(clean)) return;

        var (stage, detail) = DescribeStartupStage(clean);
        long now = Environment.TickCount64;
        if (stage != null)
        {
            _hasObservedLogActivity = true;
            _lastOutputUpdateTicks = now;
            Dispatcher.UIThread.Post(() =>
            {
                SplashStatusText.Text = stage;
                if (!string.IsNullOrEmpty(detail))
                    SplashDetailText.Text = detail;
            });
            return;
        }

        // For regular info lines, update the live detail subtitle at most every 100ms so activity is always visible.
        if (now - _lastOutputUpdateTicks >= 100 && IsInformativeLine(clean))
        {
            _hasObservedLogActivity = true;
            _lastOutputUpdateTicks = now;
            string clipped = clean.Length > 84 ? clean[..81] + "..." : clean;
            Dispatcher.UIThread.Post(() => SplashDetailText.Text = clipped);
        }
    }

    public void UpdateElapsedFallback(double elapsedSeconds)
    {
        if (_hasObservedLogActivity) return;
        string status;
        string detail;
        if (elapsedSeconds < 0.9)
        {
            status = "Starting Java Virtual Machine...";
            detail = "Launching JVM process and initializing memory heap...";
        }
        else if (elapsedSeconds < 2.2)
        {
            status = "Initializing mod loader...";
            detail = "Scanning classpath and verifying mod archives...";
        }
        else if (elapsedSeconds < 4.5)
        {
            status = "Loading mods & bytecode transformers...";
            detail = "Preparing Mixin subsystem and game provider...";
        }
        else
        {
            status = "Bootstrapping Minecraft...";
            detail = "Initializing game registries and graphics subsystem...";
        }

        Dispatcher.UIThread.Post(() =>
        {
            if (_hasObservedLogActivity) return;
            SplashStatusText.Text = status;
            SplashDetailText.Text = detail;
        });
    }

    private static string CleanLogPrefix(string line)
    {
        string s = line.Trim();
        // Strip leading [HH:mm:ss] and [Thread/LEVEL]: prefixes
        while (s.StartsWith('['))
        {
            int close = s.IndexOf(']');
            if (close < 0 || close + 1 >= s.Length) break;
            s = s[(close + 1)..].TrimStart();
            if (s.StartsWith(':')) s = s[1..].TrimStart();
        }
        // Strip leading (ModName) prefix if present
        if (s.StartsWith('('))
        {
            int close = s.IndexOf(')');
            if (close > 0 && close < 32 && close + 1 < s.Length)
                s = s[(close + 1)..].TrimStart();
        }
        return s;
    }

    private static bool IsInformativeLine(string clean)
    {
        if (clean.Length < 6) return false;
        if (clean.StartsWith("at ", StringComparison.Ordinal) || clean.StartsWith("\tat ", StringComparison.Ordinal)) return false;
        if (clean.StartsWith("- ", StringComparison.Ordinal) || clean.StartsWith("|--", StringComparison.Ordinal)) return false;
        if (clean.Contains("Reference map", StringComparison.OrdinalIgnoreCase)) return false;
        if (clean.Contains("[warning][cds]", StringComparison.OrdinalIgnoreCase)) return false;
        return true;
    }

    public static (string? Stage, string? Detail) DescribeStartupStage(string cleanLine)
    {
        if (cleanLine.Contains("Loading Minecraft ", StringComparison.OrdinalIgnoreCase))
            return ("Loading Fabric Loader & game provider...", FirstSentence(cleanLine));
        if (cleanLine.Contains("Fabric is preparing JARs", StringComparison.OrdinalIgnoreCase))
            return ("Preparing & extracting nested mod JARs...", FirstSentence(cleanLine));
        if (cleanLine.StartsWith("Loading ", StringComparison.OrdinalIgnoreCase) && cleanLine.Contains(" mods", StringComparison.OrdinalIgnoreCase))
        {
            string head = FirstSentence(cleanLine).TrimEnd(':');
            return (head.EndsWith("...") ? head : head + "...", "Resolving mod entrypoints and dependencies...");
        }
        if (cleanLine.Contains("Loading tweak class", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("FMLCorePlugin", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("Forge Mod Loader", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("ModLauncher running", StringComparison.OrdinalIgnoreCase))
            return ("Initializing Forge Mod Loader...", FirstSentence(cleanLine));
        if (cleanLine.Contains("SpongePowered MIXIN", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("MixinExtras", StringComparison.OrdinalIgnoreCase))
            return ("Applying Mixin bytecode transformations...", FirstSentence(cleanLine));
        if (cleanLine.Contains("Searching for graphics cards", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("Found graphics adapter", StringComparison.OrdinalIgnoreCase))
            return ("Detecting graphics adapter...", FirstSentence(cleanLine));
        if (cleanLine.Contains("Datafixer", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("Vanilla bootstrap", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("bootstrapped", StringComparison.OrdinalIgnoreCase))
            return ("Bootstrapping registries & DataFixer...", FirstSentence(cleanLine));
        if (cleanLine.Contains("Setting user:", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("Environment: authHost", StringComparison.OrdinalIgnoreCase))
            return ("Authenticating player session...", FirstSentence(cleanLine));
        if (cleanLine.Contains("TheLadsCore", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("The Lads Client", StringComparison.OrdinalIgnoreCase))
            return ("Initializing The Lads Core...", FirstSentence(cleanLine));
        if (cleanLine.Contains("Vulkan", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("LWJGL", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("Backend library:", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("OpenGL", StringComparison.OrdinalIgnoreCase))
            return ("Initializing graphics renderer & window...", FirstSentence(cleanLine));
        if (cleanLine.Contains("Sound engine", StringComparison.OrdinalIgnoreCase)
            || cleanLine.Contains("OpenAL", StringComparison.OrdinalIgnoreCase))
            return ("Starting audio engine...", FirstSentence(cleanLine));
        if (cleanLine.Contains("Reloading ResourceManager", StringComparison.OrdinalIgnoreCase)
            || cleanLine.StartsWith("Created: ", StringComparison.OrdinalIgnoreCase))
            return ("Loading resource packs & texture atlases...", FirstSentence(cleanLine));
        return (null, null);
    }

    private static string FirstSentence(string text)
    {
        int nl = text.IndexOfAny(new[] { '\r', '\n' });
        string line = nl >= 0 ? text[..nl].Trim() : text.Trim();
        return line.Length > 84 ? line[..81] + "..." : line;
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
