using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;

namespace TheLadsLauncher.Services;

/// <summary>Fabric metadata of one jar. Name falls back to the id; it is null only for a nested jar without
/// fabric.mod.json, whose Id is then its file name. Server-only modules keep no provides, depends or children.</summary>
public sealed record FabricModInfo(string Id, string? Name, string? Version, string? Description, IReadOnlyList<string> Authors,
    string? License, string? Environment, IReadOnlyList<string> Provides, IReadOnlyDictionary<string, string> Depends,
    bool IsLibraryBadge, IReadOnlyList<FabricModInfo> Children, string? NestedPath)
{
    public IReadOnlyDictionary<string, string> Breaks { get; init; } = new Dictionary<string, string>();
    /// <summary>Icon bytes of a top-level jar, only when requested.</summary>
    public byte[]? Icon { get; init; }
    /// <summary>Read from Forge's mcmod.info (see <see cref="FabricModMetadata.ReadForgeJar"/>), not fabric.mod.json.</summary>
    public bool Forge { get; init; }
    public bool HasMetadata => Name != null;
    public bool IsServerOnly => Environment == "server";
}

public static class FabricModMetadata
{
    // Flashback includes native encoders and exceeds 200 MB. Keep downloads bounded.
    public const long MaximumJarSize = 512L * 1024 * 1024;
    private const int MaximumDepth = 8, MaximumNestedJars = 1024, MaximumMetadataSize = 1024 * 1024;

