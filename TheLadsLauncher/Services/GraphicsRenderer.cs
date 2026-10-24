using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
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
    public const string ShadersOff = "Shaders are off, so this is suspended. Turn them on in the game's Video settings (Shader packs...); your mod choice is preserved.";
    public static string BlockedReason(string version) => SupportsVulkan(version) ? OpenGlRequired : ShadersOff;
    public const string VulkanCrashHint = "It was running on Vulkan: if this keeps happening, choose OpenGL in Settings → Graphics.";
    /// <summary>The in-game shader toggle's renderer for this game folder ({"renderer":"OpenGL"|"Vulkan"}), written by the game and
    /// kept: it wins over the launcher setting at every launch of that folder.</summary>
    public const string ChoiceFile = "lads-renderer-choice.json";
    /// <summary>Written by the game right before it quits to switch renderer; taken by <see cref="TakeRelaunch"/> once it has exited.</summary>
    public const string RelaunchFile = "lads-relaunch.json";
    private const string Key = "preferredGraphicsBackend:", Disabled = ".disabled";
    public static bool SupportsVulkan(string version) => version is "26.2" or "26.3";
    /// <summary>Iris is only loaded while shaders are on. 26.x: shaders on = OpenGL, off = Vulkan. 1.20.1 has no Vulkan (OpenGL always),
    /// so there the same choice only decides whether Iris is suspended.</summary>
    public static bool SupportsShaderToggle(string version) => SupportsVulkan(version) || version == "1.20.1";
    public static string Normalize(string? value) => value == OpenGl ? OpenGl : Vulkan;
    /// <summary>This profile's last launch: whether it used Vulkan and the mods suspended for it.</summary>
    public sealed record State(bool Vulkan, string[] SuspendedMods);
    /// <summary>One options.txt: the launcher choice last written into it and the last backend value saved in it.</summary>
    public sealed record OptionsState(string? Applied, string? Saved);
    /// <summary>The game's relaunch request: start it again in saves/<paramref name="World"/>, on <paramref name="Server"/>
    /// ("host[:port]"), or at the title screen (both null).</summary>
    public sealed record Relaunch(string Reason, string? World, string? Server);
    private sealed record Choice([property: JsonPropertyName("renderer")] string? Renderer);
    private sealed record Plan(List<string> Lines, string? Value, bool WriteOptions, bool Vulkan, bool Suspend, OptionsState? Old, OptionsState New);
    private static readonly JsonSerializerOptions Relaxed = new() { PropertyNameCaseInsensitive = true };

    public static State? ReadState(string game) => Read<State>(Path.Combine(game, StateFile));

    /// <summary>The renderer a launch of <paramref name="game"/> uses: the in-game choice (<see cref="ChoiceFile"/>) when it is
    /// valid, else <paramref name="launcherSetting"/>.</summary>
    public static string Preference(string game, string? launcherSetting) =>
        Read<Choice>(Path.Combine(game, ChoiceFile))?.Renderer switch
        {
            { } r when r.Equals(OpenGl, StringComparison.OrdinalIgnoreCase) => OpenGl,
            { } r when r.Equals(Vulkan, StringComparison.OrdinalIgnoreCase) => Vulkan,
            _ => Normalize(launcherSetting)
        };

    /// <summary>The game's <see cref="RelaunchFile"/>, read and deleted; null when there is none or it is refused (<paramref name="log"/>
    /// says why). A world must be one save folder name with a level.dat (else the title screen), a server "host[:port]".</summary>
    public static Relaunch? TakeRelaunch(string game, Action<string> log)
    {
        string file = Path.Combine(game, RelaunchFile);
        if (!File.Exists(file)) return null;
        Relaunch? request;
        try { request = JsonSerializer.Deserialize<Relaunch>(File.ReadAllText(file), Relaxed); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException)
        {
            log($"Could not read the game's relaunch request: {e.Message}");
            request = null;
        }
        try { File.Delete(file); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { } // read once already; the next launch deletes it
        if (request == null) return null;
        string? world = request.World, server = request.Server?.Trim();
        var port = server == null ? null : Regex.Match(server, @"^[A-Za-z0-9][A-Za-z0-9.-]{0,252}(?::(\d{1,5}))?$");
        if (request.Reason is not ("shaders-on" or "shaders-off") || world != null && server != null
            || world != null && (!SafeFileOps.IsPlainFileName(world) || world.Contains(".."))
            || port != null && (!port.Success || port.Groups[1].Success && int.Parse(port.Groups[1].Value) is < 1 or > 65535))
        {
            log($"Relaunch request refused (reason '{request.Reason}', world '{world}', server '{server}').");
            return null;
        }
        if (world != null && !File.Exists(Path.Combine(game, "saves", world, "level.dat")))
        {
            log($"The world '{world}' to return to was not found; the game starts at the title screen.");
            world = null;
        }
        log($"Relaunching for {request.Reason}" + (world != null ? $" into the world '{world}'." : server != null ? $" onto {server}." : " to the title screen."));
        return request with { World = world, Server = server };
    }

    /// <summary>1.20.1 had no shader switch (Iris was simply on). On the first launch under it (no choice and no renderer state yet),
    /// a game folder whose Iris config has a shader pack switched on keeps shaders on: the choice is written once. Every other folder
    /// starts with shaders off. True when it wrote the choice.</summary>
    public static async Task<bool> MigrateShadersAsync(string game, string version, CancellationToken token = default)
    {
        if (SupportsVulkan(version) || !SupportsShaderToggle(version)) return false;
        string choice = Path.Combine(game, ChoiceFile);
        if (File.Exists(choice) || File.Exists(Path.Combine(game, StateFile)) || !ShaderPackOn(Path.Combine(game, "config", "iris.properties"))) return false;
        if (SafeFileOps.IsLink(choice)) return false;
        await LockFiles.WriteAtomicallyAsync(choice, JsonSerializer.SerializeToUtf8Bytes(new Choice(OpenGl)), token);
        return true;
    }

    /// <summary>Iris's own rule (IrisConfig.load): a pack is in use when "shaderPack" names one (not empty, not "(internal)") and
    /// "enableShaders" is anything but "false" (missing = on).</summary>
    private static bool ShaderPackOn(string properties)
    {
        string? pack = null, enabled = null;
        try
        {
            foreach (var raw in File.ReadLines(properties))
            {
                var line = raw.TrimStart();
                int at = line.IndexOfAny(new[] { '=', ':' });
                if (line.Length == 0 || line[0] is '#' or '!' || at < 0) continue;
                string key = line[..at].Trim(), value = line[(at + 1)..].Trim();
                if (key == "shaderPack") pack = value; else if (key == "enableShaders") enabled = value;
            }
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { return false; }
        return !string.IsNullOrEmpty(pack) && pack != "(internal)" && enabled != "false";
    }

    public static IReadOnlySet<string> Suspended(string game) =>
        (ReadState(game)?.SuspendedMods ?? Array.Empty<string>()).ToHashSet(StringComparer.Ordinal);

    /// <summary>What the next launch with <paramref name="preference"/> suspends; writes nothing (the Mods page).</summary>
    public static IReadOnlySet<string> Suspended(string game, string version, string? preference, IEnumerable<FabricModInfo?> installed)
    {
        try { return Decide(Path.GetFullPath(game), version, preference).Suspend ? Dependents(installed) : new HashSet<string>(StringComparer.Ordinal); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { return Suspended(game); }
    }

    /// <param name="renameJars">Modpacks: the suspended jars are renamed here (<c>.jar</c> to <c>.jar.disabled</c> and back). Profiles
    /// leave that to ClientModInstaller, which keeps the choices and also holds back pack mods it has not downloaded yet.</param>
    public static async Task<IReadOnlySet<string>> PrepareAsync(string game, string version, string? preference,
        Action<string>? report = null, CancellationToken token = default, bool renameJars = false)
    {
        token.ThrowIfCancellationRequested();
        game = Path.GetFullPath(game);
        Directory.CreateDirectory(game);
        File.Delete(Path.Combine(game, RelaunchFile)); // one left over from an earlier run must not restart this one at its exit
        if (await MigrateShadersAsync(game, version, token)) preference = OpenGl;
        var plan = Decide(game, version, preference);
        var mods = Path.Combine(game, "mods");
        var jars = Directory.Exists(mods)
            ? Directory.EnumerateFiles(mods).Where(p => p.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || p.EndsWith(".jar" + Disabled, StringComparison.OrdinalIgnoreCase))
                .Select(p => (Path: p, Info: FabricModMetadata.ReadJar(p, token: token))).ToArray() : Array.Empty<(string Path, FabricModInfo? Info)>();
        var suspended = !plan.Suspend ? new HashSet<string>(StringComparer.Ordinal) : Dependents(jars.Select(j => j.Info));
        var state = ReadState(game);
        var previous = state?.SuspendedMods ?? Array.Empty<string>();
        // Renamed here: only mods that are there and load, or that the launcher suspended itself. A jar you disabled stays disabled.
        if (renameJars)
            suspended.IntersectWith(jars.Where(j => j.Info != null && (!j.Path.EndsWith(Disabled, StringComparison.OrdinalIgnoreCase) || previous.Contains(j.Info.Id)))
                .Select(j => j.Info!.Id));
        bool modsChange = !suspended.SetEquals(previous);
        // Always written once: the game takes the file for "the launcher started me" (it restarts the game for the shader toggle).
        bool writeState = modsChange || (state != null && state.Vulkan != plan.Vulkan) || (state == null && SupportsShaderToggle(version));
        bool writeTracking = plan.New != (plan.Old ?? new OptionsState(null, null));
        // Another copy of this profile may be running (Allow launching multiple copies): only a change to what it uses is refused.
        if ((plan.WriteOptions || modsChange) && RunningGameMarker.IsRunning(game))
            throw new IOException("Close this profile's game before changing its renderer.");
        var optionsPath = Path.Combine(game, "options.txt");
        var statePath = Path.Combine(game, StateFile);
        var trackingPath = Path.Combine(game, OptionsStateFile);
        var choicePath = Path.Combine(game, ChoiceFile);
        bool writeChoice = SupportsVulkan(version) && plan.Vulkan && File.Exists(choicePath)
            && !string.Equals(Read<Choice>(choicePath)?.Renderer, Vulkan, StringComparison.OrdinalIgnoreCase);
        if (plan.WriteOptions && SafeFileOps.IsLink(optionsPath) || writeState && SafeFileOps.IsLink(statePath)
            || writeTracking && SafeFileOps.IsLink(trackingPath) || writeChoice && SafeFileOps.IsLink(choicePath))
            throw new IOException("Renderer settings must be regular files in the profile.");
        if (plan.WriteOptions)
        {
            plan.Lines.RemoveAll(line => line.StartsWith(Key, StringComparison.Ordinal));
            plan.Lines.Add(Key + plan.Value);
            await LockFiles.WriteAtomicallyAsync(optionsPath, Encoding.UTF8.GetBytes(string.Join(Environment.NewLine, plan.Lines) + Environment.NewLine), token);
        }
        if (writeChoice)
            await LockFiles.WriteAtomicallyAsync(choicePath, JsonSerializer.SerializeToUtf8Bytes(new Choice(Vulkan)), token);
        // After options.txt: a failed options write must leave the choice unapplied, so the next launch writes it again.
        if (writeTracking) await LockFiles.WriteAtomicallyAsync(trackingPath, JsonSerializer.SerializeToUtf8Bytes(plan.New), token);
        // Before the state: after a failed rename the next launch still knows which jars to bring back.
        if (renameJars) RenameJars(jars, suspended, previous.Except(suspended).ToHashSet(StringComparer.Ordinal));
        if (writeState)
            await LockFiles.WriteAtomicallyAsync(statePath, JsonSerializer.SerializeToUtf8Bytes(new State(plan.Vulkan, suspended.OrderBy(x => x).ToArray())), token);
        report?.Invoke(plan.Vulkan ? "Graphics: Vulkan preferred; OpenGL is the fallback. Iris shader mods are suspended."
            : plan.Suspend ? "Graphics: OpenGL; shaders are off, so Iris is suspended. Turn them on in the game's Video settings."
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
        bool vulkan = native && value == "\"vulkan\"";
        return new(lines, value, native && value != saved, vulkan, native ? vulkan : SupportsShaderToggle(version) && choice == Vulkan, old,
            new OptionsState(native ? (vulkan ? Vulkan : choice) : old?.Applied, value ?? old?.Saved));
    }

    // <mod>.jar -> .jar.disabled for the suspended ids and back for the restored ones. When both names exist (a pack update next
    // to a suspended copy) the newer file is kept under the new name.
    private static void RenameJars(IEnumerable<(string Path, FabricModInfo? Info)> jars, IReadOnlySet<string> suspend, IReadOnlySet<string> restore)
    {
        foreach (var (path, info) in jars)
        {
            bool disabled = path.EndsWith(Disabled, StringComparison.OrdinalIgnoreCase);
            if (info == null || !(disabled ? restore : suspend).Contains(info.Id) || !File.Exists(path)) continue; // gone: moved over already
            var target = disabled ? path[..^Disabled.Length] : path + Disabled;
            if (File.Exists(target) && File.GetLastWriteTimeUtc(target) >= File.GetLastWriteTimeUtc(path)) File.Delete(path);
            else File.Move(path, target, overwrite: true);
        }
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
