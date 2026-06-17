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
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
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

namespace TheLadsLauncher;



public partial class MainWindow : Window
{
    private JELoginHandler loginHandler;
    private LauncherSettings settings;

    private string _selectedAccount = "";
    // Alt-account launch override: when non-empty, the next launch uses this account
    // WITHOUT changing _selectedAccount (the user's main). Set via LaunchAccountSelector.
    private string _launchAccountOverride = "";
    private bool _populatingLaunchSelector = false;
    private List<Process> _runningProcesses = new();
    private TaskCompletionSource<bool>? _installChoiceTcs;
    private DispatcherTimer? _updateCheckTimer;

    // ── 16:9 aspect ratio lock ───────────────────────────────────────────────
    private bool   _lockAspect     = true;
    private bool   _adjustingAspect = false;
    private const  double ASPECT_RATIO   = 16.0 / 9.0;
    private const  double DEFAULT_WIDTH  = 1152.0;
    private const  double DEFAULT_HEIGHT = 648.0;
    private DispatcherTimer _statsTimer;
    private DispatcherTimer _statSmoothTimer;
    private double _targetCpu, _dispCpu, _targetRam, _dispRam;
    private DispatcherTimer _particleTimer;
    private ParticleMeshControl? _meshControl;
    private bool _meshControlAdded;
    private Avalonia.Controls.TrayIcon? _trayIcon;
    private HashSet<string> _selectedModPaths = new();

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

    // Web Client
    private System.Net.Http.HttpClient _httpClient = new();

    // Skin & Cape Editor state
    private string _selectedEditor = "Skin";
    private int _editorWidth = 8;
    private int _editorHeight = 8;
    private Color[,] _skinBasePixels = new Color[64, 64];
    private Color[,] _skinOverlayPixels = new Color[64, 64];
    private Color[,] _capePixels = new Color[64, 64];
    private bool _isDrawing = false;
    private Color _activeEditorColor = Colors.White;

