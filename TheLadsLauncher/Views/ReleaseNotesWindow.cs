using System.Collections.Generic;
using System.Linq;
using Avalonia;
using Avalonia.Controls;
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
            Content = FullText(selected?.Markdown ?? "No bundled notes for this release.") };
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
    private static SelectableTextBlock FullText(string text) => new()
    {
        Text = text, TextWrapping = TextWrapping.Wrap, FontSize = 13, LineHeight = 21, Foreground = Brush("#C3C5CD"), Margin = new Thickness(0, 12, 0, 12)
    };
}
