using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace TheLadsLauncher;

public class LauncherSettings
{
    private static string SettingsPath => Path.Combine(Services.PathService.Instance.BaseDirectory, "settings.json");
    // Public ID owned by The Lads Client. Registration does not imply Minecraft API approval.
    public const string DefaultMicrosoftClientId = "c8ca54dc-01e3-4bb3-824a-35e09bb3aa13";

    // Memory
    public int MaxRamMb { get; set; } = RecommendedRamMb(TotalMemoryBytes());
    /// <summary>Set once the user saves their own amount; until then MaxRamMb follows <see cref="RecommendedRamMb"/>.</summary>
    public bool RamChosenByUser { get; set; }
    public int MinRamMb { get; set; } = 512;

    // Java
    public string JavaPath { get; set; } = TheLadsLauncher.Services.PathService.Instance.GetJavaExecutablePath(25);
    public bool AutoDetectJava { get; set; } = true;

    // Paths
    public string InstancePath { get; set; } = TheLadsLauncher.Services.PathService.Instance.BaseDirectory;
    public string PackwizPath { get; set; } = Path.Combine(TheLadsLauncher.Services.PathService.Instance.BaseDirectory, "packwiz");
    public string PackwizUrl { get; set; } = "https://raw.githubusercontent.com/ArashYT/TheLadsClient/main/Packwiz/pack.toml";
    public string FabricVersion { get; set; } = "fabric-loader-0.19.3-26.2";

    // Appearance
    public string Theme { get; set; } = "DarkRed";
    public bool ShowParticles { get; set; } = true;
    public bool ReducedMotion { get; set; }
    /// <summary>Google Translate code of the launcher's language (Services.LauncherTranslator.Languages); "en" is untranslated.</summary>
    public string Language { get; set; } = "en";
    public string LoadingScreenBgColor { get; set; } = "";
    public string LoadingScreenBarColor { get; set; } = "";
    public string LoadingScreenGlowColor { get; set; } = "";

    // Behavior
    public bool CloseToTray { get; set; } = true;
    public bool AutoLaunch { get; set; } = false;
    public bool AutoFixCrashes { get; set; } = true;
    public bool AutoRelaunchOnCrash { get; set; } = false;
    /// <summary>Send a client profile's crash (report, hs_err, log tail) to the Lads team. Never modpacks or Experiences.</summary>
    public bool SendCrashReports { get; set; } = true;
    /// <summary>Send anonymous client version and heartbeat stats to the Lads team to see active player counts. Default true.</summary>
    public bool SendAnonymousUsageStats { get; set; } = true;
    /// <summary>Unique anonymous installation identifier generated on first run. Never tied to player names or Minecraft accounts.</summary>
    public string InstallId { get; set; } = "";
    /// <summary>Distinct client Minecraft versions used or played by this installation.</summary>
    public List<string> ClientVersionsUsed { get; set; } = new();
    public bool AutoRejoinServer { get; set; } = false;
    public bool ZeroSmpAutoJoin { get; set; } = true;    // Experiences tab: Zero SMP joins its server as soon as the game has loaded
    public string LastServerIp { get; set; } = "";
    public int LastServerPort { get; set; } = 25565;
    public bool MinimizeOnLaunch { get; set; } = true;
    public bool AllowMultiInstance { get; set; } = false;
    public bool KeepLauncherOpen { get; set; } = false;   // don't hide/close after launching the game
    public bool DiscordRichPresence { get; set; } = true;  // launcher activity on Discord; the game has its own DiscordRPC module
    public bool KeepClosedOnExit { get; set; } = false;   // don't re-open the launcher when the game closes
    public bool UseHorizon { get; set; } = false;     // experimental, at own risk: 26.3 Play starts Horizon (Dev Launcher or /experimental_invite only)
    public bool HorizonRiskAccepted { get; set; } = false; // the player pressed "I accept the risk" (UseHorizon needs it: LoadFrom)

    // Gallery
    public List<string> GalleryFavorites { get; set; } = new();
    public string ImgurClientId { get; set; } = "";
    public string UiScale { get; set; } = "100%";
    public System.Collections.Generic.List<string> OfflineAccounts { get; set; } = new();
    public System.Collections.Generic.List<string> AccountOrder { get; set; } = new();
    public string MainAccount { get; set; } = "";
    private string _microsoftClientId = DefaultMicrosoftClientId;
    public string MicrosoftClientId
    {
        get => _microsoftClientId;
        // Migrate older unset settings; retain explicit IDs for the existing validation paths.
        set => _microsoftClientId = string.IsNullOrWhiteSpace(value) ? DefaultMicrosoftClientId : value.Trim();
    }

    // Version
    public string LauncherVersion { get; set; } = "1.0.4";
    public string UpdateUrl { get; set; } = "";
    public string LastSeenReleaseNotesVersion { get; set; } = "";

    // API Keys
    public string CurseForgeApiKey { get; set; } = "";

    // API URL Configurations
    public string ModrinthApiUrl { get; set; } = "https://api.modrinth.com/v2";
    public string CurseForgeApiUrl { get; set; } = "https://api.curseforge.com/v1";

    // Version Override
    public string SelectedMinecraftVersionOverride { get; set; } = "";

    // Window
    public double WindowWidth { get; set; } = 1000;
    public double WindowHeight { get; set; } = 650;

    // Launch
    public string GraphicsRenderer { get; set; } = Services.GraphicsRenderer.Vulkan;
    /// <summary>1.8.9 renderer for resource packs that use OptiFine features (Services.OptiFinePacks): Auto, Ionium or OptiFine.</summary>
    public string OptiFinePackRenderer { get; set; } = Services.OptiFinePacks.Auto;
    public bool FullscreenOnLaunch { get; set; } = true;
    public bool QuickLaunch { get; set; } = true;   // skip asset verification if already installed
    public string QuickLaunchServerIp { get; set; } = "";  // server to join on launch ("" = auto from logs)

