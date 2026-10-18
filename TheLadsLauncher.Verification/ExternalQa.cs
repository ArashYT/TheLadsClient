using System.Diagnostics;
using System.Security.Cryptography;
using TheLadsLauncher;
using TheLadsLauncher.Services;

/// <summary>
/// --external &lt;repository root&gt; [--only curseforge|prism|26.3|forge|modrinth]: the External tab's Play on small fake instances
/// rebuilt under artifacts\verification\extinst every run, each with one or two tiny mods and its own memory/JVM settings: CurseForge
/// Fabric 1.20.1, Fabric 26.3 and Forge 1.20.1 (47.4.10) instances, a Prism data folder (Fabric 1.20.1) and a Modrinth App folder
/// (app.db, Fabric 1.20.1); Prism and Modrinth also pin a Java inside their own folder, which must not be run. They are found by
/// ExternalInstances.Scan through LADS_EXTERNAL_ROOTS (never the owner's launchers) and started with the code the card's Play runs
/// (ExternalInstances.ToModpack + ModpackLauncher.LaunchAsync), offline as LadsQA, to the title screen. A SHA-256 listing of each
/// instance folder before and after shows what changed: only what the game writes. Evidence: artifacts\1.9.11\extinst\&lt;time&gt;.
/// </summary>
internal static class ExternalQa
{
    private static readonly string[] QaOptions = { "soundCategory_master:0.1", "soundCategory_music:0.0", "renderDistance:4", "guiScale:2", "maxFps:60", "fullscreen:false", "onboardAccessibility:false" };
    private const string Fabric = "0.19.5", Mc = "1.20.1", Mc26 = "26.3", Forge = "47.4.10";

