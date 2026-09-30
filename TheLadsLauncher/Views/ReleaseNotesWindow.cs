using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.RegularExpressions;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Documents;
using Avalonia.Layout;
using Avalonia.Media;

namespace TheLadsLauncher.Views;

public sealed class ReleaseNotesWindow : Window
{
    public Expander Details { get; }
    public Expander History { get; }
    public IReadOnlyList<string> VisibleHighlights { get; }

    public ReleaseNotesWindow(IReadOnlyList<ReleaseNote> notes, string currentVersion)
    {
        Title = "The Lads Client — What's new";
        Width = 660;
        Height = 620;
        MinWidth = 420;
        MinHeight = 360;
        WindowStartupLocation = WindowStartupLocation.CenterOwner;
        Background = Brush("#101115");
        var selected = notes.FirstOrDefault(n => n.Version == currentVersion) ?? notes.FirstOrDefault();
        VisibleHighlights = selected == null ? new string[0] : ReleaseNotes.Highlights(selected);
        var header = new StackPanel { Spacing = 10, Margin = new Thickness(30, 28, 30, 20) };
        header.Children.Add(new TextBlock { Text = "THE LADS CLIENT", FontSize = 11, LetterSpacing = 2, FontWeight = FontWeight.SemiBold, Foreground = Brush("#ED727B") });
        var title = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        title.Children.Add(new TextBlock { Text = "What's new", FontSize = 30, FontWeight = FontWeight.Bold, Foreground = Brushes.White });
        var badge = new Border { Background = Brush("#302027"), CornerRadius = new CornerRadius(6), Padding = new Thickness(10, 5), VerticalAlignment = VerticalAlignment.Center,
            Child = new TextBlock { Text = "v" + (selected?.Version ?? currentVersion), Foreground = Brush("#F5B8BE"), FontSize = 12, FontWeight = FontWeight.SemiBold } };
        Grid.SetColumn(badge, 1);
        title.Children.Add(badge);
        header.Children.Add(title);
        header.Children.Add(new TextBlock { Text = "A few things to try next time you play.", FontSize = 14, Foreground = Brush("#9B9EA9") });
        var content = new StackPanel { Spacing = 10, Margin = new Thickness(30, 0, 30, 20) };
        foreach (string highlight in VisibleHighlights)
        {
            var row = new Grid { ColumnDefinitions = new ColumnDefinitions("18,*") };
            row.Children.Add(new TextBlock { Text = "•", Foreground = Brush("#F17C85"), FontSize = 18 });
            var label = new TextBlock { Text = highlight, TextWrapping = TextWrapping.Wrap, FontSize = 14, LineHeight = 22, Foreground = Brush("#E8E9ED") };
            Grid.SetColumn(label, 1);
            row.Children.Add(label);
            content.Children.Add(new Border { Background = Brush("#1A1B21"), CornerRadius = new CornerRadius(8), Padding = new Thickness(15, 12), Child = row });
        }
        if (VisibleHighlights.Count == 0)
            content.Children.Add(new TextBlock { Text = selected == null ? "You're up to date. Release notes will appear here with your next update." : "This update includes improvements across the client. Read the full notes below.", TextWrapping = TextWrapping.Wrap, Foreground = Brush("#B5B8C1"), Margin = new Thickness(0, 10) });
        Details = new Expander { Header = "Full release notes", IsExpanded = false, HorizontalAlignment = HorizontalAlignment.Stretch, Foreground = Brush("#ADB0BB"), Margin = new Thickness(0, 10, 0, 0),
            Content = FullText(selected?.Markdown ?? "No bundled notes for this release.", skipHighlights: VisibleHighlights.Count > 0) };
        content.Children.Add(Details);
        var previous = new StackPanel { Spacing = 8 };
        foreach (var note in notes.Where(n => n != selected))
            previous.Children.Add(new Expander { Header = "Version " + note.Version, IsExpanded = false, HorizontalAlignment = HorizontalAlignment.Stretch, Content = FullText(note.Markdown) });
        History = new Expander { Header = "Previous updates", IsExpanded = false, HorizontalAlignment = HorizontalAlignment.Stretch, Foreground = Brush("#ADB0BB"), Content = previous, IsVisible = previous.Children.Count > 0 };
        content.Children.Add(History);
        var done = new Button { Content = "Let's play", HorizontalAlignment = HorizontalAlignment.Right, HorizontalContentAlignment = HorizontalAlignment.Center,
            Background = Brush("#AB2637"), Foreground = Brushes.White, FontWeight = FontWeight.SemiBold, Padding = new Thickness(24, 10), CornerRadius = new CornerRadius(6) };
        done.Click += (_, _) => Close();
        var footer = new Border { BorderBrush = Brush("#272931"), BorderThickness = new Thickness(0, 1, 0, 0), Padding = new Thickness(30, 16), Child = done };
        var panel = new DockPanel();
        DockPanel.SetDock(header, Dock.Top);
        DockPanel.SetDock(footer, Dock.Bottom);
        panel.Children.Add(header);
        panel.Children.Add(footer);
        panel.Children.Add(new ScrollViewer { Content = content, HorizontalScrollBarVisibility = Avalonia.Controls.Primitives.ScrollBarVisibility.Disabled });
        Content = panel;
    }

