using System.Collections.Generic;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Layout;
using Avalonia.Media;

namespace TheLadsLauncher.Views;

public sealed class ReleaseNotesWindow : Window
{
    public ReleaseNotesWindow(IReadOnlyList<ReleaseNote> notes, string currentVersion)
    {
        Title = "The Lads Client — Release notes";
        Width = 720;
        Height = 560;
        MinWidth = 420;
        MinHeight = 320;
        WindowStartupLocation = WindowStartupLocation.CenterOwner;
        Background = new SolidColorBrush(Color.Parse("#121218"));
        var content = new StackPanel { Spacing = 16, Margin = new Thickness(24) };
        content.Children.Add(new TextBlock { Text = $"Release notes · installed v{currentVersion}", FontSize = 22, FontWeight = FontWeight.Bold, Foreground = Brushes.White });
        foreach (var note in notes)
        {
            content.Children.Add(new TextBlock { Text = $"v{note.Version}", FontSize = 18, FontWeight = FontWeight.Bold, Foreground = new SolidColorBrush(Color.Parse("#FF6666")) });
            // Plain text keeps bundled Markdown readable without executing HTML or links.
            content.Children.Add(new SelectableTextBlock { Text = note.Markdown, TextWrapping = TextWrapping.Wrap, FontSize = 14, Foreground = new SolidColorBrush(Color.Parse("#DDDDDD")) });
        }
        if (notes.Count == 0) content.Children.Add(new TextBlock { Text = "No bundled release notes were found.", Foreground = Brushes.White });
        var close = new Button { Content = "Done", HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(20) };
        close.Click += (_, _) => Close();
        var panel = new DockPanel();
        DockPanel.SetDock(close, Dock.Bottom);
        panel.Children.Add(close);
        panel.Children.Add(new ScrollViewer { Content = content });
        Content = panel;
    }
}
