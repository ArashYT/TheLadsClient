using System;
using System.Linq;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
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

    /// <summary>The Play tab (1.13.24): the Network button is red like Play, the three shortcut buttons (Version profiles, Mods, Screenshots)
    /// and the What's new card are gone, and the profile, account and Play row sits right under the banner where the card was. Release notes
    /// stay in the sidebar and the Version profiles, Mods and Screenshots pages in the navigation.</summary>
    private void ApplyPlayTabLayout()
    {
        NetworkPlayButton.Classes.Remove("action");
        NetworkPlayButton.Classes.Add("launch");
        var launchRow = PlayStandardPanel.Children.First(c => Grid.GetRow(c) == 2);
        foreach (var gone in PlayStandardPanel.Children.Where(c => Grid.GetRow(c) is 1 or 3).ToList()) PlayStandardPanel.Children.Remove(gone);
        Grid.SetRow(launchRow, 1);
        launchRow.Margin = new Thickness(0, 12, 0, 0);
        PlayStandardPanel.RowDefinitions.RemoveRange(2, 2);
    }
}
