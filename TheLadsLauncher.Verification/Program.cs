using System.Diagnostics;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
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
    return 0;
}

// Offline check of the Fabric loaded-list parser against saved game logs (no game, no files written).
if (args.Length >= 2 && args[0] == "--parse-log")
{
    int bad = 0;
    foreach (var file in args.Skip(1))
    {
        var list = new FabricModList();
        foreach (var line in File.ReadLines(file)) list.Feed(line);
        bool ok = list.Parsed && list.Declared == list.Distinct;
        Console.WriteLine($"{(ok ? "OK " : "BAD")} {file}: declared {list.Declared}, {list.Top.Count} top-level, {list.Nested.Count} nested entries, {list.Distinct} distinct ids");
        if (!ok) bad++;
    }
    return bad == 0 ? 0 : 1;
}

if (args.Length == 1 && args[0] == "--api")
{
    foreach (var property in typeof(MinecraftPath).GetProperties()) Console.WriteLine($"{property.Name}: {property.PropertyType} writable={property.CanWrite}");
    return 0;
}

const string Usage = "Usage: dotnet run --project TheLadsLauncher.Verification -- <1.21.1|1.21.11|26.2|26.3> <repository root> [--title|--settings|--pack-smoke] [--dir <qaDirName>] [--expect-core-disabled]\n"
    + "   or: ... -- --show-mods <version> <repository root> [--dir <qaDirName>]\n"
    + "   or: ... -- --set-mods <version> <repository root> <enable|disable> <id,id,...> [--dir <qaDirName>]";
string[] supportedVersions = ["1.21.1", "1.21.11", "26.2", "26.3"];
var rest = args.ToList();
string? dirOption = null;
int dirIndex = rest.IndexOf("--dir");
if (dirIndex >= 0)
{
    if (dirIndex + 1 >= rest.Count) throw new ArgumentException(Usage);
    dirOption = rest[dirIndex + 1];
    rest.RemoveRange(dirIndex, 2);
}
bool expectCoreDisabled = rest.Remove("--expect-core-disabled");
bool setMods = rest.FirstOrDefault() == "--set-mods", showMods = rest.FirstOrDefault() == "--show-mods";
if (setMods || showMods)
{
    if (rest.Count != (setMods ? 5 : 3) || !supportedVersions.Contains(rest[1]) || (setMods && rest[3] is not ("enable" or "disable")) || expectCoreDisabled)
        throw new ArgumentException(Usage);
    rest.RemoveAt(0);
}
else if (rest.Count is < 2 or > 3 || !supportedVersions.Contains(rest[0]) || (rest.Count == 3 && rest[2] is not ("--title" or "--settings" or "--pack-smoke")))
    throw new ArgumentException(Usage);
string version = rest[0];
string root = Path.GetFullPath(rest[1]);
string runMode = setMods || showMods ? "" : rest.ElementAtOrDefault(2) ?? "";
bool packSmoke = runMode == "--pack-smoke", titleVerification = runMode == "--title", settingsVerification = runMode == "--settings";
if (!File.Exists(Path.Combine(root, "TheLadsCore", "settings.gradle")))
    throw new ArgumentException($"'{root}' is not the repository root (TheLadsCore\\settings.gradle is missing).");
string verificationRoot = Path.Combine(root, "artifacts", "verification");
Directory.CreateDirectory(verificationRoot);
var harnessEnvironment = Environment.GetEnvironmentVariables().Keys.Cast<string>()
    .Where(name => name.StartsWith("LADS_", StringComparison.OrdinalIgnoreCase)).Order(StringComparer.OrdinalIgnoreCase)
    .Select(name => $"{name}={Environment.GetEnvironmentVariable(name)}").ToList();

// Sandbox: every folder this harness touches lies under artifacts\verification, never the real .minecraft or .theladsclient.
string? rootOverride = Env("LADS_VERIFY_GLOBAL_ROOT");
if (rootOverride != null && !Path.IsPathFullyQualified(rootOverride)) throw new ArgumentException("LADS_VERIFY_GLOBAL_ROOT must be an absolute path.");
string sharedRoot = Path.GetFullPath(rootOverride ?? Path.Combine(verificationRoot, "global-sandbox"));
if (!SafeFileOps.IsSameOrInside(sharedRoot, verificationRoot) || SafeFileOps.PathsEqual(sharedRoot, verificationRoot))
    throw new ArgumentException($"The shared-content sandbox '{sharedRoot}' must be a folder inside '{verificationRoot}'.");
Directory.CreateDirectory(sharedRoot);
RequireInside(sharedRoot, verificationRoot, "Shared-content sandbox root");
string dirName = dirOption ?? (setMods || showMods || titleVerification ? version + "-title"
    : packSmoke ? version + "-instance-pack" : settingsVerification ? version + "-settings" : version);
if (!Regex.IsMatch(dirName, @"\A[A-Za-z0-9][A-Za-z0-9._-]{0,63}\z"))
    throw new ArgumentException($"--dir must be a plain folder name under artifacts\\verification, not '{dirName}'.");
string directory = Path.Combine(verificationRoot, dirName);
if (SafeFileOps.IsSameOrInside(sharedRoot, directory) || SafeFileOps.IsSameOrInside(directory, sharedRoot))
    throw new ArgumentException($"The QA game folder '{directory}' and the shared sandbox '{sharedRoot}' must not contain each other.");

// Game-run options, validated before anything is written (the --set-mods/--show-mods modes ignore them).
bool autoWorldRequested = titleVerification && (version is "26.2" or "26.3") && Env("LADS_VERIFY_AUTO_WORLD") == "1";
bool autoWorldVerification = autoWorldRequested && !expectCoreDisabled;
bool requestedFeaturesOnly = autoWorldVerification && Env("LADS_VERIFY_REQUESTS_ONLY") == "1";
bool renderScaleVerification = !expectCoreDisabled && !requestedFeaturesOnly && titleVerification && (autoWorldVerification || Env("LADS_VERIFY_RENDER_SCALE") == "1");
// The staged pack (which jars are installed) follows the env alone, so a Core-disabled run keeps the same mods folder.
bool nativePack = autoWorldRequested || ((version is "26.2" or "26.3") && Env("LADS_VERIFY_NATIVE_PORTS") == "1");
bool nativePortsVerification = nativePack && !expectCoreDisabled;
bool menuCaptureVerification = autoWorldVerification && Env("LADS_VERIFY_CAPTURE_MENU") == "1";
bool hudCaptureVerification = autoWorldVerification && Env("LADS_VERIFY_CAPTURE_HUD") == "1";
if (autoWorldVerification && dirName != version + "-title")
    throw new ArgumentException($"Auto-world QA runs only in {version}-title (LadsCore refuses any other folder).");
