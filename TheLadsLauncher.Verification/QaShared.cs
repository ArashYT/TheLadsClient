using System.Diagnostics;
using System.IO.Compression;
using System.Runtime.InteropServices;
using System.Text.Json;
using System.Text.Json.Nodes;

/// <summary>
/// Machine-wide QA rules shared by every worktree's harness (owner, 2026-10-08: "do all the tests on maximum performance, use the
/// main monitor and you can do 3 instances at a time"): at most <see cref="MaxGames"/> QA games at once, claimed as
/// &lt;shared root&gt;\.qa-slot-1..5 (a folder holding owner.txt with the harness PID), launched only with enough free RAM and not
/// within 25 s of the previous claim (a starting JVM is still growing). The slot number also picks the game's screen tile
/// (QaWindowGuard). Also a download cache (Minecraft assets, libraries, Java, Modrinth mod jars) so twelve worktrees do not each
/// fetch ~1 GB.
/// </summary>
static class QaShared
{
    public const int MaxGames = 5; // owner 2026-10-09: "maximum performance, 5 instances at a time is your limit for both AIs"
    private const string DevDirectory = @"C:\Users\Arash\Desktop\The Lads Client Dev";

    public static string? Env(string name) => Environment.GetEnvironmentVariable(name) is { Length: > 0 } v ? v.Trim() : null;

    /// <summary>Where the slots and the cache live: LADS_QA_SHARED_ROOT, else the owner's "The Lads Client Dev" folder.</summary>
    public static string Root => Path.GetFullPath(Env("LADS_QA_SHARED_ROOT") ?? (Directory.Exists(DevDirectory) ? DevDirectory : Path.Combine(Path.GetTempPath(), "lads-qa-shared")));
    public static string CacheRoot => Path.Combine(Root, ".qa-cache");

    /// <summary>Owner-playing mode (owner 2026-10-09: "do the QAs 1 instance at a time on the 3rd monitor that has antigravity open, do not tab me
    /// out of my game"): while &lt;shared root&gt;\.qa-owner-playing exists, every harness (Claude's and Antigravity's) runs ONE game machine-wide,
    /// on the monitor named in that file (e.g. \.\DISPLAY3), below-normal priority, 60 FPS, never activated. Delete the file to return to normal.</summary>
    public static string? PlayingMonitor
    {
        get
        {
            try { string f = Path.Combine(Root, ".qa-owner-playing"); return File.Exists(f) ? File.ReadAllText(f).Trim() is { Length: > 0 } m ? m : @"\\.\DISPLAY1" : null; }
            catch (IOException) { return null; }
        }
    }
    public static int EffectiveMaxGames => PlayingMonitor != null ? 1 : MaxGames;
    public static string RuntimeData => Path.Combine(CacheRoot, "runtime-data");

    public sealed class Slot : IDisposable
    {
        public int Number { get; }
        private readonly string? folder;
        internal Slot(int number, string? folder) { Number = number; this.folder = folder; }
        public void Dispose()
        {
            if (folder == null) return;
            try { Directory.Delete(folder, true); } catch (IOException) { } catch (UnauthorizedAccessException) { }
        }
    }

    /// <summary>Waits for a free slot and free RAM. LADS_QA_SLOT=n: the caller's own wrapper already holds slot n (only the tile uses it).</summary>
    public static async Task<Slot> AcquireAsync(double minFreeGb, string what, CancellationToken ct)
    {
        if (int.TryParse(Env("LADS_QA_SLOT"), out int held) && held is >= 1 and <= MaxGames) return new Slot(held, null);
        var wait = Stopwatch.StartNew();
        double lastNote = -60;
        while (true)
        {
            ct.ThrowIfCancellationRequested();
            double free = FreeGb();
            if (free >= minFreeGb && StaggerOk())
                for (int number = 1; number <= EffectiveMaxGames; number++)
                    if (TryClaim(number) is { } slot)
                    {
                        File.WriteAllText(Path.Combine(Root, ".qa-slot-last"), DateTime.UtcNow.Ticks.ToString());
                        Console.WriteLine($"QA slot {number} of {MaxGames} claimed for {what}{(PlayingMonitor != null ? " [owner playing: 1 game on " + PlayingMonitor + "]" : "")} (free RAM {free:F1} GB, waited {wait.Elapsed.TotalSeconds:F0} s).");
                        return slot;
                    }
            if (wait.Elapsed.TotalSeconds - lastNote >= 60)
            {
                lastNote = wait.Elapsed.TotalSeconds;
                Console.WriteLine($"Waiting for a QA slot for {what}: free RAM {free:F1} GB (need {minFreeGb:F0}), slots in use {InUse()}/{MaxGames}...");
            }
            if (wait.Elapsed > TimeSpan.FromHours(3)) throw new TimeoutException("No QA slot or enough free RAM within 3 hours.");
            await Task.Delay(3000, ct);
        }
    }

