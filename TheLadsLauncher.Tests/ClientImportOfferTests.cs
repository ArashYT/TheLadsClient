using System;
using System.IO;
using System.Linq;
using System.Xml.Linq;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>1.13.24: the launcher never offers the client import by itself; it opens only from the Import button.</summary>
public class ClientImportOfferTests
{
    private static string Root()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null && !File.Exists(Path.Combine(dir.FullName, "TheLadsLauncher", "MainWindow.axaml"))) dir = dir.Parent;
        Assert.NotNull(dir);
        return Path.Combine(dir!.FullName, "TheLadsLauncher");
    }

    [Fact]
    public void ThereIsNoAutomaticOfferAtStartup()
    {
        string code = File.ReadAllText(Path.Combine(Root(), "MainWindow.ClientImport.cs"));
        Assert.DoesNotContain("Opened +=", code);
        Assert.DoesNotContain("ClientImport.Detect(", code);
        // The window opens from the Import button's click only (its definition and that one call; the command palette is in MainWindow.Productivity).
        Assert.Equal(2, System.Text.RegularExpressions.Regex.Matches(code, @"OpenClientImport\(\)").Count);
    }

    [Fact]
    public void SettingsHasAnImportButtonThatOpensTheImportWindow()
    {
        var doc = XDocument.Load(Path.Combine(Root(), "MainWindow.axaml"));
        var button = doc.Descendants().Single(e => e.Name.LocalName == "Button" && (string?)e.Attribute("Click") == "ClientImport_Click");
        Assert.Contains("Import", (string?)button.Attribute("Content"));
        Assert.Contains("client", (string?)button.Attribute("Content"));
    }
}
