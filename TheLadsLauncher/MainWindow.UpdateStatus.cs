using System;
using System.Diagnostics;
using System.Text.RegularExpressions;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Threading;

namespace TheLadsLauncher;

public partial class MainWindow
{
    private Stopwatch? _updateDownloadClock;
    private int _updateDownloadStartPercent;

    private const string InstallingSentence = "Swapping in the new files; the launcher reopens by itself";

    /// <summary>The line under the startup bar says what the updater is doing right now. It follows the full updater message
    /// (UpdateBannerText gets every one, see ReportUpdate), so the installing snapshot the bridge shows carries it too.</summary>
    private void StartupDetailText_Attached(object? sender, VisualTreeAttachmentEventArgs e)
    {
        if (_hotbarBuilt) return;   // moving the control re-attaches it
        // Moving controls inside their own attach notification is unsafe; do it right after, before the first frame.
        Dispatcher.UIThread.Post(() =>
        {
            BuildHotbarStartupScreen();
            if (Environment.GetEnvironmentVariable("LADS_QA_SPLASH_SHOT") is { Length: > 0 } shots) _ = RunSplashShotsAsync(shots);
        }, DispatcherPriority.Send);
        UpdateBannerText.PropertyChanged += (_, args) =>
        {
            if (args.Property != TextBlock.TextProperty) return;
            (StartupDetailText.Text, StartupEtaText.Text) = UpdateDetail(UpdateBannerText.Text ?? "");
        };
        // The installing snapshot (RenderInstallingScreen) only sets the status line; give it its sentence and no estimate.
        StartupLoadingSpinner.PropertyChanged += (_, args) =>
        {
            if (args.Property == TextBlock.TextProperty && (StartupLoadingSpinner.Text ?? "").StartsWith("Installing update v", StringComparison.Ordinal))
                (StartupDetailText.Text, StartupEtaText.Text) = (InstallingSentence, "");
        };
    }

    /// <summary>The detail line and the time left, split so each has its own place on the screen.</summary>
    private (string Detail, string Eta) UpdateDetail(string message)
    {
        var download = Regex.Match(message, @"^Downloading v\S+: (\d+)%$");
        if (!download.Success)
        {
            _updateDownloadClock = null;
            if (message.StartsWith("Checking for launcher updates", StringComparison.Ordinal)) return ("Asking the update server for a newer version", "");
            if (message.StartsWith("Delta update did not verify", StringComparison.Ordinal)) return ("Fetching the full package instead", "");
            if (message.StartsWith("Installing", StringComparison.Ordinal)) return (InstallingSentence, "");
            return ("", "");
        }
        int percent = int.Parse(download.Groups[1].Value);
        if (percent >= 100) return ("Download done · checking it is signed by The Lads", "");
        if (_updateDownloadClock == null) { _updateDownloadClock = Stopwatch.StartNew(); _updateDownloadStartPercent = percent; }
        double seconds = _updateDownloadClock.Elapsed.TotalSeconds;
        int done = percent - _updateDownloadStartPercent;
        const string downloading = "Downloading the new launcher files";
        if (done < 2 || seconds < 1) return (downloading, "");
        double left = (100 - percent) * seconds / done;
        return (downloading, left < 60 ? $"about {Math.Max(1, (int)Math.Ceiling(left))} s left" : $"about {(int)Math.Ceiling(left / 60)} min left");
    }
}
