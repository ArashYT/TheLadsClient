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
    public const string OpenGlRequired = "Requires OpenGL. Choose OpenGL in launcher Settings → Graphics; your mod choice is preserved.";
    public static bool SupportsVulkan(string version) => version is "26.2" or "26.3";
    public static string Normalize(string? value) => value == OpenGl ? OpenGl : Vulkan;
    public sealed record State(string Preference, string[] SuspendedMods);

    public static State? ReadState(string game)
    {
        try { return JsonSerializer.Deserialize<State>(File.ReadAllText(Path.Combine(game, StateFile))); }
        catch (Exception e) when (e is IOException or JsonException or UnauthorizedAccessException) { return null; }
    }

    public static IReadOnlySet<string> Suspended(string game) =>
        (ReadState(game)?.SuspendedMods ?? Array.Empty<string>()).ToHashSet(StringComparer.Ordinal);

    public static async Task<IReadOnlySet<string>> PrepareAsync(string game, string version, string? preference,
        Action<string>? report = null, CancellationToken token = default)
    {
        token.ThrowIfCancellationRequested();
        game = Path.GetFullPath(game);
        if (RunningGameMarker.IsRunning(game)) throw new IOException("Close this profile's game before changing its renderer.");
        Directory.CreateDirectory(game);
        var choice = SupportsVulkan(version) ? Normalize(preference) : OpenGl;
        var state = ReadState(game);
        var optionsPath = Path.Combine(game, "options.txt");
        var statePath = Path.Combine(game, StateFile);
        if (SafeFileOps.IsLink(optionsPath) || SafeFileOps.IsLink(statePath))
            throw new IOException("Renderer settings must be regular files in the profile.");
        var lines = File.Exists(optionsPath) ? (await File.ReadAllLinesAsync(optionsPath, token)).ToList() : new List<string>();
        // Shared options can come back from a legacy version without this field. Restore the
        // launcher choice only when absent; explicit DEFAULT/OpenGL remains Minecraft's fallback.
        bool hasSavedBackend = lines.Any(line => line.StartsWith("preferredGraphicsBackend:", StringComparison.Ordinal));
        if (SupportsVulkan(version) && (state == null || state.Preference != choice || !hasSavedBackend))
        {
            lines.RemoveAll(line => line.StartsWith("preferredGraphicsBackend:", StringComparison.Ordinal));
            lines.Add("preferredGraphicsBackend:\"" + (choice == Vulkan ? "vulkan" : "opengl") + "\"");
            await LockFiles.WriteAtomicallyAsync(optionsPath, Encoding.UTF8.GetBytes(string.Join(Environment.NewLine, lines) + Environment.NewLine), token);
        }
        // Do not rewrite this every launch: Minecraft changes the saved value after a driver/startup failure.
        var backend = lines.LastOrDefault(line => line.StartsWith("preferredGraphicsBackend:", StringComparison.Ordinal));
        var savedApi = backend == null ? null : backend[(backend.IndexOf(':') + 1)..].Trim();
        // An in-game API change takes effect on restart even when the launcher choice is unchanged.
        bool useVulkan = SupportsVulkan(version) && savedApi == "\"vulkan\"";
        var suspended = new HashSet<string>(StringComparer.Ordinal);
        if (useVulkan)
        {
            suspended.UnionWith(new[] { "iris", "euphoria_patcher", "irisflwcompat" });
            var mods = Path.Combine(game, "mods");
            var installed = Directory.Exists(mods) ? Directory.EnumerateFiles(mods)
                .Where(p => p.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || p.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase))
                .Select(p => FabricModMetadata.ReadJar(p, token: token)).Where(m => m != null).ToArray() : Array.Empty<FabricModInfo?>();
            bool changed;
            do
            {
                changed = false;
                foreach (var mod in installed)
                    if (mod!.Depends.Keys.Any(suspended.Contains)) changed |= suspended.Add(mod.Id);
            } while (changed);
        }
        await LockFiles.WriteAtomicallyAsync(statePath, JsonSerializer.SerializeToUtf8Bytes(new State(choice, suspended.OrderBy(x => x).ToArray())), token);
        report?.Invoke(useVulkan ? "Graphics: Vulkan preferred; OpenGL is the fallback. Iris shader mods are suspended."
            : SupportsVulkan(version) && choice == Vulkan ? "Graphics: using Minecraft's saved OpenGL fallback. Choose Vulkan in the in-game Graphics API setting and restart to retry."
            : "Graphics: OpenGL (shader-compatible). ");
        return suspended;
    }
}