    private static bool StaggerOk()
    {
        try
        {
            string last = Path.Combine(Root, ".qa-slot-last");
            return !File.Exists(last) || !long.TryParse(File.ReadAllText(last), out long ticks) || DateTime.UtcNow - new DateTime(ticks, DateTimeKind.Utc) > TimeSpan.FromSeconds(25);
        }
        catch (IOException) { return true; }
    }

    private static int InUse() => Enumerable.Range(1, MaxGames).Count(n => Directory.Exists(Path.Combine(Root, ".qa-slot-" + n)));

    private static Slot? TryClaim(int number)
    {
        string folder = Path.Combine(Root, ".qa-slot-" + number), owner = Path.Combine(folder, "owner.txt");
        try
        {
            if (Directory.Exists(folder))
            {
                // A folder with a live owner is taken; one made by someone's own mkdir (no owner.txt) is taken for 3 hours.
                bool taken = File.Exists(owner) ? OwnerAlive(File.ReadAllText(owner)) : Directory.GetCreationTimeUtc(folder) > DateTime.UtcNow.AddHours(-3);
                if (taken) return null;
                Directory.Delete(folder, true);
            }
            Directory.CreateDirectory(folder);
            using (var stream = new FileStream(owner, FileMode.CreateNew, FileAccess.Write, FileShare.Read))
            using (var writer = new StreamWriter(stream))
                writer.Write($"pid={Environment.ProcessId};start={Process.GetCurrentProcess().StartTime.ToUniversalTime().Ticks};cwd={Environment.CurrentDirectory}");
            return new Slot(number, folder);
        }
        catch (IOException) { return null; }
        catch (UnauthorizedAccessException) { return null; }
    }

    private static bool OwnerAlive(string text)
    {
        try
        {
            var parts = text.Split(';').Select(p => p.Split('=', 2)).Where(p => p.Length == 2).ToDictionary(p => p[0], p => p[1]);
            using var process = Process.GetProcessById(int.Parse(parts["pid"]));
            return !process.HasExited && (!parts.TryGetValue("start", out var start) || process.StartTime.ToUniversalTime().Ticks.ToString() == start);
        }
        catch (Exception e) when (e is ArgumentException or KeyNotFoundException or FormatException or InvalidOperationException or System.ComponentModel.Win32Exception) { return false; }
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    private class MemoryStatus
    {
        public uint Length = (uint)Marshal.SizeOf<MemoryStatus>(), Load;
        public ulong TotalPhys, AvailPhys, TotalPageFile, AvailPageFile, TotalVirtual, AvailVirtual, AvailExtendedVirtual;
    }
    [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)] private static extern bool GlobalMemoryStatusEx([In, Out] MemoryStatus status);

    public static double FreeGb()
    {
        var status = new MemoryStatus();
        return GlobalMemoryStatusEx(status) ? status.AvailPhys / 1073741824.0 : 99;
    }

    private static readonly System.Text.RegularExpressions.Regex MixinError = new(
        @"InvalidMixinException|MixinApplyError|MixinTransformerError|InvalidInjectionException|InjectionError|Critical injection failure|Mixin apply (for mod \S+ )?failed|Mixin transformation of .* failed|@Inject.*failed|was not located in the target class",
        System.Text.RegularExpressions.RegexOptions.IgnoreCase);

    /// <summary>A log line about a failed mixin that concerns the Lads Core (theladscore / com.thelads).</summary>
    public static bool MixinProblem(string line) =>
        (line.Contains("theladscore", StringComparison.OrdinalIgnoreCase) || line.Contains("com.thelads", StringComparison.OrdinalIgnoreCase)) && MixinError.IsMatch(line);

    // ---- the Core jar -----------------------------------------------------------------------------------------------

