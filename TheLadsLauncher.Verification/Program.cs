using System.Diagnostics;
using System.Text.Json.Nodes;
using CmlLib.Core;
using CmlLib.Core.ProcessBuilder;
using TheLadsLauncher.Services;

// Explicit maintenance mode: prepare a profile's pinned pack without starting a game or reading accounts.
if (args.Length == 4 && args[0] == "--install-pack")
{
    string packVersion = args[1];
    if (packVersion is not ("1.21.1" or "1.21.11" or "26.2" or "26.3")) throw new ArgumentException("Unsupported pinned pack version.");
    using var installTimeout = new CancellationTokenSource(TimeSpan.FromMinutes(10));
    await BundledModInstaller.InstallAsync(Path.GetFullPath(args[2]), Path.GetFullPath(args[3]), packVersion, installTimeout.Token);
    await ClientModInstaller.InstallAsync(Path.GetFullPath(args[2]), Path.GetFullPath(args[3]), packVersion, Console.WriteLine, installTimeout.Token);
    Console.WriteLine("Profile pack prepared. No game or account sign-in was started.");
    return;
}

if (args.Length == 1 && args[0] == "--api")
{
    foreach (var property in typeof(MinecraftPath).GetProperties()) Console.WriteLine($"{property.Name}: {property.PropertyType} writable={property.CanWrite}");
    return;
}
if (args.Length is < 2 or > 3 || args[0] is not ("1.21.1" or "1.21.11" or "26.2" or "26.3") || (args.Length == 3 && args[2] is not ("--title" or "--settings" or "--pack-smoke")))
    throw new ArgumentException("Usage: dotnet run --project TheLadsLauncher.Verification -- <1.21.1|1.21.11|26.2|26.3> <repository root> [--title|--settings|--pack-smoke]");
