using System;
using System.Globalization;
using System.IO;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>
/// Per-profile mod choices in &lt;gameDir&gt;\lads-mod-state.json, shared with LadsCore:
/// {"schema":1,"mods":{"&lt;fabric id&gt;":{"enabled":false,"projectId":"…","updatedAt":"…","source":"launcher|game"}}}.
/// Only explicit keys are choices: an id without a key keeps its current disk state. Writers keep unknown content.
/// </summary>
public sealed class ModPreferences
{
    public const string FileName = "lads-mod-state.json";
    private static readonly TimeSpan LockTimeout = TimeSpan.FromSeconds(3);
    private static readonly JsonSerializerOptions Indented = new() { WriteIndented = true };
    private readonly JsonObject mods;

    private ModPreferences(JsonObject mods, string? error)
    {
        this.mods = mods;
        Error = error;
    }

    /// <summary>Why the file could not be read, or null. An unreadable file counts as no choices (disk state wins).</summary>
    public string? Error { get; }

    public static string PathFor(string gameDirectory) => Path.Combine(Path.GetFullPath(gameDirectory), FileName);

    /// <summary>Lock-free read; a missing file means no choices.</summary>
    public static ModPreferences Load(string gameDirectory)
    {
        var path = PathFor(gameDirectory);
        return Parse(ReadShared(path), path);
    }

    /// <summary>The explicit choice for the id, else for another id with the same Modrinth project, else null (keep disk state).</summary>
    public bool? GetEnabled(string id, string? projectId)
    {
        if (mods[id] is JsonObject own && Enabled(own) is bool choice) return choice;
        if (projectId == null) return null;
        foreach (var (_, node) in mods)
            if (node is JsonObject entry && entry["projectId"] is JsonValue project && project.TryGetValue<string>(out var value)
                && value == projectId && Enabled(entry) is bool sameProject)
                return sameProject;
        return null;
    }

    internal sealed record RendererJar(string FileName, string Sha512);

    internal RendererJar? GetRendererSuspendedJar(string id)
    {
        if (mods[id] is JsonObject entry && entry["rendererSuspendedJar"] is JsonObject jar
            && jar["fileName"] is JsonValue file && file.TryGetValue<string>(out var name)
            && jar["sha512"] is JsonValue hash && hash.TryGetValue<string>(out var sha512)
            && !string.IsNullOrWhiteSpace(name) && !string.IsNullOrWhiteSpace(sha512))
            return new(name, sha512);
        return null;
    }

    // Stored with the choice transaction so a failed install cannot lose which physical jar was active.
    internal static void SetRendererSuspendedJar(JsonObject root, string id, RendererJar? jar)
    {
        var all = root["mods"] as JsonObject ?? (JsonObject)(root["mods"] = new JsonObject())!;
        if (jar == null) { if (all[id] is JsonObject old) old.Remove("rendererSuspendedJar"); return; }
        var entry = all[id] as JsonObject ?? (JsonObject)(all[id] = new JsonObject())!;
        entry["rendererSuspendedJar"] = new JsonObject { ["fileName"] = jar.FileName, ["sha512"] = jar.Sha512 };
    }

    /// <summary>
    /// The single writer: a locked read-modify-write of the whole JSON tree. <paramref name="mutate"/> receives the root with
    /// "schema" and a "mods" object present. An unreadable file is renamed to lads-mod-state.json.corrupt-&lt;stamp&gt; first.
    /// </summary>
    public static async Task UpdateAsync(string gameDirectory, Action<JsonObject> mutate, CancellationToken cancellationToken = default)
    {
        var path = PathFor(gameDirectory);
        await using (await LockFiles.AcquireAsync(path + ".lock", LockTimeout, cancellationToken))
        {
            var bytes = ReadShared(path);
            if (bytes != null && ParseRoot(bytes, path).Root == null) File.Move(path, CorruptPath(path));
            await LockFiles.WriteAtomicallyAsync(path, Rewrite(bytes, path, mutate), cancellationToken);
        }
    }

    /// <summary>Where an unreadable choices file is kept before the next write: lads-mod-state.json.corrupt-&lt;time&gt;.</summary>
    internal static string CorruptPath(string path) =>
        $"{path}.corrupt-{DateTime.UtcNow.ToString("yyyyMMdd-HHmmssfff", CultureInfo.InvariantCulture)}";

    /// <summary>The new content for a writer that commits the file itself (the installer, under the same lock): the
    /// <see cref="UpdateAsync"/> rules, where an unreadable file starts over and the caller keeps it at <see cref="CorruptPath"/>.</summary>
    internal static byte[] Rewrite(byte[]? bytes, string path, Action<JsonObject> mutate)
    {
        var root = (bytes == null ? null : ParseRoot(bytes, path).Root) ?? new JsonObject();
        root["schema"] ??= 1;
        root["mods"] ??= new JsonObject();
        mutate(root);
        return Encoding.UTF8.GetBytes(root.ToJsonString(Indented));
    }

    /// <summary>Records an explicit choice inside <see cref="UpdateAsync"/>, keeping unknown fields of the entry.</summary>
    public static void SetMod(JsonObject root, string id, bool enabled, string? projectId, string source = "launcher")
    {
        var all = root["mods"] as JsonObject ?? (JsonObject)(root["mods"] = new JsonObject())!;
        var entry = all[id] as JsonObject ?? (JsonObject)(all[id] = new JsonObject())!;
        entry["enabled"] = enabled;
        if (projectId != null) entry["projectId"] = projectId;
        entry["updatedAt"] = DateTime.UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'", CultureInfo.InvariantCulture);
        entry["source"] = source;
    }

    internal static byte[]? ReadShared(string path)
    {
        try
        {
            using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            using var copy = new MemoryStream();
            stream.CopyTo(copy);
            return copy.ToArray();
        }
        catch (FileNotFoundException) { return null; }
        catch (DirectoryNotFoundException) { return null; }
    }

    internal static ModPreferences Parse(byte[]? bytes, string path)
    {
        if (bytes == null) return new(new JsonObject(), null);
        var (root, error) = ParseRoot(bytes, path);
        return new(root?["mods"] as JsonObject ?? new JsonObject(), error);
    }

    private static (JsonObject? Root, string? Error) ParseRoot(byte[] bytes, string path)
    {
        var json = bytes.AsSpan();
        if (json.StartsWith(Encoding.UTF8.Preamble)) json = json[Encoding.UTF8.Preamble.Length..];
        string problem;
        try
        {
            var root = JsonNode.Parse(json);
            Materialize(root);
            if (root is JsonObject choices && choices["mods"] is null or JsonObject) return (choices, null);
            problem = "it is not a mod choice object";
        }
        // ArgumentException: a duplicated key, which System.Text.Json reports only when an object is first used.
        catch (Exception e) when (e is JsonException or ArgumentException) { problem = e.Message; }
        return (null, $"Saved mod choices in '{path}' are unreadable ({problem}). The current enabled/disabled files are kept; " +
            "the file is renamed to " + FileName + ".corrupt-<time> on your next change.");
    }

    // Touches every object and array so parse problems surface here, not later inside GetEnabled or a writer.
    internal static void Materialize(JsonNode? node)
    {
        if (node is JsonObject obj) foreach (var (_, child) in obj) Materialize(child);
        else if (node is JsonArray array) foreach (var child in array) Materialize(child);
    }

    private static bool? Enabled(JsonObject entry) =>
        entry["enabled"] is JsonValue value && value.TryGetValue<bool>(out var enabled) ? enabled : null;
}
