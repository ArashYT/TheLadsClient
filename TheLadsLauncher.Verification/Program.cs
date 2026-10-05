using System.Diagnostics;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using CmlLib.Core;
using CmlLib.Core.ProcessBuilder;
using TheLadsLauncher.Services;

// Modpacks tab QA (ModpackQa.cs): a real Modrinth modpack installed (and updated) in the sandbox, then started to its title screen.
if (args.Length >= 3 && args[0] == "--modpack") return await ModpackQa.RunAsync(args[1], Path.GetFullPath(args[2]), args[3..]);

// Explicit maintenance mode: prepare a profile's pinned pack without starting a game or reading accounts.
if (args.Length == 4 && args[0] == "--install-pack")
{
    string packVersion = args[1];
    if (!QaCapabilities.Supported.Contains(packVersion)) throw new ArgumentException("Unsupported pinned pack version.");
    using var installTimeout = new CancellationTokenSource(TimeSpan.FromMinutes(10));
    await BundledModInstaller.InstallAsync(Path.GetFullPath(args[2]), Path.GetFullPath(args[3]), packVersion, installTimeout.Token);
    await ClientModInstaller.InstallAsync(Path.GetFullPath(args[2]), Path.GetFullPath(args[3]), packVersion, Console.WriteLine, installTimeout.Token);
    Console.WriteLine("Profile pack prepared. No game or account sign-in was started.");
    return 0;
}

// Offline check of the loaded-list parsers against saved game logs (no game, no files written): Fabric's stdout block, or
// a Forge 1.8.9 logs\latest.log (recognised by FML's own lines).
if (args.Length >= 2 && args[0] == "--parse-log")
{
    int bad = 0;
    foreach (var file in args.Skip(1))
    {
        var lines = File.ReadAllLines(file);
        LoadedModList list = lines.Any(l => l.Contains("Forge Mod Loader", StringComparison.Ordinal)) ? new ForgeModList() : new FabricModList();
        foreach (var line in lines) list.Feed(line);
        bool ok = list.Parsed && list.Declared == list.Distinct;
        Console.WriteLine($"{(ok ? "OK " : "BAD")} {file}: {list.Name} declared {list.Declared}, {list.Top.Count} top-level, {list.Nested.Count} nested entries, {list.Distinct} distinct ids"
            + (list is ForgeModList forge ? $"; loaded {forge.LoadedMods?.ToString() ?? "-"}, OptiFine {(forge.OptiFineTweaker ? forge.OptiFine ?? "tweaker only" : "absent")}, {forge.Errors.Count} mixin/coremod errors" : ""));
        if (!ok) bad++;
    }
    return bad == 0 ? 0 : 1;
}

if (args.Length == 1 && args[0] == "--api")
{
    foreach (var property in typeof(MinecraftPath).GetProperties()) Console.WriteLine($"{property.Name}: {property.PropertyType} writable={property.CanWrite}");
    return 0;
}

string[] supportedVersions = QaCapabilities.Supported;
string Usage = $"Usage: dotnet run --project TheLadsLauncher.Verification -- <{string.Join('|', supportedVersions)}> <repository root> [--title|--settings|--pack-smoke] [--dir <qaDirName>] [--expect-core-disabled]\n"
    + "   or: ... -- --show-mods <version> <repository root> [--dir <qaDirName>]\n"
    + "   or: ... -- --set-mods <version> <repository root> <enable|disable> <id,id,...> [--dir <qaDirName>]";
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
var capabilities = QaCapabilities.For(version);
foreach (var (flag, available, feature) in new[] {
    ("LADS_VERIFY_V133", capabilities.Version133, "the 1.3.3 native Jade/API probe and its Jade QA addon"),
    ("LADS_VERIFY_V134", capabilities.Version134, "Vulkan (Minecraft " + version + " renders with OpenGL only) and the 1.3.4 renderer, gallery and Flashback probes"),
    ("LADS_VERIFY_REPLAY", capabilities.Replay, "the Flashback record/replay/export probe"),
    ("LADS_VERIFY_REQUESTS_ONLY", capabilities.RequestedFeatures, "the requested-feature probe suite"),
    ("LADS_VERIFY_RENDER_SCALE", capabilities.RenderScale, "the render scale probe"),
    ("LADS_VERIFY_WELCOME", capabilities.Welcome, "the welcome-screen probe") })
    if (!(setMods || showMods) && Env(flag) == "1" && !available)
        throw new ArgumentException($"{flag}=1 needs {feature}, which LadsCore does not have on {version}. Unset it for {version} runs.");
bool autoWorldRequested = titleVerification && Env("LADS_VERIFY_AUTO_WORLD") == "1";
bool autoWorldVerification = autoWorldRequested && !expectCoreDisabled;
bool requestedFeaturesOnly = autoWorldVerification && Env("LADS_VERIFY_REQUESTS_ONLY") == "1";
bool renderScaleVerification = capabilities.RenderScale && !expectCoreDisabled && !requestedFeaturesOnly && titleVerification && (autoWorldVerification || Env("LADS_VERIFY_RENDER_SCALE") == "1");
// QA only: upstream mods left out of the staged pack, to get past a pack defect under diagnosis. Never the production manifest.
var excludedMods = Ids(Env("LADS_VERIFY_EXCLUDE_MODS"));
// The staged pack (which jars are installed) follows the env alone, so a Core-disabled run keeps the same mods folder.
bool nativePack = autoWorldRequested || Env("LADS_VERIFY_NATIVE_PORTS") == "1" || excludedMods.Count > 0;
bool nativePortsVerification = nativePack && !expectCoreDisabled;
bool menuCaptureVerification = autoWorldVerification && Env("LADS_VERIFY_CAPTURE_MENU") == "1";
bool hudCaptureVerification = autoWorldVerification && Env("LADS_VERIFY_CAPTURE_HUD") == "1";
// 1.21.x and 26.x: every Kill Banner skin, variant and kill count fired in the QA world and photographed (KillBannerCapture).
bool bannerCaptureVerification = autoWorldVerification && capabilities.KillBanner && Env("LADS_VERIFY_CAPTURE_KILLBANNER") == "1";
// Fabric versions: the 1.7 Animations poses (sword block, bow, rod, eating, 2D dropped item, red armour) photographed (OldAnimationsCapture).
bool oldAnimCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_OLDANIM") == "1";
// Fabric versions: Lads Zoom through the real key and scroll handlers, photographed, with every frame's FOV in zoom-fov.csv (ZoomCapture).
// 1.8.9's self-test (Probe160) always runs the same checks.
bool zoomCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_ZOOM") == "1";
// Fabric versions: the player in a checkerboard-layered QA skin, from the front, SkinLayers on and off (SkinLayersCapture). 1.8.9's self-test does the same.
bool skinLayersCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_SKINLAYERS") == "1";
// Fabric versions: cheats turned on as World Options and Multiplayer Options do ("set"), "off", or what stayed after the
// world reopened ("check") (CheatsProbe). 1.8.9 runs its checks as a focused self-test: LADS_VERIFY_189_FOCUS (CoreProbe).
string? cheatsPhase = autoWorldVerification && !capabilities.Forge ? Env("LADS_VERIFY_CHEATS") : null;
if (cheatsPhase is not (null or "set" or "check" or "off")) throw new ArgumentException("LADS_VERIFY_CHEATS must be set, check or off.");
string? focus189 = autoWorldVerification && capabilities.Forge ? Env("LADS_VERIFY_189_FOCUS") : null;
if (focus189 != null && !Regex.IsMatch(focus189, @"\A[a-z0-9-]{1,40}\z")) throw new ArgumentException("LADS_VERIFY_189_FOCUS must be a plain step list name.");
// Fabric versions: Toggle Sprint & Sneak in the QA world (walls, hits, hunger, items, water, sneaking, flying, death, keys), every tick's
// sprint packets in sprint-trace.csv (SprintCapture). 1.8.9's self-test runs the same checks (Probe170Sprint).
bool sprintCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_SPRINT") == "1";
// Fabric versions: the 1.7.0 HUD lane in the QA world (Hud170Capture). 1.8.9's self-test runs the same checks (Probe170Hud).
bool hud170CaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_HUD170") == "1";
// Fabric versions: runs of consecutive frames with the HUD hidden, the HUD FPS cap off and on, every HUD element in view (HudFlickerCapture).
// 1.8.9: LADS_VERIFY_189_ONLY=hudflicker (Probe172HudFlicker).
bool hudFlickerCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_HUDFLICKER") == "1";
// Fabric versions: Item Physics in a QA arena (ItemPhysicsCapture). 1.8.9's self-test runs the same checks: LADS_VERIFY_189_ONLY=itemphysics.
bool itemPhysicsCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_ITEMPHYSICS") == "1";
// Fabric versions: the survival and creative inventories with and without potion effects, centred (InventoryCapture). 1.8.9: LADS_VERIFY_189_FOCUS=inventory (ProbeInventory).
bool inventoryCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_INVENTORY") == "1";
// Fabric versions: the Lads title screen and its More screen photographed before the QA world opens (NativeWorldVerification).
// 1.8.9's self-test (CoreProbe) always captures them.
bool titleCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_TITLE") == "1";
// Fabric versions: IgnorePacketErrors, AutoReconnect (the QA world and a closed port), chat signing and Ctrl+R on the server list
// (ServerFeaturesCapture). 1.8.9's self-test (ProbeServer170) always runs the same checks.
bool serverCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_SERVER") == "1";
// Every version: Chat Heads with own, second-player and ranked server chat, photographed closed, open, aligned and off (Fabric:
// ChatHeadsCapture, which the world capture waits for; 1.8.9: ChatHeadsProbe189 alone in the self-test's QA world).
bool chatHeadsCaptureVerification = autoWorldVerification && Env("LADS_VERIFY_CAPTURE_CHATHEADS") == "1";
// Better F3 frames and Custom FOV values in the QA world (Fabric: F3FovCapture; 1.8.9: a focused self-test, Probe170F3Fov).
bool f3FovCaptureVerification = autoWorldVerification && Env("LADS_VERIFY_CAPTURE_F3FOV") == "1";
// 26.x: the AppleSkin module's food previews, food and durability tooltips and Crosshair Tweaks styles photographed (HudInfoCapture).
bool hudInfoCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_HUDINFO") == "1";
// Fabric versions: Raised and the paper doll in the QA world (RaisedDollCapture). 1.8.9's self-test runs the same (RaisedDollProbe189).
bool raisedCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_RAISED") == "1";
// Fabric versions: Lads Mouse Tweaks in a server chest through the screen's own mouse handlers (MouseTweaksCapture).
// 1.8.9's self-test (MouseTweaksProbe189) always runs the same checks.
bool mouseTweaksCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_MOUSETWEAKS") == "1";
// Fabric versions: Better Resolution photographed per setting with its FPS and GPU frame time (ResolutionCapture). 1.8.9: Probe170r.
bool resolutionCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_RESOLUTION") == "1";
// Fabric versions: Dynamic Lights at midnight with the module off and on, and a moving torch's frame rate (DynamicLightsCapture).
// 1.8.9's self-test (LightsProbe189) always checks that the module drives OptiFine's Dynamic Lights.
bool lightsCaptureVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_CAPTURE_LIGHTS") == "1";
// 26.x: Flashback Settings records into a Lads replay folder and times stock vs Lads exports of the same clip (FlashbackExportProbe).
bool flashbackVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_FLASHBACK") == "1";
// 26.x: AppleSkin server payloads (saturation, exhaustion, natural regeneration) injected in the QA world (AppleSkinSyncCapture).
bool appleSkinSyncVerification = autoWorldVerification && !capabilities.Forge && Env("LADS_VERIFY_APPLESKIN_SYNC") == "1";
if (autoWorldVerification && dirName != version + "-title")
    throw new ArgumentException($"Auto-world QA runs only in {version}-title (LadsCore refuses any other folder).");
