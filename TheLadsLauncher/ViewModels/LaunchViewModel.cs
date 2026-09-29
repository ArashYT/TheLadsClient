using System;
using System.Diagnostics;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.ViewModels;

public class LaunchViewModel : ViewModelBase
{
    private readonly ILaunchService _launchService;
    private readonly IProfileService _profileService;
    private readonly IAuthService _authService;
    private bool _isLaunching;
    private string _launchStatus = "Ready to play";
    private double _launchProgress;
    private string _launchButtonText = "PLAY";

    public LaunchViewModel(
        ILaunchService launchService,
        IProfileService profileService,
        IAuthService authService)
    {
        _launchService = launchService;
        _profileService = profileService;
        _authService = authService;
    }

    public bool IsLaunching
    {
        get => _isLaunching;
        set => SetProperty(ref _isLaunching, value);
    }

    public string LaunchStatus
    {
        get => _launchStatus;
        set => SetProperty(ref _launchStatus, value);
    }

    public double LaunchProgress
    {
        get => _launchProgress;
        set => SetProperty(ref _launchProgress, value);
    }

    public string LaunchButtonText
    {
        get => _launchButtonText;
        set => SetProperty(ref _launchButtonText, value);
    }

    public LauncherProfile CurrentProfile => _profileService.GetActiveProfile();

    public string? CurrentAccount => _authService.ActiveAccount;

    public async Task<Process?> StartLaunchAsync(
        LauncherSettings settings,
        CancellationToken cancellationToken = default)
    {
        if (IsLaunching) return null;

        IsLaunching = true;
        LaunchButtonText = "LAUNCHING...";
        LaunchProgress = 0;
        LaunchStatus = "Preparing game launch...";

        try
        {
            var profile = _profileService.GetActiveProfile();
            var account = _authService.ActiveAccount ?? throw new InvalidOperationException("Select an account before launching.");

            var progress = new Progress<double>(p =>
            {
                LaunchProgress = p;
            });

            var process = await _launchService.LaunchAsync(
                profile,
                account,
                settings,
                progress,
                status => LaunchStatus = status,
                cancellationToken);

            LaunchStatus = $"Running Minecraft {profile.MinecraftVersion}";
            LaunchButtonText = "PLAYING";
            return process;
        }
        catch (Exception ex)
        {
            LaunchStatus = $"Launch error: {ex.Message}";
            LaunchButtonText = "PLAY";
            throw;
        }
        finally
        {
            IsLaunching = false;
        }
    }
}
