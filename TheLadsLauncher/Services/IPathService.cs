using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public interface IPathService
{
    string BaseDirectory { get; }
    string SharedDirectory { get; }
    string ProfilesDirectory { get; }
    string RuntimesDirectory { get; }
    string LogsDirectory { get; }
    string BinDirectory { get; }
    string AccountsFile { get; }
    string ProfileConfigFile { get; }
    string SharedOptionsFile { get; }
    string SharedAccountsFile { get; }
    string SharedProfileConfigFile { get; }
    string SkinFile { get; }
    string CapeFile { get; }
    string PresetsDirectory { get; }

    string GetProfileDirectory(string profileId);
    string GetProfileDirectory(LauncherProfile profile);
    string GetJavaRuntimeDirectory(int majorVersion);
    string GetJavaExecutablePath(int majorVersion);
    void EnsureDirectories();
    void SetCustomBaseDirectory(string? customPath);
}
