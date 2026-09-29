using System.Text.Json.Nodes;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class ModPreferencesTests : IDisposable
{
    private readonly ModSandbox box = new();

    [Fact]
    public void MissingFileMeansNoChoices()
    {
        var preferences = ModPreferences.Load(box.Game);
        Assert.Null(preferences.GetEnabled("sodium", "AANobbMI"));
        Assert.Null(preferences.Error);
    }

    [Fact]
    public async Task UpdateKeepsUnknownContentAndRecordsTheChoice()
    {
        File.WriteAllText(box.State, """
            {"schema":1,"future":{"keep":[1,2]},"mods":{"sodium":{"enabled":true,"note":"from game","source":"game"},"iris":{"enabled":false}}}
            """);
        await ModPreferences.UpdateAsync(box.Game, root => ModPreferences.SetMod(root, "sodium", false, "AANobbMI"));

        var root = JsonNode.Parse(File.ReadAllText(box.State))!;
        Assert.Equal(2, root["future"]!["keep"]!.AsArray().Count);
        Assert.Equal("from game", root["mods"]!["sodium"]!["note"]!.GetValue<string>());
        Assert.Equal("launcher", root["mods"]!["sodium"]!["source"]!.GetValue<string>());
        Assert.Equal("AANobbMI", root["mods"]!["sodium"]!["projectId"]!.GetValue<string>());
        Assert.EndsWith("Z", root["mods"]!["sodium"]!["updatedAt"]!.GetValue<string>());
        var preferences = ModPreferences.Load(box.Game);
        Assert.False(preferences.GetEnabled("sodium", null));
        Assert.False(preferences.GetEnabled("iris", null));
    }

    [Fact]
    public async Task CorruptFileIsIgnoredAndKeptAsideBeforeTheNextWrite()
    {
        const string corrupt = "{\"mods\": {\"sodium\": {\"enabled\": fal";
        File.WriteAllText(box.State, corrupt);

        var preferences = ModPreferences.Load(box.Game);
        Assert.NotNull(preferences.Error);
        Assert.Null(preferences.GetEnabled("sodium", null));
        Assert.Equal(corrupt, File.ReadAllText(box.State)); // Reading never rewrites.

        await ModPreferences.UpdateAsync(box.Game, root => ModPreferences.SetMod(root, "iris", false, null));
        Assert.Equal(corrupt, File.ReadAllText(Assert.Single(Directory.GetFiles(box.Game, ModPreferences.FileName + ".corrupt-*"))));
        var reloaded = ModPreferences.Load(box.Game);
        Assert.Null(reloaded.Error);
        Assert.False(reloaded.GetEnabled("iris", null));
    }

    [Fact]
    public async Task DuplicatedKeysCountAsUnreadableAndAreKeptAside()
    {
        const string duplicated = """{"mods":{"sodium":{"enabled":false},"sodium":{"enabled":true}}}""";
        File.WriteAllText(box.State, duplicated);

        var preferences = ModPreferences.Load(box.Game);
        Assert.Contains("unreadable", preferences.Error);
        Assert.Null(preferences.GetEnabled("sodium", null));

        await ModPreferences.UpdateAsync(box.Game, root => ModPreferences.SetMod(root, "iris", false, null));
        Assert.Equal(duplicated, File.ReadAllText(Assert.Single(Directory.GetFiles(box.Game, ModPreferences.FileName + ".corrupt-*"))));
        Assert.False(ModPreferences.Load(box.Game).GetEnabled("iris", null));
    }

    [Fact]
    public async Task ChoiceFollowsTheProjectWhenAnotherVersionUsesADifferentModId()
    {
        await ModPreferences.UpdateAsync(box.Game, root => ModPreferences.SetMod(root, "old-id", false, "PROJECT1"));
        var preferences = ModPreferences.Load(box.Game);
        Assert.False(preferences.GetEnabled("new-id", "PROJECT1"));
        Assert.Null(preferences.GetEnabled("new-id", "PROJECT2"));
        Assert.Null(preferences.GetEnabled("new-id", null));
    }

    [Fact]
    public async Task ConcurrentWritersNeverLoseAChoice()
    {
        await Task.WhenAll(Enumerable.Range(0, 16).Select(i =>
            Task.Run(() => ModPreferences.UpdateAsync(box.Game, root => ModPreferences.SetMod(root, "mod" + i, i % 2 == 0, null)))));
        var preferences = ModPreferences.Load(box.Game);
        for (var i = 0; i < 16; i++) Assert.Equal(i % 2 == 0, preferences.GetEnabled("mod" + i, null));
    }

    public void Dispose() => box.Dispose();
}
