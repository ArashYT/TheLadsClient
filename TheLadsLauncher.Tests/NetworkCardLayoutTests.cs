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
    public void NetworkCardUsesSeparateFixedImageAndActionColumnsWithoutOverlap()
    {
        string axamlPath = Path.Combine(FindRepoRoot(), "TheLadsLauncher", "MainWindow.axaml");
        var doc = XDocument.Load(axamlPath);
        XNamespace x = "http://schemas.microsoft.com/winfx/2006/xaml";

        var playBtn = doc.Descendants().Single(e => (string?)e.Attribute(x + "Name") == "NetworkPlayButton");
        var statusText = doc.Descendants().Single(e => (string?)e.Attribute(x + "Name") == "NetworkStatusText");
        var statusDot = doc.Descendants().Single(e => (string?)e.Attribute(x + "Name") == "NetworkStatusDot");
        var particleCanvas = doc.Descendants().Single(e => (string?)e.Attribute(x + "Name") == "ParticleCanvas");
        var artworkBrush = doc.Descendants().Single(e =>
            e.Name.LocalName == "ImageBrush" &&
            ((string?)e.Attribute("Source"))?.EndsWith("/Assets/artwork.jpg", StringComparison.Ordinal) == true);

        var artworkBorder = artworkBrush.Ancestors().First(e => e.Name.LocalName == "Border");
        var bannerGrid = artworkBorder.Parent;
        Assert.NotNull(bannerGrid);
        Assert.Equal("Grid", bannerGrid!.Name.LocalName);

        // Compact banner: title column grows, the artwork column has a fixed width, so nothing overlaps at the smallest window.
        var cols = bannerGrid.Element(bannerGrid.Name.Namespace + "Grid.ColumnDefinitions")!.Elements().ToList();
        Assert.Equal(3, cols.Count);
        Assert.Equal(new[] { "*", "196", "Auto" }, cols.Select(c => (string?)c.Attribute("Width")).ToArray());
        int titleMin = int.Parse((string?)cols[0].Attribute("MinWidth")!);
        int imageColWidth = int.Parse((string?)cols[1].Attribute("Width")!);
        Assert.Equal("140", (string?)artworkBorder.Ancestors().First(e => e.Name.LocalName == "Border").Attribute("Height")); // banner stays compact at the top
        Assert.InRange(imageColWidth, 180, 260);
        Assert.InRange(titleMin, 240, 400); // "THE LADS CLIENT." at 30 px fits in 250
        Assert.Equal("Center", (string?)artworkBrush.Attribute("AlignmentX"));
        Assert.Equal("Top", (string?)artworkBrush.Attribute("AlignmentY")); // her face stays in view when the banner is wide or tall

        // Nothing else in the banner grid may share Grid.Column="1" or span across it over artwork.jpg.
        var childrenInArtColumn = bannerGrid.Elements()
            .Where(el =>
            {
                int col = int.TryParse((string?)el.Attribute("Grid.Column"), out int c) ? c : 0;
                int span = int.TryParse((string?)el.Attribute("Grid.ColumnSpan"), out int s) ? s : 1;
                return col <= 1 && col + span > 1;
            })
            .ToList();
        Assert.Single(childrenInArtColumn);
        Assert.Same(artworkBorder, childrenInArtColumn[0]);
        Assert.Null(particleCanvas.Attribute("Grid.ColumnSpan"));

        // Action column sits to the right of the artwork column with vertically centred button + status line beneath it.
        var actionHost = playBtn.Ancestors().First(e => e.Parent == bannerGrid);
        Assert.Equal("2", (string?)actionHost.Attribute("Grid.Column"));
        Assert.Contains(statusDot, actionHost.Descendants());
        Assert.Contains(statusText, actionHost.Descendants());

        var actionStack = playBtn.Parent;
        Assert.NotNull(actionStack);
        Assert.Equal("Center", (string?)actionStack!.Attribute("VerticalAlignment"));

        // Verify minimum-window-width budget (960 window - 184 sidebar - 56 page margin - 2 border = 718 inner px).
        int minBtnWidth = int.TryParse((string?)playBtn.Attribute("MinWidth"), out int bw) ? bw : 200;
        int remainingHeroWidth = 718 - imageColWidth - (minBtnWidth + 28);
        Assert.True(remainingHeroWidth >= titleMin, $"Remaining hero title width at 960px window too tight: {remainingHeroWidth}px");
    }
}
