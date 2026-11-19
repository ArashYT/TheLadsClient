using TheLadsLauncher;
using Xunit;

public class StartupProgressTests
{
    private const double Frame = 1 / 60.0;

    [Fact]
    public void StartsAtZero()
    {
        var progress = new StartupProgress();
        Assert.Equal(0, progress.Value);
        Assert.True(progress.Step(Frame, null, false) < 0.02);
    }

    [Fact]
    public void RealProgressThatStartsAt70PercentStillMovesSmoothly()
    {
        var progress = new StartupProgress();
        double last = 0, biggest = 0;
        for (int i = 0; i < 600; i++)
        {
            double v = progress.Step(Frame, 0.7, false);
            Assert.True(v >= last);
            biggest = Math.Max(biggest, v - last);
            last = v;
            if (i == 0) Assert.True(v < 0.05, "the first frame must not jump to 70%");
        }
        Assert.True(biggest < 0.03, $"largest step {biggest}");
        Assert.InRange(last, 0.6, 0.95);   // reaches 90% of the real 70%, then only creeps
    }

    [Fact]
    public void MonotonicWithNoJumpAcrossADownloadAndCompletion()
    {
        var progress = new StartupProgress();
        double last = 0, biggest = 0;
        for (int i = 0; i < 60 * 12 && !progress.Finished; i++)
        {
            double? real = i < 60 ? null : Math.Min(1, (i - 60) / 360.0);   // nothing, then a 6 s download
            double v = progress.Step(Frame, real, complete: i >= 60 * 9);
            Assert.True(v >= last, $"went backwards at frame {i}");
            biggest = Math.Max(biggest, v - last);
            last = v;
        }
        Assert.True(biggest < 0.03, $"largest step {biggest}");
        Assert.Equal(1, progress.Value);
    }

    [Fact]
    public void SlowsDownNearNinetyPercent()
    {
        double Speed(double from, double to)
        {
            var progress = new StartupProgress();
            double v = 0;
            while (v < from) v = progress.Step(Frame, 1, false);
            double t = 0;
            while (v < to && t < 120) { v = progress.Step(Frame, 1, false); t += Frame; }
            return (to - from) / t;
        }
        Assert.True(Speed(0.84, 0.88) < Speed(0.4, 0.44) * 0.5, "the bar must be clearly slower around 90% than in the middle");
    }

    [Fact]
    public void KeepsCreepingWhenRealProgressStallsButNeverFinishesOnItsOwn()
    {
        var progress = new StartupProgress();
        for (int i = 0; i < 60 * 3; i++) progress.Step(Frame, 0.5, false);
        double before = progress.Value;
        for (int i = 0; i < 60 * 10; i++) progress.Step(Frame, 0.5, false);
        Assert.True(progress.Value > before + 0.01, "still creeping after the stall");
        for (int i = 0; i < 60 * 600; i++) progress.Step(Frame, 1, false);
        Assert.True(progress.Value < 1 && StartupProgress.Percent(progress.Value) < 100);
    }

    [Fact]
    public void CompletionReachesExactlyOneSmoothlyFromAnywhere()
    {
        foreach (double start in new[] { 0.0, 0.3, 0.9, 0.99 })
        {
            var progress = new StartupProgress();
            while (progress.Value < start) progress.Step(Frame, 1, false);
            double last = progress.Value, biggest = 0, seconds = 0;
            while (!progress.Finished && seconds < 2)
            {
                double v = progress.Step(Frame, 1, true);
                Assert.True(v >= last);
                biggest = Math.Max(biggest, v - last);
                last = v;
                seconds += Frame;
            }
            Assert.Equal(1, progress.Value);
            Assert.True(seconds <= StartupProgress.FinishSeconds + 2 * Frame, $"finish took {seconds} s");
            Assert.True(biggest < 0.07, $"largest finishing step {biggest} from {start}");
        }
    }

    [Fact]
    public void ALongUiStallCannotMakeAJump()
    {
        var progress = new StartupProgress();
        Assert.True(progress.Step(5, 0.7, false) < 0.1);
    }

    [Fact]
    public void PercentMatchesTheBar()
    {
        Assert.Equal(0, StartupProgress.Percent(0));
        Assert.Equal(42, StartupProgress.Percent(0.4299));
        Assert.Equal(99, StartupProgress.Percent(0.9999));
        Assert.Equal(100, StartupProgress.Percent(1));
    }

    [Fact]
    public void ALauncherStartedAfterAnUpdateBeginsAtFullAndStaysThere()
    {
        var progress = new StartupProgress(1);
        Assert.Equal(1, progress.Step(Frame, null, false));
        Assert.True(progress.Finished);
    }
}
