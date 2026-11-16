using Avalonia;
using TheLadsLauncher.Controls;
using Xunit;

namespace TheLadsLauncher.Tests;

public class SmoothScrollTests
{
    [Fact]
    public void AWheelNotchIsEasedAndATouchpadDeltaIsNot()
    {
        Assert.False(SmoothScroll.IsPrecise(new Vector(0, 1)));     // a full notch: eased
        Assert.False(SmoothScroll.IsPrecise(new Vector(0, -3)));    // a fast spin
        Assert.True(SmoothScroll.IsPrecise(new Vector(0, 0.12)));   // a touchpad pixel delta: applied at once, no lag
        Assert.True(SmoothScroll.IsPrecise(new Vector(0.3, -0.4)));
    }

    [Fact]
    public void TheEaseMovesTowardTheTargetFasterAtFirstAndArrivesExactly()
    {
        double at = 0;
        double first = SmoothScroll.Step(at, 100, 1.0 / 60);
        Assert.InRange(first, 10, 30);                              // a quarter of a second is mostly done, a frame is a fraction
        double second = SmoothScroll.Step(first, 100, 1.0 / 60);
        Assert.True(second - first < first - at);                   // ease-out: each frame moves less than the last
        for (int i = 0; i < 120; i++) at = SmoothScroll.Step(at, 100, 1.0 / 60);
        Assert.Equal(100, at);                                      // snaps to the target, never creeps forever
    }

    [Fact]
    public void TheEaseDoesNotDependOnTheFrameRate()
    {
        double fast = 0, slow = 0;
        for (int i = 0; i < 30; i++) fast = SmoothScroll.Step(fast, 200, 1.0 / 120);
        for (int i = 0; i < 15; i++) slow = SmoothScroll.Step(slow, 200, 1.0 / 60);
        Assert.InRange(Math.Abs(fast - slow), 0, 1);
        Assert.Equal(50, SmoothScroll.Step(50, 200, 0) , 6);        // no time, no movement
    }
}
