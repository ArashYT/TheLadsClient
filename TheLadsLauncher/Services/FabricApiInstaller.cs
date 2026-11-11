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
    /// <summary>True when Fabric API is present or was installed; false (status says why) when offline or Modrinth has none for this version.</summary>
    public static async Task<bool> EnsureAsync(HttpClient http, string modsDirectory, string mcVersion, string loader,
        Action<string>? status, CancellationToken token)
    {
        if (ModpackCoreSync.HasFabricApi(modsDirectory)) return true;
        try
        {
            status?.Invoke("Installing Fabric API (needed by The Lads Core)...");
            var name = await ModrinthJarInstaller.InstallLatestAsync(http, "fabric-api", modsDirectory, mcVersion, loader, status, token);
            if (name == null)
            {
                status?.Invoke($"Modrinth has no Fabric API for Minecraft {mcVersion}.");
                return false;
            }
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
