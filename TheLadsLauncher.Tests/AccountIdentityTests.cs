using System;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class AccountIdentityTests
{
    [Theory]
    [InlineData("Steve", "Steve")]
    [InlineData(" Alex ", "Alex")]
    [InlineData("Player_123", "Player_123")]
    [InlineData("ValidName16Chars", "ValidName16Chars")]
    public void NormalizeOfflineName_ValidNames_Succeeds(string input, string expected)
    {
        string normalized = AccountIdentity.NormalizeOfflineName(input);
        Assert.Equal(expected, normalized);
    }

    [Theory]
    [InlineData("ab")] // too short
    [InlineData("toolongname1234567")] // too long (>16)
    [InlineData("Player!")] // invalid character
    [InlineData("Player-1")] // hyphens not allowed
    [InlineData("User Name")] // spaces not allowed inside name
    [InlineData("")]
    [InlineData("   ")]
    public void NormalizeOfflineName_InvalidNames_ThrowsArgumentException(string input)
    {
        Assert.Throws<ArgumentException>(() => AccountIdentity.NormalizeOfflineName(input));
    }

    [Fact]
    public void OfflineUuid_GeneratesDeterministicV3Uuid()
    {
        string uuid1 = AccountIdentity.OfflineUuid("Steve");
        string uuid2 = AccountIdentity.OfflineUuid(" Steve ");
        Assert.Equal(uuid1, uuid2);
        Assert.True(Guid.TryParse(uuid1, out var parsed));
        Assert.NotEqual(Guid.Empty, parsed);
    }
}
