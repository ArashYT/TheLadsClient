using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class LogShareServiceTests
{
    private sealed class MockHttpHandler : HttpMessageHandler
    {
        private readonly HttpResponseMessage _response;
        public string? LastRequestBody { get; private set; }

        public MockHttpHandler(HttpResponseMessage response) => _response = response;

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            if (request.Content != null)
            {
                LastRequestBody = await request.Content.ReadAsStringAsync(cancellationToken);
            }
            return _response;
        }
    }

    [Fact]
    public void SanitizeLog_ScrubsSensitiveTokensAndIps()
    {
        string raw = "[INFO] Connecting to 192.168.1.100 with accessToken: secret_token_value_1234567890\n" +
                     "[INFO] Local address 127.0.0.1 is safe";

        string sanitized = LogShareService.SanitizeLog(raw);

        Assert.DoesNotContain("secret_token_value_1234567890", sanitized);
        Assert.DoesNotContain("192.168.1.100", sanitized);
        Assert.Contains("<ip-hidden>", sanitized);
        Assert.Contains("127.0.0.1", sanitized);
    }

    [Fact]
    public async Task UploadLogAsync_ReturnsUrlOnSuccess()
    {
        var mockResponse = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent("{\"success\":true,\"url\":\"https://mclo.gs/test1234\"}")
        };
        var handler = new MockHttpHandler(mockResponse);
        using var client = new HttpClient(handler);

        var result = await LogShareService.UploadLogAsync("Sample log line with error", client);

        Assert.True(result.Success);
        Assert.Equal("https://mclo.gs/test1234", result.Url);
        Assert.Null(result.ErrorMessage);
    }

    [Fact]
    public async Task UploadLogAsync_HandlesEmptyLog()
    {
        var result = await LogShareService.UploadLogAsync("");

        Assert.False(result.Success);
        Assert.Equal("Log is empty.", result.ErrorMessage);
    }
}
