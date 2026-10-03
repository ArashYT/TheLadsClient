using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public sealed record ProfileCape(string Id, string Name, string Url, bool Active);

/// <summary>The parts of a Minecraft Java profile the Skins tab shows.</summary>
public sealed record MinecraftProfile(string Name, string? SkinUrl, string SkinModel, IReadOnlyList<ProfileCape> Capes);

public sealed class MinecraftSkinApiException(string message) : Exception(message);

/// <summary>
/// Minecraft Services skin and cape calls for a signed-in Microsoft account, plus the public read-only lookups used to
/// import another player's skin. Error messages never include tokens or response bodies.
/// </summary>
public static class MinecraftSkinApi
{
    public const string ProfileUrl = "https://api.minecraftservices.com/minecraft/profile";
    private const int MaxPngBytes = 1 << 20;

    public static HttpRequestMessage GetProfile(string token) => Authorized(HttpMethod.Get, ProfileUrl, token);

    public static HttpRequestMessage UploadSkin(string token, byte[] png, string model)
    {
        var request = Authorized(HttpMethod.Post, ProfileUrl + "/skins", token);
        // Quoted names, as browsers send them.
        var variant = new StringContent(SkinLibrary.NormalizeModel(model));
        variant.Headers.ContentDisposition = new ContentDispositionHeaderValue("form-data") { Name = "\"variant\"" };
        var file = new ByteArrayContent(png);
        file.Headers.ContentType = new MediaTypeHeaderValue("image/png");
        file.Headers.ContentDisposition = new ContentDispositionHeaderValue("form-data") { Name = "\"file\"", FileName = "\"skin.png\"" };
        request.Content = new MultipartFormDataContent { variant, file };
        return request;
    }

    public static HttpRequestMessage ShowCape(string token, string capeId)
    {
        var request = Authorized(HttpMethod.Put, ProfileUrl + "/capes/active", token);
        request.Content = JsonContent.Create(new { capeId });
        return request;
    }

    public static HttpRequestMessage HideCape(string token) => Authorized(HttpMethod.Delete, ProfileUrl + "/capes/active", token);

    /// <summary>Sends one of the requests above. All of them answer with the profile; returns null if the body is not one.</summary>
    public static async Task<MinecraftProfile?> SendAsync(HttpClient http, HttpRequestMessage request, CancellationToken cancellationToken)
    {
        using (request)
        using (var response = await http.SendAsync(request, cancellationToken))
        {
            if (!response.IsSuccessStatusCode) throw new MinecraftSkinApiException(Describe(request, response.StatusCode));
            string body = await response.Content.ReadAsStringAsync(cancellationToken);
            try { return ParseProfile(body); }
            catch (Exception e) when (e is JsonException or InvalidOperationException or KeyNotFoundException) { return null; }
        }
    }

    public static MinecraftProfile ParseProfile(string json)
    {
        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;
        var skin = Items(root, "skins").FirstOrDefault(s => Text(s, "state") == "ACTIVE");
        var capes = Items(root, "capes")
            .Select(c => new ProfileCape(Text(c, "id"), Text(c, "alias") is { Length: > 0 } alias ? alias : "Cape", Text(c, "url"), Text(c, "state") == "ACTIVE"))
            .Where(c => c.Id.Length > 0)
            .ToList();
        return new MinecraftProfile(root.GetProperty("name").GetString() ?? "",
            skin.ValueKind == JsonValueKind.Object ? Text(skin, "url") : null,
            SkinLibrary.NormalizeModel(skin.ValueKind == JsonValueKind.Object ? Text(skin, "variant") : null), capes);
    }

    /// <summary>Another player's current skin and model, from Mojang's public profile lookups (read-only, no account).</summary>
    public static async Task<(byte[] Png, string Model)> DownloadPlayerSkinAsync(HttpClient http, string username, CancellationToken cancellationToken)
    {
        string name = username.Trim();
        if (name.Length is < 1 or > 16 || !name.All(c => char.IsAsciiLetterOrDigit(c) || c == '_'))
            throw new MinecraftSkinApiException("Enter a Minecraft username (letters, digits and _, up to 16 characters) or an https:// link to a PNG.");
        using var lookup = await http.GetAsync("https://api.mojang.com/users/profiles/minecraft/" + name, cancellationToken);
        if (lookup.StatusCode is HttpStatusCode.NotFound or HttpStatusCode.NoContent)
            throw new MinecraftSkinApiException($"No Minecraft player is called {name}.");
        if (!lookup.IsSuccessStatusCode) throw new MinecraftSkinApiException($"The player lookup failed (HTTP {(int)lookup.StatusCode}). Try again later.");
        string id;
        using (var found = JsonDocument.Parse(await lookup.Content.ReadAsStringAsync(cancellationToken))) id = Text(found.RootElement, "id");
        if (!Guid.TryParse(id, out _)) throw new MinecraftSkinApiException("The player lookup returned no profile id.");

        using var profile = await http.GetAsync("https://sessionserver.mojang.com/session/minecraft/profile/" + id, cancellationToken);
        if (!profile.IsSuccessStatusCode) throw new MinecraftSkinApiException($"The skin lookup failed (HTTP {(int)profile.StatusCode}). Try again later.");
        var (url, model) = ReadTextures(await profile.Content.ReadAsStringAsync(cancellationToken));
        if (url == null) throw new MinecraftSkinApiException($"{name} uses a default skin; there is no skin file to import.");
        return (await DownloadPngAsync(http, url, cancellationToken), model);
    }

