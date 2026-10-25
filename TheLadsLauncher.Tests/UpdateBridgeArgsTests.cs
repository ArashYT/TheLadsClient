using System.Globalization;
using Avalonia;
using Avalonia.Controls;
using TheLadsLauncher;
using Xunit;

public class UpdateBridgeArgsTests
{
    private sealed class CultureScope : IDisposable
    {
        private readonly CultureInfo _culture = CultureInfo.CurrentCulture;
        public CultureScope(string name) => CultureInfo.CurrentCulture = new CultureInfo(name);
        public void Dispose() => CultureInfo.CurrentCulture = _culture;
    }

    [Fact]
    public void RestoreWindowArgsRoundTripUnderGermanCulture()
    {
        using var _ = new CultureScope("de-DE");
        var text = UpdateBridge.FormatRestoreWindow(new PixelPoint(-1920, 40), 1280.5, 720.25, WindowState.Maximized);
        Assert.True(UpdateBridge.TryParseRestoreWindow(new[] { "x", "--restore-window", text }, out var p, out var w, out var h, out var s));
        Assert.Equal((new PixelPoint(-1920, 40), 1280.5, 720.25, WindowState.Maximized), (p, w, h, s));
    }

    [Theory]
    [InlineData(null)]   // no --restore-window at all
    [InlineData("")]     // --restore-window is the last arg
    [InlineData("1,2,3")]
    [InlineData("a,b,c,d,Normal")]
    [InlineData("1,2,0,720,Normal")]
    [InlineData("1,2,1280,720,42")]
    public void MissingOrBrokenRestoreWindowIsIgnored(string? value)
    {
        string[] args = value == null ? new[] { "x" } : value == "" ? new[] { "x", "--restore-window" } : new[] { "x", "--restore-window", value };
        Assert.False(UpdateBridge.TryParseRestoreWindow(args, out _, out _, out _, out _));
    }

    [Fact]
    public void LauncherShownWaitsForTheFirstFrameWithAFallback()
    {
        int signals = 0;
        var shown = new LauncherShownSignal(() => signals++);
        shown.StartupFinished();            // a fast or failed startup check before any frame: the bridge must keep covering
        Assert.Equal(0, signals);
        shown.FramePresented();
        Assert.Equal(1, signals);
        Assert.False(shown.Fallback());     // a frame came: the fallback stays quiet
        Assert.Equal(1, signals);
        shown.StartupFinished();            // after the first frame: dismisses a bridge left by a failed startup install
        Assert.Equal(2, signals);

        var stalled = new LauncherShownSignal(() => signals++);
        Assert.True(stalled.Fallback());    // no frame within 2 s: signal anyway
        Assert.Equal(3, signals);
        stalled.StartupFinished();
        Assert.Equal(4, signals);
    }

    [Fact]
    public void ShownCompletesWithTheFirstFrameOrTheFallback()
    {
        var presented = new LauncherShownSignal(() => { });
        presented.StartupFinished();
        Assert.False(presented.Shown.IsCompleted);   // not before the first frame
        presented.FramePresented();
        Assert.True(presented.Shown.IsCompleted);

        var stalled = new LauncherShownSignal(() => { });
        Assert.False(stalled.Shown.IsCompleted);
        Assert.True(stalled.Fallback());
        Assert.True(stalled.Shown.IsCompleted);
    }

    [Fact]
    public void SignalLauncherShownWithoutABridgeDoesNothing()
    {
        UpdateBridge.SignalLauncherShown();
    }

    [Theory]
    [InlineData(1.0)]
    [InlineData(1.25)]
    [InlineData(1.5)]
    [InlineData(1.75)]   // 900 / 1.75 * 1.75 is 900.0000000001: rounding up made the snapshot 901 px
    public void SnapshotSizeMatchesTheWindowsClientPixels(double scale)
    {
        foreach (var (w, h) in new[] { (900, 921), (1259, 709), (1280, 720), (1920, 1009) })
        {
            var clientDips = new Avalonia.Size(w / scale, h / scale);   // what Avalonia reports as ClientSize for a w x h px client area
            var png = UpdateBridge.ToScreenPixels(default, new Rect(clientDips), scale).Size;
            var client = UpdateBridge.ToScreenPixels(new PixelPoint(-1920, 40), new Rect(clientDips), scale);
            Assert.Equal(new PixelSize(w, h), png);
            Assert.Equal(new PixelRect(-1920, 40, w, h), client);
        }
    }
}
