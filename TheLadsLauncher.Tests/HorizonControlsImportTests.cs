using System.IO;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class HorizonControlsImportTests
{
    [Fact]
    public void FirstStartCopiesSharedKeyBindsIntoHorizonOptions()
    {
        var dir = Path.Combine(Path.GetTempPath(), "hzimp-" + Path.GetRandomFileName());
        Directory.CreateDirectory(dir);
        try
        {
            var shared = Path.Combine(dir, "shared.txt");
            File.WriteAllText(shared, "key_key.jump:key.keyboard.j\nfov:0.5\n");
            var home = Path.Combine(dir, "home");
            Directory.CreateDirectory(home);
            HorizonEngine.ImportControlsOnce(home, shared);
            var text = File.ReadAllText(Path.Combine(home, "options.txt"));
            Assert.Contains("key_key.jump:key.keyboard.j", text);
            Assert.StartsWith("version:", text);
            Assert.True(File.Exists(Path.Combine(home, HorizonEngine.ControlsMarker)));
        }
        finally { Directory.Delete(dir, true); }
    }
}
