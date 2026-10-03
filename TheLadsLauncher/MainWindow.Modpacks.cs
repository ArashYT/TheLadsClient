using System;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Avalonia.Interactivity;
using CmlLib.Core.Auth;
using TheLadsLauncher.Services;

namespace TheLadsLauncher;

// The Modpacks tab's needs from the window: the selected account's session, settings, the log and a confirm dialog.
public partial class MainWindow
{
    internal LauncherSettings ModpackSettings => settings;
    internal void ModpackLog(string message) => Log("[Modpacks] " + message);
    internal Task<bool> ConfirmModpackAsync(string title, string message, string confirm) =>
        ShowLadsDialogAsync(title, message, confirm, "Cancel", danger: true);

    private async void NavModpacks_Click(object? sender, RoutedEventArgs e) { NavigateTo("Modpacks"); await ModpacksPage.LoadAsync(); }

    /// <summary>The account Play would use, signed in as LaunchGame does: silently, then Microsoft's sign-in window when asked for.</summary>
    internal async Task<MSession> ResolveModpackSessionAsync(CancellationToken token)
    {
        if (string.IsNullOrWhiteSpace(_selectedAccount)) throw new InvalidOperationException("Add or select an account on the Accounts page first.");
        string user = ResolveLaunchAccountName();
        if (GetAccountSummaries().Any(a => a.type == "offline" && string.Equals(a.username, user, StringComparison.OrdinalIgnoreCase)))
            return AccountIdentity.CreateOfflineSession(user);
        if (!Guid.TryParse(settings.MicrosoftClientId, out var clientId) || clientId == Guid.Empty)
            throw new InvalidOperationException("Configure your approved Microsoft application ID in Settings before launching a Microsoft account.");
        var account = loginHandler.AccountManager.GetAccounts()
            .FirstOrDefault(a => (a as CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount)?.Profile?.Username == user)
            ?? throw new InvalidOperationException("The selected account is no longer saved. Add it again before launching.");
        try
        {
            try { return await loginHandler.AuthenticateSilently(account, token); }
            catch (Exception silent) when (MicrosoftAccountService.NeedsInteractiveLogin(silent) && !token.IsCancellationRequested && _authCts == null)
            {
                _authCts = CancellationTokenSource.CreateLinkedTokenSource(token);
                _authCts.CancelAfter(TimeSpan.FromMinutes(16));
                try { return await loginHandler.AuthenticateInteractively(account, cancellationToken: _authCts.Token); }
                finally
                {
                    _msalProvider?.CompleteDialog();
                    _authCts?.Dispose();
                    _authCts = null;
                }
            }
        }
        catch (Exception error) when (!token.IsCancellationRequested && error is not InvalidOperationException)
        {
            throw new InvalidOperationException(MicrosoftAccountService.DescribeError(error), error);
        }
    }

    /// <summary>--preview-modpacks &lt;outputDir&gt; (sandbox only; Program.Main refuses it otherwise): the tab's screens, then exit.</summary>
    private async Task RunModpacksPreviewAsync(string output)
    {
        Directory.CreateDirectory(output);
        NavigateTo("Modpacks");
        await ModpacksPage.LoadAsync();
        await Task.Delay(1500);
        SaveWindowScreenshot(Path.Combine(output, "modpacks-grid.png"));
        ModpacksPage.ShowCreate();
        await Task.Delay(500);
        SaveWindowScreenshot(Path.Combine(output, "modpacks-create.png"));
        ModpacksPage.HideCreate();
        ModpacksPage.ShowBrowse();
        for (int i = 0; i < 40 && !ModpacksPage.BrowseLoaded; i++) await Task.Delay(500);
        await Task.Delay(2500); // icons
        SaveWindowScreenshot(Path.Combine(output, "modpacks-browse.png"));
        if (Modpacks.List(_pathService.BaseDirectory).FirstOrDefault() is { } first)
        {
            ModpacksPage.OpenManage(first);
            await Task.Delay(4000); // Modrinth's version list
            SaveWindowScreenshot(Path.Combine(output, "modpacks-manage.png"));
        }
        // LADS_PREVIEW_MODPACK_PLAY=<instance id>: Play pressed on that instance (the selected account, a real game; QA holds the
        // machine-wide game lock), captured while it runs, then closed as a player closes it.
        if (Environment.GetEnvironmentVariable("LADS_PREVIEW_MODPACK_PLAY") is { Length: > 0 } playId
            && Modpacks.List(_pathService.BaseDirectory).FirstOrDefault(i => i.Id == playId) is { } played)
        {
            ModpacksPage.OpenManage(played);
            ModpacksPage.PressPlay();
            for (int i = 0; i < 600 && ModpacksPage.RunningGame(played.Id) == null && !ModpacksPage.ManageStatusText.Contains("failed"); i++) await Task.Delay(500);
            Console.WriteLine("Play: " + ModpacksPage.ManageStatusText);
            SaveWindowScreenshot(Path.Combine(output, "modpacks-play-started.png"));
            if (ModpacksPage.RunningGame(played.Id) is { } game)
            {
                await Task.Delay(45000);
                SaveWindowScreenshot(Path.Combine(output, "modpacks-running.png"));
                game.CloseMainWindow();
                if (!game.WaitForExit(45000)) game.Kill(entireProcessTree: true);
                await Task.Delay(1500);
                Console.WriteLine("Closed: " + ModpacksPage.ManageStatusText);
                SaveWindowScreenshot(Path.Combine(output, "modpacks-after-exit.png"));
            }
        }
        Close();
    }
}
