using System;
using System.IO;
using System.Linq;
using Avalonia.Interactivity;
using Avalonia.Threading;
using TheLadsLauncher.Services;
using TheLadsLauncher.Views;

namespace TheLadsLauncher;

public partial class MainWindow
{
    private ImportTargets ClientImportTargets() => new(PathService.Instance, ProfileService.Instance.GetProfiles(), SharedContentService.ResolveDefaultRoot(),
        Path.Combine(PathService.Instance.BaseDirectory, "settings.json"));

    /// <summary>Called once at start: settings an in-game import left waiting, on every activation (the launcher coming back when the game
    /// closes); and the offer, once ever, on the first launch that finds another client.</summary>
    private void InitClientImport(string[] args)
    {
        Activated += (_, _) =>
        {
            if (!ClientImport.ApplyPendingLauncher(PathService.Instance.BaseDirectory, settings)) return;
            settings.Save();
            RefreshImportedSettings();
        };
        if (settings.ClientImportOfferShown || args.Any(a => a.StartsWith("--preview-", StringComparison.Ordinal))) return;
        Opened += (_, _) => Dispatcher.UIThread.Post(() =>
        {
            settings.ClientImportOfferShown = true;
            settings.Save();
            if (ClientImport.Detect(ImportEnvironment.Current).Count > 0) OpenClientImport();
        });
    }

    private void ClientImport_Click(object? sender, RoutedEventArgs e) => OpenClientImport();

    private void OpenClientImport() => new ClientImportWindow(ClientImportTargets(), settings, RefreshImportedSettings).ShowDialog(this);

    /// <summary>The controls showing what the import may change: Save reads them back into the settings.</summary>
    private void RefreshImportedSettings()
    {
        RamSlider.Value = settings.MaxRamMb / 1024;
        FullscreenOnLaunchCheckbox.IsChecked = settings.FullscreenOnLaunch;
        KeepLauncherOpenCheckbox.IsChecked = settings.KeepLauncherOpen;
        DiscordPresenceCheckbox.IsChecked = settings.DiscordRichPresence;
    }
}