    /// <summary>Returns null for a jar without fabric.mod.json; throws InvalidDataException for invalid metadata or zips.</summary>
    public static FabricModInfo? ReadJar(string path, bool includeIcon = false, CancellationToken token = default)
    {
        // FileShare.Delete: a reader (e.g. the Mods page) must never make an installer's rename of this jar fail.
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read | FileShare.Delete);
        return ReadJar(stream, includeIcon, token);
    }

    public static FabricModInfo? ReadJar(Stream stream, bool includeIcon = false, CancellationToken token = default)
    {
        using var zip = new ZipArchive(stream, ZipArchiveMode.Read, leaveOpen: true);
        return Read(zip, null, 0, new Budget(), includeIcon, token);
    }

    /// <summary>Forge's mcmod.info (legacy Forge such as 1.8.9's): a JSON array of mods, or {"modList": [...]}. The first mod
    /// names the jar. OptiFine has none and is recognised by its Forge tweaker (id "optifine"). Null for other jars without
    /// one (coremods); InvalidDataException when unreadable.</summary>
    public static FabricModInfo? ReadForgeJar(string path)
    {
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read | FileShare.Delete);
        using var zip = new ZipArchive(stream, ZipArchiveMode.Read);
        var entry = zip.GetEntry("mcmod.info");
        if (entry == null) return ReadOptiFine(zip);
        if (entry.Length > MaximumMetadataSize) throw new InvalidDataException("Oversized mcmod.info.");
        using var document = ReadFabricMetadata(entry.Open(), "mcmod.info");
        var root = document.RootElement;
        var mods = root.ValueKind == JsonValueKind.Object && root.TryGetProperty("modList", out var list) ? list : root;
        var mod = mods.ValueKind == JsonValueKind.Array ? mods.EnumerateArray().FirstOrDefault(m => Text(m, "modid") != null) : default;
        var id = Text(mod, "modid") ?? throw new InvalidDataException("mcmod.info names no mod.");
        var authors = mod.TryGetProperty("authorList", out var names) && names.ValueKind == JsonValueKind.Array
            ? names.EnumerateArray().Where(n => n.ValueKind == JsonValueKind.String).Select(n => n.GetString()!).ToList() : new List<string>();
        return new FabricModInfo(id, Text(mod, "name") ?? id, Text(mod, "version"), Text(mod, "description"), authors, null, null,
            Array.Empty<string>(), new Dictionary<string, string>(), false, Array.Empty<FabricModInfo>(), null) { Forge = true };
    }

    // Forge loads OptiFine through the TweakClass in its manifest. Its changelog.txt opens with "OptiFine <version>".
    private static FabricModInfo? ReadOptiFine(ZipArchive zip)
    {
        if (zip.GetEntry("optifine/OptiFineForgeTweaker.class") == null) return null;
        string? version = null;
        if (zip.GetEntry("changelog.txt") is { Length: <= MaximumMetadataSize } changelog)
        {
            using var reader = new StreamReader(changelog.Open());
            if (reader.ReadLine() is { } first && first.StartsWith("OptiFine ", StringComparison.Ordinal)) version = first["OptiFine ".Length..].Trim();
        }
        return new FabricModInfo(OptiFineInstaller.ModId, "OptiFine", version, null, new[] { "sp614x" }, null, null, Array.Empty<string>(),
            new Dictionary<string, string>(), false, Array.Empty<FabricModInfo>(), null) { Forge = true };
    }

    /// <summary>The mod and its nested Fabric modules that load on the client, depth first.</summary>
    public static IEnumerable<FabricModInfo> ClientModules(FabricModInfo mod)
    {
        if (!mod.HasMetadata || mod.IsServerOnly) yield break;
        yield return mod;
        foreach (var child in mod.Children)
            foreach (var nested in ClientModules(child)) yield return nested;
    }

    public static bool ValidId(string? value) => Regex.IsMatch(value ?? "", "^[a-z][a-z0-9_-]{1,63}$");

    private sealed class Budget { public long Bytes = 128L * 1024 * 1024; public int Jars = MaximumNestedJars; }

    private static FabricModInfo? Read(ZipArchive zip, string? nestedPath, int depth, Budget budget, bool includeIcon, CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        if (depth > MaximumDepth || --budget.Jars < 0) throw new InvalidDataException("Fabric nested-jar limit exceeded.");
        var entries = zip.Entries.Where(e => e.FullName == "fabric.mod.json").ToList();
        if (entries.Count == 0) return null; // Ordinary libraries embedded by some Fabric mods.
        if (entries.Count != 1 || entries[0].Length > MaximumMetadataSize)
            throw new InvalidDataException("Ambiguous or oversized Fabric metadata.");
        using var document = ReadFabricMetadata(entries[0].Open());
        var root = document.RootElement;
        var id = Text(root, "id");
        if (!ValidId(id)) throw new InvalidDataException("Invalid Fabric mod ID.");
        var environment = Text(root, "environment");
        var provides = new List<string>();
        var depends = new Dictionary<string, string>(StringComparer.Ordinal);
        var breaks = new Dictionary<string, string>(StringComparer.Ordinal);
        var children = new List<FabricModInfo>();
        if (environment != "server")
        {
            if (root.TryGetProperty("provides", out var aliases))
                foreach (var alias in aliases.EnumerateArray())
                {
                    var value = alias.GetString();
                    if (!ValidId(value)) throw new InvalidDataException($"Invalid provided mod ID in {id}.");
                    provides.Add(value!);
                }
            ReadDependencies(root, "depends", id!, depends, validate: true);
            ReadDependencies(root, "breaks", id!, breaks, validate: false);
            if (root.TryGetProperty("jars", out var nestedJars))
                foreach (var nested in nestedJars.EnumerateArray())
                {
                    token.ThrowIfCancellationRequested();
                    var name = Text(nested, "file") ?? throw new InvalidDataException($"Missing nested jar path in {id}.");
                    // Entries stay in memory; their names are never used as filesystem paths.
                    var matches = zip.Entries.Where(e => e.FullName == name).ToList();
                    if (matches.Count != 1 || matches[0].Length > budget.Bytes)
                        throw new InvalidDataException($"Missing, ambiguous or oversized nested jar '{name}' in {id}.");
                    budget.Bytes -= matches[0].Length;
                    using var input = matches[0].Open();
                    using var bytes = new MemoryStream();
                    var buffer = new byte[81920];
                    int count;
                    while ((count = input.Read(buffer, 0, buffer.Length)) != 0)
                    {
                        token.ThrowIfCancellationRequested();
                        if (bytes.Length + count > matches[0].Length)
                            throw new InvalidDataException($"Oversized nested jar '{name}' in {id}.");
                        bytes.Write(buffer, 0, count);
                    }
                    bytes.Position = 0;
                    using var nestedZip = new ZipArchive(bytes, ZipArchiveMode.Read);
                    children.Add(Read(nestedZip, name, depth + 1, budget, false, token)
                        ?? new FabricModInfo(name[(name.LastIndexOf('/') + 1)..], null, null, null, Array.Empty<string>(), null, null,
                            Array.Empty<string>(), new Dictionary<string, string>(), false, Array.Empty<FabricModInfo>(), name));
                }
        }
        return new FabricModInfo(id!, Text(root, "name") ?? id, Text(root, "version"), Text(root, "description"), Authors(root),
            License(root), environment, provides, depends, IsLibrary(root), children, nestedPath)
        {
            Breaks = breaks,
            Icon = includeIcon ? ReadIcon(zip, root) : null
        };
    }

    private static void ReadDependencies(JsonElement root, string property, string id, Dictionary<string, string> target, bool validate)
    {
        if (!root.TryGetProperty(property, out var dependencies) || (!validate && dependencies.ValueKind != JsonValueKind.Object)) return;
        foreach (var dependency in dependencies.EnumerateObject())
        {
            if (!ValidId(dependency.Name))
            {
                if (validate) throw new InvalidDataException($"Invalid dependency in {id}.");
                continue;
            }
            target[dependency.Name] = dependency.Value.ValueKind == JsonValueKind.String
                ? dependency.Value.GetString()! : dependency.Value.GetRawText();
        }
    }

    private static List<string> Authors(JsonElement root)
    {
        var authors = new List<string>();
        if (root.TryGetProperty("authors", out var list) && list.ValueKind == JsonValueKind.Array)
            foreach (var author in list.EnumerateArray())
            {
                var name = author.ValueKind == JsonValueKind.String ? author.GetString() : Text(author, "name");
                if (!string.IsNullOrWhiteSpace(name)) authors.Add(name);
            }
        return authors;
    }

    private static string? License(JsonElement root)
    {
        if (!root.TryGetProperty("license", out var license)) return null;
        if (license.ValueKind == JsonValueKind.String) return license.GetString();
        return license.ValueKind == JsonValueKind.Array
            ? string.Join(", ", license.EnumerateArray().Where(l => l.ValueKind == JsonValueKind.String).Select(l => l.GetString()))
            : null;
    }

    private static bool IsLibrary(JsonElement root) =>
        root.TryGetProperty("custom", out var custom) && custom.ValueKind == JsonValueKind.Object
        && custom.TryGetProperty("modmenu", out var modmenu) && modmenu.ValueKind == JsonValueKind.Object
        && modmenu.TryGetProperty("badges", out var badges) && badges.ValueKind == JsonValueKind.Array
        && badges.EnumerateArray().Any(b => b.ValueKind == JsonValueKind.String && b.GetString() == "library");

    private static byte[]? ReadIcon(ZipArchive zip, JsonElement root)
    {
        if (!root.TryGetProperty("icon", out var icon)) return null;
        // Fabric allows one path or a map of pixel size to path; take the largest.
        var path = icon.ValueKind == JsonValueKind.String ? icon.GetString()
            : icon.ValueKind == JsonValueKind.Object ? icon.EnumerateObject()
                .Where(p => p.Value.ValueKind == JsonValueKind.String && int.TryParse(p.Name, out _))
                .OrderByDescending(p => int.Parse(p.Name, CultureInfo.InvariantCulture)).Select(p => p.Value.GetString()).FirstOrDefault()
            : null;
        var entry = path == null ? null : zip.Entries.FirstOrDefault(e => e.FullName == path.TrimStart('/'));
        if (entry == null || entry.Length > MaximumMetadataSize) return null;
        using var input = entry.Open();
        using var bytes = new MemoryStream();
        input.CopyTo(bytes);
        return bytes.ToArray();
    }

    private static string? Text(JsonElement value, string name) =>
        value.ValueKind == JsonValueKind.Object && value.TryGetProperty(name, out var text)
            && text.ValueKind == JsonValueKind.String ? text.GetString() : null;

    // Fabric's Gson reader accepts literal line breaks in description strings. Normalize
    // only string control characters for System.Text.Json; downloaded jar bytes stay intact.
    private static JsonDocument ReadFabricMetadata(Stream stream, string file = "fabric.mod.json")
    {
        using (stream)
        using (var reader = new StreamReader(stream, Encoding.UTF8, true, 4096))
        {
            var source = reader.ReadToEnd();
            var normalized = new StringBuilder(source.Length);
            bool quoted = false, escaped = false;
            foreach (char ch in source)
            {
                if (quoted && ch < 0x20)
                    normalized.Append("\\u").Append(((int)ch).ToString("x4"));
                else normalized.Append(ch);
                if (!escaped && ch == '"') quoted = !quoted;
                escaped = quoted && !escaped && ch == '\\';
            }
            // mcmod.info files are hand-written: trailing commas and comments are common and Forge accepts them.
            var options = file == "mcmod.info" ? new JsonDocumentOptions { AllowTrailingCommas = true, CommentHandling = JsonCommentHandling.Skip } : default;
            try { return JsonDocument.Parse(normalized.ToString(), options); }
            catch (JsonException e) { throw new InvalidDataException($"Unreadable {file}: " + e.Message, e); }
        }
    }
}

