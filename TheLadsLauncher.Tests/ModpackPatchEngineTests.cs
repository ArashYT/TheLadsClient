using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ModpackPatchEngineTests : IDisposable
{
    private readonly string _tempDir;

    public ModpackPatchEngineTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), "lads_patch_test_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        try { Directory.Delete(_tempDir, true); } catch { }
    }

    [Fact]
    public void ApplyKeybinds_InjectsDefault_WhenKeyMissing()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        Directory.CreateDirectory(gameDir);
        File.WriteAllText(Path.Combine(gameDir, "options.txt"), "key_key.jump:key.keyboard.space\nkey_key.sprint:key.keyboard.left.control\n");

        var keybinds = new PatchKeybinds
        {
            Defaults = new Dictionary<string, string>
            {
                ["key_newmod.zoom"] = "key.keyboard.c"
            }
        };

        ModpackPatchEngine.ApplyKeybinds(gameDir, keybinds);

        var result = File.ReadAllText(Path.Combine(gameDir, "options.txt"));
        Assert.Contains("key_newmod.zoom:key.keyboard.c", result);
        Assert.Contains("key_key.jump:key.keyboard.space", result);
        Assert.Contains("key_key.sprint:key.keyboard.left.control", result);
    }

    [Fact]
    public void ApplyKeybinds_PreservesExistingUserKey_WhenDefaultGiven()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        Directory.CreateDirectory(gameDir);
        // Player already customized the key to 'x'
        File.WriteAllText(Path.Combine(gameDir, "options.txt"), "key_newmod.zoom:key.keyboard.x\n");

        var keybinds = new PatchKeybinds
        {
            Defaults = new Dictionary<string, string>
            {
                ["key_newmod.zoom"] = "key.keyboard.c" // Suggested default should NOT overwrite player choice
            }
        };

        ModpackPatchEngine.ApplyKeybinds(gameDir, keybinds);

        var result = File.ReadAllText(Path.Combine(gameDir, "options.txt"));
        Assert.Contains("key_newmod.zoom:key.keyboard.x", result);
        Assert.DoesNotContain("key_newmod.zoom:key.keyboard.c", result);
    }

    [Fact]
    public void ApplyKeybinds_ForcesOverride_WhenForceGiven()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        Directory.CreateDirectory(gameDir);
        File.WriteAllText(Path.Combine(gameDir, "options.txt"), "key_broken.conflict:key.keyboard.r\nkey_key.sprint:key.keyboard.left.control\n");

        var keybinds = new PatchKeybinds
        {
            Force = new Dictionary<string, string>
            {
                ["key_broken.conflict"] = "key.keyboard.k" // Forced migration
            }
        };

        ModpackPatchEngine.ApplyKeybinds(gameDir, keybinds);

        var result = File.ReadAllText(Path.Combine(gameDir, "options.txt"));
        Assert.Contains("key_broken.conflict:key.keyboard.k", result);
        Assert.DoesNotContain("key_broken.conflict:key.keyboard.r", result);
        Assert.Contains("key_key.sprint:key.keyboard.left.control", result);
    }

    [Fact]
    public void ApplyConfigPatch_DeepMergesJson_PreservingOtherFields()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        Directory.CreateDirectory(Path.Combine(gameDir, "config"));
        var configFile = Path.Combine(gameDir, "config", "testmod.json");

        // Existing user config with custom values
        File.WriteAllText(configFile, @"{
  ""playerCustomSetting"": 42,
  ""graphics"": {
    ""renderScale"": 1.5,
    ""enableFog"": false
  }
}");

        using var doc = JsonDocument.Parse(@"{ ""enableFog"": true, ""newFeature"": true }");
        var patch = new PatchConfig
        {
            File = "config/testmod.json",
            Format = "json",
            Set = new Dictionary<string, JsonElement>
            {
                ["graphics.enableFog"] = doc.RootElement.GetProperty("enableFog"),
                ["graphics.newFeature"] = doc.RootElement.GetProperty("newFeature")
            }
        };

        ModpackPatchEngine.ApplyConfigPatch(gameDir, patch);

        var content = File.ReadAllText(configFile);
        var json = JsonNode.Parse(content)!.AsObject();

        // Player's custom settings were preserved
        Assert.Equal(42, json["playerCustomSetting"]!.GetValue<int>());
        Assert.Equal(1.5, json["graphics"]!["renderScale"]!.GetValue<double>());
        // Targeted patches applied
        Assert.True(json["graphics"]!["enableFog"]!.GetValue<bool>());
        Assert.True(json["graphics"]!["newFeature"]!.GetValue<bool>());
    }

    [Fact]
    public void ApplyConfigPatch_ModifiesTomlOrCfgLine()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        Directory.CreateDirectory(Path.Combine(gameDir, "config"));
        var configFile = Path.Combine(gameDir, "config", "server.toml");

        File.WriteAllText(configFile, "maxPlayers = 20\nviewDistance = 10\n");

        var patch = new PatchConfig
        {
            File = "config/server.toml",
            Format = "toml",
            KeyValues = new Dictionary<string, string>
            {
                ["viewDistance"] = "16",
                ["simulationDistance"] = "12"
            }
        };

        ModpackPatchEngine.ApplyConfigPatch(gameDir, patch);

        var lines = File.ReadAllLines(configFile);
        Assert.Contains("maxPlayers = 20", lines);
        Assert.Contains("viewDistance = 16", lines);
        Assert.Contains("simulationDistance=12", lines);
    }

    [Fact]
    public void RecordAndGetAppliedPatches_TracksVersions()
    {
        var applied = ModpackPatchEngine.GetAppliedPatches(_tempDir);
        Assert.Empty(applied);

        ModpackPatchEngine.RecordAppliedPatch(_tempDir, "1.0.1");
        ModpackPatchEngine.RecordAppliedPatch(_tempDir, "1.0.2");

        applied = ModpackPatchEngine.GetAppliedPatches(_tempDir);
        Assert.Equal(new[] { "1.0.1", "1.0.2" }, applied);
    }
    /// <summary>The shipped Zero SMP patch 1.1.2 against the real configs it targets (Fixtures/ZeroSmp: Distant Horizons 3.3.3's own
    /// DistantHorizons.toml and ModernFix 5.27's properties, copied from the dev instance). The engine matches a bare key on the first
    /// line and appends a missing key at the end of the file, which in TOML lands in the wrong table: every key must already be there, once.</summary>
    [Fact]
    public async Task ZeroSmpPatch112_SetsEveryKeyInPlace()
    {
        var fixtures = Path.Combine(AppContext.BaseDirectory, "Fixtures", "ZeroSmp");
        var patch = JsonSerializer.Deserialize<ModpackPatch>(File.ReadAllText(Path.Combine(fixtures, "update-1.1.2.json")),
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true })!;
        var config = Path.Combine(_tempDir, "minecraft", "config");
        Directory.CreateDirectory(config);
        File.Copy(Path.Combine(fixtures, "DistantHorizons.toml"), Path.Combine(config, "DistantHorizons.toml"));
        File.Copy(Path.Combine(fixtures, "modernfix-mixins.properties"), Path.Combine(config, "modernfix-mixins.properties"));
        int dhLines = File.ReadAllLines(Path.Combine(config, "DistantHorizons.toml")).Length;

        await ModpackPatchEngine.ApplyPatchAsync(_tempDir, patch, null, null);

        Assert.Equal(2, patch.ConfigPatches!.Count);
        foreach (var cfg in patch.ConfigPatches)
        {
            var lines = File.ReadAllLines(Path.Combine(_tempDir, "minecraft", cfg.File));
            foreach (var (key, value) in cfg.KeyValues!)
            {
                var keyLines = lines.Where(l => System.Text.RegularExpressions.Regex.IsMatch(l, $@"^\s*{System.Text.RegularExpressions.Regex.Escape(key)}\s*[=:]")).ToList();
                Assert.True(keyLines.Count == 1, $"{cfg.File}: {key} is on {keyLines.Count} lines");
                Assert.EndsWith(value, keyLines[0]);
            }
        }
        Assert.Equal(dhLines, File.ReadAllLines(Path.Combine(config, "DistantHorizons.toml")).Length); // nothing appended
        Assert.Contains("rendererMode = \"DEFAULT\"", File.ReadAllText(Path.Combine(config, "DistantHorizons.toml")));
    }
}
