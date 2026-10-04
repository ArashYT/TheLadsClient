using System.Net;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class LauncherTranslatorTests
{
    private static string Template(string text, params string[] names) => LauncherTranslator.Mask(text, names).Template;

    [Fact]
    public void Masks_everything_that_must_not_reach_Google()
    {
        Assert.Equal("Downloading assets: {0} / {1} ({2}%)", Template("Downloading assets: 12.5MB / 400.0MB (3%)"));
        Assert.Equal("{0} online, mod {1}", Template("1,031 online, mod v0.7.7+fix3+26.3"));
        Assert.Equal("Username or {0} link", Template("Username or https:// link")); // "s:/" is not a drive
        Assert.Equal("{0}  /  LAUNCHER", Template("THE LADS CLIENT  /  LAUNCHER"));
        Assert.Equal("Install {0} Loader {1} for MC {2}", Template("Install Fabric Loader 0.19.3 for MC 26.3"));
        Assert.Equal("Could not open {0}: access denied", Template(@"Could not open 'C:\Users\Player\My Pack': access denied"));
        Assert.Equal("Open {0} in a browser", Template("Open https://modrinth.com/mod/sodium?x=1 in a browser"));
        Assert.Equal("Sync screenshots to the global {0} folder", Template("Sync screenshots to the global .minecraft folder"));
        Assert.Equal("Shared under Global {0}. Version is {1}.", Template("Shared under Global .minecraft. Version is 1.8.9."));
        Assert.Equal("Wrote {0} and {1}", Template("Wrote options.txt and iris-1.8.jar"));
        Assert.Equal("Joining {0}", Template("Joining play.example.net"));
        Assert.Equal("Keep {0} as it is", Template("Keep {name} as it is"));
        // An apostrophe inside a word is not a quote.
        Assert.Equal("Don't re-open the launcher when the game closes", Template("Don't re-open the launcher when the game closes"));
    }

    [Fact]
    public void Masks_names_shown_elsewhere_as_whole_words_only()
    {
        Assert.Equal("Account removed: {0}", Template("Account removed: Max", "Max"));
        Assert.Equal("Maximum RAM Allocation", Template("Maximum RAM Allocation", "Max"));
        Assert.Equal("Welcome, {0}! {1} is ready.", Template("Welcome, Silver Fox! Silver Fox's Pack is ready.", "Silver Fox", "Silver Fox's Pack"));
        var (template, values) = LauncherTranslator.Mask("Deleted Pack 1.2 now", new[] { "Pack 1" });
        Assert.Equal("Deleted {0} now", template); // a name overlapping a number is one placeholder
        Assert.Equal(new[] { "Pack 1.2" }, values);
    }

    [Fact]
    public void Unmasks_in_the_translated_order_and_refuses_lost_placeholders()
    {
        var values = new[] { "3", "40" };
        Assert.Equal("40 ワールド中 3", LauncherTranslator.Unmask("{1} ワールド中 {0}", values));
        Assert.Equal("40 ワールド中 3", LauncherTranslator.Unmask("｛1｝ ワールド中 { 0 }", values));
        Assert.Null(LauncherTranslator.Unmask("{0} mundos", values));        // {1} lost
        Assert.Null(LauncherTranslator.Unmask("{0} de {1} y {1}", values));  // repeated
        Assert.Null(LauncherTranslator.Unmask("{0} de {2}", values));        // invented
        Assert.Equal("Ajustes", LauncherTranslator.Unmask("Ajustes", Array.Empty<string>()));
    }

    [Fact]
    public void Batches_by_count_and_size()
    {
        var texts = Enumerable.Range(0, 250).Select(i => "text " + i).ToList();
        var chunks = LauncherTranslator.Chunks(texts).ToList();
        Assert.Equal(new[] { 100, 100, 50 }, chunks.Select(c => c.Count));
        Assert.Equal(texts, chunks.SelectMany(c => c));
        var big = LauncherTranslator.Chunks(new[] { new string('a', 2000), new string('b', 2000), "c" }).ToList();
        Assert.Equal(new[] { 1, 2 }, big.Select(c => c.Count));
    }

    [Fact]
    public async Task Sends_one_q_per_text_and_falls_back_to_the_second_endpoint()
    {
        var requests = new List<(Uri Url, string Body)>();
        var http = new HttpClient(new Handler(async request =>
        {
            requests.Add((request.RequestUri!, await request.Content!.ReadAsStringAsync()));
            return requests.Count == 1
                ? new HttpResponseMessage(HttpStatusCode.TooManyRequests) { Content = new StringContent("<html>Sorry...</html>") }
                : new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent("[\"Ajustes\",\"{0} de {1} mundos\",\"Línea\\nDos\"]") };
        }));
        var result = await LauncherTranslator.TranslateAsync(http, "es", new[] { "Settings", "{0} of {1} worlds", "Line\nTwo" }, CancellationToken.None);
        Assert.Equal(new[] { "Ajustes", "{0} de {1} mundos", "Línea\nDos" }, result);
        Assert.Equal(2, requests.Count);
        Assert.All(requests, r => Assert.Contains("tl=es", r.Url.Query));
        Assert.NotEqual(requests[0].Url.Host, requests[1].Url.Host);
        Assert.Equal("q=Settings&q=%7B0%7D+of+%7B1%7D+worlds&q=Line%0ATwo", requests[1].Body);
    }

    [Fact]
    public async Task Fails_when_every_endpoint_fails_or_answers_the_wrong_count()
    {
        var offline = new HttpClient(new Handler(_ => throw new HttpRequestException("offline")));
        await Assert.ThrowsAsync<HttpRequestException>(() => LauncherTranslator.TranslateAsync(offline, "de", new[] { "Settings" }, CancellationToken.None));
        var short1 = new HttpClient(new Handler(_ => Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent("[\"Einstellungen\"]") })));
        await Assert.ThrowsAsync<InvalidDataException>(() => LauncherTranslator.TranslateAsync(short1, "de", new[] { "Settings", "Play" }, CancellationToken.None));
        Assert.Equal(new[] { "Hola", "Mundo" }, LauncherTranslator.ParseTranslations("[[\"Hola\",\"en\"],[\"Mundo\",\"en\"]]", 2));
        Assert.Equal(new[] { "Hola" }, LauncherTranslator.ParseTranslations("\"Hola\"", 1));
    }

    [Fact]
    public async Task Cache_round_trips_and_a_missing_or_broken_file_is_empty()
    {
        var dir = Path.Combine(Path.GetTempPath(), "lads-lang-" + Guid.NewGuid().ToString("N"));
        try
        {
            var file = Path.Combine(dir, "lang-cache", "ja.json");
            Assert.Empty(LauncherTranslator.LoadCache(file));
            var cache = new Dictionary<string, string> { ["Settings"] = "設定", ["{0} of {1} worlds"] = "{1} ワールド中 {0}" };
            await LauncherTranslator.SaveCacheAsync(file, cache);
            Assert.Equal(cache, LauncherTranslator.LoadCache(file));
            Assert.Contains("設定", File.ReadAllText(file)); // readable, not \u-escaped
            File.WriteAllText(file, "{ not json");
            Assert.Empty(LauncherTranslator.LoadCache(file));
        }
        finally { if (Directory.Exists(dir)) Directory.Delete(dir, true); }
    }

    private sealed class Handler(Func<HttpRequestMessage, Task<HttpResponseMessage>> respond) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token) => respond(request);
    }
}
