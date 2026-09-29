using System.Text.Json;
using Xunit;

namespace TheLadsLauncher.Tests;

public class MicrosoftClientConfigurationTests
{
    private const string RegisteredLadsClientId = "c8ca54dc-01e3-4bb3-824a-35e09bb3aa13";

    [Fact]
    public void FreshSettingsUseTheRegisteredLadsApplication() =>
        Assert.Equal(RegisteredLadsClientId, new LauncherSettings().MicrosoftClientId);

    [Theory]
    [InlineData("{}")]
    [InlineData("{\"MicrosoftClientId\":\"\"}")]
    [InlineData("{\"MicrosoftClientId\":null}")]
    [InlineData("{\"MicrosoftClientId\":\"   \"}")]
    public void LegacySettingsWithoutAnApplicationUseTheLadsDefault(string json)
    {
        var settings = JsonSerializer.Deserialize<LauncherSettings>(json)!;
        Assert.Equal(RegisteredLadsClientId, settings.MicrosoftClientId);
        var reloaded = JsonSerializer.Deserialize<LauncherSettings>(JsonSerializer.Serialize(settings))!;
        Assert.Equal(RegisteredLadsClientId, reloaded.MicrosoftClientId);
    }

    [Fact]
    public void CustomApplicationSurvivesSettingsReload()
    {
        const string customId = "571f9841-8f4a-46f5-83d9-2c4a9f4ff043";
        var settings = JsonSerializer.Deserialize<LauncherSettings>($"{{\"MicrosoftClientId\":\"{customId}\",\"MaxRamMb\":6144}}")!;
        var reloaded = JsonSerializer.Deserialize<LauncherSettings>(JsonSerializer.Serialize(settings))!;
        Assert.Equal(customId, reloaded.MicrosoftClientId);
        Assert.Equal(6144, reloaded.MaxRamMb);
    }

    [Theory]
    [InlineData("not-an-application-id")]
    [InlineData("00000000-0000-0000-0000-000000000000")]
    public void InvalidExplicitApplicationIsNotSilentlyReplaced(string invalidId)
    {
        var settings = JsonSerializer.Deserialize<LauncherSettings>($"{{\"MicrosoftClientId\":\"{invalidId}\"}}")!;
        Assert.Equal(invalidId, settings.MicrosoftClientId);
        Assert.True(!Guid.TryParse(settings.MicrosoftClientId, out var id) || id == Guid.Empty);
    }

    [Fact]
    public void ClearingACustomApplicationRestoresTheLadsDefault()
    {
        var settings = new LauncherSettings { MicrosoftClientId = "571f9841-8f4a-46f5-83d9-2c4a9f4ff043" };
        settings.MicrosoftClientId = "";
        Assert.Equal(RegisteredLadsClientId, settings.MicrosoftClientId);
    }
}
