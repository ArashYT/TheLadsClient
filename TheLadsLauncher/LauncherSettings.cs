using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace TheLadsLauncher;

public class LauncherSettings
{
    private static readonly string SettingsPath = Path.Combine(
        AppDomain.CurrentDomain.BaseDirectory, "settings.json");

    // Memory
    public int MaxRamMb { get; set; } = 4096;
    public int MinRamMb { get; set; } = 512;

    // Java
    public string JavaPath { get; set; } = @"C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\java.exe";
    public bool AutoDetectJava { get; set; } = true;

    // Paths
    public string InstancePath { get; set; } = @"C:\The Lads Client";
    public string PackwizPath { get; set; } = @"C:\Users\Arash\Desktop\Lads Client\Packwiz";
    public string PackwizUrl { get; set; } = "https://raw.githubusercontent.com/ArashYT/TheLadsClient/main/Packwiz/pack.toml";
    public string FabricVersion { get; set; } = "fabric-loader-0.19.3-26.2";

    // Appearance
    public string Theme { get; set; } = "DarkRed";
    public bool ShowParticles { get; set; } = true;

    // Behavior
    public bool CloseToTray { get; set; } = true;
    public bool AutoLaunch { get; set; } = false;
    public bool AutoFixCrashes { get; set; } = true;
    public bool AutoRelaunchOnCrash { get; set; } = false;
    public bool AutoRejoinServer { get; set; } = false;
    public string LastServerIp { get; set; } = "";
    public int LastServerPort { get; set; } = 25565;
    public bool MinimizeOnLaunch { get; set; } = true;
    public bool AllowMultiInstance { get; set; } = false;
    public bool KeepLauncherOpen { get; set; } = false;   // don't hide/close after launching the game
    public bool KeepClosedOnExit { get; set; } = false;   // don't re-open the launcher when the game closes

    // Gallery
    public List<string> GalleryFavorites { get; set; } = new();
    public string ImgurClientId { get; set; } = "";
    public string UiScale { get; set; } = "100%";
    public System.Collections.Generic.List<string> OfflineAccounts { get; set; } = new();
    public System.Collections.Generic.List<string> AccountOrder { get; set; } = new();

    // Version
    public string LauncherVersion { get; set; } = "1.0.4";
    public string UpdateUrl { get; set; } = "";

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
    public bool FullscreenOnLaunch { get; set; } = true;
    public bool QuickLaunch { get; set; } = false;   // skip asset verification if already installed
    public string QuickLaunchServerIp { get; set; } = "";  // server to join on launch ("" = auto from logs)

    // Sync
    public bool SyncResourcePacksFromGlobal { get; set; } = false;
    public bool SyncScreenshotsToGlobal { get; set; } = true;

    public static LauncherSettings Load()
    {
        try
        {
            if (File.Exists(SettingsPath))
            {
                string json = File.ReadAllText(SettingsPath);
                return JsonSerializer.Deserialize<LauncherSettings>(json) ?? new LauncherSettings();
            }
        }
        catch { }
        return new LauncherSettings();
    }

    public void Save()
    {
        try
        {
            var options = new JsonSerializerOptions { WriteIndented = true };
            string json = JsonSerializer.Serialize(this, options);
            File.WriteAllText(SettingsPath, json);
        }
        catch { }
    }

    public static string[] GetAvailableThemes() => new[]
    {
        "DarkRed", "DarkBlue", "DarkPurple", "Midnight"
    };

    public (string Primary, string PrimaryLight, string PrimaryDark, string Accent) GetThemeColors()
    {
        return Theme switch
        {
            "DarkBlue" => ("#1A3A5C", "#2563EB", "#0F2440", "#3B82F6"),
            "DarkPurple" => ("#4C1D95", "#7C3AED", "#2E1065", "#8B5CF6"),
            "Midnight" => ("#1E1E2E", "#45475A", "#11111B", "#CDD6F4"),
            _ => ("#8B0000", "#B00000", "#600000", "#FF4444") // DarkRed default
        };
    }

    public string[] DetectJavaInstallations()
    {
        var paths = new System.Collections.Generic.List<string>();
        string[] searchDirs = {
            @"C:\Program Files\Java",
            @"C:\Program Files\Eclipse Adoptium",
            @"C:\Program Files\Microsoft",
            @"C:\Program Files\AdoptOpenJDK",
            @"C:\Program Files\Zulu"
        };

        foreach (var dir in searchDirs)
        {
            if (Directory.Exists(dir))
            {
                foreach (var subDir in Directory.GetDirectories(dir))
                {
                    string javaExe = Path.Combine(subDir, "bin", "java.exe");
                    if (File.Exists(javaExe))
                    {
                        paths.Add(javaExe);
                    }
                }
            }
        }
        return paths.ToArray();
    }
}
