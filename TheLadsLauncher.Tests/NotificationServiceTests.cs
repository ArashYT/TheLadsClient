using Avalonia.Controls.Notifications;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class NotificationServiceTests
{
    private sealed class MockNotifier : ILauncherNotifier
    {
        public string? LastTitle { get; private set; }
        public string? LastMessage { get; private set; }
        public NotificationType? LastType { get; private set; }

        public void Show(string title, string message, NotificationType type)
        {
            LastTitle = title;
            LastMessage = message;
            LastType = type;
        }
    }

    [Fact]
    public void Notify_DeliversNotificationToManager()
    {
        var mock = new MockNotifier();
        NotificationService.Initialize(mock);

        NotificationService.Notify("Test Title", "Test Message", NotificationType.Success);

        Assert.Equal("Test Title", mock.LastTitle);
        Assert.Equal("Test Message", mock.LastMessage);
        Assert.Equal(NotificationType.Success, mock.LastType);
    }

    [Fact]
    public void NotifyGameReady_TriggersNotificationWhenMinimized()
    {
        var mock = new MockNotifier();
        NotificationService.Initialize(mock);

        NotificationService.NotifyGameReady("Default Profile", isMinimizedOrInactive: true);

        Assert.Equal("The Lads Client", mock.LastTitle);
        Assert.Contains("ready", mock.LastMessage);
        Assert.Contains("Default Profile", mock.LastMessage);
    }

    [Fact]
    public void NotifyGameReady_SkipsWhenActiveAndNotMinimized()
    {
        var mock = new MockNotifier();
        NotificationService.Initialize(mock);

        NotificationService.NotifyGameReady("Default Profile", isMinimizedOrInactive: false);

        Assert.Null(mock.LastTitle);
        Assert.Null(mock.LastMessage);
    }
}
