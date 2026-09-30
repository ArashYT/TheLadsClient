using TheLadsLauncher.Services;
using Xunit;

public sealed class DownloadStallTests
{
    private static readonly TimeSpan Idle = TimeSpan.FromSeconds(1);

    [Fact] public async Task SlowDownloadCompletesButAStalledOneTimesOut()
    {
        // 30 bytes 40 ms apart: longer in total than the idle limit, but never idle for it.
        using var slow = new TrickleStream(new byte[30], stallAt: 30, TimeSpan.FromMilliseconds(40));
        using var output = new MemoryStream();
        Assert.Equal(30, await ClientModInstaller.CopyWithStallTimeoutAsync(slow, output, 30, Idle, "Slow", default));
        Assert.Equal(30, output.Length);

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
