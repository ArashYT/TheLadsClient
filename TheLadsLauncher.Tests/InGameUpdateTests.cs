using System.Text.Json;
using TheLadsLauncher.Services;
using Xunit;

public class InGameUpdateTests : IDisposable
{
    private readonly string _game = Path.Combine(Path.GetTempPath(), "lads-ingame-update-" + Guid.NewGuid().ToString("N"));
    public void Dispose() { if (Directory.Exists(_game)) Directory.Delete(_game, true); }

    private string Notice => Path.Combine(_game, InGameUpdate.NoticeFile);
    private string Request => Path.Combine(_game, InGameUpdate.RequestFile);

    [Fact]
    public void AReadyUpdateIsWrittenForTheGameWithThisLaunchersProcessId()
    {
        InGameUpdate.Prepare(_game, "1.13.9");
        using var doc = JsonDocument.Parse(File.ReadAllText(Notice));
        Assert.Equal("1.13.9", doc.RootElement.GetProperty("version").GetString());
        Assert.Equal(Environment.ProcessId, doc.RootElement.GetProperty("launcher").GetInt32());
    }

    [Fact]
    public void NoReadyUpdateLeavesNoNoticeAndNoOldRequest()
    {
        InGameUpdate.Prepare(_game, "1.13.9");
        File.WriteAllText(Request, "{}");
        InGameUpdate.Prepare(_game, null);
        Assert.False(File.Exists(Notice));
        Assert.False(File.Exists(Request));
    }

    [Fact]
    public void ANoticeForARunningGameKeepsItsRequest()
    {
        Directory.CreateDirectory(_game);
        File.WriteAllText(Request, "{}");
        InGameUpdate.Notify(_game, "1.13.9");
        Assert.True(File.Exists(Notice));
        Assert.True(File.Exists(Request));
    }

    [Fact]
    public void TheGamesRequestIsTakenOnce()
    {
        Directory.CreateDirectory(_game);
        Assert.False(InGameUpdate.TakeRequest(_game));
        File.WriteAllText(Request, "{\"requested\":\"2026-11-10T20:00:00Z\"}");
        Assert.True(InGameUpdate.TakeRequest(_game));
        Assert.False(InGameUpdate.TakeRequest(_game));
    }

    [Theory]
    [InlineData(new[] { "--restore-window", "1,2,3,4,Normal", "--play-profile", "abc" }, "abc")]
    [InlineData(new[] { "--play-profile" }, null)]
    [InlineData(new[] { "--play-profile", " " }, null)]
    [InlineData(new string[0], null)]
    public void ThePlayArgumentNamesTheProfile(string[] args, string? expected) => Assert.Equal(expected, InGameUpdate.PlayProfile(args));
}
