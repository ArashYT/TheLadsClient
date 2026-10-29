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
        DeepLinkService.Log = Log;
        DeepLinkService.StartIpcListener(action =>
        {
            Avalonia.Threading.Dispatcher.UIThread.Post(async () =>
            {
                await HandleDeepLinkAsync(action);
            });
        });
    }

    /// <summary>Any web page can send a link: it is logged when it fails, never left to crash the launcher (the pipe runs it async void).</summary>
    public async Task HandleDeepLinkAsync(DeepLinkAction? action)
    {
        try { await ApplyDeepLinkAsync(action); }
        catch (Exception ex) { Log($"[DeepLink] {action?.Type} link failed: {ex.Message}"); }
    }

    private async Task ApplyDeepLinkAsync(DeepLinkAction? action)
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
            // The version picker's checks, then the player's yes: a link alone never switches (and saves) the active profile.
            string? refusal = profile == null ? $"No profile named '{action.Target}'."
                : _launching ? "A game is being launched. Switch profiles when it has started."
                : DeepLinkService.ProfileSwitchRefusal(profile);
            if (refusal != null)
            {
                Log($"[DeepLink] Profile switch refused: {refusal}");
                StatusText.Text = refusal;
                return;
            }
            int choice = await ShowLadsChoiceAsync("Switch Profile Confirmation",
                $"A link asks to make this your active profile:\n\n{profile!.Name} (Minecraft {profile.MinecraftVersion})\n\nSwitch now?",
                new[] { ("Switch Profile", false, true) });
            if (choice != 0 || _launching)
            {
                Log($"[DeepLink] Profile switch to {profile.Name} not done ({(choice != 0 ? "cancelled" : "a launch started")}).");
                return;
            }
            _profileService.SetActiveProfile(profile.Id);
            ApplyProfile(profile);
            PopulateLaunchProfileSelector();
            Log($"[DeepLink] Active profile switched to {profile.Name}.");
        }
        else if (action.Type == DeepLinkActionType.InstallPack)
        {
            // Nothing is downloaded or installed from a link: a pack runs code, so the player picks it in Modpacks.
            Log("[DeepLink] Install link ignored: installing modpacks from links is not supported yet.");
            StatusText.Text = "Installing modpacks from links is not supported yet. Open Modpacks to install one.";
        }
    }
}
