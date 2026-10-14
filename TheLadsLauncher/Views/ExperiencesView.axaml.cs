using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Threading;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

public partial class ExperiencesView : UserControl
{
    private sealed record RunningInstance(int Number, string Username, Process Process);

    private static readonly HttpClient Http = new();
    private static string DataDirectory => PathService.Instance.BaseDirectory;
    private static string ZeroSmpInstanceId => ExperiencesService.ActiveZeroSmpInstanceId();
    private MainWindow? Host => TopLevel.GetTopLevel(this) as MainWindow;

    public AccountSummary? CurrentAccountOverride { get; set; }
    public AccountSummary? CurrentAccount => CurrentAccountOverride ?? Host?.GetCurrentAccount();

    public static ExperiencesService.ExperienceButtonState GetButtonState(bool installed, bool isRunning = false, bool isBusy = false, bool allowMultiInstance = false)
        => ExperiencesService.GetZeroSmpButtonState(installed, isRunning, isBusy, allowMultiInstance);

    private readonly List<RunningInstance> _runningInstances = new();
    private CancellationTokenSource? _actionCts;
    private bool _syncingAutoJoin;
    private bool _syncingAccounts;
    private bool _raceRetry; // the next ZeroSmpPlay_Click is the launcher's own one retry after a known mod-loading race

    private bool HasRunningProcess()
    {
        _runningInstances.RemoveAll(i =>
        {
            try { return i.Process.HasExited; }
            catch { return true; }
        });
        return _runningInstances.Count > 0;
    }

    private int NextInstanceNumber()
    {
        int n = 1;
        while (_runningInstances.Any(i => i.Number == n)) n++;
        return n;
    }

    public ExperiencesView()
    {
        InitializeComponent();
        ZeroSmpServerText.Text = ExperiencesService.ZeroSmp.ServerAddress; // the one place the address is written down
    }

    public async Task LoadAsync()
    {
        await Dispatcher.UIThread.InvokeAsync(() => RefreshState());
    }

    public void RefreshState() => RefreshState(null);

    public void RefreshState(AccountSummary? accountOverride)
    {
        if (!Dispatcher.UIThread.CheckAccess())
        {
            Dispatcher.UIThread.Post(() => RefreshState(accountOverride));
            return;
        }

        if (accountOverride != null) CurrentAccountOverride = accountOverride;

        var instanceId = ZeroSmpInstanceId;
        if (string.Equals(instanceId, ExperiencesService.ZeroSmpDevId, StringComparison.OrdinalIgnoreCase))
        {
            ExperiencesService.EnsureDevZeroSmpSeeded(DataDirectory);
        }

        bool installed = ExperiencesService.IsInstalled(DataDirectory, instanceId);
        bool running = HasRunningProcess();
        bool busy = _actionCts != null;
        bool allowMultiInstance = Host?.ModpackSettings.AllowMultiInstance == true;

        var btnState = ExperiencesService.GetZeroSmpButtonState(installed, running, busy, allowMultiInstance);
        ZeroSmpPlayBtn.Content = btnState.Text;
        ZeroSmpPlayBtn.IsEnabled = btnState.IsEnabled;

        if (running)
        {
            ZeroSmpStatusBadge.Background = Brush.Parse("#1D2A1F");
            ZeroSmpStatusBadge.BorderBrush = Brush.Parse("#386B42");
            ZeroSmpStatusBadgeText.Text = _runningInstances.Count > 1 ? $"Running ({_runningInstances.Count})" : "Running";
            ZeroSmpStatusBadgeText.Foreground = Brush.Parse("#85E89D");
        }
        else if (busy)
        {
            // operation status remains in ZeroSmpStatusText
        }
        else if (installed)
        {
            ZeroSmpStatusBadge.Background = Brush.Parse("#1D2A1F");
            ZeroSmpStatusBadge.BorderBrush = Brush.Parse("#386B42");
            ZeroSmpStatusBadgeText.Text = "Installed";
            ZeroSmpStatusBadgeText.Foreground = Brush.Parse("#85E89D");
            ZeroSmpStatusText.Text = "Ready to play";

            var instance = ExperiencesService.GetInstance(DataDirectory, instanceId);
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
            ZeroSmpStatusText.Text = "Not installed";
        }

        ZeroSmpAutoJoinCheck.IsVisible = true;

        ZeroSmpFolderBtn.IsEnabled = installed && !busy;
        ZeroSmpReinstallBtn.IsEnabled = installed && !busy && !running;

        if (Host is { } host)
        {
            _syncingAutoJoin = true;
            ZeroSmpAutoJoinCheck.IsChecked = host.ModpackSettings.ZeroSmpAutoJoin;
            _syncingAutoJoin = false;

            SyncAccountSelector(host);
        }

        UpdateRunningInstancesUi();
    }