string? sharedRole = Env("LADS_VERIFY_SHARED_ROLE"), runId = Env("LADS_VERIFY_RUN_ID"), modRequest = Env("LADS_VERIFY_MOD_REQUEST");
if (sharedRole is not (null or "create" or "observe")) throw new ArgumentException("LADS_VERIFY_SHARED_ROLE must be create or observe.");
if (runId != null && !Regex.IsMatch(runId, @"\A[A-Za-z0-9_-]{1,40}\z")) throw new ArgumentException("LADS_VERIFY_RUN_ID must be 1-40 letters, digits, '-' or '_'.");
if (sharedRole != null && runId == null) throw new ArgumentException("LADS_VERIFY_SHARED_ROLE needs LADS_VERIFY_RUN_ID.");
if (sharedRole != null && expectCoreDisabled) throw new ArgumentException("LADS_VERIFY_SHARED_ROLE needs LadsCore; it cannot run with --expect-core-disabled.");
// Core's create role waits for a loaded integrated world from an auto-world run; 1.21.x Core refuses create.
if (sharedRole == "create" && !(autoWorldVerification && capabilities.SharedCreate) && !(setMods || showMods))
    throw new ArgumentException("LADS_VERIFY_SHARED_ROLE=create needs a 26.x --title run with LADS_VERIFY_AUTO_WORLD=1.");
if (modRequest != null && !Regex.IsMatch(modRequest, @"\A[a-z][a-z0-9_-]{0,63}:(true|false)\z")) throw new ArgumentException("LADS_VERIFY_MOD_REQUEST must be <mod id>:<true|false>.");
if (modRequest != null && expectCoreDisabled) throw new ArgumentException("LADS_VERIFY_MOD_REQUEST needs LadsCore; it cannot run with --expect-core-disabled.");
// The 1.8.9 Core has none of the Fabric Core's QA hooks: only the --title smoke and its own self-test (LADS_VERIFY_AUTO_WORLD=1) run.
if (capabilities.Forge && !(setMods || showMods))
    foreach (var (asked, what) in new[] { (!titleVerification, "A run without --title"), (sharedRole != null, "LADS_VERIFY_SHARED_ROLE"),
        (modRequest != null, "LADS_VERIFY_MOD_REQUEST"), (Env("LADS_VERIFY_SKIN_NETWORK") == "1", "LADS_VERIFY_SKIN_NETWORK"),
        (Env("LADS_VERIFY_CAPTURE_MENU") == "1" || Env("LADS_VERIFY_CAPTURE_HUD") == "1", "LADS_VERIFY_CAPTURE_MENU/HUD") })
        if (asked) throw new ArgumentException($"{what} needs the Fabric Core's QA hooks, which LadsCore does not have on {version}. Run {version} with --title (and LADS_VERIFY_AUTO_WORLD=1 for its self-test).");
var expectAbsent = Ids(Env("LADS_VERIFY_EXPECT_ABSENT"));
var expectPresent = Ids(Env("LADS_VERIFY_EXPECT_PRESENT"));
if (expectCoreDisabled && !expectAbsent.Contains(BundledModInstaller.CoreModId)) expectAbsent.Add(BundledModInstaller.CoreModId);
// On 1.8.9 Forge's own mod list must name the Core (no Fabric Core report proves it was loaded there).
if (capabilities.Forge && !expectCoreDisabled && !expectPresent.Contains(BundledModInstaller.CoreModId)) expectPresent.Add(BundledModInstaller.CoreModId);
expectAbsent.AddRange(excludedMods.Where(id => !expectAbsent.Contains(id)));
if (setMods || showMods)
{
    if (!Directory.Exists(directory)) throw new DirectoryNotFoundException($"QA game folder '{directory}' does not exist; run the game once first.");
}
else Directory.CreateDirectory(directory);
RequireInside(directory, verificationRoot, "QA game folder");
// Anything that still resolves the launcher's default folders lands in the sandbox, never in %APPDATA%.
string launcherData = Path.Combine(verificationRoot, "harness-launcher-data");
Environment.SetEnvironmentVariable("THELADS_DIR", launcherData);
Environment.SetEnvironmentVariable(SharedContentService.RootEnvironmentVariable, sharedRoot);
// QA never reads the real Lunar Client folder: the game gets this sandbox (absent: Lunar is not installed).
string lunarRoot = Path.Combine(verificationRoot, "lunar-sandbox");
if (Directory.Exists(lunarRoot)) RequireInside(lunarRoot, verificationRoot, "Lunar Client sandbox");
Environment.SetEnvironmentVariable(GameOptionsService.LunarEnvironmentVariable, lunarRoot);
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
string bannerCaptureRequest = Path.Combine(directory, ".lads-qa-capture-killbanner");
if (autoWorldVerification && File.Exists(bannerCaptureRequest)) File.Delete(bannerCaptureRequest);
string oldAnimCaptureRequest = Path.Combine(directory, ".lads-qa-capture-oldanim");
if (autoWorldVerification && File.Exists(oldAnimCaptureRequest)) File.Delete(oldAnimCaptureRequest);
string zoomCaptureRequest = Path.Combine(directory, ".lads-qa-capture-zoom");
if (autoWorldVerification && File.Exists(zoomCaptureRequest)) File.Delete(zoomCaptureRequest);
string skinLayersCaptureRequest = Path.Combine(directory, ".lads-qa-capture-skinlayers");
if (autoWorldVerification && File.Exists(skinLayersCaptureRequest)) File.Delete(skinLayersCaptureRequest);
string cheatsRequest = Path.Combine(directory, ".lads-qa-cheats");
if (autoWorldVerification && File.Exists(cheatsRequest)) File.Delete(cheatsRequest);
if (cheatsPhase != null) File.WriteAllText(cheatsRequest, cheatsPhase);
string sprintCaptureRequest = Path.Combine(directory, ".lads-qa-capture-sprint");
if (autoWorldVerification && File.Exists(sprintCaptureRequest)) File.Delete(sprintCaptureRequest);
string hud170CaptureRequest = Path.Combine(directory, ".lads-qa-capture-hud170");
if (autoWorldVerification && File.Exists(hud170CaptureRequest)) File.Delete(hud170CaptureRequest);
string hudFlickerCaptureRequest = Path.Combine(directory, ".lads-qa-capture-hudflicker");
if (autoWorldVerification && File.Exists(hudFlickerCaptureRequest)) File.Delete(hudFlickerCaptureRequest);
string itemPhysicsCaptureRequest = Path.Combine(directory, ".lads-qa-capture-itemphysics");
if (autoWorldVerification && File.Exists(itemPhysicsCaptureRequest)) File.Delete(itemPhysicsCaptureRequest);
string inventoryCaptureRequest = Path.Combine(directory, ".lads-qa-capture-inventory");
if (autoWorldVerification && File.Exists(inventoryCaptureRequest)) File.Delete(inventoryCaptureRequest);
string serverCaptureRequest = Path.Combine(directory, ".lads-qa-capture-server");
if (autoWorldVerification && File.Exists(serverCaptureRequest)) File.Delete(serverCaptureRequest);
string f3FovCaptureRequest = Path.Combine(directory, ".lads-qa-capture-f3fov");
if (autoWorldVerification && File.Exists(f3FovCaptureRequest)) File.Delete(f3FovCaptureRequest);
string hudInfoCaptureRequest = Path.Combine(directory, ".lads-qa-capture-hudinfo");
if (autoWorldVerification && File.Exists(hudInfoCaptureRequest)) File.Delete(hudInfoCaptureRequest);
string raisedCaptureRequest = Path.Combine(directory, ".lads-qa-capture-raised");
if (autoWorldVerification && File.Exists(raisedCaptureRequest)) File.Delete(raisedCaptureRequest);
string mouseTweaksCaptureRequest = Path.Combine(directory, ".lads-qa-capture-mousetweaks");
if (autoWorldVerification && File.Exists(mouseTweaksCaptureRequest)) File.Delete(mouseTweaksCaptureRequest);
string resolutionCaptureRequest = Path.Combine(directory, ".lads-qa-capture-resolution");
if (autoWorldVerification && File.Exists(resolutionCaptureRequest)) File.Delete(resolutionCaptureRequest);
string lightsCaptureRequest = Path.Combine(directory, ".lads-qa-capture-lights");
if (autoWorldVerification && File.Exists(lightsCaptureRequest)) File.Delete(lightsCaptureRequest);
string appleSkinSyncRequest = Path.Combine(directory, ".lads-qa-appleskin-sync");
if (autoWorldVerification && File.Exists(appleSkinSyncRequest)) File.Delete(appleSkinSyncRequest);
if (autoWorldVerification)
    foreach (var flag in new[] { ".lads-qa-screenshots134", ".lads-qa-replay", ".lads-qa-replay-done", ".lads-qa-replay-failed",
        ".lads-qa-flashback", ".lads-qa-flashback-done", ".lads-qa-flashback-failed" })
        File.Delete(Path.Combine(directory, flag));
