using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using TheLadsLauncher.Services;

namespace TheLadsLauncher;

/// <summary>
/// Visible server names + addresses from Minecraft's servers.dat (uncompressed NBT; gzip also accepted).
/// Reads never block Minecraft's atomic replace of the file. An unreadable file throws InvalidDataException.
/// </summary>
public static class MinecraftServerListReader
{
    public record ServerEntry(string Name, string Ip);

    /// <summary>The shared server list every profile uses (<see cref="SharedContentService.ServersFile"/>).</summary>
    public static IReadOnlyList<ServerEntry> Read() => ReadFile(SharedContentService.Instance.ServersFile);

    /// <summary>A game folder's own servers.dat. Profiles share <see cref="Read()"/>; this remains for callers not yet moved to it.</summary>
    public static IReadOnlyList<ServerEntry> Read(string instancePath) => ReadFile(Path.Combine(instancePath, "servers.dat"));

    public static IReadOnlyList<ServerEntry> ReadFile(string path)
    {
        if (!File.Exists(path)) return Array.Empty<ServerEntry>();
        return ServerListFile.Read(path).Entries
            .Where(e => !e.Hidden && !string.IsNullOrWhiteSpace(e.Ip))
            .Select(e => new ServerEntry(string.IsNullOrWhiteSpace(e.Name) ? e.Ip : e.Name, e.Ip))
            .ToList();
    }
}
