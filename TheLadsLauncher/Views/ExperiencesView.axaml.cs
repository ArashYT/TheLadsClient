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
    private static string LadsSmpRebornInstanceId => ExperiencesService.ActiveLadsSmpRebornInstanceId();
    private static string ZeroSmpInstanceId => ExperiencesService.ActiveZeroSmpInstanceId();
    private MainWindow? Host => TopLevel.GetTopLevel(this) as MainWindow;

    public AccountSummary? CurrentAccountOverride { get; set; }
    public AccountSummary? CurrentAccount => CurrentAccountOverride ?? Host?.GetCurrentAccount();

    public static ExperiencesService.ExperienceButtonState GetButtonState(bool installed, bool isRunning = false, bool isBusy = false, bool allowMultiInstance = false)
        => ExperiencesService.GetZeroSmpButtonState(installed, isRunning, isBusy, allowMultiInstance);

    public static ExperiencesService.ExperienceButtonState GetLadsSmpRebornButtonState(bool installed, bool isRunning = false, bool isBusy = false, bool allowMultiInstance = false)
        => ExperiencesService.GetLadsSmpRebornButtonState(installed, isRunning, isBusy, allowMultiInstance);

    private readonly List<RunningInstance> _runningLadsSmpReborn = new();
    private readonly List<RunningInstance> _runningZeroSmp = new();
    private CancellationTokenSource? _ladsSmpRebornCts;
    private CancellationTokenSource? _zeroSmpCts;
    private bool _syncingAutoJoin;
    private bool _syncingAccounts;
    private bool _updatingRam;
    private string? _accessKey; // the account names last sent to the invite check
    private DateTime _accessCheckedUtc;
    private bool _raceRetry; // the next ZeroSmpPlay_Click is the launcher's own one retry after a known mod-loading race
    private GraphicsRenderer.Relaunch? _ladsSmpRebornRelaunch; // the next LadsSmpRebornPlay_Click restarts it for the shader toggle

    public static int BinaryMbToRamGb(int mb) =>
        Math.Clamp((int)Math.Round(mb / 1024.0), 1, 32);

    public static int RamGbToBinaryMb(decimal? value, int fallbackMb)
    {
        if (!value.HasValue) return fallbackMb;
        double v = (double)value.Value;
        int gb = v >= 1024.0
            ? Math.Clamp((int)Math.Round(v / 1024.0), 1, 32)
            : Math.Clamp((int)Math.Round(v), 1, 32);
        return gb * 1024;
    }

    private static void SetStatusText(TextBlock statusText, string? message)
    {
        string text = message ?? "";
        statusText.Text = text;
        statusText.IsVisible = !string.IsNullOrWhiteSpace(text);
    }

    private static bool HasRunningProcess(List<RunningInstance> list)
    {
        list.RemoveAll(i =>
        {
            try { return i.Process.HasExited; }
            catch { return true; }
        });
        return list.Count > 0;
    }

    private static int NextInstanceNumber(List<RunningInstance> list)
    {
        int n = 1;
        while (list.Any(i => i.Number == n)) n++;
        return n;
    }

    public ExperiencesView()
    {
        InitializeComponent();
        ZeroSmpServerText.Text = ExperiencesService.ZeroSmp.ServerAddress; // the one place the address is written down
        ToolTip.SetTip(LadsSmpRebornRamBox, $"{ExperiencesService.LadsSmpReborn.RecommendedRamMb} MB");
        ToolTip.SetTip(ZeroSmpRamBox, $"{ExperiencesService.ZeroSmp.RecommendedRamMb} MB");
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

        bool allowMultiInstance = Host?.ModpackSettings.AllowMultiInstance == true;

        // 1. Refresh The Lads SMP Reborn card
        RefreshInviteAccess();
        var ladsId = LadsSmpRebornInstanceId;
        if (string.Equals(ladsId, ExperiencesService.LadsSmpRebornDevId, StringComparison.OrdinalIgnoreCase))
        {
            ExperiencesService.EnsureDevLadsSmpRebornSeeded(DataDirectory);
        }
        RefreshCardState(
            ExperiencesService.LadsSmpReborn,
            ladsId,
            _runningLadsSmpReborn,
            _ladsSmpRebornCts != null,
            allowMultiInstance,
            LadsSmpRebornPlayBtn,
            LadsSmpRebornStatusBadge,
            LadsSmpRebornStatusBadgeText,
            LadsSmpRebornStatusText,
            LadsSmpRebornRamBox,
            LadsSmpRebornFolderBtn,
            LadsSmpRebornReinstallBtn,
            LadsSmpRebornInstancesDropdownBtn,
            LadsSmpRebornInstancesDropdownText,
            LadsSmpRebornInstancesBar,
            LadsSmpRebornInstancesQuickButtons,
            LadsSmpRebornProgressBar);

        // 2. Refresh Zero SMP card
        var zeroId = ZeroSmpInstanceId;
        if (string.Equals(zeroId, ExperiencesService.ZeroSmpDevId, StringComparison.OrdinalIgnoreCase))
        {
            ExperiencesService.EnsureDevZeroSmpSeeded(DataDirectory);
        }
        RefreshCardState(
            ExperiencesService.ZeroSmp,
            zeroId,
            _runningZeroSmp,
            _zeroSmpCts != null,
            allowMultiInstance,
            ZeroSmpPlayBtn,
            ZeroSmpStatusBadge,
            ZeroSmpStatusBadgeText,
            ZeroSmpStatusText,
            ZeroSmpRamBox,
            ZeroSmpFolderBtn,
            ZeroSmpReinstallBtn,
            ZeroSmpInstancesDropdownBtn,
            ZeroSmpInstancesDropdownText,
            ZeroSmpInstancesBar,
            ZeroSmpInstancesQuickButtons,
            ZeroSmpProgressBar);

        ZeroSmpAutoJoinCheck.IsVisible = true;

        if (Host is { } host)
        {
            _syncingAutoJoin = true;
            ZeroSmpAutoJoinCheck.IsChecked = host.ModpackSettings.ZeroSmpAutoJoin;
            _syncingAutoJoin = false;

            SyncAccountSelector(host, LadsSmpRebornAccountSelector);
            SyncAccountSelector(host, ZeroSmpAccountSelector);
        }
    }

    /// <summary>Invite-only: the Lads SMP Reborn card shows only when one of the Microsoft accounts is invited (/smp_invite on Discord).</summary>
    private async void RefreshInviteAccess()
    {
        var names = Host?.GetAccountSummaries().Where(a => a.type == "microsoft").Select(a => a.username).ToList() ?? new List<string>();
        var key = string.Join(",", names.OrderBy(n => n, StringComparer.OrdinalIgnoreCase));
        if (key == _accessKey && DateTime.UtcNow - _accessCheckedUtc < TimeSpan.FromMinutes(1)) return;
        _accessKey = key;
        _accessCheckedUtc = DateTime.UtcNow;
        bool allowed;
        try { allowed = await ExperiencesService.HasAccessAsync(DataDirectory, ExperiencesService.LadsSmpReborn, names); }
        catch { allowed = false; }
        if (key == _accessKey) LadsSmpRebornCard.IsVisible = allowed;
    }

    private void RefreshCardState(
        ExperienceDefinition exp,
        string instanceId,
        List<RunningInstance> runningList,
        bool busy,
        bool allowMultiInstance,
        Button playBtn,
        Border statusBadge,
        TextBlock statusBadgeText,
        TextBlock statusText,
        NumericUpDown ramBox,
        Button folderBtn,
        Button reinstallBtn,
        Button instancesDropdownBtn,
        TextBlock instancesDropdownText,
        Border instancesBar,
        WrapPanel instancesQuickButtons,
        ProgressBar progressBar)
    {
        bool installed = ExperiencesService.IsInstalled(DataDirectory, instanceId);
        bool running = HasRunningProcess(runningList);

        var btnState = ExperiencesService.GetExperienceButtonState(exp, installed, running, busy, allowMultiInstance);
        playBtn.Content = btnState.Text;
        playBtn.IsEnabled = btnState.IsEnabled;

        if (running)
        {
            statusBadge.Background = Brush.Parse("#1D2A1F");
            statusBadge.BorderBrush = Brush.Parse("#386B42");
            statusBadgeText.Text = runningList.Count > 1 ? $"Running ({runningList.Count})" : "Running";
            statusBadgeText.Foreground = Brush.Parse("#85E89D");
        }
        else if (busy)
        {
            // operation status remains in statusText
        }
        else if (installed)
        {
            statusBadge.Background = Brush.Parse("#1D2A1F");
            statusBadge.BorderBrush = Brush.Parse("#386B42");
            statusBadgeText.Text = "Installed";
            statusBadgeText.Foreground = Brush.Parse("#85E89D");

            var instance = ExperiencesService.GetInstance(DataDirectory, instanceId);
            if (instance != null && instance.MaxRamMb > 0)
            {
                _updatingRam = true;
                int gb = BinaryMbToRamGb(instance.MaxRamMb);
                ramBox.Value = gb;
                ToolTip.SetTip(ramBox, $"{gb * 1024} MB");
                _updatingRam = false;
            }
        }
        else
        {
            statusBadge.Background = Brush.Parse("#2A211D");
            statusBadge.BorderBrush = Brush.Parse("#6B3838");
            statusBadgeText.Text = "Not Installed";
            statusBadgeText.Foreground = Brush.Parse("#E88585");
        }

        statusText.IsVisible = !string.IsNullOrWhiteSpace(statusText.Text);
        folderBtn.IsEnabled = installed && !busy;
        reinstallBtn.IsEnabled = installed && !busy && !running;

        UpdateRunningInstancesUi(exp, runningList, busy, statusText, progressBar, instancesDropdownBtn, instancesDropdownText, instancesBar, instancesQuickButtons);
    }

    private void LadsSmpRebornRamBox_ValueChanged(object? sender, NumericUpDownValueChangedEventArgs e) =>
        OnRamBoxValueChanged(LadsSmpRebornRamBox, LadsSmpRebornInstanceId, ExperiencesService.LadsSmpReborn.RecommendedRamMb);

    private void ZeroSmpRamBox_ValueChanged(object? sender, NumericUpDownValueChangedEventArgs e) =>
        OnRamBoxValueChanged(ZeroSmpRamBox, ZeroSmpInstanceId, ExperiencesService.ZeroSmp.RecommendedRamMb);

    private void OnRamBoxValueChanged(NumericUpDown? ramBox, string instanceId, int fallbackMb)
    {
        if (_updatingRam || ramBox == null) return;
        _updatingRam = true;
        try
        {
            int mb = RamGbToBinaryMb(ramBox.Value, fallbackMb);
            int gb = BinaryMbToRamGb(mb);
            if (ramBox.Value != gb) ramBox.Value = gb;
            ToolTip.SetTip(ramBox, $"{mb} MB");

            var instance = ExperiencesService.GetInstance(DataDirectory, instanceId);
            if (instance != null && instance.MaxRamMb != mb)
            {
                instance.MaxRamMb = mb;
                Modpacks.Save(instance);
            }
        }
        finally
        {
            _updatingRam = false;
        }
    }

    private void SyncAccountSelector(MainWindow host, ComboBox? selector)
    {
        if (selector == null) return;
        _syncingAccounts = true;
        try
        {
            var choices = host.GetLaunchAccountChoices();
            var existing = selector.Items.OfType<AccountChoice>().ToList();
            if (!existing.SequenceEqual(choices))
            {
                selector.Items.Clear();
                foreach (var choice in choices)
                {
                    selector.Items.Add(choice);
                }
            }
            string selectedName = host.SelectedLaunchAccountName;
            selector.SelectedItem = choices.FirstOrDefault(c => string.Equals(c.Name, selectedName, StringComparison.OrdinalIgnoreCase));
        }
        finally
        {
            _syncingAccounts = false;
        }
    }

    private void LadsSmpRebornAccountSelector_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (_syncingAccounts || Host is not { } host) return;
        if (LadsSmpRebornAccountSelector?.SelectedItem is AccountChoice { Name: var name })
        {
            host.SelectLaunchAccount(name);
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

    private void UpdateRunningInstancesUi(
        ExperienceDefinition exp,
        List<RunningInstance> runningList,
        bool busy,
        TextBlock statusText,
        ProgressBar progressBar,
        Button? dropdownBtn,
        TextBlock? dropdownText,
        Border? instancesBar,
        WrapPanel? quickButtons)
    {
        if (dropdownBtn == null || dropdownBtn.Flyout is not MenuFlyout menu || instancesBar == null || quickButtons == null || dropdownText == null)
            return;

        var ordered = runningList.OrderBy(i => i.Number).ToList();
        bool anyOpen = ordered.Count > 0;

        dropdownBtn.IsVisible = anyOpen;
        instancesBar.IsVisible = anyOpen;
        menu.Items.Clear();
        quickButtons.Children.Clear();

        if (!anyOpen) return;

        dropdownText.Text = $"Instances Open ({ordered.Count})";

        var headerLabel = new TextBlock
        {
            Text = $"Instances Open ({ordered.Count}):",
            FontSize = 12,
            FontWeight = FontWeight.SemiBold,
            Foreground = Brush.Parse("#85E89D"),
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 2, 10, 2)
        };
        quickButtons.Children.Add(headerLabel);

        foreach (var inst in ordered)
        {
            var captured = inst;
            var menuItem = new MenuItem
            {
                Header = $"Close Instance {captured.Number} ({captured.Username})"
            };
            menuItem.Click += (_, _) => CloseInstance(exp, runningList, busy, statusText, progressBar, captured);
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
            quickBtn.Click += (_, _) => CloseInstance(exp, runningList, busy, statusText, progressBar, captured);
            quickButtons.Children.Add(quickBtn);
        }

        if (ordered.Count > 1)
        {
            menu.Items.Add(new Separator());
        }

        var closeAllItem = new MenuItem
        {
            Header = ordered.Count > 1 ? $"Close All ({ordered.Count} Instances)" : "Close All"
        };
        closeAllItem.Click += (_, _) => CloseAllInstances(exp, runningList, busy, statusText, progressBar);
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
        closeAllBtn.Click += (_, _) => CloseAllInstances(exp, runningList, busy, statusText, progressBar);
        quickButtons.Children.Add(closeAllBtn);
    }

    private void CloseInstance(ExperienceDefinition exp, List<RunningInstance> runningList, bool busy, TextBlock statusText, ProgressBar progressBar, RunningInstance inst)
    {
        runningList.Remove(inst);
        GameSession.Cancel(inst.Process);
        Host?.ModpackLog($"Closed {exp.Title} Instance {inst.Number} ({inst.Username}, PID {inst.Process.Id}).");
        SetStatusText(statusText, $"Closed Instance {inst.Number} ({inst.Username}).");
        if (!HasRunningProcess(runningList) && !busy) progressBar.IsVisible = false;
        RefreshState();
    }

    private void CloseAllInstances(ExperienceDefinition exp, List<RunningInstance> runningList, bool busy, TextBlock statusText, ProgressBar progressBar)
    {
        var toClose = runningList.ToList();
        runningList.Clear();
        foreach (var inst in toClose)
        {
            GameSession.Cancel(inst.Process);
            Host?.ModpackLog($"Closed {exp.Title} Instance {inst.Number} ({inst.Username}, PID {inst.Process.Id}).");
        }
        SetStatusText(statusText, toClose.Count > 1 ? $"Closed all {toClose.Count} {exp.Title} instances." : $"Closed {exp.Title} instance.");
        if (!busy) progressBar.IsVisible = false;
        RefreshState();
    }

    private void ZeroSmpAutoJoin_Changed(object? sender, RoutedEventArgs e)
    {
        if (_syncingAutoJoin || Host is not { } host) return;
        host.ModpackSettings.ZeroSmpAutoJoin = ZeroSmpAutoJoinCheck.IsChecked == true;
        host.ModpackSettings.Save();
    }

    private async void LadsSmpRebornPlay_Click(object? sender, RoutedEventArgs e)
    {
        var relaunch = _ladsSmpRebornRelaunch;
        _ladsSmpRebornRelaunch = null;
        if (Host is not { } host) return;
        if (_ladsSmpRebornCts != null) return;
        if (HasRunningProcess(_runningLadsSmpReborn) && !host.ModpackSettings.AllowMultiInstance) return;

        var exp = ExperiencesService.LadsSmpReborn;
        var instanceId = LadsSmpRebornInstanceId;
        if (string.Equals(instanceId, ExperiencesService.LadsSmpRebornDevId, StringComparison.OrdinalIgnoreCase))
        {
            ExperiencesService.EnsureDevLadsSmpRebornSeeded(DataDirectory);
        }

        bool installed = ExperiencesService.IsInstalled(DataDirectory, instanceId);
        _ladsSmpRebornCts = new CancellationTokenSource();
        var token = _ladsSmpRebornCts.Token;

        LadsSmpRebornProgressBar.IsVisible = true;
        LadsSmpRebornProgressBar.Value = 0;
        RefreshState();

        void Report(string message) => Dispatcher.UIThread.Post(() => SetStatusText(LadsSmpRebornStatusText, message));

        try
        {
            ModpackInstance? instance;
            if (!installed)
            {
                Report($"Installing {exp.Title} isolated instance...");
                instance = await ExperiencesService.InstallLadsSmpRebornAsync(DataDirectory, Report, token, Http, instanceId);
            }
            else
            {
                instance = ExperiencesService.GetInstance(DataDirectory, instanceId)
                           ?? await ExperiencesService.InstallLadsSmpRebornAsync(DataDirectory, Report, token, Http, instanceId);
            }

            if (LadsSmpRebornRamBox.Value.HasValue)
            {
                instance.MaxRamMb = RamGbToBinaryMb(LadsSmpRebornRamBox.Value, exp.RecommendedRamMb);
                Modpacks.Save(instance);
            }

            await ExperiencesService.ApplyPendingPatchesAsync(DataDirectory, instance.Id, Http, Report, token, ExperiencesService.BundledPatchesDirectory);

            Report("Signing in...");
            var session = await host.ResolveModpackSessionAsync(token);

            Report($"Preparing Minecraft {exp.McVersion} & Fabric {exp.LoaderVersion}...");
            var process = await ModpackLauncher.LaunchAsync(DataDirectory, instance, session, host.ModpackSettings, JavaService.Instance, Http, Report, token,
                relaunch?.Server, relaunch?.World);

            int instNumber = NextInstanceNumber(_runningLadsSmpReborn);
            var runningInst = new RunningInstance(instNumber, session.Username ?? "Player", process);
            _runningLadsSmpReborn.Add(runningInst);
            process.EnableRaisingEvents = true;
            process.Exited += (_, _) => Dispatcher.UIThread.Post(() =>
            {
                _runningLadsSmpReborn.Remove(runningInst);
                bool wasCancelled = GameSession.WasCancelled(process);
                int code = process.ExitCode;
                host.ModpackLog($"{exp.Title} Instance {instNumber} ({runningInst.Username}) closed (exit code {code}).");
                if (!wasCancelled)
                {
                    SetStatusText(LadsSmpRebornStatusText, code == 0
                        ? $"Instance {instNumber} ({runningInst.Username}) closed."
                        : $"Instance {instNumber} ({runningInst.Username}) closed with exit code {code}. See logs in Open Folder.");
                }
                if (!HasRunningProcess(_runningLadsSmpReborn) && _ladsSmpRebornCts == null) LadsSmpRebornProgressBar.IsVisible = false;
                RefreshState();
                host.ApplyNextAccountRequest(instance.GameDirectory);
                host.ApplyNextVersionRequest(instance.GameDirectory);
                // Closed from the in-game shader toggle: start again on the other renderer, back in the world or on the server.
                if (!wasCancelled && GraphicsRenderer.TakeRelaunch(instance.GameDirectory, host.ModpackLog) is { } next)
                {
                    _ladsSmpRebornRelaunch = next;
                    LadsSmpRebornPlay_Click(null, new RoutedEventArgs());
                }
            });

            Report($"{exp.Title} Instance {instNumber} started as {session.Username}!");
            host.ModpackLog($"Started {exp.Title} Instance {instNumber} (Fabric {exp.LoaderVersion}, Minecraft {exp.McVersion}) PID {process.Id}.");
            RefreshState();
        }
        catch (OperationCanceledException)
        {
            Report("Action cancelled.");
        }
        catch (Exception error)
        {
            Report("Failed: " + error.Message);
            host.ModpackLog($"{exp.Title} error: {error}");
        }
        finally
        {
            _ladsSmpRebornCts?.Dispose();
            _ladsSmpRebornCts = null;
            if (!HasRunningProcess(_runningLadsSmpReborn)) LadsSmpRebornProgressBar.IsVisible = false;
            RefreshState();
        }
    }

    private void LadsSmpRebornFolder_Click(object? sender, RoutedEventArgs e) =>
        OpenExperienceFolder(LadsSmpRebornInstanceId, LadsSmpRebornStatusText);

    private async void LadsSmpRebornReinstall_Click(object? sender, RoutedEventArgs e)
    {
        if (Host is not { } host) return;
        bool confirmed = await host.ConfirmModpackAsync(
            "Reinstall The Lads SMP Reborn",
            "This will re-copy the modpack mods and default configurations. Your singleplayer worlds in saves/ will NOT be deleted.",
            "Reinstall");

        if (!confirmed) return;

        _ladsSmpRebornCts = new CancellationTokenSource();
        LadsSmpRebornProgressBar.IsVisible = true;
        RefreshState();

        void Report(string message) => Dispatcher.UIThread.Post(() => SetStatusText(LadsSmpRebornStatusText, message));

        try
        {
            Report("Reinstalling The Lads SMP Reborn...");
            await ExperiencesService.InstallLadsSmpRebornAsync(DataDirectory, Report, _ladsSmpRebornCts.Token, Http, LadsSmpRebornInstanceId);
            Report("The Lads SMP Reborn reinstalled successfully.");
        }
        catch (Exception ex)
        {
            Report("Reinstall failed: " + ex.Message);
        }
        finally
        {
            _ladsSmpRebornCts?.Dispose();
            _ladsSmpRebornCts = null;
            LadsSmpRebornProgressBar.IsVisible = false;
            RefreshState();
        }
    }

    private async void ZeroSmpPlay_Click(object? sender, RoutedEventArgs e)
    {
        if (Host is not { } host) return;
        if (_zeroSmpCts != null) return;
        if (HasRunningProcess(_runningZeroSmp) && !host.ModpackSettings.AllowMultiInstance) return;
        bool retry = _raceRetry;
        _raceRetry = false;

        var instanceId = ZeroSmpInstanceId;
        if (string.Equals(instanceId, ExperiencesService.ZeroSmpDevId, StringComparison.OrdinalIgnoreCase))
        {
            ExperiencesService.EnsureDevZeroSmpSeeded(DataDirectory);
        }

        bool installed = ExperiencesService.IsInstalled(DataDirectory, instanceId);
        _zeroSmpCts = new CancellationTokenSource();
        var token = _zeroSmpCts.Token;

        ZeroSmpProgressBar.IsVisible = true;
        ZeroSmpProgressBar.Value = 0;
        RefreshState();

        void Report(string message) => Dispatcher.UIThread.Post(() => SetStatusText(ZeroSmpStatusText, message));

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
                instance.MaxRamMb = RamGbToBinaryMb(ZeroSmpRamBox.Value, ExperiencesService.ZeroSmp.RecommendedRamMb);
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

            int instNumber = NextInstanceNumber(_runningZeroSmp);
            var runningInst = new RunningInstance(instNumber, session.Username ?? "Player", process);
            _runningZeroSmp.Add(runningInst);
            process.EnableRaisingEvents = true;
            process.Exited += (_, _) => Dispatcher.UIThread.Post(() =>
            {
                _runningZeroSmp.Remove(runningInst);
                bool wasCancelled = GameSession.WasCancelled(process);
                int code = process.ExitCode;
                host.ModpackLog($"Zero SMP Instance {instNumber} ({runningInst.Username}) closed (exit code {code}).");
                if (!wasCancelled)
                {
                    SetStatusText(ZeroSmpStatusText, code == 0
                        ? $"Instance {instNumber} ({runningInst.Username}) closed."
                        : $"Instance {instNumber} ({runningInst.Username}) closed with exit code {code}. See logs in Open Folder.");
                }
                if (!HasRunningProcess(_runningZeroSmp) && _zeroSmpCts == null) ZeroSmpProgressBar.IsVisible = false;
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
            _zeroSmpCts?.Dispose();
            _zeroSmpCts = null;
            if (!HasRunningProcess(_runningZeroSmp)) ZeroSmpProgressBar.IsVisible = false;
            RefreshState();
        }
    }

    private void ZeroSmpFolder_Click(object? sender, RoutedEventArgs e) =>
        OpenExperienceFolder(ZeroSmpInstanceId, ZeroSmpStatusText);

    private static void OpenExperienceFolder(string instanceId, TextBlock statusText)
    {
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
            SetStatusText(statusText, "Could not open folder: " + ex.Message);
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

        _zeroSmpCts = new CancellationTokenSource();
        ZeroSmpProgressBar.IsVisible = true;
        RefreshState();

        void Report(string message) => Dispatcher.UIThread.Post(() => SetStatusText(ZeroSmpStatusText, message));

        try
        {
            Report("Reinstalling Zero SMP...");
            await ExperiencesService.InstallZeroSmpAsync(DataDirectory, Report, _zeroSmpCts.Token, Http, ZeroSmpInstanceId);
            Report("Zero SMP reinstalled successfully.");
        }
        catch (Exception ex)
        {
            Report("Reinstall failed: " + ex.Message);
        }
        finally
        {
            _zeroSmpCts?.Dispose();
            _zeroSmpCts = null;
            ZeroSmpProgressBar.IsVisible = false;
            RefreshState();
        }
    }
}
