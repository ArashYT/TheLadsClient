using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>A servers.dat the Servers tab edits. <see cref="LockFile"/>: held around each write (the shared list's, which LadsCore also takes).</summary>
public sealed record ServerListTarget(string Label, string File, string? LockFile);

/// <summary>Adds and removes servers in a server list without touching any other entry or field (atomic write, NBT round trip).</summary>
public static class ServerListService
{
    private static readonly TimeSpan LockTimeout = TimeSpan.FromSeconds(3);

    /// <summary>
    /// The shared list first: every Lads version profile reads the global .minecraft servers.dat (profiles never keep their own;
    /// see <see cref="SharedContentService"/>). Then each modpack instance the Modpacks tab lists (<see cref="Modpacks.List"/>).
    /// </summary>
    public static IReadOnlyList<ServerListTarget> Targets(SharedContentService shared, string launcherDataDirectory)
    {
        var targets = new List<ServerListTarget> { new("Global .minecraft (every Lads version)", shared.ServersFile, shared.ServersLockFile) };
        foreach (var instance in Modpacks.List(launcherDataDirectory))
            targets.Add(new ServerListTarget($"Modpack: {(instance.Name.Length > 0 ? instance.Name : instance.Id)} {instance.McVersion}".Trim(),
                Path.Combine(instance.GameDirectory, "servers.dat"), null));
        return targets;
    }

    /// <summary>The servers the game lists (hidden direct-connect entries left out). An unreadable file throws InvalidDataException.</summary>
    public static IReadOnlyList<ServerListEntry> Read(ServerListTarget target) =>
        ReadList(target.File).Entries.Where(e => !e.Hidden && e.Ip.Trim().Length > 0).ToList();

    /// <summary>False when the list already has this address (trimmed, case-insensitive, as the shared-list merge matches servers).</summary>
    public static Task<bool> AddAsync(ServerListTarget target, string name, string address) => EditAsync(target, list =>
    {
        if (list.Entries.Any(e => !e.Hidden && Key(e.Ip) == Key(address))) return false;
        list.Add(new NbtCompound { ["name"] = new NbtString(name), ["ip"] = new NbtString(address.Trim()) });
        return true;
    });

    /// <summary>Removes the listed server with this address. False when there is none.</summary>
    public static Task<bool> RemoveAsync(ServerListTarget target, string address) => EditAsync(target, list =>
        list.Entries.FirstOrDefault(e => !e.Hidden && Key(e.Ip) == Key(address)) is { } entry && list.Remove(entry.Raw));

    /// <summary>
    /// Swaps the listed server with this address and the next listed one above (<paramref name="offset"/> -1) or below (+1), as
    /// the game's own arrows do; hidden direct-connect entries stay where they are. False at either end or when it is not listed.
    /// </summary>
    public static Task<bool> MoveAsync(ServerListTarget target, string address, int offset) => EditAsync(target, list =>
    {
        if (list.Root["servers"] is not NbtList servers) return false;
        var listed = list.Entries.Where(e => !e.Hidden && e.Ip.Trim().Length > 0).ToList();
        var at = listed.FindIndex(e => Key(e.Ip) == Key(address));
        var to = at + Math.Sign(offset);
        if (at < 0 || offset == 0 || to < 0 || to >= listed.Count) return false;
        int a = servers.Items.IndexOf(listed[at].Raw), b = servers.Items.IndexOf(listed[to].Raw);
        (servers.Items[a], servers.Items[b]) = (servers.Items[b], servers.Items[a]);
        return true;
    });

    /// <summary>The PNG the game cached for this server when it last pinged it (servers.dat "icon", base64), or null.</summary>
    public static byte[]? Icon(ServerListEntry entry)
    {
        if (entry.Raw["icon"] is not NbtString { Value.Length: > 0 } icon) return null;
        var data = icon.Value;
        var comma = data.IndexOf("base64,", StringComparison.Ordinal); // never written by the game, but cheap to accept
        try { return Convert.FromBase64String(comma < 0 ? data : data[(comma + 7)..]); }
        catch (FormatException) { return null; }
    }

    private static string Key(string address) => address.Trim().ToLowerInvariant();

    private static async Task<bool> EditAsync(ServerListTarget target, Func<ServerListFile, bool> edit)
    {
        using var held = target.LockFile == null ? null : await LockFiles.AcquireAsync(target.LockFile, LockTimeout);
        var list = ReadList(target.File); // unreadable: throws, and the file is left as it is
        if (!edit(list)) return false;
        await list.WriteAsync(target.File);
        return true;
    }

    /// <summary>Minecraft saves by renaming servers.dat to servers.dat_old, then moving the new file in: only _old left means a game
    /// closed between the two, and _old is the list (as SharedContentService reads it).</summary>
    private static ServerListFile ReadList(string file) =>
        System.IO.File.Exists(file) ? ServerListFile.Read(file)
        : System.IO.File.Exists(file + "_old") ? ServerListFile.Read(file + "_old")
        : ServerListFile.CreateEmpty();
}
