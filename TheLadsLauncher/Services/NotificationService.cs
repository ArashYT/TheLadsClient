using System;
using Avalonia.Controls.Notifications;

namespace TheLadsLauncher.Services;

public interface ILauncherNotifier
{
    void Show(string title, string message, NotificationType type);
}

public sealed class AvaloniaWindowNotifier(WindowNotificationManager manager) : ILauncherNotifier
{
    public void Show(string title, string message, NotificationType type) =>
        manager.Show(new Notification(title, message, type));
}

/// <summary>
/// Provides native OS and in-window notifications for launcher events (e.g. game launched / ready while minimized).
/// </summary>
public static class NotificationService
{
    private static ILauncherNotifier? _notifier;
    private static System.Windows.Forms.NotifyIcon? _trayIcon;

    public static void Initialize(ILauncherNotifier? notifier, System.Windows.Forms.NotifyIcon? trayIcon = null)
    {
        _notifier = notifier;
        _trayIcon = trayIcon;
    }

    /// <summary>
    /// Displays a notification to the user (via in-window notification manager and/or native OS balloon notification).
    /// </summary>
    public static void Notify(string title, string message, NotificationType type = NotificationType.Information)
    {
        try
        {
            _notifier?.Show(title, message, type);
        }
        catch { }

        if (OperatingSystem.IsWindows() && _trayIcon != null)
        {
            try
            {
                var tipIcon = type switch
                {
                    NotificationType.Error => System.Windows.Forms.ToolTipIcon.Error,
                    NotificationType.Warning => System.Windows.Forms.ToolTipIcon.Warning,
                    _ => System.Windows.Forms.ToolTipIcon.Info
                };
                _trayIcon.ShowBalloonTip(3000, title, message, tipIcon);
            }
            catch { }
        }
    }

    /// <summary>
    /// Notifies that the game process is ready and playing.
    /// </summary>
    public static void NotifyGameReady(string profileName, bool isMinimizedOrInactive = true)
    {
        if (isMinimizedOrInactive)
        {
            Notify("The Lads Client", $"Minecraft is ready! {profileName} launched successfully.", NotificationType.Success);
        }
    }
}
