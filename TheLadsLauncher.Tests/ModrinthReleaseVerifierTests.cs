using System.Net;
using System.Text.Json;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class ModrinthReleaseVerifierTests
{
    private static readonly ClientModInstaller.Entry Entry = new("project1", "sodium", "Sodium", "sodium",
        "release1", "1", "sodium.jar", "https://cdn.modrinth.com/data/project1/versions/release1/sodium.jar",
        new string('a', 128), 100, "LGPL-3.0", null, "https://modrinth.com/mod/sodium");

    [Theory]
    [InlineData("26.3", "fabric", "project1", true)]
    [InlineData("26.2", "fabric", "project1", false)]
    [InlineData("26.3", "neoforge", "project1", false)]
    [InlineData("26.3", "fabric", "different", false)]
    public async Task RequiresExactGameLoaderAndProject(string game, string loader, string project, bool valid)
    {
        using var client = new HttpClient(new Handler(Release(game, loader, project)));
        if (valid) await ModrinthReleaseVerifier.VerifyAsync(client, Entry, "26.3", default);
        else await Assert.ThrowsAsync<InvalidDataException>(() => ModrinthReleaseVerifier.VerifyAsync(client, Entry, "26.3", default));
    }

    [Fact]
    public async Task RefusesChangedBytesEvenWhenReleaseIdMatches()
    {
        using var client = new HttpClient(new Handler(Release("26.3", "fabric", "project1")));
        await Assert.ThrowsAsync<InvalidDataException>(() => ModrinthReleaseVerifier.VerifyAsync(client,
            Entry with { Sha512 = new string('b', 128) }, "26.3", default));
    }

    [Fact]
    public async Task CancellationDoesNotFallBackToUnverifiedDownloads()
    {
        using var client = new HttpClient(new Handler(Release("26.3", "fabric", "project1")));
        using var cancelled = new CancellationTokenSource(); cancelled.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => ModrinthReleaseVerifier.VerifyAsync(client, Entry, "26.3", cancelled.Token));
    }

    private static string Release(string game, string loader, string project) => JsonSerializer.Serialize(new {
        id = Entry.VersionId, project_id = project, game_versions = new[] { game }, loaders = new[] { loader },
        files = new[] { new { filename = Entry.FileName, url = Entry.Url, size = Entry.Size, hashes = new { sha512 = Entry.Sha512 } } }
    });

    private sealed class Handler(string json) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            token.ThrowIfCancellationRequested();
            Assert.Equal("https://api.modrinth.com/v2/version/release1", request.RequestUri!.AbsoluteUri);
            Assert.Contains(request.Headers.UserAgent, value => value.Product?.Name == "TheLadsClient");
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(json) });
        }
    }
}