    private static SolidColorBrush Brush(string color) => new(Color.Parse(color));

    /// <summary>
    /// Renders the notes' Markdown subset: "# title" (the version is already shown), "## sections", "- bullets", **bold**, `code`
    /// and [links](url). skipHighlights drops the Highlights section already shown as cards above.
    /// </summary>
    private static StackPanel FullText(string markdown, bool skipHighlights = false)
    {
        var panel = new StackPanel { Spacing = 4, Margin = new Thickness(0, 10, 0, 12) };
        bool skipping = false;
        foreach (string raw in markdown.Replace("\r", "").Split('\n'))
        {
            string line = raw.Trim();
            if (line.StartsWith("## ", StringComparison.Ordinal))
            {
                string heading = line[3..].Trim();
                skipping = skipHighlights && heading.Equals("Highlights", StringComparison.OrdinalIgnoreCase);
                if (!skipping)
                    panel.Children.Add(new TextBlock { Text = heading, FontSize = 14, FontWeight = FontWeight.Bold, Foreground = Brush("#F5B8BE"), Margin = new Thickness(0, panel.Children.Count == 0 ? 0 : 12, 0, 2) });
                continue;
            }
            if (skipping || line.Length == 0 || line.StartsWith("# ", StringComparison.Ordinal)) continue;
            bool bullet = line.StartsWith("- ", StringComparison.Ordinal) || line.StartsWith("* ", StringComparison.Ordinal);
            var text = Formatted(bullet ? line[2..] : line);
            if (!bullet) { panel.Children.Add(text); continue; }
            var row = new Grid { ColumnDefinitions = new ColumnDefinitions("16,*") };
            row.Children.Add(new TextBlock { Text = "•", Foreground = Brush("#F17C85"), FontSize = 13, LineHeight = 21 });
            Grid.SetColumn(text, 1);
            row.Children.Add(text);
            panel.Children.Add(row);
        }
        if (panel.Children.Count == 0)
            panel.Children.Add(new TextBlock { Text = "Everything new in this update is listed above.", Foreground = Brush("#9B9EA9"), FontSize = 13 });
        return panel;
    }

    private static SelectableTextBlock Formatted(string text)
    {
        var block = new SelectableTextBlock { TextWrapping = TextWrapping.Wrap, FontSize = 13, LineHeight = 21, Foreground = Brush("#C3C5CD") };
        var inlines = block.Inlines ??= new InlineCollection();
        foreach (Match part in Regex.Matches(text, @"\*\*(.+?)\*\*|`(.+?)`|\[(.+?)\]\((.+?)\)|[^*`\[]+|."))
        {
            if (part.Groups[1].Success) inlines.Add(new Run(part.Groups[1].Value) { FontWeight = FontWeight.SemiBold, Foreground = Brush("#F1F2F5") });
            else if (part.Groups[2].Success) inlines.Add(new Run(part.Groups[2].Value) { FontFamily = new FontFamily("Cascadia Mono, Consolas, monospace"), Foreground = Brush("#E6C07B") });
            else if (part.Groups[3].Success) inlines.Add(new Run(part.Groups[3].Value) { TextDecorations = TextDecorations.Underline });
            else inlines.Add(new Run(part.Value));
        }
        return block;
    }
}
