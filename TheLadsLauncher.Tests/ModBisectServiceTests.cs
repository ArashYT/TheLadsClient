using System.IO;
using System.Linq;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ModBisectServiceTests
{
    [Fact]
    public void StartBisect_CreatesTimestampedBackup_AndMovesHalfToDisabledDir()
    {
        using var dir = new TestDirectory();
        var modsDir = Path.Combine(dir.Path, "mods");
        Directory.CreateDirectory(modsDir);

        File.WriteAllText(Path.Combine(modsDir, "mod-a.jar"), "a");
        File.WriteAllText(Path.Combine(modsDir, "mod-b.jar"), "b");
        File.WriteAllText(Path.Combine(modsDir, "mod-c.jar"), "c");
        File.WriteAllText(Path.Combine(modsDir, "mod-d.jar"), "d");
        File.WriteAllText(Path.Combine(modsDir, "unrelated.jar.disabled"), "preexisting disabled");

        var session = ModBisectService.StartBisect(dir.Path);

        // Verify backup folder was created and contains all original files
        Assert.True(Directory.Exists(session.BackupDirectory));
        Assert.True(File.Exists(Path.Combine(session.BackupDirectory, "mod-a.jar")));
        Assert.True(File.Exists(Path.Combine(session.BackupDirectory, "mod-b.jar")));
        Assert.True(File.Exists(Path.Combine(session.BackupDirectory, "mod-c.jar")));
        Assert.True(File.Exists(Path.Combine(session.BackupDirectory, "mod-d.jar")));
        Assert.True(File.Exists(Path.Combine(session.BackupDirectory, "unrelated.jar.disabled")));

        // Preexisting disabled mod must remain in modsDir untouched
        Assert.True(File.Exists(Path.Combine(modsDir, "unrelated.jar.disabled")));

        // Half should be active in modsDir, half in disabledDir
        var disabledDir = ModBisectService.DisabledModsDirectory(dir.Path);
        Assert.True(Directory.Exists(disabledDir));
        int modsRemaining = Directory.GetFiles(modsDir, "*.jar").Length;
        int modsDisabled = Directory.GetFiles(disabledDir, "*.jar").Length;

        Assert.Equal(2, modsRemaining);
        Assert.Equal(2, modsDisabled);
        Assert.Equal(4, session.AllCandidateFiles.Count);
    }

    [Fact]
    public void RecordStepResult_ConvergesToCulpritMod()
    {
        using var dir = new TestDirectory();
        var modsDir = Path.Combine(dir.Path, "mods");
        Directory.CreateDirectory(modsDir);

        for (int i = 1; i <= 4; i++)
        {
            File.WriteAllText(Path.Combine(modsDir, $"mod{i}.jar"), $"content{i}");
        }

        // Suppose mod3.jar is the culprit
        var session = ModBisectService.StartBisect(dir.Path);
        Assert.Equal(BisectState.InProgress, session.State);

        // Step 1: active half is mod1, mod2. Culprit mod3 is in disabled half.
        // Problem does NOT reproduce with active half:
        session = ModBisectService.RecordStepResult(dir.Path, stillReproduces: false);

        // Now suspects should be [mod3.jar, mod4.jar]
        Assert.Contains("mod3.jar", session.CurrentSuspects);
        Assert.Contains("mod4.jar", session.CurrentSuspects);

        // Step 2: active half contains mod3.jar. Problem reproduces!
        session = ModBisectService.RecordStepResult(dir.Path, stillReproduces: true);

        // Culprit found!
        Assert.Equal(BisectState.FoundCulprit, session.State);
        Assert.Equal("mod3.jar", session.CulpritFile);
    }

    [Fact]
    public void FinishOrCancel_RestoresExactStartingSet()
    {
        using var dir = new TestDirectory();
        var modsDir = Path.Combine(dir.Path, "mods");
        Directory.CreateDirectory(modsDir);

        File.WriteAllText(Path.Combine(modsDir, "mod1.jar"), "1");
        File.WriteAllText(Path.Combine(modsDir, "mod2.jar"), "2");
        File.WriteAllText(Path.Combine(modsDir, "mod3.jar"), "3");

        var session = ModBisectService.StartBisect(dir.Path);
        var disabledDir = ModBisectService.DisabledModsDirectory(dir.Path);

        Assert.NotEmpty(Directory.GetFiles(disabledDir));

        // Cancel and restore
        ModBisectService.FinishOrCancel(dir.Path, keepCulpritDisabled: false);

        // All 3 files must be back in modsDir
        Assert.True(File.Exists(Path.Combine(modsDir, "mod1.jar")));
        Assert.True(File.Exists(Path.Combine(modsDir, "mod2.jar")));
        Assert.True(File.Exists(Path.Combine(modsDir, "mod3.jar")));

        // Disabled dir must be empty
        Assert.Empty(Directory.GetFiles(disabledDir));
        Assert.False(ModBisectService.HasActiveSession(dir.Path));
    }
}
