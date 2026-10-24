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
    public void SignalLauncherShownWithoutABridgeDoesNothing()
    {
        UpdateBridge.SignalLauncherShown();
    }
}
