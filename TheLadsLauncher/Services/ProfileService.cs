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
    /// Minecraft 1.8.9 takes its game settings from Lunar Client's 1.8 profile instead when Lunar is installed (see SyncOptionsTo189).
    /// </summary>
    /// <param name="withoutSharedFolders">Only after the user chose to launch without shared worlds/packs this time
    /// (see <see cref="SharedContentUnavailableException"/>); not saved.</param>
    public async Task<SharedContentReport> PrepareProfileEnvironmentAsync(LauncherProfile profile, IProgress<string>? progress,
        CancellationToken cancellationToken = default, bool withoutSharedFolders = false)
    {
        var targetDir = _pathService.GetProfileDirectory(profile);
        var forge = GameVersionPolicy.UsesForge(profile.MinecraftVersion);
        if (forge) RefuseSharedGameFolder(profile, targetDir);
        Directory.CreateDirectory(targetDir);

        var coreEnabled = UsesCore(profile, targetDir, out var stateFileError);
        var report = await _sharedContent.PrepareProfileAsync(targetDir, profile.Name, LegacySharedServersFile, coreEnabled,
            progress, cancellationToken, shareFolders: !withoutSharedFolders);
        var warnings = report.Warnings.ToList();
        if (stateFileError != null) warnings.Add(stateFileError);
        warnings.AddRange(await ModWelcomeSettings.PrepareAsync(targetDir, cancellationToken));
        try
        {
            new WorldCatalogService(_pathService.BaseDirectory).WriteGameSources(targetDir, _sharedContent.Root,
                WorldCatalogService.ProfileSources(GetProfiles(), _pathService));
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

        // Shared settings and controls sync (options.txt and thelads_config.json)
        if (!profile.IsIsolated)
        {
            try
            {
                await Task.Run(() =>
                {
                    _pathService.EnsureDirectories();
                    if (forge)
                    {
                        // Only options.txt: 1.8.9 has no renderer choice, and its Core keeps its own thelads_config.json.
                        SyncOptionsTo189(targetDir, profile.MinecraftVersion);
                        return;
                    }
                    // Game settings (options.txt, keybinds) follow the launcher's shared copy with smart cross-version keybind translation
                    GameOptionsService.SyncToInstance(_pathService.SharedOptionsFile, Path.Combine(targetDir, "options.txt"), profile.MinecraftVersion);
                    // What the launcher last wrote into options.txt (its renderer choice) travels with it.
                    SyncFileToInstance(SharedRendererOptionsState, Path.Combine(targetDir, GraphicsRenderer.OptionsStateFile));
                    // HUD layouts, client configuration and modules sync
                    var sharedConfig = Path.Combine(_pathService.SharedDirectory, "thelads_config.json");
                    var targetConfig = Path.Combine(targetDir, "thelads_config.json");
                    if (File.Exists(sharedConfig)) SyncFileToInstance(sharedConfig, targetConfig);
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
        var targets = GetProfiles().Select(p =>
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

        if (!profile.IsIsolated && GameVersionPolicy.UsesForge(profile.MinecraftVersion))
        {
            // Settings taken from Lunar never go back: Lunar is only read.
            if (GameOptionsService.LunarOptions18(LunarRoot) == null)
                await Task.Run(() =>
                {
                    _pathService.EnsureDirectories();
                    GameOptionsService.SyncFromInstance(Path.Combine(targetDir, "options.txt"), _pathService.SharedOptionsFile, profile.MinecraftVersion);
                });
        }
        else if (!profile.IsIsolated)
        {
            await Task.Run(() =>
            {
                _pathService.EnsureDirectories();
                GameOptionsService.SyncFromInstance(Path.Combine(targetDir, "options.txt"), _pathService.SharedOptionsFile, profile.MinecraftVersion);
                SyncFileFromInstance(Path.Combine(targetDir, GraphicsRenderer.OptionsStateFile), SharedRendererOptionsState);
                var sharedConfig = Path.Combine(_pathService.SharedDirectory, "thelads_config.json");
                var targetConfig = Path.Combine(targetDir, "thelads_config.json");
                if (File.Exists(targetConfig)) SyncFileFromInstance(targetConfig, sharedConfig);
            });
        }
        // Never restore instance account/profile snapshots over newer logins or removals.
        return reconcileServerList ? await _sharedContent.ReconcileFallbackServersAsync(targetDir) : SharedContentReport.Empty;
    }

    /// <summary>A 1.8.9 (Forge) profile must not use the global folder or a Fabric profile's game folder: Forge and Fabric mods
    /// cannot share a mods folder.</summary>
    private void RefuseSharedGameFolder(LauncherProfile profile, string gameDirectory)
    {
        var folder = SafeFileOps.GetFinalPath(gameDirectory);
        var other = SafeFileOps.PathsEqual(SafeFileOps.GetFinalPath(_sharedContent.Root), folder) ? $"the global Minecraft folder '{_sharedContent.Root}'"
            : GetProfiles().FirstOrDefault(p => !GameVersionPolicy.UsesForge(p.MinecraftVersion)
                && SafeFileOps.PathsEqual(SafeFileOps.GetFinalPath(_pathService.GetProfileDirectory(p)), folder)) is { } fabric ? $"'{fabric.Name}'" : null;
        if (other != null)
            throw new InvalidOperationException($"'{profile.Name}' uses the same game folder as {other} ('{gameDirectory}'). Minecraft {profile.MinecraftVersion} runs on Forge, and Forge and Fabric mods cannot share a mods folder, so it needs a game folder of its own. Nothing was changed.");
    }

    /// <summary>Lunar Client's folder (only ever read). Tests pass a sandbox.</summary>
    public string LunarRoot { get; init; } = GameOptionsService.LunarRoot();

    /// <summary>1.8.9 game settings (options.txt, keybinds translated to 1.8 key codes) come from Lunar Client's 1.8 profile when
    /// Lunar is installed, else from the launcher's shared copy; Lunar's OptiFine settings (optionsof.txt) are copied in once.</summary>
    private void SyncOptionsTo189(string targetDir, string minecraftVersion)
    {
        var lunar = GameOptionsService.LunarOptions18(LunarRoot);
        GameOptionsService.SyncToInstance(lunar ?? _pathService.SharedOptionsFile, Path.Combine(targetDir, "options.txt"), minecraftVersion);
        var optiFine = Path.Combine(targetDir, "optionsof.txt");
        var lunarOptiFine = Path.Combine(LunarRoot, "profiles", "1.8", "optionsof.txt");
        if (lunar != null && !File.Exists(optiFine) && File.Exists(lunarOptiFine)) File.Copy(lunarOptiFine, optiFine);
    }

    private string SharedRendererOptionsState => Path.Combine(Path.GetDirectoryName(_pathService.SharedOptionsFile)!, GraphicsRenderer.OptionsStateFile);

    // Kept only as the old launcher's list: it is merged into the shared servers.dat once, never written again.
    private string LegacySharedServersFile => Path.Combine(_pathService.SharedDirectory, "servers.dat");

    /// <summary>LadsCore reads the shared server list itself only on the Fabric versions it is enabled for; the 1.8.9 Core does not,
    /// so a 1.8.9 profile gets the launcher's synced copy.</summary>
    private static bool UsesCore(LauncherProfile profile, string gameDirectory, out string? stateFileError)
    {
        stateFileError = null;
        return GameVersionPolicy.RequiresFabric(profile.MinecraftVersion) && !string.IsNullOrWhiteSpace(profile.FabricVersion)
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
                    // Before 1.4.8 every 1.8.9 profile was forced to keep its own settings; it shares them now, like any profile
                    // (once: the user may isolate it again).
                    if (!container.Shared189)
                    {
                        foreach (var forge in _profiles.Where(p => GameVersionPolicy.UsesForge(p.MinecraftVersion)))
                            forge.IsIsolated = false;
                        changed = true;
                    }
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
                        // A Fabric profile, as before 1.8.9 had a bundled Core: never switch a player to 1.8.9 by surprise.
                        var fallback = _profiles.FirstOrDefault(p => GameVersionPolicy.RequiresFabric(p.MinecraftVersion)
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

    /// <summary>The name of the profile that always follows the newest supported Minecraft version.</summary>
    public const string LatestReleaseName = "Latest Release";

    /// <summary>The highest Minecraft version among the bundled profiles.</summary>
    public static string NewestVersion => CreateDefaultProfiles()
        .Select(p => p.MinecraftVersion).OrderByDescending(v => Version.Parse(v)).First();

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
        var newest = NewestVersion;
        if (profile.MinecraftVersion is "latest.release" or "latest-release"
            || (profile.Name == LatestReleaseName && profile.MinecraftVersion != newest && GameVersionPolicy.RequiresFabric(profile.MinecraftVersion)))
        {
            var oldVersion = profile.MinecraftVersion;
            profile.MinecraftVersion = newest;
            profile.JavaMajorVersion = GameVersionPolicy.GetRequiredJavaMajor(newest);
            if (string.IsNullOrWhiteSpace(profile.FabricVersion))
                profile.FabricVersion = "0.19.5";
            else if (profile.FabricVersion.StartsWith("fabric-loader-", StringComparison.Ordinal)
                && profile.FabricVersion.EndsWith("-" + oldVersion, StringComparison.Ordinal))
                profile.FabricVersion = profile.FabricVersion.Substring(0, profile.FabricVersion.Length - oldVersion.Length) + newest;
            changed = true;
        }

        if (GameVersionPolicy.RequiresFabric(profile.MinecraftVersion) && !string.IsNullOrWhiteSpace(profile.FabricVersion))
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

        // These two URLs were generated by earlier defaults and verified to return 404. The third is The Lads' own pre-1.2 pack
        // (the old launcher default): it would put back standalone mods the launcher pack removed or Core now provides.
        // Exact comparisons intentionally preserve custom hosts, paths and query strings.
        if (profile.PackwizUrl is
            "https://raw.githubusercontent.com/ArashYT/TheLadsClient/main/Packwiz/26.2/pack.toml" or
            "https://raw.githubusercontent.com/ArashYT/TheLadsClient/main/Packwiz/1.21.1/pack.toml" or
            "https://raw.githubusercontent.com/ArashYT/TheLadsClient/main/Packwiz/pack.toml")
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
            return !GameVersionPolicy.RequiresFabric(profile.MinecraftVersion)
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
            // Forge and Java 8.
            new()
            {
                Id = "1.8.9", Name = "The Lads Client 1.8.9", MinecraftVersion = "1.8.9",
                FabricVersion = null, JavaMajorVersion = 8, IsIsolated = false, PackwizUrl = null
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
                Profiles = _profiles.ToList(),
                Shared189 = true
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
        /// <summary>The 1.8.9 profiles' forced IsIsolated was cleared (1.4.8).</summary>
        public bool Shared189 { get; set; }
    }
}
