using System;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>Resolve pinned releases through Modrinth before downloading a missing jar.
/// Existing verified cache entries remain usable offline; an API failure never selects a different game version.</summary>
public static class ModrinthReleaseVerifier
{
    public static async Task VerifyAsync(HttpClient client, ClientModInstaller.Entry entry,
        string minecraftVersion, CancellationToken cancellationToken)
    {
        if (!Regex.IsMatch(entry.VersionId, "^[a-zA-Z0-9]+$") || !Regex.IsMatch(entry.ProjectId, "^[a-zA-Z0-9]+$"))
            throw new InvalidDataException("Invalid Modrinth project or release ID.");
        using var request = new HttpRequestMessage(HttpMethod.Get,
            "https://api.modrinth.com/v2/version/" + entry.VersionId);
        request.Headers.UserAgent.ParseAdd("TheLadsClient/1.2.1");
        using var response = await client.SendAsync(request, cancellationToken);
        response.EnsureSuccessStatusCode();
        using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken));
        var release = document.RootElement;
        if (release.GetProperty("id").GetString() != entry.VersionId
            || release.GetProperty("project_id").GetString() != entry.ProjectId
            || !release.GetProperty("game_versions").EnumerateArray().Any(v => v.GetString() == minecraftVersion)
            || !release.GetProperty("loaders").EnumerateArray().Any(v => v.GetString() == "fabric"))
            throw new InvalidDataException($"Modrinth release for {entry.Name} is not a Fabric release for Minecraft {minecraftVersion}.");
        var matches = release.GetProperty("files").EnumerateArray().Where(file =>
            file.GetProperty("hashes").GetProperty("sha512").GetString()?.Equals(entry.Sha512, StringComparison.OrdinalIgnoreCase) == true
            && file.GetProperty("size").GetInt64() == entry.Size
            && file.GetProperty("url").GetString() == entry.Url
            && file.GetProperty("filename").GetString() == entry.FileName).Count();
        if (matches != 1)
            throw new InvalidDataException($"Modrinth metadata changed for {entry.Name}. Update the client pack before retrying.");
    }
}
