using TheLadsLauncher.Services;
using Xunit;

[Collection(TimingSensitive.Name)]
public sealed class DownloadStallTests
{
    private static readonly TimeSpan Idle = TimeSpan.FromSeconds(3);

    [Fact] public async Task SlowDownloadCompletesButAStalledOneTimesOut()
    {
        // 40 bytes 100 ms apart: 4 s in total, longer than the idle limit, but never idle for more than a fraction of it.
        using var slow = new TrickleStream(new byte[40], stallAt: 40, TimeSpan.FromMilliseconds(100));
        using var output = new MemoryStream();
        Assert.Equal(40, await ClientModInstaller.CopyWithStallTimeoutAsync(slow, output, 40, Idle, "Slow", default));
        Assert.Equal(40, output.Length);

        using var stalled = new TrickleStream(new byte[30], stallAt: 5, TimeSpan.FromMilliseconds(10));
        var failure = await Assert.ThrowsAsync<TimeoutException>(() =>
            ClientModInstaller.CopyWithStallTimeoutAsync(stalled, Stream.Null, 30, Idle, "Stalled", default));
        Assert.Contains("Stalled", failure.Message);

        // A user cancellation stays a cancellation, never a timeout.
        using var cancel = new CancellationTokenSource(TimeSpan.FromMilliseconds(100));
        using var waiting = new TrickleStream(new byte[30], stallAt: 0, TimeSpan.Zero);
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() =>
            ClientModInstaller.CopyWithStallTimeoutAsync(waiting, Stream.Null, 30, Idle, "Cancelled", cancel.Token));
    }

    // The client's own timeout ends the wait here; the installer's 30 s limits follow the same path.
    [Fact] public async Task AHungOptiFineServerEndsInAWarningNotAFailedLaunch()
    {
        using var dir = new TheLadsLauncher.Tests.TestDirectory();
        using var client = new HttpClient(new Hanging()) { Timeout = Idle };
        var clock = System.Diagnostics.Stopwatch.StartNew();
        var warning = await OptiFineInstaller.InstallAsync(Path.Combine(dir.Path, "launcher"), Path.Combine(dir.Path, "game"), httpClient: client);
        Assert.Contains("did not answer in time", warning);
        Assert.Contains("Minecraft starts without it", warning);
        Assert.InRange(clock.Elapsed, TimeSpan.Zero, TimeSpan.FromSeconds(30));
    }

    private sealed class Hanging : HttpMessageHandler
    {
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            await Task.Delay(Timeout.InfiniteTimeSpan, token);
            throw new InvalidOperationException("unreachable");
        }
    }

    /// <summary>Returns one byte per read after <c>delay</c>; from byte <c>stallAt</c> on it never answers (until cancelled).</summary>
    private sealed class TrickleStream(byte[] data, int stallAt, TimeSpan delay) : MemoryStream(data)
    {
        public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken token = default)
        {
            if (Position == Length) return 0;
            await Task.Delay(Position >= stallAt ? Timeout.InfiniteTimeSpan : delay, token);
            return await base.ReadAsync(buffer[..1], token);
        }
    }
}
