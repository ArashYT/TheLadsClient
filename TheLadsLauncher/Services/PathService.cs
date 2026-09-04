using System;
using System.IO;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public class PathService : IPathService
{
    private static PathService? _instance;
    public static PathService Instance => _instance ??= new PathService();

    private string _baseDirectory;

    public PathService(string? customBasePath = null)
    {
        _baseDirectory = !string.IsNullOrWhiteSpace(customBasePath)
            ? customBasePath
            : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".theladsclient");
        
        EnsureDirectories();
    }

    public string BaseDirectory => _baseDirectory;

    public void SetCustomBaseDirectory(string? customPath)
    {
        _baseDirectory = !string.IsNullOrWhiteSpace(customPath)
            ? customPath
            : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".theladsclient");
        EnsureDirectories();
    }

    public string SharedDirectory => Path.Combine(BaseDirectory, "shared");
    public string ProfilesDirectory => Path.Combine(BaseDirectory, "profiles");
    public string RuntimesDirectory => Path.Combine(BaseDirectory, "runtime");
    public string LogsDirectory => Path.Combine(BaseDirectory, "logs");
    public string BinDirectory => Path.Combine(BaseDirectory, "bin");
    public string PresetsDirectory => Path.Combine(BaseDirectory, "presets");

    public string AccountsFile => Path.Combine(BaseDirectory, "lads_accounts.json");
    public string ProfileConfigFile => Path.Combine(BaseDirectory, "lads_profile.json");
    public string SkinFile => Path.Combine(BaseDirectory, "skin.png");
    public string CapeFile => Path.Combine(BaseDirectory, "config", "cape.png");

    public string SharedOptionsFile => Path.Combine(SharedDirectory, "options.txt");
    public string SharedServersFile => Path.Combine(SharedDirectory, "servers.dat");
    public string SharedAccountsFile => Path.Combine(SharedDirectory, "lads_accounts.json");
    public string SharedProfileConfigFile => Path.Combine(SharedDirectory, "lads_profile.json");

    public string GetProfileDirectory(string profileId)
    {
        return Path.Combine(ProfilesDirectory, profileId);
    }

    public string GetProfileDirectory(LauncherProfile profile)
    {
        if (!string.IsNullOrWhiteSpace(profile.CustomGameDir))
            return profile.CustomGameDir;
        return GetProfileDirectory(profile.Id);
    }

    public string GetJavaRuntimeDirectory(int majorVersion)
    {
        return Path.Combine(RuntimesDirectory, $"java-{majorVersion}");
    }

    public string GetJavaExecutablePath(int majorVersion)
    {
        var runtimeDir = GetJavaRuntimeDirectory(majorVersion);
        var directExe = Path.Combine(runtimeDir, "bin", "java.exe");
        if (File.Exists(directExe)) return directExe;

        if (Directory.Exists(runtimeDir))
        {
            var subDirs = Directory.GetDirectories(runtimeDir);
            foreach (var sub in subDirs)
            {
                var subExe = Path.Combine(sub, "bin", "java.exe");
                if (File.Exists(subExe)) return subExe;
            }
        }

        return directExe;
    }

    public void EnsureDirectories()
    {
        try
        {
            Directory.CreateDirectory(BaseDirectory);
            Directory.CreateDirectory(SharedDirectory);
            Directory.CreateDirectory(ProfilesDirectory);
            Directory.CreateDirectory(RuntimesDirectory);
            Directory.CreateDirectory(LogsDirectory);
            Directory.CreateDirectory(BinDirectory);
            Directory.CreateDirectory(PresetsDirectory);
            var configDir = Path.Combine(BaseDirectory, "config");
            Directory.CreateDirectory(configDir);
        }
        catch { }
    }
}
