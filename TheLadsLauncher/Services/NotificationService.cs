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
/// Provides in-window notifications for launcher events.
/// </summary>
public static class NotificationService
{
    private static ILauncherNotifier? _notifier;

    public static void Initialize(ILauncherNotifier? notifier)
    {
        _notifier = notifier;
    }

    /// <summary>
    /// Displays a notification to the user via in-window notification manager.
    /// </summary>
    public static void Notify(string title, string message, NotificationType type = NotificationType.Information)
    {
        try
        {
            _notifier?.Show(title, message, type);
        }
        catch { }
    }
}
