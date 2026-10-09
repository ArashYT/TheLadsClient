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

    [Fact]
    public void ApplyConfigPatch_Flat_KeepsDottedKeysLiteral()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        Directory.CreateDirectory(Path.Combine(gameDir, "config"));
        File.WriteAllText(Path.Combine(gameDir, "config", "lazyyyyy.mixins.json"), "{\"debug\":false,\"lazy_entity_renderers\":true}");
        var patch = new PatchConfig
        {
            File = "config/lazyyyyy.mixins.json",
            Format = "json",
            Flat = true,
            Set = new Dictionary<string, JsonElement>
            {
                ["lazy_entity_renderers"] = JsonDocument.Parse("false").RootElement,
                ["entity_sound_features.async_sound_events"] = JsonDocument.Parse("false").RootElement
            }
        };

        ModpackPatchEngine.ApplyConfigPatch(gameDir, patch);

        var root = JsonNode.Parse(File.ReadAllText(Path.Combine(gameDir, "config", "lazyyyyy.mixins.json")))!.AsObject();
        Assert.False(root["lazy_entity_renderers"]!.GetValue<bool>());
        Assert.False(root["entity_sound_features.async_sound_events"]!.GetValue<bool>()); // not entity_sound_features -> async_sound_events
        Assert.False(root["debug"]!.GetValue<bool>());
        Assert.Null(root["entity_sound_features"]);
    }

    [Fact]
    public void ApplyConfigPatch_Flat_CreatesAMissingFile()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        var patch = new PatchConfig
        {
            File = "config/new.json",
            Flat = true,
            Set = new Dictionary<string, JsonElement> { ["a.b"] = JsonDocument.Parse("true").RootElement }
        };

        ModpackPatchEngine.ApplyConfigPatch(gameDir, patch);

        Assert.True(JsonNode.Parse(File.ReadAllText(Path.Combine(gameDir, "config", "new.json")))!["a.b"]!.GetValue<bool>());
    }
}
