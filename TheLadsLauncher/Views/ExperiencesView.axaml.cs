using System;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Media;
using Avalonia.Threading;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

public partial class ExperiencesView : UserControl
{
    private static readonly HttpClient Http = new();
    private static string DataDirectory => PathService.Instance.BaseDirectory;
    private MainWindow? Host => TopLevel.GetTopLevel(this) as MainWindow;

    private Process? _runningProcess;
    private CancellationTokenSource? _actionCts;
    private bool _syncingAutoJoin;

    public ExperiencesView()
    {
        InitializeComponent();
        ZeroSmpServerText.Text = ExperiencesService.ZeroSmp.ServerAddress; // the one place the address is written down
    }

    public async Task LoadAsync()
    {
        await Dispatcher.UIThread.InvokeAsync(RefreshState);
    }

    private void RefreshState()
    {
        bool installed = ExperiencesService.IsInstalled(DataDirectory, ExperiencesService.ZeroSmp.Id);
        bool running = _runningProcess is { HasExited: false };
        bool busy = _actionCts != null;

        if (running)
        {
            ZeroSmpStatusBadge.Background = Brush.Parse("#1D2A1F");
            ZeroSmpStatusBadge.BorderBrush = Brush.Parse("#386B42");
            ZeroSmpStatusBadgeText.Text = "Running";
            ZeroSmpStatusBadgeText.Foreground = Brush.Parse("#85E89D");

            ZeroSmpPlayBtn.Content = "Game Running";
            ZeroSmpPlayBtn.IsEnabled = false;
        }
        else if (busy)
        {
            ZeroSmpPlayBtn.IsEnabled = false;
        }
        else if (installed)
        {
            ZeroSmpStatusBadge.Background = Brush.Parse("#1D2A1F");
            ZeroSmpStatusBadge.BorderBrush = Brush.Parse("#386B42");
            ZeroSmpStatusBadgeText.Text = "Installed";
            ZeroSmpStatusBadgeText.Foreground = Brush.Parse("#85E89D");

            ZeroSmpPlayBtn.Content = "Play Zero SMP";
            ZeroSmpPlayBtn.IsEnabled = true;

            var instance = ExperiencesService.GetInstance(DataDirectory, ExperiencesService.ZeroSmp.Id);
            if (instance != null && instance.MaxRamMb > 0)
            {
                ZeroSmpRamBox.Value = instance.MaxRamMb;
            }
        }
        else
        {
            ZeroSmpStatusBadge.Background = Brush.Parse("#2A211D");
            ZeroSmpStatusBadge.BorderBrush = Brush.Parse("#6B3838");
            ZeroSmpStatusBadgeText.Text = "Not Installed";
            ZeroSmpStatusBadgeText.Foreground = Brush.Parse("#E88585");

            ZeroSmpPlayBtn.Content = "Install & Play Zero SMP";
            ZeroSmpPlayBtn.IsEnabled = true;
        }

        ZeroSmpFolderBtn.IsEnabled = installed && !busy;
        ZeroSmpReinstallBtn.IsEnabled = !busy && !running;

        if (Host is { } host)
        {
            _syncingAutoJoin = true;
            ZeroSmpAutoJoinCheck.IsChecked = host.ModpackSettings.ZeroSmpAutoJoin;
            _syncingAutoJoin = false;
        }
    }

    private void ZeroSmpAutoJoin_Changed(object? sender, RoutedEventArgs e)
    {
        if (_syncingAutoJoin || Host is not { } host) return;
        host.ModpackSettings.ZeroSmpAutoJoin = ZeroSmpAutoJoinCheck.IsChecked == true;
        host.ModpackSettings.Save();
    }

