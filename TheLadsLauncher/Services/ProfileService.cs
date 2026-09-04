using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public class ProfileService : IProfileService
{
    private static ProfileService? _instance;
    public static ProfileService Instance => _instance ??= new ProfileService(PathService.Instance);

    private readonly IPathService _pathService;
    private readonly string _profilesConfigPath;
    private readonly List<LauncherProfile> _profiles = new();
    private string _activeProfileId = "1.21.1";

    public ProfileService(IPathService pathService)
    {
        _pathService = pathService;
        _profilesConfigPath = Path.Combine(_pathService.BaseDirectory, "profiles.json");
        LoadProfiles();
    }

    public IReadOnlyList<LauncherProfile> GetProfiles()
    {
        lock (_profiles)
        {
            return _profiles.ToList();
        }
    }

    public LauncherProfile? GetProfile(string id)
    {
        lock (_profiles)
        {
            return _profiles.FirstOrDefault(p => string.Equals(p.Id, id, StringComparison.OrdinalIgnoreCase));
        }
    }

    public LauncherProfile GetActiveProfile()
    {
        lock (_profiles)
        {
            var profile = _profiles.FirstOrDefault(p => string.Equals(p.Id, _activeProfileId, StringComparison.OrdinalIgnoreCase));
            if (profile != null) return profile;

            if (_profiles.Count > 0)
            {
                _activeProfileId = _profiles[0].Id;
                return _profiles[0];
            }

            var defaultProfile = CreateDefaultProfiles().First();
            _profiles.Add(defaultProfile);
            _activeProfileId = defaultProfile.Id;
            SaveProfilesToDisk();
            return defaultProfile;
        }
    }

    public void SetActiveProfile(string id)
    {
        lock (_profiles)
        {
            var exists = _profiles.Any(p => string.Equals(p.Id, id, StringComparison.OrdinalIgnoreCase));
            if (exists)
            {
                _activeProfileId = id;
                SaveProfilesToDisk();
            }
        }
    }

    public LauncherProfile CreateProfile(string name, string mcVersion, int javaMajor, bool isIsolated = false, string? fabricVersion = null)
    {
        var profile = new LauncherProfile
        {
            Id = Guid.NewGuid().ToString("N"),
            Name = name,
            MinecraftVersion = mcVersion,
            JavaMajorVersion = javaMajor,
            IsIsolated = isIsolated,
            FabricVersion = fabricVersion
        };

        lock (_profiles)
        {
            _profiles.Add(profile);
            SaveProfilesToDisk();
        }

        return profile;
    }

    public void AddProfile(LauncherProfile profile)
    {
        SaveProfile(profile);
    }

    public void SaveProfile(LauncherProfile profile)
    {
        lock (_profiles)
        {
            var index = _profiles.FindIndex(p => string.Equals(p.Id, profile.Id, StringComparison.OrdinalIgnoreCase));
            if (index >= 0)
            {
                _profiles[index] = profile;
            }
            else
            {
                _profiles.Add(profile);
            }
            SaveProfilesToDisk();
        }
    }

    public void SaveProfiles()
    {
        lock (_profiles)
        {
            SaveProfilesToDisk();
        }
    }

    public bool DeleteProfile(string id)
    {
        lock (_profiles)
        {
            // Do not delete if only one profile remains
            if (_profiles.Count <= 1) return false;

            var profile = _profiles.FirstOrDefault(p => string.Equals(p.Id, id, StringComparison.OrdinalIgnoreCase));
            if (profile != null)
            {
                _profiles.Remove(profile);
                if (string.Equals(_activeProfileId, id, StringComparison.OrdinalIgnoreCase))
                {
                    _activeProfileId = _profiles[0].Id;
                }
                SaveProfilesToDisk();
                return true;
            }
            return false;
        }
    }

    public Task SyncSharedToProfileAsync(LauncherProfile profile)
    {
        return PrepareProfileEnvironmentAsync(profile);
    }

    public Task PrepareProfileEnvironmentAsync(LauncherProfile profile)
    {
        return Task.Run(() =>
        {
            var targetDir = _pathService.GetProfileDirectory(profile);
            Directory.CreateDirectory(targetDir);

            if (profile.IsIsolated)
            {
                // In isolated mode, no shared settings synchronization is performed
                return;
            }

            _pathService.EnsureDirectories();

            // 1. Sync options.txt (keybinds, video settings, etc.)
            SyncFileToInstance(_pathService.SharedOptionsFile, Path.Combine(targetDir, "options.txt"));

            // 2. Sync servers.dat (server list)
            SyncFileToInstance(_pathService.SharedServersFile, Path.Combine(targetDir, "servers.dat"));

            // 3. Sync lads_accounts.json
            var sharedAccounts = _pathService.SharedAccountsFile;
            var baseAccounts = _pathService.AccountsFile;
            var instanceAccounts = Path.Combine(targetDir, "lads_accounts.json");
            if (File.Exists(baseAccounts) && !File.Exists(sharedAccounts))
            {
                try { File.Copy(baseAccounts, sharedAccounts, true); } catch { }
            }
            SyncFileToInstance(sharedAccounts, instanceAccounts);

            // 4. Sync lads_profile.json
            var sharedProfile = _pathService.SharedProfileConfigFile;
            var baseProfile = _pathService.ProfileConfigFile;
            var instanceProfile = Path.Combine(targetDir, "lads_profile.json");
            if (File.Exists(baseProfile) && !File.Exists(sharedProfile))
            {
                try { File.Copy(baseProfile, sharedProfile, true); } catch { }
            }
            SyncFileToInstance(sharedProfile, instanceProfile);
        });
    }

    public Task SyncProfileToSharedAsync(LauncherProfile profile)
    {
        return Task.Run(() =>
        {
            if (profile.IsIsolated)
            {
                // In isolated mode, no shared synchronization is performed
                return;
            }

            var targetDir = _pathService.GetProfileDirectory(profile);
            if (!Directory.Exists(targetDir)) return;

            _pathService.EnsureDirectories();

            // 1. Sync options.txt back to shared
            SyncFileFromInstance(Path.Combine(targetDir, "options.txt"), _pathService.SharedOptionsFile);

            // 2. Sync servers.dat back to shared
            SyncFileFromInstance(Path.Combine(targetDir, "servers.dat"), _pathService.SharedServersFile);

            // 3. Sync lads_accounts.json back to shared and base
            var instanceAccounts = Path.Combine(targetDir, "lads_accounts.json");
            if (File.Exists(instanceAccounts))
            {
                SyncFileFromInstance(instanceAccounts, _pathService.SharedAccountsFile);
                SyncFileFromInstance(instanceAccounts, _pathService.AccountsFile);
            }

            // 4. Sync lads_profile.json back to shared and base
            var instanceProfile = Path.Combine(targetDir, "lads_profile.json");
            if (File.Exists(instanceProfile))
            {
                SyncFileFromInstance(instanceProfile, _pathService.SharedProfileConfigFile);
                SyncFileFromInstance(instanceProfile, _pathService.ProfileConfigFile);
            }
        });
    }

    private static void SyncFileToInstance(string sharedFile, string instanceFile)
    {
        try
        {
            if (!File.Exists(sharedFile))
            {
                if (File.Exists(instanceFile))
                {
                    File.Copy(instanceFile, sharedFile, true);
                }
                return;
            }

            if (!File.Exists(instanceFile))
            {
                File.Copy(sharedFile, instanceFile, true);
                return;
            }

            var sharedTime = File.GetLastWriteTimeUtc(sharedFile);
            var instanceTime = File.GetLastWriteTimeUtc(instanceFile);
            if (sharedTime > instanceTime)
            {
                File.Copy(sharedFile, instanceFile, true);
            }
            else if (instanceTime > sharedTime)
            {
                File.Copy(instanceFile, sharedFile, true);
            }
        }
        catch { }
    }

    private static void SyncFileFromInstance(string instanceFile, string sharedFile)
    {
        try
        {
            if (!File.Exists(instanceFile)) return;

            if (!File.Exists(sharedFile))
            {
                File.Copy(instanceFile, sharedFile, true);
                return;
            }

            var instanceTime = File.GetLastWriteTimeUtc(instanceFile);
            var sharedTime = File.GetLastWriteTimeUtc(sharedFile);
            if (instanceTime >= sharedTime)
            {
                File.Copy(instanceFile, sharedFile, true);
            }
        }
        catch { }
    }

    private void LoadProfiles()
    {
        try
        {
            if (File.Exists(_profilesConfigPath))
            {
                var json = File.ReadAllText(_profilesConfigPath);
                var container = JsonSerializer.Deserialize<ProfilesData>(json);
                if (container != null && container.Profiles != null && container.Profiles.Count > 0)
                {
                    _profiles.Clear();
                    _profiles.AddRange(container.Profiles);
                    _activeProfileId = container.ActiveProfileId ?? _profiles[0].Id;
                    return;
                }
            }
        }
        catch { }

        // Populate default multi-version profiles
        _profiles.Clear();
        _profiles.AddRange(CreateDefaultProfiles());
        _activeProfileId = "1.21.1";
        SaveProfilesToDisk();
    }

    private static List<LauncherProfile> CreateDefaultProfiles()
    {
        return new List<LauncherProfile>
        {
            new()
            {
                Id = "1.21.1",
                Name = "The Lads Client 1.21.1 (Stable)",
                MinecraftVersion = "1.21.1",
                FabricVersion = "0.16.9",
                JavaMajorVersion = 21,
                IsIsolated = false,
                PackwizUrl = "https://raw.githubusercontent.com/ArashYT/TheLadsClient/main/Packwiz/1.21.1/pack.toml",
                IconKey = "stable"
            },
            new()
            {
                Id = "26.2",
                Name = "The Lads Client 26.2 (Next-Gen)",
                MinecraftVersion = "26.2",
                FabricVersion = "0.19.3",
                JavaMajorVersion = 25,
                IsIsolated = false,
                PackwizUrl = "https://raw.githubusercontent.com/ArashYT/TheLadsClient/main/Packwiz/26.2/pack.toml",
                IconKey = "nextgen"
            },
            new()
            {
                Id = "latest-release",
                Name = "Latest Release",
                MinecraftVersion = "latest.release",
                FabricVersion = null,
                JavaMajorVersion = 21,
                IsIsolated = false,
                IconKey = "vanilla"
            }
        };
    }

    private void SaveProfilesToDisk()
    {
        try
        {
            var data = new ProfilesData
            {
                ActiveProfileId = _activeProfileId,
                Profiles = _profiles.ToList()
            };
            var options = new JsonSerializerOptions { WriteIndented = true };
            var json = JsonSerializer.Serialize(data, options);
            var dir = Path.GetDirectoryName(_profilesConfigPath);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
            File.WriteAllText(_profilesConfigPath, json);
        }
        catch { }
    }

    private class ProfilesData
    {
        public string? ActiveProfileId { get; set; }
        public List<LauncherProfile>? Profiles { get; set; }
    }
}