    private void SyncAccountSelector(MainWindow host)
    {
        if (ZeroSmpAccountSelector == null) return;
        _syncingAccounts = true;
        try
        {
            var choices = host.GetLaunchAccountChoices();
            var existing = ZeroSmpAccountSelector.Items.OfType<AccountChoice>().ToList();
            if (!existing.SequenceEqual(choices))
            {
                ZeroSmpAccountSelector.Items.Clear();
                foreach (var choice in choices)
                {
                    ZeroSmpAccountSelector.Items.Add(choice);
                }
            }
            string selectedName = host.SelectedLaunchAccountName;
            ZeroSmpAccountSelector.SelectedItem = choices.FirstOrDefault(c => string.Equals(c.Name, selectedName, StringComparison.OrdinalIgnoreCase));
        }
        finally
        {
            _syncingAccounts = false;
        }
    }

    private void ZeroSmpAccountSelector_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (_syncingAccounts || Host is not { } host) return;
        if (ZeroSmpAccountSelector?.SelectedItem is AccountChoice { Name: var name })
        {
            host.SelectLaunchAccount(name);
        }
    }

    private void UpdateRunningInstancesUi()
    {
        if (ZeroSmpInstancesDropdownBtn == null || ZeroSmpInstancesDropdownBtn.Flyout is not MenuFlyout menu || ZeroSmpInstancesBar == null || ZeroSmpInstancesQuickButtons == null)
            return;

        var ordered = _runningInstances.OrderBy(i => i.Number).ToList();
        bool anyOpen = ordered.Count > 0;

        ZeroSmpInstancesDropdownBtn.IsVisible = anyOpen;
        ZeroSmpInstancesBar.IsVisible = anyOpen;
        menu.Items.Clear();
        ZeroSmpInstancesQuickButtons.Children.Clear();

        if (!anyOpen) return;

        ZeroSmpInstancesDropdownText.Text = $"Instances Open ({ordered.Count})";

        var headerLabel = new TextBlock
        {
            Text = $"Instances Open ({ordered.Count}):",
            FontSize = 12,
            FontWeight = FontWeight.SemiBold,
            Foreground = Brush.Parse("#85E89D"),
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 2, 10, 2)
        };
        ZeroSmpInstancesQuickButtons.Children.Add(headerLabel);

        foreach (var inst in ordered)
        {
            var captured = inst;
            var menuItem = new MenuItem
            {
                Header = $"Close Instance {captured.Number} ({captured.Username})"
            };
            menuItem.Click += (_, _) => CloseInstance(captured);
            menu.Items.Add(menuItem);

            var quickBtn = new Button
            {
                Classes = { "action" },
                Content = $"✕ Close Instance {captured.Number} ({captured.Username})",
                Height = 28,
                FontSize = 11,
                Padding = new Thickness(10, 0),
                Margin = new Thickness(0, 2, 8, 2)
            };
            quickBtn.Click += (_, _) => CloseInstance(captured);
            ZeroSmpInstancesQuickButtons.Children.Add(quickBtn);
        }

        if (ordered.Count > 1)
        {
            menu.Items.Add(new Separator());
        }

        var closeAllItem = new MenuItem
        {
            Header = ordered.Count > 1 ? $"Close All ({ordered.Count} Instances)" : "Close All"
        };
        closeAllItem.Click += (_, _) => CloseAllInstances();
        menu.Items.Add(closeAllItem);

        var closeAllBtn = new Button
        {
            Classes = { "action" },
            Content = ordered.Count > 1 ? $"✕ Close All ({ordered.Count})" : "✕ Close All",
            Height = 28,
            FontSize = 11,
            Foreground = Brush.Parse("#FF8A8A"),
            Padding = new Thickness(10, 0),
            Margin = new Thickness(0, 2, 0, 2)
        };
        closeAllBtn.Click += (_, _) => CloseAllInstances();
        ZeroSmpInstancesQuickButtons.Children.Add(closeAllBtn);
    }

    private void CloseInstance(RunningInstance inst)
    {
        _runningInstances.Remove(inst);
        GameSession.Cancel(inst.Process);
        Host?.ModpackLog($"Closed Zero SMP Instance {inst.Number} ({inst.Username}, PID {inst.Process.Id}).");
        ZeroSmpStatusText.Text = $"Closed Instance {inst.Number} ({inst.Username}).";
        if (!HasRunningProcess() && _actionCts == null) ZeroSmpProgressBar.IsVisible = false;
        RefreshState();
    }

    private void CloseAllInstances()
    {
        var toClose = _runningInstances.ToList();
        _runningInstances.Clear();
        foreach (var inst in toClose)
        {
            GameSession.Cancel(inst.Process);
            Host?.ModpackLog($"Closed Zero SMP Instance {inst.Number} ({inst.Username}, PID {inst.Process.Id}).");
        }
        ZeroSmpStatusText.Text = toClose.Count > 1 ? $"Closed all {toClose.Count} Zero SMP instances." : "Closed Zero SMP instance.";
        if (_actionCts == null) ZeroSmpProgressBar.IsVisible = false;
        RefreshState();
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
        if (_actionCts != null) return;
        if (HasRunningProcess() && !host.ModpackSettings.AllowMultiInstance) return;
        bool retry = _raceRetry;
        _raceRetry = false;

        var instanceId = ZeroSmpInstanceId;
        if (string.Equals(instanceId, ExperiencesService.ZeroSmpDevId, StringComparison.OrdinalIgnoreCase))
        {
            ExperiencesService.EnsureDevZeroSmpSeeded(DataDirectory);
        }

        bool installed = ExperiencesService.IsInstalled(DataDirectory, instanceId);
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
                instance = await ExperiencesService.InstallZeroSmpAsync(DataDirectory, Report, token, Http, instanceId);
            }
            else
            {
                instance = ExperiencesService.GetInstance(DataDirectory, instanceId)
                           ?? await ExperiencesService.InstallZeroSmpAsync(DataDirectory, Report, token, Http, instanceId);
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

            ExperiencesService.EnsureZeroSmpServer(instance.GameDirectory);

            Report("Signing in...");
            var session = await host.ResolveModpackSessionAsync(token);

            Report("Preparing Minecraft 1.20.1 & Forge 47.4.10...");
            string? joinServer = host.ModpackSettings.ZeroSmpAutoJoin ? ExperiencesService.ZeroSmp.ServerAddress : null;
            var launchedUtc = DateTime.UtcNow;
            var process = await ModpackLauncher.LaunchAsync(DataDirectory, instance, session, host.ModpackSettings, JavaService.Instance, Http, Report, token, joinServer);

            int instNumber = NextInstanceNumber();
            var runningInst = new RunningInstance(instNumber, session.Username ?? "Player", process);
            _runningInstances.Add(runningInst);
            process.EnableRaisingEvents = true;
            process.Exited += (_, _) => Dispatcher.UIThread.Post(() =>
            {
                _runningInstances.Remove(runningInst);
                bool wasCancelled = GameSession.WasCancelled(process);
                int code = process.ExitCode;
                host.ModpackLog($"Zero SMP Instance {instNumber} ({runningInst.Username}) closed (exit code {code}).");
                if (!wasCancelled)
                {
                    ZeroSmpStatusText.Text = code == 0
                        ? $"Instance {instNumber} ({runningInst.Username}) closed."
                        : $"Instance {instNumber} ({runningInst.Username}) closed with exit code {code}. See logs in Open Folder.";
                }
                if (!HasRunningProcess() && _actionCts == null) ZeroSmpProgressBar.IsVisible = false;
                RefreshState();
                host.ApplyNextAccountRequest(instance.GameDirectory); // in-game account switcher
                host.ApplyNextVersionRequest(instance.GameDirectory); // closed from the in-game Switch Versions screen
                // Forge's parallel mod loading lost a known race (ModLoadingRace): start once more rather than leave the player at a crash.
                if (!wasCancelled && !retry && ModLoadingRace.FindSince(instance.GameDirectory, launchedUtc) is { } race)
                {
                    host.ModpackLog($"Zero SMP hit a known mod-loading race: {race}. Starting it once more.");
                    _raceRetry = true;
                    ZeroSmpPlay_Click(null, new RoutedEventArgs());
                }
            });

            Report($"Zero SMP Instance {instNumber} started as {session.Username}!");
            host.ModpackLog($"Started Zero SMP Instance {instNumber} (Forge 47.4.10, Minecraft 1.20.1) PID {process.Id}."
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
            if (!HasRunningProcess()) ZeroSmpProgressBar.IsVisible = false;
            RefreshState();
        }
    }

    private void ZeroSmpFolder_Click(object? sender, RoutedEventArgs e)
    {
        var instanceId = ZeroSmpInstanceId;
        var gameDir = Path.Combine(ExperiencesService.InstanceDirectory(DataDirectory, instanceId), "minecraft");
        if (!Directory.Exists(gameDir)) gameDir = ExperiencesService.InstanceDirectory(DataDirectory, instanceId);
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
            await ExperiencesService.InstallZeroSmpAsync(DataDirectory, Report, _actionCts.Token, Http, ZeroSmpInstanceId);
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
