using System;
using System.IO;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ExperiencesServiceTests : IDisposable
{
    private readonly string _tempDir;

    public ExperiencesServiceTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), "thelads_exp_test_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        try { Directory.Delete(_tempDir, true); } catch { }
    }

    [Fact]
    public void ZeroSmp_Metadata_Is_Valid()
    {
        var exp = ExperiencesService.ZeroSmp;
        Assert.Equal("zero-smp-1.20.1", exp.Id);
        Assert.Equal("Zero SMP", exp.Title);
        Assert.Equal("1.20.1", exp.McVersion);
        Assert.Equal("forge", exp.Loader);
        Assert.Equal("47.4.10", exp.LoaderVersion);
        Assert.Equal("nikzerosmp.my.pebble.host", exp.ServerAddress);
        Assert.True(exp.ModCount > 100);
        Assert.True(exp.RecommendedRamMb >= 4096);
        Assert.Contains(exp, ExperiencesService.AllExperiences);
    }

    [Fact]
    public void InstanceDirectory_CalculatesCorrectPath()
    {
        var path = ExperiencesService.InstanceDirectory(_tempDir, "zero-smp-1.20.1");
        var expected = Path.Combine(_tempDir, "instances", "zero-smp-1.20.1");
        Assert.Equal(expected, path);
    }

    [Fact]
    public void IsInstalled_ReturnsFalse_WhenNotInstalled()
    {
        Assert.False(ExperiencesService.IsInstalled(_tempDir, "zero-smp-1.20.1"));
    }

    [Fact]
    public void IsInstalled_ReturnsTrue_WhenValidInstanceExists()
    {
        var instDir = ExperiencesService.InstanceDirectory(_tempDir, "zero-smp-1.20.1");
        var modsDir = Path.Combine(instDir, "minecraft", "mods");
        Directory.CreateDirectory(modsDir);
        File.WriteAllText(Path.Combine(instDir, "instance.json"), "{}");
        File.WriteAllText(Path.Combine(modsDir, "testmod.jar"), "dummy");

        Assert.True(ExperiencesService.IsInstalled(_tempDir, "zero-smp-1.20.1"));
    }

    [Fact]
    public void CheckAndInjectLadsCore_DoesNotThrow_WhenNoModPresent()
    {
        var instDir = ExperiencesService.InstanceDirectory(_tempDir, "zero-smp-1.20.1");
        var modsDir = Path.Combine(instDir, "minecraft", "mods");
        Directory.CreateDirectory(modsDir);

        ExperiencesService.CheckAndInjectLadsCore(_tempDir, "zero-smp-1.20.1");
        Assert.Empty(Directory.EnumerateFiles(modsDir));
    }
}