    // Sync
    // Retired in 1.2.3 and ignored: resource packs are shared through the global folder. Kept so older settings.json files load.
    public bool SyncResourcePacksFromGlobal { get; set; } = false;
    public bool SyncScreenshotsToGlobal { get; set; } = true;

    public static LauncherSettings Load()
    {
        try
        {
            string legacyPath = Path.Combine(AppContext.BaseDirectory, "settings.json");
            // The new installer uses a different application directory. Preserve settings
            // from the previous EXE-only installation on its first managed launch.
            if (!File.Exists(legacyPath))
                legacyPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "The Lads Client", "settings.json");
            if (!File.Exists(SettingsPath) && string.IsNullOrEmpty(Environment.GetEnvironmentVariable("THELADS_DIR"))
                && File.Exists(legacyPath)) File.Copy(legacyPath, SettingsPath);
        }
        catch { }
        return LoadFrom(SettingsPath);
    }

    /// <summary>
    /// A settings.json that exists but cannot be read must never turn into defaults: the start-up auto-save writes them over
    /// every setting (1.9.18: "Allow launching multiple copies" and the rest reset after a restart). A locked file (another
    /// process mid-write) is read again; an unreadable one falls back to the copy <see cref="SaveTo"/> keeps.
    /// </summary>
    public static LauncherSettings LoadFrom(string settingsPath)
    {
        foreach (string path in new[] { settingsPath, settingsPath + ".bak" })
        {
            for (int attempt = 0; attempt < 5 && File.Exists(path); attempt++)
            {
                try
                {
                    var loaded = JsonSerializer.Deserialize<LauncherSettings>(File.ReadAllText(path)) ?? throw new JsonException("empty");
                    // Before 1.4.5 everyone was saved with a flat 4 GB; keep only an amount the user picked.
                    if (!loaded.RamChosenByUser) loaded.MaxRamMb = RecommendedRamMb(TotalMemoryBytes());
                    // Before 1.12.0 Horizon could be saved on while its red risk question was still open (or never answered): without a
                    // recorded acceptance it starts off again, so the question comes back the next time it is ticked.
                    if (loaded.UseHorizon && !loaded.HorizonRiskAccepted) loaded.UseHorizon = false;
                    return loaded;
                }
                catch (IOException) { System.Threading.Thread.Sleep(100); }
                catch (Exception) { break; }
            }
        }
        return new LauncherSettings();
    }

    public LauncherSettings Clone() => (LauncherSettings)MemberwiseClone();

    public void Save() => SaveTo(SettingsPath);

    public void SaveTo(string settingsPath)
    {
        try
        {
            var options = new JsonSerializerOptions { WriteIndented = true };
            string json = JsonSerializer.Serialize(this, options);
            Directory.CreateDirectory(Path.GetDirectoryName(settingsPath)!);
            string temp = settingsPath + "." + Guid.NewGuid().ToString("N") + ".tmp";
            try
            {
                File.WriteAllText(temp, json);
                try { if (File.Exists(settingsPath)) File.Copy(settingsPath, settingsPath + ".bak", true); }
                catch (IOException) { } // only the fallback copy goes stale
                File.Move(temp, settingsPath, true);
            }
            finally { if (File.Exists(temp)) File.Delete(temp); }
        }
        catch { }
    }

    /// <summary>Physical memory (0 when it cannot be read).</summary>
    public static long TotalMemoryBytes()
    {
        try { return GC.GetGCMemoryInfo().TotalAvailableMemoryBytes; } catch { return 0; }
    }

    /// <summary>Game RAM for a PC with this much memory: half of it up to 8 GB (6 GB → 3, 8 → 4), then 1 GB more per
    /// 4 GB (12 → 5, 16 → 6), between 2 and 8 GB.</summary>
    public static int RecommendedRamMb(long totalBytes)
    {
        int gb = (int)Math.Round(totalBytes / (1024.0 * 1024 * 1024));
        if (gb <= 0) return 4096;
        int pick = gb <= 8 ? gb / 2 : 4 + (gb - 8) / 4;
        return Math.Clamp(pick, 2, 8) * 1024;
    }

    public static string[] GetAvailableThemes() => new[]
    {
        "DarkRed", "DarkBlue", "DarkPurple", "Midnight", "Halloween"
    };

    public (string Primary, string PrimaryLight, string PrimaryDark, string Accent) GetThemeColors()
    {
        return Theme switch
        {
            "DarkBlue" => ("#1A3A5C", "#2563EB", "#0F2440", "#3B82F6"),
            "DarkPurple" => ("#4C1D95", "#7C3AED", "#2E1065", "#8B5CF6"),
            "Midnight" => ("#1E1E2E", "#45475A", "#11111B", "#CDD6F4"),
            "Halloween" => ("#3B1458", "#6B2FA0", "#1E0A2E", "#FF8A1F"), // purple nebulae, pumpkin-orange particles
            _ => ("#8B0000", "#B00000", "#600000", "#FF4444") // DarkRed default
        };
    }

    public string GetOrCreateInstallId()
    {
        if (string.IsNullOrWhiteSpace(InstallId) || !Guid.TryParse(InstallId, out _))
        {
            InstallId = Guid.NewGuid().ToString("D");
            Save();
        }
        return InstallId;
    }

    public string[] DetectJavaInstallations()
    {
        var found = TheLadsLauncher.Services.JavaService.Instance.ScanAllSystemJavas();
        return System.Linq.Enumerable.ToArray(found);
    }
}
