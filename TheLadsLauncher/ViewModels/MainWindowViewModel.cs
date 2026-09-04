using System;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.ViewModels;

public class MainWindowViewModel : ViewModelBase
{
    private string _currentTab = "Home";
    private string _statusText = "Ready";

    public MainWindowViewModel(
        IPathService pathService,
        IProfileService profileService,
        IJavaService javaService,
        IAuthService authService,
        ILaunchService launchService,
        LauncherSettings settings)
    {
        PathService = pathService;
        ProfileService = profileService;
        JavaService = javaService;
        AuthService = authService;
        LaunchService = launchService;
        Settings = settings;

        ProfilesVM = new ProfilesViewModel(ProfileService, PathService);
        LaunchVM = new LaunchViewModel(LaunchService, ProfileService, AuthService);
        SettingsVM = new SettingsViewModel(PathService, JavaService, Settings);
    }

    public IPathService PathService { get; }
    public IProfileService ProfileService { get; }
    public IJavaService JavaService { get; }
    public IAuthService AuthService { get; }
    public ILaunchService LaunchService { get; }
    public LauncherSettings Settings { get; }

    public ProfilesViewModel ProfilesVM { get; }
    public LaunchViewModel LaunchVM { get; }
    public SettingsViewModel SettingsVM { get; }

    public string CurrentTab
    {
        get => _currentTab;
        set => SetProperty(ref _currentTab, value);
    }

    public string StatusText
    {
        get => _statusText;
        set => SetProperty(ref _statusText, value);
    }

    public string LauncherVersion => Settings.LauncherVersion;
}
