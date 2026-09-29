using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public interface IProfileService
{
    IReadOnlyList<LauncherProfile> GetProfiles();
    LauncherProfile? GetProfile(string id);
    LauncherProfile GetActiveProfile();
    void SetActiveProfile(string id);
    LauncherProfile CreateProfile(string name, string mcVersion, int javaMajor, bool isIsolated = false, string? fabricVersion = null);
    void AddProfile(LauncherProfile profile);
    void SaveProfile(LauncherProfile profile);
    void SaveProfiles();
    bool DeleteProfile(string id);

    Task PrepareProfileEnvironmentAsync(LauncherProfile profile);
    /// <summary>Shared worlds/packs/server list for every profile, plus options.txt sync unless IsIsolated. May throw
    /// SharedContentUnavailableException; retry with withoutSharedFolders: true only when the user chose to.</summary>
    Task<SharedContentReport> PrepareProfileEnvironmentAsync(LauncherProfile profile, IProgress<string>? progress,
        CancellationToken cancellationToken = default, bool withoutSharedFolders = false);
    Task<IReadOnlyDictionary<string, SharedContentReport>> PrepareAllProfilesSharedContentAsync(IProgress<string>? progress = null,
        CancellationToken cancellationToken = default);
    IReadOnlyList<LauncherProfile> ProfilesOutside(string root);
    Task SyncSharedToProfileAsync(LauncherProfile profile);
    Task<SharedContentReport> SyncProfileToSharedAsync(LauncherProfile profile, bool reconcileServerList = true);
}
