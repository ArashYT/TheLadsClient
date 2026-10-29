namespace TheLadsLauncher.Services;

public static class LauncherHttp
{
    /// <summary>
    /// Shared descriptive User-Agent used by launcher HTTP clients and services.
    /// Format: "TheLadsLauncher/{Program.Version} (contact@thelads.com)".
    /// </summary>
    public static string UserAgent => $"TheLadsLauncher/{Program.Version} (contact@thelads.com)";
}
