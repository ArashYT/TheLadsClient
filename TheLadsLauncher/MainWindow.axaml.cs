using System;
using System.Net.Http;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Text.Json;
using System.Text.Json.Nodes;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.LogicalTree;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Platform.Storage;
using Avalonia.Threading;
using Avalonia.Markup.Xaml.Templates;
using Avalonia.Controls.Templates;
using System.Runtime.InteropServices;
using CmlLib.Core;
using CmlLib.Core.Auth;
using XboxAuthNet.Game;
using CmlLib.Core.Auth.Microsoft;
using CmlLib.Core.ProcessBuilder;
using XboxAuthNet.XboxLive;
using XboxAuthNet.Game.Msal;
using XboxAuthNet.Game.Msal.OAuth;
using Microsoft.Identity.Client;
using TheLadsLauncher.Services;

namespace TheLadsLauncher;



public partial class MainWindow : Window
{
    private MicrosoftAccountService loginHandler = null!;
    private LauncherSettings settings;
    private readonly TheLadsLauncher.Services.IProfileService _profileService = TheLadsLauncher.Services.ProfileService.Instance;
    private readonly TheLadsLauncher.Services.IJavaService _javaService = TheLadsLauncher.Services.JavaService.Instance;
    private readonly TheLadsLauncher.Services.IPathService _pathService = TheLadsLauncher.Services.PathService.Instance;
    private AvaloniaMsalProvider? _msalProvider;
    private CancellationTokenSource? _authCts;
    private bool _addingAccount;
    private bool _accountCacheRecoveryShown;
    private readonly Dictionary<string, string> _accountNotices = new(StringComparer.OrdinalIgnoreCase);
    private bool _launching;
    private CancellationTokenSource? _launchCts; // the launch overlay's Cancel, until the game process starts
    private bool _populatingProfileSelector = false;

    private string _selectedAccountInternal = "";
    private string _selectedAccount 
    {
        get => _selectedAccountInternal;
        set 
        {
            _selectedAccountInternal = value;
            if (settings != null)
            {
                settings.MainAccount = value;
                settings.Save();
            }
        }
    }
    // Alt-account launch override: when non-empty, the next launch uses this account
    // WITHOUT changing _selectedAccount (the user's main). Set via LaunchAccountSelector.
    private string _launchAccountOverride = "";
    private bool _populatingLaunchSelector = false;
    private bool _showingMicrosoftSetup;
    // Games this launcher started, with their game folder. Touched only on the UI thread.
    private readonly Dictionary<Process, string> _runningProcesses = new();
    private DispatcherTimer? _updateCheckTimer;
    // --preview-shared <outputDir>: sandbox-only screenshot/JSON capture of the shared-content surfaces, then exit.
    private string? _previewSharedOutput;
    // --preview-mods <outputDir>: sandbox-only capture of the Mods page for every filter, then exit.
    private string? _previewModsOutput;
    private string? _previewWorldsOutput;
    private string? _previewServersOutput;

    // ── 16:9 aspect ratio lock ───────────────────────────────────────────────
    private bool   _lockAspect     = false;
    private bool   _adjustingAspect = false;
    private const  double ASPECT_RATIO   = 16.0 / 9.0;
    private const  double DEFAULT_WIDTH  = 1152.0;
    private const  double DEFAULT_HEIGHT = 720.0;
    private DispatcherTimer _statsTimer;
    private DispatcherTimer _statSmoothTimer;
    private double _targetCpu, _dispCpu, _targetRam, _dispRam;
    private DispatcherTimer _particleTimer;
    private ParticleMeshControl? _meshControl;
    private bool _meshControlAdded;
    private Avalonia.Controls.TrayIcon? _trayIcon;

    // CPU & Download Tracking
    private TimeSpan _lastCpuTime = TimeSpan.Zero;
    private DateTime _lastCpuCheck = DateTime.UtcNow;
    private DateTime _lastDownloadTime = DateTime.UtcNow;
    private long _lastBytes = 0;

    // Particles
    private List<Particle> _particles = new();
    private Random _rng = new();

    // Nebula blobs (slow-drifting red glow clouds behind the particle mesh)
    private class NebulaBlob
    {
        public double X, Y, Radius, VX, VY, Phase, PulseSpeed, BaseOpacity;
    }
    private List<NebulaBlob> _nebulae = new();

    // Logging
    private Queue<string> logLines = new();
    private List<string> allLogLines = new();
    private bool _logDirty;

    // Web Client
    private System.Net.Http.HttpClient _httpClient = new();

    public MainWindow()
    {
        InitializeComponent();

        _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        settings = LauncherSettings.Load();
        InitializeProductivity();
        if (settings.LauncherVersion != Program.Version)
        {
            settings.LauncherVersion = Program.Version;
            
            // Migrate to 26.2 and new Packwiz URLs if the user has legacy settings
            if (settings.FabricVersion.Contains("26.1.2") || settings.FabricVersion == "fabric-loader-0.19.2-26.1.2")
                settings.FabricVersion = "fabric-loader-0.19.3-26.2";
            
            if (settings.PackwizPath.Contains("The Lads Client Packwiz") || settings.PackwizPath.Contains("Arash"))
                settings.PackwizPath = Path.Combine(TheLadsLauncher.Services.PathService.Instance.BaseDirectory, "packwiz");
                
            if (settings.PackwizUrl.Contains("The%20Lads%20Client%20Packwiz"))
                settings.PackwizUrl = "https://raw.githubusercontent.com/ArashYT/TheLadsClient/main/Packwiz/pack.toml";
            
            settings.Save();
        }
        
        Log($"[Settings] Loaded settings from BaseDirectory: {AppDomain.CurrentDomain.BaseDirectory}");
        Log($"[Settings] ModrinthApiUrl: '{settings.ModrinthApiUrl}', CurseForgeApiUrl: '{settings.CurseForgeApiUrl}', OverrideVersion: '{settings.SelectedMinecraftVersionOverride}', FabricVersion: '{settings.FabricVersion}'");
        InitializeAuthentication();

        // Removed automation Trigger login
        
        // Print API reflection info for accounts
        try
        {
            foreach (var prop in typeof(XboxAuthNet.Game.Accounts.IXboxGameAccount).GetProperties())
                Log($"[IXboxGameAccount Prop] {prop.Name}");
            foreach (var method in typeof(XboxAuthNet.Game.Accounts.IXboxGameAccountManager).GetMethods())
                Log($"[IXboxGameAccountManager Method] {method.Name}");
        }
        catch (Exception ex)
        {
            Log($"[Reflection ERROR] {ex.Message}");
        }

        var args = Environment.GetCommandLineArgs();
        if (args.Contains("--auto-login"))
        {
            Log("[AUTOMATION] --auto-login detected. Triggering AddNewAccount...");
            Dispatcher.UIThread.Post(async () =>
            {
                await Task.Delay(1000);
                await AddNewAccount();
            });
        }
        else if (args.Contains("--auto-launch-offline"))
        {
            Log("[AUTOMATION] --auto-launch-offline detected. Triggering LaunchGame...");
            if (!settings.OfflineAccounts.Contains("TestPlayer"))
            {
                settings.OfflineAccounts.Add("TestPlayer");
                settings.Save();
            }
            Dispatcher.UIThread.Post(async () =>
            {
                try
                {
                    await Task.Delay(2000);
                    _selectedAccount = "TestPlayer";
                    await LaunchGame();
                }
                catch (Exception ex)
                {
                    Log($"[Auto-Launch Error] {ex.Message}");
                }
            });
        }
        
        LoadSettingsUI();
        InitializeLanguageSetting();
        InitializeSettingsAutoSave();
        DiscordPresence.Log = Log;
        DiscordPresence.Enable(settings.DiscordRichPresence);
        // Every other launcher window (a dialog) shows on Discord by its title while it is open; the game splash runs with the game.
        WindowOpenedEvent.AddClassHandler<Window>((window, _) => { if (window is not MainWindow and not Views.GameStartupSplash) DiscordPresence.Dialog(window.Title); });
        WindowClosedEvent.AddClassHandler<Window>((window, _) => { if (window is not MainWindow and not Views.GameStartupSplash) DiscordPresence.Dialog(null); });
        ModsSubTabControl.SelectionChanged += ModsSubTab_SelectionChanged;
        PopulateLaunchProfileSelector();
        LoadProfilesUI();
        LoadAccounts();
        if (args.Contains("--preview-accounts")) Dispatcher.UIThread.Post(() => NavigateTo("Accounts"));
        // Program.Main already refused this switch unless THELADS_DIR and LADS_GLOBAL_MINECRAFT_DIR point at a sandbox.
        int previewShared = Array.IndexOf(args, "--preview-shared");
        if (previewShared >= 0 && previewShared + 1 < args.Length) _previewSharedOutput = Path.GetFullPath(args[previewShared + 1]);
        int previewMods = Array.IndexOf(args, "--preview-mods");
        if (previewMods >= 0 && previewMods + 1 < args.Length) _previewModsOutput = Path.GetFullPath(args[previewMods + 1]);
        int previewWorlds = Array.IndexOf(args, "--preview-worlds");
        if (previewWorlds >= 0 && previewWorlds + 1 < args.Length) _previewWorldsOutput = Path.GetFullPath(args[previewWorlds + 1]);
        int previewServers = Array.IndexOf(args, "--preview-servers");
        if (previewServers >= 0 && previewServers + 1 < args.Length) _previewServersOutput = Path.GetFullPath(args[previewServers + 1]);

        // Enable drag-and-drop of .jar files onto the Mods page to install them.
        if (ModsPage != null)
        {
            ModsPage.AddHandler(DragDrop.DragOverEvent, ModsPage_DragOver);
            ModsPage.AddHandler(DragDrop.DropEvent, ModsPage_Drop);
        }

        // === Startup/update screen: breathing logo, progress bar with a sweeping shine, mining animation ===
        const double startupDurationMs = 2200.0;
        const double startupBarWidth = 560.0;
        var startupSw = System.Diagnostics.Stopwatch.StartNew();
        int dots = 0;
        double dotAccumulatorMs = 0, shownProgress = 0, lastTickMs = 0;
        if (settings.ReducedMotion) StartupMining.Freeze(0.81);
        var startupAnimTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(16) };
        startupAnimTimer.Tick += (s, e) =>
        {
            double t = startupSw.Elapsed.TotalMilliseconds, dt = t - lastTickMs;
            lastTickMs = t;
            // A reported download drives the bar; until then it eases toward 90% so it never claims to be done early.
            double target = _startupDownload ?? 0.9 * (1.0 - Math.Pow(1.0 - Math.Min(1.0, t / startupDurationMs), 3));
            shownProgress += (target - shownProgress) * (1 - Math.Exp(-dt / 110.0));
            StartupProgressFill.Width = startupBarWidth * shownProgress;
            if (settings.ReducedMotion) return;

            // Subtle breathing pulse on the logo
            double pulse = 1.0 + 0.04 * Math.Sin(t / 300.0);
            if (StartupLogoImage?.RenderTransform is Avalonia.Media.ScaleTransform logoScale)
            {
                logoScale.ScaleX = pulse;
                logoScale.ScaleY = pulse;
            }
            if (StartupProgressShine.RenderTransform is Avalonia.Media.TranslateTransform shine)
                shine.X = -110 + (t % 1600.0) / 1600.0 * (startupBarWidth + 110);

            // Animated trailing dots
            dotAccumulatorMs += 16;
            if (dotAccumulatorMs >= 350)
            {
                dotAccumulatorMs = 0;
                dots = (dots + 1) % 4;
                LoadingDots.Text = new string('.', dots);
            }
        };
        startupAnimTimer.Start();

