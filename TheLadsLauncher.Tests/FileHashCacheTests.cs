using System;
using System.IO;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class FileHashCacheTests
{
    [Fact]
    public void StoredHashIsReturnedUntilTheFileChangesAndIsSavedToTheGameFolder()
    {
        var game = Path.Combine(Path.GetTempPath(), "lads-hashcache-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(Path.Combine(game, "mods"));
        try
        {
            var jar = Path.Combine(game, "mods", "a.jar");
            File.WriteAllText(jar, "one");
            var info = new FileInfo(jar);
            Assert.Null(FileHashCache.Get(game, "sha512", info));
            FileHashCache.Put(game, "sha512", info, "abc");
            Assert.Equal("abc", FileHashCache.Get(game, "sha512", new FileInfo(jar)));
            Assert.Null(FileHashCache.Get(game, "sha256", new FileInfo(jar))); // per algorithm

            FileHashCache.Save(game);
            var saved = File.ReadAllText(Path.Combine(game, FileHashCache.FileName));
            Assert.Contains("abc", saved);

            File.WriteAllText(jar, "changed!");
            File.SetLastWriteTimeUtc(jar, DateTime.UtcNow.AddMinutes(1));
            Assert.Null(FileHashCache.Get(game, "sha512", new FileInfo(jar)));

            File.Delete(jar);
            var other = Path.Combine(game, "mods", "b.jar");
            File.WriteAllText(other, "two");
            FileHashCache.Put(game, "sha512", new FileInfo(other), "x"); // marks the folder changed
            FileHashCache.Save(game);
            Assert.DoesNotContain("a.jar", File.ReadAllText(Path.Combine(game, FileHashCache.FileName))); // gone files are dropped
        }
        finally { Directory.Delete(game, true); }
    }
}