string? sharedRole = Env("LADS_VERIFY_SHARED_ROLE"), runId = Env("LADS_VERIFY_RUN_ID"), modRequest = Env("LADS_VERIFY_MOD_REQUEST");
if (sharedRole is not (null or "create" or "observe")) throw new ArgumentException("LADS_VERIFY_SHARED_ROLE must be create or observe.");
if (runId != null && !Regex.IsMatch(runId, @"\A[A-Za-z0-9_-]{1,40}\z")) throw new ArgumentException("LADS_VERIFY_RUN_ID must be 1-40 letters, digits, '-' or '_'.");
if (sharedRole != null && runId == null) throw new ArgumentException("LADS_VERIFY_SHARED_ROLE needs LADS_VERIFY_RUN_ID.");
if (sharedRole != null && expectCoreDisabled) throw new ArgumentException("LADS_VERIFY_SHARED_ROLE needs LadsCore; it cannot run with --expect-core-disabled.");
// Core's create role waits for a loaded integrated world, which only the 26.x auto-world run opens (1.21.x refuses create).
if (sharedRole == "create" && !autoWorldVerification && !(setMods || showMods))
    throw new ArgumentException("LADS_VERIFY_SHARED_ROLE=create needs a 26.x --title run with LADS_VERIFY_AUTO_WORLD=1.");
if (modRequest != null && !Regex.IsMatch(modRequest, @"\A[a-z][a-z0-9_-]{0,63}:(true|false)\z")) throw new ArgumentException("LADS_VERIFY_MOD_REQUEST must be <mod id>:<true|false>.");
if (modRequest != null && expectCoreDisabled) throw new ArgumentException("LADS_VERIFY_MOD_REQUEST needs LadsCore; it cannot run with --expect-core-disabled.");
var expectAbsent = Ids(Env("LADS_VERIFY_EXPECT_ABSENT"));
var expectPresent = Ids(Env("LADS_VERIFY_EXPECT_PRESENT"));
if (expectCoreDisabled && !expectAbsent.Contains(BundledModInstaller.CoreModId)) expectAbsent.Add(BundledModInstaller.CoreModId);
if (setMods || showMods)
{
    if (!Directory.Exists(directory)) throw new DirectoryNotFoundException($"QA game folder '{directory}' does not exist; run the game once first.");
}
else Directory.CreateDirectory(directory);
RequireInside(directory, verificationRoot, "QA game folder");
// Anything that still resolves the launcher's default folders lands in the sandbox, never in %APPDATA%.
Environment.SetEnvironmentVariable("THELADS_DIR", Path.Combine(verificationRoot, "harness-launcher-data"));
Environment.SetEnvironmentVariable(SharedContentService.RootEnvironmentVariable, sharedRoot);
var shared = new SharedContentService(sharedRoot);

string scenario = Env("LADS_VERIFY_SCENARIO") ?? "adhoc";
if (!Regex.IsMatch(scenario, @"\A[A-Za-z0-9][A-Za-z0-9._-]{0,79}\z")) throw new ArgumentException("LADS_VERIFY_SCENARIO must be a plain name ([A-Za-z0-9._-]).");
string evidence = Path.Combine(root, "artifacts", "verification-1.2.3", scenario,
    $"{DateTime.Now:yyyyMMdd-HHmmss}-{(setMods ? "set-mods" : showMods ? "show-mods" : "game")}-{dirName}");
Directory.CreateDirectory(evidence);
Console.WriteLine($"Scenario {scenario}; QA folder {directory}; shared sandbox {sharedRoot}; evidence {evidence}");
var failures = new List<string>();
var summary = new List<string>
{
    "Scenario: " + scenario,
    "Command: dotnet run --project TheLadsLauncher.Verification -- " + string.Join(' ', args.Select(a => a.Contains(' ') ? $"\"{a}\"" : a)),
    "Started: " + DateTime.Now.ToString("O"),
    "Harness environment: " + (harnessEnvironment.Count == 0 ? "(no LADS_* variables)" : string.Join("; ", harnessEnvironment)),
    $"QA game folder: {directory}", $"Shared sandbox (LADS_GLOBAL_MINECRAFT_DIR): {sharedRoot}"
};

// Trip-wire: the real global folder must be byte-for-byte (by listing) the same after the run.
string realMinecraft = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".minecraft");
string tripBefore = Snapshot(realMinecraft);
await File.WriteAllTextAsync(Path.Combine(evidence, "tripwire-before.txt"), tripBefore);

if (setMods || showMods)
{
    try
    {
        // Same manifests as the folder's last game run (the native-ports QA pack stages its own).
        string bundle = Path.Combine(root, "TheLadsLauncher");
        string packMarker = Path.Combine(directory, ".lads-qa-pack-source");
        if (File.Exists(packMarker) && Path.GetFullPath(File.ReadAllText(packMarker).Trim()) is var recorded
            && SafeFileOps.IsSameOrInside(recorded, root) && Directory.Exists(recorded))
            bundle = recorded;
        Console.WriteLine("Mod manifests from: " + bundle);
        summary.Add("Mod manifests from: " + bundle);
        var inventoryService = new ModInventoryService();
        var inventory = await inventoryService.BuildAsync(bundle, directory, version);
        string table = InventoryTable(inventory);
        await File.WriteAllTextAsync(Path.Combine(evidence, "inventory-rows.txt"), table);
        if (showMods) Console.Write(table);
        else if (RunningGameMarker.IsRunning(directory))
            failures.Add("The QA game of this folder is running; --set-mods changes jars only while it is closed. Nothing was changed.");
        else
        {
            bool enable = rest[2] == "enable";
            var ids = rest[3].Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
            if (ids.Length == 0) throw new ArgumentException(Usage);
            var states = new ModStateService(RunningGameMarker.IsRunning);
            var plan = states.Plan(inventory, ids, enable);
            string planText = $"Plan: {(enable ? "enable" : "disable")} targets [{string.Join(", ", plan.TargetIds)}]\n"
                + $"  alsoDisable [{string.Join(", ", plan.AlsoDisable)}]\n  alsoEnable [{string.Join(", ", plan.AlsoEnable)}]\n"
                + string.Concat(plan.Warnings.Select(w => "  warning: " + w + "\n")) + string.Concat(plan.Blockers.Select(b => "  BLOCKER: " + b + "\n"));
            Console.Write(planText);
            summary.Add(planText.TrimEnd());
            await File.WriteAllTextAsync(Path.Combine(evidence, "plan.txt"), planText);
            await File.WriteAllTextAsync(Path.Combine(evidence, "mods-before.txt"), ModsListing(directory));
            if (plan.Blockers.Count > 0) failures.Add("REFUSED: the plan has blockers; nothing was changed.");
            else
            {
                var result = await states.ApplyAsync(directory, inventory, plan);
                string resultText = $"Result: success={result.Success} appliedToFiles={result.AppliedToFiles} restartRequired={result.RestartRequired}; {result.Message}";
                Console.WriteLine(resultText);
                summary.Add(resultText);
                if (!result.Success) failures.Add("Apply failed: " + result.Message);
                string after = ModsListing(directory);
                Console.Write("mods/ after the change (name, size, SHA-256):\n" + after);
                await File.WriteAllTextAsync(Path.Combine(evidence, "mods-after.txt"), after);
                CopyIfExists(Path.Combine(directory, ModPreferences.FileName), Path.Combine(evidence, "lads-mod-state.after.json"));
                await File.WriteAllTextAsync(Path.Combine(evidence, "inventory-rows-after.txt"),
                    InventoryTable(await inventoryService.BuildAsync(bundle, directory, version)));
            }
        }
    }
    catch (Exception e) when (e is not ArgumentException)
    {
        Console.WriteLine(e);
        failures.Add($"{e.GetType().Name}: {e.Message}");
    }
    return await FinishAsync(setMods ? "MODS SET PASS" : "MODS SHOW PASS", []);
}