    private async void ZeroSmpPlay_Click(object? sender, RoutedEventArgs e)
    {
        if (Host is not { } host) return;
        if (_runningProcess is { HasExited: false }) return;

        bool installed = ExperiencesService.IsInstalled(DataDirectory, ExperiencesService.ZeroSmp.Id);
        _actionCts = new CancellationTokenSource();
        var token = _actionCts.Token;

        ZeroSmpProgressBar.IsVisible = true;
        ZeroSmpProgressBar.Value = 0;
        RefreshState();

        void Report(string message) => Dispatcher.UIThread.Post(() => ZeroSmpStatusText.Text = message);

        try
        {
            ModpackInstance? instance;
            if (!installed)
            {
                Report("Installing Zero SMP isolated instance...");
                instance = await ExperiencesService.InstallZeroSmpAsync(DataDirectory, Report, token, Http);
            }
            else
            {
                instance = ExperiencesService.GetInstance(DataDirectory, ExperiencesService.ZeroSmp.Id)
                           ?? await ExperiencesService.InstallZeroSmpAsync(DataDirectory, Report, token, Http);
            }

            // Save user RAM setting if changed
            if (ZeroSmpRamBox.Value.HasValue)
            {
                instance.MaxRamMb = (int)ZeroSmpRamBox.Value.Value;
                Modpacks.Save(instance);
            }

            // Pack patches (new mods, retired mods, key defaults, config) not applied yet, the launcher's bundled ones included; the Lads Core in the pack is brought to this
            // launcher's build by ModpackLauncher before the game starts.
            await ExperiencesService.ApplyPendingPatchesAsync(DataDirectory, instance.Id, Http, Report, token, ExperiencesService.BundledPatchesDirectory);

            Report("Signing in...");
            var session = await host.ResolveModpackSessionAsync(token);

            Report("Preparing Minecraft 1.20.1 & Forge 47.4.10...");
            string? joinServer = host.ModpackSettings.ZeroSmpAutoJoin ? ExperiencesService.ZeroSmp.ServerAddress : null;
            var process = await ModpackLauncher.LaunchAsync(DataDirectory, instance, session, host.ModpackSettings, JavaService.Instance, Http, Report, token, joinServer);

            _runningProcess = process;
            process.EnableRaisingEvents = true;
            process.Exited += (_, _) => Dispatcher.UIThread.Post(() =>
            {
                _runningProcess = null;
                int code = process.ExitCode;
                host.ModpackLog($"Zero SMP closed (exit code {code}).");
                ZeroSmpStatusText.Text = code == 0 ? "Game closed." : $"Minecraft closed with exit code {code}. See logs in Open Folder.";
                ZeroSmpProgressBar.IsVisible = false;
                RefreshState();
                host.ApplyNextAccountRequest(instance.GameDirectory); // in-game account switcher
                host.ApplyNextVersionRequest(instance.GameDirectory); // closed from the in-game Switch Versions screen
            });

            Report($"Zero SMP started as {session.Username}!");
            host.ModpackLog($"Started Zero SMP (Forge 47.4.10, Minecraft 1.20.1) PID {process.Id}."
                + (joinServer == null ? "" : $" Auto-join: --quickPlayMultiplayer {ModpackLauncher.QuickPlayServer(process)}."));
            RefreshState();
        }
        catch (OperationCanceledException)
        {
            Report("Action cancelled.");
        }
        catch (Exception error)
        {
            Report("Failed: " + error.Message);
            host.ModpackLog($"Zero SMP error: {error}");
        }
        finally
        {
            _actionCts?.Dispose();
            _actionCts = null;
            if (_runningProcess == null) ZeroSmpProgressBar.IsVisible = false;
            RefreshState();
        }
    }

    private void ZeroSmpFolder_Click(object? sender, RoutedEventArgs e)
    {
        var gameDir = Path.Combine(ExperiencesService.InstanceDirectory(DataDirectory, ExperiencesService.ZeroSmp.Id), "minecraft");
        if (!Directory.Exists(gameDir)) gameDir = ExperiencesService.InstanceDirectory(DataDirectory, ExperiencesService.ZeroSmp.Id);
        if (!Directory.Exists(gameDir)) Directory.CreateDirectory(gameDir);

        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = gameDir,
                UseShellExecute = true
            });
        }
        catch (Exception ex)
        {
            ZeroSmpStatusText.Text = "Could not open folder: " + ex.Message;
        }
    }

    private async void ZeroSmpReinstall_Click(object? sender, RoutedEventArgs e)
    {
        if (Host is not { } host) return;
        bool confirmed = await host.ConfirmModpackAsync(
            "Reinstall Zero SMP",
            "This will re-copy the modpack mods and default configurations. Your singleplayer worlds in saves/ will NOT be deleted.",
            "Reinstall");

        if (!confirmed) return;

        _actionCts = new CancellationTokenSource();
        ZeroSmpProgressBar.IsVisible = true;
        RefreshState();

        void Report(string message) => Dispatcher.UIThread.Post(() => ZeroSmpStatusText.Text = message);

        try
        {
            Report("Reinstalling Zero SMP...");
            await ExperiencesService.InstallZeroSmpAsync(DataDirectory, Report, _actionCts.Token, Http);
            Report("Zero SMP reinstalled successfully.");
        }
        catch (Exception ex)
        {
            Report("Reinstall failed: " + ex.Message);
        }
        finally
        {
            _actionCts?.Dispose();
            _actionCts = null;
            ZeroSmpProgressBar.IsVisible = false;
            RefreshState();
        }
    }
}
