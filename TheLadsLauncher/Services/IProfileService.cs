using System.Collections.Generic;
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
    Task SyncSharedToProfileAsync(LauncherProfile profile);
    Task SyncProfileToSharedAsync(LauncherProfile profile);
}
