using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>Downloads Fabric API from Modrinth into a modpack's mods folder, for the Lads Core which needs it.
/// Never touches an instance that already has one; the jar is checked against Modrinth's sha512 before it is put in place.</summary>
public static class FabricApiInstaller
{
    private const string Api = "https://api.modrinth.com/v2/project/fabric-api/version";

    /// <summary>True when Fabric API is present or was installed; false (status says why) when offline or Modrinth has none for this version.</summary>
    public static async Task<bool> EnsureAsync(HttpClient http, string modsDirectory, string mcVersion, string loader,
        Action<string>? status, CancellationToken token)
    {
        if (ModpackCoreSync.HasFabricApi(modsDirectory)) return true;
        try
        {
            status?.Invoke("Installing Fabric API (needed by The Lads Core)...");
            var query = $"{Api}?game_versions={Uri.EscapeDataString($"[\"{mcVersion}\"]")}&loaders={Uri.EscapeDataString("[\"" + (loader == "quilt" ? "quilt" : "fabric") + "\"]")}";
            using var document = JsonDocument.Parse(await http.GetStringAsync(query, token));
            // Newest first; prefer a release over a beta/alpha.
            var versions = document.RootElement.EnumerateArray().ToList();
            var version = versions.FirstOrDefault(v => v.GetProperty("version_type").GetString() == "release");
            if (version.ValueKind != JsonValueKind.Object) version = versions.FirstOrDefault();
            if (version.ValueKind != JsonValueKind.Object)
            {
                status?.Invoke($"Modrinth has no Fabric API for Minecraft {mcVersion}.");
                return false;
            }
            var files = version.GetProperty("files").EnumerateArray().ToList();
            var file = files.FirstOrDefault(f => f.TryGetProperty("primary", out var p) && p.ValueKind == JsonValueKind.True);
            if (file.ValueKind != JsonValueKind.Object) file = files.First();
            var name = file.GetProperty("filename").GetString() ?? "fabric-api.jar";
            if (name.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0 || !name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)) name = "fabric-api.jar";
            var bytes = await http.GetByteArrayAsync(file.GetProperty("url").GetString(), token);
            if (file.GetProperty("hashes").TryGetProperty("sha512", out var expected)
                && !string.Equals(Convert.ToHexString(SHA512.HashData(bytes)), expected.GetString(), StringComparison.OrdinalIgnoreCase))
            {
                status?.Invoke("The Fabric API download did not match Modrinth's checksum; it was not installed.");
                return false;
            }
            Directory.CreateDirectory(modsDirectory);
            var staged = Path.Combine(modsDirectory, $".fabric-api-{Guid.NewGuid():N}.tmp");
            try
            {
                await File.WriteAllBytesAsync(staged, bytes, token);
                File.Move(staged, Path.Combine(modsDirectory, name), overwrite: true);
            }
            finally { if (File.Exists(staged)) File.Delete(staged); }
            status?.Invoke($"Installed {name}.");
            return true;
        }
        catch (Exception e) when (!token.IsCancellationRequested && e is HttpRequestException or JsonException or IOException
            or UnauthorizedAccessException or InvalidOperationException or KeyNotFoundException or TaskCanceledException)
        {
            status?.Invoke($"Could not install Fabric API ({e.Message}).");
            return false;
        }
    }
}
