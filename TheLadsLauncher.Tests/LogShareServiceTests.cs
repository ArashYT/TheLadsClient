using System;
using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class LogShareServiceTests
{
    [Fact]
    public void SanitizeLog_ScrubsEmails()
    {
        string raw = "User authenticated as alice.smith+mc@sub.domain.org during startup.";
        string sanitized = LogShareService.SanitizeLog(raw);

        Assert.DoesNotContain("alice.smith+mc@sub.domain.org", sanitized);
        Assert.Contains("<email-hidden>", sanitized);
    }

    [Fact]
    public void SanitizeLog_ScrubsIPv4AndIPv6_WhilePreservingLoopbacks()
    {
        string raw = "Connected from 198.51.100.45 and 2001:0db8:85a3:0000:0000:8a2e:0370:7334 to 127.0.0.1 and ::1 at 12:34:56";
        string sanitized = LogShareService.SanitizeLog(raw);

        Assert.DoesNotContain("198.51.100.45", sanitized);
        Assert.DoesNotContain("2001:0db8:85a3:0000:0000:8a2e:0370:7334", sanitized);
        Assert.Contains("127.0.0.1", sanitized);
        Assert.Contains("::1", sanitized);
        // Ensure timestamps like 12:34:56 are not damaged
        Assert.Contains("12:34:56", sanitized);
    }

    [Fact]
    public void SanitizeLog_ScrubsAccessTokensAndSessionIds()
    {
        string raw = "Arguments: --accessToken secretToken123456789 -Dminecraft.session.id=session987654321 with Bearer mySuperSecretBearerToken99";
        string sanitized = LogShareService.SanitizeLog(raw);

        Assert.DoesNotContain("secretToken123456789", sanitized);
        Assert.DoesNotContain("session987654321", sanitized);
        Assert.DoesNotContain("mySuperSecretBearerToken99", sanitized);
        Assert.Contains("--accessToken <token>", sanitized);
        Assert.Contains("-Dminecraft.session.id=<token>", sanitized);
        Assert.Contains("Bearer <token>", sanitized);
    }

    [Fact]
    public void SanitizeLog_ScrubsUserPaths_WindowsLinuxAndMac()
    {
        string raw = "Paths:\nC:\\Users\\JohnDoe\\AppData\\Roaming\\.minecraft\n/home/johndoe/.local/share/mc\n/Users/johndoe/Library/Application Support/minecraft";
        string sanitized = LogShareService.SanitizeLog(raw);

        Assert.DoesNotContain("JohnDoe", sanitized);
        Assert.DoesNotContain("johndoe", sanitized);
        Assert.Contains("%USERPROFILE%\\AppData", sanitized);
        Assert.Contains("/home/<user>/.local", sanitized);
        Assert.Contains("/Users/<user>/Library", sanitized);
    }

    [Fact]
    public void SanitizeLog_PreservesPlayerUUID()
    {
        string uuid = "4566e69f-c907-48ee-8d71-d7ba5aa00d20";
        string raw = $"Player {uuid} logged in with email player@example.com from 203.0.113.19";
        string sanitized = LogShareService.SanitizeLog(raw);

        Assert.Contains(uuid, sanitized);
        Assert.DoesNotContain("player@example.com", sanitized);
        Assert.DoesNotContain("203.0.113.19", sanitized);
    }

    [Fact]
    public async Task UploadLogAsync_ReturnsValidShareUrl()
    {
        var handler = new MockHttpMessageHandler(@"{""success"": true, ""url"": ""https://mclo.gs/testlink123""}");
        using var client = new HttpClient(handler);

        var result = await LogShareService.UploadLogAsync("Sample crash log content", client);

        Assert.True(result.Success);
        Assert.Equal("https://mclo.gs/testlink123", result.Url);
    }

    private sealed class MockHttpMessageHandler : HttpMessageHandler
    {
        private readonly string _responseJson;

        public MockHttpMessageHandler(string responseJson)
        {
            _responseJson = responseJson;
        }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var response = new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(_responseJson, System.Text.Encoding.UTF8, "application/json")
            };
            return Task.FromResult(response);
        }
    }
}