    public static async Task<int> RunAsync(string root, string[] options)
    {
        string verification = Path.Combine(root, "artifacts", "verification");
        string data = Path.Combine(verification, "extinst-data"), fixtures = Path.Combine(verification, "extinst");
        Environment.SetEnvironmentVariable("THELADS_DIR", data);
        Environment.SetEnvironmentVariable(SharedContentService.RootEnvironmentVariable, Path.Combine(verification, "global-sandbox"));
        Environment.SetEnvironmentVariable("LADS_SKIP_UPDATE", "1");
        int onlyAt = Array.IndexOf(options, "--only");
        string? only = onlyAt >= 0 ? options[onlyAt + 1] : null;
        string evidence = Path.Combine(root, "artifacts", "1.9.11", "extinst", DateTime.Now.ToString("yyyyMMdd-HHmmss"));
        Directory.CreateDirectory(evidence);
        var summary = new List<string> { "Command: --external " + root + " " + string.Join(' ', options), "Sandbox launcher data: " + data };
        void Say(string line) { Console.WriteLine(line); lock (summary) summary.Add(line); }
        var failures = new List<string>();
        void Check(bool ok, string what) { Say((ok ? "OK   " : "BAD  ") + what); if (!ok) failures.Add(what); }
        using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(90));
        var ct = timeout.Token;
        try
        {
            if (Directory.Exists(fixtures)) SafeFileOps.DeleteTree(fixtures);
            string Jar(string version, string name) => Path.Combine(QaShared.CacheRoot, "mod-jars", version, name);
            string cfInstances = Path.Combine(fixtures, "curseforge", "Instances");
            BuildCurseForge(cfInstances, "Lads QA CurseForge", Mc, $"fabric-{Fabric}-{Mc}", "-Dlads.qa.instance=curseforge -Xss2m",
                Jar(Mc, "ferritecore-6.0.1-fabric.jar"), Jar(Mc, "lithium-fabric-mc1.20.1-0.11.4.jar"));
            BuildCurseForge(cfInstances, "Lads QA 26.3", Mc26, $"fabric-{Fabric}-{Mc26}", "-Dlads.qa.instance=fabric26",
                Jar(Mc26, "ferritecore-9.0.0-fabric.jar"), Jar(Mc26, "framepacer-1.2.1-[26.1-26.4]-multiloader.jar"));
            BuildCurseForge(cfInstances, "Lads QA Forge", Mc, $"forge-{Forge}", "-Dlads.qa.instance=forge",
                Jar(Mc, "framepacer-1.2-1.15-1.21.11-multiloader.jar"));
            string prismData = BuildPrism(Path.Combine(fixtures, "PrismLauncher"), Jar(Mc, "ferritecore-6.0.1-fabric.jar"), Jar(Mc, "framepacer-1.2-1.15-1.21.11-multiloader.jar"));
            string modrinthData = BuildModrinth(Path.Combine(fixtures, "ModrinthApp"), Jar(Mc, "lithium-fabric-mc1.20.1-0.11.4.jar"));
            Environment.SetEnvironmentVariable(ExternalInstances.RootsEnvironmentVariable, string.Join(';', cfInstances, prismData, modrinthData));
            var scan = ExternalInstances.Scan(data);
            foreach (var problem in scan.Problems) Say("Scan problem: " + problem);
            Say($"Scan: {string.Join(", ", scan.Instances.Select(i => $"{ExternalInstances.LauncherName(i.Launcher)} '{i.Name}' ({i.ModCount} mods)"))}");
            Check(scan.Instances.Count == 5 && scan.Instances.All(i => SafeFileOps.IsSameOrInside(i.Directory, fixtures)), "all five fake instances found, nothing else");

            // Assets, libraries and loaders: the machine-wide QA cache (as ZeroSmpQa), never the owner's launcher cache.
            string sharedPack = Path.Combine(QaShared.CacheRoot, "modpack-cache");
            Directory.CreateDirectory(sharedPack);
            ZeroSmpQa.Junction(ModpackLauncher.CacheRoot(data), sharedPack);
            var settings = new LauncherSettings { FullscreenOnLaunch = false, MaxRamMb = 4096, MinRamMb = 512, AutoDetectJava = true };
            var java = new JavaService(new PathService(QaShared.RuntimeData));
            var session = AccountIdentity.CreateOfflineSession("LadsQA");
            using var http = new HttpClient();

            foreach (var instance in scan.Instances)
            {
                string tag = instance.Name["Lads QA ".Length..].ToLowerInvariant().Replace(' ', '-');
                if (only != null && only != tag) continue;
                string? refused = ExternalInstances.DirectLaunchProblem(instance);
                Check(refused == null, $"{tag}: Play starts it directly{(refused != null ? " (refused: " + refused + ")" : "")}");
                if (refused != null) continue;
                var plan = ExternalInstances.ToModpack(instance);
                Say($"{tag}: {plan.Loader} {plan.LoaderVersion}, Minecraft {plan.McVersion}, game dir {plan.GameDirectory}, memory {plan.MaxRamMb}/{plan.MinRamMb} MB, " +
                    $"JVM [{string.Join(' ', plan.JvmArguments)}], instance Java pin {instance.JavaPath ?? "-"} -> used: {plan.JavaPath ?? "no (Lads Java)"}");
                // Modrinth: the whole app folder, so app.db is in the listing too.
                string watched = instance.Launcher == ExternalLauncher.Modrinth ? instance.DataRoot : instance.Directory;
                var before = Snapshot(watched);
                File.WriteAllLines(Path.Combine(evidence, tag + "-before.txt"), Listing(before));
                await PlayAsync(tag, plan, data, session, settings, java, http, evidence, fixtures, Say, Check, ct);
                var after = Snapshot(watched);
                File.WriteAllLines(Path.Combine(evidence, tag + "-after.txt"), Listing(after));
                var changed = before.Keys.Union(after.Keys).Order(StringComparer.OrdinalIgnoreCase)
                    .Where(k => before.GetValueOrDefault(k) != after.GetValueOrDefault(k))
                    .Select(k => (before.ContainsKey(k) ? after.ContainsKey(k) ? "changed " : "removed " : "added   ") + k).ToList();
                File.WriteAllLines(Path.Combine(evidence, tag + "-diff.txt"), changed);
                Say($"{tag}: {changed.Count} paths differ after the game (full list in {tag}-diff.txt):");
                foreach (var line in changed.Take(60)) Say("  " + line);
                string[] launcherFiles = { "minecraftinstance.json", "instance.cfg", "mmc-pack.json", "app.db", "prismlauncher.cfg" };
                string[] ladsFiles = { "instance.json", "thelads_config.json", VersionSwitch.VersionsFile, VersionSwitch.RequestFile, AccountSwitch.RequestFile,
                    GraphicsRenderer.StateFile, GraphicsRenderer.OptionsStateFile, GraphicsRenderer.RelaunchFile, RunningGameMarker.FileName, "servers.dat" };
                // Forge writes config\fml.toml itself on its first start; the Lads launcher would have put its early window in it.
                string fml = Path.Combine(plan.GameDirectory, "config", "fml.toml");
                Check(!File.Exists(fml) || !File.ReadAllText(fml).Contains("ladsearlywindow"), $"{tag}: fml.toml not given the Lads early window");
                var paths = changed.Select(c => c[8..]).ToList();
                Check(!paths.Any(p => ladsFiles.Contains(Path.GetFileName(p), StringComparer.OrdinalIgnoreCase)), $"{tag}: no Lads launcher file written");
                Check(!paths.Any(p => launcherFiles.Contains(Path.GetFileName(p), StringComparer.OrdinalIgnoreCase)), $"{tag}: its launcher's own files unchanged");
                Check(!paths.Any(p => p.Split('\\').Contains("mods", StringComparer.OrdinalIgnoreCase) && p.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)
                    || p.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase)), $"{tag}: mod jars untouched (no renames, no Lads Core)");
            }
        }
        catch (Exception error) { failures.Add(error.ToString()); }
        Say(failures.Count == 0 ? "PASS" : "FAIL:\n  " + string.Join("\n  ", failures));
        File.WriteAllLines(Path.Combine(evidence, "summary.txt"), summary);
        Console.WriteLine("Evidence: " + evidence);
        return failures.Count == 0 ? 0 : 1;
    }

    private static async Task PlayAsync(string tag, ModpackInstance plan, string data, CmlLib.Core.Auth.MSession session, LauncherSettings settings, IJavaService java,
        HttpClient http, string evidence, string fixtures, Action<string> say, Action<bool, string> check, CancellationToken ct)
    {
        string log = Path.Combine(plan.GameDirectory, "logs", "latest.log");
        Process? game = null;
        QaShared.Slot? slot = null;
        try
        {
            slot = await QaShared.AcquireAsync(3, "external " + tag, ct);
            var started = Stopwatch.StartNew();
            using (await QaShared.LockAsync(Path.Combine(QaShared.CacheRoot, ".mc.lock"), ct))
                game = await ModpackLauncher.LaunchAsync(data, plan, session, settings, java, http, Progress(say), ct); // the card's Play
            QaWindowGuard.Watch(game, slot.Number);
            string args = game.StartInfo.ArgumentList.Count > 0 ? string.Join(' ', game.StartInfo.ArgumentList) : game.StartInfo.Arguments;
            say($"{tag}: started PID {game.Id} with {game.StartInfo.FileName}");
            int min = plan.MinRamMb > 0 ? plan.MinRamMb : settings.MinRamMb; // CurseForge has no minimum: the Lads one
            check(args.Contains($"-Xmx{plan.MaxRamMb}m") && args.Contains($"-Xms{min}m"), $"{tag}: its memory (-Xmx{plan.MaxRamMb}m -Xms{min}m)");
            check(plan.JvmArguments.All(args.Contains), $"{tag}: its JVM arguments on the command line");
            check(args.Contains("-XX:+DisableAttachMechanism"), $"{tag}: Lads security JVM flags kept");
            check(args.Contains(plan.GameDirectory), $"{tag}: --gameDir is the instance's own folder");
            check(!SafeFileOps.IsSameOrInside(game.StartInfo.FileName, fixtures), $"{tag}: a Lads Java, not one from the other launcher's folder");
            check(!args.Contains(ModpackLauncher.EarlyWindowJar), $"{tag}: no Lads early window jar on the class path");
            bool window = false, title = false;
            TimeSpan titleAt = default;
            while (started.Elapsed < TimeSpan.FromMinutes(10) && !game.HasExited)
            {
                await Task.Delay(1000, ct);
                game.Refresh();
                if (!window && game.MainWindowHandle != IntPtr.Zero) { window = true; say($"{tag}: window at {started.Elapsed.TotalSeconds:F0}s"); }
                string text = ReadShared(log);
                if (text.Contains("---- Minecraft Crash Report") || text.Contains("Crash report saved")) break;
                if (!title && window && text.Contains("Sound engine started")) { title = true; titleAt = started.Elapsed; say($"{tag}: sound engine started at {titleAt.TotalSeconds:F0}s; 20 s on the title screen"); }
                if (title && started.Elapsed - titleAt > TimeSpan.FromSeconds(20)) break;
            }
            check(title && !game.HasExited, $"{tag}: reached the title screen");
            if (title && !game.HasExited)
            {
                game.Refresh();
                string shot = Path.Combine(evidence, tag + "-title-screen.png");
                ModpackQa.Capture(game.MainWindowHandle, shot);
                say($"{tag}: captured {shot}");
            }
            string gameLog = ReadShared(log);
            File.WriteAllText(Path.Combine(evidence, tag + "-latest.log"), gameLog);
            foreach (var line in gameLog.Split('\n').Where(l => l.Contains("Loading Minecraft") || l.Contains("Loading ") && l.Contains(" mods:") || l.Contains("\t- ")
                         || l.Contains("ModLauncher running") || l.Contains("Found mod file") || l.Contains("Sound engine started")))
                say($"{tag} log: " + line.TrimEnd());
        }
        finally
        {
            if (game is { HasExited: false })
            {
                game.CloseMainWindow(); // as a player closes it: the game saves options and exits normally
                if (!game.WaitForExit(45000)) game.Kill(entireProcessTree: true);
                game.WaitForExit(30000);
                say($"{tag}: game closed (exit code {(game.HasExited ? game.ExitCode : -1)})");
            }
            slot?.Dispose();
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private static void AddMods(string game, string[] jars)
    {
        Directory.CreateDirectory(Path.Combine(game, "mods"));
        foreach (var jar in jars) File.Copy(jar, Path.Combine(game, "mods", Path.GetFileName(jar)));
        File.WriteAllLines(Path.Combine(game, "options.txt"), QaOptions);
    }

    /// <summary>A CurseForge instance: minecraftinstance.json (baseModLoader "fabric-0.19.5-26.3" or "forge-47.4.10") in the game folder.</summary>
    private static void BuildCurseForge(string instances, string name, string mc, string loader, string jvm, params string[] jars)
    {
        string dir = Path.Combine(instances, name);
        Directory.CreateDirectory(dir);
        File.WriteAllText(Path.Combine(dir, "minecraftinstance.json"), $$$"""
            {"name":"{{{name}}}","gameVersion":"{{{mc}}}","guid":"{{{Guid.NewGuid()}}}","gameTypeID":432,"isVanilla":false,
             "baseModLoader":{"name":"{{{loader}}}","minecraftVersion":"{{{mc}}}"},
             "isMemoryOverride":true,"allocatedMemory":3072,"javaArgsOverride":"{{{jvm}}}",
             "manifest":{"minecraft":{"version":"{{{mc}}}","recommendedRam":6144}},"lastPlayed":"0001-01-01T00:00:00"}
            """);
        AddMods(dir, jars);
    }

    /// <summary>A Modrinth App folder: app.db (2024-2026 profiles schema with the per-profile overrides) and profiles\&lt;path&gt;.</summary>
    private static string BuildModrinth(string app, params string[] jars)
    {
        string profile = Path.Combine(app, "profiles", "lads-qa-modrinth");
        AddMods(profile, jars);
        string appJava = Path.Combine(app, "meta", "java_versions", "zulu17", "bin", "javaw.exe"); // the app's own Java: never run
        Directory.CreateDirectory(Path.GetDirectoryName(appJava)!);
        File.WriteAllText(appJava, "not a java");
        using (var db = new Microsoft.Data.Sqlite.SqliteConnection($"Data Source={Path.Combine(app, "app.db")};Pooling=False"))
        {
            db.Open();
            using var command = db.CreateCommand();
            command.CommandText = $"""
                CREATE TABLE settings (id INTEGER NOT NULL, extra_launch_args TEXT NOT NULL, mc_memory_max INTEGER NOT NULL, custom_dir TEXT NULL);
                INSERT INTO settings VALUES (0, '["-Dlads.qa.global=modrinth"]', 4096, NULL);
                CREATE TABLE profiles (path TEXT NOT NULL, install_stage TEXT NOT NULL, name TEXT NOT NULL, icon_path TEXT NULL, game_version TEXT NOT NULL,
                    mod_loader TEXT NOT NULL, mod_loader_version TEXT NULL, created INTEGER NOT NULL, modified INTEGER NOT NULL, last_played INTEGER NULL,
                    override_java_path TEXT NULL, override_extra_launch_args TEXT NOT NULL, override_mc_memory_max INTEGER NULL);
                INSERT INTO profiles VALUES ('lads-qa-modrinth', 'installed', 'Lads QA Modrinth', NULL, '{Mc}', 'fabric', '{Fabric}', 1790000000, 1790000000, NULL,
                    '{appJava.Replace('\\', '/')}', '["-Dlads.qa.instance=modrinth"]', 2048);
                """;
            command.ExecuteNonQuery();
        }
        Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
        return app;
    }

    private static string BuildPrism(string data, params string[] jars)
    {
        string instance = Path.Combine(data, "instances", "Lads QA Prism"), game = Path.Combine(instance, "minecraft");
        Directory.CreateDirectory(game);
        // A Java Prism downloaded for itself: pinned by the instance, never run by the Lads launcher.
        string prismJava = Path.Combine(data, "java", "java-runtime-gamma", "bin", "javaw.exe");
        Directory.CreateDirectory(Path.GetDirectoryName(prismJava)!);
        File.WriteAllText(prismJava, "not a java");
        File.WriteAllText(Path.Combine(data, "prismlauncher.cfg"), "[General]\nInstanceDir=instances\nMaxMemAlloc=8192\nMinMemAlloc=1024\nJvmArgs=-Dlads.qa.global=prism\n");
        File.WriteAllText(Path.Combine(instance, "instance.cfg"),
            $"[General]\nConfigVersion=1.2\nInstanceType=OneSix\nname=Lads QA Prism\niconKey=default\nlastLaunchTime=0\nOverrideMemory=true\nMaxMemAlloc=2560\nMinMemAlloc=640\n" +
            $"OverrideJavaArgs=true\nJvmArgs=-Dlads.qa.instance=prism\nOverrideJavaLocation=true\nJavaPath={prismJava.Replace('\\', '/')}\n");
        File.WriteAllText(Path.Combine(instance, "mmc-pack.json"), $$"""
            {"components":[{"uid":"org.lwjgl3","version":"3.3.1","dependencyOnly":true},{"uid":"net.minecraft","version":"{{Mc}}","important":true},
             {"uid":"net.fabricmc.intermediary","version":"{{Mc}}","dependencyOnly":true},{"uid":"net.fabricmc.fabric-loader","version":"{{Fabric}}"}],"formatVersion":1}
            """);
        AddMods(game, jars);
        return data;
    }

    /// <summary>Every file (SHA-256 and size) and folder under <paramref name="root"/>, by relative path.</summary>
    private static Dictionary<string, string> Snapshot(string root) => Directory.EnumerateFileSystemEntries(root, "*", SearchOption.AllDirectories)
        .ToDictionary(p => Path.GetRelativePath(root, p), p => File.Exists(p) ? $"{Hash(p)}  {new FileInfo(p).Length,10}" : "<dir>", StringComparer.OrdinalIgnoreCase);

    private static IEnumerable<string> Listing(Dictionary<string, string> snapshot) => snapshot.OrderBy(e => e.Key, StringComparer.OrdinalIgnoreCase).Select(e => $"{e.Value,-76}  {e.Key}");

    private static string Hash(string file)
    {
        using var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        return Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
    }

    private static string ReadShared(string file)
    {
        if (!File.Exists(file)) return "";
        using var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        return new StreamReader(stream).ReadToEnd();
    }

    private static Action<string> Progress(Action<string> say)
    {
        long last = 0;
        return message =>
        {
            long now = Environment.TickCount64;
            if (now - Interlocked.Read(ref last) < 3000) return;
            Interlocked.Exchange(ref last, now);
            say("  " + message);
        };
    }
}
