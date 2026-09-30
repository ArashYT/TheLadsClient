using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>Native Minecraft renderer preference. Vanilla retains ownership of crash fallback.</summary>
public static class GraphicsRenderer
{
    public const string Vulkan = "Vulkan", OpenGl = "OpenGL";
    public const string StateFile = "lads-renderer.json";
    /// <summary>Kept next to options.txt and synced with it like options.txt (ProfileService), so every profile sharing the
    /// game settings also shares what the launcher last wrote into them.</summary>
    public const string OptionsStateFile = "lads-renderer-options.json";
    public const string OpenGlRequired = "Requires OpenGL. Choose OpenGL in launcher Settings → Graphics; your mod choice is preserved.";
    public const string VulkanCrashHint = "It was running on Vulkan: if this keeps happening, choose OpenGL in Settings → Graphics.";
    private const string Key = "preferredGraphicsBackend:";
    public static bool SupportsVulkan(string version) => version is "26.2" or "26.3";
    public static string Normalize(string? value) => value == OpenGl ? OpenGl : Vulkan;
    /// <summary>This profile's last launch: whether it used Vulkan and the mods suspended for it.</summary>
    public sealed record State(bool Vulkan, string[] SuspendedMods);
    /// <summary>One options.txt: the launcher choice last written into it and the last backend value saved in it.</summary>
    public sealed record OptionsState(string? Applied, string? Saved);
    private sealed record Plan(List<string> Lines, string? Value, bool WriteOptions, bool Vulkan, OptionsState? Old, OptionsState New);

    public static State? ReadState(string game) => Read<State>(Path.Combine(game, StateFile));

    public static IReadOnlySet<string> Suspended(string game) =>
        (ReadState(game)?.SuspendedMods ?? Array.Empty<string>()).ToHashSet(StringComparer.Ordinal);

    /// <summary>What the next launch with <paramref name="preference"/> suspends; writes nothing (the Mods page).</summary>
    public static IReadOnlySet<string> Suspended(string game, string version, string? preference, IEnumerable<FabricModInfo?> installed)
    {
        try { return Decide(Path.GetFullPath(game), version, preference).Vulkan ? Dependents(installed) : new HashSet<string>(StringComparer.Ordinal); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { return Suspended(game); }
    }

    public static async Task<IReadOnlySet<string>> PrepareAsync(string game, string version, string? preference,
        Action<string>? report = null, CancellationToken token = default)
    {
        token.ThrowIfCancellationRequested();
        game = Path.GetFullPath(game);
        Directory.CreateDirectory(game);
        var plan = Decide(game, version, preference);
        var mods = Path.Combine(game, "mods");
        var suspended = !plan.Vulkan ? new HashSet<string>(StringComparer.Ordinal) : Dependents(Directory.Exists(mods)
            ? Directory.EnumerateFiles(mods).Where(p => p.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || p.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase))
                .Select(p => FabricModMetadata.ReadJar(p, token: token)).ToArray() : Array.Empty<FabricModInfo?>());
        var state = ReadState(game);
        bool modsChange = !suspended.SetEquals(state?.SuspendedMods ?? Array.Empty<string>());
        bool writeState = modsChange || (state != null && state.Vulkan != plan.Vulkan);
        bool writeTracking = plan.New != (plan.Old ?? new OptionsState(null, null));
        // Another copy of this profile may be running (Allow launching multiple copies): only a change to what it uses is refused.
        if ((plan.WriteOptions || modsChange) && RunningGameMarker.IsRunning(game))
            throw new IOException("Close this profile's game before changing its renderer.");
        var optionsPath = Path.Combine(game, "options.txt");
        var statePath = Path.Combine(game, StateFile);
        var trackingPath = Path.Combine(game, OptionsStateFile);
        if (plan.WriteOptions && SafeFileOps.IsLink(optionsPath) || writeState && SafeFileOps.IsLink(statePath)
            || writeTracking && SafeFileOps.IsLink(trackingPath))
            throw new IOException("Renderer settings must be regular files in the profile.");
        if (plan.WriteOptions)
        {
            plan.Lines.RemoveAll(line => line.StartsWith(Key, StringComparison.Ordinal));
            plan.Lines.Add(Key + plan.Value);
            await LockFiles.WriteAtomicallyAsync(optionsPath, Encoding.UTF8.GetBytes(string.Join(Environment.NewLine, plan.Lines) + Environment.NewLine), token);
        }
        // After options.txt: a failed options write must leave the choice unapplied, so the next launch writes it again.
        if (writeTracking) await LockFiles.WriteAtomicallyAsync(trackingPath, JsonSerializer.SerializeToUtf8Bytes(plan.New), token);
        if (writeState)
            await LockFiles.WriteAtomicallyAsync(statePath, JsonSerializer.SerializeToUtf8Bytes(new State(plan.Vulkan, suspended.OrderBy(x => x).ToArray())), token);
        report?.Invoke(plan.Vulkan ? "Graphics: Vulkan preferred; OpenGL is the fallback. Iris shader mods are suspended."
            : SupportsVulkan(version) && Normalize(preference) == Vulkan ? "Graphics: using Minecraft's saved OpenGL fallback. Choose Vulkan in the in-game Graphics API setting and restart to retry."
            : "Graphics: OpenGL (shader-compatible). ");
        return suspended;
    }

    // options.txt is shared between profiles of every version. The launcher choice is written once after it changes;
    // otherwise Minecraft's own value stays (its crash fallback, an in-game change). A 1.21.x save drops the 26.x-only key:
    // then the value the game last saved comes back, which 1.21.x launches record while the key is still there.
    private static Plan Decide(string game, string version, string? preference)
    {
        var options = Path.Combine(game, "options.txt");
        var lines = File.Exists(options) ? File.ReadAllLines(options).ToList() : new List<string>();
        var saved = lines.LastOrDefault(line => line.StartsWith(Key, StringComparison.Ordinal))?[Key.Length..].Trim();
        var old = Read<OptionsState>(Path.Combine(game, OptionsStateFile));
        var choice = Normalize(preference);
        var launcher = choice == Vulkan ? "\"vulkan\"" : "\"opengl\"";
        bool native = SupportsVulkan(version);
        var value = !native ? saved : old?.Applied != choice ? launcher : saved ?? old?.Saved ?? launcher;
        return new(lines, value, native && value != saved, native && value == "\"vulkan\"", old,
            new OptionsState(native ? choice : old?.Applied, value ?? old?.Saved));
    }

    private static HashSet<string> Dependents(IEnumerable<FabricModInfo?> installed)
    {
        var mods = installed.OfType<FabricModInfo>().ToList();
        var suspended = new HashSet<string>(new[] { "iris", "euphoria_patcher", "irisflwcompat" }, StringComparer.Ordinal);
        bool changed;
        do
        {
            changed = false;
            foreach (var mod in mods)
                if (mod.Depends.Keys.Any(suspended.Contains)) changed |= suspended.Add(mod.Id);
        } while (changed);
        return suspended;
    }

    private static T? Read<T>(string path) where T : class
    {
        try { return JsonSerializer.Deserialize<T>(File.ReadAllText(path)); }
        catch (Exception e) when (e is IOException or JsonException or UnauthorizedAccessException) { return null; }
    }
}
