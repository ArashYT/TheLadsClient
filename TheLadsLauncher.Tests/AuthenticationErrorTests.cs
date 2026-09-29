using System.Net;
using CmlLib.Core.Auth.Microsoft;
using Microsoft.Identity.Client;
using TheLadsLauncher.Services;
using XboxAuthNet.OAuth;
using XboxAuthNet.XboxLive;
using Xunit;

namespace TheLadsLauncher.Tests;

public class AuthenticationErrorTests
{
    private const string Secret = "SECRET-TEST-TOKEN-AND-USER-ID";

    [Theory]
    [InlineData(401)]
    [InlineData(403)]
    public void MinecraftStageAndStatusAreVisibleWithoutResponseFields(int status)
    {
        var failure = new JEAuthException(Secret, Secret, Secret, status);
        string description = MicrosoftAccountService.DescribeError(new Exception(Secret, failure));
        Assert.Contains("Minecraft Services authentication/profile", description);
        Assert.Contains($"HTTP {status}", description);
        Assert.Contains("does not identify the cause", description);
        Assert.DoesNotContain(Secret, description);
    }

    [Theory]
    [InlineData("2148916233")]
    [InlineData("0x8015DC09")]
    public void XboxNumericCodeIsReportedWithoutMessagesOrRedirects(string errorCode)
    {
        var failure = new XboxAuthException(errorCode, Secret, "https://example.com/" + Secret, 401);
        string description = MicrosoftAccountService.DescribeError(failure);
        Assert.Contains("Xbox Live/XSTS", description);
        Assert.Contains("HTTP 401", description);
        Assert.Contains("XErr 0x8015DC09", description);
        Assert.DoesNotContain(Secret, description);
        Assert.DoesNotContain("https://", description);
    }

    [Theory]
    [InlineData(Secret)]
    [InlineData("0xABCSECRET")]
    [InlineData("12345678901234567890")]
    public void XboxUnstructuredCodesAreNotEchoed(string errorCode)
    {
        string description = MicrosoftAccountService.DescribeError(new XboxAuthException(errorCode, Secret, Secret, 403));
        Assert.Contains("HTTP 403", description);
        Assert.DoesNotContain("XErr", description);
        Assert.DoesNotContain(errorCode, description);
        Assert.DoesNotContain(Secret, description);
    }

    [Fact]
    public void UnknownMicrosoftErrorDoesNotEchoAnArbitraryErrorCode()
    {
        string description = MicrosoftAccountService.DescribeError(new MsalServiceException(Secret, Secret, 503));
        Assert.Contains("Microsoft sign-in", description);
        Assert.Contains("HTTP 503", description);
        Assert.DoesNotContain(Secret, description);
    }

    [Fact]
    public void LegacyOAuthErrorOnlyReportsStageAndHttpStatus()
    {
        var failure = new MicrosoftOAuthException(Secret, Secret, new[] { 50076 }, 400);
        string description = MicrosoftAccountService.DescribeError(failure);
        Assert.Contains("Microsoft OAuth", description);
        Assert.Contains("HTTP 400", description);
        Assert.DoesNotContain(Secret, description);
    }

    [Fact]
    public void HttpFailuresReportStatusWithoutClaimingAnExactService()
    {
        string description = MicrosoftAccountService.DescribeError(new HttpRequestException(Secret, null, HttpStatusCode.ServiceUnavailable));
        Assert.Contains("Microsoft or Minecraft services", description);
        Assert.Contains("HTTP 503", description);
        Assert.DoesNotContain(Secret, description);
    }

    [Fact]
    public void MissingOrInvalidStructuredStatusDoesNotExposeMessageText()
    {
        string description = MicrosoftAccountService.DescribeError(new JEAuthException(Secret, Secret, Secret, 9999));
        Assert.Contains("Minecraft Services", description);
        Assert.DoesNotContain("HTTP", description);
        Assert.DoesNotContain(Secret, description);
    }
}
