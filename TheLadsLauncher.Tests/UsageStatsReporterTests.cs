using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher;
using TheLadsLauncher.Services;
using Xunit;

public class UsageStatsReporterTests
{
    private sealed class MockHttpMessageHandler : HttpMessageHandler
    {
        public int RequestCount { get; private set; }
        public HttpRequestMessage? LastRequest { get; private set; }
        public string? LastRequestBody { get; private set; }
        public HttpStatusCode StatusCodeToReturn { get; set; } = HttpStatusCode.OK;

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            RequestCount++;
            LastRequest = request;
            if (request.Content != null)
            {
                LastRequestBody = await request.Content.ReadAsStringAsync(cancellationToken);
            }
            return new HttpResponseMessage(StatusCodeToReturn)
            {
                Content = new StringContent("{\"ok\":true}")
            };
        }
    }

    [Fact]
    public void BuildPayload_WithDefaultSettings_GeneratesValidGuidInstallIdAndExpectedPayload()
    {
        var settings = new LauncherSettings
        {
            SendAnonymousUsageStats = true,
            InstallId = ""
        };

        var payload = UsageStatsReporter.BuildPayload(
            settings,
            clientVersions: new[] { "1.8.9" },
            launcherVersion: "1.9.14",
            os: "windows",
            isSandbox: false);

        Assert.NotNull(payload);
        Assert.True(Guid.TryParse(payload.InstallId, out _));
        Assert.Equal("1.9.14", payload.LauncherVersion);
        Assert.Equal("windows", payload.Os);
        Assert.Single(payload.ClientVersionsUsed);
        Assert.Contains("1.8.9", payload.ClientVersionsUsed);
    }

    [Fact]
    public void BuildPayload_OptOut_ReturnsNull()
    {
        var settings = new LauncherSettings
        {
            SendAnonymousUsageStats = false,
            InstallId = Guid.NewGuid().ToString("D")
        };

        var payload = UsageStatsReporter.BuildPayload(
            settings,
            clientVersions: new[] { "1.8.9" },
            launcherVersion: "1.9.14",
            isSandbox: false);

        Assert.Null(payload);
    }

    [Fact]
    public void BuildPayload_PreservesExistingInstallId()
    {
        string expectedGuid = "4a187e14-d03b-487e-97c5-5553b6cb5921";
        var settings = new LauncherSettings
        {
            SendAnonymousUsageStats = true,
            InstallId = expectedGuid
        };

        var payload = UsageStatsReporter.BuildPayload(settings, clientVersions: Array.Empty<string>(), isSandbox: false);

        Assert.NotNull(payload);
        Assert.Equal(expectedGuid, payload.InstallId);
    }

    [Fact]
    public void BuildPayload_DeduplicatesAndOrdersClientVersions()
    {
        var settings = new LauncherSettings
        {
            SendAnonymousUsageStats = true
        };

        var payload = UsageStatsReporter.BuildPayload(
            settings,
            clientVersions: new[] { "1.20.1", "1.8.9", "1.20.1", "  ", "26.3" }, isSandbox: false);

        Assert.NotNull(payload);
        Assert.Equal(new[] { "1.20.1", "1.8.9", "26.3" }, payload.ClientVersionsUsed);
    }

    [Fact]
    public void RecordClientVersionUsed_AddsDistinctVersions()
    {
        var settings = new LauncherSettings();
        settings.ClientVersionsUsed.Clear();

        UsageStatsReporter.RecordClientVersionUsed(settings, "1.8.9");
        UsageStatsReporter.RecordClientVersionUsed(settings, "1.8.9"); // duplicate
        UsageStatsReporter.RecordClientVersionUsed(settings, "1.20.1");
        UsageStatsReporter.RecordClientVersionUsed(settings, null);
        UsageStatsReporter.RecordClientVersionUsed(settings, "   ");

        Assert.Equal(2, settings.ClientVersionsUsed.Count);
        Assert.Contains("1.8.9", settings.ClientVersionsUsed);
        Assert.Contains("1.20.1", settings.ClientVersionsUsed);
    }

    [Fact]
    public async Task SendHeartbeatAsync_WhenOptedOut_DoesNotMakeNetworkRequest()
    {
        var handler = new MockHttpMessageHandler();
        using var client = new HttpClient(handler);

        var settings = new LauncherSettings
        {
            SendAnonymousUsageStats = false
        };

        bool result = await UsageStatsReporter.SendHeartbeatAsync(settings, client, isSandbox: false);

        Assert.False(result);
        Assert.Equal(0, handler.RequestCount);
    }

    [Fact]
    public async Task SendHeartbeatAsync_WhenEnabled_SendsPostWithExpectedHeadersAndBody()
    {
        var handler = new MockHttpMessageHandler();
        using var client = new HttpClient(handler);

        var settings = new LauncherSettings
        {
            SendAnonymousUsageStats = true,
            InstallId = "4a187e14-d03b-487e-97c5-5553b6cb5921"
        };
        settings.ClientVersionsUsed.Add("1.8.9");

        bool result = await UsageStatsReporter.SendHeartbeatAsync(settings, client, isSandbox: false);

        Assert.True(result);
        Assert.Equal(1, handler.RequestCount);
        Assert.NotNull(handler.LastRequest);
        Assert.Equal(HttpMethod.Post, handler.LastRequest.Method);
        Assert.Equal("https://bot.arashyt.ca/usage", handler.LastRequest.RequestUri?.ToString());
        Assert.Contains("TheLadsLauncher", handler.LastRequest.Headers.UserAgent.ToString());

        Assert.NotNull(handler.LastRequestBody);
        using var doc = JsonDocument.Parse(handler.LastRequestBody);
        var root = doc.RootElement;
        Assert.Equal("4a187e14-d03b-487e-97c5-5553b6cb5921", root.GetProperty("install_id").GetString());
        Assert.True(root.TryGetProperty("launcher_version", out _));
        Assert.True(root.TryGetProperty("client_versions_used", out var cv));
        Assert.Equal(JsonValueKind.Array, cv.ValueKind);
        Assert.Equal("1.8.9", cv[0].GetString());
        Assert.True(root.TryGetProperty("os", out _));
    }

    [Fact]
    public void BuildPayload_WhenSandboxEnvSet_AndNoOverride_ReturnsNull()
    {
        var settings = new LauncherSettings
        {
            SendAnonymousUsageStats = true,
            InstallId = "4a187e14-d03b-487e-97c5-5553b6cb5921"
        };

        // Explicit isSandbox = true always returns null
        var payloadExplicit = UsageStatsReporter.BuildPayload(settings, isSandbox: true);
        Assert.Null(payloadExplicit);

        // When default parameter is used under THELADS_DIR environment variable
        if (!string.IsNullOrEmpty(Environment.GetEnvironmentVariable("THELADS_DIR")))
        {
            var payloadDefault = UsageStatsReporter.BuildPayload(settings);
            Assert.Null(payloadDefault);
        }
    }

    [Fact]
    public async Task SendHeartbeatAsync_WhenSandboxEnvSet_AndNoOverride_DoesNotSend()
    {
        var handler = new MockHttpMessageHandler();
        using var client = new HttpClient(handler);

        var settings = new LauncherSettings
        {
            SendAnonymousUsageStats = true,
            InstallId = "4a187e14-d03b-487e-97c5-5553b6cb5921"
        };

        // Explicit isSandbox = true never sends
        bool resultExplicit = await UsageStatsReporter.SendHeartbeatAsync(settings, client, isSandbox: true);
        Assert.False(resultExplicit);
        Assert.Equal(0, handler.RequestCount);

        // When default parameter is used under THELADS_DIR environment variable
        if (!string.IsNullOrEmpty(Environment.GetEnvironmentVariable("THELADS_DIR")))
        {
            bool resultDefault = await UsageStatsReporter.SendHeartbeatAsync(settings, client);
            Assert.False(resultDefault);
            Assert.Equal(0, handler.RequestCount);
        }
    }
}