/// <summary>Fabric dependency predicates: "*", exact, =, &gt;=, &lt;=, &gt;, &lt;, ~, ^, x/X wildcards, space-separated AND and
/// JSON arrays (OR). Only used for warnings and the Unsupported status: anything it cannot parse counts as a match,
/// because Fabric itself stays authoritative.</summary>
public static class FabricVersionPredicate
{
    public static bool Matches(string predicate, string version)
    {
        var text = predicate.Trim();
        if (text.StartsWith('['))
        {
            List<string?> alternatives;
            try { alternatives = JsonSerializer.Deserialize<List<string?>>(text) ?? new(); }
            catch (JsonException) { return true; } // Not a list of strings: leave the verdict to Fabric.
            return alternatives.Count == 0 || alternatives.Any(a => a == null || Matches(a, version));
        }
        return text.Split(' ', StringSplitOptions.RemoveEmptyEntries).All(part => MatchesOne(part, version));
    }

    private static bool MatchesOne(string part, string version)
    {
        if (part is "*" or "x" or "X") return true;
        var op = new[] { ">=", "<=", ">", "<", "=", "~", "^" }.FirstOrDefault(o => part.StartsWith(o, StringComparison.Ordinal)) ?? "";
        var target = part[op.Length..];
        var actual = Parse(version);
        if (actual == null) return op is "" or "=" ? target == version : true;
        var wildcard = target.Split('-', '+')[0].Split('.').Select(c => c is "x" or "X" or "*").ToList();
        var bound = Parse(Regex.Replace(target, @"(?<=^|\.)[xX*](?=$|[.+-])", "0"));
        if (bound == null) return true;
        var fixedComponents = wildcard.IndexOf(true);
        if (fixedComponents >= 0 && op is "" or "=")
            return actual.Numbers.Take(fixedComponents).SequenceEqual(Pad(bound.Numbers, fixedComponents).Take(fixedComponents));
        var comparison = Compare(actual, bound);
        return op switch
        {
            ">=" => comparison >= 0,
            "<=" => comparison <= 0,
            ">" => comparison > 0,
            "<" => comparison < 0,
            "~" => comparison >= 0 && Compare(actual, NextRelease(bound, Math.Min(1, bound.Numbers.Count - 1))) < 0,
            "^" => comparison >= 0 && Compare(actual, NextRelease(bound, 0)) < 0,
            _ => comparison == 0
        };
    }