using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(flashbackVerification ? 20 : 10));
var ct = timeout.Token;

Process? process = null;
var exitHandled = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
var sessionMessages = new List<string>();
var runStartUtc = DateTime.UtcNow;
string logPath = Path.Combine(directory, "production-smoke.log");
StreamWriter? log = null;
var logGate = new object();
bool initialized = false, settingsProbePassed = false, nativeProbeFailed = false, renderScaleProbePassed = false, version133ProbePassed = false, renderer134Passed = false, screenshots134Passed = false, replay134Passed = false, screenshots134Requested = false, replay134Requested = false, flashbackPassed = false, flashbackRequested = false;
bool menuCaptureRequested = false, hudCaptureRequested = false, worldCapturesRequested = false, windowFound = false, snapshotInvalid = false;
bool resolutionCaptureRequested = false;
var passedMarkers = new System.Collections.Concurrent.ConcurrentDictionary<string, byte>(StringComparer.Ordinal);
// END markers whose FAILED line was logged instead ("Lads x capture END:" -> "Lads x capture FAILED"): that capture is settled, so one
// failure no longer ends the run before the other requested captures have finished. The run still fails (nativeProbeFailed, Require).
var failedCaptures = new System.Collections.Concurrent.ConcurrentDictionary<string, byte>(StringComparer.Ordinal);
string[] captureEnds = ["Lads menu capture END:", "Lads mods view capture END:", "Lads HUD capture END:", "Lads HUD editor probe END:",
    "Lads kill banner capture END:", "Lads 1.7 animations capture END:", "Lads zoom capture END:", "Lads skin layers capture END:",
    "Lads sprint capture END:", "Lads HUD 1.7.0 capture END:", "Lads HUD flicker capture END:", "Lads item physics capture END:", "Lads inventory capture END:", "Lads server features capture END:",
    "Lads F3/FOV capture END:", "Lads HUD info capture END:", "Lads raised capture END:", "Lads mouse tweaks capture END:",
    "Lads resolution capture END:", "Lads dynamic lights capture END:", "Lads AppleSkin sync capture END:", "Lads Flashback probe END:"];
// A requested capture is settled once all its END markers passed or one of them failed; one not requested always is.
bool Settled(bool requested, params string[] ends) => !requested || ends.All(passedMarkers.ContainsKey) || ends.Any(failedCaptures.ContainsKey);
bool CapturesSettled(bool resolutionRequested) =>
    Settled(menuCaptureVerification, "Lads menu capture END:", "Lads mods view capture END:")
    && Settled(hudCaptureVerification, "Lads HUD capture END:", "Lads HUD editor probe END:")
    && Settled(bannerCaptureVerification, "Lads kill banner capture END:")
    && Settled(oldAnimCaptureVerification, "Lads 1.7 animations capture END:")
    && Settled(zoomCaptureVerification, "Lads zoom capture END:")
    && Settled(skinLayersCaptureVerification, "Lads skin layers capture END:")
    && Settled(sprintCaptureVerification, "Lads sprint capture END:")
    && Settled(hud170CaptureVerification, "Lads HUD 1.7.0 capture END:")
    && Settled(hudFlickerCaptureVerification, "Lads HUD flicker capture END:")
    && Settled(itemPhysicsCaptureVerification, "Lads item physics capture END:")
    && Settled(inventoryCaptureVerification, "Lads inventory capture END:")
    && Settled(serverCaptureVerification, "Lads server features capture END:")
    && Settled(f3FovCaptureVerification && !capabilities.Forge, "Lads F3/FOV capture END:")
    && Settled(hudInfoCaptureVerification, "Lads HUD info capture END:")
    && Settled(raisedCaptureVerification, "Lads raised capture END:")
    && Settled(mouseTweaksCaptureVerification, "Lads mouse tweaks capture END:")
    && Settled(resolutionRequested, "Lads resolution capture END:")
    && Settled(lightsCaptureVerification, "Lads dynamic lights capture END:")
    && Settled(appleSkinSyncVerification, "Lads AppleSkin sync capture END:");
var inventorySnapshots = new Dictionary<string, JsonNode>(StringComparer.Ordinal);
var keyLines = new List<string>();
LoadedModList modList = capabilities.Forge ? new ForgeModList() : new FabricModList();
var forgeList = modList as ForgeModList;
// 1.8.9 prints log4j XML on stdout, so its plain-text logs\latest.log is read instead (deleted before the start: only this run's).
string gameLog = Path.Combine(directory, "logs", "latest.log");
long gameLogRead = 0;
string catalogFile = Path.Combine(directory, "lads-core-catalog.json");
TimeSpan? forgeTitleAt = null; // 1.8.9: when FML had loaded every mod and the Core ticked at the title screen
string? optiFineWarning = null, sandboxBefore = null;
string[] requiredTitleProbes = capabilities.TitleProbes;
string[] requiredWorldProbes = capabilities.WorldProbes;
if (requestedFeaturesOnly)
{
    requiredWorldProbes = ["Lads native feature probe END:", "Lads improvements probe END:", "Lads font reload probe END:", "Lads world capture END:", "Lads shared content probe END:"];
    Console.WriteLine("Focused requested-feature verification: legacy crosshair/render-scale suites are not part of this run.");
}
if (cheatsPhase != null) requiredWorldProbes = [.. requiredWorldProbes, "Lads cheats probe END:"];
// Every run with LadsCore reports shared content and the mod inventory at the title screen (plus the in-game request when asked).
// The 1.8.9 Core has neither report: its runs are checked from logs\latest.log (Forge's mod list, OptiFine, the title screen).
var requiredCore = new List<string>();
bool welcomeVerification = Env("LADS_VERIFY_WELCOME") == "1";
if (welcomeVerification) requiredCore.Add("Lads welcome probe END:");
// Every version: the Add Server screen names known servers (AddServerProbe), screenshots/addserver-*.png.
bool addServerVerification = titleVerification && !expectCoreDisabled && Env("LADS_VERIFY_ADDSERVER") == "1";
if (addServerVerification) requiredCore.Add("Lads add-server probe END:");
if (!expectCoreDisabled && !capabilities.Forge) requiredCore.Add("Lads shared content probe END:");
if (modRequest != null) requiredCore.Add("Lads mod request probe END:");
string[] failureMarkers = ["Lads font reload probe FAILED", "Lads native feature probe FAILED", "Lads render scale probe FAILED", "Lads paper doll probe FAILED",
    "Lads native reconnect probe FAILED", "Lads dynamic FPS probe FAILED", "Lads background policy probe FAILED", "Lads auto-world QA FAILED",
    "Lads durability tooltip probe FAILED", "Lads native SignalLoss probe FAILED", "Lads tab tweaks probe FAILED", "Lads narrator probe FAILED",
    "Lads native screenshots probe FAILED", "Lads native crosshair probe FAILED", "Lads shared content probe FAILED",
    "Lads mod request probe FAILED", "Lads mods inventory snapshot FAILED", "Lads welcome probe FAILED", "Lads menu access probe FAILED",
    "Lads HUD pipeline probe FAILED", "Lads 1.8.9 core probe FAILED", "Lads cheats probe FAILED", "Lads zoom capture FAILED", "Lads sprint capture FAILED", "Lads add-server probe FAILED",
    "Lads HUD 1.7.0 capture FAILED", "Lads item physics capture FAILED", "Lads inventory capture FAILED",
    "Lads server features capture FAILED", "Lads chat heads capture FAILED", "Lads F3/FOV capture FAILED", "Lads HUD info capture FAILED",
    "Lads raised capture FAILED", "Lads raised title probe FAILED", "Lads mouse tweaks capture FAILED", "Lads resolution capture FAILED",
    "Lads dynamic lights capture FAILED", "Lads Flashback probe FAILED",
    "Lads AppleSkin sync capture FAILED",
    "Mod resolution encountered an incompatible mod set", "Incompatible mods found"];
bool CoreChecksDone() { lock (logGate) return requiredCore.All(passedMarkers.ContainsKey) && (capabilities.Forge || inventorySnapshots.ContainsKey("title")); }
// The Core writes its catalog on its first client tick, which 1.8.9 reaches with the title screen shown.
bool CatalogWritten() => File.Exists(catalogFile) && File.GetLastWriteTimeUtc(catalogFile) >= runStartUtc;
var jvmFlags = new List<string>();