    /// <summary>The freshly built 1.20.1 Core (TheLadsCore\v1_20_1 remapJar output). Rebuilt first when a source file is newer than
    /// the jar (or --build), unless --no-build. Checks its Fabric id and that every mixin class its mixin JSONs list is in the jar.</summary>
    public static string CoreJar1201(string root, string? explicitJar, bool forceBuild, bool noBuild)
    {
        if (explicitJar != null) { CheckCoreJar(Path.GetFullPath(explicitJar)); return Path.GetFullPath(explicitJar); }
        string libs = Path.Combine(root, "TheLadsCore", "v1_20_1", "build", "libs");
        string? Newest() => Directory.Exists(libs)
            ? new DirectoryInfo(libs).EnumerateFiles("*-mc1.20.1.jar").Where(f => !f.Name.Contains("-sources", StringComparison.Ordinal)).OrderByDescending(f => f.LastWriteTimeUtc).FirstOrDefault()?.FullName
            : null;
        string? jar = Newest();
        bool stale = jar == null || NewestSource(root) > File.GetLastWriteTimeUtc(jar);
        if (!noBuild && (forceBuild || stale))
        {
            Console.WriteLine(jar == null ? "No 1.20.1 Core jar yet: building it (gradlew :v1_20_1:remapJar)." : "The 1.20.1 Core jar is older than its sources: rebuilding (gradlew :v1_20_1:remapJar).");
            Gradle(root, ":v1_20_1:remapJar");
            jar = Newest();
        }
        else if (stale) Console.WriteLine("WARNING: the 1.20.1 Core jar is older than the sources (--no-build): this run does not test your latest edits.");
        if (jar == null) throw new FileNotFoundException("No TheLadsCore\\v1_20_1\\build\\libs\\*-mc1.20.1.jar; build it with: gradlew :v1_20_1:remapJar");
        CheckCoreJar(jar);
        return jar;
    }

    private static DateTime NewestSource(string root)
    {
        DateTime newest = DateTime.MinValue;
        foreach (var build in new[] { "v1_20_1\\build.gradle", "common\\build.gradle", "build.gradle", "gradle.properties" })
            if (File.Exists(Path.Combine(root, "TheLadsCore", build)) && File.GetLastWriteTimeUtc(Path.Combine(root, "TheLadsCore", build)) is var written && written > newest) newest = written;
        foreach (var dir in new[] { "v1_20_1\\src", "common\\src" })
        {
            string path = Path.Combine(root, "TheLadsCore", dir);
            if (!Directory.Exists(path)) continue;
            foreach (var file in new DirectoryInfo(path).EnumerateFiles("*", SearchOption.AllDirectories))
                if (file.LastWriteTimeUtc > newest) newest = file.LastWriteTimeUtc;
        }
        return newest;
    }

    /// <summary>gradlew in TheLadsCore at BelowNormal priority (the owner may be gaming), --max-workers=4.</summary>
    public static void Gradle(string root, string task)
    {
        var start = new ProcessStartInfo(Path.Combine(root, "TheLadsCore", "gradlew.bat")) { WorkingDirectory = Path.Combine(root, "TheLadsCore"), UseShellExecute = false };
        foreach (var arg in new[] { task, "--max-workers=4", "--console=plain" }) start.ArgumentList.Add(arg);
        using var process = Process.Start(start)!;
        try { process.PriorityClass = ProcessPriorityClass.BelowNormal; } catch (InvalidOperationException) { } // children inherit it
        process.WaitForExit();
        if (process.ExitCode != 0) throw new InvalidOperationException($"gradlew {task} failed (exit code {process.ExitCode}); run it in TheLadsCore to see why.");
    }

    /// <summary>The jar declares Fabric mod id theladscore for Minecraft 1.20.1 and holds every mixin class its mixin configs list
    /// (a missing one crashes Fabric at startup, which would open an error window).</summary>
    public static void CheckCoreJar(string jar)
    {
        using var zip = ZipFile.OpenRead(jar);
        var meta = JsonNode.Parse(Read(zip, "fabric.mod.json") ?? throw new InvalidDataException($"{jar} has no fabric.mod.json"))!;
        if (meta["id"]?.GetValue<string>() != "theladscore") throw new InvalidDataException($"{jar} is not the Lads Core (id {meta["id"]}).");
        var names = zip.Entries.Select(e => e.FullName).ToHashSet(StringComparer.Ordinal);
        var missing = new List<string>();
        foreach (var config in zip.Entries.Where(e => e.FullName.EndsWith(".mixins.json", StringComparison.Ordinal) && !e.FullName.Contains('/')))
        {
            var json = JsonNode.Parse(Read(zip, config.FullName)!)!;
            string package = json["package"]!.GetValue<string>().Replace('.', '/');
            foreach (var group in new[] { "mixins", "client", "server" })
                foreach (var mixin in json[group]?.AsArray() ?? [])
                {
                    string entry = package + "/" + mixin!.GetValue<string>().Replace('.', '/') + ".class";
                    if (!names.Contains(entry)) missing.Add($"{config.FullName}: {entry}");
                }
        }
        if (missing.Count > 0) throw new InvalidDataException("Mixin classes listed in the Core's mixin JSON are missing from the jar: " + string.Join("; ", missing));
        Console.WriteLine($"Core jar {jar} ({new FileInfo(jar).Length / 1024} KB, {File.GetLastWriteTime(jar):yyyy-MM-dd HH:mm:ss}): mixin check OK.");
    }

