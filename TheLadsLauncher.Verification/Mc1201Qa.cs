/// <summary>
/// --mc1201 [&lt;repository root&gt;] [options]: the standard "The Lads Client 1.20.1" profile (Fabric Loader 0.19.5, Java 17, the 50 pinned
/// client mods) in the QA sandbox, with the Core jar built from THIS worktree, started to the title screen and (unless --no-world)
/// an auto-created superflat QA world. It is the normal "1.20.1 --title" run with LADS_VERIFY_AUTO_WORLD=1, so every option of
/// that path (QA options, slots, tile, evidence folder) applies; this only turns the options into the environment it reads.
/// Options: --script &lt;file&gt; (QaProbe1201 script), --script-text "a;b;c", --remove-mods a,b (delete matching jars from the sandbox mods),
/// --dir &lt;name&gt; (another QA game folder, for a second game of the same worktree), --no-world, --fresh-world (delete this folder's QA
/// world first), --build / --no-build (the Core is rebuilt by default when its sources are newer than the jar), --core-jar &lt;jar&gt;,
/// --loader &lt;version&gt; (default and minimum 0.19.5), --timeout-sec N, --scenario &lt;name&gt;, --expect-core-disabled.
/// </summary>
static class Mc1201Qa
{
    public static string[] Prepare(string[] a)
    {
        string? root = a.Length > 0 && !a[0].StartsWith("--", StringComparison.Ordinal) ? a[0] : FindRoot(Environment.CurrentDirectory) ?? FindRoot(AppContext.BaseDirectory);
        if (root == null) throw new ArgumentException("--mc1201: give the repository root, or run from inside the worktree.");
        var result = new List<string> { "1.20.1", Path.GetFullPath(root), "--title" };
        for (int i = a.Length > 0 && !a[0].StartsWith("--", StringComparison.Ordinal) ? 1 : 0; i < a.Length; i++)
        {
            string Value() => i + 1 < a.Length ? a[++i] : throw new ArgumentException($"--mc1201: {a[i]} needs a value.");
            switch (a[i])
            {
                case "--script": Environment.SetEnvironmentVariable("LADS_VERIFY_SCRIPT", Path.GetFullPath(Value())); break;
                case "--script-text": Environment.SetEnvironmentVariable("LADS_VERIFY_SCRIPT_TEXT", Value()); break;
                case "--remove-mods": Environment.SetEnvironmentVariable("LADS_VERIFY_REMOVE_MODS", Value()); break;
                case "--core-jar": Environment.SetEnvironmentVariable("LADS_VERIFY_CORE_JAR", Path.GetFullPath(Value())); break;
                case "--loader": Environment.SetEnvironmentVariable("LADS_VERIFY_LOADER", Value()); break;
                case "--timeout-sec": Environment.SetEnvironmentVariable("LADS_VERIFY_TIMEOUT_SEC", Value()); break;
                case "--scenario": Environment.SetEnvironmentVariable("LADS_VERIFY_SCENARIO", Value()); break;
                case "--dir": result.Add("--dir"); result.Add(Value()); break;
                case "--build": Environment.SetEnvironmentVariable("LADS_VERIFY_BUILD", "1"); break;
                case "--no-build": Environment.SetEnvironmentVariable("LADS_VERIFY_NO_BUILD", "1"); break;
                case "--fresh-world": Environment.SetEnvironmentVariable("LADS_VERIFY_FRESH_WORLD", "1"); break;
                case "--no-world": Environment.SetEnvironmentVariable("LADS_VERIFY_AUTO_WORLD", "0"); break;
                case "--expect-core-disabled": result.Add("--expect-core-disabled"); break;
                default: throw new ArgumentException($"--mc1201: unknown option {a[i]}.");
            }
        }
        if (Environment.GetEnvironmentVariable("LADS_VERIFY_AUTO_WORLD") is null or "") Environment.SetEnvironmentVariable("LADS_VERIFY_AUTO_WORLD", "1");
        return [.. result];
    }

    /// <summary>The nearest folder up from <paramref name="start"/> holding TheLadsCore\settings.gradle, or null.</summary>
    public static string? FindRoot(string start)
    {
        for (var dir = new DirectoryInfo(start); dir != null; dir = dir.Parent)
            if (File.Exists(Path.Combine(dir.FullName, "TheLadsCore", "settings.gradle"))) return dir.FullName;
        return null;
    }
}
