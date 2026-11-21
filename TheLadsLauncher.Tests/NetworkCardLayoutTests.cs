using System;
using System.IO;
using System.Linq;
using System.Xml.Linq;
using Xunit;

namespace TheLadsLauncher.Tests;

public class NetworkCardLayoutTests
{
    private static string FindRepoRoot()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null && !File.Exists(Path.Combine(dir.FullName, "TheLadsLauncher", "MainWindow.axaml")))
            dir = dir.Parent;
        Assert.NotNull(dir);
        return dir!.FullName;
    }

    [Fact]
    public void PlayPageHeroLaunchKeepsLaunchControlsAndGhostNetworkJoin()
    {
        string axamlPath = Path.Combine(FindRepoRoot(), "TheLadsLauncher", "MainWindow.axaml");
        var doc = XDocument.Load(axamlPath);
        XNamespace x = "http://schemas.microsoft.com/winfx/2006/xaml";
        System.Xml.Linq.XElement Named(string n) => doc.Descendants().Single(e => (string?)e.Attribute(x + "Name") == n);

        // Hero uses the artwork with a left-side scrim; Play, pickers, status, progress and network status are inside the Play panel.
        var panel = Named("PlayStandardPanel");
        var artworkBrush = panel.Descendants().Single(e => e.Name.LocalName == "ImageBrush"
            && ((string?)e.Attribute("Source"))?.EndsWith("/Assets/artwork.jpg", StringComparison.Ordinal) == true);
        Assert.Equal("UniformToFill", (string?)artworkBrush.Attribute("Stretch"));
        foreach (var n in new[] { "LaunchButton", "LaunchProfileSelector", "LaunchAccountSelector", "StatusText", "ProgressBar", "NetworkStatusText", "NetworkStatusDot", "NetworkPlayButton", "HeroTitle" })
            Assert.Contains(Named(n), panel.Descendants());

        Assert.Equal("56", (string?)Named("LaunchButton").Attribute("Height"));
        Assert.Equal("NetworkPlay_Click", (string?)Named("NetworkPlayButton").Attribute("Click"));
        Assert.Equal("LaunchButton_Click", (string?)Named("LaunchButton").Attribute("Click"));

        // Only one filled (launch) button on the Play panel; Network Join is ghost.
        var filled = panel.Descendants().Where(e => e.Name.LocalName == "Button"
            && (((string?)e.Attribute("Classes")) ?? "").Split(' ').Contains("launch")).ToList();
        Assert.Single(filled);
        Assert.Same(Named("LaunchButton"), filled[0]);
        Assert.Equal("Transparent", (string?)Named("NetworkPlayButton").Attribute("Background"));

        // Old banner is gone.
        Assert.DoesNotContain("BUILT FOR YOUR GAME", File.ReadAllText(axamlPath));
    }

    [Fact]
    public void PlayPageCardsUseRealHandlersAndTheTopBarIsLean()
    {
        var doc = XDocument.Load(Path.Combine(FindRepoRoot(), "TheLadsLauncher", "MainWindow.axaml"));
        XNamespace x = "http://schemas.microsoft.com/winfx/2006/xaml";
        System.Xml.Linq.XElement Named(string n) => doc.Descendants().Single(e => (string?)e.Attribute(x + "Name") == n);

        Assert.Equal("HomeLadsSmp_Click", (string?)Named("HomeLadsSmpBtn").Attribute("Click"));
        Assert.Equal("HomeZeroSmp_Click", (string?)Named("HomeZeroSmpBtn").Attribute("Click"));
        Assert.Equal("HomeHorizon_Click", (string?)Named("HomeHorizonBtn").Attribute("Click"));
        Assert.Equal("Open", (string?)Named("HomeHorizonBtn").Attribute("Content"));
        foreach (var n in new[] { "HomeLadsSmpStatus", "HomeZeroSmpStatus", "HomeHorizonStatus", "HeroLastPlayedText" }) Assert.NotNull(Named(n));

        // Network and Horizon cards use image assets, not glyphs.
        var network = Named("HomeNetworkCard");
        Assert.Contains(network.Descendants(), e => e.Name.LocalName == "Image" && ((string?)e.Attribute("Source"))!.EndsWith("/Assets/logo.png"));
        Assert.Contains(Named("HomeHorizonCard").Descendants(), e => e.Name.LocalName == "Image" && ((string?)e.Attribute("Source"))!.EndsWith("/Assets/lads_horizon_icon.png"));

        // Window size controls moved from the top bar into Settings.
        var settingsCard = Named("AspectLockBtn").Ancestors().Any(a => ((string?)a.Attribute(x + "Name") is "TitleBar" or "ContentTitleBar"));
        Assert.False(settingsCard);
        Assert.False(Named("ResetSizeBtn").Ancestors().Any(a => ((string?)a.Attribute(x + "Name") is "TitleBar" or "ContentTitleBar")));
    }
}
