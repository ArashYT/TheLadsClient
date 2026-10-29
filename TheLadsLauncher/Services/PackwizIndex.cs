using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>
/// What a Packwiz pack would write: its pack.toml names an index file, and every index entry (a file, or a .pw.toml metafile
/// whose download lands in the same folder) is a path in the game folder. Checked before packwiz-installer runs, because it
/// writes and deletes through the shared-folder links into the worlds and packs every version uses.
/// </summary>
public static class PackwizIndex
{
    // ponytail: the TOML subset packwiz writes (one "key = string" per line); a real TOML parser if packs ever use more.
    private static readonly Regex Table = new(@"^\s*\[\[?\s*([^\]]+?)\s*\]\]?\s*(?:#.*)?$");
    private static readonly Regex FileKey = new(@"^\s*file\s*=\s*(?:""((?:[^""\\]|\\.)*)""|'([^']*)')");

    /// <summary>The index entries that land in one of <paramref name="folders"/> (e.g. resourcepacks/Pack.zip), in index order.</summary>
    public static Task<IReadOnlyList<string>> FilesInFoldersAsync(string packUrl, IReadOnlyCollection<string> folders, HttpClient http,
        CancellationToken cancellationToken = default) => MatchingFilesAsync(packUrl, http, file => InFolder(file, folders), cancellationToken);

    /// <summary>The index entries that would write one of Lads' own state files (<see cref="Mrpack.IsLadsStateFile"/>: server_bypasses.json,
    /// thelads_config.json, ...), in index order. A metafile at the top of the game folder counts too: the file name it downloads is its
    /// own to choose, and could be one of those.</summary>
    public static Task<IReadOnlyList<string>> LadsStateFilesAsync(string packUrl, HttpClient http, CancellationToken cancellationToken = default) =>
        MatchingFilesAsync(packUrl, http, file => Mrpack.IsLadsStateFile(file) || IsTargetMetafile(file), cancellationToken);

    private static async Task<IReadOnlyList<string>> MatchingFilesAsync(string packUrl, HttpClient http, Func<string, bool> matches,
        CancellationToken cancellationToken)
    {
        var pack = new Uri(packUrl, UriKind.Absolute);
        var indexName = Files(await ReadAsync(pack, http, cancellationToken), "index").FirstOrDefault()
            ?? throw new InvalidDataException($"'{packUrl}' has no [index] file entry, so it is not a Packwiz pack.toml.");
        var index = new Uri(pack, indexName);
        // Entries are relative to the index; packs keep it next to pack.toml, so both readings are checked.
        var indexFolder = Path.GetDirectoryName(indexName.Replace('\\', '/')) ?? "";
        return Files(await ReadAsync(index, http, cancellationToken), "files")
            .Where(file => matches(file) || matches(Path.Combine(indexFolder, file))).ToList();
    }

    private static bool IsTargetMetafile(string relative)
    {
        var parts = relative.Replace('\\', '/').Split('/', StringSplitOptions.RemoveEmptyEntries).Where(p => p != ".").ToList();
        if (parts.Count == 0 || !parts[^1].EndsWith(".pw.toml", StringComparison.OrdinalIgnoreCase)) return false;
        return parts.Count == 1 || (parts.Count == 2 && parts[0].Equals("configureddefaults", StringComparison.OrdinalIgnoreCase));
    }

    private static bool InFolder(string relative, IReadOnlyCollection<string> folders)
    {
        var parts = relative.Replace('\\', '/').Split('/', StringSplitOptions.RemoveEmptyEntries).Where(p => p != ".").ToList();
        return parts.Count > 1 && folders.Contains(parts[0], StringComparer.OrdinalIgnoreCase);
    }

    // The "file" values of the tables (or arrays of tables) named <paramref name="table"/>.
    private static IEnumerable<string> Files(string toml, string table)
    {
        string? current = null;
        foreach (var line in toml.Split('\n'))
        {
            if (Table.Match(line) is { Success: true } header) current = header.Groups[1].Value;
            else if (current == table && FileKey.Match(line) is { Success: true } key)
                yield return key.Groups[1].Success ? Regex.Unescape(key.Groups[1].Value) : key.Groups[2].Value;
        }
    }

    private static async Task<string> ReadAsync(Uri uri, HttpClient http, CancellationToken cancellationToken) =>
        uri.IsFile ? await File.ReadAllTextAsync(uri.LocalPath, cancellationToken) : await http.GetStringAsync(uri, cancellationToken);
}
