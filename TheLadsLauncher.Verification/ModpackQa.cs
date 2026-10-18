using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Text.Json;
using TheLadsLauncher;
using TheLadsLauncher.Services;

/// <summary>
/// --modpack modrinth:&lt;project&gt;[@&lt;version&gt;] | mrpack:&lt;file&gt; &lt;repository root&gt; [--loader &lt;loader&gt;] [--update-to &lt;version&gt;] [--no-launch]
///   [--lads-core on|off]
/// Installs a Modrinth modpack with the Modpacks tab's own code into the sandbox launcher folder
/// (artifacts\verification\modpacks-data), optionally updates it to a newer pack version (checking that a world, a changed
/// setting and a switched-off mod survive), then starts it offline as LadsQA, muted, and captures its window at the title
/// screen. --no-launch still installs Java, the loader and the game files; only the game is not started. --loader picks
/// among versions published per loader. mrpack:&lt;file&gt; installs a local .mrpack (Modrinth's import code) instead. --lads-core sets the
/// instance's "Add Lads Core" setting before the launch (the launcher's own sync then adds or switches off mods\theladscore.jar);
/// without it the default applies (on where the Core is supported). The run checks the jar and the game log against the setting; with the
/// Core in the game the QA bar shows. Evidence goes to artifacts\1.6.0\modpacks. Never a Lads profile, never %APPDATA%.
/// Hold the machine-wide .qa-game-lock while it runs a game.
/// </summary>
internal static class ModpackQa
{
    public static async Task<int> RunAsync(string source, string root, string[] options)
    {
        string verification = Path.Combine(root, "artifacts", "verification");
        string data = Path.Combine(verification, "modpacks-data");
        Environment.SetEnvironmentVariable("THELADS_DIR", data);
        Environment.SetEnvironmentVariable(SharedContentService.RootEnvironmentVariable, Path.Combine(verification, "global-sandbox"));
        Environment.SetEnvironmentVariable("LADS_SKIP_UPDATE", "1");
        bool local = source.StartsWith("mrpack:", StringComparison.Ordinal);
        if (!local && !source.StartsWith("modrinth:", StringComparison.Ordinal)) throw new ArgumentException("Source must be modrinth:<project>[@<version>] or mrpack:<file>.");
        var (project, wanted) = local ? (Path.GetFileNameWithoutExtension(source["mrpack:".Length..]), null) : Split(source["modrinth:".Length..]);
        int coreAt = Array.IndexOf(options, "--lads-core");
        bool? ladsCore = coreAt < 0 ? null : options.ElementAtOrDefault(coreAt + 1) switch
        {
            "on" => true, "off" => false, _ => throw new ArgumentException("--lads-core takes on or off.")
        };
        int updateAt = Array.IndexOf(options, "--update-to");
        string? updateTo = updateAt >= 0 ? options[updateAt + 1] : null;
        bool launch = !options.Contains("--no-launch");
        int loaderAt = Array.IndexOf(options, "--loader");
        string? loader = loaderAt >= 0 ? options[loaderAt + 1] : null;

        string evidence = Path.Combine(root, "artifacts", "1.6.0", "modpacks", $"{DateTime.Now:yyyyMMdd-HHmmss}-{project}");
        Directory.CreateDirectory(evidence);
        var summary = new List<string> { "Command: --modpack " + source + " " + string.Join(' ', options), "Sandbox launcher data: " + data };
        void Say(string line) { Console.WriteLine(line); lock (summary) summary.Add(line); }
        var failures = new List<string>();
        using var http = ModrinthModpacks.CreateClient();
        using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(25));
        var ct = timeout.Token;
        Process? game = null;
        QaShared.Slot? slot = null;
        try
        {
            List<ModpackVersion> versions = new();
            ModpackInstance instance;
            if (local)
            {
                if (updateTo != null) throw new ArgumentException("--update-to needs a modrinth: source.");
                string file = Path.GetFullPath(source["mrpack:".Length..]);
                Say("Installing " + file);
                instance = await ModpackLauncher.InstallAsync(data, file, null, null, null, null, http, Progress(Say), ct);
            }
            else
            {
                versions = (await ModrinthModpacks.VersionsAsync(http, project, ct)).Where(v => loader == null || v.Loaders.Contains(loader)).ToList();
                var first = Pick(versions, wanted);
                var (projectTitle, author, icon) = await ModrinthModpacks.ProjectAsync(http, first.ProjectId, ct);
                Say($"Installing {projectTitle} {first.VersionNumber} by {author} (Minecraft {string.Join(",", first.GameVersions)}, {string.Join(",", first.Loaders)})");
                instance = await WithPack(http, data, first, file => ModpackLauncher.InstallAsync(data, file,
                    new ModpackSource { ProjectId = first.ProjectId, VersionId = first.Id }, projectTitle, author, icon, http, Progress(Say), ct));
            }
            Require(SafeFileOps.IsSameOrInside(instance.GameDirectory, verification), "instance outside the sandbox");
            Say($"Instance {instance.Id}: {instance.Loader} {instance.LoaderVersion}, Minecraft {instance.McVersion}, {Modpacks.Mods(instance).Count} mods, game dir {instance.GameDirectory}");

            if (updateTo != null)
            {
                // What a player leaves behind: a world, a changed setting, a mod switched off.
                string world = Path.Combine(instance.GameDirectory, "saves", "QA World", "level.dat");
                Directory.CreateDirectory(Path.GetDirectoryName(world)!);
                File.WriteAllText(world, "lads qa world");
                string optionsFile = Path.Combine(instance.GameDirectory, "options.txt");
                File.AppendAllText(optionsFile, "ladsQaMarker:1\n");
                var off = Modpacks.Mods(instance).First(m => !m.FileName.Contains("fabric-api", StringComparison.OrdinalIgnoreCase));
                Modpacks.SetModEnabled(off, false);
                var next = Pick(versions, updateTo);
                Say($"Updating to {next.VersionNumber} with a world, a changed options.txt and {off.FileName} switched off");
                await WithPack(http, data, next, async file =>
                {
                    await ModpackLauncher.UpdateAsync(instance, file, new ModpackSource { ProjectId = next.ProjectId, VersionId = next.Id }, http, Progress(Say), ct);
                    return true;
                });
                Check(failures, Say, File.Exists(world) && File.ReadAllText(world) == "lads qa world", "world kept across the update");
                Check(failures, Say, File.ReadAllText(optionsFile).Contains("ladsQaMarker:1"), "changed options.txt kept across the update");
                var after = Modpacks.Mods(instance).FirstOrDefault(m => m.FileName == off.FileName);
                Check(failures, Say, after == null || !after.Enabled, $"{off.FileName} still off after the update" + (after == null ? " (the new version dropped it)" : ""));
                Check(failures, Say, instance.PackVersion == next.VersionNumber || instance.Source?.VersionId == next.Id, $"instance.json now on {instance.PackVersion}");
                if (after != null) Modpacks.SetModEnabled(after, true);
                Say($"Now {instance.Loader} {instance.LoaderVersion}, Minecraft {instance.McVersion}, {Modpacks.Mods(instance).Count} mods");
            }
            if (ladsCore != null)
            {
                instance.LadsCore = ladsCore;
                Modpacks.Save(instance);
            }
            Say($"Add Lads Core: {(instance.LadsCore == true ? "on" : "off")} (supported: {ModpackCoreSync.IsSupported(instance, out var unsupported)}{(unsupported == null ? "" : ", " + unsupported)})");
            File.Copy(Path.Combine(instance.Directory, "instance.json"), Path.Combine(evidence, "instance.json"), true);
            File.WriteAllLines(Path.Combine(evidence, "mods.txt"), Modpacks.Mods(instance).Select(m => (m.Enabled ? "on   " : "off  ") + m.FileName));

            var settings = new LauncherSettings { FullscreenOnLaunch = false, MaxRamMb = 4096, MinRamMb = 512, AutoDetectJava = true };
            var java = new JavaService(new PathService(QaShared.RuntimeData));
            var session = AccountIdentity.CreateOfflineSession("LadsQA");
            if (!launch)
            {
                using var prepared = await ModpackLauncher.PrepareAsync(data, instance, session, settings, java, http, Progress(Say), ct);
                string arguments = prepared.StartInfo.ArgumentList.Count > 0 ? string.Join(' ', prepared.StartInfo.ArgumentList) : prepared.StartInfo.Arguments;
                Say($"Prepared, not started: {prepared.StartInfo.FileName}");
                Check(failures, Say, arguments.Contains(instance.GameDirectory), "the game folder is the instance's own");
                Check(failures, Say, !arguments.Contains("theladscore", StringComparison.OrdinalIgnoreCase), "no Lads Core on the command line (a Fabric Core sits in mods)");
                File.WriteAllText(Path.Combine(evidence, "command-line.txt"), prepared.StartInfo.FileName + " " + arguments);
            }
            else
            {
                // Muted, windowed, no first-run accessibility screen in front of the title.
                string optionsFile = Path.Combine(instance.GameDirectory, "options.txt");
                var lines = File.Exists(optionsFile) ? File.ReadAllLines(optionsFile).ToList() : new List<string>();
                lines.RemoveAll(l => l.StartsWith("soundCategory_master:") || l.StartsWith("soundCategory_music:") || l.StartsWith("fullscreen:") || l.StartsWith("onboardAccessibility:"));
                lines.AddRange(new[] { "soundCategory_master:0.1", "soundCategory_music:0.0", "fullscreen:false", "onboardAccessibility:false" });
                File.WriteAllLines(optionsFile, lines);
                QaDiscord.ForceOff(instance.GameDirectory); // never on the owner's Discord
                string log = Path.Combine(instance.GameDirectory, "logs", "latest.log");
                if (File.Exists(log)) File.Delete(log);
                var started = Stopwatch.StartNew();
                // ModpackLauncher.LaunchAsync, plus: a loader crash at startup exits with its log instead of opening Fabric Loader's
                // error window on the user's screen (the property is Fabric's; other loaders ignore it).
                game = await ModpackLauncher.PrepareAsync(data, instance, session, settings, java, http, Progress(Say), ct);
                // The launcher's sync ran in PrepareAsync: what is in mods now is what the game loads.
                string mods = Path.Combine(instance.GameDirectory, "mods");
                bool expectCore = instance.LadsCore == true && ModpackCoreSync.IsSupported(instance);
                File.WriteAllLines(Path.Combine(evidence, "mods-at-launch.txt"), Directory.Exists(mods) ? Directory.EnumerateFiles(mods).Select(f => Path.GetFileName(f)).Order() : Enumerable.Empty<string>());
                if (expectCore) Check(failures, Say, File.Exists(Path.Combine(mods, ModpackCoreSync.BundledName)), "the launcher added mods\\" + ModpackCoreSync.BundledName);
                else Check(failures, Say, ModpackCoreSync.CoreJars(mods).Count == 0, "no enabled Lads Core jar in mods");
                // Permanent QA rule: the QA bar shows whenever a Core is in the game (without one nothing can draw it).
                var jvm = new List<string> { "-Dfabric.noGui=true" };
                if (expectCore) jvm.AddRange(new[] { "-Dthelads.qaBar=1", "-Dthelads.qaRun=modpack " + project });
                if (game.StartInfo.ArgumentList.Count > 0) for (int i = jvm.Count - 1; i >= 0; i--) game.StartInfo.ArgumentList.Insert(0, jvm[i]);
                else game.StartInfo.Arguments = string.Join(' ', jvm.Select(a => a.Contains(' ') ? "\"" + a + "\"" : a)) + " " + game.StartInfo.Arguments;
                slot = await QaShared.AcquireAsync(4, "modpack " + project, ct); // at most 3 QA games machine-wide (QaShared)
                game.Start();
            LaunchToken.Send(game);
                instance.LastPlayedUtc = DateTime.UtcNow;
                Modpacks.Save(instance);
                Say($"Started PID {game.Id}: {game.StartInfo.FileName}");
                QaWindowGuard.Watch(game, slot.Number);
                bool window = false, title = false;
                TimeSpan titleAt = default;
                while (started.Elapsed < TimeSpan.FromMinutes(10) && !game.HasExited)
                {
                    await Task.Delay(1000, ct);
                    game.Refresh();
                    if (!window && game.MainWindowHandle != IntPtr.Zero) { window = true; Say($"Window at {started.Elapsed.TotalSeconds:F0}s"); }
                    string text = ReadShared(log);
                    // Legacy Forge's SplashProgress logs a "Loading screen debug info" crash report on every start: not a crash.
                    int reports = text.Split("---- Minecraft Crash Report").Length - text.Split("Loading screen debug info").Length;
                    if (reports > 0 || text.Contains("Crash report saved")) { failures.Add("the game crashed (see latest.log)"); break; }
                    if (!title && window && text.Contains("Sound engine started")) { title = true; titleAt = started.Elapsed; Say($"Resources loaded (sound engine started) at {titleAt.TotalSeconds:F0}s; waiting 25 s on the title screen"); }
                    if (title && started.Elapsed - titleAt > TimeSpan.FromSeconds(25)) break;
                }
                if (game.HasExited) failures.Add($"the game exited early with code {game.ExitCode}");
                else if (!title) failures.Add("no title screen within 10 minutes");
                else
                {
                    game.Refresh();
                    string shot = Path.Combine(evidence, "title-screen.png");
                    Capture(game.MainWindowHandle, shot);
                    Say("Captured " + shot);
                }
                if (File.Exists(log)) File.Copy(log, Path.Combine(evidence, "latest.log"), true);
                var gameLog = ReadShared(log).Split('\n');
                foreach (var line in gameLog.Where(l => l.Contains("Loading Minecraft") || l.Contains("Loading ") && l.Contains(" mods:") || l.Contains("Sound engine started") || l.Contains("Backend library")))
                    Say("log: " + line.TrimEnd());
                if (expectCore) Check(failures, Say, gameLog.Any(l => l.Contains("TheLadsCore") && l.Contains("initialized successfully")), "Lads Core loaded in the game (initialized successfully)");
                else Check(failures, Say, !gameLog.Any(l => l.Contains("theladscore", StringComparison.OrdinalIgnoreCase)), "no Lads Core in the game");
            }
        }
        catch (Exception error)
        {
            failures.Add(error.ToString());
        }
        finally
        {
            // Closed like a player closes it, so helpers a pack starts (CrashAssistant's watcher) see a normal exit and quit too.
            if (game is { HasExited: false })
            {
                game.CloseMainWindow();
                if (!game.WaitForExit(45000)) game.Kill(entireProcessTree: true);
                game.WaitForExit(30000);
                Say("Game stopped.");
            }
            slot?.Dispose();
        }
        Say(failures.Count == 0 ? "PASS" : "FAIL:\n  " + string.Join("\n  ", failures));
        File.WriteAllLines(Path.Combine(evidence, "summary.txt"), summary);
        Console.WriteLine("Evidence: " + evidence);
        return failures.Count == 0 ? 0 : 1;
    }

    private static (string Project, string? Version) Split(string s) => s.IndexOf('@') is var at and >= 0 ? (s[..at], s[(at + 1)..]) : (s, null);

    private static ModpackVersion Pick(List<ModpackVersion> versions, string? wanted) => wanted == null ? versions[0]
        : versions.FirstOrDefault(v => v.VersionNumber == wanted || v.Id == wanted) ?? throw new ArgumentException($"No version '{wanted}'.");

    private static async Task<T> WithPack<T>(HttpClient http, string data, ModpackVersion version, Func<string, Task<T>> use)
    {
        var file = Path.Combine(ModpackLauncher.CacheRoot(data), "downloads", version.Id + ".mrpack");
        Directory.CreateDirectory(Path.GetDirectoryName(file)!);
        await ModrinthModpacks.DownloadAsync(http, version, file, CancellationToken.None);
        try { return await use(file); }
        finally { File.Delete(file); }
    }

    // Progress lines at most every 3 s (the installers report per file).
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

    private static void Require(bool ok, string what) { if (!ok) throw new InvalidOperationException("SANDBOX: " + what); }

    private static void Check(List<string> failures, Action<string> say, bool ok, string what)
    {
        say((ok ? "OK   " : "BAD  ") + what);
        if (!ok) failures.Add(what);
    }

    private static string ReadShared(string file)
    {
        if (!File.Exists(file)) return "";
        using var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        return new StreamReader(stream).ReadToEnd();
    }

    [StructLayout(LayoutKind.Sequential)] private struct Rect { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] private static extern bool GetClientRect(IntPtr window, out Rect rect);
    [DllImport("user32.dll")] private static extern bool PrintWindow(IntPtr window, IntPtr dc, uint flags);

    /// <summary>The game window's client area, drawn by the window itself (PW_CLIENTONLY | PW_RENDERFULLCONTENT): no desktop input.</summary>
    internal static void Capture(IntPtr window, string file)
    {
        GetClientRect(window, out var rect);
        using var bitmap = new Bitmap(Math.Max(1, rect.Right - rect.Left), Math.Max(1, rect.Bottom - rect.Top));
        using (var graphics = Graphics.FromImage(bitmap))
        {
            var dc = graphics.GetHdc();
            try { PrintWindow(window, dc, 0x1 | 0x2); }
            finally { graphics.ReleaseHdc(dc); }
        }
        bitmap.Save(file, ImageFormat.Png);
    }
}
