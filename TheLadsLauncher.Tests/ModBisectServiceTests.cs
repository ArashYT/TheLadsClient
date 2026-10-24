using System.IO;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ModBisectServiceTests
{
    [Fact]
    public void BisectWorkflow_IdentifiesCulpritCorrectly()
    {
        using var dir = new TestDirectory();
        var modsDir = Path.Combine(dir.Path, "mods");
        Directory.CreateDirectory(modsDir);

        // Create 4 mods: mod1, mod2, mod3, mod4. Let mod3 be the culprit!
        File.WriteAllText(Path.Combine(modsDir, "mod1.jar"), "dummy1");
        File.WriteAllText(Path.Combine(modsDir, "mod2.jar"), "dummy2");
        File.WriteAllText(Path.Combine(modsDir, "mod3.jar"), "dummy3");
        File.WriteAllText(Path.Combine(modsDir, "mod4.jar"), "dummy4");

        // Step 1: Start bisect
        var session = ModBisectService.StartBisect(dir.Path);
        Assert.Equal(BisectState.InProgress, session.State);
        Assert.Equal(4, session.CurrentSuspects.Count);
        Assert.Equal(2, session.CurrentActiveHalf.Count);

        // Active half is mod1 and mod2. mod3 is in the inactive half.
        // Question: Does problem still occur?
        // Since mod3 is the culprit and is NOT active, problem does NOT occur (false).
        session = ModBisectService.RecordStepResult(dir.Path, problemStillOccurs: false);

        // Now suspects should be mod3 and mod4
        Assert.Equal(2, session.CurrentSuspects.Count);
        Assert.Contains("mod3.jar", session.CurrentSuspects);
        Assert.Contains("mod4.jar", session.CurrentSuspects);

        // Next step active half has 1 mod: mod3.jar
        Assert.Single(session.CurrentActiveHalf);
        Assert.Equal("mod3.jar", session.CurrentActiveHalf[0]);

        // Question: Does problem still occur with mod3 active?
        // Yes! (true)
        session = ModBisectService.RecordStepResult(dir.Path, problemStillOccurs: true);

        // Culprit identified!
        Assert.Equal(BisectState.FoundCulprit, session.State);
        Assert.Equal("mod3.jar", session.CulpritFile);

        // Finish bisect, keeping culprit disabled
        ModBisectService.FinishOrCancel(dir.Path, keepCulpritDisabled: true);

        Assert.True(File.Exists(Path.Combine(modsDir, "mod1.jar")));
        Assert.True(File.Exists(Path.Combine(modsDir, "mod2.jar")));
        Assert.True(File.Exists(Path.Combine(modsDir, "mod3.jar.disabled")));
        Assert.True(File.Exists(Path.Combine(modsDir, "mod4.jar")));
        Assert.False(ModBisectService.HasActiveSession(dir.Path));
    }
}
