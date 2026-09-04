using System;
using System.Collections.ObjectModel;
using System.Threading.Tasks;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.ViewModels;

public class SettingsViewModel : ViewModelBase
{
    private readonly IPathService _pathService;
    private readonly IJavaService _javaService;
    private readonly LauncherSettings _settings;
    private string _statusMessage = "";
    private double _downloadProgress = 0;
    private bool _isDownloadingJava = false;

    public SettingsViewModel(IPathService pathService, IJavaService javaService, LauncherSettings settings)
    {
        _pathService = pathService;
        _javaService = javaService;
        _settings = settings;

        DetectedJavas = new ObservableCollection<string>(_javaService.ScanAllSystemJavas());
    }

    public ObservableCollection<string> DetectedJavas { get; }

    public string BaseDirectory
    {
        get => _pathService.BaseDirectory;
        set
        {
            if (_pathService.BaseDirectory != value)
            {
                _pathService.SetCustomBaseDirectory(value);
                _settings.InstancePath = value;
                _settings.Save();
                OnPropertyChanged();
                StatusMessage = $"Base directory updated to: {value}";
            }
        }
    }

    public int MaxRamMb
    {
        get => _settings.MaxRamMb;
        set
        {
            if (_settings.MaxRamMb != value)
            {
                _settings.MaxRamMb = value;
                _settings.Save();
                OnPropertyChanged();
            }
        }
    }

    public int MinRamMb
    {
        get => _settings.MinRamMb;
        set
        {
            if (_settings.MinRamMb != value)
            {
                _settings.MinRamMb = value;
                _settings.Save();
                OnPropertyChanged();
            }
        }
    }

    public bool AutoDetectJava
    {
        get => _settings.AutoDetectJava;
        set
        {
            if (_settings.AutoDetectJava != value)
            {
                _settings.AutoDetectJava = value;
                _settings.Save();
                OnPropertyChanged();
            }
        }
    }

    public string JavaPath
    {
        get => _settings.JavaPath;
        set
        {
            if (_settings.JavaPath != value)
            {
                _settings.JavaPath = value;
                _settings.Save();
                OnPropertyChanged();
            }
        }
    }

    public string Theme
    {
        get => _settings.Theme;
        set
        {
            if (_settings.Theme != value)
            {
                _settings.Theme = value;
                _settings.Save();
                OnPropertyChanged();
            }
        }
    }

    public string StatusMessage
    {
        get => _statusMessage;
        set => SetProperty(ref _statusMessage, value);
    }

    public double DownloadProgress
    {
        get => _downloadProgress;
        set => SetProperty(ref _downloadProgress, value);
    }

    public bool IsDownloadingJava
    {
        get => _isDownloadingJava;
        set => SetProperty(ref _isDownloadingJava, value);
    }

    public async Task<string> DownloadAdoptiumJavaAsync(int majorVersion)
    {
        IsDownloadingJava = true;
        DownloadProgress = 0;
        StatusMessage = $"Starting download of Java {majorVersion} via Adoptium REST API...";

        try
        {
            var progress = new Progress<double>(p => DownloadProgress = p);
            var path = await _javaService.DownloadAndInstallAdoptiumJavaAsync(
                majorVersion,
                progress,
                msg => StatusMessage = msg);

            if (!DetectedJavas.Contains(path))
            {
                DetectedJavas.Add(path);
            }
            JavaPath = path;
            StatusMessage = $"Java {majorVersion} installed: {path}";
            return path;
        }
        catch (Exception ex)
        {
            StatusMessage = $"Failed to download Java {majorVersion}: {ex.Message}";
            throw;
        }
        finally
        {
            IsDownloadingJava = false;
        }
    }

    public void RefreshDetectedJavas()
    {
        DetectedJavas.Clear();
        foreach (var j in _javaService.ScanAllSystemJavas())
        {
            DetectedJavas.Add(j);
        }
        StatusMessage = $"Found {DetectedJavas.Count} Java installation(s) on system.";
    }
}