    /// <summary>The SKIN url and model from a session-server profile (base64 "textures" property).</summary>
    public static (string? Url, string Model) ReadTextures(string sessionProfileJson)
    {
        using var document = JsonDocument.Parse(sessionProfileJson);
        var property = Items(document.RootElement, "properties").FirstOrDefault(p => Text(p, "name") == "textures");
        if (property.ValueKind != JsonValueKind.Object) return (null, "classic");
        using var textures = JsonDocument.Parse(Encoding.UTF8.GetString(Convert.FromBase64String(Text(property, "value"))));
        if (!textures.RootElement.TryGetProperty("textures", out var all) || !all.TryGetProperty("SKIN", out var skin)) return (null, "classic");
        string url = Https(Text(skin, "url"));
        string model = skin.TryGetProperty("metadata", out var metadata) ? Text(metadata, "model") : "";
        return (url.Length == 0 ? null : url, SkinLibrary.NormalizeModel(model));
    }

    /// <summary>Mojang hands out http:// texture links; textures.minecraft.net answers on https too, so never download over plain http.</summary>
    public static string Https(string url) =>
        url.StartsWith("http://textures.minecraft.net/", StringComparison.OrdinalIgnoreCase) ? "https://" + url[7..] : url;

    /// <summary>Downloads a skin PNG from an https link (at most 1 MB) and checks it is a skin.</summary>
    public static async Task<byte[]> DownloadPngAsync(HttpClient http, string url, CancellationToken cancellationToken)
    {
        if (!Uri.TryCreate(url.Trim(), UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps)
            throw new MinecraftSkinApiException("Skin links must start with https://.");
        using var response = await http.GetAsync(uri, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        if (!response.IsSuccessStatusCode) throw new MinecraftSkinApiException($"The skin download failed (HTTP {(int)response.StatusCode}).");
        if (response.Content.Headers.ContentLength > MaxPngBytes) throw new MinecraftSkinApiException("That file is too large to be a skin.");
        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        var buffer = new MemoryStream();
        var chunk = new byte[16384];
        int read;
        while ((read = await stream.ReadAsync(chunk, cancellationToken)) > 0)
        {
            if (buffer.Length + read > MaxPngBytes) throw new MinecraftSkinApiException("That file is too large to be a skin.");
            buffer.Write(chunk, 0, read);
        }
        byte[] png = buffer.ToArray();
        SkinLibrary.CheckSkinPng(png);
        return png;
    }

    private static HttpRequestMessage Authorized(HttpMethod method, string url, string token)
    {
        var request = new HttpRequestMessage(method, url);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        return request;
    }

    private static string Describe(HttpRequestMessage request, HttpStatusCode status)
    {
        string what = request.Method == HttpMethod.Get ? "Reading the Minecraft profile" : request.RequestUri!.AbsolutePath.EndsWith("/skins") ? "Uploading the skin" : "Changing the cape";
        string hint = status switch
        {
            HttpStatusCode.Unauthorized => " The Microsoft session expired: refresh the account in Accounts and try again.",
            HttpStatusCode.TooManyRequests => " Minecraft limits how often skins and capes change: wait a minute and try again.",
            HttpStatusCode.BadRequest when what == "Uploading the skin" => " Minecraft rejected the image: it must be a 64x64 or 64x32 PNG skin.",
            HttpStatusCode.BadRequest or HttpStatusCode.Forbidden or HttpStatusCode.NotFound when what == "Changing the cape" => " This account may not own that cape.",
            _ => ""
        };
        return $"{what} failed (HTTP {(int)status}).{hint}";
    }

    private static IEnumerable<JsonElement> Items(JsonElement element, string name) =>
        element.ValueKind == JsonValueKind.Object && element.TryGetProperty(name, out var array) && array.ValueKind == JsonValueKind.Array
            ? array.EnumerateArray().ToList() : Enumerable.Empty<JsonElement>();

    private static string Text(JsonElement element, string name) =>
        element.ValueKind == JsonValueKind.Object && element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() ?? "" : "";
}
