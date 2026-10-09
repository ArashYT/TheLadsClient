using System;
using System.Collections.Generic;
using System.IO;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class ModSearchMatcherTests : IDisposable
{
    private readonly ModSandbox _box = new();

    public void Dispose() => _box.Dispose();

    [Theory]
    [InlineData("Fabric API", "fabricapi")]
    [InlineData("Reese's Sodium Options", "reesessodiumoptions")]
    [InlineData("3D Skin Layers", "3dskinlayers")]
    [InlineData("YetAnotherConfigLib", "yetanotherconfiglib")]
    [InlineData("Xaero's Minimap", "xaerosminimap")]
    [InlineData("sodium-extra", "sodiumextra")]
    [InlineData(null, "")]
    [InlineData("", "")]
    public void NormalizeKeyStripsNonAlphanumericAndLowercases(string? input, string expected)
    {
        Assert.Equal(expected, ModSearchMatcher.NormalizeKey(input));
    }

    [Fact]
    public void MatchesInventoryEntryByProjectId()
    {
        var item = new ModSearchItem { Id = "AANobbMI", Name = "Sodium", ProjectSlug = "sodium" };
        var entry = CreateEntry("sodium", "Sodium", projectId: "AANobbMI");

        Assert.True(ModSearchMatcher.MatchesInventoryEntry(item, entry));
    }

    [Fact]
    public void MatchesInventoryEntryByModIdAndSlug()
    {
        var item = new ModSearchItem { Id = "random123", Name = "Iris Shaders", ProjectSlug = "iris" };
        var entry = CreateEntry("iris", "Iris Shaders");

        Assert.True(ModSearchMatcher.MatchesInventoryEntry(item, entry));
    }

    [Fact]
    public void MatchesInventoryEntryByNormalizedName()
    {
        var item = new ModSearchItem { Id = "random456", Name = "Fabric API", ProjectSlug = "fabric-api" };
        var entry = CreateEntry("fabric-api", "Fabric API");

        Assert.True(ModSearchMatcher.MatchesInventoryEntry(item, entry));
    }

    [Fact]
    public void MatchesInventoryEntryByProjectUrl()
    {
        var item = new ModSearchItem { Id = "random789", Name = "Sodium", ProjectSlug = "sodium" };
        var entry = CreateEntry("different-id", "Sodium", projectUrl: "https://modrinth.com/mod/sodium");

        Assert.True(ModSearchMatcher.MatchesInventoryEntry(item, entry));
    }

    [Fact]
    public void MatchesInventoryEntryByProvides()
    {
        var item = new ModSearchItem { Id = "indium123", Name = "Indium", ProjectSlug = "indium" };
        var entry = CreateEntry("sodium", "Sodium", provides: new[] { "indium" });

        Assert.True(ModSearchMatcher.MatchesInventoryEntry(item, entry));
    }

    [Fact]
    public void DoesNotMatchDifferentModWithSimilarName()
    {
        // "Sodium Extra" should NOT match "Sodium"
        var sodiumExtra = new ModSearchItem { Id = "PtjYWJkn", Name = "Sodium Extra", ProjectSlug = "sodium-extra" };
        var sodium = CreateEntry("sodium", "Sodium", projectId: "AANobbMI");

        Assert.False(ModSearchMatcher.MatchesInventoryEntry(sodiumExtra, sodium));
    }

    [Fact]
    public void DoesNotMatchUnavailableOrNotDownloadedEntry()
    {
        var item = new ModSearchItem { Id = "AANobbMI", Name = "Sodium", ProjectSlug = "sodium" };
        var unavailable = CreateEntry("sodium", "Sodium", projectId: "AANobbMI", status: ModEntryStatus.Unavailable);
        var notDownloaded = CreateEntry("sodium", "Sodium", projectId: "AANobbMI", status: ModEntryStatus.NotDownloaded);

        Assert.False(ModSearchMatcher.MatchesInventoryEntry(item, unavailable));
        Assert.False(ModSearchMatcher.MatchesInventoryEntry(item, notDownloaded));
    }

    [Fact]
    public void MatchesManifestEntry()
    {
        var item = new ModSearchItem { Id = "zV5r3pPn", Name = "3D Skin Layers", ProjectSlug = "3dskinlayers" };
        var manifestEntry = new ClientModInstaller.Entry(
            ProjectId: "zV5r3pPn",
            ProjectSlug: "3dskinlayers",
            Name: "3D Skin Layers",
            ModId: "skinlayers3d",
            VersionId: "G8BCRxv5",
            Version: "1.11.3",
            FileName: "skinlayers3d-fabric-1.11.3-mc26.3.jar",
            Url: "https://example.com",
            Sha512: "abc",
            Size: 100,
            License: "MIT",
            SourceUrl: null,
            ProjectUrl: "https://modrinth.com/mod/3dskinlayers");

        Assert.True(ModSearchMatcher.MatchesManifestEntry(item, manifestEntry));
    }

    [Fact]
    public void MatchesJarFileOnDisk()
    {
        var item = new ModSearchItem { Id = "AANobbMI", Name = "Sodium", ProjectSlug = "sodium" };
        string jarPath = Path.Combine(_box.Game, "mods", "sodium-fabric-0.9.2+mc26.3.jar");

        Assert.True(ModSearchMatcher.MatchesJarFile(item, jarPath));

        // Sodium Extra should not match Sodium jar
        var sodiumExtra = new ModSearchItem { Id = "PtjYWJkn", Name = "Sodium Extra", ProjectSlug = "sodium-extra" };
        Assert.False(ModSearchMatcher.MatchesJarFile(sodiumExtra, jarPath));

        // But should match sodium-extra jar
        string sodiumExtraJar = Path.Combine(_box.Game, "mods", "sodium-extra-fabric-0.9.4+mc26.3.jar");
        Assert.True(ModSearchMatcher.MatchesJarFile(sodiumExtra, sodiumExtraJar));
    }

    [Fact]
    public void MatchesResourcePackInSharedFolder()
    {
        string rpDir = Path.Combine(_box.Root, "resourcepacks");
        Directory.CreateDirectory(rpDir);
        File.WriteAllBytes(Path.Combine(rpDir, "Faithful_32x-1.21.zip"), new byte[] { 1, 2, 3 });

        var item = new ModSearchItem { Name = "Faithful 32x", ProjectSlug = "faithful-32x" };
        Assert.True(ModSearchMatcher.MatchesResourcePack(item, rpDir));

        var uninstalled = new ModSearchItem { Name = "Bare Bones", ProjectSlug = "bare-bones" };
        Assert.False(ModSearchMatcher.MatchesResourcePack(uninstalled, rpDir));
    }

    private static ModInventoryEntry CreateEntry(string id, string displayName, string? projectId = null,
        string? projectUrl = null, IReadOnlyList<string>? provides = null, ModEntryStatus status = ModEntryStatus.Installed)
    {
        return new ModInventoryEntry(
            Id: id,
            DisplayName: displayName,
            UpstreamName: displayName,
            Version: "1.0.0",
            FileName: id + "-1.0.0.jar",
            FilePath: "C:\\mock\\" + id + "-1.0.0.jar",
            Ownership: ModOwnership.Pack,
            Status: status,
            EnabledOnDisk: true,
            RequestedEnabled: true,
            LoadedNow: null,
            RestartRequired: false,
            CanToggle: true,
            ToggleBlockedReason: null,
            ProjectId: projectId,
            ProjectUrl: projectUrl,
            License: null,
            Authors: Array.Empty<string>(),
            Depends: Array.Empty<string>(),
            Provides: provides ?? Array.Empty<string>(),
            IsLibrary: false,
            Note: null,
            ParentId: null,
            Children: Array.Empty<ModInventoryEntry>());
    }
}
