using System;
using System.Linq;
using System.Threading.Tasks;
using Avalonia.Controls;
using TheLadsLauncher.Services;

namespace TheLadsLauncher;

public partial class MainWindow : Window
{
    private void InitializeDeepLinking()
    {
        DeepLinkService.StartIpcListener(action =>
        {
            Avalonia.Threading.Dispatcher.UIThread.Post(async () =>
            {
                await HandleDeepLinkAsync(action);
            });
        });
    }

    public async Task HandleDeepLinkAsync(DeepLinkAction? action)
    {
        if (action == null || action.Type == DeepLinkActionType.None)
            return;

        if (action.Type == DeepLinkActionType.Unknown)
        {
            Log($"[DeepLink] Unknown deep link action '{action.Target}' ignored: {action.Extra}");
            return;
        }

        if (action.Type == DeepLinkActionType.JoinServer)
        {
            if (string.IsNullOrWhiteSpace(action.Target) ||
                !DeepLinkService.IsValidHost(action.Target) ||
                !DeepLinkService.IsValidPort(action.Port))
            {
                Log($"[DeepLink] Invalid server host or port '{action.Target}:{action.Port}' ignored.");
                return;
            }

            var dialog = CreateLadsDialog(
                "Join Server Confirmation",
                $"A deep link requested to join server:\n\n{action.Target}:{action.Port}\n\nWould you like to launch and connect now?",
                new[] { ("Join Server", false, true) },
                "Cancel");

            ShowForDialog(); // the launcher sits in the tray while the game runs; a hidden window cannot own the dialog
            int? choice = await dialog.ShowDialog<int?>(this);
            if (choice == 0)
            {
                Log($"[DeepLink] User confirmed connection to {action.Target}:{action.Port}. Launching...");
                await StartLaunchAsync(false, new GraphicsRenderer.Relaunch("DeepLink", null, $"{action.Target}:{action.Port}"));
            }
            else
            {
                Log($"[DeepLink] User cancelled connection to {action.Target}:{action.Port}.");
            }
        }
        else if (action.Type == DeepLinkActionType.LaunchProfile)
        {
            Log($"[DeepLink] LaunchProfile requested: {action.Target}");
            var profile = _profileService.GetProfiles().FirstOrDefault(p =>
                string.Equals(p.Name, action.Target, StringComparison.OrdinalIgnoreCase) ||
                string.Equals(p.Id, action.Target, StringComparison.OrdinalIgnoreCase));
            if (profile != null)
            {
                _profileService.SetActiveProfile(profile.Id);
                ApplyProfile(profile);
                PopulateLaunchProfileSelector();
            }
        }
    }
}