    private static string? Read(ZipArchive zip, string name)
    {
        var entry = zip.GetEntry(name);
        if (entry == null) return null;
        using var reader = new StreamReader(entry.Open());
        return reader.ReadToEnd();
    }

    /// <summary>A machine-wide lock held by an open file handle (released by the OS if the harness dies).</summary>
    public static async Task<IDisposable> LockAsync(string file, CancellationToken ct)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(file)!);
        while (true)
        {
            try { return new FileStream(file, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None, 1, FileOptions.DeleteOnClose); }
            catch (IOException) { await Task.Delay(1000, ct); }
        }
    }

    /// <summary>--remove-mods a,b,c: deletes the jars of the sandbox mods folder whose file name equals or contains a name (case-insensitive).
    /// For lanes that test a Lads module replacing a duplicate mod. Returns one report line per name.</summary>
    public static List<string> RemoveMods(string modsFolder, string? names)
    {
        var report = new List<string>();
        foreach (var name in (names ?? "").Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            var hits = Directory.Exists(modsFolder)
                ? Directory.EnumerateFiles(modsFolder, "*.jar").Where(f => Path.GetFileName(f).Contains(name, StringComparison.OrdinalIgnoreCase)).ToList()
                : new List<string>();
            foreach (var hit in hits) File.Delete(hit);
            report.Add(hits.Count == 0 ? $"remove-mods: '{name}' matched no jar in {modsFolder}" : $"remove-mods: '{name}' deleted {string.Join(", ", hits.Select(Path.GetFileName))}");
        }
        return report;
    }

    /// <summary>LADS_VERIFY_ADD_MODS=jar[;jar...]: copies stub or extra jars into the sandbox mods folder after the pack installer ran
    /// (QA of a Lads built-in standing down when the real mod is present: a jar holding only a fabric.mod.json with that id).</summary>
    public static List<string> AddMods(string modsFolder, string? jars)
    {
        var report = new List<string>();
        foreach (var jar in (jars ?? "").Split(';', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            if (!File.Exists(jar)) throw new FileNotFoundException("LADS_VERIFY_ADD_MODS: no such jar", jar);
            Directory.CreateDirectory(modsFolder);
            File.Copy(jar, Path.Combine(modsFolder, Path.GetFileName(jar)), true);
            report.Add($"add-mods: copied {Path.GetFileName(jar)} into {modsFolder}");
        }
        return report;
    }

    // ---- caches -----------------------------------------------------------------------------------------------------

    /// <summary>Modrinth jars of a version's client pack, kept by file name: the installer keeps a jar whose hash matches, so a jar
    /// restored from here is not downloaded again. Before the installer: restore; after: remember what it downloaded.</summary>
    public static void RestoreModJars(string version, string modsFolder)
    {
        string cache = Path.Combine(CacheRoot, "mod-jars", version);
        if (!Directory.Exists(cache)) return;
        Directory.CreateDirectory(modsFolder);
        foreach (var jar in Directory.EnumerateFiles(cache, "*.jar"))
        {
            string target = Path.Combine(modsFolder, Path.GetFileName(jar));
            if (!File.Exists(target)) File.Copy(jar, target);
        }
    }

    public static void RememberModJars(string version, string modsFolder, IEnumerable<string> fileNames)
    {
        string cache = Path.Combine(CacheRoot, "mod-jars", version);
        Directory.CreateDirectory(cache);
        foreach (var name in fileNames)
        {
            string from = Path.Combine(modsFolder, name), to = Path.Combine(cache, name);
            if (File.Exists(from) && !File.Exists(to)) try { File.Copy(from, to); } catch (IOException) { }
        }
    }

    /// <summary>robocopy source -> target (mirror), the source untouched. Exit codes below 8 are success.</summary>
    public static void Mirror(string source, string target, params string[] excludeDirectories)
    {
        var start = new ProcessStartInfo("robocopy.exe") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true };
        foreach (var arg in new[] { source, target, "/MIR", "/R:1", "/W:1", "/NFL", "/NDL", "/NJH", "/NJS", "/NP", "/MT:8" }) start.ArgumentList.Add(arg);
        if (excludeDirectories.Length > 0) { start.ArgumentList.Add("/XD"); foreach (var dir in excludeDirectories) start.ArgumentList.Add(dir); }
        using var process = Process.Start(start)!;
        process.StandardOutput.ReadToEnd();
        process.WaitForExit();
        if (process.ExitCode >= 8) throw new IOException($"robocopy {source} -> {target} failed (exit code {process.ExitCode}).");
    }
}
