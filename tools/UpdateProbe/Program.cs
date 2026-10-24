using System.Reflection;
using System.Text.Json;
using TheLadsLauncher;
using Velopack;

internal static class Program
{
public static void Main()
{
    VelopackApp.Build().SetAutoApplyOnStartup(false).Run();
    RunAsync().GetAwaiter().GetResult();
}

private static async Task RunAsync()
{
var root = Environment.GetEnvironmentVariable("LADS_UPDATE_PROBE_ROOT")
    ?? throw new InvalidOperationException("Run through Test-AutomaticUpdate.ps1.");
var version = Assembly.GetExecutingAssembly().GetName().Version!.ToString(3);
// LADS_UPDATE_PROBE_STAGE (0.0.3 feeds hold only 0.0.3's full package and its 0.0.2 delta, like a real release):
// "delta" = 0.0.2 -> 0.0.3 through the delta (its feed lacks the full package), "evil-delta" = a tampered delta must end
// on the genuine full package, "old-client" = an installed 0.0.1 (its 0.0.1 full package seeded) must end on the genuine
// full package; Velopack may try the 0.0.2-based delta first and fall back itself, so its LastDownloadWasDelta is only
// recorded. Unset = 0.0.1 -> 0.0.2.
string? stage = Environment.GetEnvironmentVariable("LADS_UPDATE_PROBE_STAGE");
var result = Path.Combine(root, stage == null ? "result.json" : $"{stage}-result.json");
try
{
    File.AppendAllText(Path.Combine(root, "probe.log"), $"Started {version}, PID {Environment.ProcessId}, stage {stage}\n");
    // LADS_UPDATE_PROBE_CORRUPT: "1" = a damaged package, "unsigned" = signed with a key other than the probe's.
    string? corrupt = Environment.GetEnvironmentVariable("LADS_UPDATE_PROBE_CORRUPT");
    string feed = Path.Combine(root, corrupt == "1" ? "bad-feed" : stage switch
    {
        null => "feed",
        "delta" => "feed3-delta",
        "evil-delta" => "feed3-evil",
        "old-client" => "feed3",
        _ => throw new InvalidOperationException($"Unknown stage {stage}.")
    });
    var manager = new UpdateManager(feed);
    if (!manager.IsInstalled) throw new InvalidOperationException("The probe is not packaged.");
    var fullOnly = new UpdateManager(feed, new UpdateOptions { MaximumDeltasBeforeFallback = -1 });
    VelopackUpdateBackend Backend() => new(manager, fullOnly, (_, name) => File.ReadAllBytesAsync(Path.Combine(feed, name)),
        Environment.GetEnvironmentVariable("LADS_UPDATE_PROBE_KEY") ?? throw new InvalidOperationException("No probe key."));
    if (corrupt != null)
    {
        var messages = new List<string>();
        var backend = Backend();
        await new AutoUpdater(backend).PollAsync(() => false, messages.Add);
        if (backend.PendingVersion != null || !messages.Any(message => message.Contains("failed; will retry")))
            throw new InvalidOperationException($"A {(corrupt == "1" ? "corrupt" : "wrongly signed")} package was not rejected.");
        if (corrupt == "unsigned" && Directory.EnumerateFiles(Path.Combine(AppContext.BaseDirectory, "..", "packages"), "*.nupkg").Any())
            throw new InvalidOperationException("The wrongly signed package was left for a later install.");
        File.WriteAllText(Path.Combine(root, $"corrupt-{corrupt}-result.json"), JsonSerializer.Serialize(new { passed = true, version, messages }));
        return;
    }
    if (stage == "delta" && version == "0.0.3")
    {
        if (File.ReadAllText(Path.Combine(root, "user-settings.json")) != "preserve me") throw new InvalidOperationException("User data changed.");
        if (File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "payload.txt")) != "third package") throw new InvalidOperationException("Payload did not update.");
        if (await manager.CheckForUpdatesAsync() != null) throw new InvalidOperationException("Current version repeats the update.");
        // 0.0.2 checked LastDownloadWasDelta before applying and passed it on through AutoUpdater.PrepareRestart's restart args.
        if (!Environment.GetCommandLineArgs().Contains("--probe-delta")) throw new InvalidOperationException("The restart args did not arrive.");
        File.WriteAllText(result, JsonSerializer.Serialize(new { passed = true, version, delta = true, pid = Environment.ProcessId }));
        return;
    }
    if (stage != null)
    {
        var messages = new List<string>();
        var backend = Backend();
        var updater = new AutoUpdater(backend);
        await updater.PollAsync(() => true, messages.Add);   // busy: download and verify, no install yet
        messages.RemoveAll(message => message.StartsWith("Downloading v"));
        if (backend.PendingVersion != "0.0.3") throw new InvalidOperationException($"0.0.3 was not downloaded and verified: {string.Join(" | ", messages)}");
        if (stage != "old-client" && backend.LastDownloadWasDelta != (stage == "delta"))
            throw new InvalidOperationException($"LastDownloadWasDelta is {backend.LastDownloadWasDelta}: {string.Join(" | ", messages)}");
        if (stage == "evil-delta" && !messages.Any(message => message.StartsWith("Delta update did not verify")))
            throw new InvalidOperationException($"The tampered delta was never tried: {string.Join(" | ", messages)}");
        if (stage != "delta")
        {
            // The flag is what the updater believes; the bytes show the genuine full package itself was downloaded.
            const string full = "LadsUpdateProbe-0.0.3-full.nupkg";
            if (!File.ReadAllBytes(Path.Combine(AppContext.BaseDirectory, "..", "packages", full)).AsSpan().SequenceEqual(File.ReadAllBytes(Path.Combine(feed, full))))
                throw new InvalidOperationException("The downloaded package is not the genuine full package.");
            File.WriteAllText(result, JsonSerializer.Serialize(new { passed = true, version = backend.PendingVersion, delta = backend.LastDownloadWasDelta, genuineFull = true, from = version, messages }));
            return;
        }
        updater.PrepareRestart = _ => new[] { "--probe-delta" };
        await updater.PollAsync(() => false, Log);
        throw new InvalidOperationException("Automatic restart unexpectedly returned.");
    }
    if (version == "0.0.1")
    {
        var updater = new AutoUpdater(Backend());
        // Use the actual coordinator, including deferred apply, then automatic restart.
        await updater.PollAsync(() => true, Log);
        if (manager.UpdatePendingRestart == null) throw new InvalidOperationException("Package was not downloaded.");
        File.WriteAllText(Path.Combine(root, "deferred.txt"), "Game/sign-in busy deferred the install.");
        await updater.PollAsync(() => false, Log);
        throw new InvalidOperationException("Automatic restart unexpectedly returned.");
    }
    if (version != "0.0.2") throw new InvalidOperationException("Unexpected installed version.");
    if (File.ReadAllText(Path.Combine(root, "user-settings.json")) != "preserve me") throw new InvalidOperationException("User data changed.");
    if (File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "payload.txt")) != "second package") throw new InvalidOperationException("Payload did not update.");
    if (File.Exists(Path.Combine(AppContext.BaseDirectory, "obsolete.txt"))) throw new InvalidOperationException("Obsolete application file survived.");
    if (ReleaseNotes.Read(Path.Combine(AppContext.BaseDirectory, "release-notes")).Count != 2) throw new InvalidOperationException("Offline notes were not retained.");
    if (await manager.CheckForUpdatesAsync() != null) throw new InvalidOperationException("Current version repeats the update.");
    File.WriteAllText(result, JsonSerializer.Serialize(new { passed = true, version, pid = Environment.ProcessId,
        fullPackageReplaced = true, obsoleteRemoved = true, userDataPreserved = true, releaseNotes = 2, autoRestarted = true }));
}
catch (Exception ex)
{
    File.WriteAllText(result, JsonSerializer.Serialize(new { passed = false, version, error = ex.ToString() }));
    Environment.ExitCode = 1;
}
void Log(string message) => File.AppendAllText(Path.Combine(root, "probe.log"), message + "\n");
}
}