        this.Loaded += async (s, e) =>
        {
            int startupPreview = Array.IndexOf(args, "--preview-startup");
            if (startupPreview >= 0) { await RunStartupPreviewAsync(Path.GetFullPath(args[startupPreview + 1])); return; }
            await PollForUpdatesAsync();
            if (_windowClosed) return;
            startupAnimTimer.Stop();
            StartupMining.Stop();
            if (StartupProgressFill != null) StartupProgressFill.Width = startupBarWidth;
            LauncherStartupOverlay.IsVisible = false;
            int discoveryPreview = Array.IndexOf(args, "--preview-discovery");
            if (discoveryPreview >= 0) { await RunDiscoveryPreviewAsync(Path.GetFullPath(args[discoveryPreview + 1])); return; }
            int productivityPreview = Array.IndexOf(args, "--preview-productivity");
            if (productivityPreview >= 0) { await RunProductivityPreviewAsync(Path.GetFullPath(args[productivityPreview + 1])); return; }
            int languagePreview = Array.IndexOf(args, "--preview-language");
            if (languagePreview >= 0)
            {
                await RunLanguagePreviewAsync(Path.GetFullPath(args[languagePreview + 1]),
                    languagePreview + 2 < args.Length ? args[languagePreview + 2].Split(',') : new[] { "es", "de", "ja" });
                return;
            }
            int chromePreview = Array.IndexOf(args, "--preview-chrome");
            if (chromePreview >= 0) { await RunChromePreviewAsync(Path.GetFullPath(args[chromePreview + 1])); return; }
            int contentPreview = Array.IndexOf(args, "--preview-content");
            if (contentPreview >= 0) { await RunContentPreviewAsync(Path.GetFullPath(args[contentPreview + 1])); return; }
            int modpacksPreview = Array.IndexOf(args, "--preview-modpacks");
            if (modpacksPreview >= 0) { await RunModpacksPreviewAsync(Path.GetFullPath(args[modpacksPreview + 1])); return; }
            int skinsPreview = Array.IndexOf(args, "--preview-skins");
            if (skinsPreview >= 0)
            {
                string output = Path.GetFullPath(args[skinsPreview + 1]);
                Directory.CreateDirectory(output);
                NavigateTo("Accounts");
                await Task.Delay(400);
                SaveWindowScreenshot(Path.Combine(output, "accounts.png"));
                NavigateTo("Skins");
                await SkinsPage.RunPreviewAsync(output, SaveWindowScreenshot);
                Close();
                return;
            }
            if (_previewWorldsOutput != null)
            {
                Directory.CreateDirectory(_previewWorldsOutput);
                NavigateTo("Worlds");
                await WorldsPage.LoadAsync();
                await Task.Delay(750);
                SaveWindowScreenshot(Path.Combine(_previewWorldsOutput, "worlds.png"));
                Close();
                return;
            }
            if (_previewServersOutput != null)
            {
                Directory.CreateDirectory(_previewServersOutput);
                NavigateTo("Servers");
                await ServersPage.PreviewAsync(name => SaveWindowScreenshot(Path.Combine(_previewServersOutput, name)));
                Close();
                return;
            }
            if (_previewModsOutput != null)
            {
                await RunModsPreviewAsync(_previewModsOutput);
                return;
            }
            if (_previewSharedOutput != null)
            {
                await RunSharedPreviewAsync(_previewSharedOutput);
                return;
            }
            _ = RunStartupSharedContentPassAsync();

            // Checks use a ten-minute interval; ready updates retry the idle condition
            // every fifteen seconds, including after the game or authentication ends.
            // From here on the launcher is open: updates are offered by the title-bar button instead of restarting on their own.
            _autoUpdater.AskBeforeInstall = true;
            _autoUpdater.Ready += version => Dispatcher.UIThread.Post(() => ShowUpdateButton(version));
            _updateCheckTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(15) };
            _updateCheckTimer.Tick += async (_, _) => await PollForUpdatesAsync();
            _updateCheckTimer.Start();
            if (settings.LastSeenReleaseNotesVersion != Program.Version)
                await ShowReleaseNotesAsync();
        };
        var logTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(200) };
        logTimer.Tick += (_, _) =>
        {
            lock (logLines)
            {
                if (!_logDirty) return;
                LogBox.Text = string.Join(Environment.NewLine, logLines);
                _logDirty = false;
            }
        };
        logTimer.Start();
        Closed += (_, _) => { _windowClosed = true; _updateCheckTimer?.Stop(); _updateNowPulse?.Stop(); logTimer.Stop(); _authCts?.Cancel(); _galleryScanCancellation?.Cancel(); ++_galleryGeneration; };
        // Stats timer (1 second)
        _statsTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _statsTimer.Tick += UpdateSystemStats;
        _statsTimer.Tick += WatchModFiles;
        _statsTimer.Start();

        // Smoothly ease the CPU/RAM numbers toward their targets (like the FPS counter)
        _statSmoothTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(50) };
        _statSmoothTimer.Tick += (s, e) =>
        {
            _dispCpu += (_targetCpu - _dispCpu) * 0.18;
            _dispRam += (_targetRam - _dispRam) * 0.18;
            CpuText.Text = $"{_dispCpu:F1}%";
            RamText.Text = $"{_dispRam:F2} GB";
        };
        _statSmoothTimer.Start();

        // Particle timer (30fps)
        _particleTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(33) };
        _particleTimer.Tick += UpdateParticles;

        // Initialize particles
        for (int i = 0; i < 40; i++)
            _particles.Add(CreateParticle(randomY: true));

        // Initialize nebula blobs
        for (int i = 0; i < 5; i++)
        {
            _nebulae.Add(new NebulaBlob
            {
                X = _rng.NextDouble() * 1000,
                Y = _rng.NextDouble() * 650,
                Radius = 160 + _rng.NextDouble() * 180,
                VX = (_rng.NextDouble() - 0.5) * 0.12,
                VY = (_rng.NextDouble() - 0.5) * 0.12,
                Phase = _rng.NextDouble() * Math.PI * 2,
                PulseSpeed = 0.003 + _rng.NextDouble() * 0.004,
                BaseOpacity = 0.04 + _rng.NextDouble() * 0.05
            });
        }

        // Create custom rendering mesh control
        _meshControl = new ParticleMeshControl(this);

        // Synchronize mesh control size to canvas bounds
        ParticleCanvas.PropertyChanged += (s, e) =>
        {
            if (e.Property == BoundsProperty)
            {
                _meshControl.Width = ParticleCanvas.Bounds.Width;
                _meshControl.Height = ParticleCanvas.Bounds.Height;
            }
        };

        if (settings.ShowParticles && !settings.ReducedMotion)
        {
            ParticleCanvas.Children.Add(_meshControl);
            _meshControlAdded = true;
            _particleTimer.Start();
        }

        // Immediate toggle listener
        ParticleCheckbox.IsCheckedChanged += (s, e) =>
        {
            bool show = ParticleCheckbox.IsChecked ?? false;
            settings.ShowParticles = show;
            settings.Save();

            if (show && !settings.ReducedMotion)
            {
                if (!_meshControlAdded && _meshControl != null)
                {
                    ParticleCanvas.Children.Clear();
                    ParticleCanvas.Children.Add(_meshControl);
                    _meshControlAdded = true;
                }
                if (!_particleTimer.IsEnabled)
                    _particleTimer.Start();
            }
            else
            {
                if (_particleTimer.IsEnabled)
                    _particleTimer.Stop();
                ParticleCanvas.Children.Clear();
                _meshControlAdded = false;
            }
        };

        // Custom title bar dragging
        TitleBar.PointerPressed += (s, e) => { if (e.GetCurrentPoint(this).Properties.IsLeftButtonPressed) BeginMoveDrag(e); };
        ContentTitleBar.PointerPressed += (s, e) => { if (e.GetCurrentPoint(this).Properties.IsLeftButtonPressed) BeginMoveDrag(e); };

        // System tray
        SetupTrayIcon();

        // Apply theme & UI scale
        ApplyTheme();
        ApplyUiScale();

        // Version display
        VersionText.Text = $"v{Program.Version}";
        UpdateMinecraftVersionDisplay();
        InitializeSearchMcVersions();
        _ = TriggerDefaultSearchesAsync();
    }

    // ═══════════════════════════════════════
    //  NAVIGATION
    // ═══════════════════════════════════════

    private readonly AutoUpdater _autoUpdater = new(new VelopackUpdateBackend());
    private bool _releaseNotesOpen;
    private bool _windowClosed;
    private double? _startupDownload; // 0..1 once the updater reports a download; drives the startup progress bar

    /// <summary>The startup screen splits "Downloading v1.3.5: 42%" (VelopackUpdateBackend's wording) into a status line and a large percentage.</summary>
    private void ShowStartupStatus(string message)
    {
        var download = System.Text.RegularExpressions.Regex.Match(message, @"^Downloading (v\S+): (\d+)%$");
        if (download.Success)
        {
            _startupDownload = Math.Clamp(int.Parse(download.Groups[2].Value) / 100.0, 0, 1);
            StartupLoadingSpinner.Text = "Downloading update " + download.Groups[1].Value;
            StartupPercentText.Text = download.Groups[2].Value + "%";
            return;
        }
        if (message.StartsWith("Installing", StringComparison.Ordinal)) { _startupDownload = 1; StartupPercentText.Text = "100%"; }
        // The trailing dots are animated separately.
        StartupLoadingSpinner.Text = string.IsNullOrEmpty(message) ? "Loading resources" : message.TrimEnd('…', '.');
    }

    private Task PollForUpdatesAsync() => _autoUpdater.PollAsync(UpdateBlocked, ReportUpdate,
        Environment.GetEnvironmentVariable("LADS_SKIP_UPDATE") == "1" || _windowClosed);

    /// <summary>Installing restarts the launcher, so it waits for Minecraft, sign-in, mod work and open dialogs.</summary>
    private bool UpdateBlocked() => _windowClosed || _launching || _modsBusy || _addingAccount || _showingMicrosoftSetup || _releaseNotesOpen
        || _runningProcesses.Keys.Any(IsGameRunning) || SharedContentService.Instance.IsBusy
        || _profileService.GetProfiles().Any(p => GameRunningByMarker(_pathService.GetProfileDirectory(p)));

    private void ReportUpdate(string message)
    {
        Dispatcher.UIThread.Post(() =>
        {
            if (_windowClosed) return;
            ShowStartupStatus(message);
            UpdateBannerText.Text = message;
            UpdateBanner.IsVisible = !string.IsNullOrEmpty(message);
        });
        if (!string.IsNullOrEmpty(message)) Log($"[Updater] {message}");
    }

    private DispatcherTimer? _updateNowPulse;
    private string? _readyUpdateVersion; // a verified update waits for the Update button (offered again at Play)

    /// <summary>A verified update found while the launcher is open waits for the user behind the glowing title-bar button.</summary>
    private void ShowUpdateButton(string version)
    {
        if (_windowClosed) return;
        _readyUpdateVersion = version;
        ToolTip.SetTip(UpdateNowBtn, $"Version {version} is ready. Click to install it and restart the launcher.");
        if (UpdateNowHost.IsVisible) return;
        UpdateNowHost.IsVisible = true;
        Log($"[Updater] v{version} is ready; waiting for the Update button.");
        if (settings.ReducedMotion) return;
        var clock = Stopwatch.StartNew();
        _updateNowPulse = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(16) };
        _updateNowPulse.Tick += (_, _) =>
        {
            double t = clock.Elapsed.TotalSeconds;
            UpdateNowGlow.Opacity = 0.45 + 0.4 * (0.5 + 0.5 * Math.Sin(t * Math.PI / 0.9)); // slow breathing glow
            if (UpdateNowShine.RenderTransform is Avalonia.Media.TransformGroup group && group.Children[1] is Avalonia.Media.TranslateTransform shine)
            {
                double phase = t % 2.6, width = UpdateNowBtn.Bounds.Width + 60; // one sweep, then a pause
                shine.X = phase < 0.9 ? -40 + width * (phase / 0.9) : -40;
            }
        };
        _updateNowPulse.Start();
    }

    private void UpdateNow_Click(object? sender, RoutedEventArgs e)
    {
        if (_autoUpdater.InstallNow(UpdateBlocked, ReportUpdate)) return;
        ReportUpdate(UpdateBlocked()
            ? "Close Minecraft and finish signing in or mod changes, then click Update again."
            : "The update is not ready yet; the launcher will offer it again shortly.");
    }

    private static bool IsGameRunning(Process process)
    {
        try { return !process.HasExited; }
        catch (InvalidOperationException) { return false; }
    }

    /// <summary>A game started for this folder by any launcher instance (running marker). Unreadable counts as running.</summary>
    private bool GameRunningByMarker(string gameDirectory)
    {
        try { return RunningGameMarker.IsRunning(gameDirectory); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            Log($"[Launcher] Could not read the running-game marker in '{gameDirectory}': {ex.Message}. Treating the game as running.");
            return true;
        }
    }

    private bool IsGameRunningFor(string gameDirectory) =>
        _runningProcesses.Any(p => SafeFileOps.PathsEqual(p.Value, gameDirectory) && IsGameRunning(p.Key)) || GameRunningByMarker(gameDirectory);

    private async void ReleaseNotes_Click(object? sender, RoutedEventArgs e) => await ShowReleaseNotesAsync();

    private async Task ShowReleaseNotesAsync()
    {
        if (_releaseNotesOpen || _windowClosed) return;
        _releaseNotesOpen = true;
        try
        {
            var notes = ReleaseNotes.Read(Path.Combine(AppContext.BaseDirectory, "release-notes"));
            var dialog = new Views.ReleaseNotesWindow(notes, Program.Version);
            await dialog.ShowDialog(this);
            settings.LastSeenReleaseNotesVersion = Program.Version;
            settings.Save();
        }
        catch (Exception ex) { Log($"[Release notes] {ex.Message}"); }
        finally { _releaseNotesOpen = false; }
    }
    // Mods and Packs sub-tabs show on Discord as their own pages. SelectionChanged bubbles from lists inside the tabs too.
    private void ModsSubTab_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (e.Source != ModsSubTabControl || !ModsPage.IsVisible) return;
        var tab = ModsSubTabControl.SelectedItem;
        DiscordPresence.Page(tab == ModsBrowseTab ? "BrowseMods" : tab == ModsSettingsTab ? "ModSettings" : tab == PacksBrowseTab ? "Packs"
            : tab == ShaderPacksTab ? "Shaders" : tab == DataPacksTab ? "DataPacks" : "Mods");
    }
    private void NavigateTo(string page)
    {
        DiscordPresence.Page(page);
        HomePage.IsVisible = page == "Home";
        WorldsPage.IsVisible = page == "Worlds";
        NavWorlds.Classes.Set("active", page == "Worlds");
        ServersPage.IsVisible = page == "Servers";
        NavServers.Classes.Set("active", page == "Servers");
        ModpacksPage.IsVisible = page == "Modpacks";
        NavModpacks.Classes.Set("active", page == "Modpacks");
        ProfilesPage.IsVisible = page == "Profiles";
        AccountsPage.IsVisible = page == "Accounts";
        SkinsPage.IsVisible = page == "Skins";
        SettingsPage.IsVisible = page == "Settings";
        ModsPage.IsVisible = page is "Mods" or "Packs";
        if (page is "Mods" or "Packs")
        {
            bool packs = page == "Packs";
            ModsInstalledTab.IsVisible = ModsBrowseTab.IsVisible = ModsSettingsTab.IsVisible = !packs;
            PacksBrowseTab.IsVisible = ShaderPacksTab.IsVisible = DataPacksTab.IsVisible = packs;
            ModsSubTabControl.SelectedItem = packs ? PacksBrowseTab : ModsInstalledTab;
        }
        FilesPage.IsVisible = page == "Files";
        GalleryPage.IsVisible = page == "Gallery";
        LogsPage.IsVisible = page == "Logs";

        // Update nav button styles
        NavHome.Classes.Set("active", page == "Home");
        NavProfiles.Classes.Set("active", page == "Profiles");
        NavAccounts.Classes.Set("active", page == "Accounts");
        NavSkins.Classes.Set("active", page == "Skins");
        NavSettings.Classes.Set("active", page == "Settings");
        NavMods.Classes.Set("active", page == "Mods");
        NavPacks.Classes.Set("active", page == "Packs");
        NavFiles.Classes.Set("active", page == "Files");
        NavGallery.Classes.Set("active", page == "Gallery");
        NavLogs.Classes.Set("active", page == "Logs");
        AnimateNavigation();
    }

    private async void NavWorlds_Click(object? sender, RoutedEventArgs e) { NavigateTo("Worlds"); await WorldsPage.LoadAsync(); }
    private async void NavServers_Click(object? sender, RoutedEventArgs e) { NavigateTo("Servers"); await ServersPage.LoadAsync(); }

    private void NavHome_Click(object? sender, RoutedEventArgs e) => NavigateTo("Home");
    private void NavProfiles_Click(object? sender, RoutedEventArgs e)
    {
        LoadProfilesUI();
        NavigateTo("Profiles");
    }
    private void NavAccounts_Click(object? sender, RoutedEventArgs e) { LoadAccounts(); NavigateTo("Accounts"); }
    private void NavSkins_Click(object? sender, RoutedEventArgs e) { NavigateTo("Skins"); ShowSkins(); }
    // The Skins tab applies to the main account; Microsoft accounts get a silently refreshed Minecraft token.
    private void ShowSkins()
    {
        var account = loginHandler.AccountManager.GetAccounts().FirstOrDefault(a => (a as CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount)?.Profile?.Username == _selectedAccount);
        _ = SkinsPage.ShowAsync(_selectedAccount, account == null ? null : async ct => (await loginHandler.AuthenticateSilently(account, ct)).AccessToken!, _httpClient);
    }
    private void NavSettings_Click(object? sender, RoutedEventArgs e) => NavigateTo("Settings");
    private void NavMods_Click(object? sender, RoutedEventArgs e)
    {
        ReloadModsInventory();
        NavigateTo("Mods");
    }
    private void NavPacks_Click(object? sender, RoutedEventArgs e) => NavigateTo("Packs");
    private void NavFiles_Click(object? sender, RoutedEventArgs e) { LoadFiles(settings.InstancePath); NavigateTo("Files"); }
    private void NavLogs_Click(object? sender, RoutedEventArgs e) => NavigateTo("Logs");

    // ═══════════════════════════════════════
    //  FILES EXPLORER
    // ═══════════════════════════════════════

    private string _filesCurrentDir = "";
    private string _filesRootDir = "";

    /// <summary>The Files page follows the active profile: back to its game folder on every profile switch.</summary>
    private void ResetFilesRoot()
    {
        _filesRootDir = settings.InstancePath;
        _filesCurrentDir = settings.InstancePath;
        if (FilesPage.IsVisible) LoadFiles(_filesRootDir);
    }

    private static TextBlock FilesNote(string text) => new()
    {
        Text = text, Foreground = Brush.Parse("#A0A1AA"), FontSize = 13, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(4)
    };

    private void LoadFiles(string dir)
    {
        if (string.IsNullOrWhiteSpace(_filesRootDir))
            _filesRootDir = settings.InstancePath;
        if (string.IsNullOrWhiteSpace(dir) || !Directory.Exists(dir) || !SafeFileOps.IsSameOrInside(dir, _filesRootDir))
            dir = _filesRootDir;

        _filesCurrentDir = dir;
        bool atRoot = SafeFileOps.PathsEqual(dir, _filesRootDir);
        FilesUpBtn.IsEnabled = !atRoot;
        FilesPathText.Text = dir;
        FilesList.Children.Clear();

        if (!Directory.Exists(dir))
        {
            FilesList.Children.Add(FilesNote("This profile has no game files yet. Launch it to create its game folder."));
            return;
        }

        try
        {
            // Worlds, resource packs and shader packs are links to the shared folders: badge them and say where their content lives.
            IReadOnlyList<SharedFolderStatus> statuses = SharedContentService.Instance.GetStatus(_filesRootDir);
            var sharedArea = statuses.FirstOrDefault(s => s.State is SharedFolderState.Shared or SharedFolderState.GlobalFolder
                && SafeFileOps.IsSameOrInside(dir, s.ProfilePath));
            if (sharedArea != null) FilesPathText.Text = $"{dir}   (shared with every version: {sharedArea.SharedPath})";

            foreach (var d in Directory.GetDirectories(dir).OrderBy(p => Path.GetFileName(p), StringComparer.OrdinalIgnoreCase))
                FilesList.Children.Add(BuildFileRow(d, true, atRoot ? statuses.FirstOrDefault(s => SafeFileOps.PathsEqual(s.ProfilePath, d)) : null));
            foreach (var f in Directory.GetFiles(dir).OrderBy(p => Path.GetFileName(p), StringComparer.OrdinalIgnoreCase))
                FilesList.Children.Add(BuildFileRow(f, false, null));

            if (FilesList.Children.Count == 0)
                FilesList.Children.Add(FilesNote("Empty folder."));
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            Log($"[Files] Could not list '{dir}': {ex.Message}");
            FilesList.Children.Add(FilesNote($"Could not list this folder: {ex.Message}"));
        }
    }

    private static (string Label, string Tip)? SharedBadge(SharedFolderStatus status) => status.State switch
    {
        SharedFolderState.Shared => ("SHARED", status.Detail),
        SharedFolderState.GlobalFolder => ("GLOBAL FOLDER", status.Detail),
        SharedFolderState.SeparateFolder => ("NOT SHARED YET", status.Detail),
        SharedFolderState.LinkedElsewhere => ("LINKED ELSEWHERE", status.Detail),
        SharedFolderState.BrokenLink => ("BROKEN LINK", status.Detail),
        _ => null
    };

    private Border BuildFileRow(string path, bool isDir, SharedFolderStatus? shared)
    {
        string name = Path.GetFileName(path);
        var row = new Border
        {
            Background = new SolidColorBrush(Color.Parse("#1D1E22")),
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(10, 6, 8, 6)
        };
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto,Auto") };

        var icon = new Avalonia.Controls.Shapes.Path
        {
            Data = Geometry.Parse(isDir ? "M 1,5 V 3 H 7 L 9,5 H 17 V 15 H 1 Z" : "M 3,1 H 11 L 16,6 V 17 H 3 Z M 11,1 V 6 H 16"),
            Width = 16, Height = 16, Stretch = Stretch.Uniform, Stroke = Brush.Parse("#9295A0"),
            StrokeThickness = 1.2, VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 10, 0)
        };
        Grid.SetColumn(icon, 0);
        grid.Children.Add(icon);

        var nameText = new TextBlock { Text = name, Foreground = new SolidColorBrush(Color.Parse(isDir ? "#CCCCDD" : "#AAAAAA")), FontSize = 13, VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center, TextTrimming = Avalonia.Media.TextTrimming.CharacterEllipsis }.Untranslated();
        Grid.SetColumn(nameText, 1);
        grid.Children.Add(nameText);

        if (shared != null && SharedBadge(shared) is { } badge)
        {
            var badgeBorder = new Border
            {
                Background = new SolidColorBrush(Color.Parse("#303137")), CornerRadius = new CornerRadius(4), Padding = new Thickness(6, 2),
                Margin = new Thickness(8, 0, 8, 0), VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center,
                Child = new TextBlock { Text = badge.Label, Foreground = new SolidColorBrush(Color.Parse("#E27676")), FontSize = 9, FontWeight = FontWeight.Bold }
            };
            Avalonia.Controls.ToolTip.SetTip(badgeBorder, badge.Tip);
            Grid.SetColumn(badgeBorder, 2);
            grid.Children.Add(badgeBorder);
        }
        else if (!isDir)
        {
            string size;
            try { size = FormatBytes(new FileInfo(path).Length); }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { size = "size unknown"; }
            var sizeText = new TextBlock { Text = size, Foreground = new SolidColorBrush(Color.Parse("#90929D")), FontSize = 11, VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center, Margin = new Thickness(8, 0, 8, 0) };
            Grid.SetColumn(sizeText, 2);
            grid.Children.Add(sizeText);
        }

        var delBtn = new Button { Content = "✕", Classes = { "danger" }, Width = 28, Height = 26, FontSize = 11, HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center, VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center };
        Avalonia.Controls.ToolTip.SetTip(delBtn, "Move to the Recycle Bin");
        delBtn.Click += async (s, e) => await DeleteFileEntryAsync(path);
        Grid.SetColumn(delBtn, 3);
        grid.Children.Add(delBtn);

        row.Child = grid;
        row.PointerPressed += (s, e) =>
        {
            if (isDir) LoadFiles(path);
            else OpenPath(path);
        };
        return row;
    }

    /// <summary>
    /// Every delete asks first and goes to the Recycle Bin. Shared-folder links are refused (deleting them would not remove
    /// content, and deleting through them would remove it for every version); content inside them is confirmed as shared.
    /// </summary>
    private async Task DeleteFileEntryAsync(string path)
    {
        string name = Path.GetFileName(path);
        try
        {
            if (SafeFileOps.IsLink(path))
            {
                await ShowLadsDialogAsync("Shared folder link",
                    $"'{name}' is a link to '{SafeFileOps.GetLinkTarget(path)}', not a folder of its own. Worlds, resource packs and shader packs are shared by every version, so this link cannot be deleted. Open the shared folder (Home: Worlds, Resource packs, Shader packs) to manage its content.");
                return;
            }
            var shared = SharedContentService.Instance.GetStatus(_filesRootDir).FirstOrDefault(s =>
                s.State is SharedFolderState.Shared or SharedFolderState.GlobalFolder && SafeFileOps.IsSameOrInside(path, s.ProfilePath));
            bool confirmed = shared != null
                ? await ShowLadsDialogAsync("Delete shared content",
                    $"'{name}' is shared content used by every version (it lives in '{shared.SharedPath}'). Deleting it removes it for all versions. Move it to the Recycle Bin?",
                    "Move to Recycle Bin", "Cancel", danger: true)
                : await ShowLadsDialogAsync("Delete", $"Move '{name}' to the Recycle Bin?", "Move to Recycle Bin", "Cancel", danger: true);
            if (!confirmed) return;
            await Task.Run(() => SafeFileOps.DeleteToRecycleBin(path));
            Log($"[Files] Moved to the Recycle Bin: {path}");
        }
        catch (Exception ex)
        {
            Log($"[Files] Delete failed for '{path}': {ex.Message}");
            await ShowLadsDialogAsync("Could not delete", ex.Message);
        }
        LoadFiles(_filesCurrentDir);
    }

    private static string FormatBytes(long b)
    {
        if (b >= 1024L * 1024 * 1024) return $"{b / (1024.0 * 1024 * 1024):F1} GB";
        if (b >= 1024L * 1024) return $"{b / (1024.0 * 1024):F1} MB";
        if (b >= 1024L) return $"{b / 1024.0:F0} KB";
        return $"{b} B";
    }

    private void OpenPath(string path)
    {
        try
        {
            Process.Start(new ProcessStartInfo { FileName = path, UseShellExecute = true });
        }
        catch (Exception ex) when (ex is System.ComponentModel.Win32Exception or IOException or UnauthorizedAccessException or InvalidOperationException)
        {
            Log($"[Files] Open failed for '{path}': {ex.Message}");
            _ = ShowLadsDialogAsync("Could not open", $"'{path}': {ex.Message}");
        }
    }

    private void FilesUp_Click(object? sender, RoutedEventArgs e)
    {
        var parent = Directory.GetParent(_filesCurrentDir);
        if (parent != null) LoadFiles(parent.FullName);
    }

    private void FilesRefresh_Click(object? sender, RoutedEventArgs e) => LoadFiles(_filesCurrentDir);

    private void FilesOpenExplorer_Click(object? sender, RoutedEventArgs e) => OpenPath(_filesCurrentDir);

    // ═══════════════════════════════════════
    //  GALLERY
    // ═══════════════════════════════════════

    // Screenshots are inventoried in place across Lads and other launcher instances.
    private const int GalleryPageSize = 60;
    private List<(string Path, DateTime Time)> _galleryFiles = new();
    private Dictionary<string, ScreenshotEntry> _gallerySources = new(StringComparer.OrdinalIgnoreCase);
    private CancellationTokenSource? _galleryScanCancellation;
    private string _galleryScanSummary = "";
    private int _galleryShown;
    private int _galleryGeneration;
    private Task _galleryThumbnails = Task.CompletedTask;
    private string? _galleryFavoritesError;

    private void NavGallery_Click(object? sender, RoutedEventArgs e) { _ = LoadGalleryAsync(); NavigateTo("Gallery"); }
    // Reorders the last scan; a scan still running applies the new order when it finishes.
    private void GallerySort_Changed(object? sender, Avalonia.Controls.SelectionChangedEventArgs e) { if (GalleryPage?.IsVisible == true && _galleryScanCancellation == null) ShowGallerySorted(); }
    private void ImgurId_Changed(object? sender, RoutedEventArgs e) { settings.ImgurClientId = ImgurIdBox.Text ?? ""; settings.Save(); }
    private void GalleryOpenFolder_Click(object? sender, RoutedEventArgs e)
    {
        var error = OpenFolderCreatingIt(SharedContentService.Instance.ScreenshotsDirectory);
        if (error != null) GalleryStatusText.Text = error;
    }
    private void GalleryLoadMore_Click(object? sender, RoutedEventArgs e) => ShowMoreScreenshots();

    private async Task LoadGalleryAsync()
    {
        int generation = ++_galleryGeneration;
        _galleryScanCancellation?.Cancel();
        var cancellation = new CancellationTokenSource();
        _galleryScanCancellation = cancellation;
        SyncFavoritesWithGame();
        ClearGalleryCards();
        if (ImgurIdBox != null) ImgurIdBox.Text = settings.ImgurClientId;
        GalleryStatusText.Text = "Scanning launcher instances…";
        var profiles = _profileService.GetProfiles().Select(p => new ScreenshotRoot(_pathService.GetProfileDirectory(p), "Lads · " + p.Name)).ToArray();
        try
        {
            var catalog = await Task.Run(() => CreateScreenshotCatalog().Scan(profiles, cancellation.Token), cancellation.Token);
            if (generation != _galleryGeneration || _windowClosed) return;
            _gallerySources = catalog.Entries.ToDictionary(e => e.Path, StringComparer.OrdinalIgnoreCase);
            _galleryScanSummary = $"{catalog.Folders} folders" + (catalog.Warnings.Count > 0 ? $" · {catalog.Warnings.Count} scan notices" : "");
            ToolTip.SetTip(GalleryStatusText, catalog.Warnings.Count > 0 ? string.Join("\n", catalog.Warnings) : "Lads, Modrinth, CurseForge, Prism and your added folders. Originals stay in their instance.");
            foreach (var warning in catalog.Warnings) Log("[Gallery] " + warning);
            ShowGallerySorted();
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or ArgumentException)
        {
            if (generation != _galleryGeneration) return;
            _gallerySources = new(StringComparer.OrdinalIgnoreCase); // a later re-sort must not bring back the previous scan
            GalleryStatusText.Text = _galleryScanSummary = "Screenshot scan failed: " + ex.Message;
            Log("[Gallery] " + ex.Message);
        }
        finally
        {
            if (ReferenceEquals(_galleryScanCancellation, cancellation)) _galleryScanCancellation = null;
            cancellation.Dispose();
        }
    }

    /// <summary>Shows the last scan in the chosen order. Sorting and favorites never rescan the disk.</summary>
    private void ShowGallerySorted()
    {
        ++_galleryGeneration; // stops thumbnail loading for the cards being replaced
        ClearGalleryCards();
        int sort = GallerySortBox?.SelectedIndex ?? 0;
        var favorites = new HashSet<string>(settings.GalleryFavorites, StringComparer.OrdinalIgnoreCase);
        IEnumerable<ScreenshotEntry> files = _gallerySources.Values;
        files = sort switch
        {
            1 => files.OrderBy(f => f.Time),
            2 => files.OrderBy(f => Path.GetFileName(f.Path), StringComparer.OrdinalIgnoreCase),
            3 => files.OrderByDescending(f => favorites.Contains(GalleryFavoriteKey(f.Path))).ThenByDescending(f => f.Time),
            _ => files.OrderByDescending(f => f.Time)
        };
        _galleryFiles = files.Select(f => (f.Path, f.Time)).ToList();
        _galleryShown = 0;
        if (_galleryFiles.Count == 0)
        {
            GalleryStatusText.Text = _galleryFavoritesError ?? _galleryScanSummary;
            GalleryList.Children.Add(new TextBlock { Text = "No screenshots found. Add a folder for a portable launcher or a custom instance location.", Foreground = new SolidColorBrush(Color.Parse("#90929D")), FontSize = 13, Margin = new Thickness(4), TextWrapping = TextWrapping.Wrap });
            return;
        }
        ShowMoreScreenshots();
    }

    private void ClearGalleryCards()
    {
        foreach (var old in GalleryList.GetLogicalDescendants().OfType<Image>()) (old.Source as Bitmap)?.Dispose();
        GalleryList.Children.Clear();
        GalleryLoadMoreBtn.IsVisible = false;
    }

    private ScreenshotCatalogService CreateScreenshotCatalog() => new(SharedContentService.Instance.Root,
        discoverLaunchers: !Environment.GetCommandLineArgs().Any(a => a.StartsWith("--preview-", StringComparison.Ordinal)));

    private string GalleryFavoriteKey(string path) => _gallerySources.TryGetValue(path, out var entry) && entry.IsExternal ? path : Path.GetFileName(path);

    private void GalleryRescan_Click(object? sender, RoutedEventArgs e) => _ = LoadGalleryAsync();

    private async void GalleryAddFolder_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            var chosen = await StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions { Title = "Choose a screenshots, instance or launcher folder", AllowMultiple = false });
            if (chosen.FirstOrDefault()?.TryGetLocalPath() is not { } folder) return;
            CreateScreenshotCatalog().AddRoot(folder);
            await LoadGalleryAsync();
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Text.Json.JsonException or ArgumentException)
        { await ShowLadsDialogAsync("Could not add screenshot folder", ex.Message); }
    }

    // Lists the folders added with "Add folder"; choosing one stops listing it. Its files stay where they are.
    private async void GalleryRemoveFolder_Click(object? sender, RoutedEventArgs e)
    {
        if (sender is not Button button) return;
        try
        {
            var items = CreateScreenshotCatalog().LoadCustomRoots().Select(root =>
            {
                var item = new MenuItem { Header = root.Path }.Untranslated();
                item.Click += async (_, _) =>
                {
                    try
                    {
                        CreateScreenshotCatalog().RemoveRoot(root.Path);
                        await LoadGalleryAsync();
                    }
                    catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Text.Json.JsonException or ArgumentException)
                    { await ShowLadsDialogAsync("Could not remove screenshot folder", ex.Message); }
                };
                return item;
            }).ToList();
            if (items.Count == 0) items.Add(new MenuItem { Header = "No added folders", IsEnabled = false });
            button.ContextMenu = new ContextMenu { ItemsSource = items };
            button.ContextMenu.Open(button);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Text.Json.JsonException)
        { await ShowLadsDialogAsync("Could not read the added screenshot folders", ex.Message); }
    }

    /// <summary>Adds the next page of cards; their thumbnails are decoded off the UI thread.</summary>
    private void ShowMoreScreenshots()
    {
        var page = _galleryFiles.Skip(_galleryShown).Take(GalleryPageSize).ToList();
        _galleryShown += page.Count;
        var cards = new List<(string Path, Image Image, TextBlock Meta)>();
        foreach (var (path, time) in page)
        {
            GalleryList.Children.Add(BuildGalleryCard(path, time, out var image, out var meta));
            cards.Add((path, image, meta));
        }
        int left = _galleryFiles.Count - _galleryShown;
        GalleryLoadMoreBtn.IsVisible = left > 0;
        GalleryLoadMoreBtn.Content = $"Load more ({left} left)";
        GalleryStatusText.Text = $"{_galleryShown} of {_galleryFiles.Count} · {_galleryScanSummary}"
            + (_galleryFavoritesError != null ? $" · {_galleryFavoritesError}" : "");
        _galleryThumbnails = LoadGalleryThumbnailsAsync(cards, _galleryGeneration);
    }

    private async Task LoadGalleryThumbnailsAsync(List<(string Path, Image Image, TextBlock Meta)> cards, int generation)
    {
        foreach (var card in cards)
        {
            var (bitmap, meta, error) = await Task.Run(() => ReadGalleryThumbnail(card.Path));
            if (generation != _galleryGeneration)
            {
                bitmap?.Dispose();
                return;
            }
            card.Image.Source = bitmap;
            card.Meta.Text = error ?? meta;
            card.Meta.IsVisible = card.Meta.Text.Length > 0;
        }
    }

    private static (Bitmap? Bitmap, string Meta, string? Error) ReadGalleryThumbnail(string path)
    {
        Bitmap? bitmap = null;
        string? error = null;
        try
        {
            using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            bitmap = Bitmap.DecodeToWidth(stream, 340);
        }
        catch (Exception ex) // the image decoder throws plain exceptions for damaged files
        {
            error = $"Preview unavailable: {ex.Message}";
        }
        return (bitmap, ReadScreenshotMeta(path), error);
    }

    private Border BuildGalleryCard(string path, DateTime time, out Image image, out TextBlock meta)
    {
        string name = Path.GetFileName(path);
        string favoriteKey = GalleryFavoriteKey(path);
        bool fav = settings.GalleryFavorites.Contains(favoriteKey);

        var card = new Border { Background = new SolidColorBrush(Color.Parse("#14141F")), CornerRadius = new CornerRadius(8), Margin = new Thickness(6), Width = 182, Padding = new Thickness(6) };
        var stack = new StackPanel { Spacing = 4 };

        image = new Image { Width = 170, Height = 96, Stretch = Avalonia.Media.Stretch.UniformToFill };
        var imgBorder = new Border { CornerRadius = new CornerRadius(4), ClipToBounds = true, Height = 96, Background = new SolidColorBrush(Color.Parse("#25262A")), Child = image };
        imgBorder.PointerPressed += (s, e) => ShowGalleryViewer(path);
        stack.Children.Add(imgBorder);

        stack.Children.Add(new TextBlock { Text = name, Foreground = new SolidColorBrush(Color.Parse("#AAAAAA")), FontSize = 11, TextTrimming = Avalonia.Media.TextTrimming.CharacterEllipsis }.Untranslated());
        if (_gallerySources.TryGetValue(path, out var source))
            stack.Children.Add(new TextBlock { Text = source.Source, Foreground = new SolidColorBrush(Color.Parse("#CF8D8D")), FontSize = 10, TextTrimming = TextTrimming.CharacterEllipsis }.Untranslated());
        stack.Children.Add(new TextBlock { Text = time.ToString("g"), Foreground = new SolidColorBrush(Color.Parse("#90929D")), FontSize = 10 });

        // Metadata sidecar (written in-game): server/world, coords, biome. Filled in with the thumbnail.
        meta = new TextBlock { Foreground = new SolidColorBrush(Color.Parse("#7A88B0")), FontSize = 10, TextTrimming = Avalonia.Media.TextTrimming.CharacterEllipsis, IsVisible = false }.Untranslated();
        stack.Children.Add(meta);

        var actions = new StackPanel { Orientation = Avalonia.Layout.Orientation.Horizontal, Spacing = 4, Margin = new Thickness(0, 2, 0, 0) };
        actions.Children.Add(MiniGalBtn("📋", "Copy image to clipboard", _ => CopyImageToClipboard(path)));
        actions.Children.Add(MiniGalBtn("🔗", "Upload to Imgur (copies link)", _ => UploadImgur(path)));
        actions.Children.Add(MiniGalBtn("📂", "Show in folder", _ => OpenFolderSelect(path)));
        actions.Children.Add(MiniGalBtn(fav ? "★" : "☆", "Favorite", button =>
        {
            button.Content = ToggleGalleryFav(favoriteKey) ? "★" : "☆";
            if (GallerySortBox?.SelectedIndex == 3) ShowGallerySorted();
        }));
        if (source?.IsExternal != true)
            actions.Children.Add(MiniGalBtn("✕", "Move to the Recycle Bin", button => _ = DeleteScreenshotAsync(path)));
        stack.Children.Add(actions);

        card.Child = stack;
        return card;
    }

    private Button MiniGalBtn(string content, string tip, Action<Button> onClick)
    {
        var b = new Button { Content = content, Width = 30, Height = 26, FontSize = 12, Padding = new Thickness(0), HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center, VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center };
        Avalonia.Controls.ToolTip.SetTip(b, tip);
        b.Click += (s, e) => onClick(b);
        return b;
    }

    private static string ReadScreenshotMeta(string pngPath)
    {
        string metaPath = pngPath + ".json";
        try
        {
            if (!File.Exists(metaPath)) return "";
            using var doc = JsonDocument.Parse(File.ReadAllText(metaPath));
            var root = doc.RootElement;
            var parts = new List<string>();
            if (root.TryGetProperty("server", out var sv) && !string.IsNullOrEmpty(sv.GetString()))
                parts.Add(sv.GetString()!);
            else if (root.TryGetProperty("world", out var wd) && !string.IsNullOrEmpty(wd.GetString()))
                parts.Add(wd.GetString()!);
            if (root.TryGetProperty("x", out var x) && root.TryGetProperty("z", out var z))
                parts.Add($"{x.GetInt32()}, {z.GetInt32()}");
            if (root.TryGetProperty("biome", out var bi) && !string.IsNullOrEmpty(bi.GetString()))
                parts.Add(bi.GetString()?.Replace("minecraft:", "") ?? "");
            if (root.TryGetProperty("seed", out var sd))
                parts.Add("seed " + sd.GetInt64());
            return string.Join("  ·  ", parts);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or JsonException or InvalidOperationException or FormatException)
        {
            return $"Details unreadable ({Path.GetFileName(metaPath)}: {ex.Message})";
        }
    }

    // Favorites are global (settings), like the shared screenshots folder they name. The active profile's
    // <game>\config\gallery_favorites.json only mirrors them: nothing else writes that file, and reading it back replaced the
    // global list with another profile's older copy on every profile switch (favorites were lost).
    private string GalleryFavoritesMirror => Path.Combine(settings.InstancePath, "config", "gallery_favorites.json");

    private void SyncFavoritesWithGame()
    {
        _galleryFavoritesError = null;
        if (settings.GalleryFavorites.Count > 0 || File.Exists(GalleryFavoritesMirror)) WriteGalleryFavoritesMirror();
    }

    private void SaveGalleryFavorites()
    {
        settings.Save();
        WriteGalleryFavoritesMirror();
    }

    private void WriteGalleryFavoritesMirror()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(GalleryFavoritesMirror)!);
            File.WriteAllText(GalleryFavoritesMirror, JsonSerializer.Serialize(settings.GalleryFavorites));
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            _galleryFavoritesError = $"Favorites are saved, but the copy '{GalleryFavoritesMirror}' could not be written: {ex.Message}";
            GalleryStatusText.Text = _galleryFavoritesError;
            Log($"[Gallery] {_galleryFavoritesError}");
        }
    }

    /// <summary>Returns whether the screenshot is a favorite now.</summary>
    private bool ToggleGalleryFav(string name)
    {
        bool favorite = !settings.GalleryFavorites.Remove(name);
        if (favorite) settings.GalleryFavorites.Add(name);
        SaveGalleryFavorites();
        return favorite;
    }

    private async Task DeleteScreenshotAsync(string path)
    {
        if (_gallerySources.TryGetValue(path, out var source) && source.IsExternal) return;
        string name = Path.GetFileName(path);
        if (!await ShowLadsDialogAsync("Delete screenshot",
                $"Move '{name}' to the Recycle Bin? Screenshots are shared by every version.", "Move to Recycle Bin", "Cancel", danger: true))
            return;
        var profiles = _profileService.GetProfiles().Select(p => _pathService.GetProfileDirectory(p)).ToList();
        try
        {
            var deleted = await Task.Run(() =>
            {
                // Lads profiles' copies too (found while the shared file still exists), or the next game exit copies it back.
                var files = ScreenshotCatalogService.ProfileCopies(path, SharedContentService.Instance.ScreenshotsDirectory, profiles).Append(path).ToList();
                foreach (string file in files)
                {
                    SafeFileOps.DeleteToRecycleBin(file);
                    string sidecar = file + ".json";
                    if (File.Exists(sidecar)) SafeFileOps.DeleteToRecycleBin(sidecar);
                }
                return files;
            });
            Log($"[Gallery] Moved to the Recycle Bin: {string.Join(", ", deleted)}");
            if (settings.GalleryFavorites.Remove(name)) SaveGalleryFavorites();
        }
        catch (Exception ex)
        {
            Log($"[Gallery] Delete failed for '{path}': {ex.Message}");
            await ShowLadsDialogAsync("Could not delete the screenshot", $"'{name}': {ex.Message}");
        }
        await LoadGalleryAsync();
    }

    private string _viewerPath = "";
    // Gallery viewer zoom/pan state
    private double _viewerZoom = 1.0;
    private double _viewerPanX = 0, _viewerPanY = 0;
    private bool _viewerDragging = false;
    private Avalonia.Point _viewerLastPointer;

    private void ShowGalleryViewer(string path)
    {
        _viewerPath = path;
        GalleryViewerTitle.Text = Path.GetFileName(path);
        GalleryViewerMeta.Text = File.GetLastWriteTime(path).ToString("f");
        try
        {
            using var fs = File.OpenRead(path);
            GalleryViewerImage.Source = new Bitmap(fs);
        }
        catch { GalleryViewerImage.Source = null; }
        ResetViewerZoom();
        GalleryViewerOverlay.IsVisible = true;
    }

    // ─── Gallery viewer zoom + pan ───────────────
    private void ResetViewerZoom()
    {
        _viewerZoom = 1.0;
        _viewerPanX = 0; _viewerPanY = 0;
        _viewerDragging = false;
        ApplyViewerTransform();
    }

    private void ApplyViewerTransform()
    {
        if (GalleryViewerImage == null) return;
        GalleryViewerImage.RenderTransformOrigin = Avalonia.RelativePoint.Center;
        var g = new TransformGroup();
        g.Children.Add(new ScaleTransform(_viewerZoom, _viewerZoom));
        g.Children.Add(new TranslateTransform(_viewerPanX, _viewerPanY));
        GalleryViewerImage.RenderTransform = g;
        GalleryViewerImage.Cursor = new Avalonia.Input.Cursor(
            _viewerZoom > 1.0 ? Avalonia.Input.StandardCursorType.SizeAll : Avalonia.Input.StandardCursorType.Arrow);
    }

    private void GalleryViewer_Wheel(object? sender, Avalonia.Input.PointerWheelEventArgs e)
    {
        double factor = e.Delta.Y > 0 ? 1.15 : 1.0 / 1.15;
        double newZoom = Math.Clamp(_viewerZoom * factor, 1.0, 8.0);
        if (Math.Abs(newZoom - _viewerZoom) < 0.0001) return;
        _viewerZoom = newZoom;
        if (_viewerZoom <= 1.0) { _viewerPanX = 0; _viewerPanY = 0; } // snap back to centered
        ApplyViewerTransform();
        e.Handled = true;
    }

    private void GalleryViewer_PointerPressed(object? sender, Avalonia.Input.PointerPressedEventArgs e)
    {
        if (_viewerZoom <= 1.0) return; // only pan when zoomed in
        _viewerDragging = true;
        _viewerLastPointer = e.GetPosition(GalleryViewerOverlay);
        e.Handled = true;
    }

    private void GalleryViewer_PointerMoved(object? sender, Avalonia.Input.PointerEventArgs e)
    {
        if (!_viewerDragging) return;
        var p = e.GetPosition(GalleryViewerOverlay);
        _viewerPanX += p.X - _viewerLastPointer.X;
        _viewerPanY += p.Y - _viewerLastPointer.Y;
        _viewerLastPointer = p;
        ApplyViewerTransform();
    }

    private void GalleryViewer_PointerReleased(object? sender, Avalonia.Input.PointerReleasedEventArgs e)
    {
        _viewerDragging = false;
    }

    private void GalleryViewerClose_Click(object? sender, RoutedEventArgs e)
    {
        GalleryViewerOverlay.IsVisible = false;
        GalleryViewerImage.Source = null;
        _viewerPath = "";
        ResetViewerZoom();
    }

    private void GalleryViewerFullscreen_Click(object? sender, RoutedEventArgs e)
    {
        this.WindowState = this.WindowState == WindowState.FullScreen
            ? WindowState.Normal
            : WindowState.FullScreen;
    }

    protected override void OnKeyDown(KeyEventArgs e)
    {
        if (e.Key == Key.Escape && GalleryViewerOverlay != null && GalleryViewerOverlay.IsVisible)
        {
            if (!this.IsActive || this.WindowState == WindowState.Minimized)
            {
                base.OnKeyDown(e);
                return;
            }
            GalleryViewerOverlay.IsVisible = false;
            if (GalleryViewerImage != null)
            {
                GalleryViewerImage.Source = null;
            }
            _viewerPath = "";
            e.Handled = true;
            return;
        }
        base.OnKeyDown(e);
    }

    private void OpenFolderSelect(string path)
    {
        try { Process.Start(new ProcessStartInfo { FileName = "explorer.exe", Arguments = "/select,\"" + path + "\"", UseShellExecute = true }); }
        catch (Exception ex) { Log($"[Gallery] show in folder failed: {ex.Message}"); }
    }

    private void CopyTextToClipboard(string text)
    {
        try
        {
            var p = new Process();
            p.StartInfo.FileName = "clip.exe";
            p.StartInfo.UseShellExecute = false;
            p.StartInfo.RedirectStandardInput = true;
            p.StartInfo.CreateNoWindow = true;
            p.Start();
            p.StandardInput.Write(text);
            p.StandardInput.Close();
            p.WaitForExit(1000);
        }
        catch (Exception ex) { Log($"[Gallery] copy text failed: {ex.Message}"); }
    }

    private void CopyImageToClipboard(string path)
    {
        try
        {
            string safe = path.Replace("'", "''");
            var p = new Process();
            p.StartInfo.FileName = "powershell";
            p.StartInfo.Arguments = "-NoProfile -STA -Command \"Add-Type -AssemblyName System.Windows.Forms,System.Drawing; [System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Image]::FromFile('" + safe + "'))\"";
            p.StartInfo.UseShellExecute = false;
            p.StartInfo.CreateNoWindow = true;
            p.Start();
            p.WaitForExit(4000);
            StatusText.Text = "Copied image to clipboard.";
        }
        catch (Exception ex) { Log($"[Gallery] copy image failed: {ex.Message}"); }
    }

    private async void UploadImgur(string path)
    {
        if (string.IsNullOrWhiteSpace(settings.ImgurClientId))
        {
            StatusText.Text = "Enter an Imgur Client ID in the Gallery toolbar first.";
            return;
        }
        try
        {
            StatusText.Text = "Uploading to Imgur...";
            byte[] bytes = File.ReadAllBytes(path);
            var req = new HttpRequestMessage(HttpMethod.Post, "https://api.imgur.com/3/image");
            req.Headers.Add("Authorization", "Client-ID " + settings.ImgurClientId);
            var form = new MultipartFormDataContent();
            form.Add(new ByteArrayContent(bytes), "image", Path.GetFileName(path));
            req.Content = form;
            var resp = await _httpClient.SendAsync(req);
            string body = await resp.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(body);
            if (doc.RootElement.TryGetProperty("data", out var data) && data.TryGetProperty("link", out var linkEl))
            {
                string link = linkEl.GetString() ?? "";
                CopyTextToClipboard(link);
                StatusText.Text = "Imgur link copied: " + link;
                Log($"[Gallery] Uploaded: {link}");
            }
            else
            {
                StatusText.Text = "Imgur upload failed (check your Client ID).";
            }
        }
        catch (Exception ex)
        {
            StatusText.Text = "Imgur upload failed: " + ex.Message;
        }
    }

    // ═══════════════════════════════════════
    //  WINDOW CONTROLS
    // ═══════════════════════════════════════

    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (!_lockAspect || _adjustingAspect || WindowState == WindowState.Maximized) return;
        if (change.Property.Name == "Width" && change.NewValue is double w && w > 200)
        {
            _adjustingAspect = true;
            this.Height = Math.Round(w / ASPECT_RATIO);
            _adjustingAspect = false;
        }
        else if (change.Property.Name == "Height" && change.NewValue is double h && h > 100)
        {
            _adjustingAspect = true;
            this.Width = Math.Round(h * ASPECT_RATIO);
            _adjustingAspect = false;
        }
    }

    private async void CheckUpdateBtn_Click(object? sender, RoutedEventArgs e)
    {
        Log("[Updater] Manual update check triggered.");
        ReportUpdate("Checking for updates...");
        await PollForUpdatesAsync();
    }

    private void MinimizeBtn_Click(object? sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;
    private void CloseBtn_Click(object? sender, RoutedEventArgs e)
    {
        FlushSettingsSave();
        if (settings.CloseToTray && _runningProcesses.Keys.Any(IsGameRunning))
            this.Hide();
        else
            Environment.Exit(0);
    }

    private void AspectLockBtn_Click(object? sender, RoutedEventArgs e)
    {
        bool maximized = IsAnimatedMaximized();
        _lockAspect = !_lockAspect;
        if (AspectLockBtn != null)
            AspectLockBtn.Content = _lockAspect ? "Aspect Ratio: 16:9" : "Aspect Ratio: Free";

        if (maximized) AnimateToMaximized();
        else if (_lockAspect && WindowState == WindowState.Normal)
        {
            _adjustingAspect = true;
            this.Height = this.Width / ASPECT_RATIO;
            _adjustingAspect = false;
        }
    }

    private void ResetSizeBtn_Click(object? sender, RoutedEventArgs e)
    {
        if (WindowState != WindowState.Normal)
            WindowState = WindowState.Normal;
        ForgetAnimatedMaximize();
        _adjustingAspect = true;
        this.Width  = DEFAULT_WIDTH;
        this.Height = _lockAspect ? DEFAULT_WIDTH / ASPECT_RATIO : DEFAULT_HEIGHT;
        _adjustingAspect = false;
    }

    private void MaximizeBtn_Click(object? sender, RoutedEventArgs e) => ToggleAnimatedMaximize();

    // ═══════════════════════════════════════
    //  PARTICLES
    // ═══════════════════════════════════════

    private class Particle
    {
        public double X, Y, SpeedX, SpeedY, Size, Opacity, Life, MaxLife;
    }

    private Particle CreateParticle(bool randomY = false)
    {
        double w = ParticleCanvas.Bounds.Width > 0 ? ParticleCanvas.Bounds.Width : 800;
        double h = ParticleCanvas.Bounds.Height > 0 ? ParticleCanvas.Bounds.Height : 600;
        double maxLife = 300 + _rng.Next(300);
        return new Particle
        {
            X = _rng.NextDouble() * w,
            Y = randomY ? _rng.NextDouble() * h : h + 15,
            SpeedX = (_rng.NextDouble() - 0.5) * 0.35, // Slow horizontal drift
            SpeedY = (_rng.NextDouble() - 0.5) * 0.35, // Slow vertical drift
            Size = 2.0 + _rng.NextDouble() * 4.0,       // Particle size 2px to 6px
            Opacity = 0.15 + _rng.NextDouble() * 0.45,  // Opacity 0.15 to 0.60
            Life = randomY ? _rng.NextDouble() * maxLife : 0,
            MaxLife = maxLife
        };
    }

    private void UpdateParticles(object? sender, EventArgs e)
    {
        if (!settings.ShowParticles || settings.ReducedMotion) return;

        double w = ParticleCanvas.Bounds.Width > 0 ? ParticleCanvas.Bounds.Width : 800;
        double h = ParticleCanvas.Bounds.Height > 0 ? ParticleCanvas.Bounds.Height : 600;

        for (int i = 0; i < _particles.Count; i++)
        {
            var p = _particles[i];
            p.X += p.SpeedX;
            p.Y += p.SpeedY;
            p.Life++;

            // Smooth edge wrap-around for slow-drifting network effect
            if (p.X < -25) p.X = w + 25;
            else if (p.X > w + 25) p.X = -25;
            
            if (p.Y < -25) p.Y = h + 25;
            else if (p.Y > h + 25) p.Y = -25;

            // Recreate particle if it reaches end of life
            if (p.Life >= p.MaxLife)
            {
                _particles[i] = CreateParticle();
            }
        }

        // Drift + pulse the nebula blobs
        foreach (var n in _nebulae)
        {
            n.X += n.VX;
            n.Y += n.VY;
            n.Phase += n.PulseSpeed;

            if (n.X < -n.Radius) n.X = w + n.Radius;
            else if (n.X > w + n.Radius) n.X = -n.Radius;
            if (n.Y < -n.Radius) n.Y = h + n.Radius;
            else if (n.Y > h + n.Radius) n.Y = -n.Radius;
        }

        // Trigger redrawing of the custom canvas element
        _meshControl?.InvalidateVisual();
    }

    // ═══════════════════════════════════════
    //  THEME & UI SCALE
    // ═══════════════════════════════════════

    private void ApplyTheme()
    {
        var (accent, hover, pressed, subtle) = settings.Theme switch
        {
            "DarkBlue" => ("#3869AD", "#477CC4", "#2D568F", "#1C2430"),
            "DarkPurple" => ("#7757AA", "#8B69C0", "#624790", "#25202E"),
            "Midnight" => ("#555F76", "#69758F", "#424B60", "#22252D"),
            "Halloween" => ("#F28A2E", "#FF9F4A", "#C96A1A", "#2A1B38"),
            _ => ("#C44343", "#D65353", "#A53434", "#241C1D")
        };
        // Application scope also keeps owned dialogs consistent with the selected theme.
        var resources = Application.Current!.Resources;
        resources["LadsAccent"] = Brush.Parse(accent);
        resources["LadsAccentHover"] = Brush.Parse(hover);
        resources["LadsAccentPressed"] = Brush.Parse(pressed);
        resources["LadsAccentSubtle"] = Brush.Parse(subtle);
        GameStateText.Foreground = Brush.Parse("#B9BCC6");
        ApplySeasonalChrome();
    }

    private void ApplyUiScale()
    {
        if (double.TryParse(settings.UiScale.Replace("%", ""), out double pct))
        {
            double scale = pct / 100.0;
            // Keep all controls reachable at the supported scale settings.
            MinWidth = 960 * scale;
            MinHeight = 600 * scale;
            if (UiScaleTransformControl?.LayoutTransform is ScaleTransform st)
            {
                st.ScaleX = scale;
                st.ScaleY = scale;
            }
            else if (UiScaleTransformControl != null)
            {
                UiScaleTransformControl.LayoutTransform = new ScaleTransform(scale, scale);
            }
        }
    }

    // ═══════════════════════════════════════
    //  SYSTEM STATS
    // ═══════════════════════════════════════

    private void UpdateSystemStats(object? sender, EventArgs e)
    {
        try
        {
            var proc = Process.GetCurrentProcess();
            
            var cpuTime = proc.TotalProcessorTime;
            var now = DateTime.UtcNow;
            double cpuPercent = 0;
            if (_lastCpuCheck != DateTime.MinValue)
            {
                var cpuDelta = (cpuTime - _lastCpuTime).TotalMilliseconds;
                var timeDelta = (now - _lastCpuCheck).TotalMilliseconds;
                if (timeDelta > 0)
                    cpuPercent = (cpuDelta / (timeDelta * Environment.ProcessorCount)) * 100.0;
            }
            _lastCpuTime = cpuTime;
            _lastCpuCheck = now;

            _targetCpu = cpuPercent;

            double launcherGb = proc.WorkingSet64 / (1024.0 * 1024.0 * 1024.0);
            _targetRam = launcherGb;

            var activeProcesses = _runningProcesses.Keys.Where(IsGameRunning).ToList();
            if (activeProcesses.Count > 0)
            {
                double totalGameRam = 0;
                foreach (var gameProc in activeProcesses)
                {
                    try { gameProc.Refresh(); totalGameRam += gameProc.WorkingSet64 / (1024.0 * 1024.0 * 1024.0); } catch { }
                }
                GameStateText.Text = "RUNNING";
                _targetRam = totalGameRam;
            }
            else if (!LaunchButton.IsEnabled)
            {
                GameStateText.Text = "LOADING";
            }
            else
            {
                GameStateText.Text = "IDLE";
            }
        }
        catch (Exception ex) when (ex is InvalidOperationException or System.ComponentModel.Win32Exception)
        {
            if (!_statsErrorLogged) Log($"[Launcher] Could not read process statistics: {ex.Message}");
            _statsErrorLogged = true;
        }
    }
    private bool _statsErrorLogged;

    // ═══════════════════════════════════════
    //  LOGGING
    // ═══════════════════════════════════════

    private static readonly object _logFileLock = new();

    private void Log(string message)
    {
        lock (_logFileLock)
        {
            try
            {
                var logFile = Path.Combine(TheLadsLauncher.Services.PathService.Instance.LogsDirectory, "launcher_debug.txt");
                System.IO.File.AppendAllText(logFile, $"[{DateTime.Now:HH:mm:ss}] {message}\n");
            }
            catch { }
        }
        Dispatcher.UIThread.Post(() =>
        {
            string timestamped = $"[{DateTime.Now:HH:mm:ss}] {message}";
            allLogLines.Add(timestamped);
            if (allLogLines.Count > 2000) allLogLines.RemoveRange(0, 500);
            logLines.Enqueue(timestamped);
            if (logLines.Count > 500)
                logLines.Dequeue();
            _logDirty = true;
        });
    }

    private void CopyLogs_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            if (!string.IsNullOrEmpty(LogBox.Text))
            {
                var process = new Process();
                process.StartInfo.FileName = "clip.exe";
                process.StartInfo.UseShellExecute = false;
                process.StartInfo.RedirectStandardInput = true;
                process.StartInfo.CreateNoWindow = true;
                process.Start();
                process.StandardInput.Write(LogBox.Text);
                process.StandardInput.Close();
                process.WaitForExit(2000);
                StatusText.Text = "Logs copied to clipboard!";
            }
        }
        catch { }
    }

    private void ClearLogs_Click(object? sender, RoutedEventArgs e)
    {
        logLines.Clear();
        allLogLines.Clear();
        LogBox.Text = "";
    }

    // ═══════════════════════════════════════
    //  ACCOUNTS SYSTEM
    // ═══════════════════════════════════════

    private void InitializeAuthentication()
    {
        var app = MsalClientHelper.BuildApplication(
            Guid.TryParse(settings.MicrosoftClientId, out var id) ? id.ToString() : Guid.Empty.ToString());
        _msalProvider = new AvaloniaMsalProvider(new MsalOAuthBuilder(app), this);
        _msalProvider.OnCancelRequested = () => _authCts?.Cancel();
        loginHandler = new MicrosoftAccountService(app, _msalProvider, _pathService.BaseDirectory, _httpClient);
    }

    private void LoadAccounts()
    {
        var msAccounts = loginHandler.AccountManager.GetAccounts().ToList();
        if (loginHandler.CacheRecoveryNotice is { } recovery && !_accountCacheRecoveryShown)
        {
            _accountCacheRecoveryShown = true;
            Log("[AUTH] " + recovery);
        }
        var allAccountNames = new List<string>();

        foreach (var acc in msAccounts)
        {
            string? u = (acc as CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount)?.Profile?.Username;
            if (u != null)
                allAccountNames.Add(u);
        }
        foreach (var off in settings.OfflineAccounts)
        {
            if (!allAccountNames.Contains(off))
                allAccountNames.Add(off);
        }

        // Apply order
        allAccountNames = allAccountNames.OrderBy(name => {
            int idx = settings.AccountOrder.IndexOf(name);
            return idx == -1 ? 999 : idx;
        }).ToList();

        // Update AccountOrder settings array to match
        settings.AccountOrder = allAccountNames.ToList();

        if (string.IsNullOrEmpty(_selectedAccountInternal) && !string.IsNullOrEmpty(settings.MainAccount))
        {
            _selectedAccountInternal = settings.MainAccount;
        }

        if (allAccountNames.Count > 0)
        {
            if (string.IsNullOrEmpty(_selectedAccount) || !allAccountNames.Contains(_selectedAccount))
            {
                _selectedAccount = allAccountNames[0];
            }
            MiniAccountName.Text = _selectedAccount;
            _ = LoadPlayerSkin(_selectedAccount);
        }
        else
        {
            _selectedAccount = "";
            MiniAccountName.Text = "Offline User";
        }

        // Listing accounts is local. Only launch or explicit refresh authenticates.
        RenderAccountsList(allAccountNames);
        PopulateLaunchSelector(allAccountNames);

        // Update Launch button context menu
        var launchMenu = this.FindControl<ContextMenu>("LaunchContextMenu") ?? LaunchContextMenu;
        if (launchMenu != null)
        {
            var items = new System.Collections.Generic.List<object>();
            var header = new MenuItem { Header = "Launch with account:", IsEnabled = false };
            items.Add(header);
            items.Add(new Separator());

            foreach (var accName in allAccountNames)
            {
                var item = new MenuItem { Header = accName }.Untranslated();
                if (accName == _selectedAccount)
                {
                    item.Icon = "✓";
                }
                
                item.Click += async (s, e) =>
                {
                    // Select this account first
                    _launchAccountOverride = "";
                    _selectedAccount = accName;
                    PopulateLaunchSelector(GetAccountSummaries().Select(a => a.username).ToList());
                    MiniAccountName.Text = accName;
                    await LoadPlayerSkin(accName);
                    await WriteLadsProfileAsync(accName);
                    Log($"[Auth] Switched account via launch menu to: {accName}");
                    
                    // Trigger launch
                    try
                    {
                        LaunchButton.IsEnabled = false;
                        await LaunchGame();
                    }
                    catch (Exception ex)
                    {
                        Log($"[CRASH] {ex.Message}");
                    }
                    finally
                    {
                        LaunchButton.IsEnabled = true;
                        GameLaunchOverlay.IsVisible = false;
                    }
                };
                items.Add(item);
            }
            launchMenu.Items.Clear();
            foreach (var it in items)
            {
                launchMenu.Items.Add(it);
            }
        }
    }

    // Fills the alt-account launch selector. The main account (_selectedAccount) is shown
    // selected by default; choosing any other entry sets _launchAccountOverride so the next
    // launch uses it WITHOUT changing the main account.
    private void PopulateLaunchSelector(List<string> allAccountNames)
    {
        if (LaunchAccountSelector == null) return;
        _populatingLaunchSelector = true;
        try
        {
            LaunchAccountSelector.Items.Clear();
            foreach (var name in allAccountNames)
            {
                bool isOffline = settings.OfflineAccounts.Contains(name);
                var choice = new TheLadsLauncher.Models.AccountChoice(name, name + (isOffline ? "  (Offline)" : "  (MS)"));
                LaunchAccountSelector.Items.Add(choice);
                _ = LoadAccountHeadAsync(choice);
            }

            // Keep an existing override selected if it still exists; otherwise default to main.
            string target = !string.IsNullOrEmpty(_launchAccountOverride) && allAccountNames.Contains(_launchAccountOverride)
                ? _launchAccountOverride
                : _selectedAccount;
            if (string.IsNullOrEmpty(_launchAccountOverride) || !allAccountNames.Contains(_launchAccountOverride))
                _launchAccountOverride = "";

            LaunchAccountSelector.SelectedItem = LaunchAccountSelector.Items
                .OfType<TheLadsLauncher.Models.AccountChoice>().FirstOrDefault(c => c.Name == target);
        }
        finally
        {
            _populatingLaunchSelector = false;
        }
    }

    // Skin heads by account name, so re-filling the picker does not download them again.
    private readonly Dictionary<string, Bitmap> _accountHeads = new();

    private async Task LoadAccountHeadAsync(TheLadsLauncher.Models.AccountChoice choice)
    {
        if (_accountHeads.TryGetValue(choice.Name, out var cached)) { choice.Head = cached; return; }
        try
        {
            var bytes = await _httpClient.GetByteArrayAsync($"https://mc-heads.net/avatar/{Uri.EscapeDataString(ResolveSkinId(choice.Name))}/64");
            using var ms = new MemoryStream(bytes);
            choice.Head = _accountHeads[choice.Name] = new Bitmap(ms);
        }
        catch
        {
            // Offline or no such player: the picker keeps the grey placeholder.
        }
    }

    private void LaunchAccountSelector_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (_populatingLaunchSelector) return;
        if (LaunchAccountSelector?.SelectedItem is TheLadsLauncher.Models.AccountChoice { Name: var name })
        {
            // Only treat it as an override when it differs from the main account.
            _launchAccountOverride = (name == _selectedAccount) ? "" : name;
            Log(string.IsNullOrEmpty(_launchAccountOverride)
                ? "[Launcher] Launch account set to main account."
                : $"[Launcher] Next launch will use alt account: {_launchAccountOverride} (main unchanged).");
        }
    }

    private void RenderAccountsList(List<string> allAccountNames)
    {
        AccountsListContainer.Children.Clear();
        bool registrationReady = Guid.TryParse(settings.MicrosoftClientId, out var clientId) && clientId != Guid.Empty;
        AccountsSignInStatus.Text = registrationReady
            ? "Microsoft sign-in and session refresh happen inside The Lads Client. Your default account is used at launch."
            : "Microsoft sign-in needs this launcher's application setup. Local development accounts are ready to use.";
        if (allAccountNames.Count == 0)
            AccountsListContainer.Children.Add(new TextBlock
            {
                Text = "No accounts added yet. Add a Microsoft account, or a local account for development.",
                Foreground = new SolidColorBrush(Color.Parse("#9AA7B8")), FontSize = 13,
                TextWrapping = TextWrapping.Wrap, Margin = new Thickness(4, 16)
            });

        for (int i = 0; i < allAccountNames.Count; i++)
        {
            string username = allAccountNames[i];
            bool isOffline = !loginHandler.AccountManager.GetAccounts().OfType<CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount>().Any(a => string.Equals(a.Profile?.Username, username, StringComparison.OrdinalIgnoreCase)) && settings.OfflineAccounts.Contains(username, StringComparer.OrdinalIgnoreCase);
            bool isActive = string.Equals(_selectedAccount, username, StringComparison.OrdinalIgnoreCase);

            var border = new Border
            {
                Background = new SolidColorBrush(Color.Parse(isActive ? "#28262A" : "#1D1E22")),
                BorderBrush = new SolidColorBrush(Color.Parse(isActive ? "#C44343" : "#303137")),
                BorderThickness = new Thickness(isActive ? 1.5 : 1),
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(12, 10),
                Margin = new Thickness(0, 0, 0, 6)
            };

            var grid = new Grid
            {
                ColumnDefinitions = ColumnDefinitions.Parse("Auto,*"),
                RowDefinitions = RowDefinitions.Parse("Auto,Auto")
            };
            var actions = new WrapPanel
            {
                Orientation = Avalonia.Layout.Orientation.Horizontal,
                HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Right,
                Margin = new Thickness(0, 10, 0, 0)
            };
            Grid.SetRow(actions, 1);
            Grid.SetColumnSpan(actions, 2);
            grid.Children.Add(actions);

            // Avatar Head
            var skinHead = new Avalonia.Controls.Shapes.Ellipse
            {
                Width = 28, Height = 28,
                Fill = new SolidColorBrush(Color.Parse("#202125")),
                Margin = new Thickness(0, 0, 10, 0),
                VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center
            };
            Grid.SetColumn(skinHead, 0);
            grid.Children.Add(skinHead);

            _ = Task.Run(async () =>
            {
                try
                {
                    string headUrl = $"https://mc-heads.net/avatar/{Uri.EscapeDataString(ResolveSkinId(username))}/28";
                    var headBytes = await _httpClient.GetByteArrayAsync(headUrl);
                    await Dispatcher.UIThread.InvokeAsync(() =>
                    {
                        try
                        {
                            using (var ms = new MemoryStream(headBytes))
                            {
                                var headBmp = new Bitmap(ms);
                                skinHead.Fill = new ImageBrush(headBmp);
                            }
                        }
                        catch { }
                    });
                }
                catch { }
            });

            // Account Name & Badges Stack
            var infoStack = new StackPanel
            {
                VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center,
                Spacing = 3
            };

            var nameText = new TextBlock
            {
                Text = username,
                Foreground = Brushes.White,
                FontSize = 14,
                FontWeight = FontWeight.Bold,
                TextWrapping = TextWrapping.Wrap
            }.Untranslated();
            infoStack.Children.Add(nameText);

            var badgeRow = new StackPanel
            {
                Orientation = Avalonia.Layout.Orientation.Horizontal,
                Spacing = 6
            };

            // Type Badge
            var typeBadge = new Border
            {
                Background = new SolidColorBrush(Color.Parse(isOffline ? "#B8860B" : "#107C41")),
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(6, 1)
            };
            typeBadge.Child = new TextBlock
            {
                Text = isOffline ? "Offline" : "Microsoft",
                Foreground = Brushes.White,
                FontSize = 10,
                FontWeight = FontWeight.SemiBold
            };
            badgeRow.Children.Add(typeBadge);

            // Active Badge
            if (isActive)
            {
                var activeBadge = new Border
                {
                    Background = new SolidColorBrush(Color.Parse("#1A3A2A")),
                    BorderBrush = new SolidColorBrush(Color.Parse("#00FF88")),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(4),
                    Padding = new Thickness(6, 1)
                };
                activeBadge.Child = new TextBlock
                {
                    Text = "Default",
                    Foreground = new SolidColorBrush(Color.Parse("#00FF88")),
                    FontSize = 10,
                    FontWeight = FontWeight.Bold
                };
                badgeRow.Children.Add(activeBadge);
            }

            infoStack.Children.Add(badgeRow);
            infoStack.Children.Add(new TextBlock
            {
                Text = _accountNotices.TryGetValue(username, out var notice) ? notice
                    : isOffline ? "Local play only" : "Session checked at launch",
                Foreground = new SolidColorBrush(Color.Parse("#91A0B4")), FontSize = 10,
                TextWrapping = TextWrapping.Wrap
            });
            Grid.SetColumn(infoStack, 1);
            grid.Children.Add(infoStack);

            // Set Active Button
            if (!isActive)
            {
                var activeBtn = new Button
                {
                    Content = "Set default",
                    Classes = { "action" },
                    Height = 28, FontSize = 11,
                    FontWeight = FontWeight.SemiBold,
                    Margin = new Thickness(4, 0),
                    VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center
                };
                activeBtn.Click += async (s, e) => {
                    if (_addingAccount || _launching) return;
                    _selectedAccount = username;
                    _launchAccountOverride = "";
                    MiniAccountName.Text = username;
                    await LoadPlayerSkin(username);
                    _ = WriteLadsProfileAsync(username);
                    Log($"[Auth] Default account set: {username}");
                    LoadAccounts();
                };
                actions.Children.Add(activeBtn);
            }

            // Refresh Button
            var refreshBtn = new Button
            {
                Content = "",
                Classes = { "action" },
                Height = 28, Width = 28,
                FontSize = 11,
                Margin = new Thickness(2, 0),
                VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center
            };
            ToolTip.SetTip(refreshBtn, isOffline ? "Reload skin" : "Refresh session; right-click to sign in again");
            refreshBtn.Click += async (s, e) => await RefreshSavedAccountAsync(username);
            if (!isOffline)
            {
                var signInAgain = new MenuItem { Header = "Sign in again…" };
                signInAgain.Click += async (_, _) => await RefreshSavedAccountAsync(username, true);
                refreshBtn.ContextMenu = new ContextMenu();
                refreshBtn.ContextMenu.Items.Add(signInAgain);
            }
            actions.Children.Add(refreshBtn);

            // Reorder buttons (Up/Down)
            var upBtn = new Button { Content = "▲", Classes = { "action" }, Height = 28, Width = 28, FontSize = 10, Margin = new Thickness(2, 0), VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center };
            var downBtn = new Button { Content = "▼", Classes = { "action" }, Height = 28, Width = 28, FontSize = 10, Margin = new Thickness(2, 0), VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center };
            ToolTip.SetTip(upBtn, "Move account up");
            ToolTip.SetTip(downBtn, "Move account down");
            upBtn.IsEnabled = i > 0;
            downBtn.IsEnabled = i < allAccountNames.Count - 1;

            int currentIndex = i;
            upBtn.Click += (s, e) => {
                if (currentIndex > 0) {
                    string temp = settings.AccountOrder[currentIndex];
                    settings.AccountOrder[currentIndex] = settings.AccountOrder[currentIndex - 1];
                    settings.AccountOrder[currentIndex - 1] = temp;
                    settings.Save();
                    LoadAccounts();
                }
            };
            downBtn.Click += (s, e) => {
                if (currentIndex < allAccountNames.Count - 1) {
                    string temp = settings.AccountOrder[currentIndex];
                    settings.AccountOrder[currentIndex] = settings.AccountOrder[currentIndex + 1];
                    settings.AccountOrder[currentIndex + 1] = temp;
                    settings.Save();
                    LoadAccounts();
                }
            };

            var orderPanel = new StackPanel
            {
                Orientation = Avalonia.Layout.Orientation.Horizontal,
                VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center
            };
            orderPanel.Children.Add(upBtn);
            orderPanel.Children.Add(downBtn);
            actions.Children.Add(orderPanel);

            // Delete Button
            var delBtn = new Button
            {
                Content = "✕",
                Classes = { "danger" },
                Height = 28, Width = 28,
                FontSize = 10,
                Margin = new Thickness(4, 0),
                VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center
            };
            ToolTip.SetTip(delBtn, "Remove this account");
            delBtn.Click += async (s, e) => {
                await RemoveSpecificAccount(username);
            };
            actions.Children.Add(delBtn);

            border.Child = grid;
            AccountsListContainer.Children.Add(border);
        }
    }

    private async Task RefreshSavedAccountAsync(string username, bool signInAgain = false)
    {
        if (_addingAccount || _launching || _authCts != null) return;
        var account = loginHandler.AccountManager.GetAccounts()
            .OfType<CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount>()
            .FirstOrDefault(a => string.Equals(a.Profile?.Username, username, StringComparison.OrdinalIgnoreCase));
        if (account != null && (!Guid.TryParse(settings.MicrosoftClientId, out var clientId) || clientId == Guid.Empty))
        {
            _accountNotices[username] = "Microsoft application setup required";
            LoadAccounts();
            StatusText.Text = "The launcher owner must configure The Lads Client's Microsoft application ID in Settings.";
            return;
        }
        if (account == null && !settings.OfflineAccounts.Contains(username, StringComparer.OrdinalIgnoreCase)) return;
        _addingAccount = true;
        _authCts = new CancellationTokenSource(TimeSpan.FromMinutes(16));
        _accountNotices[username] = signInAgain ? "Waiting for Microsoft sign-in…" : "Refreshing…";
        LoadAccounts();
        try
        {
            string updatedName = username;
            if (account != null)
            {
                var session = signInAgain
                    ? await loginHandler.AuthenticateInteractively(account, _authCts.Token)
                    : await loginHandler.RefreshOrSignInAsync(account, _authCts.Token);
                updatedName = session.Username!;
                // Minecraft names can change while the account UUID stays the same.
                if (string.Equals(_selectedAccount, username, StringComparison.OrdinalIgnoreCase)) _selectedAccount = updatedName;
                if (string.Equals(_launchAccountOverride, username, StringComparison.OrdinalIgnoreCase)) _launchAccountOverride = updatedName;
                int index = settings.AccountOrder.FindIndex(name => string.Equals(name, username, StringComparison.OrdinalIgnoreCase));
                if (index >= 0) settings.AccountOrder[index] = updatedName;
                _accountNotices.Remove(username);
                _accountNotices[updatedName] = "Minecraft session verified";
                settings.Save();
            }
            else _accountNotices[username] = "Local play only";
            await LoadPlayerSkin(updatedName);
            StatusText.Text = account == null ? $"Skin reloaded for {updatedName}." : $"Minecraft session refreshed for {updatedName}.";
        }
        catch (Exception error)
        {
            var detail = MicrosoftAccountService.DescribeError(error);
            _accountNotices[username] = detail;
            StatusText.Text = detail;
            Log("[Auth] " + detail);
        }
        finally
        {
            _msalProvider?.CompleteDialog();
            _authCts?.Dispose();
            _authCts = null;
            _addingAccount = false;
            LoadAccounts();
        }
    }

    private async Task RemoveSpecificAccount(string username)
    {
        if (string.IsNullOrWhiteSpace(username) || _addingAccount || _launching) return;

        bool isOffline = !loginHandler.AccountManager.GetAccounts().OfType<CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount>().Any(a => string.Equals(a.Profile?.Username, username, StringComparison.OrdinalIgnoreCase)) && settings.OfflineAccounts.Contains(username, StringComparer.OrdinalIgnoreCase);
        if (isOffline)
        {
            settings.OfflineAccounts.Remove(username);
        }
        else
        {
            try
            {
                var msAcc = loginHandler.AccountManager.GetAccounts()
                    .FirstOrDefault(a => (a as CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount)?.Profile?.Username == username);
                if (msAcc != null)
                {
                    await loginHandler.Signout(msAcc);
                }
            }
            catch (Exception ex)
            {
                StatusText.Text = "The account could not be removed. Try again.";
                Log($"[Auth] Account removal failed: {MicrosoftAccountService.DescribeError(ex)}");
                return;
            }
        }

        settings.AccountOrder.Remove(username);
        _accountNotices.Remove(username);
        if (string.Equals(_launchAccountOverride, username, StringComparison.OrdinalIgnoreCase)) _launchAccountOverride = "";
        if (_selectedAccount == username)
        {
            _selectedAccount = "";
        }
        settings.Save();
        LoadAccounts();
        StatusText.Text = $"Account removed: {username}";
        Log($"[Auth] Removed account: {username}");
    }

    // ═══════════════════════════════════════
    //  SHARED CONTENT (worlds, packs, servers)
    // ═══════════════════════════════════════

    /// <summary>What the Home folder buttons open: the shared folders every version uses (also written by --preview-shared).</summary>
    private static string SharedFolderTarget(string folder) => folder switch
    {
        "saves" => SharedContentService.Instance.SavesDirectory,
        "resourcepacks" => SharedContentService.Instance.ResourcePacksDirectory,
        "shaderpacks" => SharedContentService.Instance.ShaderPacksDirectory,
        "screenshots" => SharedContentService.Instance.ScreenshotsDirectory,
        _ => throw new ArgumentOutOfRangeException(nameof(folder), folder, "Not a shared folder.")
    };

    private void OpenSharedFolder_Click(object? sender, RoutedEventArgs e)
    {
        if ((sender as Control)?.Tag is not string folder) return;
        var error = OpenFolderCreatingIt(SharedFolderTarget(folder));
        if (error != null) StatusText.Text = error;
    }

    /// <summary>Creates the folder when missing and opens it in Explorer. Returns the error to show, or null.</summary>
    private string? OpenFolderCreatingIt(string dir)
    {
        try
        {
            Directory.CreateDirectory(dir);
            Process.Start(new ProcessStartInfo { FileName = dir, UseShellExecute = true });
            Log($"[Launcher] Opened folder: {dir}");
            return null;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.ComponentModel.Win32Exception or InvalidOperationException)
        {
            Log($"[Launcher] Could not open '{dir}': {ex.Message}");
            return $"Could not open '{dir}': {ex.Message}";
        }
    }

    /// <summary>
    /// Code-built dialog in the style of the 'Game Already Running' window. With <paramref name="confirmText"/> it asks and
    /// returns true when that button was pressed; without it, it only informs.
    /// </summary>
    private async Task<bool> ShowLadsDialogAsync(string title, string message, string? confirmText = null, string cancelText = "OK",
        bool danger = false, bool confirmEnabled = true) =>
        await ShowLadsChoiceAsync(title, message, confirmText == null ? Array.Empty<(string, bool, bool)>()
            : new[] { (confirmText, danger, confirmEnabled) }, cancelText) == 0;

    /// <summary>
    /// The same dialog with one button per choice (a disabled choice cannot be pressed) and a cancel button. Returns the
    /// index of the pressed choice, or -1 for cancel, closing the window, or a hidden (tray) window.
    /// </summary>
    private async Task<int> ShowLadsChoiceAsync(string title, string message, IReadOnlyList<(string Text, bool Danger, bool Enabled)> choices,
        string cancelText = "Cancel")
    {
        if (!IsVisible)
        {
            // A hidden (tray) window cannot own a dialog; the message still reaches the log and the status line.
            Log($"[Launcher] {title}: {message}");
            StatusText.Text = message;
            return -1;
        }
        return await CreateLadsDialog(title, message, choices, cancelText).ShowDialog<int?>(this) ?? -1;
    }

    private static Window CreateLadsDialog(string title, string message, IReadOnlyList<(string Text, bool Danger, bool Enabled)> choices, string cancelText)
    {
        var dialog = new Window
        {
            Title = title,
            Width = choices.Count > 1 ? 600 : 480, SizeToContent = SizeToContent.Height,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Background = new SolidColorBrush(Color.Parse("#17181B")),
            CanResize = false
        };
        var panel = new StackPanel { Margin = new Thickness(20), Spacing = 12 };
        panel.Children.Add(new TextBlock { Text = title, Foreground = Brushes.Orange, FontSize = 16, FontWeight = FontWeight.Bold, TextWrapping = TextWrapping.Wrap });
        panel.Children.Add(new ScrollViewer { MaxHeight = 420, Content = new TextBlock { Text = message, TextWrapping = TextWrapping.Wrap, Foreground = Brushes.White, FontSize = 12, Margin = new Thickness(0, 0, 14, 0) } });
        var buttons = new WrapPanel { HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Right };
        for (int i = 0; i < choices.Count; i++)
        {
            int index = i;
            var confirm = new Button
            {
                Content = new TextBlock { Text = choices[i].Text, TextWrapping = TextWrapping.Wrap, MaxWidth = 300 },
                Classes = { choices[i].Danger ? "danger" : "launch" }, MinHeight = 32, Padding = new Thickness(14, 6),
                VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center, IsEnabled = choices[i].Enabled, Margin = new Thickness(0, 0, 8, 0)
            };
            confirm.Click += (_, _) => dialog.Close(index);
            buttons.Children.Add(confirm);
        }
        var cancel = new Button { Content = cancelText, MinWidth = 80, Height = 32, HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center, VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center };
        cancel.Click += (_, _) => dialog.Close(-1);
        buttons.Children.Add(cancel);
        panel.Children.Add(buttons);
        dialog.Content = panel;
        return dialog;
    }

    // Per game folder: the last prepare report that needs the user's attention (renamed, waiting, warned or kept aside).
    private readonly Dictionary<string, (string Profile, SharedContentReport Report)> _sharedNotices = new(StringComparer.OrdinalIgnoreCase);

    private static bool NeedsNotice(SharedContentReport report) =>
        report.Renamed > 0 || report.Pending > 0 || report.Warnings.Count > 0 || report.BackupPath != null;

    /// <summary>Logs a prepare report and keeps the persistent notice (Home + Profiles) in step with it.</summary>
    private void RecordSharedReport(string gameDirectory, string profileName, SharedContentReport report)
    {
        foreach (var message in report.Messages) Log($"[Shared] {profileName}: {message}");
        foreach (var warning in report.Warnings) Log($"[Shared WARNING] {profileName}: {warning}");
        var key = Path.TrimEndingDirectorySeparator(Path.GetFullPath(gameDirectory));
        if (NeedsNotice(report)) _sharedNotices[key] = (profileName, report);
        else if (!report.Skipped) _sharedNotices.Remove(key); // a skipped run (game running) says nothing new
        RenderSharedNotices();
    }

    private void RecordSharedReports(IReadOnlyDictionary<string, SharedContentReport> reports)
    {
        var names = _profileService.GetProfiles().ToDictionary(
            p => Path.TrimEndingDirectorySeparator(Path.GetFullPath(_pathService.GetProfileDirectory(p))), p => p.Name, StringComparer.OrdinalIgnoreCase);
        foreach (var (gameDirectory, report) in reports)
            RecordSharedReport(gameDirectory, names.TryGetValue(gameDirectory, out var name) ? name : gameDirectory, report);
    }

    private void RenderSharedNotices()
    {
        var items = _sharedNotices.Values.ToList();
        HomeSharedNotice.IsVisible = ProfilesSharedNotice.IsVisible = items.Count > 0;
        if (items.Count == 0)
        {
            HomeSharedNotice.Child = ProfilesSharedNotice.Child = null;
            return;
        }
        HomeSharedNotice.Child = BuildSharedNotice(items, detailed: false);
        ProfilesSharedNotice.Child = BuildSharedNotice(items, detailed: true);
    }

    private static string SharedNoticeCounts(IEnumerable<SharedContentReport> reports)
    {
        var list = reports.ToList();
        var parts = new List<string>();
        int renamed = list.Sum(r => r.Renamed), pending = list.Sum(r => r.Pending), warnings = list.Sum(r => r.Warnings.Count);
        if (renamed > 0) parts.Add($"{renamed} renamed (both kept)");
        if (pending > 0) parts.Add($"{pending} waiting to move");
        if (warnings > 0) parts.Add($"{warnings} warning(s)");
        if (list.Any(r => r.BackupPath != null)) parts.Add("profile copies kept as backup");
        return string.Join(" · ", parts);
    }

    private Control BuildSharedNotice(List<(string Profile, SharedContentReport Report)> items, bool detailed)
    {
        var stack = new StackPanel { Spacing = 6 };
        stack.Children.Add(new TextBlock
        {
            Text = $"SHARED WORLDS & PACKS · {SharedNoticeCounts(items.Select(i => i.Report))}",
            Foreground = this.FindResource("LadsAccent") as IBrush ?? Brushes.IndianRed, FontSize = 11, FontWeight = FontWeight.Bold,
            LetterSpacing = 1, TextWrapping = TextWrapping.Wrap
        });
        if (detailed)
        {
            foreach (var (profile, report) in items)
            {
                stack.Children.Add(new TextBlock { Text = $"{profile}: {SharedNoticeCounts(new[] { report })}", Foreground = Brushes.White, FontSize = 13, FontWeight = FontWeight.SemiBold, Margin = new Thickness(0, 4, 0, 0), TextWrapping = TextWrapping.Wrap });
                // Renamed items ("<name> → <new name> (both kept)"), what was kept aside, and every warning with its reason.
                var lines = report.Messages.Where(m => m.Contains(" → ") || m.Contains(" kept in ")).Concat(report.Warnings).ToList();
                foreach (var line in lines.Take(8))
                    stack.Children.Add(new TextBlock { Text = "• " + line, Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")), FontSize = 12, TextWrapping = TextWrapping.Wrap });
                if (lines.Count > 8)
                    stack.Children.Add(new TextBlock { Text = $"…and {lines.Count - 8} more in the report.", Foreground = new SolidColorBrush(Color.Parse("#90929D")), FontSize = 12 });
                stack.Children.Add(SharedNoticeButtons(report, withDismiss: false));
            }
            var footer = new StackPanel { Orientation = Avalonia.Layout.Orientation.Horizontal, Spacing = 6, Margin = new Thickness(0, 4, 0, 0) };
            footer.Children.Add(NoticeButton("Dismiss", () => { _sharedNotices.Clear(); RenderSharedNotices(); }));
            stack.Children.Add(footer);
        }
        else
        {
            // One set of buttons on Home: the first report that has a report file or backup folder to open.
            var report = items.Select(i => i.Report).FirstOrDefault(r => r.ReportPath != null || r.BackupPath != null) ?? items[0].Report;
            stack.Children.Add(new TextBlock { Text = $"{string.Join(", ", items.Select(i => i.Profile))}. Details are on the Profiles page.", Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")), FontSize = 11, TextWrapping = TextWrapping.Wrap });
            stack.Children.Add(SharedNoticeButtons(report, withDismiss: true));
        }
        return stack;
    }

    private StackPanel SharedNoticeButtons(SharedContentReport report, bool withDismiss)
    {
        var row = new StackPanel { Orientation = Avalonia.Layout.Orientation.Horizontal, Spacing = 6 };
        var openReport = NoticeButton("Open report", () => OpenSharedNoticeTarget(report.ReportPath!, isFile: true));
        openReport.IsEnabled = report.ReportPath != null;
        row.Children.Add(openReport);
        row.Children.Add(NoticeButton("Open shared saves", () => OpenSharedNoticeTarget(SharedContentService.Instance.SavesDirectory, isFile: false)));
        var openBackup = NoticeButton("Open backup folder", () => OpenSharedNoticeTarget(report.BackupPath!, isFile: false));
        openBackup.IsEnabled = report.BackupPath != null;
        row.Children.Add(openBackup);
        if (withDismiss) row.Children.Add(NoticeButton("Dismiss", () => { _sharedNotices.Clear(); RenderSharedNotices(); }));
        return row;
    }

    private static Button NoticeButton(string text, Action onClick)
    {
        var button = new Button { Content = text, Classes = { "action" }, Height = 26, FontSize = 11, Padding = new Thickness(10, 0), HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center, VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center };
        button.Click += (_, _) => onClick();
        return button;
    }

    private void OpenSharedNoticeTarget(string path, bool isFile)
    {
        string? error;
        if (isFile)
        {
            try
            {
                // The report is JSON Lines, which has no default program; Notepad always exists.
                Process.Start(new ProcessStartInfo { FileName = "notepad.exe", ArgumentList = { path }, UseShellExecute = false });
                error = null;
            }
            catch (Exception ex) when (ex is System.ComponentModel.Win32Exception or InvalidOperationException)
            {
                error = $"Could not open the report '{path}': {ex.Message}";
                Log($"[Shared] {error}");
            }
        }
        else error = OpenFolderCreatingIt(path);
        if (error != null) StatusText.Text = ProfilesStatusText.Text = error;
    }

    /// <summary>
    /// Startup pass: links and migrates every existing profile folder in the background (profiles whose game is running are
    /// skipped by the service and done at their next launch), then shows the notice when something needs attention.
    /// </summary>
    private async Task<IReadOnlyDictionary<string, SharedContentReport>?> RunStartupSharedContentPassAsync()
    {
        if (_runningProcesses.Keys.Any(IsGameRunning)) return null;
        StatusText.Text = "Checking shared worlds, packs and servers...";
        try
        {
            var progress = new Progress<string>(message => { if (!_launching) StatusText.Text = message; });
            var reports = await Task.Run(() => _profileService.PrepareAllProfilesSharedContentAsync(progress));
            RecordSharedReports(reports);
            // Never redirect silently: an isolated THELADS_DIR or an explicit override shares from somewhere else.
            var redirected = SharedContentService.Instance.RedirectedRootNotice;
            if (redirected != null) Log($"[Shared] {redirected}");
            if (!_launching)
                StatusText.Text = _sharedNotices.Count > 0 ? "Shared worlds & packs need a look: see the notice below." : redirected ?? "Ready to play";
            return reports;
        }
        catch (Exception ex)
        {
            Log($"[Shared ERROR] Startup shared-content check failed: {ex}");
            if (!_launching) StatusText.Text = $"Shared worlds/packs check failed: {ex.Message}. It runs again at launch.";
            return null;
        }
    }

    /// <summary>
    /// --preview-shared &lt;outputDir&gt; (sandbox only; Program.Main refuses it otherwise): runs the startup prepare pass,
    /// captures Home, Profiles, Files and Gallery, writes preview-shared.json (folder-button targets, shared-folder states,
    /// prepare reports) and exits: 0 on success, 1 on failure.
    /// </summary>
    private async Task RunSharedPreviewAsync(string outputDirectory)
    {
        int exitCode = 0;
        var result = new JsonObject();
        try
        {
            Directory.CreateDirectory(outputDirectory);
            var reports = await RunStartupSharedContentPassAsync()
                ?? throw new InvalidOperationException("The startup shared-content pass did not run or failed; see the log.");
            var shots = new JsonArray();
            async Task Capture(string page, string file)
            {
                NavigateTo(page);
                await Task.Delay(700); // layout and row animations settle
                var target = Path.Combine(outputDirectory, file);
                SaveWindowScreenshot(target);
                shots.Add((JsonNode)target);
            }
            await Capture("Home", "home.png");
            LoadProfilesUI();
            await Capture("Profiles", "profiles.png");
            LoadFiles(settings.InstancePath);
            await Capture("Files", "files.png");
            await LoadGalleryAsync();
            await _galleryThumbnails;
            await Capture("Gallery", "gallery.png");

            var shared = SharedContentService.Instance;
            result["sharedRoot"] = shared.Root;
            result["launcherBase"] = _pathService.BaseDirectory;
            result["folderButtons"] = new JsonObject
            {
                ["Game folder"] = settings.InstancePath,
                ["Worlds"] = SharedFolderTarget("saves"),
                ["Resource packs"] = SharedFolderTarget("resourcepacks"),
                ["Shader packs"] = SharedFolderTarget("shaderpacks"),
                ["Screenshots"] = SharedFolderTarget("screenshots"),
                ["Gallery: Open Folder"] = shared.ScreenshotsDirectory
            };
            result["homeStatus"] = StatusText.Text;
            result["notice"] = new JsonObject
            {
                ["homeVisible"] = HomeSharedNotice.IsVisible,
                ["profilesVisible"] = ProfilesSharedNotice.IsVisible,
                ["profilesText"] = new JsonArray(ProfilesSharedNotice.GetLogicalDescendants().OfType<TextBlock>().Select(t => (JsonNode?)t.Text).ToArray())
            };
            result["filesRoot"] = _filesRootDir;
            result["gallery"] = new JsonObject
            {
                ["folder"] = shared.ScreenshotsDirectory, ["shown"] = _galleryShown, ["total"] = _galleryFiles.Count,
                ["loadMoreVisible"] = GalleryLoadMoreBtn.IsVisible, ["status"] = GalleryStatusText.Text
            };
            result["serverPicker"] = new JsonArray(QuickLaunchServerComboBox.Items
                .Select(i => (JsonNode?)(i is ComboBoxItem c ? c.Content?.ToString() : i?.ToString())).ToArray());
            var profiles = new JsonArray();
            foreach (var profile in _profileService.GetProfiles())
            {
                var dir = _pathService.GetProfileDirectory(profile);
                var key = Path.TrimEndingDirectorySeparator(Path.GetFullPath(dir));
                profiles.Add(new JsonObject
                {
                    ["name"] = profile.Name,
                    ["gameDirectory"] = dir,
                    ["sharedFolders"] = JsonSerializer.SerializeToNode(shared.GetStatus(dir)
                        .Select(st => new { st.Name, State = st.State.ToString(), st.ProfilePath, st.SharedPath, st.Detail })),
                    ["prepareReport"] = reports.TryGetValue(key, out var report) ? JsonSerializer.SerializeToNode(report) : null
                });
            }
            result["profiles"] = profiles;
            result["screenshots"] = shots;
        }
        catch (Exception ex)
        {
            exitCode = 1;
            result["error"] = ex.ToString();
            Log($"[Preview] {ex}");
        }
        try
        {
            File.WriteAllText(Path.Combine(outputDirectory, "preview-shared.json"), result.ToJsonString(new JsonSerializerOptions(JsonSerializerOptions.Default) { WriteIndented = true }));
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            Console.Error.WriteLine($"--preview-shared could not write its report to '{outputDirectory}': {ex.Message}");
            exitCode = 1;
        }
        Environment.Exit(exitCode);
    }

    /// <summary>
    /// Pre-launch shared content for this profile. When the folders cannot be shared (e.g. a FAT32 or network game folder),
    /// the user may launch once without sharing (WithoutSharing: that choice, for a second prepare of the same launch);
    /// null means the user cancelled.
    /// </summary>
    private async Task<(SharedContentReport Report, bool WithoutSharing)?> PrepareSharedContentForLaunchAsync(
        TheLadsLauncher.Models.LauncherProfile profile, string gameDirectory, CancellationToken token, bool withoutSharing = false)
    {
        var progress = new Progress<string>(message => GameLaunchStatusText.Text = message);
        SharedContentReport report;
        try
        {
            report = await _profileService.PrepareProfileEnvironmentAsync(profile, progress, token, withoutSharedFolders: withoutSharing);
        }
        catch (SharedContentUnavailableException ex) when (!withoutSharing)
        {
            if (ex.Report != null) RecordSharedReport(gameDirectory, profile.Name, ex.Report);
            Log($"[Shared] {profile.Name}: {ex.Message}");
            bool launchWithoutSharing = await ShowLadsDialogAsync("Shared worlds and packs are unavailable",
                ex.Message + (ex.Report != null && NeedsNotice(ex.Report) ? " What was already done is listed on the Profiles page." : ""),
                SharedContentUnavailableException.LaunchWithoutSharingChoice, "Cancel");
            if (!launchWithoutSharing)
            {
                StatusText.Text = "Launch cancelled: shared worlds and packs are unavailable for this profile.";
                return null;
            }
            report = await _profileService.PrepareProfileEnvironmentAsync(profile, progress, token, withoutSharedFolders: true);
            withoutSharing = true;
        }
        RecordSharedReport(gameDirectory, profile.Name, report);
        return (report, withoutSharing);
    }

    private async void RemoveAccount_Click(object? sender, RoutedEventArgs e)
    {
        if (!string.IsNullOrEmpty(_selectedAccount))
        {
            await RemoveSpecificAccount(_selectedAccount);
        }
        else
        {
            StatusText.Text = "No account selected to remove.";
        }
    }

    private async void ClearCache_Click(object? sender, RoutedEventArgs e)
    {
        if (_addingAccount || _launching) return;
        try
        {
            await loginHandler.ClearAsync();
            _selectedAccount = "";
            LoadAccounts();
            StatusText.Text = "Microsoft accounts signed out. Offline accounts kept.";
        }
        catch (Exception ex) { StatusText.Text = MicrosoftAccountService.DescribeError(ex); }
    }

    private async void AddOfflineAccount_Click(object? sender, RoutedEventArgs e)
    {
        if (_addingAccount || _launching || _authCts != null) return;
        try
        {
            var dialog = new TheLadsLauncher.Views.AddOfflineAccountDialog();
            await dialog.ShowDialog(this);
            string? username = dialog.ResultUsername;
            if (!string.IsNullOrWhiteSpace(username))
            {
                username = AccountIdentity.NormalizeOfflineName(username);
                if (GetAccountSummaries().Any(a => a.type == "microsoft" && string.Equals(a.username, username, StringComparison.OrdinalIgnoreCase)))
                    throw new InvalidOperationException("A Microsoft account already uses that username.");
                if (!settings.OfflineAccounts.Contains(username, StringComparer.OrdinalIgnoreCase))
                {
                    settings.OfflineAccounts.Add(username);
                }
                if (!settings.AccountOrder.Contains(username))
                {
                    settings.AccountOrder.Add(username);
                }
                settings.Save();
                _launchAccountOverride = "";
                _selectedAccount = username;
                LoadAccounts();
                MiniAccountName.Text = username;
                await LoadPlayerSkin(username);
                await WriteLadsProfileAsync(username);
                StatusText.Text = $"Offline account selected: {username}. Ready for local play.";
                Log($"[Auth] Added offline account: {username}");
            }
        }
        catch (Exception ex)
        {
            StatusText.Text = "Failed to add offline account: " + ex.Message;
            Log($"[Auth ERROR] {MicrosoftAccountService.DescribeError(ex)}");
        }
    }

    private void AddMicrosoftAccount_Click(object? sender, RoutedEventArgs e)
    {
        _ = AddNewAccount();
    }

    private async Task AddNewAccount()
    {
        if (_addingAccount || _authCts != null || _launching) return;
        if (!Guid.TryParse(settings.MicrosoftClientId, out var clientId) || clientId == Guid.Empty)
        {
            StatusText.Text = "Configure The Lads Client's Microsoft application ID in Settings before signing in.";
            NavigateTo("Settings");
            return;
        }
        _addingAccount = true;
        try
        {
            _authCts?.Cancel();
            _authCts = new CancellationTokenSource(TimeSpan.FromMinutes(16));
            StatusText.Text = "Initiating Microsoft sign-in...";
            Log("[Auth] Starting Microsoft device code sign-in...");

            var session = await loginHandler.AuthenticateInteractively(cancellationToken: _authCts.Token);

            Log($"[Auth] Microsoft login successful! Username: {session.Username}, UUID: {session.UUID}");
            
            _msalProvider?.CompleteDialog();
            if (!string.IsNullOrEmpty(session.Username))
            {
                _selectedAccount = session.Username;
                MiniAccountName.Text = session.Username;
                _ = LoadPlayerSkin(session.Username);
            }
            LoadAccounts();
            StatusText.Text = $"Account added: {session.Username}!";
            Log($"[Auth] Microsoft account added: {session.Username}");
        }
        catch (OperationCanceledException)
        {
            StatusText.Text = "Microsoft sign-in cancelled.";
            Log("[Auth] Sign-in cancelled by user.");
        }
        catch (Exception ex)
        {
            StatusText.Text = MicrosoftAccountService.DescribeError(ex);
            Log($"[Auth ERROR] {StatusText.Text}");
        }
        finally
        {
            _msalProvider?.CompleteDialog();
            _authCts?.Dispose();
            _authCts = null;
            _addingAccount = false;
        }
    }

    private void ManualLoginCancel_Click(object? sender, Avalonia.Interactivity.RoutedEventArgs e)
    {
        ManualLoginOverlay.IsVisible = false;
        StatusText.Text = "Manual login cancelled.";
    }

    private void ManualLoginSubmit_Click(object? sender, RoutedEventArgs e)
    {
        ManualLoginOverlay.IsVisible = false;
        ManualUrlTextBox.Text = "";
        _ = AddNewAccount();
    }

    // ═══════════════════════════════════════
    //  ACCOUNT SKIN HEADS (the Skins tab is Views/SkinsView)
    // ═══════════════════════════════════════

    // mc-heads renders by username OR uuid. Prefer the account's real UUID (most reliable,
    // reflects the current skin); fall back to the username. NOTE: the legacy "/body/player/<name>"
    // form silently ignores the name and always returns a default Steve — never use it.
    private string ResolveSkinId(string username)
    {
        try
        {
            var msAcc = loginHandler.AccountManager.GetAccounts()
                .FirstOrDefault(a =>
                    (a as CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount)?.Profile?.Username == username)
                as CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount;
            string? uuid = msAcc?.Profile?.UUID;
            if (!string.IsNullOrEmpty(uuid)) return uuid;
        }
        catch { }
        return username;
    }

    private async Task LoadPlayerSkin(string username)
    {
        try
        {
            MiniAccountName.Text = username;

            string skinId = ResolveSkinId(username);

            // Load head for mini icon
            string headUrl = $"https://mc-heads.net/avatar/{Uri.EscapeDataString(skinId)}/24";
            var headBytes = await _httpClient.GetByteArrayAsync(headUrl);
            using (var ms = new MemoryStream(headBytes))
            {
                var headBmp = new Bitmap(ms);
                MiniSkinHead.Fill = new ImageBrush(headBmp);
            }
        }
        catch
        {
            MiniSkinHead.Fill = new SolidColorBrush(Color.Parse("#202125"));
        }
    }

    private IReadOnlyList<AccountSummary> GetAccountSummaries()
    {
        var accounts = loginHandler.AccountManager.GetAccounts()
            .OfType<CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount>()
            .Where(a => !string.IsNullOrWhiteSpace(a.Profile?.Username))
            .Select(a => new AccountSummary(a.Profile!.Username!, a.Profile.UUID ?? "", "microsoft"))
            .ToList();
        foreach (string name in settings.OfflineAccounts)
            if (!accounts.Any(a => string.Equals(a.username, name, StringComparison.OrdinalIgnoreCase)))
                accounts.Add(new AccountSummary(name, AccountIdentity.OfflineUuid(name), "offline"));
        return accounts;
    }

    private Task WriteLadsProfileAsync(string username)
    {
        // Account selection is persisted by _selectedAccount. Launch exports are awaited separately.
        return Task.CompletedTask;
    }

    private Task WriteLaunchAccountFilesAsync(MSession session, bool offline, string gameDirectory) =>
        AccountExportService.WriteLaunchAsync(gameDirectory, session, offline, GetAccountSummaries());

    // ═══════════════════════════════════════
    //  SETTINGS PAGE
    // ═══════════════════════════════════════

    private void LoadSettingsUI()
    {
        MicrosoftClientIdTextBox.Text = settings.MicrosoftClientId;
        ulong totalRamBytes = 8UL * 1024 * 1024 * 1024;
        try { totalRamBytes = (ulong)GC.GetGCMemoryInfo().TotalAvailableMemoryBytes; } catch {}
        int maxRamGb = (int)(totalRamBytes / (1024 * 1024 * 1024));
        if (maxRamGb < 2) maxRamGb = 2;
        RamSlider.Maximum = maxRamGb;

        int currentGb = settings.MaxRamMb / 1024;
        if (currentGb < 1) currentGb = 1;
        if (currentGb > maxRamGb) currentGb = maxRamGb;

        RamSlider.Value = currentGb;
        RamValueText.Text = $"{currentGb} GB";
        RamRecommendText.Text = $"Recommended for this PC ({Math.Round(LauncherSettings.TotalMemoryBytes() / (1024.0 * 1024 * 1024))} GB): {LauncherSettings.RecommendedRamMb(LauncherSettings.TotalMemoryBytes()) / 1024} GB";
        
        RamSlider.PropertyChanged += (s, e) =>
        {
            if (e.Property.Name == "Value")
                RamValueText.Text = $"{(int)RamSlider.Value} GB";
        };

        CloseToTrayCheckbox.IsChecked = settings.CloseToTray;
        KeepLauncherOpenCheckbox.IsChecked = settings.KeepLauncherOpen;
        DiscordPresenceCheckbox.IsChecked = settings.DiscordRichPresence;
        KeepClosedOnExitCheckbox.IsChecked = settings.KeepClosedOnExit;
        FullscreenOnLaunchCheckbox.IsChecked = settings.FullscreenOnLaunch;
        GraphicsRendererSelector.SelectedIndex = GraphicsRenderer.Normalize(settings.GraphicsRenderer) == GraphicsRenderer.OpenGl ? 1 : 0;
        QuickLaunchCheckbox.IsChecked = settings.QuickLaunch;
        AutoLaunchCheckbox.IsChecked = settings.AutoLaunch;
        AutoFixCrashesCheckbox.IsChecked = settings.AutoFixCrashes;
        AutoRelaunchOnCrashCheckbox.IsChecked = settings.AutoRelaunchOnCrash;
        AutoRejoinServerCheckbox.IsChecked = settings.AutoRejoinServer;
        MultiInstanceCheckbox.IsChecked = settings.AllowMultiInstance;
        ParticleCheckbox.IsChecked = settings.ShowParticles;
        // settings.SyncResourcePacksFromGlobal is retired (kept only so old settings.json files load): packs are shared now.
        SyncScreenshotsCheckbox.IsChecked = settings.SyncScreenshotsToGlobal;

        InstancePathBox.Text = settings.InstancePath;
        PackwizPathBox.Text = settings.PackwizPath;
        FabricVersionBox.Text = settings.FabricVersion;
        CurseForgeApiKeyBox.Text = settings.CurseForgeApiKey;
        ModrinthApiUrlBox.Text = settings.ModrinthApiUrl;
        ModrinthApiUrlBox_ModTab.Text = settings.ModrinthApiUrl;
        CurseForgeApiUrlBox.Text = settings.CurseForgeApiUrl;
        CurseForgeApiUrlBox_ModTab.Text = settings.CurseForgeApiUrl;
        CfApiKeyInputBox.Text = settings.CurseForgeApiKey;
        MinecraftVersionOverrideBox.Text = settings.SelectedMinecraftVersionOverride;

        PopulateJavaSelector();

        ThemeSelector.Items.Clear();
        foreach (var theme in LauncherSettings.GetAvailableThemes())
            ThemeSelector.Items.Add(theme);
        ThemeSelector.SelectedItem = settings.Theme;

        // UI Scale
        UiScaleSelector.Items.Clear();
        string[] scales = { "100%", "125%", "150%", "175%", "200%" };
        foreach (var sc in scales)
            UiScaleSelector.Items.Add(sc);
        UiScaleSelector.SelectedItem = settings.UiScale;
    }

    // ── Server list picker ────────────────────────────────────────────────────

    private record ServerListItem(string Display, string? Ip)
    {
        public override string ToString() => Display;
    }

    /// <summary>Fills the picker from the shared server list (every profile uses it). An unreadable list is shown, not fatal.</summary>
    private void PopulateServerList()
    {
        QuickLaunchServerComboBox.Items.Clear();
        QuickLaunchServerComboBox.Items.Add(new ServerListItem("Auto (detect from logs)", null));

        try
        {
            foreach (var s in MinecraftServerListReader.Read())
                QuickLaunchServerComboBox.Items.Add(new ServerListItem($"{s.Name}  ({s.Ip})", s.Ip));
        }
        catch (Exception ex) when (ex is InvalidDataException or IOException or UnauthorizedAccessException or InvalidOperationException)
        {
            Log($"[Settings] The shared server list '{SharedContentService.Instance.ServersFile}' is unreadable: {ex.Message}");
            QuickLaunchServerComboBox.Items.Add(new ComboBoxItem { Content = "Server list unreadable (details in Logs)", IsEnabled = false });
        }

        // Restore saved selection
        if (!string.IsNullOrEmpty(settings.QuickLaunchServerIp))
        {
            foreach (var item in QuickLaunchServerComboBox.Items)
            {
                if (item is ServerListItem sli && sli.Ip == settings.QuickLaunchServerIp)
                {
                    QuickLaunchServerComboBox.SelectedItem = sli;
                    return;
                }
            }
            // Saved IP no longer in server list — add it as a manual entry
            var manual = new ServerListItem($"{settings.QuickLaunchServerIp} (manual)", settings.QuickLaunchServerIp);
            QuickLaunchServerComboBox.Items.Add(manual);
            QuickLaunchServerComboBox.SelectedItem = manual;
        }
        else
        {
            QuickLaunchServerComboBox.SelectedIndex = 0;
        }
    }

    private void RefreshServerList_Click(object? sender, RoutedEventArgs e) => PopulateServerList();

    // ── Java selector ─────────────────────────────────────────────────────────

    private void PopulateJavaSelector()
    {
        JavaSelector.Items.Clear();
        var javaInstalls = settings.DetectJavaInstallations();
        foreach (var path in javaInstalls)
            JavaSelector.Items.Add(path);
        
        if (JavaSelector.Items.Contains(settings.JavaPath))
            JavaSelector.SelectedItem = settings.JavaPath;
        else if (JavaSelector.Items.Count > 0)
            JavaSelector.SelectedIndex = 0;
    }

    private void DetectJava_Click(object? sender, RoutedEventArgs e)
    {
        PopulateJavaSelector();
        Log("[Settings] Detected Java installations refreshed.");
    }

    private async void DownloadJava_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            DownloadJavaBtn.IsEnabled = false;
            JavaDownloadProgress.IsVisible = true;
            JavaDownloadProgress.Value = 0;
            var active = _profileService.GetActiveProfile();
            int verToDownload = active?.JavaMajorVersion ?? 21;
            JavaStatusText.Text = $"Downloading Adoptium JDK {verToDownload}...";

            var progress = new Progress<double>(p =>
            {
                Dispatcher.UIThread.Post(() =>
                {
                    JavaDownloadProgress.Value = p;
                    JavaStatusText.Text = $"Downloading JDK {verToDownload}: {p:F0}%";
                });
            });

            string javaPath = await _javaService.EnsureJavaAsync(verToDownload, progress);

            JavaDownloadProgress.Value = 100;
            JavaStatusText.Text = $"Adoptium JDK {verToDownload} ready!";
            PopulateJavaSelector();
            JavaSelector.SelectedItem = javaPath;
            settings.JavaPath = javaPath;
            settings.Save();
            Log($"[Java] Adoptium JDK {verToDownload} ready at: {javaPath}");
        }
        catch (Exception ex)
        {
            JavaStatusText.Text = $"Download failed: {ex.Message}";
            Log($"[Java ERROR] {ex.Message}");
        }
        finally
        {
            DownloadJavaBtn.IsEnabled = true;
        }
    }

    // ═══════════════════════════════════════
    //  PROFILES MANAGEMENT
    // ═══════════════════════════════════════

    private void PopulateLaunchProfileSelector()
    {
        _populatingProfileSelector = true;
        try
        {
            LaunchProfileSelector.Items.Clear();
            var active = _profileService.GetActiveProfile();
            foreach (var p in ProfileTools.NewestFirst(_profileService.GetProfiles()))
            {
                bool offered = ProfileTools.PlayableVersions.Contains(p.MinecraftVersion);
                var item = new ComboBoxItem { Content = p.ToString(), Tag = p, IsEnabled = offered };
                if (!offered) ToolTip.SetTip(item, $"Minecraft {p.MinecraftVersion} is not offered on the Play screen.");
                LaunchProfileSelector.Items.Add(item);
                if (p.Id == active.Id) LaunchProfileSelector.SelectedItem = item;
            }
            LaunchProfileSelector.SelectedItem ??= LaunchProfileSelector.Items.OfType<ComboBoxItem>().FirstOrDefault(i => i.IsEnabled);
            ApplyProfile(active, false);
        }
        finally
        {
            _populatingProfileSelector = false;
        }
    }

    private void LaunchProfileSelector_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (_populatingProfileSelector) return;
        if (_launching) { PopulateLaunchProfileSelector(); return; }
        if (LaunchProfileSelector.SelectedItem is ComboBoxItem { Tag: TheLadsLauncher.Models.LauncherProfile profile })
        {
            try { GameVersionPolicy.ResolveVersionId(profile); }
            catch (ArgumentException ex)
            {
                StatusText.Text = ex.Message;
                PopulateLaunchProfileSelector();
                return;
            }
            _profileService.SetActiveProfile(profile.Id);
            ApplyProfile(profile, true);
            LoadProfilesUI();
        }
    }

    private void ApplyProfile(TheLadsLauncher.Models.LauncherProfile profile, bool saveSettings = true)
    {
        settings.SelectedMinecraftVersionOverride = profile.MinecraftVersion;
        settings.FabricVersion = GameVersionPolicy.ResolveVersionId(profile);
        if (!string.IsNullOrEmpty(profile.FabricVersion))
        {
            settings.FabricVersion = profile.FabricVersion.StartsWith("fabric-loader-")
                ? profile.FabricVersion
                : $"fabric-loader-{profile.FabricVersion}-{profile.MinecraftVersion}";
        }
        settings.PackwizUrl = profile.PackwizUrl ?? "";

        settings.InstancePath = _pathService.GetProfileDirectory(profile);
        if (!string.IsNullOrEmpty(profile.CustomJavaPath))
            settings.JavaPath = profile.CustomJavaPath;

        if (saveSettings)
            settings.Save();
        ShowProfileSettings();

        UpdateMinecraftVersionDisplay();
        ApplyNextAccountRequest(settings.InstancePath);
        // Everything bound to the previous profile follows the switch.
        PopulateServerList();
        ResetFilesRoot();
        ReloadModsInventory();
    }

    private void LoadProfilesUI()
    {
        ProfilesListContainer.Children.Clear();
        var profiles = ProfileTools.Filter(_profileService.GetProfiles(), ProfileSearch.Text, ProfileVersionFilter.SelectedItem as string).ToList();
        var active = _profileService.GetActiveProfile();
        if (profiles.Count == 0) ProfilesListContainer.Children.Add(new TextBlock { Text = "No profiles match. Clear the search or choose All versions.", Foreground = Brushes.Gray, TextWrapping = TextWrapping.Wrap });

        foreach (var profile in profiles)
        {
            bool isActive = profile.Id == active.Id;

            var card = new Border
            {
                Background = new SolidColorBrush(Color.Parse(isActive ? "#28262A" : "#1D1E22")),
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(16, 12),
                BorderBrush = new SolidColorBrush(Color.Parse(isActive ? "#C44343" : "#303137")),
                BorderThickness = new Thickness(isActive ? 2 : 0, 0, 0, 1),
                Margin = new Thickness(0, 0, 0, 8)
            };

            var grid = new Grid
            {
                ColumnDefinitions = new ColumnDefinitions("*,Auto")
            };

            // Left side details
            var leftStack = new StackPanel { Spacing = 6 };

            var titlePanel = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), Margin = new Thickness(0, 0, 12, 0) };
            titlePanel.Children.Add(new TextBlock
            {
                Text = (profile.IsFavorite ? "★ " : "") + profile.Name,
                TextTrimming = TextTrimming.CharacterEllipsis,
                Foreground = Brushes.White,
                FontSize = 15,
                FontWeight = FontWeight.Bold
            }.Untranslated());

            if (isActive)
            {
                titlePanel.Children.Add(new Border
                {
                    Background = new SolidColorBrush(Color.Parse("#C44343")),
                    CornerRadius = new CornerRadius(4),
                    Padding = new Thickness(6, 2),
                    Child = new TextBlock
                    {
                        Text = "ACTIVE",
                        Foreground = Brushes.White,
                        FontSize = 10,
                        FontWeight = FontWeight.Bold
                    }
                });
                Grid.SetColumn(titlePanel.Children[1], 1);
                titlePanel.Children[1].Margin = new Thickness(10, 0, 0, 0);
            }
            leftStack.Children.Add(titlePanel);

            var metaPanel = new StackPanel { Orientation = Avalonia.Layout.Orientation.Horizontal, Spacing = 14 };
            metaPanel.Children.Add(new TextBlock
            {
                Text = $"Minecraft: {profile.MinecraftVersion}",
                Foreground = new SolidColorBrush(Color.Parse("#AAAAAA")),
                FontSize = 12
            });
            if (!string.IsNullOrEmpty(profile.FabricVersion))
            {
                metaPanel.Children.Add(new TextBlock
                {
                    Text = $"Fabric: {profile.FabricVersion}",
                    Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")),
                    FontSize = 12
                });
            }
            metaPanel.Children.Add(new TextBlock
            {
                Text = $"Java: {profile.JavaMajorVersion}",
                Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")),
                FontSize = 12
            });
            leftStack.Children.Add(metaPanel);

            // Isolate toggle checkbox (a 1.8.9 profile's shared settings come from Lunar Client's 1.8 profile when Lunar is installed)
            bool fromLunar = GameVersionPolicy.UsesForge(profile.MinecraftVersion) && GameOptionsService.LunarOptions18() != null;
            var isolateCheck = new CheckBox
            {
                Content = new TextBlock { Text = fromLunar ? IsolationText + LunarSettingsText : IsolationText, TextWrapping = TextWrapping.Wrap },
                IsChecked = profile.IsIsolated,
                FontSize = 12,
                Foreground = new SolidColorBrush(Color.Parse("#AAAAAA")),
                Margin = new Thickness(0, 4, 0, 0)
            };
            isolateCheck.IsCheckedChanged += (s, e) =>
            {
                profile.IsIsolated = isolateCheck.IsChecked ?? false;
                _profileService.SaveProfiles();
                Log($"[Profiles] Profile '{profile.Name}' isolated set to: {profile.IsIsolated}");
            };
            leftStack.Children.Add(isolateCheck);

            grid.Children.Add(leftStack);
            Grid.SetColumn(leftStack, 0);

            // Right side buttons
            var rightStack = new StackPanel
            {
                Orientation = Avalonia.Layout.Orientation.Horizontal,
                Spacing = 8,
                VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center
            };

            if (!isActive)
            {
                var selectBtn = new Button
                {
                    Content = "Select Profile",
                    Classes = { "action" },
                    Height = 34,
                    Padding = new Thickness(12, 0),
                    FontSize = 12
                };
                selectBtn.Click += (s, e) =>
                {
                    if (_launching) return;
                    try { GameVersionPolicy.ResolveVersionId(profile); }
                    catch (ArgumentException ex) { StatusText.Text = ex.Message; return; }
                    _profileService.SetActiveProfile(profile.Id);
                    ApplyProfile(profile);
                    PopulateLaunchProfileSelector();
                    LoadProfilesUI();
                };
                rightStack.Children.Add(selectBtn);

                var deleteBtn = new Button
                {
                    Content = "Delete",
                    Classes = { "danger" },
                    Height = 34,
                    Padding = new Thickness(10, 0),
                    FontSize = 12
                };
                deleteBtn.Click += (s, e) =>
                {
                    if (_launching) return;
                    _profileService.DeleteProfile(profile.Id);
                    PopulateLaunchProfileSelector();
                    LoadProfilesUI();
                };
                rightStack.Children.Add(deleteBtn);
            }
            else
            {
                var activeBadge = new TextBlock
                {
                    Text = "Selected",
                    Foreground = new SolidColorBrush(Color.Parse("#BABDC6")),
                    FontSize = 12,
                    FontWeight = FontWeight.SemiBold,
                    VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center
                };
                rightStack.Children.Add(activeBadge);
            }

            rightStack.Children.Add(ProfileActions(profile));
            grid.Children.Add(rightStack);
            Grid.SetColumn(rightStack, 1);

            card.Child = grid;
            ProfilesListContainer.Children.Add(card);
        }
    }

    private const string IsolationText =
        "Keep this profile's game settings separate (options.txt, keybinds). Worlds, resource packs, shader packs and servers are always shared from the global .minecraft folder.";
    private const string LunarSettingsText =
        "\nOtherwise its settings come from Lunar Client's 1.8 profile (Lunar's files are never changed).";

    private async void AddProfile_Click(object? sender, RoutedEventArgs e)
    {
        var window = new Window
        {
            Title = "Create New Version Profile",
            Width = 440,
            SizeToContent = SizeToContent.Height,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Background = new SolidColorBrush(Color.Parse("#17181B")),
            CanResize = false
        };

        var panel = new StackPanel { Margin = new Thickness(24), Spacing = 12 };
        panel.Children.Add(new TextBlock
        {
            Text = "New Game Profile",
            Foreground = Brushes.White,
            FontSize = 16,
            FontWeight = FontWeight.Bold
        });

        var nameBox = new TextBox { PlaceholderText = "Profile Name (e.g. My 26.3)", Text = "New Profile", Height = 36 };
        panel.Children.Add(nameBox);

        var versionBox = new TextBox { PlaceholderText = "Minecraft Version (e.g. 26.3, 1.8.9)", Text = "26.3", Height = 36 };
        panel.Children.Add(versionBox);

        var isolateCheck = new CheckBox
        {
            Content = new TextBlock { Text = IsolationText, TextWrapping = TextWrapping.Wrap },
            IsChecked = false,
            Foreground = new SolidColorBrush(Color.Parse("#CCCCCC"))
        };
        panel.Children.Add(isolateCheck);

        var createBtn = new Button
        {
            Content = "Create Profile",
            Classes = { "launch" },
            Height = 38,
            HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Stretch,
            HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            Foreground = Brushes.White,
            FontWeight = FontWeight.Bold
        };
        createBtn.Click += (s, ev) =>
        {
            string name = nameBox.Text?.Trim() ?? "";
            string version = versionBox.Text?.Trim() ?? "26.3";
            if (GameVersionPolicy.IsDropped(version)) { versionBox.Text = ""; versionBox.PlaceholderText = $"Minecraft {version} is no longer supported (1.8.9, 26.2 or 26.3)"; return; }
            if (string.IsNullOrEmpty(name)) name = $"Profile {version}";

            bool forge = GameVersionPolicy.UsesForge(version); // 1.8.9: Forge and Java 8, never Fabric
            int javaVer = forge ? GameVersionPolicy.GetRequiredJavaMajor(version) : version.StartsWith("1.21") ? 21 : (version.StartsWith("26") ? 25 : 21);
            var newProfile = new TheLadsLauncher.Models.LauncherProfile
            {
                Name = name,
                MinecraftVersion = version,
                FabricVersion = forge ? null : "0.16.9",
                JavaMajorVersion = javaVer,
                IsIsolated = isolateCheck.IsChecked ?? false
            };

            _profileService.AddProfile(newProfile);
            PopulateLaunchProfileSelector();
            LoadProfilesUI();
            Log($"[Profiles] Created new profile '{newProfile.Name}' for Minecraft {newProfile.MinecraftVersion}");
            window.Close();
        };

        panel.Children.Add(createBtn);
        window.Content = panel;
        await window.ShowDialog(this);
    }

    /// <summary>Settings sync plus shared folders/server list for the active profile; results go to the same notice as startup.</summary>
    private async void SyncSharedSettings_Click(object? sender, RoutedEventArgs e)
    {
        var active = _profileService.GetActiveProfile();
        string gameDirectory = _pathService.GetProfileDirectory(active);
        SyncSharedBtn.IsEnabled = false;
        void Status(string text) => StatusText.Text = ProfilesStatusText.Text = text;
        try
        {
            var report = await _profileService.PrepareProfileEnvironmentAsync(active, new Progress<string>(Status));
            RecordSharedReport(gameDirectory, active.Name, report);
            Status(report.Skipped ? $"{active.Name}: {report.Messages.FirstOrDefault()}"
                : NeedsNotice(report) ? $"Synced '{active.Name}'. Some items need a look: see the notice."
                : $"'{active.Name}' is up to date: settings synced, worlds, packs and servers shared.");
            Log($"[Profiles] Synced settings and shared folders for '{active.Name}'.");
        }
        catch (SharedContentUnavailableException ex)
        {
            if (ex.Report != null) RecordSharedReport(gameDirectory, active.Name, ex.Report);
            Status($"Shared worlds and packs are unavailable for '{active.Name}'.");
            Log($"[Profiles ERROR] {ex.Message}");
            await ShowLadsDialogAsync("Shared worlds and packs are unavailable", ex.Message);
        }
        catch (Exception ex)
        {
            Status($"Sync failed: {ex.Message}");
            Log($"[Profiles ERROR] Failed to sync '{active.Name}': {ex}");
        }
        finally
        {
            SyncSharedBtn.IsEnabled = true;
        }
    }

    /// <summary>Settings → settings.json, run by the auto-save timer (see InitializeSettingsAutoSave). A field with an invalid
    /// value keeps its saved value and shows the error under itself; every other change is still saved.</summary>
    private void SaveSettingsFromUi()
    {
        if (_launching) { _settingsSaveTimer.Start(); SettingsSaveStatus.Text = "Changes are saved when the launch has finished."; return; }
        bool retry = false;
        string newClientId = MicrosoftClientIdTextBox.Text?.Trim() ?? "";
        if (newClientId.Length == 0) newClientId = LauncherSettings.DefaultMicrosoftClientId;
        string? clientIdError = Guid.TryParse(newClientId, out var parsedId) && parsedId != Guid.Empty ? null
            : "Enter a valid Microsoft application ID, or leave it blank to restore The Lads Client's default.";
        if (clientIdError == null && newClientId != settings.MicrosoftClientId)
        {
            if (_addingAccount)
            {
                clientIdError = "Finish or cancel the current sign-in before changing the application ID.";
                retry = true;
            }
            else
            {
                settings.MicrosoftClientId = newClientId;
                InitializeAuthentication();
            }
        }
        string? fabricError = string.IsNullOrWhiteSpace(FabricVersionBox.Text) ? "Enter a Fabric loader version." : null;
        string? modrinthError = HttpUrlError(ModrinthApiUrlBox.Text, "The Modrinth API URL");
        string? curseForgeError = HttpUrlError(CurseForgeApiUrlBox.Text, "The CurseForge API URL");
        bool valid = ShowFieldError(MicrosoftClientIdTextBox, clientIdError) & ShowFieldError(FabricVersionBox, fabricError)
            & ShowFieldError(ModrinthApiUrlBox, modrinthError) & ShowFieldError(ModrinthApiUrlBox_ModTab, modrinthError)
            & ShowFieldError(CurseForgeApiUrlBox, curseForgeError) & ShowFieldError(CurseForgeApiUrlBox_ModTab, curseForgeError);

        int ramMb = (int)RamSlider.Value * 1024;
        settings.RamChosenByUser |= ramMb != settings.MaxRamMb;
        settings.MaxRamMb = ramMb;
        settings.CloseToTray = CloseToTrayCheckbox.IsChecked ?? true;
        settings.KeepLauncherOpen = KeepLauncherOpenCheckbox.IsChecked ?? false;
        settings.DiscordRichPresence = DiscordPresenceCheckbox.IsChecked ?? true;
        DiscordPresence.Enable(settings.DiscordRichPresence);
        settings.KeepClosedOnExit = KeepClosedOnExitCheckbox.IsChecked ?? false;
        settings.AutoLaunch = AutoLaunchCheckbox.IsChecked ?? false;
        settings.AutoFixCrashes = AutoFixCrashesCheckbox.IsChecked ?? true;
        settings.AutoRelaunchOnCrash = AutoRelaunchOnCrashCheckbox.IsChecked ?? false;
        settings.AutoRejoinServer = AutoRejoinServerCheckbox.IsChecked ?? false;
        if (QuickLaunchServerComboBox.SelectedItem is ServerListItem sli && sli.Ip != null)
            settings.QuickLaunchServerIp = sli.Ip;
        else
            settings.QuickLaunchServerIp = "";
        settings.AllowMultiInstance = MultiInstanceCheckbox.IsChecked ?? false;
        settings.FullscreenOnLaunch = FullscreenOnLaunchCheckbox.IsChecked ?? true;
        var renderer = GraphicsRendererSelector.SelectedIndex == 1 ? GraphicsRenderer.OpenGl : GraphicsRenderer.Vulkan;
        bool rendererChanged = renderer != GraphicsRenderer.Normalize(settings.GraphicsRenderer);
        settings.GraphicsRenderer = renderer;
        settings.QuickLaunch = QuickLaunchCheckbox.IsChecked ?? false;
        settings.ShowParticles = ParticleCheckbox.IsChecked ?? true;
        settings.SyncScreenshotsToGlobal = SyncScreenshotsCheckbox.IsChecked ?? true;
        if (fabricError == null) settings.FabricVersion = FabricVersionBox.Text!.Trim();
        settings.CurseForgeApiKey = CurseForgeApiKeyBox.Text ?? "";
        if (modrinthError == null) settings.ModrinthApiUrl = ModrinthApiUrlBox.Text!.Trim();
        if (curseForgeError == null) settings.CurseForgeApiUrl = CurseForgeApiUrlBox.Text!.Trim();
        settings.SelectedMinecraftVersionOverride = MinecraftVersionOverrideBox.Text ?? settings.SelectedMinecraftVersionOverride;
        
        if (JavaSelector.SelectedItem is string javaPath)
            settings.JavaPath = javaPath;
        
        if (ThemeSelector.SelectedItem is string theme)
            settings.Theme = theme;

        if (UiScaleSelector.SelectedItem is string scale)
        {
            settings.UiScale = scale;
            ApplyUiScale();
        }

        settings.Save();
        if (rendererChanged) ReloadModsInventory(); // Iris's "Requires OpenGL" lock follows the saved renderer
        UpdateMinecraftVersionDisplay();
        ApplyTheme();

        if (settings.ShowParticles && !settings.ReducedMotion)
        {
            if (!_meshControlAdded && _meshControl != null)
            {
                ParticleCanvas.Children.Clear();
                ParticleCanvas.Children.Add(_meshControl);
                _meshControlAdded = true;
            }
            if (!_particleTimer.IsEnabled)
                _particleTimer.Start();
        }
        else
        {
            if (_particleTimer.IsEnabled)
                _particleTimer.Stop();
            ParticleCanvas.Children.Clear();
            _meshControlAdded = false;
        }

        if (retry) _settingsSaveTimer.Start();
        SettingsSaveStatus.Text = valid ? "All changes saved" : "Fix the highlighted fields; everything else is saved.";
        SettingsSaveStatus.Foreground = Brush.Parse(valid ? "#A4BAA7" : "#E27676");
        Log("[Settings] Configuration saved.");
    }

    // ═══════════════════════════════════════
    //  MODS BROWSER
    // ═══════════════════════════════════════

    private string ResolveMinecraftVersion()
    {
        return ResolveMinecraftVersionInternal(settings.FabricVersion);
    }

    // Extracts the Minecraft version from a version id, supporting both legacy (1.20.1)
    // and modern (26.1.2) version formats.
    // e.g. "fabric-loader-0.19.2-26.1.2" -> "26.1.2", "26.3" -> "26.3"
    private static string? ExtractMcVersionFromId(string versionId)
    {
        // Loader-style ids: the MC version is the part after the loader version
        var m = Regex.Match(versionId, @"(?:fabric|quilt|forge|neoforge)-loader-[\d\.]+-(\d+\.\d+(?:\.\d+)?)$", RegexOptions.IgnoreCase);
        if (m.Success) return m.Groups[1].Value;

        // Plain version id
        if (Regex.IsMatch(versionId, @"^\d+\.\d+(?:\.\d+)?$")) return versionId;

        // Otherwise take the LAST version-looking token (avoids grabbing loader versions)
        var all = Regex.Matches(versionId, @"\d+\.\d+(?:\.\d+)?");
        if (all.Count > 0) return all[all.Count - 1].Value;

        return null;
    }

    private string ResolveMinecraftVersionInternal(string versionId)
    {
        try
        {
            string versionJsonPath = Path.Combine(settings.InstancePath, "versions", versionId, versionId + ".json");
            if (!File.Exists(versionJsonPath))
            {
                return ExtractMcVersionFromId(versionId) ?? versionId;
            }

            string jsonContent = File.ReadAllText(versionJsonPath);
            using (JsonDocument doc = JsonDocument.Parse(jsonContent))
            {
                var root = doc.RootElement;

                // 1. Recurse if inheritsFrom is present
                if (root.TryGetProperty("inheritsFrom", out JsonElement inheritsFromProp))
                {
                    string inheritedVersion = inheritsFromProp.GetString() ?? "";
                    if (!string.IsNullOrEmpty(inheritedVersion))
                    {
                        return ResolveMinecraftVersionInternal(inheritedVersion);
                    }
                }

                // 2. Direct Match (e.g. 1.20.1 or 26.1.2)
                if (Regex.IsMatch(versionId, @"^\d+\.\d+(?:\.\d+)?$"))
                {
                    return versionId;
                }

                // 3. Extract from logging xml id (e.g., client-1.21.2.xml)
                if (root.TryGetProperty("logging", out JsonElement loggingProp) &&
                    loggingProp.TryGetProperty("client", out JsonElement clientProp) &&
                    clientProp.TryGetProperty("file", out JsonElement fileProp) &&
                    fileProp.TryGetProperty("id", out JsonElement idProp))
                {
                    string logId = idProp.GetString() ?? "";
                    var match = Regex.Match(logId, @"\d+\.\d+(?:\.\d+)?");
                    if (match.Success)
                        return match.Value;
                }

                // 4. Extract from downloads URL
                if (root.TryGetProperty("downloads", out JsonElement downloadsProp) &&
                    downloadsProp.TryGetProperty("client", out JsonElement clientDlProp) &&
                    clientDlProp.TryGetProperty("url", out JsonElement urlProp))
                {
                    string url = urlProp.GetString() ?? "";
                    var match = Regex.Match(url, @"1\.\d+(?:\.\d+)?");
                    if (match.Success)
                        return match.Value;
                }

                // Fallback: extract version number from the ID itself
                var selfExtracted = ExtractMcVersionFromId(versionId);
                if (selfExtracted != null)
                    return selfExtracted;
            }
        }
        catch (Exception ex)
        {
            Log($"[Version Resolve Warning] {ex.Message}");
        }

        return "Unknown";
    }

    private void UpdateMinecraftVersionDisplay()
    {
        string mcVersion = string.IsNullOrEmpty(settings.SelectedMinecraftVersionOverride)
            ? ResolveMinecraftVersion()
            : settings.SelectedMinecraftVersionOverride;
        MinecraftVersionText.Text = $"Minecraft {mcVersion} / Fabric";
        LaunchButton.Content = "Play";
        GameLaunchVersionText.Text = $"Minecraft {mcVersion} · Fabric";
    }

    // ═══════════════════════════════════════
    //  INSTALLED MODS (inventory)
    // ═══════════════════════════════════════

    // Loaded ids come from the running marker (what the game loaded at start); null while the profile's game is not running.
    // Renderer locks follow the saved Settings → Graphics choice (what the next launch applies), not only the last launch.
    private readonly ModInventoryService _modInventoryService = new(gameDirectory => RunningGameMarker.GetRunning(gameDirectory)?.LoadedMods,
        () => LauncherSettings.Load().GraphicsRenderer);
    private readonly ModStateService _modStateService = new(RunningGameMarker.IsRunning);
    private ModInventory? _modInventory;
    // The profile whose list is being built while none is shown (after a profile switch), else null.
    private string? _modsLoadingProfile;
    private int _modsGeneration;
    private string? _modsWatchSignature;
    private bool _modsWatchBusy;
    private bool _modsBusy;
    private bool _resettingModFilters;
    private readonly Dictionary<string, ModInventoryEntry> _selectedMods = new(StringComparer.OrdinalIgnoreCase);
    // The rows the list shows now (filter and search applied): Select all and --preview-mods use exactly these.
    private readonly List<ModRowView> _modRows = new();

    private sealed record ModRowView(ModInventoryEntry Entry, Control Row, Button Toggle, CheckBox? Select, Panel? Children, IReadOnlyList<string> Actions, Image Icon);

    private (string GameDirectory, string MinecraftVersion) ModsTarget()
    {
        var profile = _profileService.GetActiveProfile();
        return (_pathService.GetProfileDirectory(profile), profile.MinecraftVersion);
    }

    /// <summary>
    /// Rebuilds the Mods page (and the Home MODS stat) for the active profile: on page open, profile switch, after every
    /// change, and when the 1 s timer sees a mod file change. The build runs off the UI thread; only the newest one is shown.
    /// </summary>
    private void ReloadModsInventory() => _ = ReloadModsInventoryAsync();

    /// <summary>Returns the inventory this call built (read after any change that preceded it), even when a newer reload
    /// is the one shown; null when it failed or was skipped.</summary>
    private async Task<ModInventory?> ReloadModsInventoryAsync()
    {
        // During a launch the installers rename and replace jars; reading them now would only race that. The 1 s watch
        // reloads as soon as the launch is over (the Mods folder and the running marker have changed by then).
        if (_launching) return null;
        int generation = ++_modsGeneration;
        string gameDirectory = "";
        try
        {
            (gameDirectory, var version) = ModsTarget();
            var directory = gameDirectory;
            if (!ModInventoryView.IsFor(_modInventory, directory))
            {
                // Another profile's rows must not stay on screen (and clickable) while this one's first build hashes every jar.
                _modInventory = null;
                _modsLoadingProfile = _profileService.GetActiveProfile().Name;
                ShowModsNotice(null);
                RenderModsInventory();
            }
            var signature = await Task.Run(() => ModsWatchSignature(directory));
            if (generation == _modsGeneration) _modsWatchSignature = signature;
            var inventory = await _modInventoryService.BuildAsync(AppContext.BaseDirectory, directory, version);
            var preferencesError = await Task.Run(() => ModPreferences.Load(directory).Error);
            if (generation == _modsGeneration)
            {
                _modInventory = inventory;
                _modsLoadingProfile = null;
                ShowModsNotice(preferencesError);
                RenderModsInventory();
            }
            return inventory;
        }
        catch (Exception ex)
        {
            Log($"[Mods ERROR] Could not list the mods of '{gameDirectory}': {ex.Message}");
            if (generation != _modsGeneration) return null;
            _modInventory = null;
            _modsLoadingProfile = null;
            ShowModsNotice($"Could not list this profile's mods: {ex.Message}");
            RenderModsInventory();
            return null;
        }
    }

    /// <summary>Modification times of what the Mods page shows: choices, Lads module settings and list, the Mods folder and
    /// the running marker. No FileSystemWatcher: the 1 s stats timer compares this.</summary>
    private static string ModsWatchSignature(string gameDirectory)
    {
        try
        {
            return string.Join("|", new[] { ModPreferences.FileName, "thelads_config.json", "lads-core-catalog.json", RunningGameMarker.FileName }
                .Select(name => File.GetLastWriteTimeUtc(Path.Combine(gameDirectory, name)).Ticks)
                .Append(Directory.GetLastWriteTimeUtc(Path.Combine(gameDirectory, "mods")).Ticks));
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or ArgumentException)
        {
            return "unreadable: " + ex.Message; // a new state still triggers the reload, which reports the problem
        }
    }

    private async void WatchModFiles(object? sender, EventArgs e)
    {
        if (_modsWatchBusy || _modsWatchSignature == null) return;
        _modsWatchBusy = true;
        try
        {
            var (gameDirectory, _) = ModsTarget();
            var signature = await Task.Run(() => ModsWatchSignature(gameDirectory));
            if (signature != _modsWatchSignature) ReloadModsInventory();
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or ArgumentException or InvalidOperationException)
        {
            Log($"[Mods] Could not check the Mods folder for changes: {ex.Message}");
        }
        finally { _modsWatchBusy = false; }
    }

    private void ShowModsNotice(string? text)
    {
        ModsNoticeText.Text = text ?? "";
        ModsNoticeText.IsVisible = !string.IsNullOrEmpty(text);
    }

    /// <summary>The Mods page's own status line (Home's StatusText is not visible there).</summary>
    private void ModsStatus(string text, bool error = false)
    {
        ModsStatusText.Text = text;
        ModsStatusText.Foreground = new SolidColorBrush(Color.Parse(error ? "#E27676" : "#A0A1AA"));
        if (!string.IsNullOrEmpty(text)) Log(error ? $"[Mods ERROR] {text}" : $"[Mods] {text}");
    }

    private void RenderModsInventory()
    {
        if (ModsList == null || ModsFilterBox == null) return; // events raised while the page is still being loaded
        _selectedMods.Clear();
        _modRows.Clear();
        UpdateModsSelectionBar();
        SelectAllModsCheckbox.IsChecked = false;
        ModsList.Children.Clear();
        var inventory = _modInventory;
        if (inventory == null)
        {
            ModsPageCount.Text = "";
            ModCountText.Text = "—";
            ToolTip.SetTip(ModCountText, null);
            ModsList.Children.Add(FilesNote(_modsLoadingProfile != null ? $"Loading the mods of {_modsLoadingProfile}…"
                : "The mod list is not available; the message below says why. Refresh to try again."));
            return;
        }
        ModsPageCount.Text = ModInventoryView.CountsText(inventory.Counts);
        ModCountText.Text = inventory.Counts.EnabledFiles.ToString();
        ToolTip.SetTip(ModCountText, ModsPageCount.Text);
        var filter = (ModListFilter)Math.Clamp(ModsFilterBox.SelectedIndex, 0, ModInventoryView.FilterLabels.Count - 1);
        foreach (var row in ModInventoryView.Filter(inventory, filter, ModNameSearchBox.Text))
            ModsList.Children.Add(BuildModRow(inventory, row.Entry, row.Expanded));
        if (_modRows.Count == 0)
            ModsList.Children.Add(FilesNote("Nothing matches this filter and search. Reset filters to see the whole inventory."));
        _ = LoadModIconsAsync();
    }

    private static bool IsUserJar(ModInventoryEntry e) => e.Ownership == ModOwnership.User && e.FilePath != null;

    // Updates need a real Fabric id (an unreadable jar is listed under its file name).
    private static bool IsUpdatableJar(ModInventoryEntry e) => IsUserJar(e) && FabricModMetadata.ValidId(e.Id);

    private static bool IsSelectableMod(ModInventoryEntry e) =>
        e.Ownership is ModOwnership.Core or ModOwnership.Pack or ModOwnership.User && e.Status != ModEntryStatus.Unavailable;

    private string? ModToggleBlocked(ModInventoryEntry e) =>
        _launching ? "Wait until the launch has finished." : e.CanToggle ? null : e.ToggleBlockedReason ?? "This entry cannot be switched here.";

    private static string ModOwnershipBadge(ModInventoryEntry e) => e.Ownership switch
    {
        ModOwnership.Core or ModOwnership.NativeModule => "Lads",
        ModOwnership.Platform => "Platform",
        ModOwnership.Retired => "Removed from pack",
        _ when e.IsLibrary => "Library",
        ModOwnership.User => "User",
        _ => "Third-party"
    };

    private static string ModMetaLine(ModInventoryEntry e)
    {
        if (e.Ownership == ModOwnership.NativeModule)
            return e.Id == ModInventoryService.CatalogPlaceholderId ? "Lads modules" : "Lads module · configure it in game (Lads menu)";
        if (e.Ownership == ModOwnership.Platform) return $"id: {e.Id} · platform component";
        var parts = new List<string?> { e.Id == e.FileName ? null : "id: " + e.Id, e.FileName };
        if (e.UpstreamName != null && e.UpstreamName != e.DisplayName) parts.Add("upstream: " + e.UpstreamName);
        if (e.Authors.Count > 0) parts.Add("by " + string.Join(", ", e.Authors.Take(2)) + (e.Authors.Count > 2 ? " and others" : ""));
        return string.Join(" · ", parts.Where(p => !string.IsNullOrEmpty(p)));
    }

    // Invalid rows carry their reason in the status; other notes (e.g. "Lads integration: X") get their own line.
    private static string? ModInvalidReason(ModInventoryEntry e) =>
        e.Status == ModEntryStatus.Invalid ? e.ToggleBlockedReason ?? e.Note : null;

    private static string? ModNoteLine(ModInventoryEntry e) => e.Note == ModInvalidReason(e) ? null : e.Note;

    private static string ModStatusLine(ModInventoryEntry e, string minecraftVersion)
    {
        var status = e.Status switch
        {
            ModEntryStatus.Installed => e.Ownership == ModOwnership.NativeModule ? "Enabled" : "Installed",
            ModEntryStatus.Disabled => "Disabled",
            ModEntryStatus.PendingDownload => e.Ownership == ModOwnership.Core ? "Pending install" : "Pending download",
            ModEntryStatus.NotDownloaded => e.Ownership == ModOwnership.Core ? "Not installed" : "Not downloaded",
            ModEntryStatus.Unavailable => e.Ownership == ModOwnership.NativeModule ? "Unavailable" : $"Unavailable for {minecraftVersion}",
            ModEntryStatus.Unsupported => "Unsupported",
            ModEntryStatus.Embedded => "Embedded",
            ModEntryStatus.RetiredCopy => "Removed from pack",
            ModEntryStatus.Invalid => ModInvalidReason(e) is { } reason ? "Invalid: " + reason : "Invalid",
            _ => e.Status.ToString()
        };
        var parts = new List<string> { status };
        if (e.LoadedNow is bool loaded && loaded != e.RequestedEnabled && e.Ownership != ModOwnership.Platform)
            parts.Add($"Loaded now: {(loaded ? "yes" : "no")} · Next launch: {(e.RequestedEnabled ? "on" : "off")}");
        if (e.RestartRequired) parts.Add("Restart required");
        return string.Join(" · ", parts);
    }

    private static string ModStatusColor(ModInventoryEntry e) => e.Status switch
    {
        ModEntryStatus.Installed when e.Ownership != ModOwnership.Platform => "#A4BAA7",
        ModEntryStatus.PendingDownload => "#E0A458",
        ModEntryStatus.Disabled or ModEntryStatus.NotDownloaded => "#90929D",
        ModEntryStatus.Unsupported or ModEntryStatus.Invalid or ModEntryStatus.RetiredCopy => "#E27676",
        _ => "#A0A1AA"
    };

    private static Border ModBadge(string text, string color = "#E27676") => new()
    {
        Background = new SolidColorBrush(Color.Parse("#303137")), CornerRadius = new CornerRadius(4), Padding = new Thickness(6, 2),
        Margin = new Thickness(0, 0, 6, 0), VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center,
        Child = new TextBlock { Text = text, Foreground = new SolidColorBrush(Color.Parse(color)), FontSize = 9, FontWeight = FontWeight.Bold }
    };

    private static Button ModActionButton(string text, string style, string? tip, Action onClick)
    {
        var button = new Button
        {
            Content = text, Classes = { style }, Height = 30, FontSize = 11, Padding = new Thickness(10, 0),
            HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center, VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center
        };
        if (tip != null) ToolTip.SetTip(button, tip);
        button.Click += (_, _) => onClick();
        return button;
    }

    // "v1.2.0"; manifest version names such as "fabric-1.5.1+26.3" or "v3.3.0" are shown as they are.
    private static string ModVersionText(string version) => char.IsDigit(version[0]) ? "v" + version : version;

    private static IEnumerable<(ModInventoryEntry Entry, int Level)> ModDescendants(ModInventoryEntry entry, int level) =>
        entry.Children.SelectMany(child => ModDescendants(child, level + 1).Prepend((child, level)));

    private static string ModExpanderText(int count, bool open) =>
        $"{(open ? "Hide" : "Show")} {count} embedded {(count == 1 ? "library" : "libraries")}";

    // The valid operation for an embedded library: switch off the jar that contains it (with the usual confirmation).
    private string? ModChildActionLabel(ModInventoryEntry parent) =>
        parent.Ownership is ModOwnership.Core or ModOwnership.Pack or ModOwnership.User && parent.CanToggle && parent.RequestedEnabled
            ? $"Disable {parent.DisplayName}..." : null;

    private Control BuildModRow(ModInventory inventory, ModInventoryEntry entry, bool expanded)
    {
        bool on = entry.RequestedEnabled;
        var details = new StackPanel { Spacing = 3, VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center };
        var header = new WrapPanel();
        header.Children.Add(new TextBlock
        {
            Text = entry.DisplayName, FontSize = 14, FontWeight = FontWeight.Bold, Margin = new Thickness(0, 0, 8, 0),
            Foreground = new SolidColorBrush(Color.Parse(on || entry.Ownership == ModOwnership.Platform ? "#FFFFFF" : "#90929D")),
            VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center
        }.Untranslated());
        if (!string.IsNullOrEmpty(entry.Version))
            header.Children.Add(new TextBlock { Text = ModVersionText(entry.Version), Foreground = new SolidColorBrush(Color.Parse("#868994")), FontSize = 11, Margin = new Thickness(0, 0, 8, 0), VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center });
        header.Children.Add(ModBadge(ModOwnershipBadge(entry)));
        if (entry.RestartRequired) header.Children.Add(ModBadge("Restart required", "#E0A458"));
        details.Children.Add(header);
        // Ids, file names and authors stay as they are; a Lads module's line is the launcher's own text.
        details.Children.Add(new TextBlock { Text = ModMetaLine(entry), Foreground = new SolidColorBrush(Color.Parse("#868994")), FontSize = 11, TextTrimming = TextTrimming.CharacterEllipsis }.Untranslated(entry.Ownership != ModOwnership.NativeModule));
        details.Children.Add(new TextBlock { Text = ModStatusLine(entry, inventory.MinecraftVersion), Foreground = new SolidColorBrush(Color.Parse(ModStatusColor(entry))), FontSize = 12, FontWeight = FontWeight.SemiBold, TextWrapping = TextWrapping.Wrap });
        if (ModNoteLine(entry) is { } note)
            details.Children.Add(new TextBlock { Text = note, Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")), FontSize = 12, TextWrapping = TextWrapping.Wrap });

        StackPanel? children = null;
        Button? expander = null;
        if (entry.Children.Count > 0)
        {
            children = new StackPanel { Spacing = 4, Margin = new Thickness(0, 8, 0, 0), IsVisible = expanded };
            var list = children;
            int count = ModDescendants(entry, 0).Count();
            void Fill()
            {
                // Built on first open: a large jar (Fabric API) embeds dozens of libraries.
                if (list.Children.Count > 0) return;
                foreach (var (child, level) in ModDescendants(entry, 0)) list.Children.Add(BuildModChildRow(entry, child, level));
            }
            if (expanded) Fill();
            expander = new Button
            {
                Content = ModExpanderText(count, expanded), Classes = { "action" }, Height = 26, FontSize = 11, Padding = new Thickness(10, 0),
                HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Left, Margin = new Thickness(0, 4, 0, 0),
                VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center
            };
            var toggleList = expander;
            toggleList.Click += (_, _) =>
            {
                Fill();
                list.IsVisible = !list.IsVisible;
                toggleList.Content = ModExpanderText(count, list.IsVisible);
            };
        }

        var actionLabels = new List<string>();
        var actions = new StackPanel { Orientation = Avalonia.Layout.Orientation.Horizontal, Spacing = 4, VerticalAlignment = Avalonia.Layout.VerticalAlignment.Bottom, HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Right, Margin = new Thickness(12, 8, 0, 0) };
        void Action(Button button) { actions.Children.Add(button); actionLabels.Add(button.Content?.ToString() ?? ""); }
        if (entry.ProjectUrl is { } projectUrl) Action(ModActionButton("Project", "action", projectUrl, () => OpenPath(projectUrl)));
        if (IsUpdatableJar(entry)) Action(ModActionButton("Update", "action", "Look for a newer release on Modrinth", () => _ = UpdateUserModsAsync(new[] { entry })));
        var toggle = ModActionButton(on ? "Disable" : "Enable", on ? "danger" : "action", null, () => _ = ToggleModEntryAsync(entry, !on));
        toggle.MinWidth = 72; // a translated Disable is longer
        var blocked = ModToggleBlocked(entry);
        toggle.IsEnabled = blocked == null;
        ToolTip.SetTip(toggle, blocked);
        ToolTip.SetShowOnDisabled(toggle, true);
        Action(toggle);
        if (IsUserJar(entry)) Action(ModActionButton("Delete", "danger", "Move this jar to the Recycle Bin", () => _ = DeleteUserModsAsync(new[] { entry })));

        // Two cards per line (ModsList is a two-column WrapPanel): icon and details on top, the expander and actions below.
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*") };
        grid.Children.Add(ModIconBox(entry, out var icon));
        Grid.SetColumn(details, 1);
        grid.Children.Add(details);
        var footer = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        if (expander != null) footer.Children.Add(expander);
        Grid.SetColumn(actions, 1);
        footer.Children.Add(actions);
        var body = new StackPanel();
        body.Children.Add(grid);
        body.Children.Add(footer);
        if (children != null) body.Children.Add(children);
        var card = new Border
        {
            Background = new SolidColorBrush(Color.Parse("#1D1E22")), BorderBrush = new SolidColorBrush(Color.Parse("#303137")),
            BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(4), Padding = new Thickness(16, 12), Child = body
        };

        var wrapper = new Grid { ColumnDefinitions = new ColumnDefinitions("28,*"), Margin = new Thickness(0, 0, 8, 8) };
        CheckBox? select = null;
        if (IsSelectableMod(entry))
        {
            select = new CheckBox { VerticalAlignment = Avalonia.Layout.VerticalAlignment.Top, Margin = new Thickness(0, 14, 0, 0) };
            string key = entry.FilePath ?? entry.Id;
            select.IsCheckedChanged += (_, _) =>
            {
                if (select.IsChecked == true) _selectedMods[key] = entry;
                else _selectedMods.Remove(key);
                UpdateModsSelectionBar();
            };
            wrapper.Children.Add(select);
        }
        Grid.SetColumn(card, 1);
        wrapper.Children.Add(card);
        _modRows.Add(new ModRowView(entry, wrapper, toggle, select, children, actionLabels, icon));
        return wrapper;
    }

    private Control BuildModChildRow(ModInventoryEntry parent, ModInventoryEntry child, int level)
    {
        var text = new StackPanel { Spacing = 2, VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center };
        var header = new WrapPanel();
        header.Children.Add(new TextBlock { Text = child.DisplayName, Foreground = new SolidColorBrush(Color.Parse("#CCCCCC")), FontSize = 12, FontWeight = FontWeight.SemiBold, Margin = new Thickness(0, 0, 8, 0), VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center }.Untranslated());
        if (!string.IsNullOrEmpty(child.Version))
            header.Children.Add(new TextBlock { Text = ModVersionText(child.Version), Foreground = new SolidColorBrush(Color.Parse("#868994")), FontSize = 11, Margin = new Thickness(0, 0, 8, 0), VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center });
        header.Children.Add(ModBadge(ModOwnershipBadge(child)));
        text.Children.Add(header);
        text.Children.Add(new TextBlock { Text = ModMetaLine(child) + " · " + ModStatusLine(child, _modInventory?.MinecraftVersion ?? ""), Foreground = new SolidColorBrush(Color.Parse("#868994")), FontSize = 11, TextWrapping = TextWrapping.Wrap }.Untranslated(child.Ownership != ModOwnership.NativeModule));
        var note = ModNoteLine(child) ?? child.ToggleBlockedReason;
        if (note != null) text.Children.Add(new TextBlock { Text = note, Foreground = new SolidColorBrush(Color.Parse("#90929D")), FontSize = 11, TextWrapping = TextWrapping.Wrap });
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        grid.Children.Add(text);
        if (ModChildActionLabel(parent) is { } label)
        {
            var disableParent = ModActionButton(label, "danger", child.ToggleBlockedReason, () => _ = ToggleModEntryAsync(parent, false, alwaysConfirm: true));
            disableParent.Height = 26;
            disableParent.Margin = new Thickness(8, 0, 0, 0);
            disableParent.IsEnabled = !_launching;
            Grid.SetColumn(disableParent, 1);
            grid.Children.Add(disableParent);
        }
        return new Border
        {
            Background = new SolidColorBrush(Color.Parse("#202125")), BorderBrush = new SolidColorBrush(Color.Parse("#3A3B42")),
            BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(4), Padding = new Thickness(10, 6),
            Margin = new Thickness(level * 16, 0, 0, 0), Child = grid
        };
    }

    private Task ToggleModEntryAsync(ModInventoryEntry entry, bool enable, bool alwaysConfirm = false) =>
        entry.Ownership == ModOwnership.NativeModule
            ? SetNativeModuleAsync(entry, enable)
            : ApplyModChoiceAsync(new[] { entry.Id }, enable, entry.DisplayName, alwaysConfirm);

    private bool ModsCanChange()
    {
        // Only the active profile's list: right after a profile switch the old one must not be changed by a click.
        bool current = ModInventoryView.IsFor(_modInventory, ModsTarget().GameDirectory);
        if (_launching) ModsStatus("Wait until the launch has finished, then try again.", true);
        else if (_modsBusy) ModsStatus("Another mod change is still being applied.", true);
        else if (!current) ModsStatus(_modsLoadingProfile != null ? $"The mods of {_modsLoadingProfile} are still loading. Try again in a moment."
            : "The mod list is not loaded. Refresh and try again.", true);
        return !_launching && !_modsBusy && current;
    }

    /// <summary>The profile whose game folder the inventory lists (what a confirmation must name).</summary>
    private string ModsProfileName(ModInventory inventory) =>
        _profileService.GetProfiles().FirstOrDefault(p => SafeFileOps.PathsEqual(_pathService.GetProfileDirectory(p), inventory.GameDirectory))?.Name
            ?? inventory.GameDirectory;

    /// <summary>
    /// Enable/disable through the dependency planner: the plan (other mods switched with it, warnings, blockers) is confirmed
    /// in a dialog when it reaches beyond the chosen mods, then applied. The list is always re-read from disk afterwards, so
    /// a failed change shows the real state.
    /// </summary>
    private async Task ApplyModChoiceAsync(IReadOnlyCollection<string> ids, bool enable, string what, bool alwaysConfirm = false)
    {
        if (ids.Count == 0 || !ModsCanChange()) return;
        var inventory = _modInventory!;
        string verb = enable ? "Enable" : "Disable";
        _modsBusy = true;
        try
        {
            var plan = _modStateService.Plan(inventory, ids, enable);
            if ((alwaysConfirm || plan.AlsoDisable.Count + plan.AlsoEnable.Count + plan.Warnings.Count + plan.Blockers.Count > 0)
                && !await ShowLadsDialogAsync($"{verb} {what}", DescribeModPlan(inventory, plan, $"{verb} {what}?"), verb, "Cancel",
                    danger: !enable, confirmEnabled: plan.Blockers.Count == 0))
            {
                ModsStatus(plan.Blockers.Count > 0 ? "Not changed: " + string.Join(" ", plan.Blockers) : "Nothing was changed.", plan.Blockers.Count > 0);
                return;
            }
            ModsStatus($"{verb} {what}...");
            var result = await _modStateService.ApplyAsync(inventory.GameDirectory, inventory, plan);
            ModsStatus(result.Message, !result.Success);
        }
        catch (Exception ex)
        {
            ModsStatus($"Could not {verb.ToLowerInvariant()} {what}: {ex.Message}", true);
        }
        finally { _modsBusy = false; }
        await AfterModsChangedAsync();
    }

    /// <summary>A Lads module (thelads_config.json); the inventory already blocks this while the game runs.</summary>
    private async Task SetNativeModuleAsync(ModInventoryEntry module, bool enable)
    {
        if (!ModsCanChange()) return;
        _modsBusy = true;
        try
        {
            var result = await _modStateService.SetNativeModuleAsync(_modInventory!.GameDirectory, module.Id, enable);
            ModsStatus(result.Message, !result.Success);
        }
        catch (Exception ex)
        {
            ModsStatus($"Could not switch {module.DisplayName}: {ex.Message}", true);
        }
        finally { _modsBusy = false; }
        await AfterModsChangedAsync();
    }

    /// <summary>Re-reads the disk state and refreshes the in-game Mods view's snapshot (.lads-mod-cache\inventory.json).</summary>
    private async Task AfterModsChangedAsync()
    {
        // The inventory this reload read, not _modInventory: a newer reload may still be running, and the one shown until
        // then can predate the change.
        if (await ReloadModsInventoryAsync() is not { } inventory) return;
        try { await _modInventoryService.WriteSnapshotAsync(inventory); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            ModsStatus($"{ModsStatusText.Text} The in-game mod list could not be updated: {ex.Message}", true);
        }
    }

    private static string ModNames(ModInventory inventory, IEnumerable<string> ids, int max = 3)
    {
        var names = ids.Distinct().Select(id => inventory.Entries.SelectMany(ModInventoryView.Flatten).FirstOrDefault(e => e.Id == id)?.DisplayName ?? id).ToList();
        return string.Join(", ", names.Take(max)) + (names.Count > max ? $" and {names.Count - max} more" : "");
    }

    private static string DescribeModPlan(ModInventory inventory, ModTogglePlan plan, string question)
    {
        string Name(string id) => inventory.Entries.SelectMany(ModInventoryView.Flatten).FirstOrDefault(e => e.Id == id) is { } e && e.DisplayName != id
            ? $"{e.DisplayName} ({id})" : id;
        var lines = new List<string> { question };
        void Section(string title, IEnumerable<string> items)
        {
            var list = items.ToList();
            if (list.Count == 0) return;
            lines.Add("");
            lines.Add(title);
            lines.AddRange(list.Select(item => "• " + item));
        }
        if (plan.TargetIds.Count > 1) Section("Selected:", plan.TargetIds.Select(Name));
        Section("Also switched off, because they need it:", plan.AlsoDisable.Select(Name));
        Section("Also switched on, because it needs them:", plan.AlsoEnable.Select(Name));
        Section("Please note:", plan.Warnings);
        Section("This cannot be applied:", plan.Blockers);
        return string.Join("\n", lines);
    }

    private void RefreshMods_Click(object? sender, RoutedEventArgs e) => ReloadModsInventory();

    private void ModNameSearch_TextChanged(object? sender, TextChangedEventArgs e)
    {
        if (!_resettingModFilters) RenderModsInventory();
    }

    private void ModsFilter_Changed(object? sender, SelectionChangedEventArgs e)
    {
        if (!_resettingModFilters) RenderModsInventory();
    }

    private void SetModFilters(int filter, string search)
    {
        _resettingModFilters = true;
        try
        {
            ModsFilterBox.SelectedIndex = filter;
            ModNameSearchBox.Text = search;
        }
        finally { _resettingModFilters = false; }
        RenderModsInventory();
    }

    private void ResetModFilters_Click(object? sender, RoutedEventArgs e) => SetModFilters(0, "");

    private async void RestoreDefaultMods_Click(object? sender, RoutedEventArgs e)
    {
        if (!ModsCanChange()) return;
        var inventory = _modInventory!;
        // Busy from the question on: a launch (e.g. a crash auto-relaunch) cannot start its installers while it is open.
        _modsBusy = true;
        try
        {
            if (!await ShowLadsDialogAsync("Restore default mod set",
                    $"Clear every saved enable/disable choice for '{ModsProfileName(inventory)}'? The Lads pack mods and LadsCore are switched back on " +
                    "(missing ones are downloaded at the next launch). Mods you added yourself keep their current state.",
                    "Restore defaults", "Cancel", danger: true))
                return;
            var result = await _modStateService.RestoreDefaultsAsync(inventory.GameDirectory, inventory);
            ModsStatus(result.Success ? "Default mod set restored. " + result.Message : result.Message, !result.Success);
        }
        catch (Exception ex)
        {
            ModsStatus($"Could not restore the default mod set: {ex.Message}", true);
        }
        finally { _modsBusy = false; }
        await AfterModsChangedAsync();
    }

    /// <summary>Every mod file of the profile to the Recycle Bin (yours too), saved choices cleared, LadsCore and the pack
    /// reinstalled (ModStateService.ResetModsFolderAsync). The way out when a profile's Mods folder is beyond repair.</summary>
    private async void ResetModsFolder_Click(object? sender, RoutedEventArgs e)
    {
        if (!ModsCanChange()) return;
        var inventory = _modInventory!;
        if (IsGameRunningFor(inventory.GameDirectory))
        {
            ModsStatus($"Minecraft is running for '{ModsProfileName(inventory)}'. Close it, then reset the mods folder.", true);
            return;
        }
        // Busy from the question on: a launch cannot start its installers while it is open or while the pack reinstalls.
        _modsBusy = true;
        try
        {
            if (!await ShowLadsDialogAsync("Reset mods folder",
                    $"Reset the mods folder of '{ModsProfileName(inventory)}'?\n\n" +
                    "• Every mod file in it goes to the Recycle Bin, including mods you added yourself.\n" +
                    "• Saved mod on/off choices are cleared.\n" +
                    "• LadsCore and the Lads pack are reinstalled fresh.\n\n" +
                    "Worlds, settings and mod configs are not touched.",
                    "Reset mods folder", "Cancel", danger: true))
                return;
            ModsStatus("Resetting the mods folder...");
            var result = await Task.Run(() => _modStateService.ResetModsFolderAsync(AppContext.BaseDirectory, inventory.GameDirectory,
                inventory.MinecraftVersion, message => Dispatcher.UIThread.Post(() => ModsStatusText.Text = message)));
            ModsStatus(result.Message, !result.Success);
        }
        catch (Exception ex)
        {
            ModsStatus($"Could not reset the mods folder: {ex.Message}", true);
        }
        finally { _modsBusy = false; }
        await AfterModsChangedAsync();
    }

    // ─── Add / delete / update your own mods ───────────────

    private async void AddModFromFile_Click(object? sender, RoutedEventArgs e)
    {
        IReadOnlyList<IStorageFile> files;
        try
        {
            files = await StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
            {
                Title = "Add mod .jar file(s)",
                AllowMultiple = true,
                FileTypeFilter = new[] { new FilePickerFileType("Fabric mod (*.jar)") { Patterns = new[] { "*.jar" } } }
            });
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or UnauthorizedAccessException)
        {
            ModsStatus($"Could not open the file picker: {ex.Message}", true);
            return;
        }
        await AddUserModsAsync(files.Select(f => f.Path.LocalPath));
    }

    private async void AddModFromFolder_Click(object? sender, RoutedEventArgs e)
    {
        string[] jars;
        try
        {
            var folders = await StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions { Title = "Add all .jar files from a folder", AllowMultiple = false });
            if (folders.Count == 0) return;
            jars = Directory.GetFiles(folders[0].Path.LocalPath, "*.jar");
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or UnauthorizedAccessException or ArgumentException)
        {
            ModsStatus($"Could not read that folder: {ex.Message}", true);
            return;
        }
        if (jars.Length == 0) { ModsStatus("That folder has no .jar files.", true); return; }
        await AddUserModsAsync(jars);
    }

    private void ModsPage_DragOver(object? sender, DragEventArgs e)
    {
        // Only accept file drops (Avalonia 12 data-transfer API)
        e.DragEffects = e.DataTransfer.Contains(DataFormat.File)
            ? DragDropEffects.Copy
            : DragDropEffects.None;
        e.Handled = true;
    }

    private async void ModsPage_Drop(object? sender, DragEventArgs e)
    {
        e.Handled = true;
        var files = e.DataTransfer.TryGetFiles();
        if (files == null) return;
        await AddUserModsAsync(files.Select(f => f.Path.LocalPath).ToList());
    }

    /// <summary>Add from file, folder or drag-and-drop: each jar is copied into Mods by UserModFiles, which never
    /// overwrites and refuses pack/LadsCore mods and a second copy of a mod. Every refusal is listed.</summary>
    private async Task AddUserModsAsync(IEnumerable<string> paths)
    {
        var list = paths.Where(p => !string.IsNullOrEmpty(p)).ToList();
        if (list.Count == 0 || !ModsCanChange()) return;
        var (gameDirectory, version) = ModsTarget();
        var added = new List<string>();
        var refused = new List<string>();
        _modsBusy = true;
        try
        {
            foreach (var path in list)
            {
                try
                {
                    // Rebuilt for every jar, so two dropped copies of one mod are caught (the scan cache keeps this cheap).
                    var inventory = await _modInventoryService.BuildAsync(AppContext.BaseDirectory, gameDirectory, version);
                    var destination = await Task.Run(() => UserModFiles.Add(inventory, path));
                    added.Add(Path.GetFileName(destination));
                }
                catch (Exception ex)
                {
                    refused.Add($"{Path.GetFileName(path)}: {ex.Message}");
                }
            }
        }
        finally { _modsBusy = false; }
        var text = added.Count > 0 ? $"Added {string.Join(", ", added)}." : "";
        if (refused.Count > 0) text += (text.Length > 0 ? " " : "") + "Not added: " + string.Join(" ", refused);
        ModsStatus(text, refused.Count > 0);
        await AfterModsChangedAsync();
    }

    /// <summary>Only jars you added yourself are deleted here (to the Recycle Bin, after confirmation); pack mods and
    /// LadsCore are switched off instead, so the launcher does not download them again.</summary>
    private async Task DeleteUserModsAsync(IReadOnlyList<ModInventoryEntry> entries)
    {
        var jars = entries.Where(IsUserJar).ToList();
        if (jars.Count == 0)
        {
            ModsStatus("Only mods you added yourself can be deleted. Switch pack mods and LadsCore off instead.", true);
            return;
        }
        if (!ModsCanChange()) return;
        int skipped = entries.Count - jars.Count;
        var message = $"Move {(jars.Count == 1 ? "this mod" : $"these {jars.Count} mods")} of '{ModsProfileName(_modInventory!)}' to the Recycle Bin?\n\n"
            + string.Join("\n", jars.Take(12).Select(j => $"• {j.DisplayName} ({j.FileName})"))
            + (jars.Count > 12 ? $"\n…and {jars.Count - 12} more" : "")
            + (skipped > 0 ? $"\n\n{skipped} selected pack or Lads entries are not deleted; switch them off instead." : "");
        var failures = new List<string>();
        // Busy from the question on: a launch cannot start its installers while it is open.
        _modsBusy = true;
        try
        {
            if (!await ShowLadsDialogAsync("Delete mods", message, "Move to Recycle Bin", "Cancel", danger: true)) return;
            foreach (var jar in jars)
            {
                try { await Task.Run(() => SafeFileOps.DeleteToRecycleBin(jar.FilePath!)); }
                catch (Exception ex) { failures.Add($"{jar.FileName}: {ex.Message}"); }
            }
        }
        finally { _modsBusy = false; }
        ModsStatus(failures.Count == 0
            ? $"Moved {string.Join(", ", jars.Select(j => j.FileName))} to the Recycle Bin."
            : $"Moved {jars.Count - failures.Count} of {jars.Count} to the Recycle Bin. Not deleted: {string.Join("; ", failures)}", failures.Count > 0);
        await AfterModsChangedAsync();
    }

    private async void DeleteSelectedMods_Click(object? sender, RoutedEventArgs e) => await DeleteUserModsAsync(_selectedMods.Values.ToList());

    private void UpdateModsSelectionBar()
    {
        int count = _selectedMods.Count;
        if (ModsMassActionBar != null) ModsMassActionBar.IsVisible = count > 0;
        if (ModsSelectionCount != null) ModsSelectionCount.Text = $"{count} selected";
    }

    // Selects only the rows the filter and search show: a mass action never reaches a mod you cannot see.
    private void SelectAllMods_Click(object? sender, RoutedEventArgs e)
    {
        bool selectAll = SelectAllModsCheckbox?.IsChecked == true;
        foreach (var row in _modRows)
            if (row.Select != null) row.Select.IsChecked = selectAll;
        UpdateModsSelectionBar();
    }

    private string SelectionName() =>
        _selectedMods.Count == 1 ? _selectedMods.Values.First().DisplayName : $"{_selectedMods.Count} selected mods";

    private async void EnableSelectedMods_Click(object? sender, RoutedEventArgs e) =>
        await ApplyModChoiceAsync(_selectedMods.Values.Select(m => m.Id).Distinct().ToList(), true, SelectionName());

    private async void DisableSelectedMods_Click(object? sender, RoutedEventArgs e) =>
        await ApplyModChoiceAsync(_selectedMods.Values.Select(m => m.Id).Distinct().ToList(), false, SelectionName());

    private async void UpdateSelectedMods_Click(object? sender, RoutedEventArgs e) => await UpdateUserModsAsync(_selectedMods.Values.ToList());

    private async void UpdateAllMods_Click(object? sender, RoutedEventArgs e) =>
        await UpdateUserModsAsync(_modInventory?.Entries.Where(IsUpdatableJar).ToList() ?? new List<ModInventoryEntry>());

    /// <summary>
    /// Updates mods you added yourself from Modrinth (mod id as the project slug). Pack mods and LadsCore come only from the
    /// launcher's pinned pack. The download must be the same Fabric mod; it replaces the old jar (kept in
    /// .lads-mod-cache\user-mod-backups) and keeps its disabled state.
    /// </summary>
    private async Task UpdateUserModsAsync(IReadOnlyList<ModInventoryEntry> entries)
    {
        var jars = entries.Where(IsUpdatableJar).ToList();
        if (jars.Count == 0)
        {
            ModsStatus("Only mods you added yourself are updated here; the launcher keeps the pack mods and LadsCore up to date.", entries.Count > 0);
            return;
        }
        if (!ModsCanChange()) return;
        var inventory = _modInventory!;
        var lines = new List<string>();
        int updated = 0, failed = 0;
        _modsBusy = true;
        UpdateAllModsBtn.IsEnabled = false;
        try
        {
            foreach (var jar in jars)
            {
                ModsStatus($"Checking {jar.DisplayName}...");
                try
                {
                    var (changed, message) = await UpdateUserModAsync(inventory, jar);
                    if (changed) updated++;
                    lines.Add(message);
                }
                catch (Exception ex)
                {
                    failed++;
                    lines.Add($"{jar.DisplayName}: {ex.Message}");
                }
            }
        }
        finally
        {
            _modsBusy = false;
            UpdateAllModsBtn.IsEnabled = true;
        }
        ModsStatus($"Updated {updated} of {jars.Count}. " + string.Join(" ", lines), failed > 0);
        if (updated > 0) await AfterModsChangedAsync();
    }

    private async Task<(bool Updated, string Message)> UpdateUserModAsync(ModInventory inventory, ModInventoryEntry jar)
    {
        string url = $"{settings.ModrinthApiUrl}/project/{Uri.EscapeDataString(jar.Id)}/version?loaders=[\"fabric\"]&game_versions=[\"{inventory.MinecraftVersion}\"]";
        using var response = await _httpClient.GetAsync(url);
        if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
            return (false, $"{jar.DisplayName}: Modrinth has no project '{jar.Id}'.");
        response.EnsureSuccessStatusCode();
        using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        if (document.RootElement.ValueKind != JsonValueKind.Array || document.RootElement.GetArrayLength() == 0)
            return (false, $"{jar.DisplayName}: no Modrinth release for Minecraft {inventory.MinecraftVersion}.");
        var latest = document.RootElement[0];
        string latestVersion = latest.TryGetProperty("version_number", out var number) ? number.GetString() ?? "?" : "?";
        var file = PrimaryModrinthFile(latest) ?? throw new InvalidDataException($"Modrinth lists no file for {jar.DisplayName} {latestVersion}.");
        string? sha512 = file.TryGetProperty("hashes", out var hashes) && hashes.TryGetProperty("sha512", out var hash) ? hash.GetString() : null;
        string installed = await Task.Run(() => FileSha512(jar.FilePath!));
        if (sha512 != null ? sha512.Equals(installed, StringComparison.OrdinalIgnoreCase) : latestVersion == jar.Version)
            return (false, $"{jar.DisplayName} is up to date ({latestVersion}).");
        string downloadUrl = file.TryGetProperty("url", out var link) ? link.GetString() ?? "" : "";
        if (downloadUrl.Length == 0) throw new InvalidDataException($"Modrinth lists no download for {jar.DisplayName} {latestVersion}.");
        string fileName = file.TryGetProperty("filename", out var name) ? name.GetString() ?? "" : "";
        var temp = await DownloadToModCacheAsync(downloadUrl, inventory.GameDirectory, sha512, null);
        try
        {
            await Task.Run(() => UserModFiles.Install(inventory, temp, fileName, expectedId: jar.Id));
        }
        finally
        {
            if (File.Exists(temp)) File.Delete(temp);
        }
        return (true, $"{jar.DisplayName}: {jar.Version} → {latestVersion}.");
    }

    private static JsonElement? PrimaryModrinthFile(JsonElement version)
    {
        if (!version.TryGetProperty("files", out var files) || files.ValueKind != JsonValueKind.Array || files.GetArrayLength() == 0) return null;
        foreach (var file in files.EnumerateArray())
            if (file.TryGetProperty("primary", out var primary) && primary.ValueKind == JsonValueKind.True) return file;
        return files[0];
    }

    private static string FileSha512(string path)
    {
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read | FileShare.Delete);
        return Convert.ToHexString(System.Security.Cryptography.SHA512.HashData(stream)).ToLowerInvariant();
    }

    /// <summary>Downloads into the profile's .lads-mod-cache (the same drive as Mods, so placing it is a rename). A failed or
    /// mismatching download is deleted; Mods is not touched here.</summary>
    private async Task<string> DownloadToModCacheAsync(string url, string gameDirectory, string? sha512, IProgress<double>? progress)
    {
        var cache = Path.Combine(gameDirectory, ".lads-mod-cache");
        Directory.CreateDirectory(cache);
        var temp = Path.Combine(cache, "download-" + Guid.NewGuid().ToString("N") + ".tmp");
        try
        {
            using (var response = await _httpClient.GetAsync(url, HttpCompletionOption.ResponseHeadersRead))
            {
                response.EnsureSuccessStatusCode();
                long? total = response.Content.Headers.ContentLength;
                await using var input = await response.Content.ReadAsStreamAsync();
                await using var output = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, true);
                var buffer = new byte[81920];
                long done = 0;
                int read;
                while ((read = await input.ReadAsync(buffer)) > 0)
                {
                    await output.WriteAsync(buffer.AsMemory(0, read));
                    done += read;
                    if (total > 0) progress?.Report(done * 100.0 / total.Value);
                }
            }
            if (sha512 != null && !(await Task.Run(() => FileSha512(temp))).Equals(sha512, StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException("The download does not match the checksum Modrinth published. Nothing was changed.");
            return temp;
        }
        catch
        {
            if (File.Exists(temp)) File.Delete(temp);
            throw;
        }
    }

    /// <summary>
    /// --preview-mods &lt;outputDir&gt; (sandbox only; Program.Main refuses it otherwise): builds the active profile's inventory,
    /// opens the Mods page and, for every filter, one search for an embedded library id and a Reset, saves a screenshot and
    /// the visible rows (texts, statuses, badges, toggle state, children) with the counts to mods-preview.json; then exits.
    /// </summary>
    private async Task RunModsPreviewAsync(string outputDirectory)
    {
        int exitCode = 0;
        var result = new JsonObject();
        try
        {
            Directory.CreateDirectory(outputDirectory);
            NavigateTo("Mods");
            await ReloadModsInventoryAsync();
            var inventory = _modInventory ?? throw new InvalidOperationException("The mod list could not be built: " + ModsNoticeText.Text);
            result["profile"] = _profileService.GetActiveProfile().Name;
            result["gameDirectory"] = inventory.GameDirectory;
            result["minecraftVersion"] = inventory.MinecraftVersion;
            result["gameRunning"] = inventory.GameRunning;
            result["counts"] = JsonSerializer.SerializeToNode(inventory.Counts);
            result["countsLabel"] = ModsPageCount.Text;
            result["homeModsStat"] = ModCountText.Text;
            result["notice"] = ModsNoticeText.IsVisible ? ModsNoticeText.Text : null;
            result["entriesTotal"] = inventory.Entries.Count;
            var views = new JsonArray();
            async Task Capture(string name)
            {
                await Task.Delay(600); // layout settles
                var shot = Path.Combine(outputDirectory, $"mods-{name}.png");
                SaveWindowScreenshot(shot);
                views.Add(new JsonObject
                {
                    ["name"] = name,
                    ["filter"] = ModInventoryView.FilterLabels[ModsFilterBox.SelectedIndex],
                    ["search"] = ModNameSearchBox.Text ?? "",
                    ["screenshot"] = shot,
                    ["visibleRows"] = _modRows.Count,
                    ["rows"] = new JsonArray(_modRows.Select(r => (JsonNode?)DescribeModRow(inventory, r)).ToArray())
                });
            }
            for (int i = 0; i < ModInventoryView.FilterLabels.Count; i++)
            {
                SetModFilters(i, "");
                await Capture("filter-" + Regex.Replace(ModInventoryView.FilterLabels[i].ToLowerInvariant(), "[^a-z0-9]+", "-"));
            }
            // An embedded library whose parent's own name/id does not contain its id: found only through the children.
            var nested = inventory.Entries.SelectMany(parent => ModInventoryView.Flatten(parent).Skip(1).Select(child => (Parent: parent, Child: child)))
                .FirstOrDefault(p => FabricModMetadata.ValidId(p.Child.Id) && !p.Parent.Id.Contains(p.Child.Id, StringComparison.OrdinalIgnoreCase)
                    && !p.Parent.DisplayName.Contains(p.Child.Id, StringComparison.OrdinalIgnoreCase));
            if (nested.Child == null) throw new InvalidOperationException("This profile has no embedded library to search for.");
            SetModFilters(0, nested.Child.Id);
            await Capture("search-nested-library");
            ResetModFilters_Click(this, new RoutedEventArgs());
            await Capture("reset");
            result["views"] = views;
            // Layout check of every kind of row: the list scrolled to the first row of each status (screenshots only).
            var statusShots = new JsonObject();
            foreach (var group in _modRows.GroupBy(r => r.Entry.Status).ToList())
            {
                group.First().Row.BringIntoView();
                await Task.Delay(600);
                var shot = Path.Combine(outputDirectory, $"mods-status-{group.Key.ToString().ToLowerInvariant()}.png");
                SaveWindowScreenshot(shot);
                statusShots[group.Key.ToString()] = new JsonObject { ["firstRow"] = group.First().Entry.Id, ["rows"] = group.Count(), ["screenshot"] = shot };
            }
            result["statusScreenshots"] = statusShots;
            // The confirmation a toggle shows: the library whose disabling reaches the most mods, and LadsCore (warning).
            var dialogs = new JsonArray();
            var library = inventory.Entries.Where(e => e.CanToggle && e.RequestedEnabled && e.Ownership is ModOwnership.Pack or ModOwnership.User)
                .Select(e => (Entry: e, Plan: _modStateService.Plan(inventory, new[] { e.Id }, false)))
                .OrderByDescending(p => p.Plan.AlsoDisable.Count).FirstOrDefault();
            var core = inventory.Entries.FirstOrDefault(e => e.Ownership == ModOwnership.Core);
            foreach (var (name, entry) in new[] { ("disable-library", library.Entry), ("disable-core", core) })
            {
                if (entry == null) continue;
                var plan = _modStateService.Plan(inventory, new[] { entry.Id }, false);
                var text = DescribeModPlan(inventory, plan, $"Disable {entry.DisplayName}?");
                var dialog = CreateLadsDialog($"Disable {entry.DisplayName}", text, new[] { ("Disable", true, plan.Blockers.Count == 0) }, "Cancel");
                dialog.Show(this);
                await Task.Delay(600);
                var shot = Path.Combine(outputDirectory, $"mods-dialog-{name}.png");
                using (var bitmap = new RenderTargetBitmap(new PixelSize(Math.Max(1, (int)dialog.Bounds.Width), Math.Max(1, (int)dialog.Bounds.Height)), new Vector(96, 96)))
                {
                    bitmap.Render(dialog);
                    bitmap.Save(shot);
                }
                dialog.Close();
                dialogs.Add(new JsonObject { ["name"] = name, ["plan"] = JsonSerializer.SerializeToNode(plan), ["text"] = text, ["screenshot"] = shot });
            }
            result["dialogs"] = dialogs;
        }
        catch (Exception ex)
        {
            exitCode = 1;
            result["error"] = ex.ToString();
            Log($"[Preview] {ex}");
        }
        try
        {
            File.WriteAllText(Path.Combine(outputDirectory, "mods-preview.json"), result.ToJsonString(new JsonSerializerOptions(JsonSerializerOptions.Default) { WriteIndented = true }));
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            Console.Error.WriteLine($"--preview-mods could not write its report to '{outputDirectory}': {ex.Message}");
            exitCode = 1;
        }
        Environment.Exit(exitCode);
    }

    private JsonObject DescribeModRow(ModInventory inventory, ModRowView row)
    {
        var e = row.Entry;
        return new JsonObject
        {
            ["id"] = e.Id,
            ["displayName"] = e.DisplayName,
            ["upstreamName"] = e.UpstreamName,
            ["version"] = e.Version,
            ["fileName"] = e.FileName,
            ["badge"] = ModOwnershipBadge(e),
            ["meta"] = ModMetaLine(e),
            ["status"] = ModStatusLine(e, inventory.MinecraftVersion),
            ["note"] = ModNoteLine(e),
            ["restartRequired"] = e.RestartRequired,
            ["loadedNow"] = e.LoadedNow,
            ["nextLaunch"] = e.RequestedEnabled,
            ["toggle"] = new JsonObject
            {
                ["label"] = row.Toggle.Content?.ToString(),
                ["enabled"] = row.Toggle.IsEnabled,
                ["blockedReason"] = ToolTip.GetTip(row.Toggle)?.ToString()
            },
            ["selectable"] = row.Select != null,
            ["actions"] = new JsonArray(row.Actions.Select(a => (JsonNode?)a).ToArray()),
            ["expanded"] = row.Children?.IsVisible ?? false,
            ["children"] = new JsonArray(ModDescendants(e, 0).Select(c => (JsonNode?)new JsonObject
            {
                ["level"] = c.Level,
                ["id"] = c.Entry.Id,
                ["displayName"] = c.Entry.DisplayName,
                ["version"] = c.Entry.Version,
                ["badge"] = ModOwnershipBadge(c.Entry),
                ["status"] = ModStatusLine(c.Entry, inventory.MinecraftVersion),
                ["note"] = ModNoteLine(c.Entry) ?? c.Entry.ToggleBlockedReason,
                ["action"] = ModChildActionLabel(e)
            }).ToArray())
        };
    }

    private void SaveWindowScreenshot(string target)
    {
        var size = new PixelSize(Math.Max(1, (int)Bounds.Width), Math.Max(1, (int)Bounds.Height));
        using var bitmap = new RenderTargetBitmap(size, new Vector(96, 96));
        bitmap.Render(this);
        bitmap.Save(target);
    }

    // ═══════════════════════════════════════
    //  GAME LAUNCH
    // ═══════════════════════════════════════

    private async void LaunchButton_Click(object? sender, RoutedEventArgs e)
    {
        try {
            LaunchButton.IsEnabled = false;

            if (string.IsNullOrEmpty(_selectedAccount))
            {
                // Don't silently do nothing — tell the user and send them to Accounts.
                StatusText.Text = "Select or add an account first.";
                Log("[Launcher] Launch aborted: no account is selected.");
                NavigateTo("Accounts");
                return;
            }

            await LaunchGame();
        } catch (Exception ex) {
            try {
                var crashFile = Path.Combine(TheLadsLauncher.Services.PathService.Instance.LogsDirectory, "crash_launchbtn.txt");
                System.IO.File.WriteAllText(crashFile, ex.ToString());
            } catch { }
            Log($"[CRASH] {ex.Message}");
        } finally {
            LaunchButton.IsEnabled = true;
            GameLaunchOverlay.IsVisible = false;
        }
    }

    // Stops the launch at its next step; LaunchGame reports it once the steps have unwound (the game is never started).
    private void CancelLaunch_Click(object? sender, RoutedEventArgs e)
    {
        if (_launchCts is not { IsCancellationRequested: false } launch) return;
        CancelLaunchButton.IsEnabled = false;
        CancelLaunchButton.Content = "Cancelling...";
        Log("[Launcher] Cancelling the launch...");
        launch.Cancel();
    }

    private string ResolveLaunchAccountName()
    {
        return !string.IsNullOrEmpty(_launchAccountOverride)
            && GetAccountSummaries().Any(a => string.Equals(a.username, _launchAccountOverride, StringComparison.OrdinalIgnoreCase))
                ? _launchAccountOverride : _selectedAccount;
    }

    private async Task ShowMicrosoftSetupDialogAsync()
    {
        _showingMicrosoftSetup = true;
        try
        {
            var dialog = new Window
            {
                Title = "Microsoft login setup required", Width = 600, Height = 300,
                WindowStartupLocation = WindowStartupLocation.CenterOwner, CanResize = false,
                Background = new SolidColorBrush(Color.Parse("#1D1E22"))
            };
            var panel = new StackPanel { Margin = new Thickness(24), Spacing = 16 };
            panel.Children.Add(new TextBlock
            {
                Text = "Microsoft login setup required", FontSize = 21,
                FontWeight = FontWeight.Bold, Foreground = Brushes.White
            });
            panel.Children.Add(new TextBlock
            {
                Text = "This launcher has no Microsoft application ID configured. Its owner must register The Lads Client and obtain Minecraft API approval before Microsoft accounts can launch.",
                TextWrapping = TextWrapping.Wrap, FontSize = 14, Foreground = Brushes.LightGray
            });
            panel.Children.Add(new TextBlock
            {
                Text = "To try the new title screen now, add a separate offline account. Offline accounts support local play, but cannot join Realms or servers requiring Microsoft authentication.",
                TextWrapping = TextWrapping.Wrap, FontSize = 13, Foreground = Brushes.LightGray
            });
            var buttons = new Grid { ColumnDefinitions = ColumnDefinitions.Parse("*,*,Auto") };
            var setup = new Button { Content = "Set up Microsoft login", Height = 38, HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Stretch, HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center, Margin = new Thickness(0, 0, 8, 0) };
            var offline = new Button { Content = "Add offline account", Height = 38, HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Stretch, HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center, Margin = new Thickness(0, 0, 8, 0) };
            var cancel = new Button { Content = "Cancel", Height = 38, Width = 80, HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center };
            setup.Click += (_, _) => dialog.Close("setup");
            offline.Click += (_, _) => dialog.Close("offline");
            cancel.Click += (_, _) => dialog.Close("cancel");
            Grid.SetColumn(offline, 1);
            Grid.SetColumn(cancel, 2);
            buttons.Children.Add(setup);
            buttons.Children.Add(offline);
            buttons.Children.Add(cancel);
            panel.Children.Add(buttons);
            dialog.Content = panel;
            string choice = await dialog.ShowDialog<string>(this);
            if (choice == "setup")
            {
                NavigateTo("Settings");
                MicrosoftClientIdTextBox.Focus();
            }
            else if (choice == "offline")
            {
                NavigateTo("Accounts");
                AddOfflineAccount_Click(this, new RoutedEventArgs());
            }
        }
        finally { _showingMicrosoftSetup = false; }
    }

    private async Task LaunchGame()
    {
        if (_launching || _addingAccount || _authCts != null || _showingMicrosoftSetup) return;
        if (_modsBusy)
        {
            // An add/update/delete on the Mods page is writing to Mods; the installers must not run next to it.
            StatusText.Text = "A mod change on the Mods page is still being applied. Launch again when it has finished.";
            return;
        }
        if (string.IsNullOrWhiteSpace(_selectedAccount))
        {
            StatusText.Text = "Add or select an account before launching.";
            NavigateTo("Accounts");
            return;
        }
        // Resolve and validate identity before touching game files or starting downloads.
        string selectedUser = ResolveLaunchAccountName();
        string requestedLaunchOverride = _launchAccountOverride;
        bool isOffline = GetAccountSummaries().Any(a => a.type == "offline" && string.Equals(a.username, selectedUser, StringComparison.OrdinalIgnoreCase));
        if (!isOffline && (!Guid.TryParse(settings.MicrosoftClientId, out var configuredId) || configuredId == Guid.Empty))
        {
            StatusText.Text = "Microsoft login setup required. Add an offline account to try local play.";
            await ShowMicrosoftSetupDialogAsync();
            return;
        }
        var guardProfile = _profileService.GetActiveProfile();
        if (!settings.AllowMultiInstance && IsGameRunningFor(_pathService.GetProfileDirectory(guardProfile)))
        {
            await ShowLadsDialogAsync("⚠️ Game Already Running",
                $"Minecraft is already running for '{guardProfile.Name}'. Please turn on 'Allow launching multiple copies' in settings if you want to open another instance.");
            return;
        }
        // A waiting launcher update brings the current Lads pack and LadsCore: offer it before playing with the old ones.
        if (_readyUpdateVersion is { } update && !UpdateBlocked()
            && await ShowLadsDialogAsync("Update ready", $"An update is ready (v{update}). Restart and update now? (recommended)",
                "Update now", "Play anyway"))
        {
            UpdateNow_Click(this, new RoutedEventArgs());
            return;
        }

        _launching = true;
        DiscordPresence.Launching(guardProfile.MinecraftVersion);
        bool gameStarted = false; // from then on the launch shows on Discord until the game window is up (WatchForGameWindowAsync)
        using var launchCts = new CancellationTokenSource();
        _launchCts = launchCts;
        var token = launchCts.Token;
        CancelLaunchButton.Content = "Cancel";
        CancelLaunchButton.IsEnabled = true;
        LaunchButton.IsEnabled = false;
        GameLaunchOverlay.IsVisible = true;
        RenderModsInventory(); // toggles are off while launching
        try
        {
            GameLaunchProgressBar.Value = 0;
            GameLaunchStatusText.Text = "Initializing...";

            StatusText.Text = "Initializing...";
            Log("[Launcher] Starting launch sequence...");

            var activeProfile = _profileService.GetActiveProfile();
            string gameDirectory = _pathService.GetProfileDirectory(activeProfile);
            string launchVersionId = GameVersionPolicy.ResolveVersionId(activeProfile);
            if (GameVersionPolicy.RequiresFabric(activeProfile.MinecraftVersion) && string.IsNullOrWhiteSpace(activeProfile.FabricVersion))
                throw new InvalidOperationException("The Lads Client profile requires a Fabric loader.");
            // Shared worlds/packs/server list (and options.txt unless isolated). Null: the user cancelled at the sharing prompt.
            if (await PrepareSharedContentForLaunchAsync(activeProfile, gameDirectory, token) is not { } prepared) return;
            // The server list was prepared for this LadsCore state (Core reads the shared list, a profile without it gets a copy).
            bool coreRequested = SharedContentService.IsCoreRequested(gameDirectory, out _);
            settings.InstancePath = gameDirectory;

            var path = new MinecraftPath(gameDirectory);
            var launcher = new MinecraftLauncher(path);

            _lastBytes = 0;
            _lastDownloadTime = DateTime.UtcNow;

            long lastProgressTick = 0;
            launcher.ByteProgressChanged += (sender, args) =>
            {
                long nowTick = Environment.TickCount64;
                if (nowTick - Interlocked.Read(ref lastProgressTick) < 100 && args.ProgressedBytes < args.TotalBytes) return;
                Interlocked.Exchange(ref lastProgressTick, nowTick);
                Dispatcher.UIThread.InvokeAsync(() =>
                {
                    if (args.TotalBytes > 0)
                    {
                        long progressPercentage = (args.ProgressedBytes * 100) / args.TotalBytes;

                        var now = DateTime.UtcNow;
                        var elapsed = (now - _lastDownloadTime).TotalSeconds;
                        if (elapsed >= 0.5)
                        {
                            var bytesDiff = args.ProgressedBytes - _lastBytes;
                            double mbPerSec = (bytesDiff / elapsed) / (1024.0 * 1024.0);
                            DownloadSpeedText.Text = $"{mbPerSec:F1} MB/s";
                            _lastDownloadTime = now;
                            _lastBytes = args.ProgressedBytes;
                        }

                        double currentMb = args.ProgressedBytes / (1024.0 * 1024.0);
                        double totalMb = args.TotalBytes / (1024.0 * 1024.0);
                        GameLaunchStatusText.Text = $"Downloading assets: {currentMb:F1}MB / {totalMb:F1}MB ({progressPercentage}%)";
                        GameLaunchProgressBar.Maximum = 100;
                        GameLaunchProgressBar.Value = (int)progressPercentage;
                    }
                });
            };

            launcher.FileProgressChanged += (sender, args) =>
            {
                Dispatcher.UIThread.InvokeAsync(() =>
                {
                    int percentage = args.TotalTasks > 0 ? (args.ProgressedTasks * 100 / args.TotalTasks) : 0;
                    GameLaunchStatusText.Text = $"Downloading assets... {percentage}% ({args.Name})";
                    GameLaunchProgressBar.Maximum = args.TotalTasks;
                    GameLaunchProgressBar.Value = args.ProgressedTasks;
                });

            };

            GameLaunchStatusText.Text = "Checking version...";

            // The chosen identity was captured before the first asynchronous launch step.
            if (selectedUser != _selectedAccount)
                Log($"[Launcher] Launching with alt account '{selectedUser}' (main stays '{_selectedAccount}').");
            MSession session;
            if (isOffline)
            {
                session = AccountIdentity.CreateOfflineSession(selectedUser);
            }
            else
            {
                var msAccount = loginHandler.AccountManager.GetAccounts().FirstOrDefault(a => (a as CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount)?.Profile?.Username == selectedUser);

                try
                {
                    if (!Guid.TryParse(settings.MicrosoftClientId, out var registeredId) || registeredId == Guid.Empty)
                        throw new AccountVerificationException("Configure your approved Microsoft application ID in Settings before launching a Microsoft account.");
                    if (msAccount == null)
                    {
                        throw new InvalidOperationException("The selected account is no longer saved. Add it again before launching.");
                    }
                    else
                    {
                        try
                        {
                            // Use cached refresh tokens — no UI
                            GameLaunchStatusText.Text = "Logging in silently...";
                            session = await loginHandler.AuthenticateSilently(msAccount, token);
                        }
                        catch (Exception silentEx) when (MicrosoftAccountService.NeedsInteractiveLogin(silentEx) && !token.IsCancellationRequested)
                        {
                            // Tokens expired/revoked: fall back to the sign-in window
                            Log("[Auth] The selected account needs Microsoft sign-in again.");
                            GameLaunchStatusText.Text = "Session expired — please sign in again...";
                            _authCts?.Cancel();
                            _authCts = CancellationTokenSource.CreateLinkedTokenSource(token);
                            _authCts.CancelAfter(TimeSpan.FromMinutes(16));
                            session = await loginHandler.AuthenticateInteractively(msAccount, cancellationToken: _authCts.Token);
                        }
                    }
                }
                catch (PlatformNotSupportedException ex)
                {
                    GameLaunchStatusText.Text = "Login failed: default browser could not be opened.";
                    Log($"[Auth ERROR] Platform not supported: {ex.Message}");
                    throw;
                }
                catch (Exception ex) when (!token.IsCancellationRequested)
                {
                    GameLaunchStatusText.Text = "Login failed.";
                    Log($"[Auth ERROR] {MicrosoftAccountService.DescribeError(ex)}");
                    throw new InvalidOperationException(MicrosoftAccountService.DescribeError(ex));
                }
                finally
                {
                    _msalProvider?.CompleteDialog();
                    _authCts?.Dispose();
                    _authCts = null;
                }
            }

            LoadAccounts();
            await WriteLaunchAccountFilesAsync(session, isOffline, gameDirectory);
            GameLaunchStatusText.Text = $"Welcome, {session.Username}!";
            Log($"[Auth] Logged in as {session.Username}");

            Log($"[Launcher] Building process for {launchVersionId}...");

            // QuickLaunch: skip asset verification for a faster startup.
            // If the game fails to start, the user should turn QuickLaunch off.
            System.Diagnostics.Process process;
            var launchOpt = new MLaunchOption
            {
                MaximumRamMb = Math.Max(1024, settings.MaxRamMb),
                MinimumRamMb = Math.Clamp(settings.MinRamMb, 256, Math.Max(1024, settings.MaxRamMb)),
                FullScreen = settings.FullscreenOnLaunch,
                Session = session,
                JavaPath = settings.JavaPath
            };

            int requiredJava = GameVersionPolicy.GetRequiredJavaMajor(activeProfile.MinecraftVersion, activeProfile.JavaMajorVersion > 0 ? activeProfile.JavaMajorVersion : null);
            if (!string.IsNullOrEmpty(activeProfile.CustomJavaPath))
            {
                launchOpt.JavaPath = activeProfile.CustomJavaPath;
            }
            else if (!settings.AutoDetectJava)
            {
                launchOpt.JavaPath = settings.JavaPath;
            }
            else
            {
                try
                {
                    GameLaunchStatusText.Text = $"Verifying Java {requiredJava}...";
                    string resolvedJava = await _javaService.EnsureJavaAsync(requiredJava, new Progress<double>(p =>
                    {
                        Dispatcher.UIThread.Post(() =>
                        {
                            GameLaunchStatusText.Text = $"Downloading Java {requiredJava} runtime: {p:F0}%";
                            GameLaunchProgressBar.Value = (int)p;
                        });
                    }), cancellationToken: token);
                    launchOpt.JavaPath = resolvedJava;
                }
                catch (Exception jEx)
                {
                    throw new InvalidOperationException($"Could not prepare Java {requiredJava} for Minecraft {activeProfile.MinecraftVersion}.", jEx);
                }
            }

            if (_javaService.GetJavaMajorVersion(launchOpt.JavaPath) != requiredJava)
                throw new InvalidOperationException($"Select a Java {requiredJava} installation for Minecraft {activeProfile.MinecraftVersion}.");
            await RunPackwizInstaller(activeProfile, launchOpt.JavaPath, token);
            await GraphicsRenderer.PrepareAsync(gameDirectory, activeProfile.MinecraftVersion, settings.GraphicsRenderer, message => Dispatcher.UIThread.Post(() => StatusText.Text = message), token);
            await BundledModInstaller.InstallAsync(AppContext.BaseDirectory, gameDirectory, activeProfile.MinecraftVersion, token);
            // Without a loader (Fabric, or Forge on 1.8.9) nothing in Mods loads, as in LaunchService: no choices to apply.
            bool forge = GameVersionPolicy.UsesForge(activeProfile.MinecraftVersion);
            if ((forge || !string.IsNullOrWhiteSpace(activeProfile.FabricVersion)) && !await InstallClientModsAsync(gameDirectory, activeProfile.MinecraftVersion, token)) return;
            // OptiFine is downloaded from optifine.net and verified; if that fails the game starts without it (warned below).
            string? optiFineWarning = forge ? await Task.Run(() => OptiFineInstaller.InstallAsync(_pathService.BaseDirectory, gameDirectory,
                status: message => Dispatcher.UIThread.Post(() => GameLaunchStatusText.Text = message), cancellationToken: token)) : null;
            if (optiFineWarning != null) Log($"[OptiFine] {optiFineWarning}");
            // A dependency fix chosen at the prompt above can switch LadsCore: prepare the server list again for the new state.
            if (SharedContentService.IsCoreRequested(gameDirectory, out _) != coreRequested
                && await PrepareSharedContentForLaunchAsync(activeProfile, gameDirectory, token, prepared.WithoutSharing) == null) return;
            // The final mod set: the in-game Mods view reads this snapshot, and the running marker records its enabled ids.
            GameLaunchStatusText.Text = "Checking mods...";
            var launchInventory = await _modInventoryService.BuildAsync(AppContext.BaseDirectory, gameDirectory, activeProfile.MinecraftVersion, token);
            await _modInventoryService.WriteSnapshotAsync(launchInventory, token);
            var loadedMods = ModInventoryView.EnabledJarIds(launchInventory);

            if (settings.AutoRejoinServer)
            {
                // Prefer a manually-picked server; fall back to the last log-detected one
                string rejoinIp = !string.IsNullOrEmpty(settings.QuickLaunchServerIp)
                    ? settings.QuickLaunchServerIp
                    : settings.LastServerIp;

                if (!string.IsNullOrEmpty(rejoinIp))
                {
                    // Parse host:port if present
                    int colon = rejoinIp.LastIndexOf(':');
                    if (colon > 0 && int.TryParse(rejoinIp.Substring(colon + 1), out int parsedPort))
                    {
                        launchOpt.ServerIp = rejoinIp.Substring(0, colon);
                        launchOpt.ServerPort = parsedPort;
                    }
                    else
                    {
                        launchOpt.ServerIp = rejoinIp;
                        if (settings.LastServerPort > 0 && string.IsNullOrEmpty(settings.QuickLaunchServerIp))
                            launchOpt.ServerPort = settings.LastServerPort;
                    }
                    Log($"[Launcher] Auto-Rejoin: {launchOpt.ServerIp}:{launchOpt.ServerPort}");
                }
            }

            // Ensure the Fabric version JSON exists locally; if not, automatically download & install it via FabricInstaller
            string versionDir = Path.Combine(gameDirectory, "versions", launchVersionId);
            string versionJson = Path.Combine(versionDir, launchVersionId + ".json");
            if (!File.Exists(versionJson))
            {
                var fabricMatch = Regex.Match(launchVersionId, @"fabric-loader-(?<loader>[\d\.]+)-(?<mc>[\w\.\-]+)");
                if (fabricMatch.Success)
                {
                    string loaderVer = fabricMatch.Groups["loader"].Value;
                    string mcVer = fabricMatch.Groups["mc"].Value;
                    try
                    {
                        GameLaunchStatusText.Text = $"Installing Fabric Loader ({loaderVer} for MC {mcVer})...";
                        Log($"[Launcher] Auto-installing Fabric Loader {loaderVer} for Minecraft {mcVer} into '{gameDirectory}'...");
                        var fabricInstaller = new CmlLib.Core.ModLoaders.FabricMC.FabricInstaller(_httpClient);
                        await fabricInstaller.Install(mcVer, loaderVer, path);
                        Log($"[Launcher] Fabric Loader installation complete for {launchVersionId}");
                    }
                    catch (Exception fEx)
                    {
                        throw new InvalidOperationException($"Could not install Fabric {loaderVer} for Minecraft {mcVer}.", fEx);
                    }
                }
            }
            if (GameVersionPolicy.UsesForge(activeProfile.MinecraftVersion))
                await LaunchService.InstallForgeAsync(launcher, gameDirectory, _httpClient,
                    message => Dispatcher.UIThread.Post(() => GameLaunchStatusText.Text = message), token);

            string installedMarker = Path.Combine(gameDirectory, "versions", launchVersionId, ".lads-verified");
            if (settings.QuickLaunch && File.Exists(installedMarker))
            {
                GameLaunchStatusText.Text = "Quick launching (skipping verification)...";
                Log("[Launcher] QuickLaunch enabled — skipping asset verification.");
                process = await launcher.BuildProcessAsync(launchVersionId, launchOpt, token);
            }
            else
            {
                process = await launcher.InstallAndBuildProcessAsync(launchVersionId, launchOpt, token);
                Directory.CreateDirectory(Path.GetDirectoryName(installedMarker)!);
                await File.WriteAllTextAsync(installedMarker, DateTimeOffset.UtcNow.ToString("O"));
            }

            // The last point a cancel stops the launch (steps without a token, like the Fabric install, end here). Nothing
            // below awaits before process.Start(), so the overlay's Cancel cannot run in between.
            token.ThrowIfCancellationRequested();
            process.StartInfo.UseShellExecute = false;
            process.StartInfo.RedirectStandardOutput = true;
            process.StartInfo.RedirectStandardError = true;
            process.StartInfo.CreateNoWindow = true;
            GameSession.Configure(process.StartInfo, gameDirectory, SharedContentService.Instance.Root);

            process.OutputDataReceived += (s, ev) => { if (GameSession.ReadableOutput(ev.Data) is { } line) Log($"[Game] {line}"); };
            process.ErrorDataReceived += (s, ev) => { if (GameSession.ReadableOutput(ev.Data) is { } line) Log($"[Game ERROR] {line}"); };

            // Apply fullscreen setting by patching options.txt before launch.
            if (settings.FullscreenOnLaunch)
            {
                try
                {
                    string optFile = System.IO.Path.Combine(gameDirectory, "options.txt");
                    string optContent = System.IO.File.Exists(optFile) ? System.IO.File.ReadAllText(optFile) : "";
                    var lines = new System.Collections.Generic.List<string>(optContent.Split('\n'));
                    bool found = false;
                    for (int li = 0; li < lines.Count; li++)
                    {
                        if (lines[li].StartsWith("fullscreen:"))
                        {
                            lines[li] = "fullscreen:true";
                            found = true;
                            break;
                        }
                    }
                    if (!found) lines.Add("fullscreen:true");
                    await LockFiles.WriteAtomicallyAsync(optFile, System.Text.Encoding.UTF8.GetBytes(string.Join('\n', lines)));
                }
                catch (Exception ex) { Log($"[Launch] Could not set fullscreen in options.txt: {ex.Message}"); }
            }

            GameLaunchStatusText.Text = "Launching game...";
            Log("[Launcher] Starting game process...");
            WriteLadsVersions(gameDirectory, activeProfile.MinecraftVersion);
            EnforceEssentialSettings(gameDirectory);
            process.Start();
            _runningProcesses[process] = gameDirectory;
            DiscordPresence.GameRunning(gameStarted = true);
            // Running marker now; on exit (once): marker removed, server list reconciled, then OnGameExitedAsync.
            var sessionMessages = optiFineWarning == null ? new List<string>() : new List<string> { optiFineWarning };
            GameSession.Attach(process, gameDirectory, loadedMods, message =>
                {
                    lock (sessionMessages) sessionMessages.Add(message);
                    Log($"[Shared] {message}");
                },
                afterExit: () => OnGameExitedAsync(process, activeProfile, gameDirectory, sessionMessages));
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();

            // A next-launch choice is consumed only after a process actually starts.
            // Preserve a different choice made while this launch was preparing.
            if (!string.IsNullOrEmpty(requestedLaunchOverride)
                && string.Equals(_launchAccountOverride, requestedLaunchOverride, StringComparison.OrdinalIgnoreCase)
                && string.Equals(selectedUser, requestedLaunchOverride, StringComparison.OrdinalIgnoreCase))
            {
                _launchAccountOverride = "";
                PopulateLaunchSelector(GetAccountSummaries().Select(account => account.username).ToList());
            }

            // Pop the startup splash immediately so there is never a dead gap
            // between pressing Launch and the Minecraft window appearing.
            var startupSplash = new Views.GameStartupSplash();
            startupSplash.SetGameVersion(activeProfile.MinecraftVersion);
            // Until the game window appears: the exit then says "Launch cancelled" (OnGameExitedAsync), not a crash.
            startupSplash.CancelRequested += () =>
            {
                Log("[Launcher] Launch cancelled while the game was starting; stopping it.");
                GameSession.Cancel(process);
            };
            startupSplash.Show();
            _ = WatchForGameWindowAsync(process, startupSplash);

            lock (sessionMessages) StatusText.Text = sessionMessages.Count > 0 ? "Game running. " + string.Join(" ", sessionMessages) : "Game running.";
            GameLaunchOverlay.IsVisible = false;

            // Hide to tray after launch, unless the user wants the launcher to stay open.
            if (settings.CloseToTray && !settings.KeepLauncherOpen)
                this.Hide();
        }
        catch (Exception ex) when (token.IsCancellationRequested)
        {
            // Whatever a step threw once Cancel was pressed (often its OperationCanceledException) ends the launch quietly.
            StatusText.Text = "Launch cancelled.";
            Log(ex is OperationCanceledException ? "[Launcher] Launch cancelled." : $"[Launcher] Launch cancelled ({ex.Message}).");
        }
        catch (Exception ex)
        {
            StatusText.Text = "Launch failed: " + ex.Message;
            Log($"[Launch ERROR] {ex.Message}");
            throw;
        }
        finally
        {
            _launchCts = null;
            _launching = false;
            if (!gameStarted) DiscordPresence.Launching(null);
            GameLaunchOverlay.IsVisible = false;
            LaunchButton.IsEnabled = true;
            RenderModsInventory();
        }
    }

    /// <summary>
    /// Installs the pinned pack. When the requested mods cannot load together (typically a library switched off while a mod
    /// that needs it stays on), it offers the coherent fixes from the dependency planner: disable the mods that need it, or
    /// switch the library back on. The choice is saved and the install tried once more. False: cancelled (status says why).
    /// </summary>
    private async Task<bool> InstallClientModsAsync(string gameDirectory, string minecraftVersion, CancellationToken token)
    {
        // The launch overlay shows it too, or a long download looks like the step before it hanging.
        Action<string> status = message => Dispatcher.UIThread.Post(() => StatusText.Text = GameLaunchStatusText.Text = message);
        try
        {
            await ClientModInstaller.InstallAsync(AppContext.BaseDirectory, gameDirectory, minecraftVersion, status, token);
            return true;
        }
        catch (ClientModDependencyException problem)
        {
            Log($"[Mods] {problem.Message}");
            var inventory = await _modInventoryService.BuildAsync(AppContext.BaseDirectory, gameDirectory, minecraftVersion, token);
            var (disable, enable) = _modStateService.DependencyFixes(inventory, problem);
            var fixes = new List<(string Text, ModTogglePlan Plan, bool Danger)>();
            if (disable != null) fixes.Add(("Disable " + ModNames(inventory, disable.TargetIds.Concat(disable.AlsoDisable)), disable, true));
            if (enable != null) fixes.Add(("Re-enable " + ModNames(inventory, enable.TargetIds.Concat(enable.AlsoEnable)), enable, false));
            if (fixes.Count == 0) throw; // nothing coherent to offer: the message names each problem and its fix
            var message = problem.Message + "\n\nChoose a fix; it is saved for this profile and the launch continues."
                + string.Concat(fixes.Where(f => f.Plan.Warnings.Count > 0).Select(f => $"\n\n{f.Text}: {string.Join(" ", f.Plan.Warnings)}"));
            int picked = await ShowLadsChoiceAsync("Mods cannot load together", message, fixes.Select(f => (f.Text, f.Danger, true)).ToList());
            if (picked < 0)
            {
                StatusText.Text = "Launch cancelled. " + problem.Message;
                Log("[Launcher] Launch cancelled at the mod dependency prompt.");
                return false;
            }
            var result = await _modStateService.ApplyAsync(gameDirectory, inventory, fixes[picked].Plan, token);
            if (!result.Success) throw new InvalidOperationException(result.Message);
            Log($"[Mods] {fixes[picked].Text}: {result.Message}");
            // Once: a second failure ends the launch with its own message. (The Mods page reloads when the launch is over.)
            await ClientModInstaller.InstallAsync(AppContext.BaseDirectory, gameDirectory, minecraftVersion, status, token);
            return true;
        }
    }

    /// <summary>Browse install/update of a mod: downloaded next to Mods, then placed by UserModFiles, which replaces only
    /// your own copy of the same Fabric id (read from the download), refuses pack/LadsCore mods and never overwrites.</summary>
    private async Task<string?> InstallBrowsedModAsync(ModSearchItem item, string downloadUrl, string fileName, IProgress<double> progress)
    {
        var (gameDirectory, version) = ModsTarget();
        var temp = await DownloadToModCacheAsync(downloadUrl, gameDirectory, null, progress);
        try
        {
            // A launch or another Mods change that started during the download is writing to Mods right now.
            if (_launching || _modsBusy)
                throw new InvalidOperationException("A launch or another mod change started during the download, so nothing was installed. Install it again when it has finished.");
            // Held until the jar is placed, so a launch cannot start its installers in between (LaunchGame checks it).
            _modsBusy = true;
            try
            {
                var inventory = await _modInventoryService.BuildAsync(AppContext.BaseDirectory, gameDirectory, version);
                var path = await Task.Run(() => UserModFiles.Install(inventory, temp, fileName));
                Log($"[Installer] Installed {item.Name} as '{path}'");
                ModsStatus($"Installed {item.Name} as {Path.GetFileName(path)}.");
            }
            finally { _modsBusy = false; }
        }
        finally
        {
            if (File.Exists(temp)) File.Delete(temp);
        }
        await AfterModsChangedAsync();
        return null;
    }

    /// <summary>
    /// After a game exits (GameSession already removed its marker and reconciled the server list): settings back to the
    /// shared copy and the 1.21.x screenshot copy off the UI thread, then the account request, window and crash handling.
    /// Every failure ends up in the status line and the log.
    /// </summary>
    private async Task OnGameExitedAsync(Process process, TheLadsLauncher.Models.LauncherProfile profile, string gameDirectory, List<string> sessionMessages)
    {
        var notes = new List<string>();
        lock (sessionMessages) notes.AddRange(sessionMessages);
        try
        {
            await _profileService.SyncProfileToSharedAsync(profile, reconcileServerList: false);
        }
        catch (Exception ex)
        {
            notes.Add($"Game settings (options.txt) were not synced back: {ex.Message}");
        }
        if (settings.SyncScreenshotsToGlobal)
        {
            var error = await Task.Run(() => SyncScreenshotsToGlobal(gameDirectory));
            if (error != null) notes.Add(error);
        }
        await Dispatcher.UIThread.InvokeAsync(() =>
        {
            _runningProcesses.Remove(process);
            DiscordPresence.GameRunning(_runningProcesses.Count > 0);
            ApplyNextAccountRequest(gameDirectory);
            bool relaunchRequested = ApplyNextVersionRequest(gameDirectory);
            int exitCode = 0;
            try { exitCode = process.ExitCode; }
            catch (InvalidOperationException ex) { Log($"[Launcher] Could not read the game's exit code: {ex.Message}"); }

            // Cancelled from the startup splash: killed on purpose, so no crash report or auto-relaunch.
            bool cancelled = GameSession.WasCancelled(process);
            // Re-show the launcher when the game closes, unless the user opted out (a cancel always shows it).
            if ((!settings.KeepClosedOnExit || cancelled) && !relaunchRequested)
            {
                this.Show();
                this.WindowState = WindowState.Normal;
            }
            DownloadSpeedText.Text = "0 MB/s";
            foreach (var note in notes) Log($"[Launcher] {note}");
            string suffix = notes.Count > 0 ? " " + string.Join(" ", notes) : "";

            if (cancelled)
            {
                StatusText.Text = "Launch cancelled." + suffix;
                Log("[Launcher] The game was stopped before its window appeared (launch cancelled).");
            }
            else if (exitCode != 0)
            {
                StatusText.Text = $"Game crashed! (exit code: {exitCode}){suffix}";
                Log($"[Launcher] Game exited with code {exitCode}");
                HandleCrashDetection(gameDirectory);
            }
            else
            {
                StatusText.Text = "Game exited normally." + suffix;
                Log("[Launcher] Game exited normally.");
            }
        });
    }

    private void ApplyNextAccountRequest(string gameDirectory)
    {
        string requestFile = Path.Combine(gameDirectory, "lads_next_account.json");
        if (!File.Exists(requestFile)) return;
        try
        {
            using var request = JsonDocument.Parse(File.ReadAllText(requestFile));
            string name = request.RootElement.GetProperty("username").GetString() ?? "";
            string uuid = request.RootElement.GetProperty("uuid").GetString() ?? "";
            string type = request.RootElement.GetProperty("type").GetString() ?? "";
            var existing = GetAccountSummaries().FirstOrDefault(a =>
                string.Equals(a.username, name, StringComparison.OrdinalIgnoreCase) &&
                string.Equals(a.uuid.Replace("-", ""), uuid.Replace("-", ""), StringComparison.OrdinalIgnoreCase));
            if (existing == null && type == "offline" &&
                string.Equals(AccountIdentity.OfflineUuid(name), uuid, StringComparison.OrdinalIgnoreCase) &&
                !GetAccountSummaries().Any(a => string.Equals(a.username, name, StringComparison.OrdinalIgnoreCase)))
            {
                settings.OfflineAccounts.Add(AccountIdentity.NormalizeOfflineName(name));
                settings.Save();
                existing = new AccountSummary(name, uuid, "offline");
            }
            if (existing == null) throw new InvalidOperationException("Requested account is not saved in the launcher.");
            _launchAccountOverride = existing.username;
            LoadAccounts();
            Log($"[Accounts] Next launch will use {existing.username}.");
            File.Delete(requestFile);
        }
        catch (Exception) { Log("[Accounts] Could not apply the game's account selection. Select an account in the launcher."); }
    }

    private void WriteLadsVersions(string gameDirectory, string currentVersion)
    {
        try
        {
            var versions = _profileService.GetProfiles()
                .Select(p => p.MinecraftVersion)
                .Where(v => !string.IsNullOrWhiteSpace(v))
                .Distinct()
                .ToList();
            if (!versions.Contains("26.3")) versions.Add("26.3");
            if (!versions.Contains("26.2")) versions.Add("26.2");
            if (!versions.Contains("1.8.9")) versions.Add("1.8.9");

            var obj = new JsonObject
            {
                ["current"] = currentVersion,
                ["versions"] = new JsonArray(versions.Select(v => (JsonNode)JsonValue.Create(v)!).ToArray())
            };
            string path = Path.Combine(gameDirectory, "lads_versions.json");
            File.WriteAllText(path, obj.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
        }
        catch (Exception ex)
        {
            Log($"[Launcher] Could not write lads_versions.json: {ex.Message}");
        }
    }

    private bool ApplyNextVersionRequest(string gameDirectory)
    {
        string requestFile = Path.Combine(gameDirectory, "lads_next_version.json");
        if (!File.Exists(requestFile)) return false;
        try
        {
            using var doc = JsonDocument.Parse(File.ReadAllText(requestFile));
            string targetVersion = doc.RootElement.GetProperty("version").GetString() ?? "";
            File.Delete(requestFile);
            if (string.IsNullOrWhiteSpace(targetVersion)) return false;

            Log($"[Launcher] Version switch requested to: {targetVersion}");
            var targetProfile = _profileService.GetProfiles().FirstOrDefault(p =>
                string.Equals(p.MinecraftVersion, targetVersion, StringComparison.OrdinalIgnoreCase) ||
                string.Equals(p.Name, targetVersion, StringComparison.OrdinalIgnoreCase));

            if (targetProfile == null)
            {
                targetProfile = _profileService.CreateProfile(targetVersion, targetVersion, targetVersion.StartsWith("26") ? 25 : 8, false, "");
            }

            _profileService.SetActiveProfile(targetProfile.Id);
            ApplyProfile(targetProfile, true);
            LoadProfilesUI();
            Log($"[Launcher] Switched active profile to: {targetProfile.Name} ({targetProfile.MinecraftVersion}). Auto-relaunching...");

            _ = Dispatcher.UIThread.InvokeAsync(async () =>
            {
                await Task.Delay(500);
                LaunchButton_Click(null, new RoutedEventArgs());
            });
            return true;
        }
        catch (Exception ex)
        {
            Log($"[Launcher] Could not apply version switch: {ex.Message}");
            return false;
        }
    }

    private void EnforceEssentialSettings(string gameDirectory)
    {
        try
        {
            string essentialDir = Path.Combine(gameDirectory, "essential");
            string configFile = Path.Combine(essentialDir, "config.toml");
            string onboardingFile = Path.Combine(essentialDir, "onboarding.json");

            if (File.Exists(configFile))
            {
                string text = File.ReadAllText(configFile);
                string updated = text;
                var list = new (string Section, string Key, string Value)[]
                {
                    ("privacy.general", "display_current_server", "true"),
                    ("general.general", "streamer_mode", "false"),
                    ("general.general", "telemetry", "false"),
                    ("general.online_status", "show_essential_indicator_on_nametags", "false"),
                    ("general.online_status", "show_essential_indicator_on_tab", "false"),
                    ("general.experience", "show_nameplate_in_third_person", "false"),
                    ("quality_of_life.nameplate", "show_my_nameplate_in_third-person", "false"),
                    ("quality_of_life.screenshots", "essential_screenshots", "false"),
                    ("quality_of_life.screenshots", "vanilla_screenshot_message", "true"),
                    ("emotes.general", "disable_emotes", "true"),
                    ("cosmetics.general", "disable_cosmetics", "true"),
                    ("quality_of_life.discord_integration", "set_activity_status_on_discord", "false")
                };

                foreach (var (section, key, val) in list)
                {
                    string keyPattern = $@"(?m)^([\t ]*{Regex.Escape(key)}[\t ]*=[\t ]*)[^\r\n]*";
                    if (Regex.IsMatch(updated, keyPattern))
                    {
                        updated = Regex.Replace(updated, keyPattern, $"${{1}}{val}");
                    }
                    else
                    {
                        string newline = updated.Contains("\r\n", StringComparison.Ordinal) ? "\r\n" : "\n";
                        var table = Regex.Match(updated, $@"(?m)^[\t ]*\[[\t ]*{Regex.Escape(section)}[\t ]*\][^\r\n]*");
                        if (table.Success)
                        {
                            updated = updated.Insert(table.Index + table.Length, newline + "\t\t" + $"{key} = {val}");
                        }
                        else
                        {
                            string separator = updated.Length == 0 || updated.EndsWith('\n') ? "" : newline;
                            updated = updated + separator + $"[{section}]" + newline + $"\t{key} = {val}" + newline;
                        }
                    }
                }
                if (updated != text) File.WriteAllText(configFile, updated);
            }

            if (File.Exists(onboardingFile))
            {
                string text = File.ReadAllText(onboardingFile);
                var root = JsonNode.Parse(text) as JsonObject;
                if (root != null)
                {
                    bool changed = false;
                    if (root["sent_auto_update_telemetry"]?.GetValue<bool>() != false)
                    {
                        root["sent_auto_update_telemetry"] = false;
                        changed = true;
                    }
                    if (root["allow_telemetry"]?.GetValue<bool>() != false)
                    {
                        root["allow_telemetry"] = false;
                        changed = true;
                    }
                    if (changed) File.WriteAllText(onboardingFile, root.ToJsonString(new JsonSerializerOptions { WriteIndented = true }) + "\n");
                }
            }
        }
        catch (Exception ex)
        {
            Log($"[Launcher] Could not enforce Essential settings: {ex.Message}");
        }
    }

    // Keeps the startup splash visible until the game window actually exists
    // (the mod's early window makes that fast), then closes it.
    private async Task WatchForGameWindowAsync(System.Diagnostics.Process process, Views.GameStartupSplash splash)
    {
        try
        {
            var sw = System.Diagnostics.Stopwatch.StartNew();
            while (sw.Elapsed < TimeSpan.FromSeconds(120))
            {
                if (process.HasExited)
                {
                    Log("[Launcher] Game exited before its window appeared.");
                    splash.SetStatus("Minecraft stopped before opening");
                    break;
                }
                try
                {
                    process.Refresh();
                    if (process.MainWindowHandle != IntPtr.Zero)
                    {
                        Log($"[Launcher] Game window detected after {sw.Elapsed.TotalSeconds:F1}s.");
                        splash.SetStatus("Minecraft is ready to show");
                        break;
                    }
                }
                catch { break; }
                await Task.Delay(150);
            }
        }
        catch { }

        // Small grace period so the game's first (black) frame is on screen
        // before the splash disappears.
        await Task.Delay(500);
        DiscordPresence.Launching(null);
        Dispatcher.UIThread.Post(() => { try { splash.Close(); } catch { } });
    }

    // Resolve only the explicitly selected profile's exact version.
    private string ResolveLaunchVersionId() => GameVersionPolicy.ResolveVersionId(_profileService.GetActiveProfile());

    // ═══════════════════════════════════════
    //  CRASH DETECTION
    // ═══════════════════════════════════════

    /// <summary>
    /// 1.21.x saves screenshots in the profile; copy them to the shared screenshots folder (26.x writes there directly).
    /// Runs off the UI thread; returns the error to show, or null.
    /// </summary>
    private string? SyncScreenshotsToGlobal(string gameDirectory)
    {
        try
        {
            string instanceScreenshots = Path.Combine(gameDirectory, "screenshots");
            string globalScreenshots = SharedContentService.Instance.ScreenshotsDirectory;

            if (!Directory.Exists(instanceScreenshots)) return null;
            Directory.CreateDirectory(globalScreenshots);

            int count = 0;
            foreach (string src in Directory.GetFiles(instanceScreenshots))
            {
                string dst = Path.Combine(globalScreenshots, Path.GetFileName(src));
                if (!File.Exists(dst) || File.GetLastWriteTime(src) > File.GetLastWriteTime(dst))
                {
                    File.Copy(src, dst, overwrite: true);
                    count++;
                }
            }
            if (count > 0)
                Log($"[Sync] Copied {count} screenshot(s) to the shared screenshots folder '{globalScreenshots}'.");
            return null;
        }
        catch (Exception ex)
        {
            Log($"[Sync] Screenshots copy failed: {ex.Message}");
            return $"Screenshots were not copied to the shared screenshots folder: {ex.Message}";
        }
    }

    private void HandleCrashDetection(string gameDirectory)
    {
        try
        {
            string crashDir = Path.Combine(gameDirectory, "crash-reports");
            string latestLog = Path.Combine(gameDirectory, "logs", "latest.log");

            if (File.Exists(latestLog))
            {
                string logContent = File.ReadAllText(latestLog);
                var ipMatch = System.Text.RegularExpressions.Regex.Match(logContent, @"Connecting to ([a-zA-Z0-9\.\-_]+), (\d+)");
                if (ipMatch.Success)
                {
                    settings.LastServerIp = ipMatch.Groups[1].Value;
                    settings.LastServerPort = int.Parse(ipMatch.Groups[2].Value);
                    settings.Save();
                }
            }

            string crashInfo = "The game has crashed.";
            string? crashFile = null;

            if (Directory.Exists(crashDir))
            {
                var latestCrash = Directory.GetFiles(crashDir, "*.txt")
                    .OrderByDescending(File.GetLastWriteTime)
                    .FirstOrDefault();

                if (latestCrash != null && (DateTime.Now - File.GetLastWriteTime(latestCrash)).TotalMinutes < 2)
                {
                    crashFile = latestCrash;
                    var allLines = File.ReadAllLines(latestCrash);
                    var lines = allLines.Take(20).ToArray();
                    var descLine = lines.FirstOrDefault(l => l.Contains("Description:"));
                    var causeLine = allLines.FirstOrDefault(l => l.Contains("Caused by:"));

                    if (descLine != null)
                        crashInfo = descLine.Trim();
                    if (causeLine != null)
                        crashInfo += "\n" + causeLine.Trim();

                    // Include mixin/mod detail lines so the culprit is visible in the dialog
                    var detailLines = allLines
                        .Where(l => l.Contains("Mixin", StringComparison.OrdinalIgnoreCase)
                                 || l.Contains("from mod")
                                 || l.Contains("InjectionError")
                                 || l.Contains("Critical injection failure"))
                        .Select(l => l.Trim())
                        .Distinct()
                        .Take(8)
                        .ToList();
                    if (detailLines.Count > 0)
                        crashInfo += "\n" + string.Join("\n", detailLines);
                }
            }

            if (File.Exists(latestLog))
            {
                var logContent = File.ReadAllText(latestLog);
                if (logContent.Contains("Incompatible mods found"))
                {
                    crashInfo = "Incompatible mods detected! Check the Mods page to disable conflicting mods.";
                }
                else if (logContent.Contains("MixinApplyError") || logContent.Contains("MixinTransformerError"))
                {
                    crashInfo = "A mod's mixin failed to apply (MixinTransformerError). A mod is incompatible with this Minecraft version.";
                }
            }

            // The launch that crashed ran on Vulkan: suggest OpenGL, never switch for the user.
            string rendererHint = GraphicsRenderer.ReadState(gameDirectory)?.Vulkan == true ? GraphicsRenderer.VulkanCrashHint : "";
            if (rendererHint.Length > 0) crashInfo += "\n" + rendererHint;
            Log($"[Crash] {crashInfo}");

            if (settings.AutoFixCrashes)
            {
                bool fixedCrash = false;

                // AutoFixCrashes removed by user request
                // The launcher will no longer rename crashed mods to .disabled

                if (fixedCrash)
                {
                    StatusText.Text = "Crash auto-fixed. Relaunching...";
                    _ = LaunchGame();
                    return;
                }
            }

            if (settings.AutoRelaunchOnCrash)
            {
                Log("[Crash Detection] Auto-relaunching due to AutoRelaunchOnCrash setting.");
                StatusText.Text = ("Auto-relaunching after crash... " + rendererHint).TrimEnd();
                _ = LaunchGame();
                return;
            }

            ShowCrashDialog(crashInfo, crashFile);
        }
        catch (Exception ex)
        {
            Log($"[Crash Detection ERROR] {ex.Message}");
        }
    }

    private async void ShowCrashDialog(string crashInfo, string? crashFile)
    {
        var window = new Window
        {
            Title = "Game Crashed",
            Width = 550, Height = 300,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Background = new SolidColorBrush(Color.Parse("#17181B")),
            CanResize = false
        };

        var panel = new StackPanel { Margin = new Thickness(24), Spacing = 16 };

        panel.Children.Add(new TextBlock
        {
            Text = "💥 Game Crashed",
            Foreground = new SolidColorBrush(Color.Parse("#E27676")),
            FontSize = 22,
            FontWeight = FontWeight.Bold
        });

        panel.Children.Add(new TextBox
        {
            Text = crashInfo,
            IsReadOnly = true,
            TextWrapping = TextWrapping.Wrap,
            MaxHeight = 120,
            Background = new SolidColorBrush(Color.Parse("#202125")),
            Foreground = new SolidColorBrush(Color.Parse("#CCCCCC")),
            FontSize = 13,
            BorderBrush = new SolidColorBrush(Color.Parse("#3A3B42"))
        });

        var buttonRow = new StackPanel { Orientation = Avalonia.Layout.Orientation.Horizontal, Spacing = 8 };

        var relaunchBtn = new Button
        {
            Content = "Relaunch",
            Background = new SolidColorBrush(Color.Parse("#C44343")),
            Foreground = Brushes.White,
            FontWeight = FontWeight.Bold,
            Height = 38, Width = 120,
            HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            CornerRadius = new CornerRadius(8)
        };
        relaunchBtn.Click += async (s, e) =>
        {
            try
            {
                window.Close();
                await LaunchGame();
            }
            catch (Exception ex)
            {
                Log($"[Relaunch ERROR] {ex.Message}");
                StatusText.Text = "Relaunch failed: " + ex.Message;
            }
        };
        buttonRow.Children.Add(relaunchBtn);

        if (crashFile != null)
        {
            var openBtn = new Button
            {
                Content = "Open Report",
                Background = new SolidColorBrush(Color.Parse("#303137")),
                Foreground = new SolidColorBrush(Color.Parse("#AAAAAA")),
                Height = 38, Width = 120,
                HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center,
                CornerRadius = new CornerRadius(8)
            };
            openBtn.Click += (s, e) =>
            {
                Process.Start(new ProcessStartInfo(crashFile) { UseShellExecute = true });
            };
            buttonRow.Children.Add(openBtn);
        }

        var modsBtn = new Button
        {
            Content = "View Mods",
            Background = new SolidColorBrush(Color.Parse("#303137")),
            Foreground = new SolidColorBrush(Color.Parse("#AAAAAA")),
            Height = 38, Width = 120,
            HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            CornerRadius = new CornerRadius(8)
        };
        modsBtn.Click += (s, e) => { window.Close(); ReloadModsInventory(); NavigateTo("Mods"); };
        buttonRow.Children.Add(modsBtn);

        panel.Children.Add(buttonRow);
        window.Content = panel;
        await window.ShowDialog(this);
    }

    // ═══════════════════════════════════════
    //  PACKWIZ
    // ═══════════════════════════════════════

    private async Task RunPackwizInstaller(TheLadsLauncher.Models.LauncherProfile profile, string javaPath, CancellationToken token)
    {
        if (string.IsNullOrWhiteSpace(profile.PackwizUrl)) return;
        string bootstrap = Path.Combine(AppContext.BaseDirectory, "packwiz-installer-bootstrap.jar");
        if (!File.Exists(bootstrap)) bootstrap = Path.Combine(_pathService.BinDirectory, "packwiz-installer-bootstrap.jar");
        if (!File.Exists(bootstrap))
            throw new FileNotFoundException("This profile uses Packwiz, but its installer is missing. Install the bootstrap or clear this profile's Packwiz URL.");
        // packwiz-installer writes, replaces and deletes its files through the shared-folder links, i.e. in the worlds and packs
        // every version uses. A pack that manages files there is not run.
        var linked = SharedContentService.Instance.GetStatus(_pathService.GetProfileDirectory(profile))
            .Where(s => s.State is not (SharedFolderState.SeparateFolder or SharedFolderState.NotCreated)).Select(s => s.Name).ToList();
        if (linked.Count > 0)
        {
            IReadOnlyList<string> shared;
            try { shared = await PackwizIndex.FilesInFoldersAsync(profile.PackwizUrl, linked, _httpClient, token); }
            catch (Exception ex) when (ex is HttpRequestException or IOException or UnauthorizedAccessException or UriFormatException or TaskCanceledException)
            {
                throw new InvalidOperationException($"Could not read this profile's Packwiz pack '{profile.PackwizUrl}' ({ex.Message}). Check its Packwiz URL and connection.", ex);
            }
            if (shared.Count > 0)
                throw new InvalidOperationException($"This profile's Packwiz pack manages files in the folders every version shares " +
                    $"({string.Join(", ", shared.Take(5))}{(shared.Count > 5 ? $" and {shared.Count - 5} more" : "")}). Packwiz would replace or delete " +
                    "them for every version, so it was not run. Remove those files from the pack, or clear this profile's Packwiz URL in Profiles.");
        }
        using var process = new Process();
        process.StartInfo = new ProcessStartInfo(javaPath)
        {
            WorkingDirectory = settings.InstancePath,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true
        };
        foreach (string argument in new[] { "-jar", bootstrap, "--no-gui", profile.PackwizUrl })
            process.StartInfo.ArgumentList.Add(argument);
        process.Start();
        // Cancelling the launch stops packwiz-installer and what it started; the next launch updates the profile again.
        using var stop = token.Register(() => { try { process.Kill(entireProcessTree: true); } catch { } });
        Task output = process.StandardOutput.ReadToEndAsync();
        Task error = process.StandardError.ReadToEndAsync();
        await Task.WhenAll(output, error, process.WaitForExitAsync());
        token.ThrowIfCancellationRequested();
        if (process.ExitCode != 0) throw new InvalidOperationException($"Packwiz could not update this profile (exit {process.ExitCode}). Check its Packwiz URL and connection.");
        Log("[Packwiz] Profile update complete.");
    }

    private async Task PatchModAccessWideners()
    {
        string modsDir = Path.Combine(settings.InstancePath, "mods");
        if (!Directory.Exists(modsDir)) return;

        await Task.Run(() =>
        {
            foreach (string jarPath in Directory.GetFiles(modsDir, "*.jar"))
            {
                try
                {
                    if (PatchJarRecursive(jarPath))
                        Log($"[AW-Patch] Patched {Path.GetFileName(jarPath)}");
                }
                catch (Exception ex)
                {
                    Log($"[AW-Patch] Skipped {Path.GetFileName(jarPath)}: {ex.Message}");
                    try { File.Delete(jarPath + ".aw_patch.tmp"); } catch { }
                }
            }
        });
    }

    // Returns true if any patching was done. Handles JIJ (Jar-in-Jar) nested jars recursively.
    private bool PatchJarRecursive(string jarPath)
    {
        var rootPatches = new List<(string fullName, string patched)>();
        var jijPatches = new List<(string fullName, byte[] patchedBytes)>();

        using (var zip = System.IO.Compression.ZipFile.OpenRead(jarPath))
        {
            foreach (var entry in zip.Entries)
            {
                string n = entry.Name.ToLowerInvariant();
                if (n.EndsWith(".accesswidener") || n.EndsWith(".classtweaker") || n.EndsWith(".aw") || n.EndsWith(".ct"))
                {
                    string content;
                    using (var s = entry.Open()) using (var r = new System.IO.StreamReader(s)) content = r.ReadToEnd();
                    if (content.Contains("official"))
                        rootPatches.Add((entry.FullName, content.Replace("official", "intermediary")));
                }
                else if (entry.FullName.StartsWith("META-INF/jars/") && n.EndsWith(".jar"))
                {
                    byte[] nestedBytes;
                    using (var s = entry.Open()) using (var ms = new System.IO.MemoryStream())
                    { s.CopyTo(ms); nestedBytes = ms.ToArray(); }
                    string tmpNested = jarPath + "." + entry.Name + ".jij.tmp";
                    File.WriteAllBytes(tmpNested, nestedBytes);
                    bool nestedPatched = PatchJarRecursive(tmpNested);
                    if (nestedPatched) jijPatches.Add((entry.FullName, File.ReadAllBytes(tmpNested)));
                    File.Delete(tmpNested);
                }
            }
        }

        if (rootPatches.Count == 0 && jijPatches.Count == 0) return false;

        string tmp = jarPath + ".aw_patch.tmp";
        File.Copy(jarPath, tmp, true);
        using (var zip = System.IO.Compression.ZipFile.Open(tmp, System.IO.Compression.ZipArchiveMode.Update))
        {
            foreach (var (fullName, patched) in rootPatches)
            {
                zip.GetEntry(fullName)?.Delete();
                var ne = zip.CreateEntry(fullName, System.IO.Compression.CompressionLevel.Fastest);
                using var s = ne.Open(); using var w = new System.IO.StreamWriter(s); w.Write(patched);
            }
            foreach (var (fullName, bytes) in jijPatches)
            {
                zip.GetEntry(fullName)?.Delete();
                var ne = zip.CreateEntry(fullName, System.IO.Compression.CompressionLevel.NoCompression);
                using var s = ne.Open(); s.Write(bytes, 0, bytes.Length);
            }
        }
        File.Move(tmp, jarPath, true);
        return true;
    }

    // ═══════════════════════════════════════
    //  SYSTEM TRAY
    // ═══════════════════════════════════════

    private void SetupTrayIcon()
    {
        string iconPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Assets", "icon.ico");
        _trayIcon = new TrayIcon
        {
            Icon = File.Exists(iconPath) ? new WindowIcon(iconPath) : this.Icon,
            ToolTipText = "The Lads Client",
            IsVisible = true
        };

        _trayIcon.Clicked += (s, e) => { this.Show(); this.WindowState = WindowState.Normal; this.Activate(); };

        var showItem = new NativeMenuItem("Show Launcher");
        showItem.Click += (s, e) => { this.Show(); this.WindowState = WindowState.Normal; this.Activate(); };

        var exitItem = new NativeMenuItem("Exit");
        exitItem.Click += (s, e) => { FlushSettingsSave(); Environment.Exit(0); };

        var menu = new NativeMenu();
        menu.Items.Add(showItem);
        menu.Items.Add(exitItem);
        _trayIcon.Menu = menu;
    }
}

public class ParticleMeshControl : Avalonia.Controls.Control
{
    private readonly MainWindow _parent;

    public ParticleMeshControl(MainWindow parent)
    {
        _parent = parent;
        IsHitTestVisible = false;
    }

    public override void Render(DrawingContext context)
    {
        base.Render(context);
        _parent.DrawParticleMesh(context, Bounds.Width, Bounds.Height);
    }
}

public partial class MainWindow : Window
{
    public void DrawParticleMesh(DrawingContext context, double width, double height)
    {
        if (!settings.ShowParticles || settings.ReducedMotion) return;

        var (primaryHex, _, _, accentHex) = settings.GetThemeColors();
        var primaryColor = Color.Parse(primaryHex);
        var accentColor = Color.Parse(accentHex);

        // Draw nebula glow clouds first (deepest layer) — concentric soft ellipses
        foreach (var n in _nebulae)
        {
            double pulse = 0.7 + 0.3 * Math.Sin(n.Phase);
            for (int ring = 5; ring >= 1; ring--)
            {
                double t = ring / 5.0;
                double alpha = n.BaseOpacity * pulse * (1.0 - t) * 0.9 + 0.004;
                var brush = new SolidColorBrush(primaryColor, alpha);
                context.DrawEllipse(brush, null, new Point(n.X, n.Y), n.Radius * t, n.Radius * t * 0.78);
            }
        }

        double maxDist = 110.0;
        double maxDistSq = maxDist * maxDist;

        // Draw connecting lines first (underneath particles)
        for (int i = 0; i < _particles.Count; i++)
        {
            var p1 = _particles[i];
            double lifeRatio1 = p1.Life / p1.MaxLife;
            double fadeOpacity1 = p1.Opacity * (lifeRatio1 < 0.2 ? lifeRatio1 / 0.2 : lifeRatio1 > 0.8 ? (1.0 - lifeRatio1) / 0.2 : 1.0);

            for (int j = i + 1; j < _particles.Count; j++)
            {
                var p2 = _particles[j];
                double dx = p1.X - p2.X;
                double dy = p1.Y - p2.Y;
                double distSq = dx * dx + dy * dy;

                if (distSq < maxDistSq)
                {
                    double dist = Math.Sqrt(distSq);
                    double distanceFactor = 1.0 - (dist / maxDist);
                    double lifeRatio2 = p2.Life / p2.MaxLife;
                    double fadeOpacity2 = p2.Opacity * (lifeRatio2 < 0.2 ? lifeRatio2 / 0.2 : lifeRatio2 > 0.8 ? (1.0 - lifeRatio2) / 0.2 : 1.0);

                    double lineOpacity = distanceFactor * Math.Min(fadeOpacity1, fadeOpacity2) * 0.45;
                    if (lineOpacity > 0.01)
                    {
                        var linePen = new Pen(new SolidColorBrush(primaryColor, lineOpacity), 1.0);
                        context.DrawLine(linePen, new Point(p1.X, p1.Y), new Point(p2.X, p2.Y));
                    }
                }
            }
        }

        // Draw particles on top
        for (int i = 0; i < _particles.Count; i++)
        {
            var p = _particles[i];
            double lifeRatio = p.Life / p.MaxLife;
            double fadeOpacity = p.Opacity * (lifeRatio < 0.2 ? lifeRatio / 0.2 : lifeRatio > 0.8 ? (1.0 - lifeRatio) / 0.2 : 1.0);

            if (fadeOpacity > 0.01)
            {
                var brush = new SolidColorBrush(accentColor, fadeOpacity);
                context.DrawEllipse(brush, null, new Point(p.X, p.Y), p.Size / 2.0, p.Size / 2.0);
            }
        }
    }

    // ═══════════════════════════════════════
    //  MOD WORK / BROWSER & MANAGER
    // ═══════════════════════════════════════

    private void InitializeSearchMcVersions()
    {
        string activeVersion = ResolveMinecraftVersion();
        var versionList = new List<string> { activeVersion };
        var commonVersions = new[] { "26.3", "26.2", "1.21.11", "26.1.2", "26.1.1", "26.1", "1.21", "1.20.6", "1.20.4", "1.20.1", "1.19.2", "1.18.2", "1.16.5", "1.8.9" };
        
        foreach (var v in commonVersions)
        {
            if (!versionList.Contains(v)) versionList.Add(v);
        }
        
        SearchModMcVersionDropdown.Items.Clear();
        SearchRpMcVersionDropdown.Items.Clear();
        
        foreach (var v in versionList)
        {
            SearchModMcVersionDropdown.Items.Add(v);
            SearchRpMcVersionDropdown.Items.Add(v);
        }
        
        SearchModMcVersionDropdown.SelectedIndex = 0;
        SearchRpMcVersionDropdown.SelectedIndex = 0;
    }

    // The browse tabs open with popular mods / resource packs of their source and version (Prism-style), never blank.
    private async Task TriggerDefaultSearchesAsync()
    {
        await SearchBrowseAsync(ModSearchBox, ModSearchProvider, SearchModMcVersionDropdown, isResourcePack: false, BrowseModsList);
        await SearchBrowseAsync(RpSearchBox, RpSearchProvider, SearchRpMcVersionDropdown, isResourcePack: true, BrowseRpList);
    }

    private string GetInstalledVersion(string itemName, bool isResourcePack)
    {
        // Resource packs are shared by every version: look in the shared folder, not the profile.
        string targetFolder = isResourcePack
            ? SharedContentService.Instance.ResourcePacksDirectory
            : Path.Combine(settings.InstancePath, "mods");
        if (!Directory.Exists(targetFolder)) return "";

        string cleanName = Regex.Replace(itemName, @"\s+", "").ToLower();
        var files = Directory.GetFiles(targetFolder);
        foreach (var file in files)
        {
            string cleanFile = Path.GetFileNameWithoutExtension(file).Replace(".disabled", "").Replace(" ", "").ToLower();
            if (cleanFile.Contains(cleanName) || cleanName.Contains(cleanFile))
            {
                if (isResourcePack)
                {
                    return "installed";
                }
                else
                {
                    try
                    {
                        using var archive = ZipFile.OpenRead(file);
                        var entry = archive.GetEntry("fabric.mod.json");
                        if (entry != null)
                        {
                            using var stream = entry.Open();
                            using var reader = new StreamReader(stream);
                            string json = reader.ReadToEnd();
                            using var doc = JsonDocument.Parse(json);
                            if (doc.RootElement.TryGetProperty("version", out var verProp))
                            {
                                return verProp.GetString() ?? "";
                            }
                        }
                    }
                    catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or InvalidDataException or JsonException or InvalidOperationException)
                    {
                        // Only the Browse "Installed/Update" label depends on this; the Mods page lists the jar as Invalid.
                        Log($"[Mods] Could not read the version of '{Path.GetFileName(file)}': {ex.Message}");
                    }
                }
            }
        }
        return "";
    }

    /// <summary>Downloads the newest matching file. Returns null on success, otherwise the reason it failed.</summary>
    private async Task<string?> DownloadModOrPackAsync(ModSearchItem item, string mcVersion, bool isResourcePack, IProgress<double> progress)
    {
        try
        {
            var catalog = Catalog();
            var file = await catalog.LatestFileAsync(item, isResourcePack ? ContentKind.ResourcePack : ContentKind.Mod, mcVersion, ContentCatalog.ModLoader(mcVersion));
            if (file == null)
            {
                Log($"[Installer] Could not find version file for {item.Name} on version {mcVersion}");
                return $"No file of {item.Name} for Minecraft {mcVersion} was found.";
            }
            if (file.Url.Length == 0) return $"The author of {item.Name} allows downloads only on {item.Provider}'s own site.";
            if (!isResourcePack) return await InstallBrowsedModAsync(item, file.Url, file.FileName, progress);
            // Resource packs go to the shared folder every version uses; an existing pack is never replaced (a taken name gets " (2)").
            var shared = SharedContentService.Instance;
            Log($"[Installer] Downloading {item.Name} to {shared.ResourcePacksDirectory} from {file.Url}");
            string final = await catalog.InstallAsync(file, shared.ResourcePacksDirectory, shared, progress);
            Log($"[Installer] Installed {item.Name} as '{final}'");
            return null;
        }
        catch (Exception ex)
        {
            Log($"[Installer Error] Failed to download {item.Name}: {ex.Message}");
            return ex.Message;
        }
    }

    private async void SearchMods_Click(object? sender, RoutedEventArgs e) =>
        await SearchBrowseAsync(ModSearchBox, ModSearchProvider, SearchModMcVersionDropdown, isResourcePack: false, BrowseModsList);

    private async void SearchRp_Click(object? sender, RoutedEventArgs e) =>
        await SearchBrowseAsync(RpSearchBox, RpSearchProvider, SearchRpMcVersionDropdown, isResourcePack: true, BrowseRpList);

    // Debounced live search for the Browse tabs: query ~450ms after the user stops typing,
    // so it updates automatically without an explicit Search button or hammering the API.
    private DispatcherTimer? _modSearchTimer;
    private DispatcherTimer? _rpSearchTimer;

    private void ModSearchBox_TextChanged(object? sender, TextChangedEventArgs e)
    {
        if (_modSearchTimer == null)
        {
            _modSearchTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(450) };
            _modSearchTimer.Tick += (s, _) => { _modSearchTimer!.Stop(); SearchMods_Click(null, new RoutedEventArgs()); };
        }
        _modSearchTimer.Stop();
        _modSearchTimer.Start();
    }

    private void RpSearchBox_TextChanged(object? sender, TextChangedEventArgs e)
    {
        if (_rpSearchTimer == null)
        {
            _rpSearchTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(450) };
            _rpSearchTimer.Tick += (s, _) => { _rpSearchTimer!.Stop(); SearchRp_Click(null, new RoutedEventArgs()); };
        }
        _rpSearchTimer.Stop();
        _rpSearchTimer.Start();
    }

    private static string FormatDownloadCount(long count)
    {
        if (count >= 1_000_000) return $"{count / 1_000_000.0:F1}M downloads";
        if (count >= 1_000) return $"{count / 1_000.0:F1}K downloads";
        return $"{count} downloads";
    }

    private void RenderSearchResults(List<ModSearchItem> results, string mcVersion, bool isResourcePack, StackPanel listPanel)
    {
        if (results == null || results.Count == 0)
        {
            ShowSearchMessage(listPanel, $"No results found for Minecraft {mcVersion}.");
            return;
        }
        listPanel.Children.Clear();

        var template = this.FindResource("ModListItemTemplate") as DataTemplate;
        if (template == null) return;

        foreach (var item in results)
        {
            var row = template.Build(item) as Border;
            if (row == null) continue;

            var grid = row.Child as Grid;
            if (grid == null) continue;

            // Retrieve components by layout hierarchy
            var iconFrame = grid.Children.Count > 0 ? grid.Children[0] as Border : null;
            var iconImage = iconFrame?.Child as Image;
            var detailsPanel = grid.Children.Count > 1 ? grid.Children[1] as StackPanel : null;
            var titleBlock = detailsPanel?.Children.Count > 0 ? detailsPanel.Children[0] as TextBlock : null;
            var descBlock = detailsPanel?.Children.Count > 1 ? detailsPanel.Children[1] as TextBlock : null;
            var metaPanel = detailsPanel?.Children.Count > 2 ? detailsPanel.Children[2] as StackPanel : null;
            var downloadsBlock = metaPanel?.Children.Count > 0 ? metaPanel.Children[0] as TextBlock : null;
            var badgesPanel = metaPanel?.Children.Count > 1 ? metaPanel.Children[1] as StackPanel : null;
            var actionPanel = grid.Children.Count > 2 ? grid.Children[2] as StackPanel : null;

            // Set Title & Description
            if (titleBlock != null) titleBlock.Text = item.Name;
            if (descBlock != null) descBlock.Text = item.Summary;

            // Set Downloads and Provider info
            string authorPart = string.IsNullOrEmpty(item.Author) || item.Author == "CurseForge Creator" ? "" : $"  ·  by {item.Author}";
            if (downloadsBlock != null) downloadsBlock.Text = $"⬇ {FormatDownloadCount(item.DownloadCount)}  ·  {item.Provider}{authorPart}";

            // Fetch and set Icon Asynchronously
            if (iconImage != null && !string.IsNullOrEmpty(item.IconUrl))
            {
                _ = Task.Run(async () =>
                {
                    try
                    {
                        var bytes = await _httpClient.GetByteArrayAsync(item.IconUrl);
                        await Dispatcher.UIThread.InvokeAsync(() =>
                        {
                            try
                            {
                                using var ms = new MemoryStream(bytes);
                                iconImage.Source = new Bitmap(ms);
                            }
                            catch {}
                        });
                    }
                    catch {}
                });
            }

            // Populate Category Badges
            if (badgesPanel != null)
            {
                badgesPanel.Children.Clear();
                foreach (var catName in item.Categories.Take(3))
                {
                    var badge = new Border
                    {
                        Background = new SolidColorBrush(Color.Parse("#303137")),
                        CornerRadius = new CornerRadius(4),
                        Padding = new Thickness(6, 2),
                        Margin = new Thickness(0, 0, 4, 0),
                        Child = new TextBlock
                        {
                            Text = catName,
                            Foreground = new SolidColorBrush(Color.Parse("#E27676")),
                            FontSize = 9,
                            FontWeight = FontWeight.Bold
                        }
                    };
                    badgesPanel.Children.Add(badge);
                }
            }

            // Determine installation & update state
            var installedVersion = GetInstalledVersion(item.Name, isResourcePack);
            var isInstalled = !string.IsNullOrEmpty(installedVersion);
            var needsUpdate = false;
            if (isInstalled && !isResourcePack && !string.IsNullOrEmpty(item.Version) && installedVersion != "installed")
            {
                needsUpdate = item.Version != installedVersion;
            }

            // Create buttons
            var installBtn = new Button
            {
                Content = needsUpdate ? "Update" : (isInstalled ? "Installed" : "Install"),
                IsEnabled = !isInstalled || needsUpdate,
                Classes = { (isInstalled && !needsUpdate) ? "action" : "launch" },
                Height = 30,
                Padding = new Thickness(12, 0),
                HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center,
                VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center
            };
            var progressBar = new ProgressBar { Minimum = 0, Maximum = 100, Value = 0, Height = 6, Width = 80, IsVisible = false, Foreground = new SolidColorBrush(Color.Parse("#C44343")) };
            
            if (actionPanel != null)
            {
                actionPanel.Children.Clear();
                actionPanel.Children.Add(installBtn);
                actionPanel.Children.Add(progressBar);
            }

            installBtn.Click += async (s, e) =>
            {
                installBtn.IsEnabled = false;
                installBtn.Content = needsUpdate ? "Updating..." : "Installing...";
                progressBar.IsVisible = true;

                // Nothing is deleted first: a mod update replaces only your own copy of the same Fabric id (UserModFiles).
                var progress = new Progress<double>(val =>
                {
                    progressBar.Value = val;
                });

                string? failure = await DownloadModOrPackAsync(item, mcVersion, isResourcePack, progress);
                Avalonia.Controls.ToolTip.SetTip(installBtn, failure);
                if (failure == null)
                {
                    installBtn.Content = "Installed";
                    installBtn.Classes.Remove("launch");
                    installBtn.Classes.Add("action");
                    installBtn.IsEnabled = false;
                    progressBar.IsVisible = false;
                    needsUpdate = false;
                }
                else
                {
                    installBtn.IsEnabled = true;
                    installBtn.Content = needsUpdate ? "Update Failed" : "Failed";
                    progressBar.IsVisible = false;
                    ModsStatus($"{item.Name}: {failure}", true);
                }
            };

            listPanel.Children.Add(row);
        }
    }
}

