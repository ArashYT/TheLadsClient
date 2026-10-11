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
var result = Path.Combine(root, "result.json");
try
{
    File.AppendAllText(Path.Combine(root, "probe.log"), $"Started {version}, PID {Environment.ProcessId}\n");
    // LADS_UPDATE_PROBE_CORRUPT: "1" = a damaged package, "unsigned" = signed with a key other than the probe's.
    string? corrupt = Environment.GetEnvironmentVariable("LADS_UPDATE_PROBE_CORRUPT");
    string feed = Path.Combine(root, corrupt == "1" ? "bad-feed" : "feed");
    var manager = new UpdateManager(feed);
    if (!manager.IsInstalled) throw new InvalidOperationException("The probe is not packaged.");
    VelopackUpdateBackend Backend() => new(manager, (_, name) => File.ReadAllBytesAsync(Path.Combine(feed, name)),
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