// ---- Game run ----
string stopRequest = Path.Combine(directory, ".lads-qa-stop");
if (autoWorldVerification && File.Exists(stopRequest)) File.Delete(stopRequest);
string menuCaptureRequest = Path.Combine(directory, ".lads-qa-capture-menu");
if (autoWorldVerification && File.Exists(menuCaptureRequest)) File.Delete(menuCaptureRequest);
string hudCaptureRequest = Path.Combine(directory, ".lads-qa-capture-hud");
if (autoWorldVerification && File.Exists(hudCaptureRequest)) File.Delete(hudCaptureRequest);
using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(10));
var ct = timeout.Token;

Process? process = null;
var exitHandled = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
var sessionMessages = new List<string>();
var runStartUtc = DateTime.UtcNow;
string logPath = Path.Combine(directory, "production-smoke.log");
StreamWriter? log = null;
var logGate = new object();
bool initialized = false, settingsProbePassed = false, nativeProbeFailed = false, renderScaleProbePassed = false, version133ProbePassed = false;
bool menuCaptureRequested = false, hudCaptureRequested = false, windowFound = false, snapshotInvalid = false;
var passedMarkers = new System.Collections.Concurrent.ConcurrentDictionary<string, byte>(StringComparer.Ordinal);
var inventorySnapshots = new Dictionary<string, JsonNode>(StringComparer.Ordinal);
var keyLines = new List<string>();
var modList = new FabricModList();
string[] requiredTitleProbes = ["Lads raised title probe END:",
    "Lads native reconnect probe END:", "Lads background policy probe END:", "Lads native SignalLoss probe END:", "Lads narrator probe END:"];
string[] requiredWorldProbes = ["Lads native feature probe END:", "Lads food render probe END:", "Lads food JEI probe END:",
    "Lads paper doll probe END:", "Lads food server sync END:", "Lads render scale probe END:", "Lads world capture END:",
    "Lads durability tooltip probe END:", "Lads tab tweaks probe END:", "Lads clumps server probe END:", "Lads native screenshots probe END:", "Lads native crosshair probe END:",
    "Lads shared content probe END:"];
if (requestedFeaturesOnly)
{
    requiredWorldProbes = ["Lads native feature probe END:", "Lads improvements probe END:", "Lads font reload probe END:", "Lads world capture END:", "Lads shared content probe END:"];
    Console.WriteLine("Focused requested-feature verification: legacy crosshair/render-scale suites are not part of this run.");
}
// Every run with LadsCore reports shared content and the mod inventory at the title screen (plus the in-game request when asked).
var requiredCore = new List<string>();
bool welcomeVerification = Env("LADS_VERIFY_WELCOME") == "1";
if (welcomeVerification) requiredCore.Add("Lads welcome probe END:");
if (!expectCoreDisabled) requiredCore.Add("Lads shared content probe END:");
if (modRequest != null) requiredCore.Add("Lads mod request probe END:");
string[] failureMarkers = ["Lads font reload probe FAILED", "Lads native feature probe FAILED", "Lads render scale probe FAILED", "Lads paper doll probe FAILED",
    "Lads native reconnect probe FAILED", "Lads dynamic FPS probe FAILED", "Lads background policy probe FAILED", "Lads auto-world QA FAILED",
    "Lads durability tooltip probe FAILED", "Lads native SignalLoss probe FAILED", "Lads tab tweaks probe FAILED", "Lads narrator probe FAILED",
    "Lads native screenshots probe FAILED", "Lads native crosshair probe FAILED", "Lads shared content probe FAILED",
    "Lads mod request probe FAILED", "Lads mods inventory snapshot FAILED", "Lads welcome probe FAILED",
    "Mod resolution encountered an incompatible mod set", "Incompatible mods found"];
bool CoreChecksDone() { lock (logGate) return requiredCore.All(passedMarkers.ContainsKey) && inventorySnapshots.ContainsKey("title"); }
var jvmFlags = new List<string>();