    public MainWindow()
    {
        // Kill lingering launcher instances to avoid test/port conflicts —
        // but ONLY when the user hasn't enabled "Allow launching multiple copies".
        try
        {
            bool allowMulti = false;
            try { allowMulti = LauncherSettings.Load().AllowMultiInstance; } catch { }
            if (!allowMulti)
            {
                var currentProc = System.Diagnostics.Process.GetCurrentProcess();
                foreach (var p in System.Diagnostics.Process.GetProcessesByName("TheLadsLauncher"))
                {
                    if (p.Id != currentProc.Id)
                    {
                        p.Kill();
                        p.WaitForExit(1000);
                    }
                }
            }
        }
        catch { }

        InitializeComponent();

        settings = LauncherSettings.Load();
        Log($"[Settings] Loaded settings from BaseDirectory: {AppDomain.CurrentDomain.BaseDirectory}");
        Log($"[Settings] ModrinthApiUrl: '{settings.ModrinthApiUrl}', CurseForgeApiUrl: '{settings.CurseForgeApiUrl}', OverrideVersion: '{settings.SelectedMinecraftVersionOverride}', FabricVersion: '{settings.FabricVersion}'");
        loginHandler = JELoginHandlerBuilder.BuildDefault();
        
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
                AddNewAccount();
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
        LoadAccounts();
        InitializeEditor();

        // Enable drag-and-drop of .jar files onto the Mods page to install them.
        if (ModsPage != null)
        {
            ModsPage.AddHandler(DragDrop.DragOverEvent, ModsPage_DragOver);
            ModsPage.AddHandler(DragDrop.DropEvent, ModsPage_Drop);
        }

        // === Fancy startup animation: breathing logo + smooth eased progress bar ===
        const double startupDurationMs = 2200.0;
        const double startupBarWidth = 320.0;
        var startupSw = System.Diagnostics.Stopwatch.StartNew();
        int dots = 0;
        double dotAccumulatorMs = 0;
        var startupAnimTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(16) };
        startupAnimTimer.Tick += (s, e) =>
        {
            double t = startupSw.Elapsed.TotalMilliseconds;

            // Subtle breathing pulse on the logo
            double pulse = 1.0 + 0.04 * Math.Sin(t / 300.0);
            if (StartupLogoImage?.RenderTransform is Avalonia.Media.ScaleTransform logoScale)
            {
                logoScale.ScaleX = pulse;
                logoScale.ScaleY = pulse;
            }

            // Eased progress fill (easeOutCubic)
            double p = Math.Min(1.0, t / startupDurationMs);
            double eased = 1.0 - Math.Pow(1.0 - p, 3);
            if (StartupProgressFill != null)
                StartupProgressFill.Width = startupBarWidth * eased;

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
            bool updateApplied = false;
            try
            {
                // 1. Check for installation
                string installedDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "The Lads Client");
                string installedExe = Path.Combine(installedDir, "TheLadsLauncher.exe");
                string currentExe = Process.GetCurrentProcess().MainModule?.FileName ?? "";
                
                bool isDebug = currentExe.Contains("\\bin\\Debug\\") || currentExe.Contains("\\bin\\Release\\") || System.Diagnostics.Debugger.IsAttached;
                if (!string.IsNullOrEmpty(currentExe) && !string.Equals(currentExe, installedExe, StringComparison.OrdinalIgnoreCase) && !isDebug)
                {
                    InstallationOverlay.IsVisible = true;
                    _installChoiceTcs = new TaskCompletionSource<bool>();
                    bool install = await _installChoiceTcs.Task;
                    InstallationOverlay.IsVisible = false;
                    
                    if (install)
                    {
                        try
                        {
                            Directory.CreateDirectory(installedDir);
                            File.Copy(currentExe, installedExe, true);
                            
                            // Copy settings.json if exists
                            string currentSettings = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "settings.json");
                            string installedSettings = Path.Combine(installedDir, "settings.json");
                            if (File.Exists(currentSettings))
                            {
                                File.Copy(currentSettings, installedSettings, true);
                            }
                            
                            // Create shortcuts using PowerShell COM object call
                            string desktopPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Desktop), "The Lads Client.lnk");
                            string startMenuDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.StartMenu), "Programs");
                            string startMenuPath = Path.Combine(startMenuDir, "The Lads Client.lnk");
                            
                            string psCommand = $"$s=(New-Object -ComObject WScript.Shell).CreateShortcut('{desktopPath}');$s.TargetPath='{installedExe}';$s.WorkingDirectory='{installedDir}';$s.Save();" +
                                               $"$s=(New-Object -ComObject WScript.Shell).CreateShortcut('{startMenuPath}');$s.TargetPath='{installedExe}';$s.WorkingDirectory='{installedDir}';$s.Save();";
                                               
                            var psStartInfo = new ProcessStartInfo
                            {
                                FileName = "powershell.exe",
                                Arguments = $"-NoProfile -ExecutionPolicy Bypass -Command \"{psCommand}\"",
                                CreateNoWindow = true,
                                UseShellExecute = false
                            };
                            using var psProc = Process.Start(psStartInfo);
                            psProc?.WaitForExit();
                            
                            // Launch the installed one
                            Process.Start(new ProcessStartInfo
                            {
                                FileName = installedExe,
                                WorkingDirectory = installedDir,
                                UseShellExecute = true
                            });
                            
                            Environment.Exit(0);
                        }
                        catch (Exception ex)
                        {
                            Log($"[Installation Error] {ex.Message}");
                        }
                    }
                }

                // 2. Check for updates on startup (skippable for local/dev testing via env var)
                if (Environment.GetEnvironmentVariable("LADS_SKIP_UPDATE") != "1")
                {
                    if (StartupLoadingSpinner != null) StartupLoadingSpinner.Text = "Checking for updates";
                    var update = await AutoUpdater.CheckForUpdatesAsync(settings.LauncherVersion);
                    if (update != null)
                    {
                        if (StartupLoadingSpinner != null) StartupLoadingSpinner.Text = $"Downloading update v{update.LatestVersion}";
                        bool downloaded = await AutoUpdater.DownloadUpdateAsync(update.DownloadUrl, update.LatestVersion);
                        if (downloaded)
                        {
                            if (StartupLoadingSpinner != null) StartupLoadingSpinner.Text = "Applying update";
                            updateApplied = true;
                            AutoUpdater.ApplyUpdateAndRestart();
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Log($"[Startup Update Error] {ex.Message}");
            }

            if (!updateApplied)
            {
                await Task.Delay(500); // Small grace period
                startupAnimTimer.Stop();
                if (StartupProgressFill != null) StartupProgressFill.Width = startupBarWidth;
                LauncherStartupOverlay.IsVisible = false;
                
                // Start background update timer (every 10 minutes)
                _updateCheckTimer = new DispatcherTimer { Interval = TimeSpan.FromMinutes(10) };
                _updateCheckTimer.Tick += async (sender, args) => await PollForUpdatesAsync();
                _updateCheckTimer.Start();
                
                // Initial check in background after startup
                _ = Task.Run(async () => {
                    await Task.Delay(5000);
                    await PollForUpdatesAsync();
                });
            }
        };
        
        // Stats timer (1 second)
        _statsTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _statsTimer.Tick += UpdateSystemStats;
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

        if (settings.ShowParticles)
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

            if (show)
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

        // Drawing release event handler
        this.PointerReleased += (s, e) => _isDrawing = false;

        // Version display
        VersionText.Text = $"v{settings.LauncherVersion}";
        UpdateMinecraftVersionDisplay();
        InitializeSearchMcVersions();
        _ = TriggerDefaultSearchesAsync();
    }

    // ═══════════════════════════════════════
    //  NAVIGATION
    // ═══════════════════════════════════════

    private void InstallLauncher_Click(object? sender, RoutedEventArgs e)
    {
        _installChoiceTcs?.TrySetResult(true);
    }

    private void SkipInstall_Click(object? sender, RoutedEventArgs e)
    {
        _installChoiceTcs?.TrySetResult(false);
    }

    private void ApplyUpdate_Click(object? sender, RoutedEventArgs e)
    {
        AutoUpdater.ApplyUpdateAndRestart();
    }

    private async Task PollForUpdatesAsync()
    {
        if (Environment.GetEnvironmentVariable("LADS_SKIP_UPDATE") == "1") return;
        try
        {
            var update = await AutoUpdater.CheckForUpdatesAsync(settings.LauncherVersion);
            if (update != null && !AutoUpdater.IsUpdateReady)
            {
                bool downloaded = await AutoUpdater.DownloadUpdateAsync(update.DownloadUrl, update.LatestVersion);
                if (downloaded)
                {
                    Dispatcher.UIThread.Post(() =>
                    {
                        UpdateBannerText.Text = $"Launcher Update Ready: v{update.LatestVersion} is downloaded and ready to apply.";
                        UpdateBanner.IsVisible = true;
                    });
                }
            }
        }
        catch (Exception ex)
        {
            Log($"[Update Poll Error] {ex.Message}");
        }
    }

    private void NavigateTo(string page)
    {
        HomePage.IsVisible = page == "Home";
        AccountsPage.IsVisible = page == "Accounts";
        SettingsPage.IsVisible = page == "Settings";
        ModsPage.IsVisible = page == "Mods";
        FilesPage.IsVisible = page == "Files";
        GalleryPage.IsVisible = page == "Gallery";
        LogsPage.IsVisible = page == "Logs";

        // Update nav button styles
        NavHome.Classes.Set("active", page == "Home");
        NavAccounts.Classes.Set("active", page == "Accounts");
        NavSettings.Classes.Set("active", page == "Settings");
        NavMods.Classes.Set("active", page == "Mods");
        NavFiles.Classes.Set("active", page == "Files");
        NavGallery.Classes.Set("active", page == "Gallery");
        NavLogs.Classes.Set("active", page == "Logs");
    }

    private void NavHome_Click(object? sender, RoutedEventArgs e) => NavigateTo("Home");
    private void NavAccounts_Click(object? sender, RoutedEventArgs e) => NavigateTo("Accounts");
    private void ManageAccountsShortcutBtn_Click(object? sender, RoutedEventArgs e) => NavigateTo("Accounts");
    private void NavSettings_Click(object? sender, RoutedEventArgs e) => NavigateTo("Settings");
    private void NavMods_Click(object? sender, RoutedEventArgs e) { LoadModsList(); NavigateTo("Mods"); }
    private void NavFiles_Click(object? sender, RoutedEventArgs e) { LoadFiles(settings.InstancePath); NavigateTo("Files"); }
    private void NavLogs_Click(object? sender, RoutedEventArgs e) => NavigateTo("Logs");

    // ═══════════════════════════════════════
    //  FILES EXPLORER
    // ═══════════════════════════════════════

    private string _filesCurrentDir = "";
    private string _filesRootDir = "";

    private void LoadFiles(string dir)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(_filesRootDir))
                _filesRootDir = settings.InstancePath;
            if (string.IsNullOrWhiteSpace(dir) || !Directory.Exists(dir))
                dir = _filesRootDir;

            _filesCurrentDir = dir;
            FilesPathText.Text = dir;
            FilesUpBtn.IsEnabled = !string.Equals(
                Path.GetFullPath(dir).TrimEnd('\\', '/'),
                Path.GetFullPath(_filesRootDir).TrimEnd('\\', '/'),
                StringComparison.OrdinalIgnoreCase);

            FilesList.Children.Clear();

            foreach (var d in Directory.GetDirectories(dir).OrderBy(p => Path.GetFileName(p), StringComparer.OrdinalIgnoreCase))
                FilesList.Children.Add(BuildFileRow(d, true));
            foreach (var f in Directory.GetFiles(dir).OrderBy(p => Path.GetFileName(p), StringComparer.OrdinalIgnoreCase))
                FilesList.Children.Add(BuildFileRow(f, false));

            if (FilesList.Children.Count == 0)
                FilesList.Children.Add(new TextBlock { Text = "Empty folder.", Foreground = new SolidColorBrush(Color.Parse("#666666")), FontSize = 13, Margin = new Thickness(4) });
        }
        catch (Exception ex)
        {
            Log($"[Files] {ex.Message}");
        }
    }

    private Border BuildFileRow(string path, bool isDir)
    {
        string name = Path.GetFileName(path);
        var row = new Border
        {
            Background = new SolidColorBrush(Color.Parse("#14141F")),
            CornerRadius = new CornerRadius(6),
            Padding = new Thickness(10, 6, 8, 6)
        };
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto,Auto") };

        var icon = new TextBlock { Text = isDir ? "📁" : "📄", FontSize = 14, VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center, Margin = new Thickness(0, 0, 8, 0) };
        Grid.SetColumn(icon, 0);
        grid.Children.Add(icon);

        var nameText = new TextBlock { Text = name, Foreground = new SolidColorBrush(Color.Parse(isDir ? "#CCCCDD" : "#AAAAAA")), FontSize = 13, VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center, TextTrimming = Avalonia.Media.TextTrimming.CharacterEllipsis };
        Grid.SetColumn(nameText, 1);
        grid.Children.Add(nameText);

        if (!isDir)
        {
            try
            {
                long bytes = new FileInfo(path).Length;
                var sizeText = new TextBlock { Text = FormatBytes(bytes), Foreground = new SolidColorBrush(Color.Parse("#666666")), FontSize = 11, VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center, Margin = new Thickness(8, 0, 8, 0) };
                Grid.SetColumn(sizeText, 2);
                grid.Children.Add(sizeText);
            }
            catch { }
        }

        var delBtn = new Button { Content = "✕", Classes = { "danger" }, Width = 28, Height = 26, FontSize = 11, HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center, VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center };
        delBtn.Click += (s, e) => DeleteFileEntry(path, isDir);
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

    private void DeleteFileEntry(string path, bool isDir)
    {
        try
        {
            if (isDir) Directory.Delete(path, true);
            else File.Delete(path);
            Log($"[Files] Deleted: {Path.GetFileName(path)}");
            LoadFiles(_filesCurrentDir);
        }
        catch (Exception ex)
        {
            Log($"[Files] Delete failed: {ex.Message}");
        }
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
        catch (Exception ex)
        {
            Log($"[Files] Open failed: {ex.Message}");
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

    private void NavGallery_Click(object? sender, RoutedEventArgs e) { LoadGallery(); NavigateTo("Gallery"); }
    private void GallerySort_Changed(object? sender, Avalonia.Controls.SelectionChangedEventArgs e) { if (GalleryPage?.IsVisible == true) LoadGallery(); }
    private void ImgurId_Changed(object? sender, RoutedEventArgs e) { settings.ImgurClientId = ImgurIdBox.Text ?? ""; settings.Save(); }
    private void GalleryOpenFolder_Click(object? sender, RoutedEventArgs e) => OpenPath(Path.Combine(settings.InstancePath, "screenshots"));

    private void LoadGallery()
    {
        SyncFavoritesWithGame();
        GalleryList.Children.Clear();
        if (ImgurIdBox != null) ImgurIdBox.Text = settings.ImgurClientId;

        string dir = Path.Combine(settings.InstancePath, "screenshots");
        if (!Directory.Exists(dir))
        {
            GalleryList.Children.Add(new TextBlock { Text = "No screenshots folder yet.", Foreground = new SolidColorBrush(Color.Parse("#666666")), FontSize = 13, Margin = new Thickness(4) });
            return;
        }

        var files = Directory.GetFiles(dir, "*.png").Concat(Directory.GetFiles(dir, "*.jpg")).ToList();
        int sort = GallerySortBox?.SelectedIndex ?? 0;
        if (sort == 1) files = files.OrderBy(f => File.GetLastWriteTime(f)).ToList();
        else if (sort == 2) files = files.OrderBy(f => Path.GetFileName(f), StringComparer.OrdinalIgnoreCase).ToList();
        else if (sort == 3) files = files.OrderByDescending(f => settings.GalleryFavorites.Contains(Path.GetFileName(f))).ThenByDescending(f => File.GetLastWriteTime(f)).ToList();
        else files = files.OrderByDescending(f => File.GetLastWriteTime(f)).ToList();

        if (files.Count == 0)
        {
            GalleryList.Children.Add(new TextBlock { Text = "No screenshots yet.", Foreground = new SolidColorBrush(Color.Parse("#666666")), FontSize = 13, Margin = new Thickness(4) });
            return;
        }
        foreach (var f in files) GalleryList.Children.Add(BuildGalleryCard(f));
    }

    private Border BuildGalleryCard(string path)
    {
        string name = Path.GetFileName(path);
        bool fav = settings.GalleryFavorites.Contains(name);

        var card = new Border { Background = new SolidColorBrush(Color.Parse("#14141F")), CornerRadius = new CornerRadius(8), Margin = new Thickness(6), Width = 182, Padding = new Thickness(6) };
        var stack = new StackPanel { Spacing = 4 };

        var img = new Image { Width = 170, Height = 96, Stretch = Avalonia.Media.Stretch.UniformToFill };
        try { using var fs = File.OpenRead(path); img.Source = Bitmap.DecodeToWidth(fs, 340); } catch { }
        var imgBorder = new Border { CornerRadius = new CornerRadius(4), ClipToBounds = true, Height = 96, Child = img };
        imgBorder.PointerPressed += (s, e) => ShowGalleryViewer(path);
        stack.Children.Add(imgBorder);

        stack.Children.Add(new TextBlock { Text = name, Foreground = new SolidColorBrush(Color.Parse("#AAAAAA")), FontSize = 11, TextTrimming = Avalonia.Media.TextTrimming.CharacterEllipsis });
        stack.Children.Add(new TextBlock { Text = File.GetLastWriteTime(path).ToString("g"), Foreground = new SolidColorBrush(Color.Parse("#666666")), FontSize = 10 });

        // Metadata sidecar (written in-game): server/world, coords, biome
        string metaLine = ReadScreenshotMeta(path);
        if (!string.IsNullOrEmpty(metaLine))
        {
            stack.Children.Add(new TextBlock { Text = metaLine, Foreground = new SolidColorBrush(Color.Parse("#7A88B0")), FontSize = 10, TextTrimming = Avalonia.Media.TextTrimming.CharacterEllipsis });
        }

        var actions = new StackPanel { Orientation = Avalonia.Layout.Orientation.Horizontal, Spacing = 4, Margin = new Thickness(0, 2, 0, 0) };
        actions.Children.Add(MiniGalBtn("📋", "Copy image to clipboard", () => CopyImageToClipboard(path)));
        actions.Children.Add(MiniGalBtn("🔗", "Upload to Imgur (copies link)", () => UploadImgur(path)));
        actions.Children.Add(MiniGalBtn("📂", "Show in folder", () => OpenFolderSelect(path)));
        actions.Children.Add(MiniGalBtn(fav ? "★" : "☆", "Favorite", () => ToggleGalleryFav(name)));
        actions.Children.Add(MiniGalBtn("✕", "Delete", () => DeleteScreenshot(path)));
        stack.Children.Add(actions);

        card.Child = stack;
        return card;
    }

    private Button MiniGalBtn(string content, string tip, Action onClick)
    {
        var b = new Button { Content = content, Width = 30, Height = 26, FontSize = 12, HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center, VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center };
        Avalonia.Controls.ToolTip.SetTip(b, tip);
        b.Click += (s, e) => onClick();
        return b;
    }

    private string ReadScreenshotMeta(string pngPath)
    {
        try
        {
            string metaPath = pngPath + ".json";
            if (!File.Exists(metaPath)) return "";
            using var doc = JsonDocument.Parse(File.ReadAllText(metaPath));
            var root = doc.RootElement;
            var parts = new List<string>();
            if (root.TryGetProperty("server", out var sv) && !string.IsNullOrEmpty(sv.GetString()))
                parts.Add(sv.GetString());
            else if (root.TryGetProperty("world", out var wd) && !string.IsNullOrEmpty(wd.GetString()))
                parts.Add(wd.GetString());
            if (root.TryGetProperty("x", out var x) && root.TryGetProperty("z", out var z))
                parts.Add($"{x.GetInt32()}, {z.GetInt32()}");
            if (root.TryGetProperty("biome", out var bi) && !string.IsNullOrEmpty(bi.GetString()))
                parts.Add(bi.GetString().Replace("minecraft:", ""));
            if (root.TryGetProperty("seed", out var sd))
                parts.Add("seed " + sd.GetInt64());
            return string.Join("  ·  ", parts);
        }
        catch { return ""; }
    }

    private void SyncFavoritesWithGame()
    {
        try
        {
            string favPath = Path.Combine(settings.InstancePath, "config", "gallery_favorites.json");
            if (File.Exists(favPath))
            {
                string json = File.ReadAllText(favPath);
                var gameFavs = JsonSerializer.Deserialize<List<string>>(json);
                if (gameFavs != null)
                {
                    bool changed = false;
                    foreach (var fav in gameFavs)
                    {
                        if (!settings.GalleryFavorites.Contains(fav))
                        {
                            settings.GalleryFavorites.Add(fav);
                            changed = true;
                        }
                    }
                    if (settings.GalleryFavorites.Count != gameFavs.Count || changed)
                    {
                        settings.GalleryFavorites = gameFavs;
                        settings.Save();
                    }
                }
            }
            else
            {
                if (settings.GalleryFavorites.Count > 0)
                {
                    string dir = Path.GetDirectoryName(favPath);
                    if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);
                    string json = JsonSerializer.Serialize(settings.GalleryFavorites);
                    File.WriteAllText(favPath, json);
                }
            }
        }
        catch { }
    }

    private void ToggleGalleryFav(string name)
    {
        if (settings.GalleryFavorites.Contains(name)) settings.GalleryFavorites.Remove(name);
        else settings.GalleryFavorites.Add(name);
        settings.Save();
        try
        {
            string favPath = Path.Combine(settings.InstancePath, "config", "gallery_favorites.json");
            string dir = Path.GetDirectoryName(favPath);
            if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);
            string json = JsonSerializer.Serialize(settings.GalleryFavorites);
            File.WriteAllText(favPath, json);
        }
        catch { }
        LoadGallery();
    }

    private void DeleteScreenshot(string path)
    {
        try {
            File.Delete(path);
            Log($"[Gallery] Deleted: {Path.GetFileName(path)}");
            string jsonPath = path + ".json";
            if (File.Exists(jsonPath)) File.Delete(jsonPath);
            string name = Path.GetFileName(path);
            if (settings.GalleryFavorites.Contains(name))
            {
                settings.GalleryFavorites.Remove(name);
                settings.Save();
                try
                {
                    string favPath = Path.Combine(settings.InstancePath, "config", "gallery_favorites.json");
                    string json = JsonSerializer.Serialize(settings.GalleryFavorites);
                    File.WriteAllText(favPath, json);
                }
                catch {}
            }
            LoadGallery();
        }
        catch (Exception ex) { Log($"[Gallery] delete failed: {ex.Message}"); }
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

    private void MinimizeBtn_Click(object? sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;
    private void CloseBtn_Click(object? sender, RoutedEventArgs e)
    {
        if (settings.CloseToTray && _runningProcesses.Any(p => !p.HasExited))
            this.Hide();
        else
            Environment.Exit(0);
    }

    private void AspectLockBtn_Click(object? sender, RoutedEventArgs e)
    {
        _lockAspect = !_lockAspect;
        if (AspectLockBtn != null)
            AspectLockBtn.Content = _lockAspect ? "16:9 🔒" : "Free 🔓";

        if (_lockAspect && WindowState == WindowState.Normal)
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
        _adjustingAspect = true;
        this.Width  = DEFAULT_WIDTH;
        this.Height = DEFAULT_HEIGHT;
        _adjustingAspect = false;
    }

    private void MaximizeBtn_Click(object? sender, RoutedEventArgs e)
    {
        WindowState = WindowState == WindowState.Maximized
            ? WindowState.Normal
            : WindowState.Maximized;
        if (MaximizeBtn != null)
            MaximizeBtn.Content = WindowState == WindowState.Maximized ? "❐" : "□";
    }

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
        if (!settings.ShowParticles) return;

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
        var (primary, primaryLight, primaryDark, accent) = settings.GetThemeColors();
        GameStateText.Foreground = new SolidColorBrush(Color.Parse(accent));
    }

    private void ApplyUiScale()
    {
        if (double.TryParse(settings.UiScale.Replace("%", ""), out double pct))
        {
            double scale = pct / 100.0;
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

            if (Directory.Exists(Path.Combine(settings.InstancePath, "mods")))
            {
                int modCount = Directory.GetFiles(Path.Combine(settings.InstancePath, "mods"), "*.jar").Length;
                ModCountText.Text = modCount.ToString();
            }

            var activeProcesses = _runningProcesses.Where(p => !p.HasExited).ToList();
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
        catch { }
    }

    // ═══════════════════════════════════════
    //  LOGGING
    // ═══════════════════════════════════════

    private static readonly object _logFileLock = new();

    private void Log(string message)
    {
        lock (_logFileLock)
            System.IO.File.AppendAllText(@"C:\Users\Arash\Desktop\launcher_debug.txt", $"[{DateTime.Now:HH:mm:ss}] {message}\n");
        Dispatcher.UIThread.Post(() =>
        {
            string timestamped = $"[{DateTime.Now:HH:mm:ss}] {message}";
            allLogLines.Add(timestamped);
            logLines.Enqueue(timestamped);
            if (logLines.Count > 500)
                logLines.Dequeue();
            LogBox.Text = string.Join(Environment.NewLine, logLines);
            LogBox.CaretIndex = LogBox.Text?.Length ?? 0;
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

    private void LoadAccounts()
    {
        var msAccounts = loginHandler.AccountManager.GetAccounts().ToList();
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
            PlayerSkinPreview.Source = null;
        }

        _ = WriteLadsProfileAsync(_selectedAccount);
        _ = WriteLadsAccountsJsonAsync();
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
                var item = new MenuItem { Header = accName };
                if (accName == _selectedAccount)
                {
                    item.Icon = "✓";
                }
                
                item.Click += async (s, e) =>
                {
                    // Select this account first
                    _selectedAccount = accName;
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
                LaunchAccountSelector.Items.Add(new ComboBoxItem
                {
                    Content = name + (isOffline ? "  (Offline)" : "  (MS)"),
                    Tag = name
                });
            }

            // Keep an existing override selected if it still exists; otherwise default to main.
            string target = !string.IsNullOrEmpty(_launchAccountOverride) && allAccountNames.Contains(_launchAccountOverride)
                ? _launchAccountOverride
                : _selectedAccount;
            if (string.IsNullOrEmpty(_launchAccountOverride) || !allAccountNames.Contains(_launchAccountOverride))
                _launchAccountOverride = "";

            foreach (var obj in LaunchAccountSelector.Items)
            {
                if (obj is ComboBoxItem cbi && (cbi.Tag as string) == target)
                {
                    LaunchAccountSelector.SelectedItem = cbi;
                    break;
                }
            }
        }
        finally
        {
            _populatingLaunchSelector = false;
        }
    }

    private void LaunchAccountSelector_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (_populatingLaunchSelector) return;
        if (LaunchAccountSelector?.SelectedItem is ComboBoxItem cbi && cbi.Tag is string name)
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

        for (int i = 0; i < allAccountNames.Count; i++)
        {
            string username = allAccountNames[i];
            bool isOffline = settings.OfflineAccounts.Contains(username);

            var border = new Border
            {
                Background = new SolidColorBrush(Color.Parse("#0E0E18")),
                BorderBrush = new SolidColorBrush(Color.Parse(_selectedAccount == username ? "#8B0000" : "#1A1A2E")),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(12, 8),
                Margin = new Thickness(0, 0, 0, 4)
            };

            var grid = new Grid { ColumnDefinitions = ColumnDefinitions.Parse("Auto,*,Auto,Auto,Auto,Auto") };

            var skinHead = new Avalonia.Controls.Shapes.Ellipse
            {
                Width = 24, Height = 24,
                Fill = new SolidColorBrush(Color.Parse("#111118")),
                Margin = new Thickness(0, 0, 8, 0)
            };
            Grid.SetColumn(skinHead, 0);
            grid.Children.Add(skinHead);

            _ = Task.Run(async () =>
            {
                try
                {
                    string headUrl = $"https://mc-heads.net/avatar/{Uri.EscapeDataString(ResolveSkinId(username))}/24";
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

            var nameText = new TextBlock
            {
                Text = username + (isOffline ? " (Offline)" : " (MS)"),
                Foreground = Brushes.White,
                FontSize = 14,
                VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center
            };
            Grid.SetColumn(nameText, 1);
            grid.Children.Add(nameText);

            // Use Button
            var activeBtn = new Button
            {
                Content = "Use",
                Classes = { "action" },
                Height = 28, FontSize = 11,
                Margin = new Thickness(4, 0)
            };
            activeBtn.Click += async (s, e) => {
                _selectedAccount = username;
                MiniAccountName.Text = username;
                await LoadPlayerSkin(username);
                _ = WriteLadsProfileAsync(username);
                Log($"[Auth] Switched to account: {username}");
            };
            Grid.SetColumn(activeBtn, 2);
            grid.Children.Add(activeBtn);

            // Reorder buttons (Up/Down)
            var upBtn = new Button { Content = "▲", Classes = { "action" }, Height = 28, Width = 28, FontSize = 10, Margin = new Thickness(2, 0) };
            var downBtn = new Button { Content = "▼", Classes = { "action" }, Height = 28, Width = 28, FontSize = 10, Margin = new Thickness(2, 0) };

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

            Grid.SetColumn(upBtn, 3);
            grid.Children.Add(upBtn);

            Grid.SetColumn(downBtn, 4);
            grid.Children.Add(downBtn);

            // Delete Button
            var delBtn = new Button { Content = "✕", Classes = { "danger" }, Height = 28, Width = 28, FontSize = 10, Margin = new Thickness(2, 0) };
            delBtn.Click += (s, e) => {
                if (isOffline)
                {
                    settings.OfflineAccounts.Remove(username);
                }
                else
                {
                    loginHandler.AccountManager.ClearAccounts();
                    try
                    {
                        var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                        var authDir = System.IO.Path.Combine(appData, "The Lads Client", "auth_cache");
                        if (System.IO.Directory.Exists(authDir))
                            System.IO.Directory.Delete(authDir, true);
                    } catch {}
                }
                settings.AccountOrder.Remove(username);
                settings.Save();
                LoadAccounts();
                Log($"[Auth] Removed account: {username}");
            };
            Grid.SetColumn(delBtn, 5);
            grid.Children.Add(delBtn);

            border.Child = grid;
            border.PointerPressed += (s, e) => {
                _selectedAccount = username;
                LoadAccounts();
            };
            AccountsListContainer.Children.Add(border);
        }
    }

    private void OpenFolder_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            var dir = settings.InstancePath;
            if (string.IsNullOrWhiteSpace(dir))
            {
                StatusText.Text = "Game folder is not set.";
                return;
            }
            if (!System.IO.Directory.Exists(dir))
                System.IO.Directory.CreateDirectory(dir);

            // UseShellExecute opens the folder in the OS file explorer
            System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
            {
                FileName = dir,
                UseShellExecute = true
            });
            Log($"[Launcher] Opened game folder: {dir}");
        }
        catch (Exception ex)
        {
            StatusText.Text = "Failed to open folder: " + ex.Message;
            Log($"[Launcher] Open folder failed: {ex.Message}");
        }
    }

    private void RemoveAccount_Click(object? sender, RoutedEventArgs e)
    {
        loginHandler.AccountManager.ClearAccounts();
        try
        {
            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            var authDir = System.IO.Path.Combine(appData, "The Lads Client", "auth_cache");
            if (System.IO.Directory.Exists(authDir))
                System.IO.Directory.Delete(authDir, true);
        } catch {}
        
        _selectedAccount = "";
        LoadAccounts();
        StatusText.Text = "Account removed.";
    }

    private void ClearCache_Click(object? sender, RoutedEventArgs e)
    {
        // Clear all MSAL / login cache by deleting the directory
        try
        {
            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            var theLadsDir = System.IO.Path.Combine(appData, "The Lads Client");
            var authDir = System.IO.Path.Combine(theLadsDir, "auth_cache");
            if (System.IO.Directory.Exists(authDir))
                System.IO.Directory.Delete(authDir, true);
                
            var msalCache = System.IO.Path.Combine(theLadsDir, "cmllib_msal_cache.txt");
            if (System.IO.File.Exists(msalCache))
                System.IO.File.Delete(msalCache);
                
            _selectedAccount = "";
            LoadAccounts();
            StatusText.Text = "All authentication data cleared.";
        }
        catch (Exception ex)
        {
            StatusText.Text = "Failed to clear cache: " + ex.Message;
        }
    }

    private async void AddOfflineAccount_Click(object? sender, RoutedEventArgs e)
    {
        var window = new Window()
        {
            Title = "Add Offline Account",
            Width = 400, Height = 180,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Background = new SolidColorBrush(Color.Parse("#0A0A0F")),
            CanResize = false
        };
        var panel = new StackPanel() { Margin = new Thickness(20), Spacing = 10 };
        panel.Children.Add(new TextBlock() { Text = "Enter offline username:", Foreground = Brushes.White, FontSize = 14 });
        
        var textBox = new TextBox() { Height = 36, FontSize = 14 };
        panel.Children.Add(textBox);
        
        var button = new Button() { 
            Content = "Add Account", 
            Background = new SolidColorBrush(Color.Parse("#8B0000")),
            Foreground = Brushes.White,
            FontWeight = FontWeight.Bold,
            HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            Height = 36
        };
        button.Click += (s, ev) => {
            string username = textBox.Text;
            if (!string.IsNullOrEmpty(username))
            {
                if (!settings.OfflineAccounts.Contains(username))
                {
                    settings.OfflineAccounts.Add(username);
                    settings.AccountOrder.Add(username);
                    settings.Save();
                }
                LoadAccounts();
                Log($"[Auth] Added offline account: {username}");
            }
            window.Close();
        };
        panel.Children.Add(button);
        window.Content = panel;
        window.ShowDialog(this);
    }

    private void AddMicrosoftAccount_Click(object? sender, RoutedEventArgs e)
    {
        _ = AddNewAccount();
    }

    private async Task AddNewAccount()
    {
        try
        {
            StatusText.Text = "Opening Microsoft sign-in window...";
            Log("[Auth] Starting interactive Microsoft login (embedded browser)...");

            // Opens an embedded WebView2 Microsoft sign-in window.
            // Sign in with your Microsoft account there; tokens are cached for silent re-login later.
            var session = await loginHandler.AuthenticateInteractively();

            Log($"[Auth] Interactive login completed! Session Username: {session.Username}, UUID: {session.UUID}");
            
            // Try to force add and save the account
            loginHandler.AccountManager.SaveAccounts();
            
            Log($"[Auth] GetAccounts() count: {loginHandler.AccountManager.GetAccounts().Count}");
            
            LoadAccounts();
            _selectedAccount = session.Username ?? _selectedAccount;
            StatusText.Text = $"Account added: {session.Username}!";
            Log($"[Auth] New account added: {session.Username}");

            // Auto launch
            Dispatcher.UIThread.Post(() => LaunchButton_Click(null, null));
        }
        catch (PlatformNotSupportedException)
        {
            StatusText.Text = "Microsoft login requires the WebView2 Runtime. Please install 'Microsoft Edge WebView2 Runtime' and try again.";
            Log("[Auth ERROR] WebView2 Runtime not found.");
        }
        catch (Exception ex)
        {
            StatusText.Text = "Failed to add account: " + ex.Message;
            Log($"[Auth ERROR] {ex.Message}");
            Log($"[Auth ERROR STACK] {ex.StackTrace}");
        }
    }

    private void ManualLoginCancel_Click(object? sender, Avalonia.Interactivity.RoutedEventArgs e)
    {
        ManualLoginOverlay.IsVisible = false;
        StatusText.Text = "Manual login cancelled.";
    }

    private async void ManualLoginSubmit_Click(object? sender, Avalonia.Interactivity.RoutedEventArgs e)
    {
        string url = ManualUrlTextBox.Text ?? "";
        ManualLoginOverlay.IsVisible = false;
        StatusText.Text = "Processing manual login...";
        try
        {
            // Parse code from URL
            var uri = new Uri(url);
            var queryParams = System.Web.HttpUtility.ParseQueryString(uri.Query);
            string code = queryParams["code"] ?? "";
            if (string.IsNullOrEmpty(code)) throw new Exception("Code not found in URL");

            // We need to use XboxAuthNet manually to exchange code for tokens
            // Wait, does CmlLib support exchanging raw code?
            // Since we can't easily inject a manual code into MsalApp, we will use CmlLib's native Device Code? No, MSAL is required in modern CmlLib for full session.
            // Actually, we can use the old-school manual MSAL Auth if we instantiate it!
            throw new Exception("Code captured: " + code);
        }
        catch (Exception ex)
        {
            StatusText.Text = "Manual login failed: " + ex.Message;
        }
    }

    // ═══════════════════════════════════════
    //  SKIN & CAPE CUSTOMIZATION / EDITOR
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
            SelectedAccountText.Text = username;
            MiniAccountName.Text = username;

            string skinId = ResolveSkinId(username);

            // Load 3D-like body render (use uuid/username directly — NOT the broken /player/ path)
            string bodyUrl = $"https://mc-heads.net/body/{Uri.EscapeDataString(skinId)}/150";
            var bodyBytes = await _httpClient.GetByteArrayAsync(bodyUrl);
            using (var ms = new MemoryStream(bodyBytes))
            {
                PlayerSkinPreview.Source = new Bitmap(ms);
            }

            // Load head for mini icon
            string headUrl = $"https://mc-heads.net/avatar/{Uri.EscapeDataString(skinId)}/24";
            var headBytes = await _httpClient.GetByteArrayAsync(headUrl);
            using (var ms = new MemoryStream(headBytes))
            {
                var headBmp = new Bitmap(ms);
                MiniSkinHead.Fill = new ImageBrush(headBmp);
            }

            LoadPresetsList(username);
        }
        catch
        {
            PlayerSkinPreview.Source = null;
            MiniSkinHead.Fill = new SolidColorBrush(Color.Parse("#111118"));
        }
    }

    private async Task WriteLadsProfileAsync(string username)
    {
        if (string.IsNullOrEmpty(username)) return;
        try
        {
            string type = settings.OfflineAccounts.Contains(username) || username == "TestPlayer"
                ? "offline" : "microsoft";

            string uuid = "";
            string accessToken = "";

            if (type == "microsoft")
            {
                var msAcc = loginHandler.AccountManager.GetAccounts()
                    .FirstOrDefault(a =>
                        (a as CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount)?.Profile?.Username == username);
                if (msAcc != null)
                {
                    try
                    {
                        var session = await loginHandler.AuthenticateSilently(msAcc);
                        uuid = session.UUID ?? uuid;
                        accessToken = session.AccessToken ?? "";
                    }
                    catch (Exception ex)
                    {
                        Log($"[Profile] Silent auth failed for {username}: {ex.Message}");
                    }
                }
            }

            // Skin URL via Mojang texture API (best-effort, skip on failure)
            string skinUrl = "";
            try
            {
                string lookupUrl = $"https://api.mojang.com/users/profiles/minecraft/{username}";
                var resp = await _httpClient.GetStringAsync(lookupUrl);
                var obj = System.Text.Json.JsonDocument.Parse(resp).RootElement;
                if (string.IsNullOrEmpty(uuid) && obj.TryGetProperty("id", out var idEl))
                    uuid = idEl.GetString() ?? "";
                if (!string.IsNullOrEmpty(uuid))
                {
                    string profUrl = $"https://sessionserver.mojang.com/session/minecraft/profile/{uuid}";
                    var profResp = await _httpClient.GetStringAsync(profUrl);
                    var profObj = System.Text.Json.JsonDocument.Parse(profResp).RootElement;
                    if (profObj.TryGetProperty("properties", out var props))
                    {
                        foreach (var prop in props.EnumerateArray())
                        {
                            if (prop.TryGetProperty("name", out var n) && n.GetString() == "textures")
                            {
                                var b64 = prop.GetProperty("value").GetString() ?? "";
                                var decoded = System.Text.Encoding.UTF8.GetString(Convert.FromBase64String(b64));
                                var tex = System.Text.Json.JsonDocument.Parse(decoded).RootElement;
                                if (tex.TryGetProperty("textures", out var textures) &&
                                    textures.TryGetProperty("SKIN", out var skin) &&
                                    skin.TryGetProperty("url", out var url))
                                {
                                    skinUrl = url.GetString() ?? "";
                                }
                            }
                        }
                    }
                }
            }
            catch { }

            var profile = new
            {
                username,
                uuid,
                type,
                accessToken,
                skinUrl,
                capeUrl = (string?)null,
                lastUpdated = DateTime.UtcNow.ToString("o")
            };

            string json = System.Text.Json.JsonSerializer.Serialize(profile,
                new System.Text.Json.JsonSerializerOptions { WriteIndented = true });
            string path = System.IO.Path.Combine(@"C:\The Lads Client", "lads_profile.json");
            await System.IO.File.WriteAllTextAsync(path, json);
            Log($"[Profile] lads_profile.json updated for {username}");
        }
        catch (Exception ex)
        {
            Log($"[Profile] Failed to write lads_profile.json: {ex.Message}");
        }
    }

    public class LadsAccountJson
    {
        public string username { get; set; } = "";
        public string uuid { get; set; } = "";
        public string type { get; set; } = "";
        public string accessToken { get; set; } = "";
    }

    private async Task WriteLadsAccountsJsonAsync()
    {
        try
        {
            var msAccounts = loginHandler.AccountManager.GetAccounts().ToList();
            var accountsList = new System.Collections.Generic.List<LadsAccountJson>();

            // 1. Add Microsoft accounts
            foreach (var acc in msAccounts)
            {
                if (acc is CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount gameAcc)
                {
                    string? username = gameAcc.Profile?.Username;
                    string uuid = gameAcc.Profile?.UUID ?? "";
                    string accessToken = "";
                    try
                    {
                        // Silent auth also hydrates the username/uuid for accounts whose cached
                        // Profile metadata isn't populated yet — so we never drop a real account.
                        var session = await loginHandler.AuthenticateSilently(acc);
                        if (string.IsNullOrEmpty(username)) username = session.Username;
                        uuid = session.UUID ?? uuid;
                        accessToken = session.AccessToken ?? "";
                    }
                    catch { }

                    // Only skip if we genuinely couldn't resolve a username from cache OR silent auth.
                    if (string.IsNullOrEmpty(username))
                    {
                        Log("[Accounts] Skipped an MS account with no resolvable username.");
                        continue;
                    }
                    if (accountsList.Any(a => a.username == username)) continue;

                    accountsList.Add(new LadsAccountJson
                    {
                        username = username,
                        uuid = uuid,
                        type = "microsoft",
                        accessToken = accessToken
                    });
                }
            }

            // 2. Add Offline accounts
            foreach (var username in settings.OfflineAccounts)
            {
                if (string.IsNullOrEmpty(username)) continue;
                if (accountsList.Any(a => a.username == username)) continue;

                string uuid = "";
                try
                {
                    using (var md5 = System.Security.Cryptography.MD5.Create())
                    {
                        byte[] hash = md5.ComputeHash(System.Text.Encoding.UTF8.GetBytes("OfflinePlayer:" + username));
                        hash[6] = (byte)((hash[6] & 0x0f) | 0x30);
                        hash[8] = (byte)((hash[8] & 0x3f) | 0x80);
                        uuid = new Guid(hash).ToString();
                    }
                }
                catch { }

                accountsList.Add(new LadsAccountJson
                {
                    username = username,
                    uuid = uuid,
                    type = "offline",
                    accessToken = "0"
                });
            }

            // Ensure TestPlayer is there if empty
            if (!accountsList.Any())
            {
                accountsList.Add(new LadsAccountJson
                {
                    username = "TestPlayer",
                    uuid = Guid.NewGuid().ToString(),
                    type = "offline",
                    accessToken = "0"
                });
            }

            string json = System.Text.Json.JsonSerializer.Serialize(accountsList,
                new System.Text.Json.JsonSerializerOptions { WriteIndented = true });
            string path = System.IO.Path.Combine(settings.InstancePath, "lads_accounts.json");
            
            // Ensure directory exists
            string dir = Path.GetDirectoryName(path) ?? "";
            if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            {
                Directory.CreateDirectory(dir);
            }
            
            await System.IO.File.WriteAllTextAsync(path, json);
            Log($"[Accounts] lads_accounts.json updated with {accountsList.Count} accounts.");
        }
        catch (Exception ex)
        {
            Log($"[Accounts ERROR] Failed to write lads_accounts.json: {ex.Message}");
        }
    }

    private void LoadPresetsList(string username)
    {
        PresetSelector.Items.Clear();
        string presetDir = Path.Combine(@"C:\The Lads Client", "presets", username);
        if (Directory.Exists(presetDir))
        {
            var files = Directory.GetFiles(presetDir, "*.png");
            foreach (var f in files)
            {
                PresetSelector.Items.Add(Path.GetFileNameWithoutExtension(f));
            }
        }
        if (PresetSelector.Items.Count > 0)
            PresetSelector.SelectedIndex = 0;
    }

    private void SavePreset_Click(object? sender, RoutedEventArgs e)
    {
        string username = SelectedAccountText.Text;
        if (string.IsNullOrEmpty(username) || username == "No Account Selected") return;

        string name = "Preset_" + DateTime.Now.ToString("yyyyMMdd_HHmmss");
        string presetDir = Path.Combine(@"C:\The Lads Client", "presets", username);
        Directory.CreateDirectory(presetDir);
        string dest = Path.Combine(presetDir, name + ".png");

        SaveSkinPng(dest, false);
        LoadPresetsList(username);
        Log($"[Presets] Saved skin preset: {name}");
    }

    private void ApplyPreset_Click(object? sender, RoutedEventArgs e)
    {
        string username = SelectedAccountText.Text;
        if (string.IsNullOrEmpty(username)) return;
        string? selected = PresetSelector.SelectedItem as string;
        if (string.IsNullOrEmpty(selected)) return;

        string src = Path.Combine(@"C:\The Lads Client", "presets", username, selected + ".png");
        string dest = Path.Combine(@"C:\The Lads Client", "skin.png");
        if (File.Exists(src))
        {
            File.Copy(src, dest, true);
            Log($"[Presets] Applied skin preset: {selected}");
            _ = LoadPlayerSkin(username);
        }
    }

    private void DeletePreset_Click(object? sender, RoutedEventArgs e)
    {
        string username = SelectedAccountText.Text;
        if (string.IsNullOrEmpty(username)) return;
        string? selected = PresetSelector.SelectedItem as string;
        if (string.IsNullOrEmpty(selected)) return;

        string path = Path.Combine(@"C:\The Lads Client", "presets", username, selected + ".png");
        if (File.Exists(path))
        {
            File.Delete(path);
            Log($"[Presets] Deleted skin preset: {selected}");
            LoadPresetsList(username);
        }
    }

    private async void UploadSkin_Click(object? sender, RoutedEventArgs e)
    {
        var files = await this.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Select Skin PNG File",
            FileTypeFilter = new[] { FilePickerFileTypes.ImageAll }
        });

        if (files != null && files.Count > 0)
        {
            string src = files[0].Path.LocalPath;
            string dest = Path.Combine(@"C:\The Lads Client", "skin.png");
            File.Copy(src, dest, true);
            Log($"[Skin] Imported skin from local file.");
            
            string username = SelectedAccountText.Text;
            if (!string.IsNullOrEmpty(username) && username != "No Account Selected")
                _ = LoadPlayerSkin(username);
        }
    }

    private void InitializeEditor()
    {
        var palette = new[] {
            Colors.Red, Colors.Orange, Colors.Yellow, Colors.Green,
            Colors.Blue, Colors.Purple, Colors.Pink, Colors.White,
            Colors.Black, Colors.Gray, Colors.Brown, Colors.Teal
        };
        ColorPaletteContainer.Children.Clear();
        foreach (var c in palette)
        {
            var btn = new Button
            {
                Width = 24, Height = 24,
                Background = new SolidColorBrush(c),
                Margin = new Thickness(2),
                CornerRadius = new CornerRadius(4)
            };
            btn.Click += (s, e) => { SetEditorColor(c); };
            ColorPaletteContainer.Children.Add(btn);
        }

        // Sliders property change bindings
        SliderRed.PropertyChanged += (s, e) => { if (e.Property.Name == "Value") UpdateColorFromSliders(); };
        SliderGreen.PropertyChanged += (s, e) => { if (e.Property.Name == "Value") UpdateColorFromSliders(); };
        SliderBlue.PropertyChanged += (s, e) => { if (e.Property.Name == "Value") UpdateColorFromSliders(); };

        ColorHexInput.PropertyChanged += (s, e) => {
            if (e.Property.Name == "Text" && ColorHexInput.Text != null && ColorHexInput.Text.StartsWith("#") && ColorHexInput.Text.Length == 9) {
                try {
                    var parsed = Color.Parse(ColorHexInput.Text);
                    SetEditorColor(parsed);
                } catch {}
            }
        };

        SkinPartSelector.SelectionChanged += (s, e) => UpdateEditorGridSize();
        RadioBaseLayer.IsCheckedChanged += (s, e) => DrawPixelGrid();
        RadioOverlayLayer.IsCheckedChanged += (s, e) => DrawPixelGrid();

        // Initial color setup
        SetEditorColor(Colors.White);

        // Load skin.png if exists
        LoadPixelsFromSkinFile(Path.Combine(@"C:\The Lads Client", "skin.png"));
    }

    private void UpdateColorFromSliders()
    {
        byte r = (byte)SliderRed.Value;
        byte g = (byte)SliderGreen.Value;
        byte b = (byte)SliderBlue.Value;
        _activeEditorColor = Color.FromArgb(255, r, g, b);

        TextRed.Text = r.ToString();
        TextGreen.Text = g.ToString();
        TextBlue.Text = b.ToString();

        ActiveColorPreview.Background = new SolidColorBrush(_activeEditorColor);
        ColorHexInput.Text = $"#{_activeEditorColor.A:X2}{_activeEditorColor.R:X2}{_activeEditorColor.G:X2}{_activeEditorColor.B:X2}";
    }

    private void SetEditorColor(Color color)
    {
        _activeEditorColor = color;
        SliderRed.Value = color.R;
        SliderGreen.Value = color.G;
        SliderBlue.Value = color.B;

        TextRed.Text = color.R.ToString();
        TextGreen.Text = color.G.ToString();
        TextBlue.Text = color.B.ToString();

        ActiveColorPreview.Background = new SolidColorBrush(color);
        ColorHexInput.Text = $"#{color.A:X2}{color.R:X2}{color.G:X2}{color.B:X2}";
    }

    private void ToggleSkinEditor_Click(object? sender, RoutedEventArgs e)
    {
        _selectedEditor = "Skin";
        ToggleSkinEditorBtn.Classes.Add("active");
        ToggleCapeEditorBtn.Classes.Remove("active");
        EditorContainer.IsVisible = true;
        UpdateEditorGridSize();
    }

    private void ToggleCapeEditor_Click(object? sender, RoutedEventArgs e)
    {
        _selectedEditor = "Cape";
        ToggleCapeEditorBtn.Classes.Add("active");
        ToggleSkinEditorBtn.Classes.Remove("active");
        EditorContainer.IsVisible = true;
        UpdateEditorGridSize();
    }

    private void UpdateEditorGridSize()
    {
        if (_selectedEditor == "Cape")
        {
            _editorWidth = 22;
            _editorHeight = 17;
            EditorLayersRow.IsVisible = false;
            SkinPartSelector.IsVisible = false;
        }
        else
        {
            EditorLayersRow.IsVisible = true;
            SkinPartSelector.IsVisible = true;
            string part = (SkinPartSelector.SelectedItem as ComboBoxItem)?.Content as string ?? "Head (8x8)";
            if (part.Contains("Head")) { _editorWidth = 8; _editorHeight = 8; }
            else if (part.Contains("Body")) { _editorWidth = 8; _editorHeight = 12; }
            else { _editorWidth = 4; _editorHeight = 12; }
        }
        DrawPixelGrid();
    }

    private void DrawPixelGrid()
    {
        PixelEditorCanvas.Children.Clear();
        double canvasSize = 192.0;
        double pixelSize = canvasSize / Math.Max(_editorWidth, _editorHeight);

        double offsetX = (canvasSize - (_editorWidth * pixelSize)) / 2;
        double offsetY = (canvasSize - (_editorHeight * pixelSize)) / 2;

        bool isOverlay = RadioOverlayLayer.IsChecked ?? false;

        for (int y = 0; y < _editorHeight; y++)
        {
            for (int x = 0; x < _editorWidth; x++)
            {
                int localX = x;
                int localY = y;
                
                var border = new Border
                {
                    Width = pixelSize,
                    Height = pixelSize,
                    BorderBrush = new SolidColorBrush(Color.Parse("#1A1A22")),
                    BorderThickness = new Thickness(0.5)
                };

                Color c = GetEditorPixel(localX, localY, isOverlay);
                border.Background = new SolidColorBrush(c);

                border.PointerPressed += (s, e) => {
                    if (e.GetCurrentPoint(PixelEditorCanvas).Properties.IsLeftButtonPressed)
                    {
                        _isDrawing = true;
                        border.Background = new SolidColorBrush(_activeEditorColor);
                        SetEditorPixel(localX, localY, isOverlay, _activeEditorColor);
                    }
                };

                border.PointerMoved += (s, e) => {
                    if (_isDrawing && e.GetCurrentPoint(PixelEditorCanvas).Properties.IsLeftButtonPressed)
                    {
                        border.Background = new SolidColorBrush(_activeEditorColor);
                        SetEditorPixel(localX, localY, isOverlay, _activeEditorColor);
                    }
                };

                Canvas.SetLeft(border, offsetX + (x * pixelSize));
                Canvas.SetTop(border, offsetY + (y * pixelSize));
                PixelEditorCanvas.Children.Add(border);
            }
        }
    }

    private (int X, int Y) GetMappedCoordinates(int localX, int localY, bool isOverlay)
    {
        if (_selectedEditor == "Cape")
        {
            return (1 + localX, 1 + localY);
        }

        string part = (SkinPartSelector.SelectedItem as ComboBoxItem)?.Content as string ?? "Head (8x8)";
        if (part.Contains("Head"))
        {
            return (isOverlay ? 40 + localX : 8 + localX, 8 + localY);
        }
        else if (part.Contains("Body"))
        {
            return (isOverlay ? 20 + localX : 20 + localX, isOverlay ? 36 + localY : 20 + localY);
        }
        else if (part.Contains("Left Arm"))
        {
            return (isOverlay ? 52 + localX : 36 + localX, 52 + localY);
        }
        else if (part.Contains("Right Arm"))
        {
            return (isOverlay ? 44 + localX : 44 + localX, isOverlay ? 36 + localY : 20 + localY);
        }
        else if (part.Contains("Left Leg"))
        {
            return (isOverlay ? 4 + localX : 20 + localX, 52 + localY);
        }
        else
        {
            return (isOverlay ? 4 + localX : 4 + localX, isOverlay ? 36 + localY : 20 + localY);
        }
    }

    private Color GetEditorPixel(int localX, int localY, bool isOverlay)
    {
        var (tx, ty) = GetMappedCoordinates(localX, localY, isOverlay);
        if (_selectedEditor == "Cape")
        {
            return _capePixels[tx, ty];
        }
        return isOverlay ? _skinOverlayPixels[tx, ty] : _skinBasePixels[tx, ty];
    }

    private void SetEditorPixel(int localX, int localY, bool isOverlay, Color color)
    {
        var (tx, ty) = GetMappedCoordinates(localX, localY, isOverlay);
        if (_selectedEditor == "Cape")
        {
            _capePixels[tx, ty] = color;
        }
        else
        {
            if (isOverlay)
                _skinOverlayPixels[tx, ty] = color;
            else
                _skinBasePixels[tx, ty] = color;
        }
    }

    private void ClearEditorCanvas_Click(object? sender, RoutedEventArgs e)
    {
        bool isOverlay = RadioOverlayLayer.IsChecked ?? false;
        for (int y = 0; y < _editorHeight; y++)
        {
            for (int x = 0; x < _editorWidth; x++)
            {
                SetEditorPixel(x, y, isOverlay, Colors.Transparent);
            }
        }
        DrawPixelGrid();
    }

    private void SaveEditorDrawing_Click(object? sender, RoutedEventArgs e)
    {
        string username = SelectedAccountText.Text;
        if (string.IsNullOrEmpty(username) || username == "No Account Selected")
        {
            if (_selectedEditor == "Cape")
            {
                string path = Path.Combine(@"C:\The Lads Client", "config", "cape.png");
                Directory.CreateDirectory(Path.GetDirectoryName(path)!);
                SaveSkinPng(path, true);
                Log("[Cape] Saved custom cape to config.");
            }
            else
            {
                string path = Path.Combine(@"C:\The Lads Client", "skin.png");
                SaveSkinPng(path, false);
                Log("[Skin] Saved custom skin.");
            }
            return;
        }

        if (_selectedEditor == "Cape")
        {
            string path = Path.Combine(@"C:\The Lads Client", "config", "cape.png");
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            SaveSkinPng(path, true);
            Log($"[Cape] Saved custom cape for {username}.");
        }
        else
        {
            string path = Path.Combine(@"C:\The Lads Client", "skin.png");
            SaveSkinPng(path, false);
            Log($"[Skin] Saved custom skin for {username}.");
            _ = LoadPlayerSkin(username);
        }
    }

    private void LoadPixelsFromSkinFile(string filePath)
    {
        if (!File.Exists(filePath)) return;
        try
        {
            using (var stream = File.OpenRead(filePath))
            {
                using (var bmp = new Bitmap(stream))
                {
                    var w = (int)bmp.PixelSize.Width;
                    var h = (int)bmp.PixelSize.Height;
                    if (w == 64 && (h == 64 || h == 32))
                    {
                        var writeable = new WriteableBitmap(bmp.PixelSize, new Vector(96, 96), Avalonia.Platform.PixelFormat.Bgra8888, Avalonia.Platform.AlphaFormat.Premul);
                        using (var buf = writeable.Lock())
                        {
                            byte[] raw = new byte[w * h * 4];
                            Marshal.Copy(buf.Address, raw, 0, raw.Length);
                            for (int y = 0; y < h; y++)
                            {
                                for (int x = 0; x < w; x++)
                                {
                                    int idx = (y * w + x) * 4;
                                    byte b = raw[idx];
                                    byte g = raw[idx + 1];
                                    byte r = raw[idx + 2];
                                    byte a = raw[idx + 3];
                                    var color = Color.FromArgb(a, r, g, b);
                                    
                                    _skinBasePixels[x, y] = color;
                                    if (IsOverlayRegion(x, y))
                                    {
                                        _skinOverlayPixels[x, y] = color;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        catch { }
    }

    private bool IsOverlayRegion(int x, int y)
    {
        if (x >= 32 && x < 64 && y >= 0 && y < 16) return true;
        if (x >= 16 && x < 32 && y >= 32 && y < 48) return true;
        return false;
    }

    private void SaveSkinPng(string filePath, bool isCape)
    {
        int w = 64;
        int h = isCape ? 32 : 64;
        
        var bitmap = new WriteableBitmap(new PixelSize(w, h), new Vector(96, 96), Avalonia.Platform.PixelFormat.Bgra8888, Avalonia.Platform.AlphaFormat.Premul);
        using (var buf = bitmap.Lock())
        {
            int size = w * h * 4;
            byte[] raw = new byte[size];
            for (int y = 0; y < h; y++)
            {
                for (int x = 0; x < w; x++)
                {
                    Color c = isCape ? _capePixels[x, y] : _skinBasePixels[x, y];
                    if (!isCape)
                    {
                        if (_skinOverlayPixels[x, y].A > 0)
                        {
                            c = _skinOverlayPixels[x, y];
                        }
                    }
                    
                    int idx = (y * w + x) * 4;
                    raw[idx] = c.B;
                    raw[idx + 1] = c.G;
                    raw[idx + 2] = c.R;
                    raw[idx + 3] = c.A;
                }
            }
            Marshal.Copy(raw, 0, buf.Address, raw.Length);
        }
        bitmap.Save(filePath);
    }

    // ═══════════════════════════════════════
    //  SETTINGS PAGE
    // ═══════════════════════════════════════

    private void LoadSettingsUI()
    {
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
        
        RamSlider.PropertyChanged += (s, e) =>
        {
            if (e.Property.Name == "Value")
                RamValueText.Text = $"{(int)RamSlider.Value} GB";
        };

        CloseToTrayCheckbox.IsChecked = settings.CloseToTray;
        KeepLauncherOpenCheckbox.IsChecked = settings.KeepLauncherOpen;
        KeepClosedOnExitCheckbox.IsChecked = settings.KeepClosedOnExit;
        FullscreenOnLaunchCheckbox.IsChecked = settings.FullscreenOnLaunch;
        QuickLaunchCheckbox.IsChecked = settings.QuickLaunch;
        AutoLaunchCheckbox.IsChecked = settings.AutoLaunch;
        AutoFixCrashesCheckbox.IsChecked = settings.AutoFixCrashes;
        MultiInstanceCheckbox.IsChecked = settings.AllowMultiInstance;
        ParticleCheckbox.IsChecked = settings.ShowParticles;
        SyncResourcePacksCheckbox.IsChecked = settings.SyncResourcePacksFromGlobal;
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

        ModVersionFilterBox.PropertyChanged += (s, e) =>
        {
            if (e.Property.Name == "Text")
                LoadModsList();
        };

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

    private void SaveSettings_Click(object? sender, RoutedEventArgs e)
    {
        settings.MaxRamMb = (int)RamSlider.Value * 1024;
        settings.CloseToTray = CloseToTrayCheckbox.IsChecked ?? true;
        settings.KeepLauncherOpen = KeepLauncherOpenCheckbox.IsChecked ?? false;
        settings.KeepClosedOnExit = KeepClosedOnExitCheckbox.IsChecked ?? false;
        settings.AutoLaunch = AutoLaunchCheckbox.IsChecked ?? false;
        settings.AutoFixCrashes = AutoFixCrashesCheckbox.IsChecked ?? true;
        settings.AllowMultiInstance = MultiInstanceCheckbox.IsChecked ?? false;
        settings.FullscreenOnLaunch = FullscreenOnLaunchCheckbox.IsChecked ?? true;
        settings.QuickLaunch = QuickLaunchCheckbox.IsChecked ?? false;
        settings.ShowParticles = ParticleCheckbox.IsChecked ?? true;
        settings.SyncResourcePacksFromGlobal = SyncResourcePacksCheckbox.IsChecked ?? false;
        settings.SyncScreenshotsToGlobal = SyncScreenshotsCheckbox.IsChecked ?? true;
        settings.FabricVersion = FabricVersionBox.Text ?? settings.FabricVersion;
        settings.CurseForgeApiKey = CurseForgeApiKeyBox.Text ?? "";
        settings.ModrinthApiUrl = ModrinthApiUrlBox.Text ?? settings.ModrinthApiUrl;
        settings.CurseForgeApiUrl = CurseForgeApiUrlBox.Text ?? settings.CurseForgeApiUrl;
        settings.SelectedMinecraftVersionOverride = MinecraftVersionOverrideBox.Text ?? settings.SelectedMinecraftVersionOverride;

        // Sync with Mod Tab
        ModrinthApiUrlBox_ModTab.Text = settings.ModrinthApiUrl;
        CurseForgeApiUrlBox_ModTab.Text = settings.CurseForgeApiUrl;
        CfApiKeyInputBox.Text = settings.CurseForgeApiKey;
        
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
        UpdateMinecraftVersionDisplay();
        ApplyTheme();

        if (settings.ShowParticles)
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

        StatusText.Text = "Settings saved!";
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
    // e.g. "fabric-loader-0.19.2-26.1.2" -> "26.1.2", "1.21.1" -> "1.21.1"
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
        MinecraftVersionText.Text = $"Minecraft: {mcVersion}";
        LaunchButton.Content = $"▶  LAUNCH ({mcVersion})";
        GameLaunchVersionText.Text = $"Minecraft {mcVersion} · Fabric";
        ModVersionFilterBox.Text = mcVersion;
    }

    public class ModFileItem
    {
        public string FilePath { get; set; } = "";
        public string DisplayName { get; set; } = "";
        public string Description { get; set; } = "";
        public byte[]? IconBytes { get; set; }
        public bool IsEnabled { get; set; }
        public List<string> Categories { get; set; } = new();
        public string ModId { get; set; } = "";
        public string ModVersion { get; set; } = "";
    }

    private async void LoadModsList()
    {
        string modsPath = Path.Combine(settings.InstancePath, "mods");
        string rpPath = Path.Combine(settings.InstancePath, "resourcepacks");

        string filterText = ModVersionFilterBox.Text?.Trim() ?? "";
        string nameQuery = ModNameSearchBox?.Text?.Trim() ?? "";
        string fabricVersion = settings.FabricVersion;
        string instancePath = settings.InstancePath;

        // Run metadata extraction and filtering on a background thread
        var matchingMods = await Task.Run(() =>
        {
            var list = new List<ModFileItem>();

            // Resolve equivalent versions
            var equivalentVersions = new List<string> { filterText };
            string resolvedMcVersion = "Unknown";
            try
            {
                resolvedMcVersion = ResolveMinecraftVersion();
            }
            catch {}

            if (!string.IsNullOrEmpty(filterText) && filterText.Equals(resolvedMcVersion, StringComparison.OrdinalIgnoreCase))
            {
                equivalentVersions.Add(fabricVersion);
                var fabricLoaderMatch = Regex.Match(fabricVersion, @"fabric-loader-[\d\.]+-([\d\.]+)");
                if (fabricLoaderMatch.Success)
                {
                    equivalentVersions.Add(fabricLoaderMatch.Groups[1].Value);
                }
                try
                {
                    string versionJsonPath = Path.Combine(instancePath, "versions", fabricVersion, fabricVersion + ".json");
                    if (File.Exists(versionJsonPath))
                    {
                        string jsonContent = File.ReadAllText(versionJsonPath);
                        using (JsonDocument doc = JsonDocument.Parse(jsonContent))
                        {
                            if (doc.RootElement.TryGetProperty("inheritsFrom", out JsonElement inheritsProp))
                            {
                                string inherited = inheritsProp.GetString() ?? "";
                                if (!string.IsNullOrEmpty(inherited))
                                {
                                    equivalentVersions.Add(inherited);
                                }
                            }
                        }
                    }
                }
                catch {}
            }

            // 1. Scan Installed Mods
            if (Directory.Exists(modsPath))
            {
                var jarFiles = Directory.GetFiles(modsPath, "*.jar")
                    .Concat(Directory.GetFiles(modsPath, "*.jar.disabled"))
                    .OrderBy(f => Path.GetFileName(f))
                    .ToArray();

                foreach (var file in jarFiles)
                {
                    string fileName = Path.GetFileName(file);
                    bool isEnabled = !fileName.EndsWith(".disabled");
                    string displayName = isEnabled ? fileName.Replace(".jar", "") : fileName.Replace(".jar.disabled", "");
                    string description = "";
                    string modId = "";
                    string modVersion = "";
                    byte[]? iconBytes = null;
                    var categories = new List<string> { "Mod", "Fabric" };

                    bool matchesFilter = true;
                    if (!string.IsNullOrEmpty(filterText))
                    {
                        matchesFilter = false;
                        foreach (var eqVer in equivalentVersions)
                        {
                            if (fileName.Contains(eqVer, StringComparison.OrdinalIgnoreCase))
                            {
                                matchesFilter = true;
                                break;
                            }
                        }
                    }

                    // Try to read zip content for description/icon
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
                            var root = doc.RootElement;
                            
                            if (root.TryGetProperty("id", out var idProp2))
                                modId = idProp2.GetString() ?? "";
                            if (root.TryGetProperty("version", out var verProp2))
                                modVersion = verProp2.GetString() ?? "";
                            if (root.TryGetProperty("name", out var nameProp))
                            {
                                displayName = nameProp.GetString() ?? displayName;
                            }
                            if (root.TryGetProperty("description", out var descProp))
                            {
                                description = descProp.GetString() ?? "";
                            }
                            if (root.TryGetProperty("icon", out var iconProp))
                            {
                                string iconPath = iconProp.GetString() ?? "";
                                if (!string.IsNullOrEmpty(iconPath))
                                {
                                    var iconEntry = archive.GetEntry(iconPath);
                                    if (iconEntry != null)
                                    {
                                        using var iconStream = iconEntry.Open();
                                        using var ms = new MemoryStream();
                                        iconStream.CopyTo(ms);
                                        iconBytes = ms.ToArray();
                                    }
                                }
                            }

                            if (!string.IsNullOrEmpty(filterText) && !matchesFilter)
                            {
                                foreach (var eqVer in equivalentVersions)
                                {
                                    if (json.Contains(eqVer, StringComparison.OrdinalIgnoreCase))
                                    {
                                        matchesFilter = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    catch {}

                    bool matchesName = string.IsNullOrEmpty(nameQuery)
                        || displayName.Contains(nameQuery, StringComparison.OrdinalIgnoreCase)
                        || fileName.Contains(nameQuery, StringComparison.OrdinalIgnoreCase)
                        || modId.Contains(nameQuery, StringComparison.OrdinalIgnoreCase);

                    if (matchesFilter && matchesName)
                    {
                        list.Add(new ModFileItem
                        {
                            FilePath = file,
                            DisplayName = displayName,
                            Description = description,
                            IconBytes = iconBytes,
                            IsEnabled = isEnabled,
                            Categories = categories,
                            ModId = modId,
                            ModVersion = modVersion
                        });
                    }
                }
            }

            // 2. Scan Installed Resource Packs (only if no mod version filter is set, or if filter matches pack name)
            if (Directory.Exists(rpPath))
            {
                var zipFiles = Directory.GetFiles(rpPath, "*.zip")
                    .Concat(Directory.GetFiles(rpPath, "*.zip.disabled"))
                    .OrderBy(f => Path.GetFileName(f))
                    .ToArray();

                foreach (var file in zipFiles)
                {
                    string fileName = Path.GetFileName(file);
                    bool isEnabled = !fileName.EndsWith(".disabled");
                    string displayName = isEnabled ? fileName.Replace(".zip", "") : fileName.Replace(".zip.disabled", "");
                    
                    bool matchesFilter = true;
                    if (!string.IsNullOrEmpty(filterText))
                    {
                        matchesFilter = fileName.Contains(filterText, StringComparison.OrdinalIgnoreCase);
                    }
                    bool matchesName = string.IsNullOrEmpty(nameQuery)
                        || displayName.Contains(nameQuery, StringComparison.OrdinalIgnoreCase)
                        || fileName.Contains(nameQuery, StringComparison.OrdinalIgnoreCase);

                    if (matchesFilter && matchesName)
                    {
                        list.Add(new ModFileItem
                        {
                            FilePath = file,
                            DisplayName = displayName,
                            Description = "Local Resource Pack",
                            IsEnabled = isEnabled,
                            Categories = new List<string> { "Resource Pack" }
                        });
                    }
                }
            }

            return list;
        });

        // Populate elements on UI Thread
        _selectedModPaths.Clear();
        UpdateModsSelectionBar();
        if (SelectAllModsCheckbox != null) SelectAllModsCheckbox.IsChecked = false;
        ModsList.Children.Clear();
        var template = this.FindResource("ModListItemTemplate") as DataTemplate;
        if (template == null) return;

        int totalEnabledCount = 0;
        foreach (var mod in matchingMods)
        {
            if (mod.IsEnabled && !mod.FilePath.Contains("resourcepacks"))
                totalEnabledCount++;

            var row = template.Build(mod) as Border;
            if (row == null) continue;

            row.Opacity = mod.IsEnabled ? 1.0 : 0.5;
            row.Margin = new Thickness(0);

            var grid = row.Child as Grid;
            if (grid == null) continue;

            var iconFrame = grid.Children[0] as Border;
            var iconImage = iconFrame?.Child as Image;
            var detailsPanel = grid.Children[1] as StackPanel;
            var titleBlock = detailsPanel.Children[0] as TextBlock;
            var descBlock = detailsPanel.Children[1] as TextBlock;
            var metaPanel = detailsPanel.Children[2] as StackPanel;
            var downloadsBlock = metaPanel.Children[0] as TextBlock;
            var badgesPanel = metaPanel.Children[1] as StackPanel;
            var actionPanel = grid.Children[2] as StackPanel;

            // Titles & Descriptions
            titleBlock.Text = mod.DisplayName;
            titleBlock.Foreground = new SolidColorBrush(mod.IsEnabled ? Color.Parse("#CCCCCC") : Color.Parse("#666666"));
            descBlock.Text = string.IsNullOrEmpty(mod.Description) ? "No description available." : mod.Description;
            downloadsBlock.Text = string.IsNullOrEmpty(mod.ModVersion) ? "Local file" : $"v{mod.ModVersion}";

            // Render local icon if present
            if (iconImage != null)
            {
                if (mod.IconBytes != null)
                {
                    try { using var ms = new MemoryStream(mod.IconBytes); iconImage.Source = new Bitmap(ms); }
                    catch {}
                }
                else { iconImage.Source = null; }
            }

            // Categories Badges
            if (badgesPanel != null)
            {
                badgesPanel.Children.Clear();
                foreach (var catName in mod.Categories)
                {
                    badgesPanel.Children.Add(new Border
                    {
                        Background = new SolidColorBrush(Color.Parse("#1A1A2E")),
                        CornerRadius = new CornerRadius(4),
                        Padding = new Thickness(6, 2),
                        Margin = new Thickness(0, 0, 4, 0),
                        Child = new TextBlock { Text = catName, Foreground = new SolidColorBrush(Color.Parse("#FF4444")), FontSize = 9, FontWeight = FontWeight.Bold }
                    });
                }
            }

            // Action buttons
            string filePath = mod.FilePath;
            bool isEnabled = mod.IsEnabled;

            // Update button (only for mods with a Modrinth id)
            if (!string.IsNullOrEmpty(mod.ModId) && !mod.FilePath.Contains("resourcepacks"))
            {
                var modSnap = mod;
                var updateBtn = new Button
                {
                    Content = "⬆",
                    Height = 30, Width = 36, FontSize = 11,
                    HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center,
                    VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center
                };
                updateBtn.Classes.Add("action");
                ToolTip.SetTip(updateBtn, "Check & update from Modrinth");
                updateBtn.Click += async (s, e) => await CheckAndUpdateSingleMod(modSnap, updateBtn);
                actionPanel.Children.Add(updateBtn);
            }

            var toggleBtn = new Button
            {
                Content = isEnabled ? "Disable" : "Enable",
                Height = 30, Width = 70, FontSize = 11,
                HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center,
                VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center
            };
            toggleBtn.Classes.Add(isEnabled ? "danger" : "action");
            toggleBtn.Click += (s, e) => ToggleMod(filePath, isEnabled);
            actionPanel.Children.Add(toggleBtn);

            var deleteBtn = new Button
            {
                Content = "✕",
                Height = 30, Width = 30, FontSize = 12,
                HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center,
                VerticalContentAlignment = Avalonia.Layout.VerticalAlignment.Center
            };
            deleteBtn.Classes.Add("danger");
            deleteBtn.Click += (s, e) => DeleteMod(filePath);
            actionPanel.Children.Add(deleteBtn);

            // Wrap in a row with a selection checkbox
            var cb = new CheckBox
            {
                VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 8, 0),
                Tag = filePath
            };
            cb.IsCheckedChanged += (s, e) =>
            {
                string fp2 = (cb.Tag as string) ?? "";
                if (cb.IsChecked == true) _selectedModPaths.Add(fp2);
                else _selectedModPaths.Remove(fp2);
                UpdateModsSelectionBar();
            };

            var wrapper = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*"), Margin = new Thickness(0, 0, 0, 8) };
            Grid.SetColumn(cb, 0);
            Grid.SetColumn(row, 1);
            wrapper.Children.Add(cb);
            wrapper.Children.Add(row);
            ModsList.Children.Add(wrapper);
        }
        ModsPageCount.Text = $"({totalEnabledCount})";
    }


    private void ToggleMod(string filePath, bool isCurrentlyEnabled)
    {
        try
        {
            if (isCurrentlyEnabled)
            {
                File.Move(filePath, filePath + ".disabled");
                Log($"[Mods] Disabled: {Path.GetFileName(filePath)}");
            }
            else
            {
                string enabledPath = filePath.Replace(".jar.disabled", ".jar");
                File.Move(filePath, enabledPath);
                Log($"[Mods] Enabled: {Path.GetFileName(enabledPath)}");
            }
            LoadModsList();
        }
        catch (Exception ex)
        {
            Log($"[Mods ERROR] {ex.Message}");
        }
    }

    private void DeleteMod(string filePath)
    {
        try
        {
            File.Delete(filePath);
            Log($"[Mods] Deleted: {Path.GetFileName(filePath)}");
            LoadModsList();
        }
        catch (Exception ex)
        {
            Log($"[Mods ERROR] {ex.Message}");
        }
    }

    private void RefreshMods_Click(object? sender, RoutedEventArgs e) => LoadModsList();

    // ─── Add / search / drag-drop for installed mods ───────────────

    private void ModNameSearch_KeyUp(object? sender, KeyEventArgs e)
    {
        // Re-scan on Enter (cheap key-by-key re-scan is avoided because it re-reads jar metadata).
        if (e.Key == Key.Enter) LoadModsList();
    }

    private async void AddModFromFile_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            var files = await this.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
            {
                Title = "Add mod .jar file(s)",
                AllowMultiple = true,
                FileTypeFilter = new[]
                {
                    new FilePickerFileType("Fabric mod (*.jar)") { Patterns = new[] { "*.jar" } }
                }
            });
            if (files == null || files.Count == 0) return;
            int added = InstallModFiles(files.Select(f => f.Path.LocalPath));
            StatusText.Text = $"Added {added} mod(s).";
            LoadModsList();
        }
        catch (Exception ex) { Log($"[Mods] Add from file failed: {ex.Message}"); }
    }

    private async void AddModFromFolder_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            var folders = await this.StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions
            {
                Title = "Add all .jar files from a folder",
                AllowMultiple = false
            });
            if (folders == null || folders.Count == 0) return;
            string dir = folders[0].Path.LocalPath;
            if (!Directory.Exists(dir)) return;
            int added = InstallModFiles(Directory.GetFiles(dir, "*.jar"));
            StatusText.Text = $"Added {added} mod(s) from folder.";
            LoadModsList();
        }
        catch (Exception ex) { Log($"[Mods] Add from folder failed: {ex.Message}"); }
    }

    private void ModsPage_DragOver(object? sender, DragEventArgs e)
    {
        // Only accept file drops (Avalonia 12 data-transfer API)
        e.DragEffects = e.DataTransfer.Contains(DataFormat.File)
            ? DragDropEffects.Copy
            : DragDropEffects.None;
        e.Handled = true;
    }

    private void ModsPage_Drop(object? sender, DragEventArgs e)
    {
        try
        {
            var files = e.DataTransfer.TryGetFiles();
            if (files != null)
            {
                int added = InstallModFiles(files.Select(f => f.Path.LocalPath));
                if (added > 0)
                {
                    StatusText.Text = $"Added {added} mod(s) via drag-and-drop.";
                    LoadModsList();
                }
            }
        }
        catch (Exception ex) { Log($"[Mods] Drop failed: {ex.Message}"); }
        e.Handled = true;
    }

    // Copies the given .jar paths into the instance mods folder. Returns the number installed.
    private int InstallModFiles(IEnumerable<string> paths)
    {
        string modsDir = Path.Combine(settings.InstancePath, "mods");
        Directory.CreateDirectory(modsDir);
        int count = 0;
        foreach (var src in paths)
        {
            try
            {
                if (string.IsNullOrEmpty(src) || !src.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)) continue;
                if (!File.Exists(src)) continue;
                string dest = Path.Combine(modsDir, Path.GetFileName(src));
                File.Copy(src, dest, overwrite: true);
                Log($"[Mods] Installed {Path.GetFileName(src)}");
                count++;
            }
            catch (Exception ex) { Log($"[Mods] Failed to copy {src}: {ex.Message}"); }
        }
        return count;
    }

    // ─── Multi-select helpers ───────────────

    private void UpdateModsSelectionBar()
    {
        int count = _selectedModPaths.Count;
        if (ModsMassActionBar != null) ModsMassActionBar.IsVisible = count > 0;
        if (ModsSelectionCount != null) ModsSelectionCount.Text = $"{count} selected";
    }

    private void SelectAllMods_Click(object? sender, RoutedEventArgs e)
    {
        bool selectAll = SelectAllModsCheckbox?.IsChecked == true;
        _selectedModPaths.Clear();
        foreach (var child in ModsList.Children)
        {
            if (child is Grid wrapper && wrapper.Children.Count > 0 && wrapper.Children[0] is CheckBox cb)
            {
                cb.IsChecked = selectAll;
                if (selectAll && cb.Tag is string fp && !string.IsNullOrEmpty(fp))
                    _selectedModPaths.Add(fp);
            }
        }
        UpdateModsSelectionBar();
    }

    private void EnableSelectedMods_Click(object? sender, RoutedEventArgs e)
    {
        foreach (var fp in _selectedModPaths.ToList())
            if (fp.EndsWith(".disabled")) ToggleMod(fp, false);
        _selectedModPaths.Clear();
        LoadModsList();
    }

    private void DisableSelectedMods_Click(object? sender, RoutedEventArgs e)
    {
        foreach (var fp in _selectedModPaths.ToList())
            if (!fp.EndsWith(".disabled")) ToggleMod(fp, true);
        _selectedModPaths.Clear();
        LoadModsList();
    }

    private void DeleteSelectedMods_Click(object? sender, RoutedEventArgs e)
    {
        foreach (var fp in _selectedModPaths.ToList())
        {
            try { File.Delete(fp); Log($"[Mods] Deleted: {Path.GetFileName(fp)}"); }
            catch (Exception ex) { Log($"[Mods] Delete failed: {ex.Message}"); }
        }
        _selectedModPaths.Clear();
        LoadModsList();
    }

    private async void UpdateSelectedMods_Click(object? sender, RoutedEventArgs e)
    {
        var paths = _selectedModPaths.ToList();
        if (paths.Count == 0) return;
        int updated = await RunModUpdates(paths);
        StatusText.Text = updated > 0 ? $"Updated {updated} mod(s)." : "Selected mods are up to date.";
        if (updated > 0) LoadModsList();
    }

    private async void UpdateAllMods_Click(object? sender, RoutedEventArgs e)
    {
        if (UpdateAllModsBtn != null) { UpdateAllModsBtn.IsEnabled = false; UpdateAllModsBtn.Content = "⬆ Checking..."; }
        string modsPath = Path.Combine(settings.InstancePath, "mods");
        var paths = Directory.Exists(modsPath) ? Directory.GetFiles(modsPath, "*.jar").ToList() : new List<string>();
        int updated = await RunModUpdates(paths);
        if (UpdateAllModsBtn != null) { UpdateAllModsBtn.IsEnabled = true; UpdateAllModsBtn.Content = "⬆ Update All"; }
        StatusText.Text = updated > 0 ? $"Updated {updated} mod(s)." : "All mods are up to date.";
        if (updated > 0) LoadModsList();
    }

    private async Task<int> RunModUpdates(List<string> jarPaths)
    {
        string mcVersion = ResolveMinecraftVersion();
        int updated = 0;
        foreach (var file in jarPaths)
        {
            try
            {
                string modId = "", modVersion = "";
                using (var archive = ZipFile.OpenRead(file))
                {
                    var entry = archive.GetEntry("fabric.mod.json");
                    if (entry == null) continue;
                    using var stream = entry.Open();
                    using var reader = new StreamReader(stream);
                    using var doc = JsonDocument.Parse(reader.ReadToEnd());
                    var root = doc.RootElement;
                    if (root.TryGetProperty("id", out var idEl)) modId = idEl.GetString() ?? "";
                    if (root.TryGetProperty("version", out var verEl)) modVersion = verEl.GetString() ?? "";
                }
                if (string.IsNullOrEmpty(modId)) continue;

                string apiUrl = $"{settings.ModrinthApiUrl}/project/{modId}/version?loaders=[\"fabric\"]&game_versions=[\"{mcVersion}\"]";
                var resp = await _httpClient.GetStringAsync(apiUrl);
                using var vDoc = JsonDocument.Parse(resp);
                var arr = vDoc.RootElement;
                if (arr.ValueKind != JsonValueKind.Array || arr.GetArrayLength() == 0) continue;

                var latest = arr[0];
                string latestVer = latest.TryGetProperty("version_number", out var vn) ? vn.GetString() ?? "" : "";
                if (string.IsNullOrEmpty(latestVer) || latestVer == modVersion) continue;

                string? dlUrl = GetPrimaryDownloadUrl(latest);
                if (string.IsNullOrEmpty(dlUrl)) continue;

                var bytes = await _httpClient.GetByteArrayAsync(dlUrl);
                string dir = Path.GetDirectoryName(file)!;
                string newName = Uri.UnescapeDataString(Path.GetFileName(new Uri(dlUrl).AbsolutePath));
                File.Delete(file);
                File.WriteAllBytes(Path.Combine(dir, newName), bytes);
                Log($"[Mods] Updated {modId}: {modVersion} → {latestVer}");
                updated++;
            }
            catch (Exception ex) { Log($"[Mods] Update skipped ({Path.GetFileName(file)}): {ex.Message}"); }
        }
        return updated;
    }

    private async Task CheckAndUpdateSingleMod(ModFileItem mod, Button btn)
    {
        btn.Content = "...";
        btn.IsEnabled = false;
        try
        {
            string mcVersion = ResolveMinecraftVersion();
            string apiUrl = $"{settings.ModrinthApiUrl}/project/{mod.ModId}/version?loaders=[\"fabric\"]&game_versions=[\"{mcVersion}\"]";
            var resp = await _httpClient.GetStringAsync(apiUrl);
            using var doc = JsonDocument.Parse(resp);
            var arr = doc.RootElement;
            if (arr.ValueKind != JsonValueKind.Array || arr.GetArrayLength() == 0)
            {
                btn.Content = "—"; ToolTip.SetTip(btn, "Not on Modrinth for this version"); return;
            }
            var latest = arr[0];
            string latestVer = latest.TryGetProperty("version_number", out var vn) ? vn.GetString() ?? "" : "";
            if (string.IsNullOrEmpty(latestVer) || latestVer == mod.ModVersion)
            {
                btn.Content = "✓"; ToolTip.SetTip(btn, $"Up to date ({mod.ModVersion})"); return;
            }
            // Update available — download immediately
            string? dlUrl = GetPrimaryDownloadUrl(latest);
            if (string.IsNullOrEmpty(dlUrl)) { btn.Content = "?"; return; }
            btn.Content = $"↓ {latestVer}";
            var bytes = await _httpClient.GetByteArrayAsync(dlUrl);
            string dir = Path.GetDirectoryName(mod.FilePath)!;
            string newName = Uri.UnescapeDataString(Path.GetFileName(new Uri(dlUrl).AbsolutePath));
            File.Delete(mod.FilePath);
            File.WriteAllBytes(Path.Combine(dir, newName), bytes);
            Log($"[Mods] Updated {mod.ModId}: {mod.ModVersion} → {latestVer}");
            StatusText.Text = $"Updated: {mod.DisplayName} → {latestVer}";
            LoadModsList();
        }
        catch (Exception ex)
        {
            btn.Content = "✕"; btn.IsEnabled = false;
            ToolTip.SetTip(btn, $"Failed: {ex.Message}");
            Log($"[Mods] Update check failed for {mod.ModId}: {ex.Message}");
        }
    }

    private static string? GetPrimaryDownloadUrl(JsonElement versionElement)
    {
        if (!versionElement.TryGetProperty("files", out var files) || files.GetArrayLength() == 0) return null;
        foreach (var f in files.EnumerateArray())
        {
            if (f.TryGetProperty("primary", out var p) && p.GetBoolean() && f.TryGetProperty("url", out var u))
                return u.GetString();
        }
        return files[0].TryGetProperty("url", out var fallback) ? fallback.GetString() : null;
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
            System.IO.File.WriteAllText(@"C:\Users\Arash\Desktop\crash_launchbtn.txt", ex.ToString());
            Log($"[CRASH] {ex.Message}");
        } finally {
            LaunchButton.IsEnabled = true;
            GameLaunchOverlay.IsVisible = false;
        }
    }

    private async Task LaunchGame()
    {
        if (!settings.AllowMultiInstance && _runningProcesses.Any(p => !p.HasExited))
        {
            var dialog = new Window
            {
                Title = "Game Already Running",
                Width = 400, Height = 160,
                WindowStartupLocation = WindowStartupLocation.CenterOwner,
                Background = new SolidColorBrush(Color.Parse("#0A0A0F")),
                CanResize = false
            };
            var panel = new StackPanel { Margin = new Thickness(20), Spacing = 12 };
            panel.Children.Add(new TextBlock { Text = "⚠️ Game Already Running", Foreground = Brushes.Orange, FontSize = 16, FontWeight = FontWeight.Bold });
            panel.Children.Add(new TextBlock { Text = "The game is already running! Please turn on 'Allow launching multiple copies' in settings if you want to open another instance.", TextWrapping = TextWrapping.Wrap, Foreground = Brushes.White, FontSize = 12 });
            var okBtn = new Button { Content = "OK", Width = 80, Height = 32, HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Right };
            okBtn.Click += (s, e) => dialog.Close();
            panel.Children.Add(okBtn);
            dialog.Content = panel;
            dialog.ShowDialog(this);
            return;
        }

        GameLaunchOverlay.IsVisible = true;
        GameLaunchProgressBar.Value = 0;
        GameLaunchStatusText.Text = "Initializing...";

        StatusText.Text = "Initializing...";
        Log("[Launcher] Starting launch sequence...");

        var path = new MinecraftPath(settings.InstancePath);
        var launcher = new MinecraftLauncher(path);

        _lastBytes = 0;
        _lastDownloadTime = DateTime.UtcNow;

        launcher.ByteProgressChanged += (sender, args) =>
        {
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
            Log($"[Launcher] Downloading: {args.Name}");
        };

        GameLaunchStatusText.Text = "Checking version...";

        // Use the alt-account override if the user picked one; otherwise the main account.
        // This launches the alt WITHOUT changing _selectedAccount (the persisted main).
        string selectedUser = (!string.IsNullOrEmpty(_launchAccountOverride)
                && (settings.OfflineAccounts.Contains(_launchAccountOverride)
                    || loginHandler.AccountManager.GetAccounts().Any(a =>
                        (a as CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount)?.Profile?.Username == _launchAccountOverride)))
            ? _launchAccountOverride
            : _selectedAccount;
        if (selectedUser != _selectedAccount)
            Log($"[Launcher] Launching with alt account '{selectedUser}' (main stays '{_selectedAccount}').");
        bool isOffline = settings.OfflineAccounts.Contains(selectedUser)
            || selectedUser == "TestPlayer"
            || Environment.GetCommandLineArgs().Contains("--auto-launch-offline");

        MSession session;
        if (isOffline)
        {
            session = MSession.CreateOfflineSession(selectedUser);
        }
        else
        {
            var msAccount = loginHandler.AccountManager.GetAccounts().FirstOrDefault(a => (a as CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount)?.Profile?.Username == selectedUser);

            try
            {
                if (msAccount == null)
                {
                    // No saved account: open the embedded Microsoft sign-in window
                    GameLaunchStatusText.Text = "Opening Microsoft sign-in...";
                    session = await loginHandler.AuthenticateInteractively();
                }
                else
                {
                    try
                    {
                        // Use cached refresh tokens — no UI
                        GameLaunchStatusText.Text = "Logging in silently...";
                        session = await loginHandler.AuthenticateSilently(msAccount);
                    }
                    catch (Exception silentEx)
                    {
                        // Tokens expired/revoked: fall back to the sign-in window
                        Log($"[Auth] Silent login failed ({silentEx.Message}). Falling back to interactive login...");
                        GameLaunchStatusText.Text = "Session expired — please sign in again...";
                        session = await loginHandler.AuthenticateInteractively(msAccount);
                    }
                }
                loginHandler.AccountManager.SaveAccounts();
            }
            catch (PlatformNotSupportedException)
            {
                GameLaunchStatusText.Text = "Login failed: WebView2 Runtime missing.";
                Log("[Auth ERROR] WebView2 Runtime not found. Install 'Microsoft Edge WebView2 Runtime'.");
                throw;
            }
            catch (Exception ex)
            {
                GameLaunchStatusText.Text = "Login failed.";
                Log($"[Auth ERROR] {ex.Message}");
                throw;
            }
        }

        LoadAccounts();
        // If launching an alt, make sure the game reads the alt's profile (not the main's,
        // which LoadAccounts just wrote). Awaited so it's the last write before process start.
        if (selectedUser != _selectedAccount)
            await WriteLadsProfileAsync(selectedUser);
        GameLaunchStatusText.Text = $"Welcome, {session.Username}!";
        Log($"[Auth] Logged in as {session.Username}");

        if (!isOffline)
        {
            GameLaunchStatusText.Text = "Checking for mod updates...";
            await RunPackwizInstaller();
        }

        string launchVersionId = ResolveLaunchVersionId();
        Log($"[Launcher] Building process for {launchVersionId}...");

        // QuickLaunch: skip asset verification for a faster startup.
        // If the game fails to start, the user should turn QuickLaunch off.
        System.Diagnostics.Process process;
        var launchOpt = new MLaunchOption
        {
            MaximumRamMb = settings.MaxRamMb,
            Session = session,
            JavaPath = settings.JavaPath
        };

        if (settings.QuickLaunch)
        {
            GameLaunchStatusText.Text = "Quick launching (skipping verification)...";
            Log("[Launcher] QuickLaunch enabled — skipping asset verification.");
            process = await launcher.BuildProcessAsync(launchVersionId, launchOpt);
        }
        else
        {
            process = await launcher.InstallAndBuildProcessAsync(launchVersionId, launchOpt);
        }

        _runningProcesses.Add(process);

        process.StartInfo.UseShellExecute = false;
        process.StartInfo.RedirectStandardOutput = true;
        process.StartInfo.RedirectStandardError = true;
        process.StartInfo.CreateNoWindow = true;

        process.EnableRaisingEvents = true;
        process.Exited += (s, ev) =>
        {
            Dispatcher.UIThread.Post(() =>
            {
                int exitCode = 0;
                try { exitCode = process.ExitCode; } catch { }

                // Re-show the launcher when the game closes, unless the user opted out.
                if (!settings.KeepClosedOnExit)
                {
                    this.Show();
                    this.WindowState = WindowState.Normal;
                }
                DownloadSpeedText.Text = "0 MB/s";

                if (exitCode != 0)
                {
                    StatusText.Text = $"Game crashed! (exit code: {exitCode})";
                    Log($"[Launcher] Game exited with code {exitCode}");
                    HandleCrashDetection();
                }
                else
                {
                    StatusText.Text = "Game exited normally.";
                    Log("[Launcher] Game exited normally.");
                }

                if (settings.SyncScreenshotsToGlobal)
                    SyncScreenshotsToGlobal();
            });
        };

        process.OutputDataReceived += (s, ev) => { if (!string.IsNullOrEmpty(ev.Data)) Log($"[Game] {ev.Data}"); };
        process.ErrorDataReceived += (s, ev) => { if (!string.IsNullOrEmpty(ev.Data)) Log($"[Game ERROR] {ev.Data}"); };

        // Sync resource packs from global .minecraft folder before launch.
        if (settings.SyncResourcePacksFromGlobal)
            SyncResourcePacksFromGlobal();

        // Apply fullscreen setting by patching options.txt before launch.
        if (settings.FullscreenOnLaunch)
        {
            try
            {
                string optFile = System.IO.Path.Combine(settings.InstancePath, "options.txt");
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
                System.IO.File.WriteAllText(optFile, string.Join('\n', lines));
            }
            catch (Exception ex) { Log($"[Launch] Could not set fullscreen in options.txt: {ex.Message}"); }
        }

        // Prepend JVM performance flags. G1GC is used instead of ZGC for fast startup
        // (ZGC + AlwaysPreTouch was causing the 10-15s freeze before the window appeared).
        string jvmFlags =
            "-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:+DisableExplicitGC " +
            "-XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=50 " +
            "-XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:G1HeapRegionSize=32M " +
            "-XX:ReservedCodeCacheSize=256m -XX:+OptimizeStringConcat " +
            "-Dfml.ignoreInvalidMinecraftCertificates=true " +
            "-Dfml.ignorePatchDiscrepancies=true -Djava.net.preferIPv4Stack=true";
        if (!string.IsNullOrWhiteSpace(process.StartInfo.Arguments))
            process.StartInfo.Arguments = jvmFlags + " " + process.StartInfo.Arguments;
        else if (process.StartInfo.ArgumentList.Count > 0)
        {
            // CmlLib uses ArgumentList — insert flags before the first arg
            var flags = jvmFlags.Split(' ');
            for (int fi = flags.Length - 1; fi >= 0; fi--)
                process.StartInfo.ArgumentList.Insert(0, flags[fi]);
        }

        GameLaunchStatusText.Text = "Launching game...";
        Log("[Launcher] Starting game process...");
        process.Start();
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();

        // Pop the startup splash immediately so there is never a dead gap
        // between pressing Launch and the Minecraft window appearing.
        var startupSplash = new Views.GameStartupSplash();
        startupSplash.Show();
        _ = WatchForGameWindowAsync(process, startupSplash);

        // Boost to RealTime during game startup so loading is faster; revert after 120s.
        _ = Task.Run(async () => {
            try { process.PriorityClass = ProcessPriorityClass.RealTime; Log("[Launcher] Process priority set to RealTime."); }
            catch { }
            await Task.Delay(120_000);
            try {
                if (!process.HasExited) {
                    process.PriorityClass = ProcessPriorityClass.Normal;
                    Log("[Launcher] Process priority reverted to Normal.");
                }
            } catch { }
        });

        StatusText.Text = "Game running.";
        GameLaunchOverlay.IsVisible = false;

        // Hide to tray after launch, unless the user wants the launcher to stay open.
        if (settings.CloseToTray && !settings.KeepLauncherOpen)
            this.Hide();
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
                    break;
                }
                try
                {
                    process.Refresh();
                    if (process.MainWindowHandle != IntPtr.Zero)
                    {
                        Log($"[Launcher] Game window detected after {sw.Elapsed.TotalSeconds:F1}s.");
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
        Dispatcher.UIThread.Post(() => { try { splash.Close(); } catch { } });
    }

    // If the configured version doesn't exist in the instance's versions folder,
    // fall back to the newest installed fabric-loader profile instead of failing.
    private string ResolveLaunchVersionId()
    {
        string want = settings.FabricVersion;
        string vdir = Path.Combine(settings.InstancePath, "versions");
        if (File.Exists(Path.Combine(vdir, want, want + ".json"))) return want;

        if (Directory.Exists(vdir))
        {
            var candidates = Directory.GetDirectories(vdir)
                .Select(d => Path.GetFileName(d) ?? "")
                .Where(n => !string.IsNullOrEmpty(n) && File.Exists(Path.Combine(vdir, n, n + ".json")))
                .ToList();

            var best = candidates.Where(n => n.StartsWith("fabric-loader-", StringComparison.OrdinalIgnoreCase))
                                 .OrderByDescending(n => n, StringComparer.OrdinalIgnoreCase)
                                 .FirstOrDefault()
                       ?? candidates.OrderByDescending(n => n, StringComparer.OrdinalIgnoreCase).FirstOrDefault();
            if (best != null)
            {
                Log($"[Launcher] Configured version '{want}' not found in instance. Falling back to installed '{best}'.");
                return best;
            }
        }
        return want;
    }

    // ═══════════════════════════════════════
    //  CRASH DETECTION
    // ═══════════════════════════════════════

    private void SyncResourcePacksFromGlobal()
    {
        try
        {
            string globalPacks = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                ".minecraft", "resourcepacks");
            string instancePacks = Path.Combine(settings.InstancePath, "resourcepacks");

            if (!Directory.Exists(globalPacks)) return;
            Directory.CreateDirectory(instancePacks);

            int count = 0;
            foreach (string src in Directory.GetFileSystemEntries(globalPacks))
            {
                string name = Path.GetFileName(src);
                string dst = Path.Combine(instancePacks, name);
                if (File.Exists(src))
                {
                    if (!File.Exists(dst) || File.GetLastWriteTime(src) > File.GetLastWriteTime(dst))
                    {
                        File.Copy(src, dst, overwrite: true);
                        count++;
                    }
                }
                else if (Directory.Exists(src) && !Directory.Exists(dst))
                {
                    CopyDirectoryRecursive(src, dst);
                    count++;
                }
            }
            Log($"[Sync] Synced {count} resource pack(s) from global .minecraft.");
        }
        catch (Exception ex)
        {
            Log($"[Sync] Resource packs sync failed: {ex.Message}");
        }
    }

    private void SyncScreenshotsToGlobal()
    {
        try
        {
            string instanceScreenshots = Path.Combine(settings.InstancePath, "screenshots");
            string globalScreenshots = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                ".minecraft", "screenshots");

            if (!Directory.Exists(instanceScreenshots)) return;
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
                Log($"[Sync] Synced {count} screenshot(s) to global .minecraft.");
        }
        catch (Exception ex)
        {
            Log($"[Sync] Screenshots sync failed: {ex.Message}");
        }
    }

    private static void CopyDirectoryRecursive(string src, string dst)
    {
        Directory.CreateDirectory(dst);
        foreach (string file in Directory.GetFiles(src))
            File.Copy(file, Path.Combine(dst, Path.GetFileName(file)), overwrite: true);
        foreach (string dir in Directory.GetDirectories(src))
            CopyDirectoryRecursive(dir, Path.Combine(dst, Path.GetFileName(dir)));
    }

    private void HandleCrashDetection()
    {
        try
        {
            string crashDir = Path.Combine(settings.InstancePath, "crash-reports");
            string latestLog = Path.Combine(settings.InstancePath, "logs", "latest.log");

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

            Log($"[Crash] {crashInfo}");

            if (settings.AutoFixCrashes)
            {
                bool fixedCrash = false;

                if (crashFile != null && File.Exists(crashFile))
                {
                    string crashContent = File.ReadAllText(crashFile);
                    var match = Regex.Match(crashContent, @"provided by '([^']+)'");
                    if (match.Success)
                    {
                        string modId = match.Groups[1].Value;
                        string? disabledMod = DisableModById(modId);
                        if (disabledMod != null)
                        {
                            Log($"[AutoFix] Disabled {disabledMod} due to crash.");
                            fixedCrash = true;
                        }
                    }

                    // Mixin crashes (MixinTransformerError / MixinApplyError): find the offending mod
                    if (!fixedCrash && (crashContent.Contains("MixinTransformerError") || crashContent.Contains("MixinApplyError") || crashContent.Contains("InvalidMixinException")))
                    {
                        string? culprit = FindMixinCulpritMod(crashContent);
                        if (culprit != null)
                        {
                            string? disabledMod = DisableModById(culprit) ?? DisableModByMixinConfig(culprit);
                            if (disabledMod != null)
                            {
                                Log($"[AutoFix] Disabled {disabledMod} due to a mixin failure.");
                                crashInfo += $"\nAuto-fixed: disabled '{disabledMod}'.";
                                fixedCrash = true;
                            }
                        }
                    }
                }

                if (!fixedCrash && File.Exists(latestLog))
                {
                    var logContent = File.ReadAllText(latestLog);
                    var match = Regex.Match(logContent, @"Mod '([^']+)' \(([^)]+)\) requires");
                    if (!match.Success) match = Regex.Match(logContent, @"incompatible with.*'([^']+)' \(([^)]+)\)");
                    if (match.Success)
                    {
                        string modId = match.Groups[2].Value;
                        string? disabledMod = DisableModById(modId);
                        if (disabledMod != null)
                        {
                            Log($"[AutoFix] Disabled {disabledMod} due to missing/incompatible dependencies.");
                            fixedCrash = true;
                        }
                    }

                    // Mixin failures often only appear in latest.log
                    if (!fixedCrash && (logContent.Contains("MixinTransformerError") || logContent.Contains("MixinApplyError") || logContent.Contains("InvalidMixinException")))
                    {
                        string? culprit = FindMixinCulpritMod(logContent);
                        if (culprit != null)
                        {
                            string? disabledMod = DisableModById(culprit) ?? DisableModByMixinConfig(culprit);
                            if (disabledMod != null)
                            {
                                Log($"[AutoFix] Disabled {disabledMod} due to a mixin failure.");
                                crashInfo += $"\nAuto-fixed: disabled '{disabledMod}'.";
                                fixedCrash = true;
                            }
                        }
                    }
                }

                if (fixedCrash)
                {
                    StatusText.Text = "Crash auto-fixed. Relaunching...";
                    _ = LaunchGame();
                    return;
                }
            }

            ShowCrashDialog(crashInfo, crashFile);
        }
        catch (Exception ex)
        {
            Log($"[Crash Detection ERROR] {ex.Message}");
        }
    }

    // Extracts the failing mod id (or mixin config name) from a Mixin crash dump.
    // Returns a mod id like "examplemod", or a mixin config like "examplemod.mixins.json".
    private string? FindMixinCulpritMod(string content)
    {
        // 1. "Mixin apply for mod examplemod failed examplemod.mixins.json:SomeMixin ..."
        var m = Regex.Match(content, @"Mixin apply(?:\s+for mod)\s+([a-zA-Z0-9_\-]+)\s+failed");
        if (m.Success) return m.Groups[1].Value;

        // 2. "... from mod examplemod" (typical mixin stack trace annotation)
        m = Regex.Match(content, @"from mod ([a-zA-Z0-9_\-]+)");
        if (m.Success) return m.Groups[1].Value;

        // 3. "in config [examplemod.mixins.json]" or "config examplemod.mixins.json"
        m = Regex.Match(content, @"([a-zA-Z0-9_\-\.]+\.mixins?\.json)");
        if (m.Success) return m.Groups[1].Value;

        // 4. "Error loading class: ... (java.lang.ClassNotFoundException)" preceded by mixin transformer — try mixin package name
        m = Regex.Match(content, @"Critical injection failure.*?([a-zA-Z0-9_\-]+)\.mixins", RegexOptions.Singleline);
        if (m.Success) return m.Groups[1].Value;

        return null;
    }

    // Disables the mod jar that contains the given mixin config file (e.g. "examplemod.mixins.json").
    private string? DisableModByMixinConfig(string mixinConfigName)
    {
        if (!mixinConfigName.EndsWith(".json")) mixinConfigName += ".mixins.json";

        string modsPath = Path.Combine(settings.InstancePath, "mods");
        if (!Directory.Exists(modsPath)) return null;

        foreach (var file in Directory.GetFiles(modsPath, "*.jar"))
        {
            try
            {
                using var archive = ZipFile.OpenRead(file);
                if (archive.GetEntry(mixinConfigName) != null)
                {
                    archive.Dispose();
                    File.Move(file, file + ".disabled");
                    LoadModsList();
                    return Path.GetFileName(file);
                }
            }
            catch { }
        }
        return null;
    }

    private string? DisableModById(string targetModId)
    {
        string modsPath = Path.Combine(settings.InstancePath, "mods");
        if (!Directory.Exists(modsPath)) return null;

        var jarFiles = Directory.GetFiles(modsPath, "*.jar");
        foreach (var file in jarFiles)
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
                    var match = Regex.Match(json, @"""id""\s*:\s*""([^""]+)""");
                    if (match.Success && match.Groups[1].Value == targetModId)
                    {
                        archive.Dispose();
                        string dest = file + ".disabled";
                        File.Move(file, dest);
                        LoadModsList();
                        return Path.GetFileName(file);
                    }
                }
            }
            catch { }
        }
        return null;
    }

    private async void ShowCrashDialog(string crashInfo, string? crashFile)
    {
        var window = new Window
        {
            Title = "Game Crashed",
            Width = 550, Height = 300,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Background = new SolidColorBrush(Color.Parse("#0A0A0F")),
            CanResize = false
        };

        var panel = new StackPanel { Margin = new Thickness(24), Spacing = 16 };

        panel.Children.Add(new TextBlock
        {
            Text = "💥 Game Crashed",
            Foreground = new SolidColorBrush(Color.Parse("#FF4444")),
            FontSize = 22,
            FontWeight = FontWeight.Bold
        });

        panel.Children.Add(new TextBox
        {
            Text = crashInfo,
            IsReadOnly = true,
            TextWrapping = TextWrapping.Wrap,
            MaxHeight = 120,
            Background = new SolidColorBrush(Color.Parse("#111118")),
            Foreground = new SolidColorBrush(Color.Parse("#CCCCCC")),
            FontSize = 13,
            BorderBrush = new SolidColorBrush(Color.Parse("#222233"))
        });

        var buttonRow = new StackPanel { Orientation = Avalonia.Layout.Orientation.Horizontal, Spacing = 8 };

        var relaunchBtn = new Button
        {
            Content = "Relaunch",
            Background = new SolidColorBrush(Color.Parse("#8B0000")),
            Foreground = Brushes.White,
            FontWeight = FontWeight.Bold,
            Height = 38, Width = 120,
            HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            CornerRadius = new CornerRadius(8)
        };
        relaunchBtn.Click += async (s, e) => { window.Close(); await LaunchGame(); };
        buttonRow.Children.Add(relaunchBtn);

        if (crashFile != null)
        {
            var openBtn = new Button
            {
                Content = "Open Report",
                Background = new SolidColorBrush(Color.Parse("#1A1A2E")),
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
            Background = new SolidColorBrush(Color.Parse("#1A1A2E")),
            Foreground = new SolidColorBrush(Color.Parse("#AAAAAA")),
            Height = 38, Width = 120,
            HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            CornerRadius = new CornerRadius(8)
        };
        modsBtn.Click += (s, e) => { window.Close(); NavigateTo("Mods"); };
        buttonRow.Children.Add(modsBtn);

        panel.Children.Add(buttonRow);
        window.Content = panel;
        window.ShowDialog(this);
    }

    // ═══════════════════════════════════════
    //  PACKWIZ
    // ═══════════════════════════════════════

    private async Task RunPackwizInstaller()
    {
        if (Environment.GetCommandLineArgs().Contains("--auto-launch-offline"))
        {
            Log("[Packwiz] Skipped: Offline launch requested.");
            return;
        }

        try
        {
            Log("[Packwiz] Syncing mods from remote repository...");
            var process = new Process();
            process.StartInfo.FileName = settings.JavaPath;
            string packUrl = string.IsNullOrWhiteSpace(settings.PackwizUrl) 
                ? $"file:///{settings.PackwizPath.Replace("\\", "/")}/pack.toml".Replace(" ", "%20")
                : settings.PackwizUrl;
                
            string bootstrapJar = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "packwiz-installer-bootstrap.jar");

            if (!File.Exists(bootstrapJar))
                bootstrapJar = @"C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher\packwiz-installer-bootstrap.jar";

            process.StartInfo.Arguments = $"-jar \"{bootstrapJar}\" --no-gui \"{packUrl}\"";
            process.StartInfo.WorkingDirectory = settings.InstancePath;
            process.StartInfo.UseShellExecute = false;
            process.StartInfo.CreateNoWindow = true;
            process.StartInfo.RedirectStandardOutput = false;
            process.StartInfo.RedirectStandardError = false;

            process.Start();
            await process.WaitForExitAsync();

            if (process.ExitCode != 0)
            {
                Log($"[Packwiz ERROR] Mod sync finished with exit code {process.ExitCode}");
            }
            else
            {
                Log("[Packwiz] Mod sync completed successfully.");
            }
        }
        catch (Exception ex)
        {
            Log($"[Packwiz ERROR] {ex.Message}");
        }
    }

    // ═══════════════════════════════════════
    //  SYSTEM TRAY
    // ═══════════════════════════════════════

    private void SetupTrayIcon()
    {
        _trayIcon = new TrayIcon
        {
            Icon = new WindowIcon(@"C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher\Assets\icon.ico"),
            ToolTipText = "The Lads Client",
            IsVisible = true
        };

        _trayIcon.Clicked += (s, e) => { this.Show(); this.WindowState = WindowState.Normal; this.Activate(); };

        var showItem = new NativeMenuItem("Show Launcher");
        showItem.Click += (s, e) => { this.Show(); this.WindowState = WindowState.Normal; this.Activate(); };

        var exitItem = new NativeMenuItem("Exit");
        exitItem.Click += (s, e) => Environment.Exit(0);

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
        if (!settings.ShowParticles) return;

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
        var commonVersions = new[] { "26.1.2", "26.1.1", "26.1", "1.21.1", "1.21", "1.20.6", "1.20.4", "1.20.1", "1.19.2", "1.18.2", "1.16.5" };
        
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

    private async Task TriggerDefaultSearchesAsync()
    {
        try
        {
            string mcVersion = string.IsNullOrEmpty(settings.SelectedMinecraftVersionOverride)
                ? ResolveMinecraftVersion()
                : settings.SelectedMinecraftVersionOverride;

            Log($"[Search] TriggerDefaultSearchesAsync started. MC Version: '{mcVersion}'");

            Log($"[Search] Querying Modrinth at: '{settings.ModrinthApiUrl}'");

            // Populate the browse tabs with popular mods / resource packs by default,
            // so the page isn't blank before the user types a query (Prism-style).
            var modResults = await SearchModrinthAsync("", mcVersion, isResourcePack: false);
            if (!string.IsNullOrWhiteSpace(settings.CurseForgeApiKey))
            {
                var cfMods = await SearchCurseForgeAsync("", mcVersion, isResourcePack: false);
                modResults.AddRange(cfMods);
            }
            RenderSearchResults(modResults, mcVersion, isResourcePack: false, BrowseModsList);

            var rpResults = await SearchModrinthAsync("", mcVersion, isResourcePack: true);
            if (!string.IsNullOrWhiteSpace(settings.CurseForgeApiKey))
            {
                var cfRps = await SearchCurseForgeAsync("", mcVersion, isResourcePack: true);
                rpResults.AddRange(cfRps);
            }
            RenderSearchResults(rpResults, mcVersion, isResourcePack: true, BrowseRpList);

            Log($"[Search] Default browse loaded: {modResults.Count} mods, {rpResults.Count} packs");
        }
        catch (Exception ex)
        {
            Log($"[Search Error] TriggerDefaultSearchesAsync failed: {ex.Message}");
        }
    }

    private void SaveModSettings_Click(object? sender, RoutedEventArgs e)
    {
        settings.ModrinthApiUrl = ModrinthApiUrlBox_ModTab.Text ?? settings.ModrinthApiUrl;
        settings.CurseForgeApiUrl = CurseForgeApiUrlBox_ModTab.Text ?? settings.CurseForgeApiUrl;
        settings.CurseForgeApiKey = CfApiKeyInputBox.Text ?? settings.CurseForgeApiKey;
        settings.Save();
        
        // Sync UI elements
        ModrinthApiUrlBox.Text = settings.ModrinthApiUrl;
        CurseForgeApiUrlBox.Text = settings.CurseForgeApiUrl;
        CurseForgeApiKeyBox.Text = settings.CurseForgeApiKey;
        
        StatusText.Text = "Mod settings saved!";
        Log("[Settings] Mod configurations saved and synced.");
    }

    private async Task<List<ModSearchItem>> SearchModrinthAsync(string query, string mcVersion, bool isResourcePack)
    {
        var list = new List<ModSearchItem>();
        try
        {
            // Modrinth facets: filter by project_type (not a "mods" category, which
            // doesn't exist) and by loader.
            string projectType = isResourcePack ? "resourcepack" : "mod";
            var facets = new List<string[]>();
            facets.Add(new[] { $"project_type:{projectType}" });
            if (!isResourcePack) facets.Add(new[] { "categories:fabric" });
            if (!string.IsNullOrEmpty(mcVersion)) facets.Add(new[] { $"versions:{mcVersion}" });

            string facetsJson = JsonSerializer.Serialize(facets);
            string baseUrl = string.IsNullOrWhiteSpace(settings.ModrinthApiUrl) ? "https://api.modrinth.com/v2" : settings.ModrinthApiUrl.TrimEnd('/');
            if (!baseUrl.EndsWith("/v2") && !baseUrl.Contains("/v2/")) baseUrl += "/v2";
            // Empty query → show popular projects (sorted by downloads), like Prism's browse.
            string index = string.IsNullOrWhiteSpace(query) ? "downloads" : "relevance";
            string url = $"{baseUrl}/search?query={Uri.EscapeDataString(query)}&facets={Uri.EscapeDataString(facetsJson)}&index={index}&limit=30";

            var request = new HttpRequestMessage(HttpMethod.Get, url);
            request.Headers.Add("User-Agent", "TheLadsLauncher/1.0.0 (contact@thelads.com)");

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                string content = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(content);
                if (doc.RootElement.TryGetProperty("hits", out var hits))
                {
                    foreach (var hit in hits.EnumerateArray())
                    {
                        list.Add(new ModSearchItem
                        {
                            Id = hit.TryGetProperty("project_id", out var idProp) ? idProp.GetString() ?? "" : "",
                            Name = hit.TryGetProperty("title", out var titleProp) ? titleProp.GetString() ?? "" : "",
                            Summary = hit.TryGetProperty("description", out var descProp) ? descProp.GetString() ?? "" : "",
                            IconUrl = hit.TryGetProperty("icon_url", out var i) ? i.GetString() ?? "" : "",
                            Author = hit.TryGetProperty("author", out var a) ? a.GetString() ?? "" : "Unknown",
                            DownloadCount = hit.TryGetProperty("downloads", out var d) ? d.GetInt64() : 0,
                            Provider = "Modrinth",
                            ProjectSlug = hit.TryGetProperty("slug", out var slugProp) ? slugProp.GetString() ?? "" : "",
                            Categories = hit.TryGetProperty("categories", out var catProp) && catProp.ValueKind == JsonValueKind.Array
                                ? catProp.EnumerateArray().Select(v => v.GetString() ?? "").Where(s => !string.IsNullOrEmpty(s)).ToList()
                                : new List<string>(),
                            Version = hit.TryGetProperty("latest_version", out var verProp) ? verProp.GetString() ?? "" : "",
                        });
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Log($"[Modrinth Search Error] {ex.Message}");
        }
        return list;
    }

    private async Task<ModFileVersion?> GetLatestModrinthVersionAsync(string projectId, string mcVersion, bool isResourcePack)
    {
        try
        {
            string loaderFacet = isResourcePack ? "" : "&loaders=[\"fabric\"]";
            string baseUrl = string.IsNullOrWhiteSpace(settings.ModrinthApiUrl) ? "https://api.modrinth.com/v2" : settings.ModrinthApiUrl.TrimEnd('/');
            if (!baseUrl.EndsWith("/v2") && !baseUrl.Contains("/v2/")) baseUrl += "/v2";
            string url = $"{baseUrl}/project/{projectId}/version?game_versions=[\"{mcVersion}\"]" + loaderFacet;
            
            var request = new HttpRequestMessage(HttpMethod.Get, url);
            request.Headers.Add("User-Agent", "TheLadsLauncher/1.0.0 (contact@thelads.com)");

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                string content = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(content);
                if (doc.RootElement.ValueKind == JsonValueKind.Array && doc.RootElement.GetArrayLength() > 0)
                {
                    var latestVer = doc.RootElement[0];
                    if (latestVer.TryGetProperty("files", out var files) && files.ValueKind == JsonValueKind.Array && files.GetArrayLength() > 0)
                    {
                        var file = files.EnumerateArray().FirstOrDefault(f => f.TryGetProperty("primary", out var p) && p.ValueKind == JsonValueKind.True);
                        if (file.ValueKind == JsonValueKind.Undefined) file = files[0];

                        return new ModFileVersion
                        {
                            Id = latestVer.TryGetProperty("id", out var idProp) ? idProp.GetString() ?? "" : "",
                            Name = latestVer.TryGetProperty("name", out var nameProp) ? nameProp.GetString() ?? "" : "",
                            FileName = file.TryGetProperty("filename", out var fnProp) ? fnProp.GetString() ?? "" : "",
                            DownloadUrl = file.TryGetProperty("url", out var urlProp) ? urlProp.GetString() ?? "" : "",
                            Size = file.TryGetProperty("size", out var szProp) ? szProp.GetInt64() : 0
                        };
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Log($"[Modrinth Version Error] {ex.Message}");
        }
        return null;
    }

    private async Task<List<ModSearchItem>> SearchCurseForgeAsync(string query, string mcVersion, bool isResourcePack)
    {
        var list = new List<ModSearchItem>();
        string apiKey = settings.CurseForgeApiKey;
        if (string.IsNullOrWhiteSpace(apiKey))
        {
            Log("[CurseForge Search] Cancelled: No API Key configured.");
            return list;
        }

        try
        {
            int classId = isResourcePack ? 12 : 6;
            string loaderParam = isResourcePack ? "" : "&modLoaderType=4";
            string versionParam = string.IsNullOrEmpty(mcVersion) ? "" : $"&gameVersion={Uri.EscapeDataString(mcVersion)}";
            string baseUrl = string.IsNullOrWhiteSpace(settings.CurseForgeApiUrl) ? "https://api.curseforge.com/v1" : settings.CurseForgeApiUrl.TrimEnd('/');
            if (!baseUrl.EndsWith("/v1") && !baseUrl.Contains("/v1/")) baseUrl += "/v1";
            string url = $"{baseUrl}/mods/search?gameId=432&classId={classId}&searchFilter={Uri.EscapeDataString(query)}{versionParam}{loaderParam}&pageSize=20";

            var request = new HttpRequestMessage(HttpMethod.Get, url);
            request.Headers.Add("x-api-key", apiKey);

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                string content = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(content);
                if (doc.RootElement.TryGetProperty("data", out var data))
                {
                    foreach (var mod in data.EnumerateArray())
                    {
                        string iconUrl = "";
                        if (mod.TryGetProperty("logo", out var logoProp) && logoProp.ValueKind == JsonValueKind.Object)
                        {
                            iconUrl = logoProp.TryGetProperty("thumbnailUrl", out var thumbProp) ? thumbProp.GetString() ?? "" : "";
                        }

                        list.Add(new ModSearchItem
                        {
                            Id = mod.TryGetProperty("id", out var idProp)
                                ? (idProp.ValueKind == JsonValueKind.Number ? idProp.GetInt32().ToString() : idProp.GetString() ?? "")
                                : "",
                            Name = mod.TryGetProperty("name", out var nameProp) ? nameProp.GetString() ?? "" : "",
                            Summary = mod.TryGetProperty("summary", out var summaryProp) ? summaryProp.GetString() ?? "" : "",
                            IconUrl = iconUrl,
                            Author = "CurseForge Creator",
                            DownloadCount = mod.TryGetProperty("downloadCount", out var dl) ? (long)dl.GetDouble() : 0,
                            Provider = "CurseForge",
                            Categories = mod.TryGetProperty("categories", out var catsProp) && catsProp.ValueKind == JsonValueKind.Array
                                ? catsProp.EnumerateArray().Select(c => c.TryGetProperty("name", out var n) ? n.GetString() ?? "" : "").Where(s => !string.IsNullOrEmpty(s)).ToList()
                                : new List<string>(),
                        });
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Log($"[CurseForge Search Error] {ex.Message}");
        }
        return list;
    }

    private async Task<ModFileVersion?> GetLatestCurseForgeVersionAsync(string modId, string mcVersion, bool isResourcePack)
    {
        string apiKey = settings.CurseForgeApiKey;
        if (string.IsNullOrWhiteSpace(apiKey)) return null;

        try
        {
            string loaderParam = isResourcePack ? "" : "&modLoaderType=4";
            string baseUrl = string.IsNullOrWhiteSpace(settings.CurseForgeApiUrl) ? "https://api.curseforge.com/v1" : settings.CurseForgeApiUrl.TrimEnd('/');
            if (!baseUrl.EndsWith("/v1") && !baseUrl.Contains("/v1/")) baseUrl += "/v1";
            string url = $"{baseUrl}/mods/{modId}/files?gameVersion={Uri.EscapeDataString(mcVersion)}{loaderParam}";

            var request = new HttpRequestMessage(HttpMethod.Get, url);
            request.Headers.Add("x-api-key", apiKey);

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                string content = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(content);
                if (doc.RootElement.TryGetProperty("data", out var data) && data.ValueKind == JsonValueKind.Array && data.GetArrayLength() > 0)
                {
                    var latestFile = data[0];
                    string dlUrl = latestFile.TryGetProperty("downloadUrl", out var dlProp) ? dlProp.GetString() ?? "" : "";
                    
                    if (string.IsNullOrEmpty(dlUrl))
                    {
                        int fileId = latestFile.TryGetProperty("id", out var idProp) ? idProp.GetInt32() : 0;
                        string fileName = latestFile.TryGetProperty("fileName", out var fnProp) ? fnProp.GetString() ?? "" : "";
                        string folder1 = (fileId / 1000).ToString();
                        string folder2 = (fileId % 1000).ToString("D3");
                        dlUrl = $"https://edge.forgecdn.net/files/{folder1}/{folder2}/{Uri.EscapeDataString(fileName)}";
                    }

                    return new ModFileVersion
                    {
                        Id = latestFile.TryGetProperty("id", out var idProp2)
                            ? (idProp2.ValueKind == JsonValueKind.Number ? idProp2.GetInt32().ToString() : idProp2.GetString() ?? "")
                            : "",
                        Name = latestFile.TryGetProperty("displayName", out var dnProp) ? dnProp.GetString() ?? "" : "",
                        FileName = latestFile.TryGetProperty("fileName", out var fnProp3) ? fnProp3.GetString() ?? "" : "",
                        DownloadUrl = dlUrl,
                        Size = latestFile.TryGetProperty("fileLength", out var flProp) ? flProp.GetInt64() : 0
                    };
                }
            }
        }
        catch (Exception ex)
        {
            Log($"[CurseForge Version Error] {ex.Message}");
        }
        return null;
    }

    private string GetInstalledVersion(string itemName, bool isResourcePack)
    {
        string targetFolder = Path.Combine(settings.InstancePath, isResourcePack ? "resourcepacks" : "mods");
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
                    catch {}
                }
            }
        }
        return "";
    }

    private bool CheckIfInstalled(string itemName, bool isResourcePack)
    {
        string targetFolder = Path.Combine(settings.InstancePath, isResourcePack ? "resourcepacks" : "mods");
        if (!Directory.Exists(targetFolder)) return false;

        string cleanName = Regex.Replace(itemName, @"\s+", "").ToLower();
        var files = Directory.GetFiles(targetFolder);
        foreach (var file in files)
        {
            string cleanFile = Path.GetFileNameWithoutExtension(file).Replace(".disabled", "").Replace(" ", "").ToLower();
            if (cleanFile.Contains(cleanName) || cleanName.Contains(cleanFile))
                return true;
        }
        return false;
    }

    private async Task<bool> DownloadModOrPackAsync(ModSearchItem item, string mcVersion, bool isResourcePack, IProgress<double> progress)
    {
        try
        {
            ModFileVersion? fileVersion = null;
            if (item.Provider == "Modrinth")
            {
                fileVersion = await GetLatestModrinthVersionAsync(item.Id, mcVersion, isResourcePack);
            }
            else if (item.Provider == "CurseForge")
            {
                fileVersion = await GetLatestCurseForgeVersionAsync(item.Id, mcVersion, isResourcePack);
            }

            if (fileVersion == null || string.IsNullOrEmpty(fileVersion.DownloadUrl))
            {
                Log($"[Installer] Could not find version file for {item.Name} on version {mcVersion}");
                return false;
            }

            string targetFolder = Path.Combine(settings.InstancePath, isResourcePack ? "resourcepacks" : "mods");
            Directory.CreateDirectory(targetFolder);

            string safeFileName = Path.GetFileName(fileVersion.FileName);
            if (string.IsNullOrEmpty(safeFileName))
            {
                safeFileName = fileVersion.Name + (isResourcePack ? ".zip" : ".jar");
            }
            string targetPath = Path.Combine(targetFolder, safeFileName);

            Log($"[Installer] Downloading {item.Name} to {targetPath} from {fileVersion.DownloadUrl}");

            using (var response = await _httpClient.GetAsync(fileVersion.DownloadUrl, HttpCompletionOption.ResponseHeadersRead))
            {
                response.EnsureSuccessStatusCode();
                long? totalBytes = response.Content.Headers.ContentLength;

                using (var contentStream = await response.Content.ReadAsStreamAsync())
                using (var fileStream = new FileStream(targetPath, FileMode.Create, FileAccess.Write, FileShare.None, 8192, true))
                {
                    var buffer = new byte[8192];
                    long totalRead = 0;
                    int read;
                    while ((read = await contentStream.ReadAsync(buffer, 0, buffer.Length)) > 0)
                    {
                        await fileStream.WriteAsync(buffer, 0, read);
                        totalRead += read;
                        if (totalBytes.HasValue)
                        {
                            double pct = (double)totalRead * 100 / totalBytes.Value;
                            progress?.Report(pct);
                        }
                    }
                }
            }

            Log($"[Installer] Successfully installed {item.Name}");
            return true;
        }
        catch (Exception ex)
        {
            Log($"[Installer Error] Failed to download {item.Name}: {ex.Message}");
            return false;
        }
    }

    private async void SearchMods_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            ModSearchBtn.IsEnabled = false;
            ModSearchBtn.Content = "🔍 Searching...";
            BrowseModsList.Children.Clear();

            string query = ModSearchBox.Text ?? "";
            string provider = (ModSearchProvider.SelectedItem as ComboBoxItem)?.Content as string ?? "Modrinth";
            string mcVersion = SearchModMcVersionDropdown.SelectedItem as string ?? ResolveMinecraftVersion();

            List<ModSearchItem> results = new();
            if (provider == "Modrinth")
            {
                results = await SearchModrinthAsync(query, mcVersion, isResourcePack: false);
            }
            else if (provider == "CurseForge")
            {
                results = await SearchCurseForgeAsync(query, mcVersion, isResourcePack: false);
            }

            RenderSearchResults(results, mcVersion, isResourcePack: false, BrowseModsList);
        }
        catch (Exception ex)
        {
            Log($"[Search Error] {ex.Message}");
        }
        finally
        {
            ModSearchBtn.IsEnabled = true;
            ModSearchBtn.Content = "🔍 Search";
        }
    }

    private async void SearchRp_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            RpSearchBtn.IsEnabled = false;
            RpSearchBtn.Content = "🔍 Searching...";
            BrowseRpList.Children.Clear();

            string query = RpSearchBox.Text ?? "";
            string provider = (RpSearchProvider.SelectedItem as ComboBoxItem)?.Content as string ?? "Modrinth";
            string mcVersion = SearchRpMcVersionDropdown.SelectedItem as string ?? ResolveMinecraftVersion();

            List<ModSearchItem> results = new();
            if (provider == "Modrinth")
            {
                results = await SearchModrinthAsync(query, mcVersion, isResourcePack: true);
            }
            else if (provider == "CurseForge")
            {
                results = await SearchCurseForgeAsync(query, mcVersion, isResourcePack: true);
            }

            RenderSearchResults(results, mcVersion, isResourcePack: true, BrowseRpList);
        }
        catch (Exception ex)
        {
            Log($"[Search Error] {ex.Message}");
        }
        finally
        {
            RpSearchBtn.IsEnabled = true;
            RpSearchBtn.Content = "🔍 Search";
        }
    }

    private static string FormatDownloadCount(long count)
    {
        if (count >= 1_000_000) return $"{count / 1_000_000.0:F1}M downloads";
        if (count >= 1_000) return $"{count / 1_000.0:F1}K downloads";
        return $"{count} downloads";
    }

    private void RenderSearchResults(List<ModSearchItem> results, string mcVersion, bool isResourcePack, StackPanel listPanel)
    {
        listPanel.Children.Clear();
        if (results == null || results.Count == 0)
        {
            listPanel.Children.Add(new TextBlock { Text = "No results found.", Foreground = new SolidColorBrush(Color.Parse("#888888")), HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Center, Margin = new Thickness(0, 20, 0, 0) });
            return;
        }

        var template = this.FindResource("ModListItemTemplate") as DataTemplate;
        if (template == null) return;

        foreach (var item in results)
        {
            var row = template.Build(item) as Border;
            if (row == null) continue;

            var grid = row.Child as Grid;
            if (grid == null) continue;

            // Retrieve components by layout hierarchy
            var iconFrame = grid.Children[0] as Border;
            var iconImage = iconFrame?.Child as Image;
            var detailsPanel = grid.Children[1] as StackPanel;
            var titleBlock = detailsPanel.Children[0] as TextBlock;
            var descBlock = detailsPanel.Children[1] as TextBlock;
            var metaPanel = detailsPanel.Children[2] as StackPanel;
            var downloadsBlock = metaPanel.Children[0] as TextBlock;
            var badgesPanel = metaPanel.Children[1] as StackPanel;
            var actionPanel = grid.Children[2] as StackPanel;

            // Set Title & Description
            titleBlock.Text = item.Name;
            descBlock.Text = item.Summary;

            // Set Downloads and Provider info
            string authorPart = string.IsNullOrEmpty(item.Author) || item.Author == "CurseForge Creator" ? "" : $"  ·  by {item.Author}";
            downloadsBlock.Text = $"⬇ {FormatDownloadCount(item.DownloadCount)}  ·  {item.Provider}{authorPart}";

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
                        Background = new SolidColorBrush(Color.Parse("#1A1A2E")),
                        CornerRadius = new CornerRadius(4),
                        Padding = new Thickness(6, 2),
                        Margin = new Thickness(0, 0, 4, 0),
                        Child = new TextBlock
                        {
                            Text = catName,
                            Foreground = new SolidColorBrush(Color.Parse("#FF4444")),
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
            var progressBar = new ProgressBar { Minimum = 0, Maximum = 100, Value = 0, Height = 6, Width = 80, IsVisible = false, Foreground = new SolidColorBrush(Color.Parse("#8B0000")) };
            
            actionPanel.Children.Clear();
            actionPanel.Children.Add(installBtn);
            actionPanel.Children.Add(progressBar);

            installBtn.Click += async (s, e) =>
            {
                installBtn.IsEnabled = false;
                installBtn.Content = needsUpdate ? "Updating..." : "Installing...";
                progressBar.IsVisible = true;

                // For update, delete the old file first
                if (needsUpdate)
                {
                    string targetFolder = Path.Combine(settings.InstancePath, isResourcePack ? "resourcepacks" : "mods");
                    string cleanName = Regex.Replace(item.Name, @"\s+", "").ToLower();
                    if (Directory.Exists(targetFolder))
                    {
                        var files = Directory.GetFiles(targetFolder);
                        foreach (var file in files)
                        {
                            string cleanFile = Path.GetFileNameWithoutExtension(file).Replace(".disabled", "").Replace(" ", "").ToLower();
                            if (cleanFile.Contains(cleanName) || cleanName.Contains(cleanFile))
                            {
                                try { File.Delete(file); } catch {}
                            }
                        }
                    }
                }

                var progress = new Progress<double>(val =>
                {
                    progressBar.Value = val;
                });

                bool success = await DownloadModOrPackAsync(item, mcVersion, isResourcePack, progress);
                if (success)
                {
                    installBtn.Content = "Installed";
                    installBtn.Classes.Remove("launch");
                    installBtn.Classes.Add("action");
                    installBtn.IsEnabled = false;
                    progressBar.IsVisible = false;
                    needsUpdate = false;
                    if (!isResourcePack)
                    {
                        LoadModsList();
                    }
                }
                else
                {
                    installBtn.IsEnabled = true;
                    installBtn.Content = needsUpdate ? "Update Failed" : "Failed";
                    progressBar.IsVisible = false;
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

public class ModFileVersion
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string FileName { get; set; } = "";
    public string DownloadUrl { get; set; } = "";
    public long Size { get; set; }
}
