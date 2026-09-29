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
    bool corrupt = Environment.GetEnvironmentVariable("LADS_UPDATE_PROBE_CORRUPT") == "1";
    var manager = new UpdateManager(Path.Combine(root, corrupt ? "bad-feed" : "feed"));
    if (!manager.IsInstalled) throw new InvalidOperationException("The probe is not packaged.");
    if (corrupt)
    {
        var messages = new List<string>();
        await new AutoUpdater(new VelopackUpdateBackend(manager)).PollAsync(() => false, messages.Add);
        if (manager.UpdatePendingRestart != null || !messages.Any(message => message.Contains("failed; will retry")))
            throw new InvalidOperationException("Corrupt package was not rejected.");
        File.WriteAllText(Path.Combine(root, "corrupt-result.json"), JsonSerializer.Serialize(new { passed = true, version, messages }));
        return;
    }
    if (version == "0.0.1")
    {
        var updater = new AutoUpdater(new VelopackUpdateBackend(manager));
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
