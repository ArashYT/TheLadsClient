using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using TheLadsLauncher.Services;
using Xunit;

public sealed class ClientModInstallerTests : IDisposable
{
    private readonly string root = Path.Combine(Path.GetTempPath(), "lads-pack-test-" + Guid.NewGuid().ToString("N"));
    private string Game => Path.Combine(root, "game");
    private string Mods => Path.Combine(Game, "mods");
    private string Destination => Path.Combine(Mods, "lads-testmod.jar");
    private static byte[] Jar(string id, string version = "1")
    {
        using var bytes = new MemoryStream();
        using (var zip = new ZipArchive(bytes, ZipArchiveMode.Create, true))
        using (var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open()))
            writer.Write(JsonSerializer.Serialize(new {schemaVersion=1,id,version}));
        return bytes.ToArray();
    }
    private ClientModInstaller.Entry Manifest(byte[] bytes, string game = "26.2", string? hash = null, string id = "testmod", string? url = null)
    {
        var entry = new ClientModInstaller.Entry("test", "test", "Test Mod", id, "v1", "1", "test.jar",
            url ?? "https://cdn.modrinth.com/data/test/versions/v1/test.jar", hash ?? Convert.ToHexString(SHA512.HashData(bytes)),
            bytes.Length, "MIT", null, "https://modrinth.com/mod/test");
        var dir = Path.Combine(root, "game-mods", "26.2"); Directory.CreateDirectory(dir);
        File.WriteAllText(Path.Combine(dir, "client-mods.json"), JsonSerializer.Serialize(new ClientModInstaller.Manifest(game, new() {entry})));
        return entry;
    }
    private sealed class Handler(byte[] bytes) : HttpMessageHandler
    {
        public int Calls;
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            token.ThrowIfCancellationRequested(); Calls++;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) {Content = new ByteArrayContent(bytes)});
        }
    }
    private Task Install(HttpClient client, CancellationToken token = default) => ClientModInstaller.InstallAsync(root, Game, "26.2", cancellationToken:token, httpClient:client);

    [Fact] public async Task InstallsVerifiedJarThenLaunchesWithoutDownloadingAgain()
    {
        var bytes = Jar("testmod"); Manifest(bytes); var handler = new Handler(bytes); using var client = new HttpClient(handler);
        await Install(client); Assert.Equal(bytes, File.ReadAllBytes(Destination));
        await Install(client); Assert.Equal(1, handler.Calls);
    }
    [Fact] public async Task UpstreamLiteralNewlineInDescriptionDoesNotChangeDownloadedJar()
    {
        using var buffer = new MemoryStream();
        using (var zip = new ZipArchive(buffer, ZipArchiveMode.Create, true))
        using (var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open()))
            writer.Write("{\"schemaVersion\":1,\"id\":\"testmod\",\"version\":\"1\",\"description\":\"first\nsecond\"}");
        var bytes = buffer.ToArray(); Manifest(bytes);
        using var client = new HttpClient(new Handler(bytes));
        await Install(client);
        Assert.Equal(bytes, File.ReadAllBytes(Destination));
    }
    [Fact] public async Task BadHashNeverInstallsAnything()
    {
        var bytes = Jar("testmod"); Manifest(bytes, hash:new string('0',128)); using var client = new HttpClient(new Handler(bytes));
        await Assert.ThrowsAsync<InvalidDataException>(() => Install(client)); Assert.False(File.Exists(Destination));
        Assert.Empty(Directory.GetFiles(Path.Combine(Game,".lads-mod-cache"), "*.tmp"));
    }
    [Fact] public async Task WrongModIdIsRejectedEvenWhenHashMatches()
    {
        var bytes = Jar("anothermod"); Manifest(bytes); using var client = new HttpClient(new Handler(bytes));
        await Assert.ThrowsAsync<InvalidDataException>(() => Install(client)); Assert.False(File.Exists(Destination));
    }
    [Fact] public async Task UserJarIsPreservedOnConflict()
    {
        var bytes = Jar("testmod", "2"); Manifest(bytes); Directory.CreateDirectory(Mods);
        var personal = Path.Combine(Mods,"my-own-copy.jar"); var original = Jar("testmod", "1"); File.WriteAllBytes(personal, original);
        var handler = new Handler(bytes); using var client = new HttpClient(handler);
        await Assert.ThrowsAsync<IOException>(() => Install(client)); Assert.Equal(original,File.ReadAllBytes(personal)); Assert.Equal(0,handler.Calls);
    }
    [Fact] public async Task DisabledManagedModStaysDisabled()
    {
        var bytes = Jar("testmod"); Manifest(bytes); Directory.CreateDirectory(Mods); File.WriteAllBytes(Destination+".disabled",bytes);
        var handler = new Handler(bytes); using var client = new HttpClient(handler);
        await Install(client); Assert.False(File.Exists(Destination)); Assert.Equal(0,handler.Calls);
    }
    [Fact] public async Task ManagedUpgradeKeepsPreviousJar()
    {
        var first = Jar("testmod","1"); Manifest(first); using (var client = new HttpClient(new Handler(first))) await Install(client);
        var second = Jar("testmod","2"); Manifest(second); using (var client = new HttpClient(new Handler(second))) await Install(client);
        Assert.Equal(second,File.ReadAllBytes(Destination));
        Assert.Equal(first, File.ReadAllBytes(Assert.Single(Directory.GetFiles(Cache, "previous-testmod-*.jar"))));
    }
    [Theory]
    [InlineData("1.21.11", "testmod", "https://cdn.modrinth.com/mod.jar")]
    [InlineData("26.2", "../escape", "https://cdn.modrinth.com/mod.jar")]
    [InlineData("26.2", "testmod", "https://example.com/mod.jar")]
    public async Task InvalidManifestRejectedBeforeDownload(string game, string id, string url)
    {
        var bytes=Jar("testmod"); Manifest(bytes,game,id:id,url:url); var handler=new Handler(bytes); using var client=new HttpClient(handler);
        await Assert.ThrowsAsync<InvalidDataException>(()=>Install(client)); Assert.Equal(0,handler.Calls);
    }
    [Fact] public async Task CancellationDoesNotInstall()
    {
        var bytes=Jar("testmod"); Manifest(bytes); using var client=new HttpClient(new Handler(bytes));
        using var source=new CancellationTokenSource(); source.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(()=>Install(client,source.Token)); Assert.False(File.Exists(Destination));
    }
    [Fact] public async Task RemovedManagedModIsArchivedAndUserFilesRemain()
    {
        var bytes=Jar("testmod"); Manifest(bytes); using var client=new HttpClient(new Handler(bytes)); await Install(client);
        var personal=Path.Combine(Mods,"personal.jar"); var own=Jar("personal");File.WriteAllBytes(personal,own);
        File.WriteAllText(Path.Combine(root,"game-mods","26.2","client-mods.json"),JsonSerializer.Serialize(new ClientModInstaller.Manifest("26.2",new())));
        await Install(client);Assert.False(File.Exists(Destination));Assert.Equal(own,File.ReadAllBytes(personal));
        Assert.Single(Directory.GetFiles(Path.Combine(Game,".lads-mod-cache"),"retired-testmod-*.jar"));
    }

    private string Cache => Path.Combine(Game, ".lads-mod-cache");
    private string Receipt => Path.Combine(Cache, "installed.json");
    private string Managed(string id) => Path.Combine(Mods, "lads-" + id + ".jar");
    private static string Sha(byte[] bytes) => Convert.ToHexString(SHA512.HashData(bytes));
    private static ClientModInstaller.Entry Pin(string id, byte[] bytes) =>
        new(id, id, id, id, "v1", "1", id + ".jar",
            "https://cdn.modrinth.com/data/test/versions/v1/" + id + ".jar", Sha(bytes), bytes.Length, "MIT", null, "https://modrinth.com/mod/test");
    private void WritePins(params (string Id, byte[] Bytes)[] pins)
    {
        var dir = Path.Combine(root, "game-mods", "26.2"); Directory.CreateDirectory(dir);
        File.WriteAllText(Path.Combine(dir, "client-mods.json"),
            JsonSerializer.Serialize(new ClientModInstaller.Manifest("26.2", pins.Select(p => Pin(p.Id, p.Bytes)).ToList())));
    }
    private sealed class ScriptedHandler(Func<HttpRequestMessage, HttpResponseMessage> respond) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        { token.ThrowIfCancellationRequested(); return Task.FromResult(respond(request)); }
    }
    private static HttpClient Downloads(params (string Id, byte[] Bytes)[] pins) => new(new ScriptedHandler(request =>
        new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(
            pins.Single(p => request.RequestUri!.AbsolutePath.EndsWith("/" + p.Id + ".jar")).Bytes) }));
    private Task InstallWithStatus(HttpClient client, Action<string> status, CancellationToken token = default) =>
        ClientModInstaller.InstallAsync(root, Game, "26.2", status, token, client);
    private Dictionary<string, string> ReadReceipt() =>
        JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllText(Receipt))!;
    private static byte[] FabricJar(string id, Dictionary<string, object>? depends = null, string[]? provides = null,
        (string Name, byte[] Bytes)[]? nested = null, string environment = "*")
    {
        using var bytes = new MemoryStream();
        using (var zip = new ZipArchive(bytes, ZipArchiveMode.Create, true))
        {
            using (var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open()))
                writer.Write(JsonSerializer.Serialize(new {
                    schemaVersion = 1, id, version = "1.0.0", name = id, environment,
                    depends = depends ?? new(), provides = provides ?? Array.Empty<string>(),
                    jars = (nested ?? Array.Empty<(string Name, byte[] Bytes)>()).Select(j => new { file = j.Name })
                }));
            foreach (var item in nested ?? Array.Empty<(string Name, byte[] Bytes)>())
            {
                using var entry = zip.CreateEntry(item.Name).Open(); entry.Write(item.Bytes);
            }
        }
        return bytes.ToArray();
    }

    [Fact] public async Task AcceptedUpstreamFilenameRemainsDisabledByItsFabricId()
    {
        var bytes = Jar("testmod"); Manifest(bytes); Directory.CreateDirectory(Mods);
        var personal = Path.Combine(Mods, "upstream-1.0.jar"); File.WriteAllBytes(personal, bytes);
        var handler = new Handler(bytes); using var client = new HttpClient(handler);
        await Install(client);
        File.Move(personal, personal + ".disabled");
        await Install(client);
        Assert.Equal(bytes, File.ReadAllBytes(personal + ".disabled"));
        Assert.False(File.Exists(Destination)); Assert.Equal(0, handler.Calls);
    }

    [Fact] public async Task EnabledAndDisabledCopiesAreReportedWithoutDeletingEither()
    {
        var bytes = Jar("testmod"); Manifest(bytes); Directory.CreateDirectory(Mods);
        File.WriteAllBytes(Destination, bytes);
        var disabled = Path.Combine(Mods, "upstream.jar.disabled"); File.WriteAllBytes(disabled, bytes);
        using var client = new HttpClient(new Handler(bytes));
        var error = await Assert.ThrowsAsync<IOException>(() => Install(client));
        Assert.Contains("both enabled and disabled", error.Message);
        Assert.Equal(bytes, File.ReadAllBytes(Destination)); Assert.Equal(bytes, File.ReadAllBytes(disabled));
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task DisabledHardDependencyReportsDependentBeforeAnyCommit(bool upstreamName)
    {
        // The pinned BetterF3 19.0.0 metadata requires cloth-config >=26.2.0.
        var consumer = FabricJar("betterf3", new() { ["cloth-config"] = ">=26.2.0" });
        var dependency = FabricJar("cloth-config");
        WritePins(("betterf3", consumer), ("cloth-config", dependency)); Directory.CreateDirectory(Mods);
        var disabled = Path.Combine(Mods, upstreamName ? "cloth-config-26.2.155.jar.disabled" : "lads-cloth-config.jar.disabled");
        File.WriteAllBytes(disabled, dependency);
        using var client = Downloads(("betterf3", consumer), ("cloth-config", dependency));
        var error = await Assert.ThrowsAsync<InvalidDataException>(() => Install(client));
        Assert.Contains("betterf3", error.Message); Assert.Contains("cloth-config >=26.2.0", error.Message);
        Assert.Contains("disabled", error.Message); Assert.False(File.Exists(Managed("betterf3")));
        Assert.False(File.Exists(Receipt)); Assert.Equal(dependency, File.ReadAllBytes(disabled));
    }

    [Fact] public async Task NestedFabricModulesAndProvidedAliasesSatisfyHardDependencies()
    {
        var api = FabricJar("fabric-api", provides: new[] { "fabric" }, nested: new[] {
            ("META-INF/jars/api.jar", FabricJar("fabric-api-base", provides: new[] { "api_alias" })) });
        var consumer = FabricJar("testmod", new() {
            ["fabric"] = "*", ["fabric-api-base"] = ">=1", ["api_alias"] = new[] { "1", "2" },
            ["minecraft"] = "~26.2-", ["fabricloader"] = ">=0.19.3", ["java"] = ">=25" });
        WritePins(("testmod", consumer), ("fabric-api", api));
        using var client = Downloads(("testmod", consumer), ("fabric-api", api)); await Install(client);
        Assert.Equal(consumer, File.ReadAllBytes(Destination)); Assert.Equal(api, File.ReadAllBytes(Managed("fabric-api")));
    }

    [Fact] public async Task MissingDependencyInsideNestedJarBlocksCommit()
    {
        var bytes = FabricJar("testmod", nested: new[] {
            ("META-INF/jars/child.jar", FabricJar("childmod", new() { ["missinglib"] = ">=2" })) });
        Manifest(bytes); using var client = new HttpClient(new Handler(bytes));
        var error = await Assert.ThrowsAsync<InvalidDataException>(() => Install(client));
        Assert.Contains("childmod", error.Message); Assert.Contains("missinglib >=2", error.Message);
        Assert.False(File.Exists(Destination)); Assert.False(File.Exists(Receipt));
    }

    [Theory]
    [InlineData("1.21.11")]
    [InlineData("26.2")]
    public async Task SupportedFabricRuntimeSuppliesMixinExtras(string minecraft)
    {
        var bytes = FabricJar("dynamic_fps", new() { ["mixinextras"] = ">=0.3.2" });
        var dir = Path.Combine(root, "game-mods", minecraft); Directory.CreateDirectory(dir);
        File.WriteAllText(Path.Combine(dir, "client-mods.json"),
            JsonSerializer.Serialize(new ClientModInstaller.Manifest(minecraft, new() { Pin("dynamic_fps", bytes) })));
        using var client = Downloads(("dynamic_fps", bytes));
        await ClientModInstaller.InstallAsync(root, Game, minecraft, httpClient: client);
        Assert.Equal(bytes, File.ReadAllBytes(Managed("dynamic_fps")));
    }

    [Fact] public async Task DisabledNestedProviderDoesNotSatisfyEnabledConsumer()
    {
        var consumer = FabricJar("testmod", new() { ["nestedlib"] = "*" });
        var provider = FabricJar("provider", nested: new[] { ("nested.jar", FabricJar("nestedlib")) });
        Manifest(consumer); Directory.CreateDirectory(Mods);
        File.WriteAllBytes(Path.Combine(Mods, "provider.jar.disabled"), provider);
        using var client = new HttpClient(new Handler(consumer));
        var error = await Assert.ThrowsAsync<InvalidDataException>(() => Install(client));
        Assert.Contains("nestedlib *, which is disabled", error.Message); Assert.False(File.Exists(Destination));
    }

    [Fact] public async Task ServerOnlyNestedModuleDoesNotAddClientDependencies()
    {
        var bytes = FabricJar("testmod", nested: new[] {
            ("server.jar", FabricJar("servermod", new() { ["serverlib"] = "*" }, environment: "server")) });
        Manifest(bytes); using var client = new HttpClient(new Handler(bytes)); await Install(client);
        Assert.Equal(bytes, File.ReadAllBytes(Destination));
    }

    [Fact] public async Task ServerOnlyNestedModuleCannotSatisfyClientDependency()
    {
        var bytes = FabricJar("testmod", new() { ["servermod"] = "*" }, nested: new[] {
            ("server.jar", FabricJar("servermod", environment: "server")) });
        Manifest(bytes); using var client = new HttpClient(new Handler(bytes));
        var error = await Assert.ThrowsAsync<InvalidDataException>(() => Install(client));
        Assert.Contains("servermod *, which is missing", error.Message); Assert.False(File.Exists(Destination));
    }

    [Fact] public async Task ExcessiveNestedDepthFailsBeforeInstalling()
    {
        var bytes = FabricJar("nestedmod");
        for (var i = 0; i < 10; i++) bytes = FabricJar("nestedmod", nested: new[] { ("nested.jar", bytes) });
        bytes = FabricJar("testmod", nested: new[] { ("nested.jar", bytes) });
        Manifest(bytes); using var client = new HttpClient(new Handler(bytes));
        await Assert.ThrowsAsync<InvalidDataException>(() => Install(client)); Assert.False(File.Exists(Destination));
    }

    [Fact] public async Task CacheHitStillValidatesFabricIdentity()
    {
        var bytes = Jar("wrongmod"); var pin = Manifest(bytes); Directory.CreateDirectory(Cache);
        File.WriteAllBytes(Path.Combine(Cache, pin.Sha512 + ".jar"), bytes);
        var handler = new Handler(bytes); using var client = new HttpClient(handler);
        await Assert.ThrowsAsync<InvalidDataException>(() => Install(client));
        Assert.Equal(0, handler.Calls); Assert.False(File.Exists(Destination));
    }

    [Fact] public async Task RemovingRequiredProviderPreservesExistingPackAndReceipt()
    {
        var provider = FabricJar("provider"); WritePins(("provider", provider));
        using (var client = Downloads(("provider", provider))) await Install(client);
        var personal = Path.Combine(Mods, "personal.jar");
        var consumer = FabricJar("consumer", new() { ["provider"] = "*" }); File.WriteAllBytes(personal, consumer);
        var receipt = File.ReadAllBytes(Receipt); WritePins();
        using var empty = Downloads();
        var error = await Assert.ThrowsAsync<InvalidDataException>(() => Install(empty));
        Assert.Contains("consumer", error.Message);
        Assert.Equal(provider, File.ReadAllBytes(Managed("provider"))); Assert.Equal(receipt, File.ReadAllBytes(Receipt));
        Assert.Equal(consumer, File.ReadAllBytes(personal)); Assert.Empty(Directory.GetFiles(Cache, "retired-*"));
    }

    [Fact] public async Task FailedNewDownloadDoesNotRetireOldPack()
    {
        var old = Jar("testmod"); Manifest(old); using (var client = new HttpClient(new Handler(old))) await Install(client);
        var receipt = File.ReadAllBytes(Receipt); var next = Jar("nextmod"); WritePins(("nextmod", next));
        using var failed = new HttpClient(new ScriptedHandler(_ => new(HttpStatusCode.ServiceUnavailable)));
        await Assert.ThrowsAsync<HttpRequestException>(() => Install(failed));
        Assert.Equal(old, File.ReadAllBytes(Destination)); Assert.Equal(receipt, File.ReadAllBytes(Receipt));
        Assert.Empty(Directory.GetFiles(Cache, "retired-*")); Assert.False(File.Exists(Managed("nextmod")));
    }

    private sealed class InterruptedStream(byte[] bytes, Action interrupt) : Stream
    {
        private bool read;
        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => throw new NotSupportedException();
        public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
        public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken token = default)
        {
            token.ThrowIfCancellationRequested();
            if (read) { interrupt(); token.ThrowIfCancellationRequested(); throw new IOException("Connection interrupted"); }
            read = true; var length = Math.Min(bytes.Length / 2, buffer.Length);
            bytes.AsMemory(0, length).CopyTo(buffer); return ValueTask.FromResult(length);
        }
        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override void Flush() => throw new NotSupportedException();
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task InterruptedBodyPreservesOldPackAndCleansOnlyOwnPartialFile(bool cancel)
    {
        var old = Jar("testmod"); Manifest(old); using (var client = new HttpClient(new Handler(old))) await Install(client);
        var receipt = File.ReadAllBytes(Receipt); var next = Jar("nextmod"); WritePins(("nextmod", next));
        var foreignTemp = Path.Combine(Cache, "foreign.tmp"); File.WriteAllText(foreignTemp, "preserve");
        using var source = new CancellationTokenSource();
        using var failed = new HttpClient(new ScriptedHandler(_ => new(HttpStatusCode.OK) {
            Content = new StreamContent(new InterruptedStream(next, () => { if (cancel) source.Cancel(); })) }));
        if (cancel) await Assert.ThrowsAnyAsync<OperationCanceledException>(() => Install(failed, source.Token));
        else await Assert.ThrowsAsync<IOException>(() => Install(failed));
        Assert.Equal(old, File.ReadAllBytes(Destination)); Assert.Equal(receipt, File.ReadAllBytes(Receipt));
        Assert.Equal("preserve", File.ReadAllText(foreignTemp)); Assert.Single(Directory.GetFiles(Cache, "*.tmp"));
        Assert.Empty(Directory.GetFiles(Cache, "retired-*")); Assert.False(File.Exists(Managed("nextmod")));
    }

    [Fact] public async Task CancellationAtCommitBoundaryLeavesEverythingUnchanged()
    {
        var old = Jar("testmod"); Manifest(old); using (var client = new HttpClient(new Handler(old))) await Install(client);
        var receipt = File.ReadAllBytes(Receipt); var next = Jar("nextmod"); WritePins(("nextmod", next));
        using var source = new CancellationTokenSource(); using var nextClient = Downloads(("nextmod", next));
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => InstallWithStatus(nextClient, message => {
            if (message == "Applying client mod changes...") source.Cancel();
        }, source.Token));
        Assert.Equal(old, File.ReadAllBytes(Destination)); Assert.Equal(receipt, File.ReadAllBytes(Receipt));
        Assert.False(File.Exists(Managed("nextmod"))); Assert.Empty(Directory.GetFiles(Cache, "retired-*"));
    }

    [Fact] public async Task CancellationAfterReplacementCompletesReceiptAndAllowsLaterUpgrade()
    {
        var first = Jar("testmod", "1"); Manifest(first); using (var client = new HttpClient(new Handler(first))) await Install(client);
        var second = Jar("testmod", "2"); var other = Jar("anothermod");
        WritePins(("testmod", second), ("anothermod", other));
        using var source = new CancellationTokenSource(); using var next = Downloads(("testmod", second), ("anothermod", other));
        await InstallWithStatus(next, message => { if (message == "Installed testmod.") source.Cancel(); }, source.Token);
        Assert.True(source.IsCancellationRequested); Assert.Equal(second, File.ReadAllBytes(Destination));
        Assert.Equal(other, File.ReadAllBytes(Managed("anothermod")));
        Assert.Equal(Sha(second), ReadReceipt()["testmod"], ignoreCase: true);
        Assert.Equal(Sha(other), ReadReceipt()["anothermod"], ignoreCase: true);
        var third = Jar("testmod", "3"); Manifest(third);
        using var final = new HttpClient(new Handler(third)); await Install(final);
        Assert.Equal(third, File.ReadAllBytes(Destination));
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task CommitFailureRollsBackRetirementReplacementsAndReceipt(bool failAfterReceipt)
    {
        var old = Jar("oldmod"); var first = Jar("testmod", "1");
        WritePins(("oldmod", old), ("testmod", first));
        using (var client = Downloads(("oldmod", old), ("testmod", first))) await Install(client);
        var receipt = File.ReadAllBytes(Receipt);
        var second = Jar("testmod", "2"); var extra = Jar("extramod");
        WritePins(("testmod", second), ("extramod", extra));
        using var next = Downloads(("testmod", second), ("extramod", extra));
        await Assert.ThrowsAsync<IOException>(() => InstallWithStatus(next, message => {
            if (message == (failAfterReceipt ? "Saved client mod receipt." : "Installed testmod."))
                throw new IOException("Injected commit failure");
        }));
        Assert.Equal(old, File.ReadAllBytes(Managed("oldmod"))); Assert.Equal(first, File.ReadAllBytes(Destination));
        Assert.Equal(receipt, File.ReadAllBytes(Receipt)); Assert.False(File.Exists(Managed("extramod")));
        Assert.Empty(Directory.GetFiles(Mods, "*.tmp"));
        using var retry = Downloads(("testmod", second), ("extramod", extra)); await Install(retry);
        Assert.Equal(second, File.ReadAllBytes(Destination)); Assert.False(File.Exists(Managed("oldmod")));
    }

    [Fact] public async Task ReceiptSharingViolationRollsBackJarReplacement()
    {
        var first = Jar("testmod", "1"); Manifest(first); using (var client = new HttpClient(new Handler(first))) await Install(client);
        var receipt = File.ReadAllBytes(Receipt); var second = Jar("testmod", "2"); Manifest(second);
        using var next = new HttpClient(new Handler(second)); FileStream? locked = null;
        try
        {
            await Assert.ThrowsAsync<IOException>(() => InstallWithStatus(next, message => {
                if (message.StartsWith("Installed ")) locked = new FileStream(Receipt, FileMode.Open, FileAccess.Read, FileShare.None);
            }));
        }
        finally { locked?.Dispose(); }
        Assert.Equal(first, File.ReadAllBytes(Destination)); Assert.Equal(receipt, File.ReadAllBytes(Receipt));
    }

    [Fact] public async Task RollbackConflictPreservesExternalEditAndRecoverableOriginal()
    {
        var first = Jar("testmod", "1"); Manifest(first);
        using (var client = new HttpClient(new Handler(first))) await Install(client);
        var receipt = File.ReadAllBytes(Receipt);
        var second = Jar("testmod", "2"); Manifest(second);
        var edited = Jar("testmod", "external-edit");
        using var next = new HttpClient(new Handler(second));
        var error = await Assert.ThrowsAsync<IOException>(() => InstallWithStatus(next, message => {
            if (message.StartsWith("Installed "))
            {
                File.WriteAllBytes(Destination, edited);
                throw new IOException("Failure after an external edit");
            }
        }));
        Assert.Contains("rollback could not finish", error.Message);
        Assert.Equal(edited, File.ReadAllBytes(Destination));
        Assert.Equal(first, File.ReadAllBytes(Assert.Single(Directory.GetFiles(Cache, "previous-testmod-*.jar"))));
        Assert.Equal(receipt, File.ReadAllBytes(Receipt));
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task VerifiedManagedPinRepairsLegacyMissingOrStaleReceipt(bool stale)
    {
        var bytes = Jar("testmod", "2"); Manifest(bytes); Directory.CreateDirectory(Mods); Directory.CreateDirectory(Cache);
        File.WriteAllBytes(Destination, bytes);
        if (stale) File.WriteAllText(Receipt, JsonSerializer.Serialize(new Dictionary<string, string> { ["testmod"] = Sha(Jar("testmod", "1")) }));
        var handler = new Handler(bytes); using var client = new HttpClient(handler); await Install(client);
        Assert.Equal(0, handler.Calls); Assert.Equal(Sha(bytes), ReadReceipt()["testmod"], ignoreCase: true);
        var next = Jar("testmod", "3"); Manifest(next); using var upgrade = new HttpClient(new Handler(next)); await Install(upgrade);
        Assert.Equal(next, File.ReadAllBytes(Destination));
    }

    [Fact] public async Task ExistingPredictableReceiptTempAndBackupAreNeverOverwritten()
    {
        var first = Jar("testmod", "1"); Manifest(first); using (var client = new HttpClient(new Handler(first))) await Install(client);
        var temp = Receipt + ".tmp"; var backup = Path.Combine(Cache, "previous-testmod.jar");
        File.WriteAllText(temp, "user receipt temp"); File.WriteAllText(backup, "user backup");
        var second = Jar("testmod", "2"); Manifest(second); using var next = new HttpClient(new Handler(second)); await Install(next);
        Assert.Equal("user receipt temp", File.ReadAllText(temp)); Assert.Equal("user backup", File.ReadAllText(backup));
        Assert.Equal(first, File.ReadAllBytes(Assert.Single(Directory.GetFiles(Cache, "previous-testmod-*.jar"))));
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task PredictableHardLinkedWriteTargetsAreNotFollowed(bool backupLink)
    {
        var first = Jar("testmod", "1"); Manifest(first); using (var client = new HttpClient(new Handler(first))) await Install(client);
        var external = Path.Combine(root, "outside-cache.txt"); File.WriteAllText(external, "keep this");
        var link = backupLink ? Path.Combine(Cache, "previous-testmod.jar") : Receipt + ".tmp";
        CreateFileLink(link, external);
        try
        {
            var second = Jar("testmod", "2"); Manifest(second);
            using var client = new HttpClient(new Handler(second)); await Install(client);
            Assert.Equal("keep this", File.ReadAllText(external)); Assert.Equal("keep this", File.ReadAllText(link));
            Assert.Equal(second, File.ReadAllBytes(Destination));
        }
        finally { File.Delete(link); }
    }

    [Theory]
    [InlineData("mods")]
    [InlineData(".lads-mod-cache")]
    [InlineData("ancestor")]
    [InlineData("receipt")]
    public async Task LinkedManagedPathsFailWithoutWritingOutsideProfile(string target)
    {
        var bytes = Jar("testmod"); Manifest(bytes);
        var external = Path.Combine(root, "external"); Directory.CreateDirectory(external); Directory.CreateDirectory(Game);
        string link;
        if (target == "receipt")
        {
            Directory.CreateDirectory(Cache); var file = Path.Combine(external, "receipt.json"); File.WriteAllText(file, "{}");
            link = Receipt; CreateDirectoryLink(link, external);
        }
        else if (target == "ancestor")
        {
            link = Path.Combine(root, "linked-parent"); CreateDirectoryLink(link, external);
        }
        else
        {
            link = Path.Combine(Game, target); CreateDirectoryLink(link, external);
        }
        try
        {
            using var client = new HttpClient(new Handler(bytes));
            if (target == "ancestor")
                await Assert.ThrowsAsync<IOException>(() => ClientModInstaller.InstallAsync(root,
                    Path.Combine(link, "new-profile"), "26.2", httpClient: client));
            else await Assert.ThrowsAsync<IOException>(() => Install(client));
            Assert.False(File.Exists(Destination)); Assert.False(Directory.Exists(Path.Combine(external, "new-profile")));
            if (target == "receipt") Assert.Equal("{}", File.ReadAllText(Path.Combine(external, "receipt.json")));
            else Assert.Empty(Directory.EnumerateFileSystemEntries(external));
        }
        finally
        {
            Directory.Delete(link);
        }
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task UserChangesDuringDownloadArePreservedAndAbortCommit(bool disable)
    {
        var first = Jar("testmod", "1"); Manifest(first); using (var client = new HttpClient(new Handler(first))) await Install(client);
        var receipt = File.ReadAllBytes(Receipt); var second = Jar("testmod", "2"); Manifest(second);
        var changed = Jar("testmod", "user");
        using var client2 = new HttpClient(new Handler(second));
        await Assert.ThrowsAsync<IOException>(() => InstallWithStatus(client2, message => {
            if (message == "Preparing client mod changes...")
            {
                if (disable) File.Move(Destination, Destination + ".disabled");
                else File.WriteAllBytes(Destination, changed);
            }
        }));
        Assert.Equal(receipt, File.ReadAllBytes(Receipt));
        if (disable) { Assert.False(File.Exists(Destination)); Assert.Equal(first, File.ReadAllBytes(Destination + ".disabled")); }
        else Assert.Equal(changed, File.ReadAllBytes(Destination));
    }
    [System.Runtime.InteropServices.DllImport("kernel32.dll", EntryPoint = "CreateHardLinkW", CharSet = System.Runtime.InteropServices.CharSet.Unicode, SetLastError = true)]
    [return: System.Runtime.InteropServices.MarshalAs(System.Runtime.InteropServices.UnmanagedType.Bool)]
    private static extern bool CreateHardLink(string link, string target, IntPtr reserved);

    private static void CreateFileLink(string link, string target)
    {
        // NTFS hard links need no elevation and reproduce truncation through an existing write target.
        if (OperatingSystem.IsWindows())
            Assert.True(CreateHardLink(link, target, IntPtr.Zero),
                "CreateHardLink failed: " + System.Runtime.InteropServices.Marshal.GetLastWin32Error());
        else File.CreateSymbolicLink(link, target);
    }

    private static void CreateDirectoryLink(string link, string target)
    {
        if (!OperatingSystem.IsWindows()) { Directory.CreateSymbolicLink(link, target); return; }
        // Junctions exercise ancestor/reparse checks without requiring Windows symlink privileges.
        var script = "New-Item -ItemType Junction -Path '" + link.Replace("'", "''") +
            "' -Target '" + target.Replace("'", "''") + "' -ErrorAction Stop | Out-Null";
        var start = new System.Diagnostics.ProcessStartInfo("powershell.exe") {
            UseShellExecute = false, CreateNoWindow = true, RedirectStandardError = true, RedirectStandardOutput = true
        };
        start.ArgumentList.Add("-NoProfile"); start.ArgumentList.Add("-NonInteractive");
        start.ArgumentList.Add("-EncodedCommand"); start.ArgumentList.Add(Convert.ToBase64String(Encoding.Unicode.GetBytes(script)));
        using var process = System.Diagnostics.Process.Start(start)!;
        Assert.True(process.WaitForExit(10000), "Timed out creating fixture junction.");
        Assert.True(process.ExitCode == 0, process.StandardError.ReadToEnd());
    }

    public void Dispose()
    {
        var full = Path.GetFullPath(root);
        Assert.Equal(Path.GetFullPath(Path.GetTempPath()).TrimEnd(Path.DirectorySeparatorChar), Path.GetDirectoryName(full));
        Assert.StartsWith("lads-pack-test-", Path.GetFileName(full));
        if (Directory.Exists(full)) Directory.Delete(full, true);
    }
}
