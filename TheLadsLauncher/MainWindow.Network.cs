using System;
using System.Threading.Tasks;
using Avalonia.Interactivity;
using Avalonia.Media;
using Avalonia.Threading;
using TheLadsLauncher.Services;

namespace TheLadsLauncher;

/// <summary>The Play page's "Play The Lads Network" card: live status (a real status ping), one click launches and joins.</summary>
public partial class MainWindow
{
    private void StartNetworkStatus()
    {
        var timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(30) };
        timer.Tick += async (_, _) =>
        {
            if (IsVisible && HomePage.IsVisible) await RefreshNetworkStatusAsync(); // nothing while in the tray or on another page
        };
        timer.Start();
        _ = RefreshNetworkStatusAsync();
    }

    private async Task RefreshNetworkStatusAsync()
    {
        var status = await ServerStatusService.SlpAsync(ServerListService.PinnedAddress);
        NetworkStatusDot.Fill = new SolidColorBrush(Color.Parse(status != null ? "#50C878" : "#6B6D76"));
        NetworkStatusText.Text = status == null ? "Offline"
            : "Online · " + status.Players + (status.Players == 1 ? " player" : " players");
    }

    private async void NetworkPlay_Click(object? sender, RoutedEventArgs e) =>
        await StartLaunchAsync(false, new GraphicsRenderer.Relaunch("Network", null, ServerListService.PinnedAddress));
}