bool packSmoke = args.Length == 3 && args[2] == "--pack-smoke";
bool titleVerification = args.Length == 3 && args[2] == "--title";
bool settingsVerification = args.Length == 3 && args[2] == "--settings";
bool autoWorldVerification = titleVerification && (args[0] is "26.2" or "26.3") && Environment.GetEnvironmentVariable("LADS_VERIFY_AUTO_WORLD") == "1";
bool requestedFeaturesOnly = autoWorldVerification && Environment.GetEnvironmentVariable("LADS_VERIFY_REQUESTS_ONLY") == "1";
bool renderScaleVerification = !requestedFeaturesOnly && titleVerification && (autoWorldVerification || Environment.GetEnvironmentVariable("LADS_VERIFY_RENDER_SCALE") == "1");
string version = args[0];
bool nativePortsVerification = autoWorldVerification || ((version is "26.2" or "26.3") && Environment.GetEnvironmentVariable("LADS_VERIFY_NATIVE_PORTS") == "1");
bool menuCaptureVerification = autoWorldVerification && Environment.GetEnvironmentVariable("LADS_VERIFY_CAPTURE_MENU") == "1";
bool hudCaptureVerification = autoWorldVerification && Environment.GetEnvironmentVariable("LADS_VERIFY_CAPTURE_HUD") == "1";
string root = Path.GetFullPath(args[1]);
string directory = Path.Combine(root, "artifacts", "verification", packSmoke ? version + "-instance-pack" : settingsVerification ? version + "-settings" : titleVerification ? version + "-title" : version);
Directory.CreateDirectory(directory);
string stopRequest = Path.Combine(directory, ".lads-qa-stop");
if (autoWorldVerification && File.Exists(stopRequest)) File.Delete(stopRequest);
string menuCaptureRequest = Path.Combine(directory, ".lads-qa-capture-menu");
if (autoWorldVerification && File.Exists(menuCaptureRequest)) File.Delete(menuCaptureRequest);
string hudCaptureRequest = Path.Combine(directory, ".lads-qa-capture-hud");
if (autoWorldVerification && File.Exists(hudCaptureRequest)) File.Delete(hudCaptureRequest);
using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(10));
var ct = timeout.Token;
var path = new MinecraftPath(directory);
string cache = Path.Combine(root, "artifacts", "verification", "shared-cache");
path.Assets = Path.Combine(cache, "assets");
path.Library = Path.Combine(cache, "libraries");
var launcher = new MinecraftLauncher(path);
long lastProgress = 0;
launcher.FileProgressChanged += (_, progress) =>
{
    long now = Environment.TickCount64;
    if (now - Interlocked.Read(ref lastProgress) < 3000) return;
    Interlocked.Exchange(ref lastProgress, now);
    Console.WriteLine($"Install: {progress.ProgressedTasks}/{progress.TotalTasks}");
};
await BundledModInstaller.InstallAsync(Path.Combine(root, "TheLadsLauncher"), directory, version, ct);
string packSource = Path.Combine(root, "TheLadsLauncher");
if (nativePortsVerification)
{
    var manifest = JsonNode.Parse(await File.ReadAllTextAsync(Path.Combine(packSource, "game-mods", version, "client-mods.json"), ct))!.AsObject();
    var mods = manifest["mods"]!.AsArray();
    for (int index = mods.Count - 1; index >= 0; index--)
        if (mods[index]?["modId"]?.GetValue<string>() is "paperdoll" or "autoreconnectrf" or "tabtweaks" or "screenshot_viewer" or "clumps") mods.RemoveAt(index);
    packSource = Path.Combine(root, "artifacts", "verification", "native-ports-pack");
    string stagedManifest = Path.Combine(packSource, "game-mods", version, "client-mods.json");
    Directory.CreateDirectory(Path.GetDirectoryName(stagedManifest)!);
    await File.WriteAllTextAsync(stagedManifest, manifest.ToJsonString(new() { WriteIndented = true }), ct);
    Console.WriteLine("Native port QA: staged pack without replaced Paper Doll, AutoReconnect, TabTweaks, Screenshot Viewer and Clumps upstream jars. Production manifest preserved.");
}
await ClientModInstaller.InstallAsync(packSource, directory, version, Console.WriteLine, ct);
var java = new JavaService(new PathService(Path.Combine(root, "artifacts", "verification", "runtime-data")));
string javaPath = await java.EnsureJavaAsync(GameVersionPolicy.GetRequiredJavaMajor(version), cancellationToken: ct);
Console.WriteLine($"Java: {java.GetJavaMajorVersion(javaPath)}; Minecraft: {version}");
using var http = new HttpClient();
var fabric = new CmlLib.Core.ModLoaders.FabricMC.FabricInstaller(http);
string loaderVersion = "0.19.5";
string id = await fabric.Install(version, loaderVersion, path);
if (id != $"fabric-loader-{loaderVersion}-{version}") throw new InvalidOperationException("Fabric selected a different version.");
var session = AccountIdentity.CreateOfflineSession("LadsQA");
await AccountExportService.WriteLaunchAsync(directory, session, true, new[] { new AccountSummary(session.Username!, session.UUID!, "offline") });
await File.WriteAllTextAsync(Path.Combine(directory, "options.txt"), "fullscreen:false\nmaxFps:144\nrenderDistance:4\nsimulationDistance:5\nguiScale:2\ntutorialStep:none\n", ct);
Console.WriteLine("Installing production dependencies...");
using var process = await launcher.InstallAndBuildProcessAsync(id, new MLaunchOption
{
    Session = session, JavaPath = javaPath, MaximumRamMb = 2048, MinimumRamMb = 256,
    FullScreen = false, ScreenWidth = titleVerification ? 1280 : 960, ScreenHeight = titleVerification ? 720 : 600
}, ct);
// Probe public settings APIs only in this isolated QA process, when its Lads menu opens.
if (process.StartInfo.ArgumentList.Count > 0) process.StartInfo.ArgumentList.Insert(0, "-Dthelads.verifyIntegrations=true");
else process.StartInfo.Arguments = "-Dthelads.verifyIntegrations=true " + process.StartInfo.Arguments;
if (!autoWorldVerification)
{
    if (process.StartInfo.ArgumentList.Count > 0) process.StartInfo.ArgumentList.Insert(0, "-Dthelads.verifyInput=true");
    else process.StartInfo.Arguments = "-Dthelads.verifyInput=true " + process.StartInfo.Arguments;
}
else
{
    if (process.StartInfo.ArgumentList.Count > 0) process.StartInfo.ArgumentList.Insert(0, "-Dthelads.verifyAutoWorld=true");
    else process.StartInfo.Arguments = "-Dthelads.verifyAutoWorld=true " + process.StartInfo.Arguments;
    if (process.StartInfo.ArgumentList.Count > 0) process.StartInfo.ArgumentList.Insert(0, "-Dthelads.verifyDurabilityTooltip=true");
    else process.StartInfo.Arguments = "-Dthelads.verifyDurabilityTooltip=true " + process.StartInfo.Arguments;
    string featureFlag = requestedFeaturesOnly ? "-Dthelads.verifyRequestedFeaturesOnly=true" : "-Dthelads.verifyCrosshair=true";
    if (process.StartInfo.ArgumentList.Count > 0) process.StartInfo.ArgumentList.Insert(0, featureFlag);
    else process.StartInfo.Arguments = featureFlag + " " + process.StartInfo.Arguments;
}
if (titleVerification && !autoWorldVerification)
{
    if (process.StartInfo.ArgumentList.Count > 0) process.StartInfo.ArgumentList.Insert(0, "-Dthelads.verifyKillBannerPreview=true");
    else process.StartInfo.Arguments = "-Dthelads.verifyKillBannerPreview=true " + process.StartInfo.Arguments;
}
if (settingsVerification)
{
    if (process.StartInfo.ArgumentList.Count > 0) process.StartInfo.ArgumentList.Insert(0, "-Dthelads.verifyAllIntegrationWrites=true");
    else process.StartInfo.Arguments = "-Dthelads.verifyAllIntegrationWrites=true " + process.StartInfo.Arguments;
    if (process.StartInfo.ArgumentList.Count > 0) process.StartInfo.ArgumentList.Insert(0, "-Dthelads.verifyIntegrationWrites=true");
    else process.StartInfo.Arguments = "-Dthelads.verifyIntegrationWrites=true " + process.StartInfo.Arguments;
}
if (renderScaleVerification)
{
    if (process.StartInfo.ArgumentList.Count > 0) process.StartInfo.ArgumentList.Insert(0, "-Dthelads.verifyRenderScale=true");
    else process.StartInfo.Arguments = "-Dthelads.verifyRenderScale=true " + process.StartInfo.Arguments;
}
if (nativePortsVerification)
{
    if (process.StartInfo.ArgumentList.Count > 0) process.StartInfo.ArgumentList.Insert(0, "-Dthelads.verifyBackgroundPolicies=true");
    else process.StartInfo.Arguments = "-Dthelads.verifyBackgroundPolicies=true " + process.StartInfo.Arguments;
}
process.StartInfo.UseShellExecute = false;
process.StartInfo.CreateNoWindow = true;
process.StartInfo.RedirectStandardOutput = true;
process.StartInfo.RedirectStandardError = true;
process.StartInfo.Environment["THELADS_DIR"] = directory;
string logPath = Path.Combine(directory, "production-smoke.log");
using var log = new StreamWriter(logPath) { AutoFlush = true };
var logGate = new object();
bool initialized = false;
bool settingsProbePassed = false;
bool nativeProbeFailed = false;
bool renderScaleProbePassed = false;
bool menuCaptureRequested = false;
bool hudCaptureRequested = false;
var nativeTitleProbes = new System.Collections.Concurrent.ConcurrentDictionary<string, byte>(StringComparer.Ordinal);
string[] requiredTitleProbes = ["Lads raised title probe END:",
    "Lads native reconnect probe END:", "Lads background policy probe END:", "Lads native SignalLoss probe END:", "Lads narrator probe END:"];
