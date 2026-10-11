using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ModLoadingRaceTests
{
    // Heads of the real crash reports from the 1.9.x laptop research runs (t1, I1, D1).
    private static string Fixture(string name) => File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "Fixtures", "CrashRace", name));

    [Theory]
    [InlineData("registrate-chromatic-compound.txt")]
    [InlineData("registrate-potato-cannon.txt")]
    [InlineData("zeta-subscribe.txt")]
    [InlineData("zeta-find.txt")] // Arash-PC int QA: the same Zeta race seen in Object2ObjectOpenHashMap.find, no rehash frame
    public void TheLaptopRacesAreKnown(string file) => Assert.NotNull(ModLoadingRace.Match(Fixture(file)));

    [Fact]
    public void OtherCrashesAreLeftAlone()
    {
        // The same lost entry while playing is not a loading race.
        Assert.Null(ModLoadingRace.Match(Fixture("registrate-potato-cannon.txt").Replace("Description: Initializing game", "Description: Ticking entity")));
        // A loading crash without either signature (a mixin failure, say).
        Assert.Null(ModLoadingRace.Match("Description: Initializing game\n\norg.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError: An unexpected critical error"));
        Assert.Null(ModLoadingRace.Match(""));
    }

    [Fact]
    public void OnlyACrashReportFromThisLaunchCounts()
    {
        using var dir = new TestDirectory();
        Assert.Null(ModLoadingRace.FindSince(dir.Path, DateTime.UtcNow)); // no crash-reports folder

        var reports = Directory.CreateDirectory(Path.Combine(dir.Path, "crash-reports")).FullName;
        string old = Path.Combine(reports, "crash-2026-10-11_02.44.51-fml.txt");
        File.WriteAllText(old, Fixture("zeta-subscribe.txt"));
        File.SetLastWriteTimeUtc(old, DateTime.UtcNow.AddHours(-1));
        var launched = DateTime.UtcNow.AddMinutes(-1);
        Assert.Null(ModLoadingRace.FindSince(dir.Path, launched)); // an older launch's race

        string fresh = Path.Combine(reports, "crash-2026-10-11_06.09.26-client.txt");
        File.WriteAllText(fresh, Fixture("registrate-potato-cannon.txt"));
        Assert.Contains("crash-2026-10-11_06.09.26-client.txt", ModLoadingRace.FindSince(dir.Path, launched));

        // This launch's newest report decides: a later ordinary crash is not retried.
        string later = Path.Combine(reports, "crash-2026-10-11_06.10.00-client.txt");
        File.WriteAllText(later, "Description: Initializing game\n\njava.lang.OutOfMemoryError: Java heap space");
        File.SetLastWriteTimeUtc(later, DateTime.UtcNow.AddSeconds(5));
        Assert.Null(ModLoadingRace.FindSince(dir.Path, launched));
    }
}
