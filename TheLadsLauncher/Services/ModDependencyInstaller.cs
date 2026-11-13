using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>Downloads the newest compatible build of a Modrinth project into a mods folder; the jar is checked against
/// Modrinth's sha512 before it is put in place. Never overwrites a file.</summary>
public static class ModrinthJarInstaller
{
    private const string Api = "https://api.modrinth.com/v2/project/";

    /// <summary>The installed file name, or null (status says why) when Modrinth has no such project or build or the download failed.</summary>
    public static async Task<string?> InstallLatestAsync(HttpClient http, string project, string modsDirectory, string mcVersion, string loader,
        Action<string>? status, CancellationToken token)
    {
        try
        {
            var query = $"{Api}{Uri.EscapeDataString(project)}/version?game_versions={Uri.EscapeDataString($"[\"{mcVersion}\"]")}&loaders={Uri.EscapeDataString("[\"" + (loader == "quilt" ? "quilt" : "fabric") + "\"]")}";
            using var response = await http.GetAsync(query, token);
            if (response.StatusCode == System.Net.HttpStatusCode.NotFound) return null;
            response.EnsureSuccessStatusCode();
            using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync(token));
            // Newest first; prefer a release over a beta/alpha.
            var versions = document.RootElement.EnumerateArray().ToList();
            var version = versions.FirstOrDefault(v => v.GetProperty("version_type").GetString() == "release");
            if (version.ValueKind != JsonValueKind.Object) version = versions.FirstOrDefault();
            if (version.ValueKind != JsonValueKind.Object)
            {
                status?.Invoke($"Modrinth has no {project} for Minecraft {mcVersion}.");
                return null;
            }
            var files = version.GetProperty("files").EnumerateArray().ToList();
            var file = files.FirstOrDefault(f => f.TryGetProperty("primary", out var p) && p.ValueKind == JsonValueKind.True);
            if (file.ValueKind != JsonValueKind.Object) file = files.First();
            var name = file.GetProperty("filename").GetString() ?? project + ".jar";
            if (name.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0 || !name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)) name = project + ".jar";
            var bytes = await http.GetByteArrayAsync(file.GetProperty("url").GetString(), token);
            if (file.GetProperty("hashes").TryGetProperty("sha512", out var expected)
                && !string.Equals(Convert.ToHexString(SHA512.HashData(bytes)), expected.GetString(), StringComparison.OrdinalIgnoreCase))
            {
                status?.Invoke($"The {project} download did not match Modrinth's checksum; it was not installed.");
                return null;
            }
            Directory.CreateDirectory(modsDirectory);
            var target = Path.Combine(modsDirectory, name);
            if (File.Exists(target)) return name;
            var staged = Path.Combine(modsDirectory, $".{project}-{Guid.NewGuid():N}.tmp");
            try
            {
                await File.WriteAllBytesAsync(staged, bytes, token);
                File.Move(staged, target, overwrite: false);
            }
            finally { if (File.Exists(staged)) File.Delete(staged); }
            return name;
        }
        catch (Exception e) when (!token.IsCancellationRequested && e is HttpRequestException or JsonException or IOException
            or UnauthorizedAccessException or InvalidOperationException or KeyNotFoundException or TaskCanceledException)
        {
            status?.Invoke($"Could not install {project} ({e.Message}).");
            return null;
        }
    }
}

/// <summary>Installs the libraries the mods in a modpack's mods folder require and nothing provides, from Modrinth. A required id
/// is looked up as a Modrinth project with the same slug (Fabric API's modules map to fabric-api); ids with no such project are
/// left for the game's own error. A mod id is not a Modrinth name, so apart from Fabric API a guessed project is installed only when the
/// player confirms it. Fabric and Quilt only; Forge packs bring their libraries.</summary>
public static class ModDependencyInstaller
{
    private static readonly HashSet<string> Builtin = new(StringComparer.Ordinal)
        { "minecraft", "java", "fabricloader", "fabric-loader", "quilt_loader", "quilt_base", "mixinextras" };
    private const int Rounds = 3; // a library can need another library

    /// <summary>Required ids no enabled jar in <paramref name="modsDirectory"/> provides.</summary>
    public static IReadOnlyList<string> MissingIds(string modsDirectory)
    {
        if (!Directory.Exists(modsDirectory)) return Array.Empty<string>();
        var provided = new HashSet<string>(Builtin, StringComparer.Ordinal);
        var required = new HashSet<string>(StringComparer.Ordinal);
        foreach (var path in Directory.EnumerateFiles(modsDirectory, "*.jar"))
        {
            FabricModInfo? info;
            try { info = FabricModMetadata.ReadJar(path); }
            catch (Exception e) when (e is InvalidDataException or IOException or UnauthorizedAccessException) { continue; }
            if (info == null || info.IsServerOnly) continue;
            Collect(info);
        }
        return required.Where(id => !provided.Contains(id) && !FabricModuleOfFabricApi(id, provided)).OrderBy(id => id, StringComparer.Ordinal).ToList();

        void Collect(FabricModInfo mod)
        {
            provided.Add(mod.Id);
            foreach (var id in mod.Provides) provided.Add(id);
            foreach (var id in mod.Depends.Keys) required.Add(id);
            foreach (var child in mod.Children) Collect(child);
        }
    }

    // Fabric API's modules (fabric-resource-loader-v0 ...) are inside one jar; "fabric" is its umbrella id.
    private static bool FabricModuleOfFabricApi(string id, HashSet<string> provided) =>
        IsFabricApiId(id) && provided.Contains("fabric-api");

    private static bool IsFabricApiId(string id) => id is "fabric" or "fabric-api" || Regex.IsMatch(id, "^fabric-.+-v\\d+$");

    /// <summary>Installs what is missing (up to three rounds). Returns the installed file names; status reports each install and each id Modrinth could not supply.
    /// <paramref name="confirm"/> (id, slug) asks the player about a guessed project; without it only Fabric API is installed.</summary>
    public static async Task<IReadOnlyList<string>> EnsureAsync(HttpClient http, string modsDirectory, string mcVersion, string loader,
        Action<string>? status, CancellationToken token, Func<string, string, Task<bool>>? confirm = null)
    {
        var installed = new List<string>();
        loader = (loader ?? "").Trim().ToLowerInvariant();
        if (loader is not ("fabric" or "quilt")) return installed;
        var tried = new HashSet<string>(StringComparer.Ordinal);
        var slugs = new HashSet<string>(StringComparer.Ordinal);
        for (int round = 0; round < Rounds; round++)
        {
            var missing = MissingIds(modsDirectory).Where(id => tried.Add(id)).ToList();
            if (missing.Count == 0) break;
            foreach (var id in missing)
            {
                token.ThrowIfCancellationRequested();
                var slug = IsFabricApiId(id) ? "fabric-api" : id.Replace('_', '-');
                if (!slugs.Add(slug)) continue; // one Fabric API download covers every module id
                if (slug != "fabric-api" && (confirm == null || !await confirm(id, slug)))
                {
                    status?.Invoke($"The required mod '{id}' was not installed from Modrinth ('{slug}' is only a guess; add it yourself or confirm it when saving the modpack).");
                    continue;
                }
                status?.Invoke($"Installing {slug} (needed by a mod)...");
                var file = await ModrinthJarInstaller.InstallLatestAsync(http, slug, modsDirectory, mcVersion, loader, status, token);
                if (file != null) { installed.Add(file); status?.Invoke($"Installed {file}."); }
                else status?.Invoke($"No Modrinth download for the required mod '{id}'.");
            }
        }
        return installed;
    }
}
