using System;
using System.Diagnostics;
using System.Text.RegularExpressions;
using Avalonia;
using Avalonia.Controls;

namespace TheLadsLauncher;

public partial class MainWindow
{
    private Stopwatch? _updateDownloadClock;
    private int _updateDownloadStartPercent;

    /// <summary>The line under the startup bar says what the updater is doing right now. It follows the full updater message
    /// (UpdateBannerText gets every one, see ReportUpdate), so the installing snapshot the bridge shows carries it too.</summary>
    private void StartupDetailText_Attached(object? sender, VisualTreeAttachmentEventArgs e)
    {
        UpdateBannerText.PropertyChanged += (_, args) =>
        {
            if (args.Property == TextBlock.TextProperty) StartupDetailText.Text = UpdateDetail(UpdateBannerText.Text ?? "");
        };
    }

    private string UpdateDetail(string message)
    {
        var download = Regex.Match(message, @"^Downloading v\S+: (\d+)%$");
        if (!download.Success)
        {
            _updateDownloadClock = null;
            if (message.StartsWith("Checking for launcher updates", StringComparison.Ordinal)) return "Asking the update server for a newer version";
            if (message.StartsWith("Delta update did not verify", StringComparison.Ordinal)) return "Fetching the full package instead";
            if (message.StartsWith("Installing", StringComparison.Ordinal)) return "Closing the launcher and swapping in the new files; it reopens by itself";
            return "";
        }
        int percent = int.Parse(download.Groups[1].Value);
        if (percent >= 100) return "Download done · checking it is signed by The Lads";
        if (_updateDownloadClock == null) { _updateDownloadClock = Stopwatch.StartNew(); _updateDownloadStartPercent = percent; }
        double seconds = _updateDownloadClock.Elapsed.TotalSeconds;
        int done = percent - _updateDownloadStartPercent;
        if (done < 2 || seconds < 1) return "Downloading the new launcher files";
        double left = (100 - percent) * seconds / done;
        return "Downloading the new launcher files · " + (left < 60 ? $"about {Math.Max(1, (int)Math.Ceiling(left))} s left" : $"about {(int)Math.Ceiling(left / 60)} min left");
    }
}
