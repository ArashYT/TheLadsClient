using System.Diagnostics;
using System.Text;
using System.Text.RegularExpressions;
using TheLadsLauncher;
using TheLadsLauncher.Services;

/// <summary>
/// --zero-smp &lt;sandbox dir&gt; [options]: a sandbox COPY of the owner's Zero SMP instance (%APPDATA%\.theladsclient\instances\zero-smp-1.20.1,
/// read-only source: mods, config, defaultconfigs, options and the other pack folders; no saves, Distant Horizons data,
/// screenshots, logs, backups) started with Forge 47.4.10 + Sinytra Connector through the launcher's own ModpackLauncher, with the
/// 1.20.1 Core built from this worktree replacing mods\theladscore.jar. The sandbox dir is the launcher data folder (THELADS_DIR);
/// the instance is &lt;dir&gt;\instances\zero-smp-1.20.1\minecraft. It must lie in the worktree's artifacts\verification.
/// Options: --script &lt;file&gt;, --script-text "a;b", --remove-mods a,b (delete matching jars from the COPY only), --no-world,
/// --fresh-world (delete the QA world of the copy), --build / --no-build, --core-jar &lt;jar&gt;, --source &lt;instance folder&gt;,
/// --timeout-sec N (default 900), --scenario &lt;name&gt;. Runs hold a QA slot (QaShared) and need 7 GB free RAM.
/// </summary>
internal static class ZeroSmpQa
{
    private const string InstanceId = "zero-smp-1.20.1", WorldName = "LadsQA-zero-smp";
    // Top-level folders and files of the instance that stay behind: worlds, big mod data, logs, caches, backups.
    private static readonly Regex Skip = new(@"\A(saves|screenshots|logs|crash-reports|debug|backups|mods_backup.*|mods-disabled|Distant_Horizons.*|\.mixin\.out|\.connector|.*_cache|.*_BACKUP.*|hs_err.*|servers\.dat.*|usercache\.json|usernamecache\.json|fabricloader\.log|.*\.log|\.qmenu_opened\.marker)\z", RegexOptions.IgnoreCase);

