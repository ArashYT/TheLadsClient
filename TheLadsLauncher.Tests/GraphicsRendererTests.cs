using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Text.Json;
using TheLadsLauncher.Services;
using Xunit;

public sealed class GraphicsRendererTests : IDisposable
{
    private readonly string root = Path.Combine(Path.GetTempPath(), "lads-renderer-test-" + Guid.NewGuid().ToString("N"));
    private string Game => Path.Combine(root, "game");
    private string Options => Path.Combine(Game, "options.txt");
    public GraphicsRendererTests() => Directory.CreateDirectory(Game);

    [Fact] public async Task MigratesOldOpenGlOnceAndPreservesCrashFallback()
    {
        File.WriteAllText(Options, "key_key.attack:key.mouse.left\npreferredGraphicsBackend:\"opengl\"\nfov:0.4\n");
        var blocked = await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan");
        Assert.Contains("iris", blocked);
        Assert.Contains("preferredGraphicsBackend:\"vulkan\"", File.ReadAllText(Options));
        Assert.Contains("fov:0.4", File.ReadAllText(Options));
        File.WriteAllText(Options, File.ReadAllText(Options).Replace("\"vulkan\"", "\"default\""));
        Assert.Empty(await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan"));
        Assert.Contains("\"default\"", File.ReadAllText(Options));
        await GraphicsRenderer.PrepareAsync(Game, "26.3", "OpenGL");
        await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan");
        Assert.Contains("\"vulkan\"", File.ReadAllText(Options));
    }

    [Theory] [InlineData("1.21.1")] [InlineData("1.21.11")]
    public async Task LegacyVersionsKeepTheirOptions(string version)
    {
        File.WriteAllText(Options, "fov:0.1\n");
        Assert.Empty(await GraphicsRenderer.PrepareAsync(Game, version, "Vulkan"));
        Assert.Equal("fov:0.1\n", File.ReadAllText(Options));
        Assert.False(File.Exists(Path.Combine(Game, GraphicsRenderer.StateFile)));
        Assert.False(File.Exists(Path.Combine(Game, GraphicsRenderer.OptionsStateFile)));
    }

    [Theory] [InlineData("\"default\"")] [InlineData("\"opengl\"")]
    public async Task GameSavedBackendSurvivesALegacySaveOfTheSharedOptions(string gameValue)
    {
        await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan");
        File.WriteAllText(Options, "fov:0.6\npreferredGraphicsBackend:" + gameValue + "\n"); // crash fallback or in-game choice
        await GraphicsRenderer.PrepareAsync(Game, "1.21.11", "Vulkan"); // a 1.21.x launch still sees the key...
        File.WriteAllText(Options, "fov:0.6\n");                        // ...and its save drops it
        Assert.Empty(await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan"));
        Assert.Equal("fov:0.6" + Environment.NewLine + "preferredGraphicsBackend:" + gameValue, File.ReadAllText(Options).TrimEnd());
    }

    [Fact] public async Task LauncherChangeAppliedThroughOneProfileReachesAnotherSharingItsOptions()
    {
        using var dir = new TheLadsLauncher.Tests.TestDirectory(); // deletes the shared-folder links it creates
        var paths = new PathService(Path.Combine(dir.Path, "launcher"));
        var profiles = new ProfileService(paths, new SharedContentService(Path.Combine(dir.Path, "global")));
        var first = profiles.CreateProfile("First", "26.2", 25, false, "0.19.5");
        var second = profiles.CreateProfile("Second", "26.3", 25, false, "0.19.5");
        async Task Launch(TheLadsLauncher.Models.LauncherProfile profile, string choice)
        {
            await profiles.PrepareProfileEnvironmentAsync(profile, null);
            await GraphicsRenderer.PrepareAsync(paths.GetProfileDirectory(profile), profile.MinecraftVersion, choice);
            await profiles.SyncProfileToSharedAsync(profile, reconcileServerList: false);
        }
        await Launch(first, "Vulkan"); await Launch(second, "Vulkan");
        await Launch(first, "OpenGL"); // the shared options now say OpenGL
        await Launch(second, "Vulkan"); // Vulkan chosen again: this profile must not keep the OpenGL value
        Assert.Contains("preferredGraphicsBackend:\"vulkan\"", File.ReadAllText(Path.Combine(paths.GetProfileDirectory(second), "options.txt")));
    }

    [Fact] public async Task RunningCopyOfTheProfileOnlyBlocksARendererChange()
    {
        await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan");
        using var java = TheLadsLauncher.Tests.RunningJava.Start(root);
        if (java == null) return; // no Java on this machine
        RunningGameMarker.Write(Game, java.Process.Id, java.Process.StartTime.ToUniversalTime(), new[] { "theladscore" });
        Assert.Contains("iris", await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan")); // another copy: nothing changes
        await Assert.ThrowsAsync<IOException>(() => GraphicsRenderer.PrepareAsync(Game, "26.3", "OpenGL"));
        Assert.Contains("preferredGraphicsBackend:\"vulkan\"", File.ReadAllText(Options));
    }

    [Fact] public async Task ModsPageLockFollowsTheSavedSelectionBeforeTheNextLaunch()
    {
        WriteIrisManifest(FabricJar("iris", "1"));
        await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan");
        string selection = GraphicsRenderer.OpenGl;
        async Task<bool> IrisLocked(ModInventoryService service) =>
            !Assert.Single((await service.BuildAsync(root, Game, "26.3")).Entries.Where(e => e.Id == "iris")).CanToggle;
        Assert.False(await IrisLocked(new ModInventoryService(rendererSelection: () => selection)));
        Assert.True(await IrisLocked(new ModInventoryService())); // without a selection: what the last launch applied
        selection = GraphicsRenderer.Vulkan;
        Assert.True(await IrisLocked(new ModInventoryService(rendererSelection: () => selection)));
    }

    [Fact] public async Task NativeUserChangeToOpenGlIsRespectedUntilLauncherChoiceChanges()
    {
        await GraphicsRenderer.PrepareAsync(Game, "26.2", "Vulkan");
        File.WriteAllText(Options, "preferredGraphicsBackend:\"opengl\"\n");
        Assert.Empty(await GraphicsRenderer.PrepareAsync(Game, "26.2", "Vulkan"));
        Assert.Equal("preferredGraphicsBackend:\"opengl\"\n", File.ReadAllText(Options));
    }

    [Theory] [InlineData("Vulkan", "vulkan")] [InlineData("OpenGL", "opengl")]
    public async Task LegacySharedOptionsRoundTripRestoresMissingBackendWithoutLosingOtherSettings(string choice, string backend)
    {
        await GraphicsRenderer.PrepareAsync(Game, "26.3", choice);
        // A legacy game saves the shared options without unknown 26.x fields, then the launcher
        // copies those newer options back into this profile. Renderer state still remembers choice.
        File.WriteAllText(Options, "fov:0.6\nkey_key.attack:key.mouse.left\n");
        await GraphicsRenderer.PrepareAsync(Game, "1.21.11", choice);
        Assert.DoesNotContain("preferredGraphicsBackend:", File.ReadAllText(Options));
        var suspended = await GraphicsRenderer.PrepareAsync(Game, "26.3", choice);
        string saved = File.ReadAllText(Options);
        Assert.Contains($"preferredGraphicsBackend:\"{backend}\"", saved);
        Assert.Contains("fov:0.6", saved);
        Assert.Contains("key_key.attack:key.mouse.left", saved);
        Assert.Equal(choice == GraphicsRenderer.Vulkan, suspended.Contains("iris"));
    }

    [Theory] [InlineData("\"vulkan\"")] [InlineData("  \"vulkan\"  ")]
    public async Task NativeUserChangeToVulkanSuspendsIrisDespiteRememberedOpenGlChoice(string value)
    {
        await GraphicsRenderer.PrepareAsync(Game, "26.3", "OpenGL");
        string nativeOptions = "fov:0.4\npreferredGraphicsBackend:" + value + "\n";
        File.WriteAllText(Options, nativeOptions);
        var messages = new List<string>();
        var blocked = await GraphicsRenderer.PrepareAsync(Game, "26.3", "OpenGL", messages.Add);
        Assert.Contains("iris", blocked);
        Assert.Equal(nativeOptions, File.ReadAllText(Options));
        Assert.True(GraphicsRenderer.ReadState(Game)!.Vulkan);
        Assert.Contains(messages, message => message.StartsWith("Graphics: Vulkan preferred;", StringComparison.Ordinal));
    }

    [Theory] [InlineData(false)] [InlineData(true)]
    public async Task SuspendingAndRestoringIrisPreservesExplicitDisabledChoice(bool initiallyDisabled)
    {
        byte[] jar;
        using (var bytes = new MemoryStream())
        {
            using (var zip = new ZipArchive(bytes, ZipArchiveMode.Create, true))
            using (var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open()))
                writer.Write("{\"schemaVersion\":1,\"id\":\"iris\",\"name\":\"Iris\",\"version\":\"1\"}");
            jar = bytes.ToArray();
        }
        var bundle = Path.Combine(root, "game-mods", "26.3"); Directory.CreateDirectory(bundle);
        var entry = new ClientModInstaller.Entry("iris-project", "iris", "Iris", "iris", "test", "1", "iris.jar",
            "https://cdn.modrinth.com/data/test/versions/test/iris.jar", Convert.ToHexString(SHA512.HashData(jar)), jar.Length,
            "LGPL-3.0", null, "https://modrinth.com/mod/iris");
        File.WriteAllText(Path.Combine(bundle, "client-mods.json"), JsonSerializer.Serialize(new ClientModInstaller.Manifest("26.3", new() {entry})));
        if (initiallyDisabled) await ModPreferences.UpdateAsync(Game, j => ModPreferences.SetMod(j, "iris", false, "iris-project"));
        using var http = new HttpClient(new JarHandler(jar));
        await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan");
        await ClientModInstaller.InstallAsync(root, Game, "26.3", httpClient: http);
        Assert.False(File.Exists(Path.Combine(Game, "mods", "iris.jar")));
        Assert.Equal(!initiallyDisabled, ModPreferences.Load(Game).GetEnabled("iris", "iris-project"));
        await GraphicsRenderer.PrepareAsync(Game, "26.3", "OpenGL");
        await ClientModInstaller.InstallAsync(root, Game, "26.3", httpClient: http);
        Assert.Equal(!initiallyDisabled, File.Exists(Path.Combine(Game, "mods", "iris.jar")));
        if (!initiallyDisabled)
        {
            await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan");
            await ClientModInstaller.InstallAsync(root, Game, "26.3", httpClient: http);
            Assert.True(File.Exists(Path.Combine(Game, "mods", "iris.jar.disabled")));
            var inventory = await new ModInventoryService().BuildAsync(root, Game, "26.3");
            var iris = Assert.Single(inventory.Entries.Where(e => e.Id == "iris"));
            Assert.False(iris.CanToggle); Assert.Contains("Requires OpenGL", iris.Note);
            await ModPreferences.UpdateAsync(Game, j => ModPreferences.SetMod(j, "iris", false, "iris-project"));
            await GraphicsRenderer.PrepareAsync(Game, "26.3", "OpenGL");
            await ClientModInstaller.InstallAsync(root, Game, "26.3", httpClient: http);
            Assert.True(File.Exists(Path.Combine(Game, "mods", "iris.jar.disabled")));
        }
    }

    [Fact] public async Task CancellationDoesNotWriteRendererSettings()
    {
        using var cts = new CancellationTokenSource(); cts.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan", token: cts.Token));
        Assert.False(File.Exists(Options));
        Assert.False(File.Exists(Path.Combine(Game, GraphicsRenderer.StateFile)));
    }

    [Theory] [InlineData(false)] [InlineData(true)]
    public async Task CustomAddonWithDisabledBackupRetainsOriginalChoiceAcrossRendererSwitch(bool initiallyEnabled)
    {
        byte[] iris = FabricJar("iris", "1");
        WriteIrisManifest(iris);
        string mods = Path.Combine(Game, "mods"); Directory.CreateDirectory(mods);
        byte[] current = FabricJar("shader_addon", "2", "iris"), backup = FabricJar("shader_addon", "1", "iris");
        // Create and name the backup first so enumeration cannot accidentally hide a wrong-version restore.
        File.WriteAllBytes(Path.Combine(mods, "addon-0-old.jar.disabled"), backup);
        File.WriteAllBytes(Path.Combine(mods, "addon-9-current.jar" + (initiallyEnabled ? "" : ".disabled")), current);
        using var http = new HttpClient(new JarHandler(iris));

        await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan");
        await ClientModInstaller.InstallAsync(root, Game, "26.3", httpClient: http);
        Assert.Equal(initiallyEnabled, ModPreferences.Load(Game).GetEnabled("shader_addon", null));
        Assert.Empty(Directory.EnumerateFiles(mods, "addon-*.jar"));
        Assert.Equal(2, Directory.EnumerateFiles(mods, "addon-*.jar.disabled").Count());
        Assert.Equal("addon-0-old.jar.disabled", Path.GetFileName(Directory.EnumerateFiles(mods, "addon-*.jar.disabled").First()));

        // A second Vulkan launch must keep the remembered intent despite every copy now being disabled.
        await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan");
        await ClientModInstaller.InstallAsync(root, Game, "26.3", httpClient: http);
        Assert.Equal(initiallyEnabled, ModPreferences.Load(Game).GetEnabled("shader_addon", null));
        using (var state = JsonDocument.Parse(File.ReadAllText(ModPreferences.PathFor(Game))))
        {
            var entry = state.RootElement.GetProperty("mods").GetProperty("shader_addon");
            Assert.Equal(initiallyEnabled, entry.TryGetProperty("rendererSuspendedJar", out var suspended));
            if (initiallyEnabled)
            {
                Assert.Equal("addon-9-current.jar", suspended.GetProperty("fileName").GetString());
                Assert.Equal(Convert.ToHexString(SHA512.HashData(current)), suspended.GetProperty("sha512").GetString(), ignoreCase: true);
            }
        }
        await GraphicsRenderer.PrepareAsync(Game, "26.3", "OpenGL");
        await ClientModInstaller.InstallAsync(root, Game, "26.3", httpClient: http);
        Assert.Equal(initiallyEnabled ? 1 : 0, Directory.EnumerateFiles(mods, "addon-*.jar").Count());
        if (initiallyEnabled)
        {
            var enabled = Assert.Single(Directory.EnumerateFiles(mods, "addon-*.jar"));
            Assert.Equal("addon-9-current.jar", Path.GetFileName(enabled));
            Assert.Equal(current, File.ReadAllBytes(enabled));
        }
        Assert.Equal(backup, File.ReadAllBytes(Path.Combine(mods, "addon-0-old.jar.disabled")));
        using (var restored = JsonDocument.Parse(File.ReadAllText(ModPreferences.PathFor(Game))))
            Assert.False(restored.RootElement.GetProperty("mods").GetProperty("shader_addon").TryGetProperty("rendererSuspendedJar", out _));
        Assert.Equal(2, Directory.EnumerateFiles(mods, "addon-*.*").Count());
        var hashes = Directory.EnumerateFiles(mods, "addon-*.*").Select(path => Convert.ToHexString(SHA512.HashData(File.ReadAllBytes(path)))).Order().ToArray();
        Assert.Equal(new[] { Convert.ToHexString(SHA512.HashData(current)), Convert.ToHexString(SHA512.HashData(backup)) }.Order().ToArray(), hashes);
    }

    [Theory] [InlineData(false)] [InlineData(true)]
    public async Task MissingOrChangedSuspendedCopyNeverEnablesAnOlderBackup(bool replace)
    {
        byte[] iris = FabricJar("iris", "1"); WriteIrisManifest(iris);
        string mods = Path.Combine(Game, "mods"); Directory.CreateDirectory(mods);
        byte[] backup = FabricJar("shader_addon", "1", "iris"), current = FabricJar("shader_addon", "2", "iris");
        string olderPath = Path.Combine(mods, "addon-0-old.jar.disabled");
        string currentPath = Path.Combine(mods, "addon-9-current.jar");
        File.WriteAllBytes(olderPath, backup); File.WriteAllBytes(currentPath, current);
        using var http = new HttpClient(new JarHandler(iris));
        await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan");
        await ClientModInstaller.InstallAsync(root, Game, "26.3", httpClient: http);
        byte[] changed = FabricJar("shader_addon", "3", "iris");
        if (replace) File.WriteAllBytes(currentPath + ".disabled", changed);
        else File.Delete(currentPath + ".disabled");
        string preferences = File.ReadAllText(ModPreferences.PathFor(Game));

        await GraphicsRenderer.PrepareAsync(Game, "26.3", "OpenGL");
        var failure = await Assert.ThrowsAsync<IOException>(() => ClientModInstaller.InstallAsync(root, Game, "26.3", httpClient: http));
        Assert.Contains("previously active 'addon-9-current.jar' was changed or removed", failure.Message);
        Assert.Empty(Directory.EnumerateFiles(mods, "addon-*.jar"));
        Assert.Equal(backup, File.ReadAllBytes(olderPath));
        if (replace) Assert.Equal(changed, File.ReadAllBytes(currentPath + ".disabled"));
        Assert.Equal(preferences, File.ReadAllText(ModPreferences.PathFor(Game)));
    }

    [Fact] public async Task NeverDownloadedSuspendedShaderModIsNotAPendingDownload()
    {
        byte[] iris = FabricJar("iris", "1"); WriteIrisManifest(iris);
        await GraphicsRenderer.PrepareAsync(Game, "26.3", "Vulkan");
        using var http = new HttpClient(new JarHandler(iris));
        await ClientModInstaller.InstallAsync(root, Game, "26.3", httpClient: http);
        var inventory = await new ModInventoryService().BuildAsync(root, Game, "26.3");
        var entry = Assert.Single(inventory.Entries.Where(e => e.Id == "iris"));
        Assert.False(entry.RequestedEnabled); Assert.False(entry.CanToggle);
        Assert.Equal(ModEntryStatus.NotDownloaded, entry.Status);
        Assert.Equal(0, inventory.Counts.PendingDownloads);
        Assert.True(ModPreferences.Load(Game).GetEnabled("iris", "iris-project"));
    }

    private void WriteIrisManifest(byte[] jar)
    {
        var bundle = Path.Combine(root, "game-mods", "26.3"); Directory.CreateDirectory(bundle);
        var entry = new ClientModInstaller.Entry("iris-project", "iris", "Iris", "iris", "test", "1", "iris.jar",
            "https://cdn.modrinth.com/data/test/versions/test/iris.jar", Convert.ToHexString(SHA512.HashData(jar)), jar.Length,
            "LGPL-3.0", null, "https://modrinth.com/mod/iris");
        File.WriteAllText(Path.Combine(bundle, "client-mods.json"), JsonSerializer.Serialize(new ClientModInstaller.Manifest("26.3", new() { entry })));
    }

    private static byte[] FabricJar(string id, string version, string? dependency = null)
    {
        using var bytes = new MemoryStream();
        using (var zip = new ZipArchive(bytes, ZipArchiveMode.Create, true))
        using (var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open()))
            writer.Write(JsonSerializer.Serialize(new { schemaVersion = 1, id, name = id, version,
                depends = dependency == null ? new Dictionary<string, string>() : new Dictionary<string, string> { [dependency] = "*" } }));
        return bytes.ToArray();
    }

    private sealed class JarHandler(byte[] bytes) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token) =>
            Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) {Content = new ByteArrayContent(bytes)});
    }
    public void Dispose()
    {
        Assert.Equal(Path.GetFullPath(Path.GetTempPath()).TrimEnd(Path.DirectorySeparatorChar), Path.GetDirectoryName(root));
        Assert.StartsWith("lads-renderer-test-", Path.GetFileName(root));
        Directory.Delete(root, true);
    }
}
