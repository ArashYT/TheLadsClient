using System.Net;
using CmlLib.Core.Auth;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class LicenseVerificationTests
{
    [Theory]
    [InlineData("game_minecraft")]
    [InlineData("product_minecraft")]
    public async Task PlayableAndOwnedLicensesAreAccepted(string entitlement)
    {
        using var http = Client(HttpStatusCode.OK, $"{{\"items\":[{{\"name\":\"{entitlement}\"}}]}}");
        await MinecraftLicenseVerifier.VerifyAsync(http, Session(), default);
    }

    [Theory]
    [InlineData("{\"items\":[]}")]
    [InlineData("{\"items\":[{\"name\":\"unrelated_product\"}]}")]
    public async Task UnrelatedOrMissingLicenseCannotReportSuccessfulLogin(string body)
    {
        using var http = Client(HttpStatusCode.OK, body);
        var error = await Assert.ThrowsAsync<AccountVerificationException>(() => MinecraftLicenseVerifier.VerifyAsync(http, Session(), default));
        Assert.Contains("no active Minecraft Java license", error.Message);
    }

    [Theory]
    [InlineData(HttpStatusCode.TooManyRequests)]
    [InlineData(HttpStatusCode.ServiceUnavailable)]
    public async Task ServiceOutageIsNotMisreportedAsMissingOwnership(HttpStatusCode status)
    {
        using var http = Client(status, "{}");
        await Assert.ThrowsAsync<HttpRequestException>(() => MinecraftLicenseVerifier.VerifyAsync(http, Session(), default));
    }

    [Fact]
    public async Task PermissionFailureDoesNotClaimDefiniteAppOrOwnershipCause()
    {
        using var http = Client(HttpStatusCode.Forbidden, "SECRET-TEST-TOKEN");
        var error = await Assert.ThrowsAsync<AccountVerificationException>(() => MinecraftLicenseVerifier.VerifyAsync(http, Session(), default));
        Assert.DoesNotContain("SECRET-TEST-TOKEN", error.Message);
        Assert.Contains("Minecraft license verification", error.Message);
        Assert.Contains("HTTP 403", error.Message);
        Assert.Contains("account", error.Message);
        Assert.Contains("approval", error.Message);
    }

    private static MSession Session() => new() { Username = "Tester", UUID = Guid.NewGuid().ToString(), AccessToken = "synthetic-token" };
    private static HttpClient Client(HttpStatusCode status, string body) => new(new ResponseHandler(status, body));
    private sealed class ResponseHandler(HttpStatusCode status, string body) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            Assert.Equal("https://api.minecraftservices.com/entitlements/license", request.RequestUri!.AbsoluteUri);
            Assert.Equal("Bearer", request.Headers.Authorization!.Scheme);
            Assert.Equal("synthetic-token", request.Headers.Authorization.Parameter);
            return Task.FromResult(new HttpResponseMessage(status) { Content = new StringContent(body) });
        }
    }
}