try
{
    string sandboxBefore = Snapshot(sharedRoot);
    await File.WriteAllTextAsync(Path.Combine(evidence, "sandbox-before.txt"), sandboxBefore, ct);
    bool coreRequested = SharedContentService.IsCoreRequested(directory, out var stateFileError);
    if (stateFileError != null) Console.WriteLine("Mod choices: " + stateFileError);
    if (expectCoreDisabled && coreRequested)
        throw new InvalidOperationException($"--expect-core-disabled, but lads-mod-state.json does not disable theladscore. Run --set-mods {version} <root> disable theladscore --dir {dirName} first.");
    if (!expectCoreDisabled && !coreRequested)
        throw new InvalidOperationException($"LadsCore is disabled for {dirName}; pass --expect-core-disabled or run --set-mods {version} <root> enable theladscore --dir {dirName}.");

    // The same pre-launch order as the launcher: shared content, installers (which honour lads-mod-state.json), inventory snapshot.
    var prepared = await shared.PrepareProfileAsync(directory, "QA " + dirName, null, coreRequested, null, ct);
    string preparedText = $"Shared content prepare (coreEnabled={coreRequested}): skipped={prepared.Skipped} renamed={prepared.Renamed} pending={prepared.Pending} "
        + $"report={prepared.ReportPath ?? "-"} backup={prepared.BackupPath ?? "-"}\n"
        + string.Concat(prepared.Messages.Select(m => "  message: " + m + "\n")) + string.Concat(prepared.Warnings.Select(w => "  warning: " + w + "\n"))
        + string.Concat(shared.GetStatus(directory).Select(s => $"  {s.Name}: {s.State} ({s.ProfilePath} -> {s.SharedPath}) {s.Detail}\n"));
    Console.Write(preparedText);
    await File.WriteAllTextAsync(Path.Combine(evidence, "shared-prepare.txt"), preparedText, ct);
    if (prepared.ReportPath != null) CopyIfExists(prepared.ReportPath, Path.Combine(evidence, "shared-prepare-report.jsonl"));

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
    if (nativePack)
    {
        var manifest = JsonNode.Parse(await File.ReadAllTextAsync(Path.Combine(packSource, "game-mods", version, "client-mods.json"), ct))!.AsObject();
        var mods = manifest["mods"]!.AsArray();
        for (int index = mods.Count - 1; index >= 0; index--)
            if (mods[index]?["modId"]?.GetValue<string>() is "paperdoll" or "autoreconnectrf" or "tabtweaks" or "screenshot_viewer" or "clumps") mods.RemoveAt(index);
        packSource = Path.Combine(root, "artifacts", "verification", "native-ports-pack");
        string stagedManifest = Path.Combine(packSource, "game-mods", version, "client-mods.json");
        Directory.CreateDirectory(Path.GetDirectoryName(stagedManifest)!);
        await File.WriteAllTextAsync(stagedManifest, manifest.ToJsonString(new() { WriteIndented = true }), ct);
        // The other versions' production manifests, so the inventory's "unavailable" rows match a real launcher bundle.
        foreach (var other in supportedVersions.Where(v => v != version))
        {
            string production = Path.Combine(root, "TheLadsLauncher", "game-mods", other, "client-mods.json");
            string staged = Path.Combine(packSource, "game-mods", other, "client-mods.json");
            Directory.CreateDirectory(Path.GetDirectoryName(staged)!);
            if (File.Exists(production)) File.Copy(production, staged, overwrite: true);
        }
        Console.WriteLine("Native port QA: staged pack without replaced Paper Doll, AutoReconnect, TabTweaks, Screenshot Viewer and Clumps upstream jars. Production manifest preserved.");
    }
    await ClientModInstaller.InstallAsync(packSource, directory, version, Console.WriteLine, ct);
    if (Env("LADS_VERIFY_V133") == "1")
    {
        string addon = Path.Combine(root,"TheLadsCore",version=="26.3"?"v26_3":"v26_2","build","verification","lads-jade-addon-qa.jar");
        File.Copy(addon,Path.Combine(directory,"mods","lads-jade-addon-qa.jar"),true);
    }
    foreach (string warning in await ModWelcomeSettings.PrepareAsync(directory, ct))
        throw new InvalidOperationException(warning);
    await File.WriteAllTextAsync(Path.Combine(directory, ".lads-qa-pack-source"), packSource, ct);
    var inventoryService = new ModInventoryService();
    var inventory = await inventoryService.BuildAsync(packSource, directory, version, ct);
    await inventoryService.WriteSnapshotAsync(inventory, ct);
    var loadedMods = ModInventoryView.EnabledJarIds(inventory);
    CopyIfExists(Path.Combine(directory, ".lads-mod-cache", "inventory.json"), Path.Combine(evidence, "launcher-inventory-snapshot.json"));
    await File.WriteAllTextAsync(Path.Combine(evidence, "inventory-rows.txt"), InventoryTable(inventory), ct);
    await File.WriteAllTextAsync(Path.Combine(evidence, "mods-listing.txt"), ModsListing(directory), ct);
    CopyIfExists(Path.Combine(directory, ModPreferences.FileName), Path.Combine(evidence, "lads-mod-state.before.json"));
    Console.WriteLine($"Launcher inventory: {ModInventoryView.CountsText(inventory.Counts)}; {loadedMods.Count} enabled jar ids recorded for the running marker.");

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
    process = await launcher.InstallAndBuildProcessAsync(id, new MLaunchOption
    {
        Session = session, JavaPath = javaPath, MaximumRamMb = 2048, MinimumRamMb = 256,
        FullScreen = false, ScreenWidth = titleVerification ? 1280 : 960, ScreenHeight = titleVerification ? 720 : 600
    }, ct);
    void AddJvm(string argument)
    {
        jvmFlags.Add(argument);
        if (process.StartInfo.ArgumentList.Count > 0) process.StartInfo.ArgumentList.Insert(0, argument);
        else process.StartInfo.Arguments = argument + " " + process.StartInfo.Arguments;
    }
    // Probe public settings APIs only in this isolated QA process, when its Lads menu opens.
    if (welcomeVerification) AddJvm("-Dthelads.verifyWelcome=true");
    else AddJvm("-Dthelads.verifyIntegrations=true");
    if (!autoWorldVerification) AddJvm("-Dthelads.verifyInput=true");
    else
    {
        AddJvm("-Dthelads.verifyAutoWorld=true");
        AddJvm("-Dthelads.verifyDurabilityTooltip=true");
        AddJvm(requestedFeaturesOnly ? "-Dthelads.verifyRequestedFeaturesOnly=true" : "-Dthelads.verifyCrosshair=true");
    }
    if (titleVerification && !autoWorldVerification) AddJvm("-Dthelads.verifyKillBannerPreview=true");
    if (settingsVerification)
    {
        AddJvm("-Dthelads.verifyAllIntegrationWrites=true");
        AddJvm("-Dthelads.verifyIntegrationWrites=true");
    }
    if (renderScaleVerification) AddJvm("-Dthelads.verifyRenderScale=true");
    if (Env("LADS_VERIFY_V133") == "1") AddJvm("-Dthelads.verify133=true");
    if (Env("LADS_VERIFY_SKIN_NETWORK") == "1") AddJvm("-Dthelads.verifySkinNetwork=true");
    if (nativePortsVerification) AddJvm("-Dthelads.verifyBackgroundPolicies=true");
    AddJvm("-Dthelads.verifySharedContent=true");
    if (sharedRole != null) AddJvm("-Dthelads.sharedContentRole=" + sharedRole);
    if (runId != null) AddJvm("-Dthelads.sharedContentRunId=" + runId);
    AddJvm("-Dthelads.verifyModInventory=true");
    if (modRequest != null) AddJvm("-Dthelads.verifyModRequest=" + modRequest);
    process.StartInfo.UseShellExecute = false;
    process.StartInfo.CreateNoWindow = true;
    process.StartInfo.RedirectStandardOutput = true;
    process.StartInfo.RedirectStandardError = true;
    GameSession.Configure(process.StartInfo, directory, shared.Root);
    summary.Add($"Child environment: THELADS_DIR={directory}; {SharedContentService.RootEnvironmentVariable}={shared.Root}");
    summary.Add("JVM QA flags: " + string.Join(' ', jvmFlags));
    log = new StreamWriter(logPath) { AutoFlush = true };
    void WriteLine(object sender, DataReceivedEventArgs e)
    {
        if (e.Data == null) return;
        string line = e.Data;
        lock (logGate)
        {
            if (log == null) return;
            log.WriteLine(line);
            if (modList.Feed(line)) return;
            if (line.Contains($"TheLadsCore {version} initialized successfully")) initialized = true;
            if (line.Contains("Lads integration write probe END:") && Passed(line)) settingsProbePassed = true;
            if (failureMarkers.Any(line.Contains)) nativeProbeFailed = true;
            if (line.Contains("Lads 1.3.3 probe END:") && Passed(line)) version133ProbePassed = true;
            foreach (string marker in requiredTitleProbes.Concat(requiredCore))
                if (line.Contains(marker) && Passed(line)) passedMarkers.TryAdd(marker, 0);
            foreach (string marker in requiredWorldProbes)
                if (line.Contains(marker) && (Passed(line) || marker == "Lads food server sync END:")) passedMarkers.TryAdd(marker, 0);
            foreach (string marker in new[] { "Lads menu capture END:", "Lads mods view capture END:", "Lads HUD capture END:", "Lads HUD editor probe END:" })
                if (line.Contains(marker) && Passed(line)) passedMarkers.TryAdd(marker, 0);
            if (line.Contains("Lads render scale probe END:") && Passed(line)) renderScaleProbePassed = true;
            const string snapshotMarker = "Lads mods inventory snapshot: ";
            int snapshotAt = line.IndexOf(snapshotMarker, StringComparison.Ordinal);
            if (snapshotAt >= 0)
            {
                string json = line[(snapshotAt + snapshotMarker.Length)..];
                int end = json.IndexOf("]]>", StringComparison.Ordinal);
                try
                {
                    var node = JsonNode.Parse(end >= 0 ? json[..end] : json)!;
                    inventorySnapshots[node["source"]?.GetValue<string>() ?? "unknown"] = node;
                    keyLines.Add($"Lads mods inventory snapshot ({node["source"]}): {node["rows"]?.AsArray().Count} rows, counts {node["counts"]?.ToJsonString()}");
                }
                catch (Exception parse) when (parse is JsonException or InvalidOperationException)
                {
                    snapshotInvalid = true;
                    keyLines.Add("Lads mods inventory snapshot is not valid JSON: " + parse.Message);
                }
            }
            bool key = line.Contains("probe END:") || line.Contains("probe FAILED") || line.Contains("capture END:") || line.Contains("capture FAILED")
                || line.Contains("Lads auto-world QA") || line.Contains("Lads shared content") || line.Contains("Lads mod request probe")
                || line.Contains("initialized successfully") || failureMarkers.Any(line.Contains);
            if (key && snapshotAt < 0) keyLines.Add(Clean(line));
            if (line.Contains("Lads integration write") || (key && snapshotAt < 0)) Console.WriteLine(Clean(line));
            else if (line.Contains("ERROR") || line.Contains("Exception") || line.Contains("Initializing TheLadsCore")) Console.WriteLine(line);
        }
    }
    process.OutputDataReceived += WriteLine;
    process.ErrorDataReceived += WriteLine;
    var stopwatch = Stopwatch.StartNew();
    process.Start();
    GameSession.Attach(process, directory, loadedMods, message => { lock (sessionMessages) sessionMessages.Add(message); }, shared,
        () => { exitHandled.TrySetResult(); return Task.CompletedTask; });
    process.BeginOutputReadLine();
    process.BeginErrorReadLine();
    Console.WriteLine($"Started QA game PID {process.Id}. Log: {logPath}");
    TimeSpan windowAt = TimeSpan.Zero;
    // Title runs without the 26.x auto-world end once every check the harness asserts has passed (they never did before, so
    // 1.21.x waited the full 540 s); the stop is a Kill, as for every non-auto-world run.
    bool earlyTitleExit = titleVerification && !autoWorldVerification;
    while (stopwatch.Elapsed < TimeSpan.FromSeconds(titleVerification ? 540 : 180) && !process.HasExited)
    {
        process.Refresh();
        if (!windowFound && process.MainWindowHandle != IntPtr.Zero)
        {
            windowFound = true;
            windowAt = stopwatch.Elapsed;
            Console.WriteLine($"Game window detected at {stopwatch.Elapsed.TotalSeconds:F1}s; waiting for {(expectCoreDisabled ? "Fabric's loaded mod list" : "UI verification")}.");
        }
        await Task.Delay(500, ct);
        if (expectCoreDisabled)
        {
            // No LadsCore hooks exist in this process: the window and Fabric's loaded list are the whole signal.
            if (nativeProbeFailed || (windowFound && modList.Parsed && stopwatch.Elapsed - windowAt > TimeSpan.FromSeconds(15))) break;
            continue;
        }
        if (autoWorldVerification && requiredTitleProbes.Concat(requiredWorldProbes).All(passedMarkers.ContainsKey) && CoreChecksDone())
        {
            bool menuDone = !menuCaptureVerification || (passedMarkers.ContainsKey("Lads menu capture END:") && passedMarkers.ContainsKey("Lads mods view capture END:"));
            bool hudDone = !hudCaptureVerification || (passedMarkers.ContainsKey("Lads HUD capture END:") && passedMarkers.ContainsKey("Lads HUD editor probe END:"));
            if (menuDone && hudDone) break;
            if (!menuDone && !menuCaptureRequested)
            {
                await File.WriteAllTextAsync(menuCaptureRequest, "Capture the native Lads mods menu after all world probes pass.", ct);
                menuCaptureRequested = true;
                Console.WriteLine("World probes passed; requesting actual Lads menu and Installed mods frames from the QA game.");
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
        if (nativeProbeFailed && (!autoWorldVerification || passedMarkers.ContainsKey("Lads native screenshots probe END:")
            || stopwatch.Elapsed > TimeSpan.FromSeconds(90))) break;
        if (earlyTitleExit && windowFound && initialized && CoreChecksDone() && modList.Parsed
            && (!nativePortsVerification || requiredTitleProbes.All(passedMarkers.ContainsKey))) break;
        if (!titleVerification && windowFound && initialized && (!settingsVerification || settingsProbePassed)
            && (!nativePortsVerification || requiredTitleProbes.All(passedMarkers.ContainsKey)) && CoreChecksDone()
            && stopwatch.Elapsed > TimeSpan.FromSeconds(20)) break;
    }
    bool exitedOnItsOwn = process.HasExited;
    if (exitedOnItsOwn) Console.WriteLine($"Game exit: {process.ExitCode}");
    lock (logGate)
    {
        void Require(bool ok, string message) { if (!ok) failures.Add(message); }
        Require(windowFound, "No game window was observed. Inspect production-smoke.log.");
        Require(!exitedOnItsOwn || process.ExitCode == 0, "Game exited with an error.");
        Require(Env("LADS_VERIFY_V133") != "1" || version133ProbePassed, "1.3.3 native/API probe did not finish.");
        Require(!nativeProbeFailed, "A runtime probe or Fabric reported a failure (see the FAILED lines). Inspect production-smoke.log.");
        if (expectCoreDisabled)
        {
            Require(!initialized, "LadsCore initialized although it is disabled for this folder.");
            Require(!exitedOnItsOwn, "The game exited on its own before the harness stopped it.");
        }
        else
        {
            Require(initialized, $"LadsCore did not log 'TheLadsCore {version} initialized successfully'.");
            Require(requiredCore.All(passedMarkers.ContainsKey), "Missing passing END markers: " + string.Join(", ", requiredCore.Where(m => !passedMarkers.ContainsKey(m))));
            Require(inventorySnapshots.ContainsKey("title") && !snapshotInvalid, "The title-screen 'Lads mods inventory snapshot:' line was missing or not valid JSON.");
        }
        Require(modList.Parsed, "Fabric's 'Loading N mods:' list was not found in the game output.");
        // Fabric's N counts distinct ids (a library nested in several jars is listed under each).
        Require(!modList.Parsed || modList.Declared == modList.Distinct,
            $"Fabric declared {modList.Declared} mods but the harness parsed {modList.Distinct} distinct ids; the loaded-mods parse is incomplete.");
        foreach (var absent in expectAbsent)
        {
            if (modList.Top.ContainsKey(absent)) failures.Add($"Expected absent, but Fabric loaded {absent} as a top-level mod.");
            foreach (var nested in modList.Nested.Where(n => n.Id == absent)) failures.Add($"Expected absent, but Fabric loaded {absent} nested in {nested.Parent}.");
        }
        foreach (var present in expectPresent)
            Require(modList.Top.ContainsKey(present), $"Expected present, but Fabric did not load {present} as a top-level mod.");
        if (modList.Parsed)
        {
            string listText = $"Fabric loaded list: declared {modList.Declared}, {modList.Top.Count} top-level, {modList.Nested.Count} nested entries, {modList.Distinct} distinct ids; "
                + $"absent as expected: [{string.Join(", ", expectAbsent.Where(i => !modList.Top.ContainsKey(i) && modList.Nested.All(n => n.Id != i)))}]; "
                + $"present as expected: [{string.Join(", ", expectPresent.Where(modList.Top.ContainsKey))}]";
            Console.WriteLine(listText);
            keyLines.Add(listText);
        }
        Require(!settingsVerification || settingsProbePassed, "Runtime settings write probe did not pass. Inspect production-smoke.log.");
        Require(!nativePortsVerification || requiredTitleProbes.All(passedMarkers.ContainsKey),
            "Required native title probes did not all pass: " + string.Join(", ", requiredTitleProbes.Where(m => !passedMarkers.ContainsKey(m))));
        Require(!renderScaleVerification || renderScaleProbePassed, "Render scale GPU/world probe did not pass. Inspect production-smoke.log.");
        Require(!autoWorldVerification || requiredWorldProbes.All(passedMarkers.ContainsKey),
            "Required world probes did not all pass: " + string.Join(", ", requiredWorldProbes.Where(m => !passedMarkers.ContainsKey(m))));
        Require(!menuCaptureVerification || (passedMarkers.ContainsKey("Lads menu capture END:") && passedMarkers.ContainsKey("Lads mods view capture END:")),
            "The requested native menu and Installed mods frames were not both captured.");
        Require(!hudCaptureVerification || (passedMarkers.ContainsKey("Lads HUD capture END:") && passedMarkers.ContainsKey("Lads HUD editor probe END:")),
            "The requested HUD editor interaction checks and native frame capture did not complete.");
    }
}
catch (Exception e) // every failure after the trip-wire snapshot still reaches FinishAsync (trip-wire after, evidence)
{
    Console.WriteLine(e);
    failures.Add($"{e.GetType().Name}: {e.Message}");
}
finally
{
    if (process != null && IsRunning(process))
    {
        if (autoWorldVerification || welcomeVerification)
        {
            await File.WriteAllTextAsync(stopRequest, "Gracefully stop this isolated QA game.");
            using var exitTimeout = new CancellationTokenSource(TimeSpan.FromSeconds(15));
            try { await process.WaitForExitAsync(exitTimeout.Token); }
            catch (OperationCanceledException) { Console.WriteLine("QA graceful shutdown timed out; stopping only the QA process."); }
        }
        if (!process.HasExited)
        {
            Console.WriteLine(expectCoreDisabled ? "Stopping the QA game with Kill: LadsCore is disabled, so this process has no graceful-stop hook."
                : "Stopping only the QA process (Kill).");
            process.Kill(entireProcessTree: true);
        }
    }
    // After Kill too: the parameterless wait drains the redirected output before the log closes (bounded first, in case Kill failed).
    if (process != null && HasStarted(process) && process.WaitForExit(60_000)) process.WaitForExit();
}

// This independent addon belongs only to the opt-in compatibility probe.
if (Env("LADS_VERIFY_V133") == "1" && (process == null || !IsRunning(process)))
    File.Delete(Path.Combine(directory,"mods","lads-jade-addon-qa.jar"));

// Exit path: GameSession removes the running marker and reconciles the fallback server list, then signals here.
if (process != null && HasStarted(process))
{
    bool handled = await Task.WhenAny(exitHandled.Task, Task.Delay(TimeSpan.FromSeconds(30))) == exitHandled.Task;
    if (!handled) failures.Add("GameSession's exit handler did not finish within 30 s (running marker removal and fallback server-list reconcile).");
    bool markerGone = !File.Exists(RunningGameMarker.PathFor(directory));
    if (!markerGone) failures.Add("The running marker was not removed after the game exited.");
    bool fallback = File.Exists(Path.Combine(directory, SharedContentService.ServersBaseFileName));
    string exitText = $"Exit path: handler {(handled ? "finished" : "TIMED OUT")}; running marker {(markerGone ? "removed" : "STILL PRESENT")}; "
        + (fallback ? $"fallback server-list copy (LadsCore disabled) {(handled ? "passed to GameSession's reconcile into the shared servers.dat" : "NOT reconciled")}"
            : "no fallback server-list copy (LadsCore reads the shared servers.dat)")
        + string.Concat(sessionMessages.Select(m => "\n  session: " + m));
    Console.WriteLine(exitText);
    summary.Add(exitText);
}
lock (logGate) { log?.Dispose(); log = null; }
if (File.Exists(logPath)) File.Copy(logPath, Path.Combine(evidence, $"production-smoke-{scenario}.log"), overwrite: true);
// Readable evidence: versions keep their "+" instead of +.
var evidenceJson = new JsonSerializerOptions { WriteIndented = true, Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping };
if (modList.Top.Count > 0)
    await File.WriteAllTextAsync(Path.Combine(evidence, "loaded-mods.json"), new JsonObject
    {
        ["declared"] = modList.Declared,
        ["distinct"] = modList.Distinct,
        ["topLevel"] = new JsonObject(modList.Top.OrderBy(p => p.Key, StringComparer.Ordinal).Select(p => KeyValuePair.Create(p.Key, (JsonNode?)p.Value))),
        ["nested"] = new JsonArray(modList.Nested.Select(n => (JsonNode?)new JsonObject { ["id"] = n.Id, ["version"] = n.Version, ["parent"] = n.Parent }).ToArray())
    }.ToJsonString(evidenceJson));
foreach (var (source, node) in inventorySnapshots)
    await File.WriteAllTextAsync(Path.Combine(evidence, $"mods-inventory-snapshot-{source}.json"), node.ToJsonString(evidenceJson));
string screenshots = Path.Combine(directory, "screenshots");
if (Directory.Exists(screenshots))
    foreach (var png in new DirectoryInfo(screenshots).EnumerateFiles("*.png").Where(f => f.LastWriteTimeUtc >= runStartUtc))
    {
        Directory.CreateDirectory(Path.Combine(evidence, "screenshots"));
        png.CopyTo(Path.Combine(evidence, "screenshots", png.Name), overwrite: true);
    }
CopyIfExists(Path.Combine(directory, ModPreferences.FileName), Path.Combine(evidence, "lads-mod-state.after.json"));
await File.WriteAllTextAsync(Path.Combine(evidence, "sandbox-after.txt"), Snapshot(sharedRoot));
string loadedText = $"Fabric loaded {modList.Top.Count} top-level mods (declared {modList.Declared})"
    + (expectAbsent.Count > 0 ? $", none of [{string.Join(", ", expectAbsent)}]" : "")
    + (expectPresent.Count > 0 ? $", including [{string.Join(", ", expectPresent)}]" : "");
string coreText = $"core initialized, {string.Join(", ", requiredCore.Select(m => m.TrimEnd(':')))} and the title inventory snapshot passed";
string passLine = expectCoreDisabled
    ? $"PRODUCTION SMOKE PASS (LadsCore disabled): correct version, native window observed, {loadedText}; stopped with Kill because no LadsCore hooks exist. Online sign-in is not exercised."
    : requestedFeaturesOnly
        ? $"PRODUCTION SMOKE PASS (requested-features only; the broader 26.x suites were not run): correct version, {coreText}, native window observed, {loadedText}. Online sign-in is not exercised."
        : $"PRODUCTION SMOKE PASS: correct version, {coreText}, native window observed, {loadedText}. Online sign-in is not exercised.";
return await FinishAsync(passLine, keyLines);

async Task<int> FinishAsync(string passLine, IReadOnlyList<string> logLines)
{
    string tripAfter = Snapshot(realMinecraft);
    await File.WriteAllTextAsync(Path.Combine(evidence, "tripwire-after.txt"), tripAfter);
    if (tripAfter != tripBefore)
    {
        var before = tripBefore.Split('\n').ToHashSet();
        var after = tripAfter.Split('\n').ToHashSet();
        failures.Add("TRIP-WIRE: the real %APPDATA%\\.minecraft changed during this run: "
            + string.Join("; ", before.Except(after).Select(l => "- " + l).Concat(after.Except(before).Select(l => "+ " + l))));
    }
    else Console.WriteLine($"Trip-wire: {realMinecraft} unchanged (top-level entries, servers.dat SHA-256, saves/resourcepacks/shaderpacks entries).");
    string verdict = failures.Count == 0 ? passLine : "FAIL: " + string.Join(" | ", failures);
    summary.Add("Finished: " + DateTime.Now.ToString("O"));
    summary.Add("Result: " + verdict);
    summary.Add("Key log lines:");
    summary.AddRange(logLines.Select(l => "  " + (l.Length > 400 ? l[..400] + "..." : l)));
    summary.Add("Evidence files:");
    summary.AddRange(Directory.EnumerateFiles(evidence, "*", SearchOption.AllDirectories).Order(StringComparer.OrdinalIgnoreCase)
        .Select(f => $"  {Path.GetRelativePath(evidence, f)} ({new FileInfo(f).Length} bytes)"));
    await File.WriteAllLinesAsync(Path.Combine(evidence, "summary.txt"), summary);
    // The running RESULTS.md: one section per run, the same text as summary.txt (command, env, result, key lines, files).
    string results = Path.Combine(root, "artifacts", "verification-1.2.3", "RESULTS.md");
    try
    {
        if (!File.Exists(results))
            await File.WriteAllTextAsync(results, "# 1.2.3 verification results\n\nOne section per harness run, appended by TheLadsLauncher.Verification. "
                + "Evidence folders are relative to artifacts/verification-1.2.3.\n");
        await File.AppendAllTextAsync(results, $"\n## {scenario}: {Path.GetRelativePath(Path.GetDirectoryName(results)!, evidence).Replace('\\', '/')}\n\n"
            + $"**{(failures.Count == 0 ? "PASS" : "FAIL")}**\n\n```text\n{string.Join("\n", summary)}\n```\n");
    }
    catch (IOException e) { Console.WriteLine($"RESULTS.md was not updated ({e.Message}); the same text is in summary.txt."); }
    Console.WriteLine(failures.Count == 0 ? passLine
        : (setMods ? "MODS SET FAIL" : showMods ? "MODS SHOW FAIL" : "PRODUCTION SMOKE FAIL") + ":\n  " + string.Join("\n  ", failures));
    Console.WriteLine("Evidence: " + evidence);
    return failures.Count == 0 ? 0 : 1;
}

static string? Env(string name) => Environment.GetEnvironmentVariable(name) is { Length: > 0 } value ? value.Trim() : null;

static List<string> Ids(string? value) => (value ?? "").Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).Distinct().ToList();

// ", 0 failed" never matches "10 failed" or "1 failed"; every END format ("N passed, 0 failed", "N checks passed, 0 failed", "N passed, M skipped, 0 failed") has it.
static bool Passed(string line) => line.Contains(", 0 failed", StringComparison.Ordinal);

static string Clean(string line)
{
    int start = line.IndexOf("<![CDATA[", StringComparison.Ordinal);
    string text = start >= 0 ? line[(start + 9)..] : line.Trim();
    int end = text.IndexOf("]]>", StringComparison.Ordinal);
    return end >= 0 ? text[..end] : text;
}

static bool IsRunning(Process process) => HasStarted(process) && !process.HasExited;

static bool HasStarted(Process process)
{
    try { _ = process.Id; return true; }
    catch (InvalidOperationException) { return false; }
}

static void RequireInside(string path, string parent, string what)
{
    string real = SafeFileOps.GetFinalPath(path), realParent = SafeFileOps.GetFinalPath(parent);
    if (!SafeFileOps.IsSameOrInside(real, realParent) || SafeFileOps.PathsEqual(real, realParent))
        throw new ArgumentException($"{what} '{path}' resolves to '{real}', which is not inside '{realParent}'. QA only uses folders under artifacts\\verification.");
}

static void CopyIfExists(string source, string destination)
{
    if (File.Exists(source)) File.Copy(source, destination, overwrite: true);
}

// Read-only listing: top-level names, sizes and mtimes; servers.dat SHA-256; saves/resourcepacks/shaderpacks entry names and mtimes.
static string Snapshot(string folder)
{
    var top = new DirectoryInfo(folder);
    if (!top.Exists) return $"{folder}\tabsent\n";
    var lines = new List<string> { "root\t" + folder };
    static string Size(FileSystemInfo entry) => entry is FileInfo file ? file.Length.ToString() : "dir";
    foreach (var entry in top.EnumerateFileSystemInfos().OrderBy(e => e.Name, StringComparer.OrdinalIgnoreCase))
        lines.Add($"top\t{entry.Name}\t{Size(entry)}\t{entry.LastWriteTimeUtc:O}{(entry.LinkTarget != null ? "\tlink -> " + entry.LinkTarget : "")}");
    string servers = Path.Combine(folder, "servers.dat");
    lines.Add("servers.dat\t" + (File.Exists(servers) ? SafeFileOps.Sha256(servers) : "absent"));
    foreach (var name in new[] { "saves", "resourcepacks", "shaderpacks" })
    {
        var sub = new DirectoryInfo(Path.Combine(folder, name));
        if (!sub.Exists) { lines.Add(name + "\tabsent"); continue; }
        foreach (var entry in sub.EnumerateFileSystemInfos().OrderBy(e => e.Name, StringComparer.OrdinalIgnoreCase))
            lines.Add($"{name}\t{entry.Name}\t{Size(entry)}\t{entry.LastWriteTimeUtc:O}");
    }
    return string.Join("\n", lines) + "\n";
}

static string ModsListing(string gameDirectory)
{
    var mods = new DirectoryInfo(Path.Combine(gameDirectory, "mods"));
    if (!mods.Exists) return "mods folder absent\n";
    var text = new StringBuilder();
    foreach (var file in mods.EnumerateFiles().OrderBy(f => f.Name, StringComparer.OrdinalIgnoreCase))
        text.Append($"{file.Name}\t{file.Length}\t{SafeFileOps.Sha256(file.FullName)}\n");
    return text.ToString();
}

static string InventoryTable(ModInventory inventory)
{
    var text = new StringBuilder($"Inventory {inventory.MinecraftVersion} for {inventory.GameDirectory}: {ModInventoryView.CountsText(inventory.Counts)}\n")
        .Append("id\townership\tstatus\tenabledOnDisk\trequested\tfileName\n");
    void Row(ModInventoryEntry entry, int depth)
    {
        text.Append($"{new string(' ', depth * 2)}{entry.Id}\t{entry.Ownership}\t{entry.Status}\t{entry.EnabledOnDisk}\t{entry.RequestedEnabled}\t{entry.FileName ?? "-"}\n");
        foreach (var child in entry.Children) Row(child, depth + 1);
    }
    foreach (var entry in inventory.Entries) Row(entry, 0);
    return text.ToString();
}

/// <summary>Fabric's "Loading N mods:" block: top-level jars and the jar-in-jar mods under them. N counts distinct ids
/// (a library nested in several jars is listed under each). Nesting is 3 characters for the first level, then 5 per level.</summary>
sealed class FabricModList
{
    private static readonly Regex Start = new(@"Loading (\d+) mods:\s*$");
    private static readonly Regex Entry = new(@"^\t(?<indent>[ |]*)(?:- |[|\\]-- )(?<id>[^\s\]]+)(?: (?<version>.*?))?(?:\]\]>.*)?$");
    private readonly List<string> stack = new();
    private bool inList;

    public int? Declared { get; private set; }
    public bool Parsed { get; private set; }
    public Dictionary<string, string> Top { get; } = new(StringComparer.Ordinal);
    public List<(string Id, string Version, string Parent)> Nested { get; } = new();
    public int Distinct => Top.Keys.Union(Nested.Select(n => n.Id)).Count();

    /// <summary>True when the line was part of the block (its first line or an entry).</summary>
    public bool Feed(string line)
    {
        if (inList)
        {
            var entry = Entry.Match(line);
            if (!entry.Success)
            {
                inList = false;
                Parsed = Top.Count > 0;
                return false;
            }
            int indent = entry.Groups["indent"].Length, depth = indent == 0 ? 0 : (indent + 2) / 5;
            string id = entry.Groups["id"].Value, version = entry.Groups["version"].Value;
            if (stack.Count > depth) stack.RemoveRange(depth, stack.Count - depth);
            if (depth == 0) Top.TryAdd(id, version);
            else Nested.Add((id, version, stack.Count > 0 ? stack[^1] : "?"));
            stack.Add(id);
            if (line.Contains("]]>", StringComparison.Ordinal)) { inList = false; Parsed = true; }
            return true;
        }
        if (Parsed || Start.Match(line) is not { Success: true } start) return false;
        Declared = int.Parse(start.Groups[1].Value);
        inList = true;
        return true;
    }
}
