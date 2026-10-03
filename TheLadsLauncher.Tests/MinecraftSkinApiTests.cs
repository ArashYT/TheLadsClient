using System.Net;
using System.Text;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class MinecraftSkinApiTests
{
    private const string Profile = """
        {"id":"fedcba9876543210fedcba9876543210","name":"Lad",
         "skins":[{"id":"s0","state":"INACTIVE","url":"http://textures.minecraft.net/texture/old","variant":"CLASSIC"},
                  {"id":"s1","state":"ACTIVE","url":"http://textures.minecraft.net/texture/now","variant":"SLIM"}],
         "capes":[{"id":"c1","state":"INACTIVE","url":"http://textures.minecraft.net/texture/c1","alias":"Migrator"},
                  {"id":"c2","state":"ACTIVE","url":"http://textures.minecraft.net/texture/c2","alias":"Vanilla"}]}
        """;

    [Fact]
    public async Task UploadSkinPostsMultipartVariantAndPngWithBearerToken()
    {
        byte[] png = SkinLibraryTests.Png(64, 64, 7);
        var handler = new Recorder(_ => Json(Profile));
        var profile = await MinecraftSkinApi.SendAsync(new HttpClient(handler), MinecraftSkinApi.UploadSkin("synthetic-token", png, "Slim"), default);

        var request = Assert.Single(handler.Requests);
        Assert.Equal(HttpMethod.Post, request.Method);
        Assert.Equal("https://api.minecraftservices.com/minecraft/profile/skins", request.Url);
        Assert.Equal("Bearer synthetic-token", request.Authorization);
        Assert.StartsWith("multipart/form-data", request.ContentType);
        Assert.Equal("slim", request.Parts["variant"].Text);
        Assert.Equal(png, request.Parts["file"].Bytes);
        Assert.Equal("image/png", request.Parts["file"].ContentType);
        Assert.Equal("skin.png", request.Parts["file"].FileName);
        Assert.Equal("form-data; name=\"file\"; filename=\"skin.png\"", request.Parts["file"].Disposition);
        Assert.Equal("form-data; name=\"variant\"", request.Parts["variant"].Disposition);
        Assert.Equal("Lad", profile!.Name);
    }

    [Fact]
    public async Task CapeRequestsUseActiveCapeEndpoint()
    {
        var handler = new Recorder(_ => Json(Profile));
        var http = new HttpClient(handler);
        await MinecraftSkinApi.SendAsync(http, MinecraftSkinApi.ShowCape("synthetic-token", "c1"), default);
        await MinecraftSkinApi.SendAsync(http, MinecraftSkinApi.HideCape("synthetic-token"), default);
        await MinecraftSkinApi.SendAsync(http, MinecraftSkinApi.GetProfile("synthetic-token"), default);

        Assert.Equal(new[] { "PUT", "DELETE", "GET" }, handler.Requests.Select(r => r.Method.Method));
        Assert.Equal("https://api.minecraftservices.com/minecraft/profile/capes/active", handler.Requests[0].Url);
        Assert.Equal("{\"capeId\":\"c1\"}", handler.Requests[0].Body);
        Assert.StartsWith("application/json", handler.Requests[0].ContentType);
        Assert.Equal("https://api.minecraftservices.com/minecraft/profile/capes/active", handler.Requests[1].Url);
        Assert.Null(handler.Requests[1].Body);
        Assert.Equal("https://api.minecraftservices.com/minecraft/profile", handler.Requests[2].Url);
        Assert.All(handler.Requests, r => Assert.Equal("Bearer synthetic-token", r.Authorization));
    }

    [Fact]
    public void ProfileParsingFindsActiveSkinModelAndOwnedCapes()
    {
        var profile = MinecraftSkinApi.ParseProfile(Profile);
        Assert.Equal("http://textures.minecraft.net/texture/now", profile.SkinUrl);
        Assert.Equal("slim", profile.SkinModel);
        Assert.Equal(new[] { new ProfileCape("c1", "Migrator", "http://textures.minecraft.net/texture/c1", false), new ProfileCape("c2", "Vanilla", "http://textures.minecraft.net/texture/c2", true) }, profile.Capes);
        Assert.Empty(MinecraftSkinApi.ParseProfile("{\"name\":\"NoCapes\"}").Capes);
    }

    [Theory]
    [InlineData(HttpStatusCode.Unauthorized, "refresh the account")]
    [InlineData(HttpStatusCode.TooManyRequests, "wait a minute")]
    [InlineData(HttpStatusCode.BadRequest, "64x64")]
    public async Task FailuresExplainTheStatusWithoutEchoingTheBodyOrToken(HttpStatusCode status, string hint)
    {
        var http = new HttpClient(new Recorder(_ => new HttpResponseMessage(status) { Content = new StringContent("SECRET-BODY synthetic-token") }));
        var error = await Assert.ThrowsAsync<MinecraftSkinApiException>(() =>
            MinecraftSkinApi.SendAsync(http, MinecraftSkinApi.UploadSkin("synthetic-token", SkinLibraryTests.Png(64, 64), "classic"), default));
        Assert.Contains($"HTTP {(int)status}", error.Message);
        Assert.Contains(hint, error.Message);
        Assert.DoesNotContain("SECRET-BODY", error.Message);
        Assert.DoesNotContain("synthetic-token", error.Message);
    }

    [Fact]
    public async Task UsernameImportReadsSkinAndSlimModelFromPublicLookups()
    {
        string textures = Convert.ToBase64String(Encoding.UTF8.GetBytes("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/abc\",\"metadata\":{\"model\":\"slim\"}}}}"));
        byte[] png = SkinLibraryTests.Png(64, 64, 9);
        var handler = new Recorder(r => r.Url switch
        {
            "https://api.mojang.com/users/profiles/minecraft/Some_Lad" => Json("{\"id\":\"0123456789abcdef0123456789abcdef\",\"name\":\"Some_Lad\"}"),
            "https://sessionserver.mojang.com/session/minecraft/profile/0123456789abcdef0123456789abcdef" => Json($"{{\"properties\":[{{\"name\":\"textures\",\"value\":\"{textures}\"}}]}}"),
            "https://textures.minecraft.net/texture/abc" => new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(png) },
            _ => new HttpResponseMessage(HttpStatusCode.NotFound)
        });
        var (skin, model) = await MinecraftSkinApi.DownloadPlayerSkinAsync(new HttpClient(handler), " Some_Lad ", default);
        Assert.Equal(png, skin);
        Assert.Equal("slim", model);
        Assert.All(handler.Requests, r => Assert.Equal(HttpMethod.Get, r.Method));
        Assert.All(handler.Requests, r => Assert.Null(r.Authorization));
    }

    [Theory]
    [InlineData("not a name!")]
    [InlineData("ThisNameIsWayTooLong")]
    public async Task BadUsernamesAreRefusedBeforeAnyRequest(string name)
    {
        var handler = new Recorder(_ => throw new InvalidOperationException("no request expected"));
        await Assert.ThrowsAsync<MinecraftSkinApiException>(() => MinecraftSkinApi.DownloadPlayerSkinAsync(new HttpClient(handler), name, default));
        Assert.Empty(handler.Requests);
    }

    [Fact]
    public async Task UnknownPlayerAndDefaultSkinGiveReadableErrors()
    {
        var unknown = new HttpClient(new Recorder(_ => new HttpResponseMessage(HttpStatusCode.NotFound)));
        Assert.Contains("No Minecraft player", (await Assert.ThrowsAsync<MinecraftSkinApiException>(() => MinecraftSkinApi.DownloadPlayerSkinAsync(unknown, "Nobody", default))).Message);
        Assert.Equal((null, "classic"), MinecraftSkinApi.ReadTextures("{\"properties\":[]}"));
    }

    [Fact]
    public async Task LinkDownloadsMustBeHttpsSkinPngsUnderOneMegabyte()
    {
        var handler = new Recorder(r => r.Url.EndsWith("big.png")
            ? new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(new byte[(1 << 20) + 1]) }
            : new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(SkinLibraryTests.Png(r.Url.EndsWith("wide.png") ? 128 : 64, 64)) });
        var http = new HttpClient(handler);
        Assert.Equal(SkinLibraryTests.Png(64, 64), await MinecraftSkinApi.DownloadPngAsync(http, "https://example.invalid/skin.png", default));
        await Assert.ThrowsAsync<MinecraftSkinApiException>(() => MinecraftSkinApi.DownloadPngAsync(http, "http://example.invalid/skin.png", default));
        await Assert.ThrowsAsync<MinecraftSkinApiException>(() => MinecraftSkinApi.DownloadPngAsync(http, "file:///C:/skin.png", default));
        await Assert.ThrowsAsync<MinecraftSkinApiException>(() => MinecraftSkinApi.DownloadPngAsync(http, "https://example.invalid/big.png", default));
        await Assert.ThrowsAsync<InvalidDataException>(() => MinecraftSkinApi.DownloadPngAsync(http, "https://example.invalid/wide.png", default));
        Assert.Equal(2, handler.Requests.Count(r => r.Url is "https://example.invalid/big.png" or "https://example.invalid/wide.png"));
        Assert.Equal("https://textures.minecraft.net/texture/x", MinecraftSkinApi.Https("http://textures.minecraft.net/texture/x"));
    }

    private static HttpResponseMessage Json(string body) => new(HttpStatusCode.OK) { Content = new StringContent(body, Encoding.UTF8, "application/json") };

    private sealed record Part(string? Text, byte[] Bytes, string? ContentType, string? FileName, string Disposition);
    private sealed record Seen(HttpMethod Method, string Url, string? Authorization, string? ContentType, string? Body, Dictionary<string, Part> Parts);

    /// <summary>Records each request (content read before the client disposes it) and answers from <paramref name="respond"/>.</summary>
    private sealed class Recorder(Func<Seen, HttpResponseMessage> respond) : HttpMessageHandler
    {
        public readonly List<Seen> Requests = new();
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var parts = new Dictionary<string, Part>();
            string? body = null;
            if (request.Content is MultipartFormDataContent multipart)
                foreach (var part in multipart)
                {
                    var bytes = await part.ReadAsByteArrayAsync(cancellationToken);
                    parts[part.Headers.ContentDisposition!.Name!.Trim('"')] = new Part(Encoding.UTF8.GetString(bytes), bytes,
                        part.Headers.ContentType?.MediaType, part.Headers.ContentDisposition.FileName?.Trim('"'), part.Headers.ContentDisposition.ToString());
                }
            else if (request.Content != null) body = await request.Content.ReadAsStringAsync(cancellationToken);
            var seen = new Seen(request.Method, request.RequestUri!.AbsoluteUri, request.Headers.Authorization?.ToString(),
                request.Content?.Headers.ContentType?.ToString(), body, parts);
            Requests.Add(seen);
            return respond(seen);
        }
    }
}