try
{
    sandboxBefore = Snapshot(sharedRoot);
    await File.WriteAllTextAsync(Path.Combine(evidence, "sandbox-before.txt"), sandboxBefore, ct);
    bool coreRequested = SharedContentService.IsCoreRequested(directory, out var stateFileError);
    if (stateFileError != null) Console.WriteLine("Mod choices: " + stateFileError);
    if (expectCoreDisabled && coreRequested)
        throw new InvalidOperationException($"--expect-core-disabled, but lads-mod-state.json does not disable theladscore. Run --set-mods {version} <root> disable theladscore --dir {dirName} first.");
    if (!expectCoreDisabled && !coreRequested)
        throw new InvalidOperationException($"LadsCore is disabled for {dirName}; pass --expect-core-disabled or run --set-mods {version} <root> enable theladscore --dir {dirName}.");

    // The same pre-launch order as the launcher: shared content, installers (which honour lads-mod-state.json), inventory snapshot.
    // The 1.8.9 Core does not read the shared server list, so 1.8.9 gets a synced copy.
    bool coreReadsServers = coreRequested && !capabilities.Forge;
    var prepared = await shared.PrepareProfileAsync(directory, "QA " + dirName, null, coreReadsServers, null, ct);
    if (LinkedOutside(directory, sharedRoot) is { } linked)
        throw new InvalidOperationException($"SANDBOX: {linked}. Nothing was started.");
    string preparedText = $"Shared content prepare (coreEnabled={coreReadsServers}): skipped={prepared.Skipped} renamed={prepared.Renamed} pending={prepared.Pending} "
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
        // Upstream mods the Core replaces: this manifest's retired entries "Replaced by native Lads Core functionality".
        var nativePorts = (manifest["retired"]?.AsArray() ?? []).Select(entry => entry!.AsObject())
            .Where(entry => entry["reason"]?.GetValue<string>().Contains("Replaced by native Lads Core functionality", StringComparison.Ordinal) == true)
            .Select(entry => entry["modId"]!.GetValue<string>()).ToList();
        for (int index = mods.Count - 1; index >= 0; index--)
            if (mods[index]?["modId"]?.GetValue<string>() is { } modId && (nativePorts.Contains(modId) || excludedMods.Contains(modId))) mods.RemoveAt(index);
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
        Console.WriteLine(nativePorts.Count > 0 ? $"Native port QA: staged pack without replaced {string.Join(", ", nativePorts)} upstream jars. Production manifest preserved."
            : $"Native port QA: the {version} manifest retires no upstream jar for native Lads Core functionality yet; staged it unchanged. Production manifest preserved.");
        if (excludedMods.Count > 0) Console.WriteLine($"QA pack: left out {string.Join(", ", excludedMods)} (LADS_VERIFY_EXCLUDE_MODS). This run does not test the production pack.");
    }
    if (Env("LADS_VERIFY_V134") == "1")
    {
        var renderer = Env("LADS_VERIFY_RENDERER") == "OpenGL" ? GraphicsRenderer.OpenGl : GraphicsRenderer.Vulkan;
        await File.WriteAllTextAsync(Path.Combine(directory, "options.txt"), "preferredGraphicsBackend:\"" + (renderer == GraphicsRenderer.Vulkan ? "vulkan" : "opengl") + "\"\n", ct);
        await GraphicsRenderer.PrepareAsync(directory, version, renderer, Console.WriteLine, ct);
    }
    await ClientModInstaller.InstallAsync(packSource, directory, version, Console.WriteLine, ct);
    if (Env("LADS_VERIFY_V133") == "1")
    {
        string addon = Path.Combine(root,"TheLadsCore",version=="26.3"?"v26_3":"v26_2","build","verification","lads-jade-addon-qa.jar");
        File.Copy(addon,Path.Combine(directory,"mods","lads-jade-addon-qa.jar"),true);
    }
    // As LaunchService: OptiFine from optifine.net, checked against the pinned SHA-256 and cached in the sandbox launcher folder.
    // It fails open (the game starts without it); the verdict then says why OptiFine is missing.
    if (capabilities.Forge && (optiFineWarning = await OptiFineInstaller.InstallAsync(launcherData, directory, OptiFineInstaller.M5, Console.WriteLine, ct)) != null)
    {
        Console.WriteLine("OptiFine: " + optiFineWarning);
        summary.Add("OptiFine: " + optiFineWarning);
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
    int requiredJava = GameVersionPolicy.GetRequiredJavaMajor(version);
    string javaPath = await java.EnsureJavaAsync(requiredJava, cancellationToken: ct);
    int? javaMajor = java.GetJavaMajorVersion(javaPath);
    Console.WriteLine($"Java: {javaMajor}; Minecraft: {version}");
    // As LaunchService: 1.8.9 needs Java 8 exactly (Forge's LaunchWrapper crashes on Java 9 and newer).
    if (!GameVersionPolicy.AcceptsJava(version, requiredJava, javaMajor))
        throw new InvalidOperationException($"'{javaPath}' reports Java {javaMajor}; Minecraft {version} needs {GameVersionPolicy.DescribeJava(version, requiredJava)}.");
    using var http = new HttpClient();
    string id;
    if (capabilities.Forge)
    {
        // The launcher's own Forge path: Forge 11.15.1.2318 through CmlLib's Forge installer, then its manifest check.
        await LaunchService.InstallForgeAsync(launcher, directory, http, Console.WriteLine, ct);
        id = GameVersionPolicy.ForgeVersionId;
    }
    else
    {
        var fabric = new CmlLib.Core.ModLoaders.FabricMC.FabricInstaller(http);
        string loaderVersion = "0.19.5";
        id = await fabric.Install(version, loaderVersion, path);
        if (id != $"fabric-loader-{loaderVersion}-{version}") throw new InvalidOperationException("Fabric selected a different version.");
    }
    var session = AccountIdentity.CreateOfflineSession("LadsQA");
    await AccountExportService.WriteLaunchAsync(directory, session, true, new[] { new AccountSummary(session.Username!, session.UUID!, "offline") });
    await File.WriteAllTextAsync(Path.Combine(directory, "options.txt"), "fullscreen:false\nmaxFps:120\nrenderDistance:4\nsimulationDistance:4\nguiScale:2\ntutorialStep:none\n" + (Env("LADS_VERIFY_V134") == "1" ? "preferredGraphicsBackend:\"" + (Env("LADS_VERIFY_RENDERER") == "OpenGL" ? "opengl" : "vulkan") + "\"\n" : ""), ct);
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
    // Two real frames of the Lads loading screen (startup), saved to the sandbox's screenshots folder.
    if (titleVerification) AddJvm("-Dthelads.verifyLoadingScreen=true");
    if (addServerVerification) AddJvm("-Dthelads.verifyAddServer=true");
    if (capabilities.Forge)
    {
        // The 1.8.9 Core's one QA switch: its self-test (Lads menu on the title screen, in its own QA world and from the
        // pause-menu button, the 1.8.9 bridge, the launcher catalog). Every flag below is the Fabric Core's.
        if (autoWorldVerification) AddJvm("-Dthelads.verify189Core=true");
        if (focus189 != null) AddJvm("-Dthelads.verify189Focus=" + focus189);
        if (chatHeadsCaptureVerification) AddJvm("-Dthelads.verifyChatHeads=true");
        if (f3FovCaptureVerification) AddJvm("-Dthelads.verify189F3Fov=true");
        // LADS_VERIFY_189_ONLY=170: only the 1.7.0 in-world checks (Probe170Sprint, Probe170Hud), straight in the QA world;
        // =itemphysics: only Item Physics (Probe170ItemPhysics); =raised: only Raised and the paper doll (RaisedDollProbe189);
        // =leave: only the QA world's final leave after its server stopped first (the 1.7.0 freeze regression check).
        // =hudflicker: only runs of frames with the HUD FPS cap off and on (Probe172HudFlicker).
        if (autoWorldVerification && Env("LADS_VERIFY_189_ONLY") is "170" or "itemphysics" or "raised" or "leave" or "hudflicker") AddJvm("-Dthelads.verify189Only=" + Env("LADS_VERIFY_189_ONLY"));
    }
    else
    {
        // A crash at startup exits with its log (the run fails) instead of opening Fabric Loader's error window on the user's screen.
        AddJvm("-Dfabric.noGui=true");
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
        if (titleCaptureVerification) AddJvm("-Dthelads.verifyTitleCapture=true");
        if (Env("LADS_VERIFY_V134") == "1") { AddJvm("-Dthelads.verify134=true"); AddJvm("-Dthelads.verifyRenderer=" + (Env("LADS_VERIFY_RENDERER") == "OpenGL" ? "opengl" : "vulkan")); }
        if (Env("LADS_VERIFY_SKIN_NETWORK") == "1") AddJvm("-Dthelads.verifySkinNetwork=true");
        if (flashbackVerification) AddJvm("-Dthelads.verifyFlashback=true");
        if (nativePortsVerification) AddJvm("-Dthelads.verifyBackgroundPolicies=true");
        AddJvm("-Dthelads.verifySharedContent=true");
        if (sharedRole != null) AddJvm("-Dthelads.sharedContentRole=" + sharedRole);
        if (runId != null) AddJvm("-Dthelads.sharedContentRunId=" + runId);
        AddJvm("-Dthelads.verifyModInventory=true");
        if (modRequest != null) AddJvm("-Dthelads.verifyModRequest=" + modRequest);
        if (chatHeadsCaptureVerification) AddJvm("-Dthelads.verifyChatHeads=true");
    }
    process.StartInfo.UseShellExecute = false;
    process.StartInfo.CreateNoWindow = true;
    process.StartInfo.RedirectStandardOutput = true;
    process.StartInfo.RedirectStandardError = true;
    GameSession.Configure(process.StartInfo, directory, shared.Root);
    summary.Add($"Child environment: THELADS_DIR={directory}; {SharedContentService.RootEnvironmentVariable}={shared.Root}; {GameOptionsService.LunarEnvironmentVariable}={lunarRoot}");
    summary.Add("JVM QA flags: " + string.Join(' ', jvmFlags));
    log = new StreamWriter(logPath) { AutoFlush = true };
    void WriteLine(object sender, DataReceivedEventArgs e)
    {
        if (e.Data == null) return;
        lock (logGate)
        {
            if (log == null) return;
            log.WriteLine(e.Data);
            // 1.8.9's stdout is log4j XML: its lines are read from logs\latest.log instead (ReadGameLog).
            if (!capabilities.Forge) ReadLine(e.Data);
        }
    }
    // 1.8.9: the lines logs\latest.log gained since the last read, up to its last complete line.
    void ReadGameLog()
    {
        if (!capabilities.Forge || !File.Exists(gameLog)) return;
        using var added = new MemoryStream();
        using (var stream = new FileStream(gameLog, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
        {
            if (stream.Length < gameLogRead) gameLogRead = 0; // a new file
            stream.Position = gameLogRead;
            stream.CopyTo(added);
        }
        byte[] bytes = added.ToArray();
        int end = Array.LastIndexOf(bytes, (byte)'\n');
        if (end < 0) return;
        gameLogRead += end + 1;
        foreach (string line in Encoding.UTF8.GetString(bytes, 0, end).Split('\n')) ReadLine(line.TrimEnd('\r'));
    }
    // One game log line: the loaded-mods list, then init, probe, capture and snapshot markers (lock is re-entrant).
    void ReadLine(string line)
    {
        lock (logGate)
        {
            if (modList.Feed(line)) return;
            if (line.Contains($"TheLadsCore {version} initialized successfully")) initialized = true;
            if (line.Contains("Lads integration write probe END:") && Passed(line)) settingsProbePassed = true;
            if (failureMarkers.Any(line.Contains)) nativeProbeFailed = true;
            foreach (string end in captureEnds)
                if (line.Contains(end.Replace(" END:", " FAILED"))) failedCaptures.TryAdd(end, 0);
            if (line.Contains("Lads 1.3.4 screenshots probe END:") && Passed(line)) screenshots134Passed = true;
            if (line.Contains("Lads 1.3.4 replay probe END:") && Passed(line)) replay134Passed = true;
            if (line.Contains("Lads Flashback probe END:") && Passed(line)) flashbackPassed = true;
            if (line.Contains("Lads 1.3.4 renderer probe END:") && Passed(line)) renderer134Passed = true;
            if (line.Contains("Lads 1.3.3 probe END:") && Passed(line)) version133ProbePassed = true;
            foreach (string marker in requiredTitleProbes.Concat(requiredCore))
                if (line.Contains(marker) && Passed(line)) passedMarkers.TryAdd(marker, 0);
            foreach (string marker in requiredWorldProbes)
                if (line.Contains(marker) && Passed(line)) passedMarkers.TryAdd(marker, 0);
            foreach (string marker in new[] { "Lads menu capture END:", "Lads mods view capture END:", "Lads HUD capture END:", "Lads HUD editor probe END:", "Lads kill banner capture END:",
                "Lads 1.7 animations capture END:", "Lads zoom capture END:", "Lads skin layers capture END:", "Lads sprint capture END:", "Lads HUD 1.7.0 capture END:",
                "Lads HUD flicker capture END:", "Lads item physics capture END:", "Lads inventory capture END:", "Lads title capture END:", "Lads title More capture END:",
                "Lads server features capture END:", "Lads F3/FOV capture END:",
                "Lads HUD info capture END:", "Lads raised capture END:", "Lads mouse tweaks capture END:", "Lads resolution capture END:",
                "Lads dynamic lights capture END:", "Lads AppleSkin sync capture END:" })
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
                || line.Contains("initialized successfully") || failureMarkers.Any(line.Contains)
                || (forgeList != null && (line.Contains("Forge Mod Loader has") || line.Contains("Loading tweaker ") || line.Contains("MIXIN Subsystem")));
            if (key && snapshotAt < 0) keyLines.Add(Clean(line));
            if (line.Contains("Lads integration write") || (key && snapshotAt < 0)) Console.WriteLine(Clean(line));
            else if (line.Contains("ERROR") || line.Contains("Exception") || line.Contains("Initializing TheLadsCore")) Console.WriteLine(line);
        }
    }
    process.OutputDataReceived += WriteLine;
    process.ErrorDataReceived += WriteLine;
    if (capabilities.Forge && File.Exists(gameLog)) File.Delete(gameLog); // only this run's lines are read
    // QA instances are muted and kept light (owner's standing rule): the owner may be using the computer meanwhile.
    // 120 FPS cap (VSync off so the cap is what applies), render/simulation distance 4, GUI scale 2 (1.8.9 ignores simulationDistance);
    // pauseOnLostFocus off: a pause menu when the owner clicks away would stop the integrated server and the probes' input.
    string[] qaForced = { "soundCategory_master:0.0", "maxFps:120", "enableVsync:false", "renderDistance:4", "simulationDistance:4", "guiScale:2",
        "pauseOnLostFocus:false" };
    string qaOptions = Path.Combine(directory, "options.txt");
    var qaLines = File.Exists(qaOptions)
        ? File.ReadAllLines(qaOptions).Where(l => !qaForced.Any(f => l.StartsWith(f[..(f.IndexOf(':') + 1)], StringComparison.Ordinal))).ToList()
        : new List<string>();
    qaLines.AddRange(qaForced);
    File.WriteAllLines(qaOptions, qaLines);
    QaDiscord.ForceOff(directory); // QA games never show on the owner's Discord (Essential's activity status, Lads DiscordRPC)
    var stopwatch = Stopwatch.StartNew();
    // 26.3 (SDL) shows its window without activating it; QaWindowGuard covers 1.8.9 and 26.2 (and moves every game window).
    if (Environment.GetEnvironmentVariable("LADS_VERIFY_FOCUS") != "1") process.StartInfo.Environment["SDL_WINDOW_ACTIVATE_WHEN_SHOWN"] = "0";
    process.Start();
    QaWindowGuard.Watch(process);
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
            Console.WriteLine($"Game window detected at {stopwatch.Elapsed.TotalSeconds:F1}s; waiting for {(expectCoreDisabled ? modList.Name + "'s loaded mod list" : "UI verification")}.");
        }
        await Task.Delay(500, ct);
        ReadGameLog();
        // 1.8.9's title screen: FML loaded every mod and the Core ticked (its catalog export on the first client tick).
        if (forgeList != null && forgeTitleAt == null && forgeList.LoadedMods != null && (expectCoreDisabled || CatalogWritten()))
        {
            forgeTitleAt = stopwatch.Elapsed;
            Console.WriteLine($"Title screen at {stopwatch.Elapsed.TotalSeconds:F1}s: FML loaded {forgeList.LoadedMods} mods{(expectCoreDisabled ? "" : " and the Core ticked")}; watching it 10 s for errors.");
        }
        if (expectCoreDisabled)
        {
            // No LadsCore hooks exist in this process: the window and Fabric's loaded list are the whole signal.
            if (nativeProbeFailed || (windowFound && modList.Parsed && stopwatch.Elapsed - windowAt > TimeSpan.FromSeconds(15)
                && (forgeList == null || forgeTitleAt != null))) break;
            continue;
        }
        if (autoWorldVerification && requiredTitleProbes.Concat(requiredWorldProbes).All(passedMarkers.ContainsKey) && CoreChecksDone())
        {
            // Settled: passed, or failed (then the run fails at the end, after the other requested captures had their turn).
            bool menuDone = Settled(menuCaptureVerification, "Lads menu capture END:", "Lads mods view capture END:");
            bool hudDone = Settled(hudCaptureVerification, "Lads HUD capture END:", "Lads HUD editor probe END:");
            if (CapturesSettled(resolutionCaptureVerification))
            {
                if (Env("LADS_VERIFY_V134") == "1" && !screenshots134Passed)
                {
                    if (!screenshots134Requested)
                    {
                        await LockFiles.WriteAtomicallyAsync(Path.Combine(directory, ".lads-qa-screenshots134"), Encoding.UTF8.GetBytes("Verify the external gallery in this sandbox."), ct);
                        screenshots134Requested = true;
                        Console.WriteLine("Requesting 1.3.4 external screenshot gallery verification.");
                    }
                }
                else if (Env("LADS_VERIFY_REPLAY") == "1" && !replay134Passed)
                {
                    if (!replay134Requested)
                    {
                        await LockFiles.WriteAtomicallyAsync(Path.Combine(directory, ".lads-qa-replay"), Encoding.UTF8.GetBytes("Record, reopen and export this isolated QA world."), ct);
                        replay134Requested = true;
                        Console.WriteLine("Requesting real Flashback recording, replay and PNG export.");
                    }
                }
                else if (flashbackVerification && !flashbackPassed && !failedCaptures.ContainsKey("Lads Flashback probe END:"))
                {
                    if (!flashbackRequested)
                    {
                        await LockFiles.WriteAtomicallyAsync(Path.Combine(directory, ".lads-qa-flashback"), Encoding.UTF8.GetBytes("Record into the Lads replay folder and time stock vs Lads exports."), ct);
                        flashbackRequested = true;
                        Console.WriteLine("Requesting Flashback Settings recording and stock vs Lads exports.");
                    }
                }
                else break;
            }
            // The world captures hold items, keys and the zoom, which a screen opened by a world probe (the screenshots gallery,
            // the pause menu) cuts short: they are asked for once those probes have passed. The Core runs them one at a time and
            // opens the menu or HUD capture only between them.
            if (!worldCapturesRequested)
            {
                foreach (var (asked, request, text) in new[] {
                    (bannerCaptureVerification, bannerCaptureRequest, "Fire every kill banner skin in the QA world and capture its frames."),
                    (oldAnimCaptureVerification, oldAnimCaptureRequest, "Pose 1.7 Animations in the QA world and capture its frames."),
                    (zoomCaptureVerification, zoomCaptureRequest, "Drive Lads Zoom in the QA world and capture its frames."),
                    (skinLayersCaptureVerification, skinLayersCaptureRequest, "Photograph the player's 3D skin layers with SkinLayers on and off."),
                    (sprintCaptureVerification, sprintCaptureRequest, "Walk Toggle Sprint & Sneak through the QA world and log every tick's sprint packets."),
                    (hud170CaptureVerification, hud170CaptureRequest, "Capture the 1.7.0 HUD changes in the QA world."),
                    (hudFlickerCaptureVerification, hudFlickerCaptureRequest, "Capture runs of frames with the HUD FPS cap off and on."),
                    (itemPhysicsCaptureVerification, itemPhysicsCaptureRequest, "Drop items in an Item Physics arena and capture its frames."),
                    (inventoryCaptureVerification, inventoryCaptureRequest, "Capture the inventories with and without potion effects in the QA world."),
                    (serverCaptureVerification, serverCaptureRequest, "Check the multiplayer features in the QA world and capture their frames."),
                    (f3FovCaptureVerification && !capabilities.Forge, f3FovCaptureRequest, "Capture Better F3 frames and log Custom FOV values."),
                    (hudInfoCaptureVerification, hudInfoCaptureRequest, "Photograph food previews, tooltips and crosshair styles in the QA world."),
                    (raisedCaptureVerification, raisedCaptureRequest, "Capture Raised and the paper doll in the QA world."),
                    (mouseTweaksCaptureVerification, mouseTweaksCaptureRequest, "Drive Lads Mouse Tweaks in a QA chest and capture its frames."),
                    (lightsCaptureVerification, lightsCaptureRequest, "Light the QA world at midnight with Dynamic Lights and capture its frames."),
                    (appleSkinSyncVerification, appleSkinSyncRequest, "Inject AppleSkin server payloads in the QA world and capture the food bar.") })
                    if (asked) await LockFiles.WriteAtomicallyAsync(request, Encoding.UTF8.GetBytes(text), ct);
                worldCapturesRequested = true;
            }
            if (!menuDone && !menuCaptureRequested)
            {
                await LockFiles.WriteAtomicallyAsync(menuCaptureRequest, Encoding.UTF8.GetBytes("Capture the native Lads mods menu after all world probes pass."), ct);
                menuCaptureRequested = true;
                Console.WriteLine("World probes passed; requesting actual Lads menu and Installed mods frames from the QA game.");
            }
            if (menuDone && !hudDone && !hudCaptureRequested)
            {
                await LockFiles.WriteAtomicallyAsync(hudCaptureRequest, Encoding.UTF8.GetBytes("Verify the native HUD editor and capture its completed framebuffer."), ct);
                hudCaptureRequested = true;
                Console.WriteLine("Requesting native HUD editor interaction checks and actual frame capture.");
            }
        }
        // Better Resolution's capture sets the same world-scale settings as the render scale probe, so it waits for that probe and the
        // other GPU frame probe; it also runs when an unrelated probe failed (e.g. 1.7 Animations' hand checks under an Iris shader pack).
        if (resolutionCaptureVerification && !resolutionCaptureRequested && passedMarkers.ContainsKey("Lads render scale probe END:")
            && passedMarkers.ContainsKey("Lads native screenshots probe END:") && (nativeProbeFailed || requiredWorldProbes.All(passedMarkers.ContainsKey)))
        {
            await LockFiles.WriteAtomicallyAsync(resolutionCaptureRequest, Encoding.UTF8.GetBytes("Photograph Better Resolution at each setting in the QA world."), ct);
            resolutionCaptureRequested = true;
        }
        // Allow independent world/GPU probes to finish after a restored-state assertion fails.
        // The run still fails below; collecting their evidence avoids hiding subsequent defects.
        // Once the world captures were asked for, every requested one (and a requested Flashback probe) settles first.
        if (nativeProbeFailed && (!autoWorldVerification || capabilities.Forge || passedMarkers.ContainsKey("Lads native screenshots probe END:")
            || stopwatch.Elapsed > TimeSpan.FromSeconds(90))
            && (Settled(resolutionCaptureRequested, "Lads resolution capture END:") || stopwatch.Elapsed > TimeSpan.FromSeconds(180))
            && (!worldCapturesRequested || (CapturesSettled(resolutionCaptureRequested) && (!flashbackRequested || flashbackPassed || failedCaptures.ContainsKey("Lads Flashback probe END:"))))) break;
        if (earlyTitleExit && windowFound && initialized && CoreChecksDone() && modList.Parsed
            && (!nativePortsVerification || requiredTitleProbes.All(passedMarkers.ContainsKey))
            && (forgeList == null || stopwatch.Elapsed - forgeTitleAt >= TimeSpan.FromSeconds(10))) break;
        if (!titleVerification && windowFound && initialized && (!settingsVerification || settingsProbePassed)
            && (!nativePortsVerification || requiredTitleProbes.All(passedMarkers.ContainsKey)) && CoreChecksDone()
            && stopwatch.Elapsed > TimeSpan.FromSeconds(20)) break;
    }
    ReadGameLog();
    bool exitedOnItsOwn = process.HasExited;
    if (exitedOnItsOwn) Console.WriteLine($"Game exit: {process.ExitCode}");
    lock (logGate)
    {
        void Require(bool ok, string message) { if (!ok) failures.Add(message); }
        Require(windowFound, "No game window was observed. Inspect production-smoke.log.");
        Require(!exitedOnItsOwn || process.ExitCode == 0, "Game exited with an error.");
        Require(Env("LADS_VERIFY_V134") != "1" || screenshots134Passed, "1.3.4 external screenshot gallery probe did not finish.");
        Require(Env("LADS_VERIFY_REPLAY") != "1" || replay134Passed, "Flashback record/replay/export probe did not finish.");
        Require(!flashbackVerification || flashbackPassed, "Flashback Settings probe did not finish.");
        Require(Env("LADS_VERIFY_V134") != "1" || renderer134Passed, "1.3.4 actual renderer/Flashback probe did not finish.");
        Require(Env("LADS_VERIFY_V133") != "1" || version133ProbePassed, "1.3.3 native/API probe did not finish.");
        Require(!nativeProbeFailed, $"A runtime probe or {modList.Name} reported a failure (see the FAILED lines). Inspect {(capabilities.Forge ? @"logs\latest.log" : "production-smoke.log")}.");
        if (expectCoreDisabled)
        {
            Require(!initialized, "LadsCore initialized although it is disabled for this folder.");
            Require(!exitedOnItsOwn, "The game exited on its own before the harness stopped it.");
        }
        else
        {
            Require(initialized, $"LadsCore did not log 'TheLadsCore {version} initialized successfully'.");
            Require(requiredCore.All(passedMarkers.ContainsKey), "Missing passing END markers: " + string.Join(", ", requiredCore.Where(m => !passedMarkers.ContainsKey(m))));
            Require(capabilities.Forge || (inventorySnapshots.ContainsKey("title") && !snapshotInvalid), "The title-screen 'Lads mods inventory snapshot:' line was missing or not valid JSON.");
        }
        if (forgeList != null)
        {
            // 1.8.9, from logs\latest.log: the title screen, OptiFine and no mixin or coremod error. Nothing stops the game but the harness.
            Require(forgeList.LoadedMods != null, @"FML did not finish loading ('Forge Mod Loader has successfully loaded N mods' is not in logs\latest.log).");
            Require(expectCoreDisabled || CatalogWritten(), "The Core never ticked at the title screen: it did not write lads-core-catalog.json during this run.");
            Require(forgeList.OptiFineTweaker && forgeList.OptiFine != null,
                @"OptiFine was not loaded (no OptiFine tweaker and FML detection in logs\latest.log)" + (optiFineWarning != null ? ": " + optiFineWarning : "."));
            Require(forgeList.Errors.Count == 0, @"Mixin or coremod errors in logs\latest.log: " + string.Join(" | ", forgeList.Errors.Take(3)));
            if (!expectCoreDisabled) Require(!exitedOnItsOwn, @"The game exited on its own before the harness stopped it (see logs\latest.log).");
            string forgeText = $"Forge (logs\\latest.log): FML loaded {forgeList.LoadedMods?.ToString() ?? "no"} mods, title screen {(forgeTitleAt is { } at ? $"at {at.TotalSeconds:F1}s" : "not reached")}, "
                + $"OptiFine {(forgeList.OptiFineTweaker ? forgeList.OptiFine ?? "tweaker without FML detection" : "not loaded")}, {forgeList.Errors.Count} mixin or coremod errors";
            Console.WriteLine(forgeText);
            keyLines.Add(forgeText);
        }
        Require(modList.Parsed, modList.NotFound);
        // Fabric's N counts distinct ids (a library nested in several jars is listed under each).
        Require(!modList.Parsed || modList.Declared == modList.Distinct,
            $"{modList.Name} declared {modList.Declared} mods but the harness parsed {modList.Distinct} distinct ids; the loaded-mods parse is incomplete.");
        foreach (var absent in expectAbsent)
        {
            if (modList.Top.ContainsKey(absent)) failures.Add($"Expected absent, but {modList.Name} loaded {absent} as a top-level mod.");
            foreach (var nested in modList.Nested.Where(n => n.Id == absent)) failures.Add($"Expected absent, but {modList.Name} loaded {absent} nested in {nested.Parent}.");
        }
        foreach (var present in expectPresent)
            Require(modList.Top.ContainsKey(present), $"Expected present, but {modList.Name} did not load {present} as a top-level mod.");
        if (modList.Parsed)
        {
            string listText = $"{modList.Name} loaded list: declared {modList.Declared}, {modList.Top.Count} top-level, {modList.Nested.Count} nested entries, {modList.Distinct} distinct ids; "
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
        Require(!bannerCaptureVerification || passedMarkers.ContainsKey("Lads kill banner capture END:"),
            "The requested Kill Banner frames were not all captured.");
        Require(!oldAnimCaptureVerification || passedMarkers.ContainsKey("Lads 1.7 animations capture END:"),
            "The requested 1.7 Animations frames were not all captured.");
        Require(!zoomCaptureVerification || passedMarkers.ContainsKey("Lads zoom capture END:"),
            "The requested Lads Zoom capture did not pass. Inspect production-smoke.log.");
        Require(!skinLayersCaptureVerification || passedMarkers.ContainsKey("Lads skin layers capture END:"),
            "The requested 3D skin layers frames were not both captured.");
        Require(!sprintCaptureVerification || passedMarkers.ContainsKey("Lads sprint capture END:"),
            "The requested Toggle Sprint & Sneak capture did not pass. Inspect production-smoke.log.");
        Require(!hud170CaptureVerification || passedMarkers.ContainsKey("Lads HUD 1.7.0 capture END:"),
            "The requested 1.7.0 HUD capture did not pass. Inspect production-smoke.log.");
        Require(!hudFlickerCaptureVerification || passedMarkers.ContainsKey("Lads HUD flicker capture END:"),
            "The requested HUD flicker frames were not all captured. Inspect production-smoke.log.");
        Require(!itemPhysicsCaptureVerification || passedMarkers.ContainsKey("Lads item physics capture END:"),
            "The requested Item Physics capture did not pass. Inspect production-smoke.log.");
        Require(!inventoryCaptureVerification || passedMarkers.ContainsKey("Lads inventory capture END:"),
            "The requested inventory capture did not pass. Inspect production-smoke.log.");
        Require(!titleCaptureVerification || passedMarkers.ContainsKey("Lads title capture END:") && passedMarkers.ContainsKey("Lads title More capture END:"),
            "The requested title and More frames were not both captured. Inspect production-smoke.log.");
        Require(!serverCaptureVerification || passedMarkers.ContainsKey("Lads server features capture END:"),
            "The requested multiplayer features capture did not pass. Inspect production-smoke.log.");
        Require(!f3FovCaptureVerification || capabilities.Forge || passedMarkers.ContainsKey("Lads F3/FOV capture END:"),
            "The requested Better F3 / Custom FOV capture did not pass. Inspect production-smoke.log.");
        Require(!hudInfoCaptureVerification || passedMarkers.ContainsKey("Lads HUD info capture END:"),
            "The requested food, tooltip and crosshair frames were not all captured. Inspect production-smoke.log.");
        Require(!raisedCaptureVerification || passedMarkers.ContainsKey("Lads raised capture END:"),
            "The requested Raised and paper doll capture did not pass. Inspect production-smoke.log.");
        Require(!mouseTweaksCaptureVerification || passedMarkers.ContainsKey("Lads mouse tweaks capture END:"),
            "The requested Lads Mouse Tweaks capture did not pass. Inspect production-smoke.log.");
        Require(!resolutionCaptureVerification || passedMarkers.ContainsKey("Lads resolution capture END:"),
            "The requested Better Resolution capture did not pass. Inspect production-smoke.log.");
        Require(!lightsCaptureVerification || passedMarkers.ContainsKey("Lads dynamic lights capture END:"),
            "The requested Dynamic Lights capture did not pass. Inspect production-smoke.log.");
        Require(!appleSkinSyncVerification || passedMarkers.ContainsKey("Lads AppleSkin sync capture END:"),
            "The requested AppleSkin server payload capture did not pass. Inspect production-smoke.log.");
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
        if ((autoWorldVerification || welcomeVerification) && !capabilities.Forge)
        {
            await LockFiles.WriteAtomicallyAsync(stopRequest, Encoding.UTF8.GetBytes("Gracefully stop this isolated QA game."));
            using var exitTimeout = new CancellationTokenSource(TimeSpan.FromSeconds(15));
            try { await process.WaitForExitAsync(exitTimeout.Token); }
            catch (OperationCanceledException) { Console.WriteLine("QA graceful shutdown timed out; stopping only the QA process."); }
        }
        if (!process.HasExited)
        {
            Console.WriteLine(expectCoreDisabled ? "Stopping the QA game with Kill: LadsCore is disabled, so this process has no graceful-stop hook."
                : capabilities.Forge ? "Stopping the QA game with Kill: the 1.8.9 Core has no graceful-stop hook (its self-test saves and leaves its QA world first)."
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
        + (fallback ? $"fallback server-list copy ({(capabilities.Forge ? "the 1.8.9 Core does not read the shared list" : "LadsCore disabled")}) {(handled ? "passed to GameSession's reconcile into the shared servers.dat" : "NOT reconciled")}"
            : "no fallback server-list copy (LadsCore reads the shared servers.dat)")
        + string.Concat(sessionMessages.Select(m => "\n  session: " + m));
    Console.WriteLine(exitText);
    summary.Add(exitText);
}
lock (logGate) { log?.Dispose(); log = null; }
if (File.Exists(logPath)) File.Copy(logPath, Path.Combine(evidence, $"production-smoke-{scenario}.log"), overwrite: true);
if (capabilities.Forge) CopyIfExists(gameLog, Path.Combine(evidence, $"latest-{scenario}.log"));
// Catalog parity with 26.2, the 1.3.5 target: informational, or a failure with LADS_VERIFY_PARITY=1 (the release gate).
// 1.8.9's first-release scope is deliberately smaller (artifacts\1.4.0\PLAN-1.4.0.md): there it is only reported.
bool parityGate = Env("LADS_VERIFY_PARITY") == "1" && !capabilities.Forge;
if (!expectCoreDisabled && process != null && HasStarted(process))
{
    var parityLines = new List<string>();
    if (capabilities.Forge)
        parityLines.Add($"Lads catalog parity: report only on {version}, whose first-release scope is deliberately smaller than 26.2's; LADS_VERIFY_PARITY does not apply.");
    try
    {
        var reference = CatalogSupport(Path.Combine(verificationRoot, "26.2-title", "lads-core-catalog.json"));
        var current = CatalogSupport(Path.Combine(directory, "lads-core-catalog.json"));
        var builtIn = reference.Where(module => module.Value.Support == "builtIn").Select(module => module.Key).ToList();
        // Equivalent: built in, or an upstream mod this pack installs behind the module's Lads card.
        var missing = builtIn.Where(name => !current.TryGetValue(name, out var here) || !(here.Support == "builtIn" || here.Support == "external" && here.Label == "Installed mod"))
            .Select(name => current.TryGetValue(name, out var here) ? $"{name} ({here.Support}{(here.Support == "external" ? ": " + here.Label : "")})" : $"{name} (absent)").ToList();
        parityLines.Add($"Lads catalog parity END: {builtIn.Count} checks, {missing.Count} missing");
        if (missing.Count > 0)
        {
            parityLines.Add($"Lads catalog parity: built in on 26.2 but neither built in nor an installed external mod on {version}: {string.Join(", ", missing)}");
            if (parityGate) failures.Add($"LADS_VERIFY_PARITY=1: {missing.Count} of {builtIn.Count} modules built in on 26.2 are missing on {version}.");
        }
    }
    catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException or InvalidOperationException or KeyNotFoundException or NullReferenceException)
    {
        parityLines.Add($"Lads catalog parity FAILED: {e.GetType().Name}: {e.Message}");
        if (parityGate) failures.Add(parityLines[^1]);
    }
    foreach (var line in parityLines) { Console.WriteLine(line); keyLines.Add(line); }
}
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
    foreach (var png in new DirectoryInfo(screenshots).EnumerateFiles().Where(f => f.Extension is ".png" or ".csv" && f.LastWriteTimeUtc >= runStartUtc))
    {
        Directory.CreateDirectory(Path.Combine(evidence, "screenshots"));
        png.CopyTo(Path.Combine(evidence, "screenshots", png.Name), overwrite: true);
    }
CopyIfExists(Path.Combine(directory, ModPreferences.FileName), Path.Combine(evidence, "lads-mod-state.after.json"));
string sandboxAfter = Snapshot(sharedRoot);
await File.WriteAllTextAsync(Path.Combine(evidence, "sandbox-after.txt"), sandboxAfter);
if (LinkedOutside(directory, sharedRoot) is { } linkedAfter) failures.Add($"SANDBOX: after the run {linkedAfter}.");
string loadedText = $"{modList.Name} loaded {modList.Top.Count} top-level mods (declared {modList.Declared})"
    + (expectAbsent.Count > 0 ? $", none of [{string.Join(", ", expectAbsent)}]" : "")
    + (expectPresent.Count > 0 ? $", including [{string.Join(", ", expectPresent)}]" : "");
string coreText = forgeList != null
    ? $"core initialized, OptiFine {forgeList.OptiFine ?? "(missing)"} loaded next to it, no mixin or coremod errors in logs\\latest.log"
        + (autoWorldVerification ? " and the Core's 1.8.9 self-test ended with 0 failed" : "")
    : $"core initialized, {string.Join(", ", requiredCore.Select(m => m.TrimEnd(':')))} and the title inventory snapshot passed";
string passLine = expectCoreDisabled
    ? $"PRODUCTION SMOKE PASS (LadsCore disabled): correct version, native window observed, {loadedText}; stopped with Kill because no LadsCore hooks exist. Online sign-in is not exercised."
    : requestedFeaturesOnly
        ? $"PRODUCTION SMOKE PASS (requested-features only; the broader 26.x suites were not run): correct version, {coreText}, native window observed, {loadedText}. Online sign-in is not exercised."
        : $"PRODUCTION SMOKE PASS: correct version, {coreText}, native window observed, {loadedText}. Online sign-in is not exercised.";
if (excludedMods.Count > 0) passLine = passLine.Replace("PRODUCTION SMOKE PASS", $"PRODUCTION SMOKE PASS (QA pack without {string.Join(", ", excludedMods)}, not the production pack)");
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

// A shared folder of the game folder that links outside the sandbox shared root: "saves -> <target>", or null when none does.
static string? LinkedOutside(string gameDirectory, string sharedRoot)
{
    var root = SafeFileOps.GetFinalPath(sharedRoot);
    var links = SharedContentService.SharedFolders.Select(folder => Path.Combine(gameDirectory, folder)).Where(SafeFileOps.IsLink)
        .Where(link => SafeFileOps.GetLinkTarget(link) is not { } target || !SafeFileOps.IsSameOrInside(SafeFileOps.GetFinalPath(target), root))
        .Select(link => $"{Path.GetFileName(link)} -> {SafeFileOps.GetLinkTarget(link) ?? "?"}").ToList();
    return links.Count == 0 ? null : $"the QA game folder links outside the shared sandbox '{sharedRoot}': {string.Join(", ", links)}";
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

// Each module's catalog support ("builtIn", "external", "pending", "unavailable") and label, by module name.
static Dictionary<string, (string Support, string Label)> CatalogSupport(string file) =>
    JsonNode.Parse(File.ReadAllText(file))!["modules"]!.AsArray().Select(module => module!.AsObject())
        .ToDictionary(module => module["name"]!.GetValue<string>(), module => (module["support"]!.GetValue<string>(), module["label"]?.GetValue<string>() ?? ""), StringComparer.Ordinal);

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

/// <summary>What LadsCore can verify on each version: the markers each run requires and the probes an env flag may ask for.
/// 1.21.x gains entries as the 1.3.5 units port features; the 26.x lists are the ones every 26.x run already required.
/// <see cref="Forge"/>: 1.8.9, whose Core is a Forge mod read from logs\latest.log with only its own self-test as a world probe.</summary>
sealed record QaCapabilities(bool SharedCreate, bool RenderScale, bool Welcome, bool Version133, bool Version134, bool Replay,
    bool RequestedFeatures, string[] TitleProbes, string[] WorldProbes)
{
    public static readonly string[] Supported = ["1.8.9", "1.21.11", "26.2", "26.3"];

    public bool Forge { get; init; }
    /// <summary>The Kill Banner skins exist in the Fabric Cores (1.21.x and 26.x), not on 1.8.9.</summary>
    public bool KillBanner { get; init; }

    public static QaCapabilities For(string version) => version is "26.2" or "26.3"
        ? new(true, true, true, true, GraphicsRenderer.SupportsVulkan(version), true, true,
            ["Lads raised title probe END:", "Lads native reconnect probe END:", "Lads background policy probe END:", "Lads native SignalLoss probe END:", "Lads narrator probe END:"],
            ["Lads native feature probe END:", "Lads food probe END:",
                "Lads paper doll probe END:", "Lads render scale probe END:", "Lads world capture END:",
                "Lads durability tooltip probe END:", "Lads tab tweaks probe END:", "Lads clumps server probe END:", "Lads native screenshots probe END:", "Lads native crosshair probe END:",
                "Lads shared content probe END:"]) { KillBanner = true }
        // 1.8.9 (Forge): the C1 Core self-test (menu key, pause-menu button, bridge in its own QA world, launcher catalog) is its one probe.
        : GameVersionPolicy.UsesForge(version)
            ? new(false, false, false, false, false, false, false, [], ["Lads 1.8.9 core probe END:"]) { Forge = true }
        // 1.21.x: NativeWorldVerification on its own QA save, the native feature probe, the U1 menu access probe and the U3 HUD pipeline probe.
        : new(false, false, false, false, false, false, false, [],
            ["Lads native feature probe END:", "Lads menu access probe END:", "Lads HUD pipeline probe END:", "Lads world capture END:", "Lads shared content probe END:"]) { KillBanner = true };
}

/// <summary>The mods a loader reported loading, from the game's log lines.</summary>
abstract class LoadedModList
{
    public abstract string Name { get; }
    /// <summary>The failure when the list never appeared.</summary>
    public abstract string NotFound { get; }
    public int? Declared { get; protected set; }
    public bool Parsed { get; protected set; }
    public Dictionary<string, string> Top { get; } = new(StringComparer.Ordinal);
    public List<(string Id, string Version, string Parent)> Nested { get; } = new();
    public int Distinct => Top.Keys.Union(Nested.Select(n => n.Id)).Count();

    /// <summary>True when the line was part of the list (its first line or an entry) and needs no other reading.</summary>
    public abstract bool Feed(string line);
}

/// <summary>Forge 1.8.9's FML lines in logs\latest.log: "has identified N mods to load", the mod ids in "Attempting connection
/// with missing mods [mcp, FML, Forge, theladscore] at CLIENT" (every loaded mod, FML's wording), "has successfully loaded N
/// mods" (the title screen is next), OptiFine's tweaker and FML's detection of it, and mixin or coremod errors. Versions come
/// from the integrated server's "Client attempting to join with N mods : id@version,..." when a world is opened.</summary>
sealed class ForgeModList : LoadedModList
{
    private static readonly Regex Identified = new(@"Forge Mod Loader has identified (\d+) mods to load");
    private static readonly Regex Loaded = new(@"Forge Mod Loader has successfully loaded (\d+) mods");
    private static readonly Regex List = new(@"Attempting connection with missing mods \[([^\]]*)\] at CLIENT");
    private static readonly Regex Versions = new(@"attempting to join with \d+ mods : (.*)$");
    private static readonly Regex Tweaker = new(@"Loading tweaker optifine\.OptiFineForgeTweaker from (\S+)");
    private static readonly Regex Detected = new(@"Forge Mod Loader has detected optifine (\S+),");
    private static readonly Regex Error = new(@"InvalidMixinException|MixinApplyError|MixinTransformerError|Mixin apply failed|Critical injection failure|InjectionError|MixinPrepareError"
        + @"|/(?:ERROR|FATAL)\]: .*(?:[Mm]ixin|[Cc]oremod|TheLadsCore|theladscore|Lads )|Failed to load coremod|has failed to load correctly");

    public override string Name => "Forge";
    public override string NotFound => @"FML's mod list ('Attempting connection with missing mods [...] at CLIENT') was not found in logs\latest.log.";
    /// <summary>N of "has successfully loaded N mods": FML finished and the title screen follows.</summary>
    public int? LoadedMods { get; private set; }
    public bool OptiFineTweaker { get; private set; }
    /// <summary>The OptiFine FML detected ("OptiFine_1.8.9_HD_U_M5"), or null.</summary>
    public string? OptiFine { get; private set; }
    public List<string> Errors { get; } = new();

    public override bool Feed(string line)
    {
        if (Identified.Match(line) is { Success: true } identified) Declared = int.Parse(identified.Groups[1].Value);
        else if (Loaded.Match(line) is { Success: true } loaded) LoadedMods = int.Parse(loaded.Groups[1].Value);
        else if (Tweaker.IsMatch(line)) OptiFineTweaker = true;
        else if (Detected.Match(line) is { Success: true } detected) OptiFine = detected.Groups[1].Value;
        // Mixin 0.8 (Essential's) names a config without "minVersion" at ERROR level, as 3D Skin Layers' is; it still loads.
        else if (Error.IsMatch(line) && !line.Contains("does not specify \"minVersion\"", StringComparison.Ordinal)) Errors.Add(line.Trim());
        else if (Versions.Match(line) is { Success: true } versions)
            foreach (var part in versions.Groups[1].Value.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
                if (part.Split('@') is [var id, var version] && Top.ContainsKey(id)) Top[id] = version;
        if (Parsed || List.Match(line) is not { Success: true } list) return false;
        foreach (var id in list.Groups[1].Value.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)) Top.TryAdd(id, "");
        Parsed = Top.Count > 0;
        return Parsed;
    }
}

/// <summary>Fabric's "Loading N mods:" block: top-level jars and the jar-in-jar mods under them. N counts distinct ids
/// (a library nested in several jars is listed under each). Nesting is 3 characters for the first level, then 5 per level.</summary>
sealed class FabricModList : LoadedModList
{
    private static readonly Regex Start = new(@"Loading (\d+) mods:\s*$");
    private static readonly Regex Entry = new(@"^\t(?<indent>[ |]*)(?:- |[|\\]-- )(?<id>[^\s\]]+)(?: (?<version>.*?))?(?:\]\]>.*)?$");
    private readonly List<string> stack = new();
    private bool inList;

    public override string Name => "Fabric";
    public override string NotFound => "Fabric's 'Loading N mods:' list was not found in the game output.";

    /// <summary>True when the line was part of the block (its first line or an entry).</summary>
    public override bool Feed(string line)
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