string[] requiredWorldProbes = ["Lads native feature probe END:", "Lads food render probe END:", "Lads food JEI probe END:",
    "Lads paper doll probe END:", "Lads food server sync END:", "Lads render scale probe END:", "Lads world capture END:",
    "Lads durability tooltip probe END:", "Lads tab tweaks probe END:", "Lads clumps server probe END:", "Lads native screenshots probe END:", "Lads native crosshair probe END:"];
if (requestedFeaturesOnly)
{
    requiredWorldProbes = ["Lads native feature probe END:", "Lads world capture END:"];
    Console.WriteLine("Focused requested-feature verification: legacy crosshair/render-scale suites are not part of this run.");
}
void WriteLine(object sender, DataReceivedEventArgs e)
{
    if (e.Data == null) return;
    lock (logGate)
    {
        log.WriteLine(e.Data);
        if (e.Data.Contains($"TheLadsCore {version} initialized successfully")) initialized = true;
        if (e.Data.Contains("Lads integration write probe END:") && e.Data.Contains(", 0 failed")) settingsProbePassed = true;
        if (e.Data.Contains("Lads native feature probe FAILED")) nativeProbeFailed = true;
        if (e.Data.Contains("Lads render scale probe FAILED")) nativeProbeFailed = true;
        if (e.Data.Contains("Lads paper doll probe FAILED") || e.Data.Contains("Lads native reconnect probe FAILED")
            || e.Data.Contains("Lads dynamic FPS probe FAILED") || e.Data.Contains("Lads background policy probe FAILED")
            || e.Data.Contains("Lads auto-world QA FAILED") || e.Data.Contains("Lads durability tooltip probe FAILED")
            || e.Data.Contains("Lads native SignalLoss probe FAILED") || e.Data.Contains("Lads tab tweaks probe FAILED")
            || e.Data.Contains("Lads narrator probe FAILED") || e.Data.Contains("Lads native screenshots probe FAILED")
            || e.Data.Contains("Lads native crosshair probe FAILED")) nativeProbeFailed = true;
        foreach (string marker in requiredTitleProbes)
            if (e.Data.Contains(marker) && e.Data.Contains("0 failed")) nativeTitleProbes.TryAdd(marker, 0);
        foreach (string marker in requiredWorldProbes)
            if (e.Data.Contains(marker) && (e.Data.Contains("0 failed") || marker == "Lads food server sync END:")) nativeTitleProbes.TryAdd(marker, 0);
        if (e.Data.Contains("Lads menu capture END:") && e.Data.Contains("0 failed")) nativeTitleProbes.TryAdd("Lads menu capture END:", 0);
        if (e.Data.Contains("Lads HUD capture END:") && e.Data.Contains("0 failed")) nativeTitleProbes.TryAdd("Lads HUD capture END:", 0);
        if (e.Data.Contains("Lads HUD editor probe END:") && e.Data.Contains("0 failed")) nativeTitleProbes.TryAdd("Lads HUD editor probe END:", 0);
        if (e.Data.Contains("Lads render scale probe END:") && e.Data.Contains(", 0 failed")) renderScaleProbePassed = true;
        if (e.Data.Contains("Lads integration write")) Console.WriteLine(e.Data);
        if (e.Data.Contains("probe END:") || e.Data.Contains("probe FAILED")) Console.WriteLine(e.Data);
        if (e.Data.Contains("ERROR") || e.Data.Contains("Exception") || e.Data.Contains("Initializing TheLadsCore")) Console.WriteLine(e.Data);
    }
}
process.OutputDataReceived += WriteLine;
process.ErrorDataReceived += WriteLine;
var stopwatch = Stopwatch.StartNew();
process.Start();
process.BeginOutputReadLine();
process.BeginErrorReadLine();
Console.WriteLine($"Started QA game PID {process.Id}. Log: {logPath}");
bool windowFound = false;
try
{
    while (stopwatch.Elapsed < TimeSpan.FromSeconds(titleVerification ? 540 : settingsVerification ? 120 : 60) && !process.HasExited)
    {
        process.Refresh();
        if (!windowFound && process.MainWindowHandle != IntPtr.Zero)
        {
            windowFound = true;
            Console.WriteLine($"Game window detected at {stopwatch.Elapsed.TotalSeconds:F1}s; waiting for UI verification.");
        }
        await Task.Delay(500, ct);
        if (autoWorldVerification && requiredTitleProbes.Concat(requiredWorldProbes).All(nativeTitleProbes.ContainsKey))
        {
            bool menuDone = !menuCaptureVerification || nativeTitleProbes.ContainsKey("Lads menu capture END:");
            bool hudDone = !hudCaptureVerification || (nativeTitleProbes.ContainsKey("Lads HUD capture END:") && nativeTitleProbes.ContainsKey("Lads HUD editor probe END:"));
            if (menuDone && hudDone) break;
            if (!menuDone && !menuCaptureRequested)
            {
                await File.WriteAllTextAsync(menuCaptureRequest, "Capture the native Lads mods menu after all world probes pass.", ct);
                menuCaptureRequested = true;
                Console.WriteLine("World probes passed; requesting an actual Lads menu frame from the QA game.");
            }
            if (menuDone && !hudDone && !hudCaptureRequested)
            {
                await File.WriteAllTextAsync(hudCaptureRequest, "Verify the native HUD editor and capture its completed framebuffer.", ct);
                hudCaptureRequested = true;
                Console.WriteLine("Requesting native HUD editor interaction checks and actual frame capture.");
            }
        }
        // Allow independent world/GPU probes to finish after a restored-state assertion fails.
        // The run still fails below; collecting their evidence avoids hiding subsequent defects.
        if (nativeProbeFailed && (!autoWorldVerification || nativeTitleProbes.ContainsKey("Lads native screenshots probe END:")
            || stopwatch.Elapsed > TimeSpan.FromSeconds(90))) break;
        if (!titleVerification && windowFound && initialized && (!settingsVerification || settingsProbePassed)
            && (!nativePortsVerification || requiredTitleProbes.All(nativeTitleProbes.ContainsKey))
            && stopwatch.Elapsed > TimeSpan.FromSeconds(20)) break;
    }
    if (process.HasExited) Console.WriteLine($"Game exit: {process.ExitCode}");
    if (!windowFound || !initialized) throw new InvalidOperationException("Game did not reach a window and successful Core initialization. Inspect production-smoke.log.");
    if (process.HasExited && process.ExitCode != 0) throw new InvalidOperationException("Game exited with an error.");
    if (settingsVerification && !settingsProbePassed) throw new InvalidOperationException("Runtime settings write probe did not pass. Inspect production-smoke.log.");
    if (nativeProbeFailed) throw new InvalidOperationException("Native feature runtime probe failed. Inspect production-smoke.log.");
    if (nativePortsVerification && requiredTitleProbes.Any(marker => !nativeTitleProbes.ContainsKey(marker)))
        throw new InvalidOperationException("Required native title probes did not all pass: " + string.Join(", ", requiredTitleProbes.Where(marker => !nativeTitleProbes.ContainsKey(marker))));
    if (renderScaleVerification && !renderScaleProbePassed) throw new InvalidOperationException("Render scale GPU/world probe did not pass. Inspect production-smoke.log.");
    if (autoWorldVerification && requiredWorldProbes.Any(marker => !nativeTitleProbes.ContainsKey(marker)))
        throw new InvalidOperationException("Required world probes did not all pass: " + string.Join(", ", requiredWorldProbes.Where(marker => !nativeTitleProbes.ContainsKey(marker))));
    if (menuCaptureVerification && !nativeTitleProbes.ContainsKey("Lads menu capture END:"))
        throw new InvalidOperationException("The requested native menu frame was not captured.");
    if (hudCaptureVerification && (!nativeTitleProbes.ContainsKey("Lads HUD capture END:") || !nativeTitleProbes.ContainsKey("Lads HUD editor probe END:")))
        throw new InvalidOperationException("The requested HUD editor interaction checks and native frame capture did not complete.");
    Console.WriteLine("PRODUCTION SMOKE PASS: correct version, core initialized, native window observed. Online sign-in is not exercised.");
}
finally
{
    if (autoWorldVerification && !process.HasExited)
    {
        await File.WriteAllTextAsync(stopRequest, "Gracefully stop this isolated QA game.");
        using var exitTimeout = new CancellationTokenSource(TimeSpan.FromSeconds(15));
        try { await process.WaitForExitAsync(exitTimeout.Token); }
        catch (OperationCanceledException) { Console.WriteLine("QA graceful shutdown timed out; stopping only the QA process."); }
    }
    if (!process.HasExited) { process.Kill(entireProcessTree: true); await process.WaitForExitAsync(); }
}