    private sealed record SemVer(List<long> Numbers, string[]? Prerelease);

    private static SemVer? Parse(string version)
    {
        var core = version.Split('+')[0];
        var dash = core.IndexOf('-');
        var numbers = new List<long>();
        foreach (var component in (dash < 0 ? core : core[..dash]).Split('.'))
        {
            if (!long.TryParse(component, NumberStyles.None, CultureInfo.InvariantCulture, out var number)) return null;
            numbers.Add(number);
        }
        return new SemVer(numbers, dash < 0 ? null : core[(dash + 1)..].Split('.', StringSplitOptions.RemoveEmptyEntries));
    }

    private static List<long> Pad(List<long> numbers, int count) =>
        numbers.Concat(Enumerable.Repeat(0L, Math.Max(0, count - numbers.Count))).ToList();

    // The smallest version above every release of the given component: ~1.2 -> 1.3-, ^1.2 -> 2-.
    private static SemVer NextRelease(SemVer bound, int component)
    {
        var numbers = Pad(bound.Numbers, component + 1).Take(component + 1).ToList();
        numbers[component]++;
        return new SemVer(numbers, Array.Empty<string>());
    }

    private static int Compare(SemVer left, SemVer right)
    {
        var count = Math.Max(left.Numbers.Count, right.Numbers.Count);
        var a = Pad(left.Numbers, count);
        var b = Pad(right.Numbers, count);
        for (var i = 0; i < count; i++)
            if (a[i] != b[i]) return a[i].CompareTo(b[i]);
        if (left.Prerelease == null || right.Prerelease == null)
            return (left.Prerelease == null ? 1 : 0) - (right.Prerelease == null ? 1 : 0);
        for (var i = 0; i < Math.Min(left.Prerelease.Length, right.Prerelease.Length); i++)
        {
            var x = left.Prerelease[i];
            var y = right.Prerelease[i];
            var result = long.TryParse(x, out var nx) && long.TryParse(y, out var ny) ? nx.CompareTo(ny) : string.CompareOrdinal(x, y);
            if (result != 0) return Math.Sign(result);
        }
        return left.Prerelease.Length.CompareTo(right.Prerelease.Length);
    }
}
