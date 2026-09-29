using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.ViewModels;

public class ProfilesViewModel : ViewModelBase
{
    private readonly IProfileService _profileService;
    private readonly IPathService _pathService;
    private LauncherProfile? _selectedProfile;
    private string _statusMessage = "";

    public ProfilesViewModel(IProfileService profileService, IPathService pathService)
    {
        _profileService = profileService;
        _pathService = pathService;
        Profiles = new ObservableCollection<LauncherProfile>(_profileService.GetProfiles());
        _selectedProfile = _profileService.GetActiveProfile();
    }

    public ObservableCollection<LauncherProfile> Profiles { get; }

    public LauncherProfile? SelectedProfile
    {
        get => _selectedProfile;
        set
        {
            if (SetProperty(ref _selectedProfile, value) && value != null)
            {
                _profileService.SetActiveProfile(value.Id);
                StatusMessage = $"Active profile set to: {value.Name} ({value.MinecraftVersion})";
            }
        }
    }

    public string StatusMessage
    {
        get => _statusMessage;
        set => SetProperty(ref _statusMessage, value);
    }

    public void RefreshProfiles()
    {
        Profiles.Clear();
        foreach (var p in _profileService.GetProfiles())
        {
            Profiles.Add(p);
        }
        SelectedProfile = _profileService.GetActiveProfile();
    }

    public LauncherProfile AddProfile(string name, string mcVersion, int javaMajor, bool isIsolated = false, string? fabricVersion = null)
    {
        var profile = _profileService.CreateProfile(name, mcVersion, javaMajor, isIsolated, fabricVersion);
        Profiles.Add(profile);
        SelectedProfile = profile;
        StatusMessage = $"Created new profile: {profile.Name}";
        return profile;
    }

    public bool DeleteProfile(LauncherProfile? profile)
    {
        if (profile == null) return false;
        var success = _profileService.DeleteProfile(profile.Id);
        if (success)
        {
            Profiles.Remove(profile);
            SelectedProfile = _profileService.GetActiveProfile();
            StatusMessage = $"Deleted profile: {profile.Name}";
        }
        return success;
    }

    public void ToggleIsolation(LauncherProfile? profile)
    {
        if (profile == null) return;
        profile.IsIsolated = !profile.IsIsolated;
        _profileService.SaveProfile(profile);
        OnPropertyChanged(nameof(SelectedProfile));
        StatusMessage = $"{profile.Name} now {(profile.IsIsolated ? "keeps its own game settings (options.txt, keybinds)" : "shares game settings (options.txt, keybinds) with other profiles")}. "
            + "Worlds, resource packs, shader packs and the server list are always shared.";
    }

    public async Task SyncProfileSettingsAsync(LauncherProfile? profile)
    {
        if (profile == null) return;
        StatusMessage = $"Syncing settings for {profile.Name}...";
        var report = await _profileService.PrepareProfileEnvironmentAsync(profile, null);
        StatusMessage = report.Warnings.Count > 0
            ? $"Synced {profile.Name} with {report.Warnings.Count} warning(s): {report.Warnings[0]}"
            : $"Settings synced for {profile.Name} via {_pathService.SharedDirectory}";
    }
}
