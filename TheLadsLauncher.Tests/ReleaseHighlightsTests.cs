using TheLadsLauncher;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class ReleaseHighlightsTests
{
    [Fact]
    public void UsesFourCuratedHighlightsAndKeepsFullNotesIntact()
    {
        const string text = "# 1.3.4\n## Highlights\n- **Gallery** scans your instances.\n- HUD grouping is easier.\n- [Details](https://example.com) are optional.\n- Controls keep their place.\n- Extra fifth item.\n## Fixed\n- Full detailed note.\n";
        var note = new ReleaseNote("1.3.4", text);
        Assert.Equal(new[] { "Gallery scans your instances.", "HUD grouping is easier.", "Details are optional.", "Controls keep their place." }, ReleaseNotes.Highlights(note));
        Assert.Equal(text, note.Markdown);
    }

    [Fact]
    public void OlderNotesBalanceChangeTypesInsteadOfDumpingHistory()
    {
        var note = new ReleaseNote("1.3.3", "## Added\n- Feature A\n- Feature B\n- Feature C\n## Changed\n- Better menus\n## Fixed\n- Important fix\n## Verification\n- Technical diagnostics\n");
        Assert.Equal(new[] { "Feature A", "Better menus", "Important fix", "Feature B" }, ReleaseNotes.Highlights(note));
    }

    [Fact]
    public void CapsVerboseBulletsAndSkipsCodeWithoutExecutingMarkup()
    {
        var longText = string.Join(" ", Enumerable.Repeat("detail", 80));
        var note = new ReleaseNote("1.3.3", "## Added\n```\n- hidden\n```\n- " + longText);
        string item = Assert.Single(ReleaseNotes.Highlights(note));
        Assert.True(item.Length <= 145);
        Assert.EndsWith("…", item);
        Assert.DoesNotContain("hidden", item);
    }
}