public class ModSearchItem
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string Summary { get; set; } = "";
    public string IconUrl { get; set; } = "";
    public string Author { get; set; } = "";
    public long DownloadCount { get; set; }
    public string Provider { get; set; } = ""; // "Modrinth" or "CurseForge"
    public string ProjectSlug { get; set; } = "";
    public List<string> Categories { get; set; } = new(); // NEW: M4 Categories support
    public string Version { get; set; } = ""; // NEW: M4 Version tracking for updates
}

public class AvaloniaMsalProvider : IAuthenticationProvider
{
    private readonly MsalOAuthBuilder _builder;
    private readonly Window _window;
    public Action? OnCancelRequested { get; set; }
    public TheLadsLauncher.Views.DeviceCodeLoginDialog? CurrentDialog { get; set; }

    public AvaloniaMsalProvider(MsalOAuthBuilder builder, Window window)
    {
        _builder = builder;
        _window = window;
    }

    public XboxAuthNet.Game.Authenticators.IAuthenticator Authenticate() => _builder.CodeFlow();

    public void CompleteDialog()
    {
        var dialog = CurrentDialog;
        CurrentDialog = null;
        dialog?.Complete();
    }

    public XboxAuthNet.Game.Authenticators.IAuthenticator AuthenticateInteractively() => _builder.DeviceCode(async deviceCodeResult =>
    {
        await Avalonia.Threading.Dispatcher.UIThread.InvokeAsync(() =>
        {
            CompleteDialog();
                var dialog = new TheLadsLauncher.Views.DeviceCodeLoginDialog(deviceCodeResult, () =>
                {
                    OnCancelRequested?.Invoke();
                });
                CurrentDialog = dialog;
                dialog.Show(_window);
        });
    });

    public XboxAuthNet.Game.Authenticators.IAuthenticator AuthenticateSilently() => _builder.Silent();
    public XboxAuthNet.Game.Authenticators.ISessionValidator CreateSessionValidator() => XboxAuthNet.Game.Authenticators.StaticValidator.Invalid;
    public XboxAuthNet.Game.Authenticators.IAuthenticator ClearSession() => _builder.ClearSession();
    public XboxAuthNet.Game.Authenticators.IAuthenticator Signout() => _builder.ClearSession();
}

