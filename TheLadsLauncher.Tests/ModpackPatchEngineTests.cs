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
    public void ApplyKeybinds_ChangesOnlyItsOwnLines_AndWritesTheFileAtomically()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        Directory.CreateDirectory(gameDir);
        var options = Path.Combine(gameDir, "options.txt");
        // Lines the key:value map would lose or merge: no colon, a padded value, keys that differ only in case, a repeated key.
        File.WriteAllText(options, "version:3465\r\nkey_key.jump:key.keyboard.space\r\n# not a pair\r\nautoJump:false\r\nresourcePacks:[ \"a\" ]  \r\n" +
            "Gamma:1.0\r\ngamma:0.5\r\nkey_dup:one\r\nkey_dup:two\r\n");

        ModpackPatchEngine.ApplyKeybinds(gameDir, new PatchKeybinds
        {
            Defaults = new() { ["key_newmod.zoom"] = "key.keyboard.c", ["key_key.jump"] = "ignored: already there" },
            Force = new() { ["key_key.jump"] = "key.keyboard.j", ["key_dup"] = "three" }
        });

        Assert.Equal("version:3465\r\nkey_key.jump:key.keyboard.j\r\n# not a pair\r\nautoJump:false\r\nresourcePacks:[ \"a\" ]  \r\n" +
            "Gamma:1.0\r\ngamma:0.5\r\nkey_dup:one\r\nkey_dup:three\r\nkey_newmod.zoom:key.keyboard.c\r\n", File.ReadAllText(options));
        Assert.Equal(new[] { "options.txt" }, Directory.GetFiles(gameDir).Select(Path.GetFileName)); // no temp file left beside it
    }

    [Fact]
    public void ApplyConfigPatch_LeavesAJsonFileThatDoesNotParseExactlyAsItIs()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        Directory.CreateDirectory(Path.Combine(gameDir, "config"));
        using var doc = JsonDocument.Parse("true");
        PatchConfig Patch(string file) => new() { File = file, Set = new() { ["graphics.enableFog"] = doc.RootElement } };
        var withComments = "{\n  // the player's own notes\n  \"playerCustomSetting\": 42,\n  \"graphics\": { \"renderScale\": 1.5, },\n}";
        File.WriteAllText(Path.Combine(gameDir, "config", "comments.json"), withComments);
        File.WriteAllText(Path.Combine(gameDir, "config", "array.json"), "[1, 2, 3]");
        File.WriteAllText(Path.Combine(gameDir, "config", "cut.json"), "{ \"playerCustomSetting\": 4");
        File.WriteAllText(Path.Combine(gameDir, "config", "empty.json"), " \n");

        Assert.False(ModpackPatchEngine.ApplyConfigPatch(gameDir, Patch("config/comments.json")));
        Assert.False(ModpackPatchEngine.ApplyConfigPatch(gameDir, Patch("config/array.json")));
        Assert.False(ModpackPatchEngine.ApplyConfigPatch(gameDir, Patch("config/cut.json")));

        Assert.Equal(withComments, File.ReadAllText(Path.Combine(gameDir, "config", "comments.json")));
        Assert.Equal("[1, 2, 3]", File.ReadAllText(Path.Combine(gameDir, "config", "array.json")));
        Assert.Equal("{ \"playerCustomSetting\": 4", File.ReadAllText(Path.Combine(gameDir, "config", "cut.json")));
        // Nothing to lose in an empty or missing file: it is patched.
        Assert.True(ModpackPatchEngine.ApplyConfigPatch(gameDir, Patch("config/empty.json")));
        Assert.True(ModpackPatchEngine.ApplyConfigPatch(gameDir, Patch("config/missing.json")));
        Assert.True(JsonNode.Parse(File.ReadAllText(Path.Combine(gameDir, "config", "empty.json")))!["graphics"]!["enableFog"]!.GetValue<bool>());
        Assert.True(File.Exists(Path.Combine(gameDir, "config", "missing.json")));
    }

    [Fact]
    public async Task APatchWithASkippedConfigIsReportedAndRetriedAtTheNextLaunch()
    {
        var instance = Path.Combine(_tempDir, "instance");
        var config = Path.Combine(instance, "minecraft", "config", "mod.json");
        Directory.CreateDirectory(Path.GetDirectoryName(config)!);
        File.WriteAllText(config, "{ \"keep\": 1, // comment\n }");
        using var doc = JsonDocument.Parse("2");
        var patch = new ModpackPatch
        {
            PackId = "p", Version = "1.0.1",
            Keybinds = new PatchKeybinds { Defaults = new() { ["key_newmod.zoom"] = "key.keyboard.c" } },
            ConfigPatches = new() { new PatchConfig { File = "config/mod.json", Set = new() { ["value"] = doc.RootElement } } }
        };
        var status = new List<string>();

        await ModpackPatchEngine.ApplyPatchAsync(instance, patch, null, status.Add);

        Assert.Contains(status, m => m.Contains("Could not patch config/mod.json") && m.Contains("next launch"));
        Assert.Equal("{ \"keep\": 1, // comment\n }", File.ReadAllText(config));
        Assert.Empty(ModpackPatchEngine.GetAppliedPatches(instance)); // so the next launch applies it again...
        Assert.Contains("key_newmod.zoom:key.keyboard.c", File.ReadAllText(Path.Combine(instance, "minecraft", "options.txt"))); // (the rest of it went in)

        File.WriteAllText(config, "{ \"keep\": 1 }"); // ...once the player has fixed the file
        await ModpackPatchEngine.ApplyPatchAsync(instance, patch, null, status.Add);

        Assert.Equal(new[] { "1.0.1" }, ModpackPatchEngine.GetAppliedPatches(instance));
        var patched = JsonNode.Parse(File.ReadAllText(config))!.AsObject();
        Assert.Equal((1, 2), (patched["keep"]!.GetValue<int>(), patched["value"]!.GetValue<int>()));
        Assert.Equal(1, File.ReadAllLines(Path.Combine(instance, "minecraft", "options.txt")).Count(l => l.StartsWith("key_newmod.zoom:")));
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

    private static readonly string ZeroSmpFixtures = Path.Combine(AppContext.BaseDirectory, "Fixtures", "ZeroSmp");

    private static ModpackPatch BundledPatch(string version) =>
        JsonSerializer.Deserialize<ModpackPatch>(File.ReadAllText(Path.Combine(ExperiencesService.BundledPatchesDirectory, $"update-{version}.json")),
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true })!;

    /// <summary>"table.key" (root keys bare) -> every value it has in a key=value file; comments and blank lines skipped.</summary>
    private static Dictionary<string, List<string>> KeyTable(string file)
    {
        var map = new Dictionary<string, List<string>>();
        string table = "";
        foreach (var line in File.ReadAllLines(file))
        {
            var t = line.Trim();
            if (t.StartsWith('[')) { table = t.Trim('[', ']'); continue; }
            var m = System.Text.RegularExpressions.Regex.Match(t, @"^([A-Za-z0-9_.]+)\s*[=:]\s*(.*)$");
            if (!m.Success) continue;
            var key = table.Length == 0 ? m.Groups[1].Value : table + "." + m.Groups[1].Value;
            if (!map.TryGetValue(key, out var values)) map[key] = values = new List<string>();
            values.Add(m.Groups[2].Value.Trim());
        }
        return map;
    }

    /// <summary>The shipped Zero SMP patch 1.1.2 (bundled with the launcher) against the dev instance's real DistantHorizons.toml (DH 3.3.3,
    /// config version 5) and ModernFix properties, and against the version 4 DH file the 1.0.0 pack shipped: every key ends up once,
    /// in its own [table], with the patch's value.</summary>
    [Theory]
    [InlineData("owner/config/DistantHorizons.toml", 0)]
    [InlineData("DistantHorizons-v4.toml", 1)] // version 4 has no generatorPlan: inserted into [common.worldGenerator]
    public async Task ZeroSmpPatch112_SetsEveryKeyInItsTable(string dhFixture, int insertedLines)
    {
        var patch = BundledPatch("1.1.2");
        var config = Path.Combine(_tempDir, "minecraft", "config");
        Directory.CreateDirectory(config);
        File.Copy(Path.Combine(ZeroSmpFixtures, dhFixture), Path.Combine(config, "DistantHorizons.toml"));
        File.Copy(Path.Combine(ZeroSmpFixtures, "owner", "config", "modernfix-mixins.properties"), Path.Combine(config, "modernfix-mixins.properties"));
        File.Copy(Path.Combine(ZeroSmpFixtures, "owner", "config", "fml.toml"), Path.Combine(config, "fml.toml")); // maxThreads = -1 there
        var ifJson = Path.Combine(config, "immediatelyfast.json");
        File.Copy(Path.Combine(ZeroSmpFixtures, "owner", "config", "immediatelyfast.json"), ifJson);
        var ifBefore = JsonNode.Parse(File.ReadAllText(ifJson))!.AsObject();
        Assert.False(ifBefore["experimental_screen_batching"]!.GetValue<bool>());
        int dhLines = File.ReadAllLines(Path.Combine(config, "DistantHorizons.toml")).Length;

        await ModpackPatchEngine.ApplyPatchAsync(_tempDir, patch, null, null);

        Assert.Equal(4, patch.ConfigPatches!.Count);
        foreach (var cfg in patch.ConfigPatches.Where(c => c.KeyValues != null))
        {
            var keys = KeyTable(Path.Combine(_tempDir, "minecraft", cfg.File));
            foreach (var (key, value) in cfg.KeyValues!)
                Assert.True(keys.TryGetValue(key, out var values) && values.Count == 1 && values[0] == value,
                    $"{cfg.File}: {key} = [{string.Join(", ", values ?? new List<string>())}], expected {value}");
        }
        // ImmediatelyFast: only experimental_screen_batching changes; every other key of the owner's file stays as it was.
        var ifAfter = JsonNode.Parse(File.ReadAllText(ifJson))!.AsObject();
        Assert.True(ifAfter["experimental_screen_batching"]!.GetValue<bool>());
        ifBefore.Remove("experimental_screen_batching"); ifAfter.Remove("experimental_screen_batching");
        Assert.True(JsonNode.DeepEquals(ifBefore, ifAfter));
        var dh = File.ReadAllLines(Path.Combine(config, "DistantHorizons.toml"));
        Assert.Equal(dhLines + insertedLines, dh.Length);
        Assert.Equal("_version = 5", dh[0]);
        Assert.Single(KeyTable(Path.Combine(config, "DistantHorizons.toml")).Keys, k => !k.Contains('.')); // only _version outside a table
    }

    [Fact]
    public void ApplyConfigPatch_Toml_TableKeyGoesIntoItsTable_OrANewOne()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        Directory.CreateDirectory(Path.Combine(gameDir, "config"));
        var file = Path.Combine(gameDir, "config", "a.toml");
        File.WriteAllText(file, "x = 1\n[a]\n\ty = 2\n\n[a.b]\n\ty = 3\n");

        ModpackPatchEngine.ApplyConfigPatch(gameDir, new PatchConfig
        {
            File = "config/a.toml", Format = "toml",
            KeyValues = new Dictionary<string, string> { ["a.b.y"] = "4", ["a.z"] = "5", ["c.w"] = "6", ["x"] = "7" }
        });

        Assert.Equal(new[] { "x = 7", "[a]", "\ty = 2", "\tz = 5", "", "[a.b]", "\ty = 4", "[c]", "w = 6" }, File.ReadAllLines(file));
    }

    /// <summary>The dev instance has no .lads-applied-patches.json. Played through the launcher with the bundled patches, it must not get
    /// old mod changes again: 1.0.1 (Crawl on Demand was dropped later) and 1.1.0 are already there, so only 1.1.1 (its explicit
    /// ModernFix lines are missing) and 1.1.2 apply, once. Fixtures/ZeroSmp/owner: the dev instance's config files the patches touch
    /// and its mods folder listing.</summary>
    [Fact]
    public async Task DevInstanceWithoutRecord_AdoptsPatchesItReflects_AndAppliesTheRestOnce()
    {
        var instDir = ExperiencesService.InstanceDirectory(_tempDir, "zero-smp-1.20.1");
        var gameDir = Path.Combine(instDir, "minecraft");
        CopyTree(Path.Combine(ZeroSmpFixtures, "owner", "config"), Path.Combine(gameDir, "config"));
        Directory.CreateDirectory(Path.Combine(gameDir, "mods"));
        foreach (var jar in File.ReadAllLines(Path.Combine(ZeroSmpFixtures, "owner", "mods.txt")).Where(l => l.Length > 0))
            File.WriteAllText(Path.Combine(gameDir, "mods", jar), "");
        var modsBefore = Directory.GetFiles(Path.Combine(gameDir, "mods")).Length;
        var status = new List<string>();

        int applied = await ExperiencesService.ApplyPendingPatchesAsync(_tempDir, "zero-smp-1.20.1", null, status.Add, default, ExperiencesService.BundledPatchesDirectory);

        Assert.Equal(3, applied);
        Assert.Equal(new[] { "1.0.1", "1.1.0", "1.1.1", "1.1.2", "1.1.3" }, ModpackPatchEngine.GetAppliedPatches(instDir));
        Assert.DoesNotContain(status, s => s.Contains("1.0.1") || s.Contains("1.1.0"));
        Assert.Equal(modsBefore, Directory.GetFiles(Path.Combine(gameDir, "mods")).Length);
        var dh = KeyTable(Path.Combine(gameDir, "config", "DistantHorizons.toml"));
        Assert.Equal("\"DEFAULT\"", dh["client.advanced.debugging.rendererMode"].Single());
        Assert.Contains("mixin.perf.dynamic_resources=false", File.ReadAllLines(Path.Combine(gameDir, "config", "modernfix-mixins.properties")));
        Assert.Equal(0, await ExperiencesService.ApplyPendingPatchesAsync(_tempDir, "zero-smp-1.20.1", null, null, default, ExperiencesService.BundledPatchesDirectory));
    }

    [Fact]
    public void AdoptReflectedPatches_StopsAtTheFirstMissingPatch_AndNeverRunsWithARecord()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        Directory.CreateDirectory(Path.Combine(gameDir, "mods"));
        File.WriteAllText(Path.Combine(gameDir, "mods", "kept.jar"), "");
        ModpackPatch P(string v, PatchMods mods) => new() { PackId = "p", Version = v, Mods = mods };
        var pending = new List<ModpackPatch>
        {
            P("1", new PatchMods { Add = new() { new PatchModFile { FileName = "kept.jar" }, new PatchModFile { FileName = "dropped-later.jar" } } }),
            P("2", new PatchMods { Remove = new() { "kept.jar" } }), // not reflected: kept.jar is still there
            P("3", new PatchMods { Remove = new() { "never-there.jar" } }),
        };

        var rest = ModpackPatchEngine.AdoptReflectedPatches(_tempDir, pending);

        Assert.Equal(new[] { "2", "3" }, rest.Select(p => p.Version));
        Assert.Equal(new[] { "1" }, ModpackPatchEngine.GetAppliedPatches(_tempDir));
        Assert.Equal(3, ModpackPatchEngine.AdoptReflectedPatches(_tempDir, pending).Count); // a record exists now: nothing adopted
    }

    private static void CopyTree(string from, string to)
    {
        foreach (var file in Directory.GetFiles(from, "*", SearchOption.AllDirectories))
        {
            var target = Path.Combine(to, Path.GetRelativePath(from, file));
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            File.Copy(file, target);
        }
    }
}
