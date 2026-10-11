using System.IO;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>A loader installer that is not the expected build is never kept or run.</summary>
public class InstallerVerificationTests
{
    [Fact]
    public async Task APinnedInstallerWithOtherBytesIsRefused()
    {
        using var http = new HttpClient();
        await Assert.ThrowsAsync<InvalidDataException>(() => ModpackLauncher.VerifyInstallerAsync(http,
            "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-installer.jar",
            "not the installer"u8.ToArray(), CancellationToken.None));
    }
}
