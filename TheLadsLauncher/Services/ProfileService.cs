using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public class ProfileService : IProfileService
{
    private static ProfileService? _instance;
    public static ProfileService Instance => _instance ??= new ProfileService(PathService.Instance, SharedContentService.Instance);

    private readonly IPathService _pathService;
    private readonly SharedContentService _sharedContent;
    private readonly string _profilesConfigPath;
    private readonly List<LauncherProfile> _profiles = new();
    private string _activeProfileId = "26.3";

    // There is deliberately no constructor without the shared-content root: tests must pass a sandbox root.
    public ProfileService(IPathService pathService, SharedContentService sharedContent)
    {
        _pathService = pathService;
        _sharedContent = sharedContent;
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

    public Task PrepareProfileEnvironmentAsync(LauncherProfile profile) => PrepareProfileEnvironmentAsync(profile, null);

    /// <summary>
    /// Every profile, isolated or not, gets shared worlds, resource packs, shader packs and server list. IsIsolated only keeps
    /// the profile's own game settings (options.txt, keybinds) instead of syncing them through the launcher's shared folder.
    /// Minecraft 1.8.9 (<see cref="GameVersionPolicy.KeepsOwnWorlds"/>) is the exception: it shares only the server list and
    /// screenshots, and always keeps its own worlds, packs and options.txt.
    /// </summary>
    /// <param name="withoutSharedFolders">Only after the user chose to launch without shared worlds/packs this time
    /// (see <see cref="SharedContentUnavailableException"/>); not saved.</param>
    public async Task<SharedContentReport> PrepareProfileEnvironmentAsync(LauncherProfile profile, IProgress<string>? progress,
        CancellationToken cancellationToken = default, bool withoutSharedFolders = false)
    {
        var targetDir = _pathService.GetProfileDirectory(profile);
        var ownWorlds = GameVersionPolicy.KeepsOwnWorlds(profile.MinecraftVersion);
        if (ownWorlds) RefuseSharedGameFolder(profile, targetDir);
        Directory.CreateDirectory(targetDir);
        var coreEnabled = UsesCore(profile, targetDir, out var stateFileError);
        var report = await _sharedContent.PrepareProfileAsync(targetDir, profile.Name, LegacySharedServersFile, coreEnabled,
            progress, cancellationToken, shareFolders: !withoutSharedFolders, keepOwnFolders: ownWorlds);
        var warnings = report.Warnings.ToList();
        if (stateFileError != null) warnings.Add(stateFileError);
        warnings.AddRange(await ModWelcomeSettings.PrepareAsync(targetDir, cancellationToken));
        try
        {
            // An in-game world picker must never offer a world across the 1.8.9 line: a newer world opened in 1.8.9 is
            // corrupted, and so is a 1.8.9 world that a newer version upgraded and 1.8.9 opens again.
            if (ownWorlds) File.Delete(Path.Combine(targetDir, WorldCatalogService.GameSourcesFile));
            else new WorldCatalogService(_pathService.BaseDirectory).WriteGameSources(targetDir, _sharedContent.Root,
                WorldCatalogService.ProfileSources(GetProfiles().Where(p => !GameVersionPolicy.KeepsOwnWorlds(p.MinecraftVersion)), _pathService));
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException)
        { warnings.Add("World folder list could not be written: " + e.Message); }

        try
        {
            var screenshots = new ScreenshotCatalogService(_sharedContent.Root,
                discoverLaunchers: !Environment.GetCommandLineArgs().Any(a => a.StartsWith("--preview-", StringComparison.Ordinal)));
            screenshots.WriteGameSources(targetDir, GetProfiles().Select(p => new ScreenshotRoot(_pathService.GetProfileDirectory(p), "Lads · " + p.Name)), cancellationToken);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException)
        { warnings.Add("Screenshot folder list could not be written: " + e.Message); }

        // 1.8.9 always keeps its own options.txt: its keys and values differ from the newer versions' shared copy.
        if (!profile.IsIsolated && !ownWorlds)
        {
            try
            {
                await Task.Run(() =>
                {
                    _pathService.EnsureDirectories();
                    // Game settings (options.txt, keybinds) follow the launcher's shared copy unless the profile keeps its own.
                    SyncFileToInstance(_pathService.SharedOptionsFile, Path.Combine(targetDir, "options.txt"));
                    // What the launcher last wrote into options.txt (its renderer choice) travels with it.
                    SyncFileToInstance(SharedRendererOptionsState, Path.Combine(targetDir, GraphicsRenderer.OptionsStateFile));
                }, cancellationToken);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                warnings.Add($"Game settings (options.txt) were not synced: {e.Message}");
            }
        }
        // Authentication snapshots belong to the auth gateway, never timestamp-based sync.
        return report with { Warnings = warnings };
    }

    /// <summary>Startup pass over every profile folder whose game is not running (see SharedContentService.PrepareAllAsync).</summary>
    public Task<IReadOnlyDictionary<string, SharedContentReport>> PrepareAllProfilesSharedContentAsync(IProgress<string>? progress = null,
        CancellationToken cancellationToken = default)
    {
        // 1.8.9 folders are never linked; each is separated and checked when it is prepared for a launch.
        var targets = GetProfiles().Where(p => !GameVersionPolicy.KeepsOwnWorlds(p.MinecraftVersion)).Select(p =>
        {
            var dir = _pathService.GetProfileDirectory(p);
            return (dir, p.Name, UsesCore(p, dir, out _));
        }).ToList();
        return _sharedContent.PrepareAllAsync(targets, LegacySharedServersFile, progress, cancellationToken);
    }

    /// <summary>Profiles whose game folder, links resolved, is not inside <paramref name="root"/>. The QA preview modes refuse to
    /// run while there are any: they migrate and link every profile folder, and a sandbox must never reach a real one.</summary>
    public IReadOnlyList<LauncherProfile> ProfilesOutside(string root)
    {
        var finalRoot = SafeFileOps.GetFinalPath(root);
        return GetProfiles().Where(p => !SafeFileOps.IsSameOrInside(SafeFileOps.GetFinalPath(_pathService.GetProfileDirectory(p)), finalRoot)).ToList();
    }

    /// <summary>After the game exits: options.txt back to the launcher's shared copy (unless isolated), then the fallback server-list reconcile.</summary>
    /// <param name="reconcileServerList">False when <see cref="GameSession.Attach"/> already reconciled the server list for this exit.</param>
    public async Task<SharedContentReport> SyncProfileToSharedAsync(LauncherProfile profile, bool reconcileServerList = true)
    {
        var targetDir = _pathService.GetProfileDirectory(profile);
        if (!Directory.Exists(targetDir)) return SharedContentReport.Empty;

        if (!profile.IsIsolated && !GameVersionPolicy.KeepsOwnWorlds(profile.MinecraftVersion))
        {
            await Task.Run(() =>
            {
                _pathService.EnsureDirectories();
                SyncFileFromInstance(Path.Combine(targetDir, "options.txt"), _pathService.SharedOptionsFile);
                SyncFileFromInstance(Path.Combine(targetDir, GraphicsRenderer.OptionsStateFile), SharedRendererOptionsState);
            });
        }
        // Never restore instance account/profile snapshots over newer logins or removals.
        return reconcileServerList ? await _sharedContent.ReconcileFallbackServersAsync(targetDir) : SharedContentReport.Empty;
    }

    /// <summary>A 1.8.9 profile must not share its game folder with a newer version's profile: that profile links or keeps newer
    /// worlds and packs in it. (The global folder itself is refused by SharedContentService.)</summary>
    private void RefuseSharedGameFolder(LauncherProfile profile, string gameDirectory)
    {
        var folder = SafeFileOps.GetFinalPath(gameDirectory);
        var other = GetProfiles().FirstOrDefault(p => !GameVersionPolicy.KeepsOwnWorlds(p.MinecraftVersion)
            && SafeFileOps.PathsEqual(SafeFileOps.GetFinalPath(_pathService.GetProfileDirectory(p)), folder));
        if (other != null)
            throw new InvalidOperationException($"'{profile.Name}' uses the same game folder as '{other.Name}' ('{gameDirectory}'). Minecraft {profile.MinecraftVersion} corrupts newer worlds, so it needs a game folder of its own. Nothing was changed.");
    }

    private string SharedRendererOptionsState => Path.Combine(Path.GetDirectoryName(_pathService.SharedOptionsFile)!, GraphicsRenderer.OptionsStateFile);

    // Kept only as the old launcher's list: it is merged into the shared servers.dat once, never written again.
    private string LegacySharedServersFile => Path.Combine(_pathService.SharedDirectory, "servers.dat");

    /// <summary>LadsCore (which reads the shared server list itself) runs only for bundled-Core Fabric versions it is enabled for.</summary>
    private static bool UsesCore(LauncherProfile profile, string gameDirectory, out string? stateFileError)
    {
        stateFileError = null;
        return GameVersionPolicy.RequiresBundledCore(profile.MinecraftVersion) && !string.IsNullOrWhiteSpace(profile.FabricVersion)
            && SharedContentService.IsCoreRequested(gameDirectory, out stateFileError);
    }

    private static void SyncFileToInstance(string sharedFile, string instanceFile)
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

    private static void SyncFileFromInstance(string instanceFile, string sharedFile)
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

    private void LoadProfiles()
    {
        try
        {
            if (File.Exists(_profilesConfigPath))
            {
                var json = File.ReadAllText(_profilesConfigPath);
                var container = JsonSerializer.Deserialize<ProfilesData>(json);
                if (container?.Profiles == null || container.Profiles.Any(p => p == null))
                    throw new InvalidDataException("The saved profile list is invalid.");
                if (container != null && container.Profiles != null && container.Profiles.Count > 0)
                {
                    _profiles.Clear();
                    _profiles.AddRange(container.Profiles);
                    _activeProfileId = container.ActiveProfileId ?? _profiles[0].Id;
                    var changed = false;
                    foreach (var savedProfile in _profiles)
                        changed |= MigrateSavedProfile(savedProfile);
                    foreach (var added in CreateDefaultProfiles())
                    {
                        if (_profiles.Any(p => p.MinecraftVersion == added.MinecraftVersion))
                            continue;
                        // A user may already use the default ID for a different profile/world.
                        if (_profiles.Any(p => string.Equals(p.Id, added.Id, StringComparison.OrdinalIgnoreCase)))
                            added.Id = added.MinecraftVersion + "-" + Guid.NewGuid().ToString("N");
                        _profiles.Add(added);
                        changed = true;
                    }
                    var active = _profiles.FirstOrDefault(p => string.Equals(p.Id, _activeProfileId, StringComparison.OrdinalIgnoreCase));
                    if (active == null || !IsValidStartupProfile(active))
                    {
                        // Keep unresolved aliases/custom profiles for the user to repair, but
                        // never pass one to startup's exact-version resolver as the active profile.
                        var fallback = _profiles.FirstOrDefault(p => GameVersionPolicy.RequiresBundledCore(p.MinecraftVersion)
                            && IsValidStartupProfile(p));
                        if (fallback == null)
                        {
                            fallback = CreateDefaultProfiles().First();
                            if (_profiles.Any(p => string.Equals(p.Id, fallback.Id, StringComparison.OrdinalIgnoreCase)))
                                fallback.Id += "-" + Guid.NewGuid().ToString("N");
                            _profiles.Add(fallback);
                        }
                        _activeProfileId = fallback.Id;
                        changed = true;
                    }
                    if (changed)
                        SaveProfilesToDisk();
                    return;
                }
            }
        }
        catch (Exception ex)
        {
            // Do not replace an unreadable user profile file with defaults.
            throw new InvalidDataException($"Could not load '{_profilesConfigPath}'. Existing profiles were left intact; repair or restore this file before continuing.", ex);
        }

        // Populate default multi-version profiles
        _profiles.Clear();
        _profiles.AddRange(CreateDefaultProfiles());
        _activeProfileId = "26.3";
        SaveProfilesToDisk();
    }

    private static bool MigrateSavedProfile(LauncherProfile profile)
    {
        var changed = false;
        if (profile.MinecraftVersion == "26.2" && profile.Name is "The Lads Client 26.2 (Next-Gen)" or "The Lads Client 26.2 (Primary)")
        {
            profile.Name = "The Lads Client 26.2";
            changed = true;
        }
        if (profile.MinecraftVersion == "1.21.11" && profile.Name == "The Lads Client 1.21.11 (Stable)")
        {
            profile.Name = "The Lads Client 1.21.11 (Legacy)";
            changed = true;
        }
        if (profile.MinecraftVersion is "latest.release" or "latest-release")
        {
            var oldVersion = profile.MinecraftVersion;
            profile.MinecraftVersion = "26.2";
            if (string.IsNullOrWhiteSpace(profile.FabricVersion))
                profile.FabricVersion = "0.19.5";
            else if (profile.FabricVersion.StartsWith("fabric-loader-", StringComparison.Ordinal)
                && profile.FabricVersion.EndsWith("-" + oldVersion, StringComparison.Ordinal))
                profile.FabricVersion = profile.FabricVersion.Substring(0, profile.FabricVersion.Length - oldVersion.Length) + "26.2";
            changed = true;
        }

        if (GameVersionPolicy.RequiresBundledCore(profile.MinecraftVersion) && !string.IsNullOrWhiteSpace(profile.FabricVersion))
        {
            var loader = profile.FabricVersion;
            var fullId = loader.StartsWith("fabric-loader-", StringComparison.Ordinal);
            var suffix = "-" + profile.MinecraftVersion;
            if (fullId && loader.EndsWith(suffix, StringComparison.Ordinal)
                && loader.Length > "fabric-loader-".Length + suffix.Length)
                loader = loader.Substring("fabric-loader-".Length, loader.Length - "fabric-loader-".Length - suffix.Length);
            // Parse only numeric releases; retain custom builds and mismatched full IDs.
            var minimumLoader = "0.19.5";
            if (Version.TryParse(loader, out var parsed) && parsed < Version.Parse(minimumLoader))
            {
                profile.FabricVersion = fullId ? "fabric-loader-" + minimumLoader + suffix : minimumLoader;
                changed = true;
            }
        }

        // These two URLs were generated by earlier defaults and verified to return 404.
        // Exact comparisons intentionally preserve custom hosts, paths and query strings.
        if (profile.PackwizUrl is
            "https://raw.githubusercontent.com/ArashYT/TheLadsClient/main/Packwiz/26.2/pack.toml" or
            "https://raw.githubusercontent.com/ArashYT/TheLadsClient/main/Packwiz/1.21.1/pack.toml")
        {
            profile.PackwizUrl = null;
            changed = true;
        }

        if (profile.MinecraftVersion is "1.21.1" or "1.21.11" or "26.2" or "26.3")
        {
            var requiredJava = GameVersionPolicy.GetRequiredJavaMajor(profile.MinecraftVersion);
            if (profile.JavaMajorVersion < requiredJava)
            {
                profile.JavaMajorVersion = requiredJava;
                changed = true;
            }
        }

        // 1.8.9 runs on Forge and exactly Java 8.
        if (GameVersionPolicy.UsesForge(profile.MinecraftVersion)
            && (profile.FabricVersion != null || profile.JavaMajorVersion != GameVersionPolicy.GetRequiredJavaMajor(profile.MinecraftVersion)))
        {
            profile.FabricVersion = null;
            profile.JavaMajorVersion = GameVersionPolicy.GetRequiredJavaMajor(profile.MinecraftVersion);
            changed = true;
        }
        return changed;
    }

    private static bool IsValidStartupProfile(LauncherProfile profile)
    {
        try
        {
            GameVersionPolicy.ResolveVersionId(profile);
            return !GameVersionPolicy.RequiresBundledCore(profile.MinecraftVersion)
                || !string.IsNullOrWhiteSpace(profile.FabricVersion);
        }
        catch (ArgumentException)
        {
            return false;
        }
    }

    private static List<LauncherProfile> CreateDefaultProfiles()
    {
        return new List<LauncherProfile>
        {
            new()
            {
                Id = "26.3", Name = "The Lads Client 26.3 (Primary)", MinecraftVersion = "26.3",
                FabricVersion = "0.19.5", JavaMajorVersion = 25, IsIsolated = false,
                PackwizUrl = null, IconKey = "stable"
            },
            new()
            {
                Id = "26.2",
                Name = "The Lads Client 26.2",
                MinecraftVersion = "26.2",
                FabricVersion = "0.19.5",
                JavaMajorVersion = 25,
                IsIsolated = false,
                PackwizUrl = null,
                IconKey = "stable"
            },
            new()
            {
                Id = "1.21.11",
                Name = "The Lads Client 1.21.11 (Legacy)",
                MinecraftVersion = "1.21.11",
                FabricVersion = "0.19.5",
                JavaMajorVersion = 21,
                IsIsolated = false,
                PackwizUrl = null,
                IconKey = "nextgen"
            },
            new()
            {
                Id = "1.21.1", Name = "The Lads Client 1.21.1 (Legacy)", MinecraftVersion = "1.21.1",
                FabricVersion = "0.19.5", JavaMajorVersion = 21, IsIsolated = false,
                PackwizUrl = null, IconKey = "nextgen"
            },
            // Forge, Java 8, and its own worlds, packs and settings (GameVersionPolicy.KeepsOwnWorlds).
            new()
            {
                Id = "1.8.9", Name = "The Lads Client 1.8.9", MinecraftVersion = "1.8.9",
                FabricVersion = null, JavaMajorVersion = 8, IsIsolated = true, PackwizUrl = null
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