    public static async Task<int> RunAsync(string[] a)
    {
        if (a.Length == 0 || a[0].StartsWith("--", StringComparison.Ordinal)) throw new ArgumentException("--zero-smp <sandbox dir> [options]; see README-1201.md.");
        string data = Path.GetFullPath(a[0]);
        string? script = null, scriptText = null, removeMods = null, coreJarOption = null, source = null, scenario = null;
        bool noWorld = false, freshWorld = false, build = false, noBuild = false;
        int timeoutSec = 900;
        for (int i = 1; i < a.Length; i++)
        {
            string Value() => i + 1 < a.Length ? a[++i] : throw new ArgumentException($"--zero-smp: {a[i]} needs a value.");
            switch (a[i])
            {
                case "--script": script = Path.GetFullPath(Value()); break;
                case "--script-text": scriptText = Value(); break;
                case "--remove-mods": removeMods = Value(); break;
                case "--core-jar": coreJarOption = Path.GetFullPath(Value()); break;
                case "--source": source = Path.GetFullPath(Value()); break;
                case "--timeout-sec": timeoutSec = int.Parse(Value()); break;
                case "--scenario": scenario = Value(); break;
                case "--no-world": noWorld = true; break;
                case "--fresh-world": freshWorld = true; break;
                case "--build": build = true; break;
                case "--no-build": noBuild = true; break;
                default: throw new ArgumentException($"--zero-smp: unknown option {a[i]}.");
            }
        }
        string root = Mc1201Qa.FindRoot(data) ?? Mc1201Qa.FindRoot(Environment.CurrentDirectory) ?? Mc1201Qa.FindRoot(AppContext.BaseDirectory)
            ?? throw new ArgumentException("--zero-smp: the repository root (TheLadsCore\\settings.gradle) was not found above the sandbox dir or the current folder.");
        string verification = Path.Combine(root, "artifacts", "verification");
        Directory.CreateDirectory(verification);
        if (!SafeFileOps.IsSameOrInside(data, verification) || SafeFileOps.PathsEqual(data, verification))
            throw new ArgumentException($"The sandbox dir '{data}' must be a folder inside '{verification}'.");
        string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        string realSource = source ?? Path.Combine(appData, ".theladsclient", "instances", InstanceId);
        if (!Directory.Exists(Path.Combine(realSource, "minecraft", "mods"))) throw new DirectoryNotFoundException($"No Zero SMP instance (minecraft\\mods) at '{realSource}'. Pass --source.");
        if (SafeFileOps.IsSameOrInside(data, realSource) || SafeFileOps.IsSameOrInside(realSource, data)) throw new ArgumentException("The sandbox dir and the source instance must not contain each other.");

        // Every launcher folder this run touches is the sandbox.
        Environment.SetEnvironmentVariable("THELADS_DIR", data);
        Environment.SetEnvironmentVariable(SharedContentService.RootEnvironmentVariable, Path.Combine(verification, "global-sandbox"));
        Environment.SetEnvironmentVariable("LADS_SKIP_UPDATE", "1");
        Environment.SetEnvironmentVariable(ModpackCoreSync.SkipEnvironmentVariable, "1");
        string realMinecraft = Path.Combine(appData, ".minecraft");
        string tripBefore = Listing(realMinecraft), instanceBefore = Listing(Path.Combine(realSource, "minecraft"));

        scenario ??= QaShared.Env("LADS_VERIFY_SCENARIO") ?? "adhoc";
        if (!Regex.IsMatch(scenario, @"\A[A-Za-z0-9][A-Za-z0-9._-]{0,79}\z")) throw new ArgumentException("scenario must be a plain name.");
        string evidence = Path.Combine(root, "artifacts", "verification-1.2.3", scenario, $"{DateTime.Now:yyyyMMdd-HHmmss}-zero-smp-{Path.GetFileName(data)}");
        Directory.CreateDirectory(evidence);
        var summary = new List<string> { "Command: --zero-smp " + string.Join(' ', a.Select(x => x.Contains(' ') ? $"\"{x}\"" : x)), "Started: " + DateTime.Now.ToString("O"),
            "Sandbox launcher data: " + data, "Source (read-only): " + realSource };
        void Say(string line) { Console.WriteLine(line); lock (summary) summary.Add(line); }
        var failures = new List<string>();
        Process? game = null;
        QaShared.Slot? slot = null;
        var runStart = DateTime.UtcNow;
        using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(75)); // slot wait, copy, downloads
        var ct = timeout.Token;
        string gameDir = "";
        try
        {
            string coreJar = QaShared.CoreJar1201(root, coreJarOption, build, noBuild);
            Say("Core jar: " + coreJar + " (" + File.GetLastWriteTime(coreJar).ToString("O") + ")");

            // The instance: a fresh mirror of the pack folders (the sandbox keeps only its own saves between runs).
            string instanceDir = ExperiencesService.InstanceDirectory(data, InstanceId);
            gameDir = Path.Combine(instanceDir, "minecraft");
            Directory.CreateDirectory(gameDir);
            Say("Copying the Zero SMP instance (mods, config, ...) into the sandbox...");
            await Task.Run(() => CopyInstance(Path.Combine(realSource, "minecraft"), gameDir), ct);
            string mods = Path.Combine(gameDir, "mods");
            File.Copy(coreJar, Path.Combine(mods, "theladscore.jar"), overwrite: true);
            foreach (var line in QaShared.RemoveMods(mods, removeMods)) Say(line);
            foreach (var line in QaShared.AddMods(mods, QaShared.Env("LADS_VERIFY_ADD_MODS"))) Say(line);
            if (freshWorld) { string world = Path.Combine(gameDir, "saves", WorldName); if (Directory.Exists(world)) { SafeFileOps.DeleteTree(world); Say("Fresh world: deleted " + world); } }
            File.WriteAllLines(Path.Combine(evidence, "mods.txt"), Directory.EnumerateFiles(mods, "*.jar").Select(f => Path.GetFileName(f)).Order(StringComparer.OrdinalIgnoreCase));

            // Options: the owner's own options.txt of the pack with the QA values forced (the same array as the 1.20.1 and 26.x runs).
            // Research lane (1.8.5, Zero SMP performance study): LADS_VERIFY_PERF_RD / _SIM / _FPS let a measurement run use a realistic render distance and
            // an uncapped frame rate (maxFps 260 = unlimited), as the 1.8.9 perf benchmark does. Unset = the standard QA values; the window, priority,
            // mute, slot and RAM rules are untouched.
            string perfRd = QaShared.Env("LADS_VERIFY_PERF_RD") ?? "4", perfSim = QaShared.Env("LADS_VERIFY_PERF_SIM") ?? "4", perfFps = QaShared.Env("LADS_VERIFY_PERF_FPS") ?? "120";
            bool perfOk = int.TryParse(perfRd, out int rdNumber) && rdNumber is >= 2 and <= 16 && int.TryParse(perfSim, out int simNumber) && simNumber is >= 2 and <= 16
                && (perfFps is "30" or "60" or "120" or "144" or "240" or "260");
            if (!perfOk) throw new ArgumentException("LADS_VERIFY_PERF_RD/_SIM must be 2..16, LADS_VERIFY_PERF_FPS one of 30 60 120 144 240 260.");
            // LADS_VERIFY_FULLSCREEN=1: started as the launcher does with "Fullscreen on launch" (its default): --fullscreen and fullscreen:true.
            bool fullscreen = QaShared.Env("LADS_VERIFY_FULLSCREEN") == "1";
            string[] qaForced = { "soundCategory_master:0.0", "maxFps:" + perfFps, "enableVsync:false", "renderDistance:" + perfRd, "simulationDistance:" + perfSim, "guiScale:2",
                "pauseOnLostFocus:false", "fullscreen:" + (fullscreen ? "true" : "false"), "onboardAccessibility:false", "tutorialStep:none" };
            string options = Path.Combine(gameDir, "options.txt");
            var lines = File.Exists(options) ? File.ReadAllLines(options).Where(l => !qaForced.Any(f => l.StartsWith(f[..(f.IndexOf(':') + 1)], StringComparison.Ordinal))).ToList() : new List<string>();
            lines.AddRange(qaForced);
            File.WriteAllLines(options, lines);
            QaDiscord.ForceOff(gameDir); // Essential's and Lads' Discord activity off in the copy
            // LADS_VERIFY_SHADERPACK=<file in shaderpacks>: Oculus on with that pack in the copy (the owner's config keeps shaders off).
            if (QaShared.Env("LADS_VERIFY_SHADERPACK") is { } shaderPack)
            {
                if (!File.Exists(Path.Combine(gameDir, "shaderpacks", shaderPack))) throw new FileNotFoundException("LADS_VERIFY_SHADERPACK: no such pack in the copy's shaderpacks", shaderPack);
                string oculus = Path.Combine(gameDir, "config", "oculus.properties");
                var props = File.Exists(oculus) ? File.ReadAllLines(oculus).Where(l => !l.StartsWith("enableShaders=") && !l.StartsWith("shaderPack=")).ToList() : new List<string>();
                props.Add("enableShaders=true");
                props.Add("shaderPack=" + shaderPack);
                File.WriteAllLines(oculus, props);
                Say("Shaders on in the copy: " + shaderPack);
            }
            string scriptFile = Path.Combine(gameDir, ".lads-qa-script.txt");
            if (script != null || scriptText != null)
                File.WriteAllText(scriptFile, script != null ? File.ReadAllText(script) : string.Join('\n', scriptText!.Split(';', StringSplitOptions.TrimEntries)));
            File.Delete(Path.Combine(gameDir, ".lads-qa-stop"));

            // Launcher caches shared machine-wide (assets, libraries, Forge, Java), seeded once from the owner's launcher (read-only source).
            string sharedPack = Path.Combine(QaShared.CacheRoot, "modpack-cache");
            using (await QaShared.LockAsync(Path.Combine(QaShared.CacheRoot, ".zero-seed.lock"), ct))
            {
                Directory.CreateDirectory(sharedPack);
                string realCache = Path.Combine(appData, ".theladsclient", "modpack-cache");
                if (!Directory.Exists(Path.Combine(sharedPack, "versions", "1.20.1-forge-47.4.10")) && Directory.Exists(Path.Combine(realCache, "versions", "1.20.1-forge-47.4.10")))
                {
                    Say("Seeding the shared QA cache from the launcher's modpack-cache (once)...");
                    foreach (var folder in new[] { "assets", "libraries", "versions", "installers", "runtime" })
                        if (Directory.Exists(Path.Combine(realCache, folder))) QaShared.Mirror(Path.Combine(realCache, folder), Path.Combine(sharedPack, folder));
                    if (File.Exists(Path.Combine(realCache, "launcher_profiles.json"))) File.Copy(Path.Combine(realCache, "launcher_profiles.json"), Path.Combine(sharedPack, "launcher_profiles.json"), true);
                }
                string realJava = Path.Combine(appData, ".theladsclient", "runtime", "java-17"), sharedJava = Path.Combine(QaShared.RuntimeData, "runtime", "java-17");
                if (!Directory.Exists(sharedJava) && Directory.Exists(realJava)) QaShared.Mirror(realJava, sharedJava);
                Junction(Path.Combine(data, "modpack-cache"), sharedPack);
            }

            var instance = new ModpackInstance
            {
                Id = InstanceId, Name = "Zero SMP", McVersion = "1.20.1", Loader = "forge", LoaderVersion = "47.4.10", IconPath = "icon.png",
                CreatedUtc = DateTime.UtcNow, Author = "The Lads", PackVersion = "1.0.0", MaxRamMb = 6144, Directory = instanceDir
            };
            Modpacks.Save(instance);
            var settings = new LauncherSettings { FullscreenOnLaunch = QaShared.Env("LADS_VERIFY_FULLSCREEN") == "1", MaxRamMb = 6144, MinRamMb = 1024, AutoDetectJava = true };
            var java = new JavaService(new PathService(QaShared.RuntimeData));
            var session = AccountIdentity.CreateOfflineSession("LadsQA");
            using var http = new HttpClient();
            long lastNote = 0;
            void Progress(string message)
            {
                long now = Environment.TickCount64;
                if (now - Interlocked.Read(ref lastNote) < 4000) return;
                Interlocked.Exchange(ref lastNote, now);
                Console.WriteLine("  " + message);
            }
            using (await QaShared.LockAsync(Path.Combine(QaShared.CacheRoot, ".mc.lock"), ct))
                game = await ModpackLauncher.PrepareAsync(data, instance, session, settings, java, http, Progress, ct);
            // ModpackLauncher gives the game THELADS_GLOBAL_DIR = this sandbox folder (never the owner's); a shared config left by an earlier run is dropped so the
            // Core re-seeds it from the copy's own thelads_config.json (the owner's layout, Discord RPC off).
            if (game.StartInfo.Environment.TryGetValue("THELADS_GLOBAL_DIR", out var globalDir) && SafeFileOps.IsSameOrInside(globalDir ?? "", verification))
                File.Delete(Path.Combine(globalDir!, "shared", "thelads_config.json"));
            else throw new InvalidOperationException("THELADS_GLOBAL_DIR of the Zero SMP game is not inside the sandbox: " + globalDir);
            Say("Core global config dir (THELADS_GLOBAL_DIR): " + globalDir);
            var args = game.StartInfo.ArgumentList;
            var flags = new List<string> { "-Dfabric.noGui=true" };
            if (!noWorld) { flags.Add("-Dthelads.verifyAutoWorld=true"); flags.Add("-Dthelads.qaWorld=" + WorldName); }
            if (script != null || scriptText != null) flags.Add("-Dthelads.qaScript=" + scriptFile);
            // LADS_VERIFY_FASTQUIT=run|verify|baseline: the FastQuit probe (FastQuitProbe1201) drives the game from the title screen instead.
            string? fastQuit = QaShared.Env("LADS_VERIFY_FASTQUIT");
            if (fastQuit != null) flags.Add("-Dthelads.verifyFastQuit=" + fastQuit);
            if (fastQuit == "baseline") flags.Add("-Dlads.fastquit=false");
            foreach (var extra in (QaShared.Env("LADS_VERIFY_EXTRA_JVM") ?? "").Split(';', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
                flags.Add(System.Text.RegularExpressions.Regex.IsMatch(extra, @"\A-Dlads\.[a-z0-9.]{1,40}=[A-Za-z0-9._-]{1,40}\z") ? extra : throw new ArgumentException("LADS_VERIFY_EXTRA_JVM takes only -Dlads.<name>=<value> flags, not '" + extra + "'."));
            // LADS_VERIFY_EBE=1|off|xmas: the baked block entity probe (BakedBlockEntitiesProbe1201), asked for after the world capture.
            string? ebe = QaShared.Env("LADS_VERIFY_EBE");
            if (ebe is not (null or "1" or "off" or "xmas")) throw new ArgumentException("LADS_VERIFY_EBE must be 1, off or xmas.");
            if (ebe != null) flags.Add("-Dlads.ebe.probe=true");
            if (ebe == "off") flags.Add("-Dlads.ebe=false");
            if (ebe == "xmas") flags.Add("-Dlads.ebe.xmas=true");
            File.Delete(Path.Combine(gameDir, ".lads-qa-ebe"));
            if (args.Count > 0) for (int i = flags.Count - 1; i >= 0; i--) args.Insert(0, flags[i]);
            else game.StartInfo.Arguments = string.Join(' ', flags.Select(f => f.Contains(' ') && !f.StartsWith("\"") ? $"\"{f}\"" : f)) + " " + game.StartInfo.Arguments;
            if (QaShared.Env("LADS_VERIFY_NO_RESIZE") != "1" && args.Count > 0)
            {
                var size = QaWindowGuard.TileClient(1);
                foreach (var extra in new[] { "--width", size.Width.ToString(), "--height", size.Height.ToString() }) args.Add(extra);
            }
            Say("JVM QA flags: " + string.Join(' ', flags));
            File.WriteAllText(Path.Combine(evidence, "command-line.txt"), game.StartInfo.FileName + " " + string.Join(' ', args));

            string log = Path.Combine(gameDir, "logs", "latest.log");
            if (File.Exists(log)) File.Delete(log);
            slot = await QaShared.AcquireAsync(7, "Zero SMP", ct);
            var started = Stopwatch.StartNew();
            game.Start();
            Say($"Started PID {game.Id}: {game.StartInfo.FileName}");
            QaWindowGuard.Watch(game, slot.Number);

            bool begin = false, title = false, ready = false, worldDone = false, scriptDone = false, initialized = false, stopSent = false, fastQuitDone = false, ebeDone = false, ebeAsked = false;
            bool scripted = script != null || scriptText != null;
            var seen = new HashSet<string>();
            while (!game.HasExited && started.Elapsed < TimeSpan.FromSeconds(timeoutSec))
            {
                await Task.Delay(1000, ct);
                string text = ReadShared(log);
                foreach (var line in text.Split('\n'))
                {
                    if (line.Contains("Lads auto-world QA BEGIN")) begin = true;
                    if (line.Contains("Lads auto-world QA TITLE")) title = true;
                    if (line.Contains("Lads auto-world QA READY")) ready = true;
                    if (line.Contains("Lads world capture END:") && line.Contains(", 0 failed")) worldDone = true;
                    if (line.Contains("Lads qa script END:") && line.Contains(", 0 failed")) scriptDone = true;
                    if (line.Contains("TheLadsCore 1.20.1 initialized successfully")) initialized = true;
                    if (line.Contains("Lads fast quit probe END:")) fastQuitDone = true;
                    if (line.Contains("Lads fast quit probe FAILED") && seen.Add(line)) failures.Add(line.Trim());
                    if (line.Contains("Lads EBE probe END:")) { ebeDone = true; if (!line.Contains(", 0 failed") && seen.Add(line)) failures.Add(line.Trim()); }
                    if (line.Contains("Lads EBE probe FAILED") && seen.Add(line)) failures.Add(line.Trim());
                    if ((line.Contains("Lads auto-world QA FAILED") || line.Contains("Lads qa script FAILED") || line.Contains("Lads qa step FAILED")) && seen.Add(line))
                        failures.Add(line.Trim());
                    if (QaShared.MixinProblem(line) && seen.Add(line)) failures.Add("MIXIN PROBLEM: " + line.Trim());
                    if (line.Contains("Crash report saved") || line.Contains("---- Minecraft Crash Report")) { if (seen.Add("crash")) failures.Add("the game crashed (see latest.log / crash-reports)"); }
                }
                if (failures.Count > 0 && !stopSent && (failures.Any(f => f.Contains("crashed")) || failures.Any(f => f.Contains("auto-world QA FAILED")))) break;
                if (ebe != null && worldDone && !ebeAsked) { File.WriteAllText(Path.Combine(gameDir, ".lads-qa-ebe"), "probe"); ebeAsked = true; }
                bool finished = fastQuit != null ? initialized && fastQuitDone : initialized && begin && (noWorld ? title : worldDone) && (!scripted || scriptDone) && (ebe == null || ebeDone);
                if (finished && !stopSent)
                {
                    stopSent = true;
                    Say($"Reached title{(noWorld ? "" : " and the QA world")}{(scripted ? " and finished the script" : "")} at {started.Elapsed.TotalSeconds:F0} s; asking the game to stop.");
                    File.WriteAllText(Path.Combine(gameDir, ".lads-qa-stop"), "stop");
                    break;
                }
            }
            if (game.HasExited && !stopSent && !(fastQuit != null ? fastQuitDone : initialized && begin && (noWorld ? title : worldDone) && (!scripted || scriptDone)))
                failures.Add($"the game exited early with code {game.ExitCode}");
            else if (!game.HasExited && !stopSent && failures.Count == 0)
                failures.Add($"no title/world/script completion within {timeoutSec} s (begin={begin} title={title} ready={ready} world={worldDone} script={scriptDone} initialized={initialized})");
            if (!initialized) failures.Add("the Core never logged 'TheLadsCore 1.20.1 initialized successfully' (not loaded through Connector?)");
            if (!begin && fastQuit == null) failures.Add("the QA probe never started (Lads auto-world QA BEGIN missing)");
            // Graceful stop, then the tree.
            if (!game.HasExited) { try { await game.WaitForExitAsync(new CancellationTokenSource(TimeSpan.FromSeconds(60)).Token); } catch (OperationCanceledException) { } }
            if (!game.HasExited) { Say("Stopping the Zero SMP game with Kill (it ignored the stop request)."); game.Kill(entireProcessTree: true); game.WaitForExit(30000); }
            if (File.Exists(log)) File.Copy(log, Path.Combine(evidence, "latest.log"), true);
            var logText = ReadShared(log).Split('\n');
            foreach (var line in logText.Where(l => l.Contains("Lads auto-world QA") || l.Contains("Lads world capture") || l.Contains("Lads qa ") || l.Contains("TheLadsCore")
                || l.Contains("Lads EBE") || l.Contains("Baked block entities")))
                Say("log: " + line.TrimEnd());
        }
        catch (Exception error)
        {
            failures.Add(error.GetType().Name + ": " + error.Message);
            Console.WriteLine(error);
        }
        finally
        {
            if (game is { HasExited: false }) { try { game.Kill(entireProcessTree: true); } catch (InvalidOperationException) { } }
            slot?.Dispose();
        }
        // Evidence: screenshots written by the probe, and the trip-wires.
        string shots = Path.Combine(gameDir, "screenshots");
        if (gameDir.Length > 0 && Directory.Exists(shots))
            foreach (var png in new DirectoryInfo(shots).EnumerateFiles("*.png").Where(f => f.LastWriteTimeUtc >= runStart))
            {
                Directory.CreateDirectory(Path.Combine(evidence, "screenshots"));
                png.CopyTo(Path.Combine(evidence, "screenshots", png.Name), true);
            }
        if (Listing(realMinecraft) != tripBefore)
        {
            // The owner plays on this PC while QA runs; LADS_VERIFY_TRIPWIRE=strict makes a change a failure (see Program.cs).
            if (QaShared.Env("LADS_VERIFY_TRIPWIRE") == "strict") failures.Add("TRIP-WIRE: the real %APPDATA%\\.minecraft changed during this run.");
            else Say("TRIP-WIRE WARNING (not a failure; the owner may be playing): the real %APPDATA%\\.minecraft changed during this run.");
        }
        else Say($"Trip-wire: {realMinecraft} unchanged.");
        if (Listing(Path.Combine(realSource, "minecraft")) != instanceBefore) Say("note: the source instance listing changed during the run (read-only for the harness: the owner may have played).");
        Say(failures.Count == 0 ? "PASS" : "FAIL:\n  " + string.Join("\n  ", failures));
        summary.Add("Finished: " + DateTime.Now.ToString("O"));
        File.WriteAllLines(Path.Combine(evidence, "summary.txt"), summary);
        Console.WriteLine("Evidence: " + evidence);
        return failures.Count == 0 ? 0 : 1;
    }

    /// <summary>Mirrors the pack folders of the source into the copy (source untouched), skipping worlds, logs, caches and backups.</summary>
    private static void CopyInstance(string source, string target)
    {
        foreach (var entry in new DirectoryInfo(source).EnumerateFileSystemInfos())
        {
            if (Skip.IsMatch(entry.Name)) continue;
            if (entry is DirectoryInfo dir) QaShared.Mirror(dir.FullName, Path.Combine(target, dir.Name));
            else File.Copy(entry.FullName, Path.Combine(target, entry.Name), overwrite: true);
        }
    }

    /// <summary>A directory junction link -> target (the launcher's modpack-cache lands in the shared QA cache).</summary>
    private static void Junction(string link, string target)
    {
        if (Directory.Exists(link))
        {
            if (new DirectoryInfo(link).LinkTarget is { } existing && SafeFileOps.PathsEqual(Path.GetFullPath(existing), target)) return;
            if (new DirectoryInfo(link).LinkTarget != null) Directory.Delete(link); else SafeFileOps.DeleteTree(link);
        }
        Directory.CreateDirectory(Path.GetDirectoryName(link)!);
        var start = new ProcessStartInfo("cmd.exe") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
        foreach (var arg in new[] { "/c", "mklink", "/J", link, target }) start.ArgumentList.Add(arg);
        using var process = Process.Start(start)!;
        process.WaitForExit();
        if (process.ExitCode != 0 || !Directory.Exists(link)) throw new IOException($"mklink /J {link} {target} failed: {process.StandardError.ReadToEnd()}");
    }

    private static string ReadShared(string file)
    {
        if (!File.Exists(file)) return "";
        using var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        return new StreamReader(stream).ReadToEnd();
    }

    /// <summary>Top-level names, sizes and mtimes plus options.txt: the read-only trip-wire of a folder the run must not change.</summary>
    private static string Listing(string folder)
    {
        var top = new DirectoryInfo(folder);
        if (!top.Exists) return folder + "\tabsent\n";
        var text = new StringBuilder();
        foreach (var entry in top.EnumerateFileSystemInfos().OrderBy(e => e.Name, StringComparer.OrdinalIgnoreCase))
            if (!entry.Name.Equals("logs", StringComparison.OrdinalIgnoreCase) && !entry.Name.Equals("crash-reports", StringComparison.OrdinalIgnoreCase))
                text.Append($"{entry.Name}\t{(entry is FileInfo f ? f.Length.ToString() : "dir")}\t{(entry is FileInfo ? entry.LastWriteTimeUtc.ToString("O") : "")}\n");
        return text.ToString();
    }
}
