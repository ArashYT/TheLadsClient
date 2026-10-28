using TheLadsLauncher.Views;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ModpacksBusyTests
{
    [Theory]
    [InlineData(0, 0, false, 0, false)]
    [InlineData(1, 0, false, 0, true)]
    [InlineData(0, 1, false, 0, true)]
    [InlineData(0, 0, true, 0, true)]
    [InlineData(0, 0, false, 1, true)]
    [InlineData(2, 1, true, 3, true)]
    public void ModpacksView_CalculateIsBusy_ReflectsInstallingAndOtherActivities(
        int launching, int busy, bool importing, int installing, bool expected)
    {
        Assert.Equal(expected, ModpacksView.CalculateIsBusy(launching, busy, importing, installing));
    }
}
