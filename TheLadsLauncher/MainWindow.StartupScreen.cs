// The "Hotbar" startup/update screen. Its layout lives in code only because MainWindow.axaml was claimed by another session
// on 2026-11-20; it moves the SAME named controls into a new tree, so every reference elsewhere keeps working.
// TODO: fold this back into LauncherStartupOverlay in MainWindow.axaml (and drop the logo glow / shine code in MainWindow.axaml.cs).
using System;
using System.IO;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Shapes;
using Avalonia.Layout;
using Avalonia.Media;

namespace TheLadsLauncher;

public partial class MainWindow
{
    private bool _hotbarBuilt;

    private static readonly IBrush HotbarText = new SolidColorBrush(Color.Parse("#F2F3F5"));
    private static readonly IBrush HotbarGround = new SolidColorBrush(Color.Parse("#17181B"));

    private static void Detach(Control control) => (control.Parent as Panel)?.Children.Remove(control);

    private void BuildHotbarStartupScreen()
    {
        if (_hotbarBuilt) return;
        _hotbarBuilt = true;
        const double rowWidth = 600, barHeight = 22;
        var tnum = FontFeatureCollection.Parse("+tnum");

        var track = (Border)StartupProgressFill.Parent!;
        foreach (var c in new Control[] { StartupLogoImage, StartupLoadingSpinner, LoadingDots, StartupPercentText, track, StartupDetailText, StartupMining }) Detach(c);
        var oldStack = LauncherStartupOverlay.Children[0];
        LauncherStartupOverlay.Children.Remove(oldStack);

        StartupLogoImage.Effect = null;
        StartupLogoImage.Width = 440; StartupLogoImage.Height = 100;
        StartupLogoImage.Margin = new Thickness(0, 0, 0, 28);

        StartupMining.Margin = new Thickness(0, 0, 0, 24);

        foreach (var text in new[] { StartupLoadingSpinner, LoadingDots })
        {
            text.FontSize = 22; text.FontWeight = FontWeight.Bold; text.Foreground = HotbarText;
        }
        var title = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center, Children = { StartupLoadingSpinner, LoadingDots } };
        StartupPercentText.FontSize = 22; StartupPercentText.FontWeight = FontWeight.Bold;
        // The XAML Foreground is a DynamicResource (accent red) that re-applies on attach and wins over a plain assignment.
        StartupPercentText.SetValue(TextBlock.ForegroundProperty, HotbarText, Avalonia.Data.BindingPriority.Animation);
        StartupPercentText.FontFeatures = tnum; StartupPercentText.HorizontalAlignment = HorizontalAlignment.Right; StartupPercentText.VerticalAlignment = VerticalAlignment.Center;
        var titleRow = new Grid { Width = rowWidth, Height = 30, ColumnDefinitions = new ColumnDefinitions("*,Auto"), Margin = new Thickness(0, 0, 0, 14), Children = { title, StartupPercentText } };
        Grid.SetColumn(StartupPercentText, 1);

        // Flat track and fill, no border, square corners. The fill's Width is driven by the animation timer against a 560 wide bar, so it is
        // stretched to the 600 wide track from its left edge; the track clips the over-wide 100% fill. The shine stays, invisible.
        track.Width = rowWidth; track.Height = barHeight; track.CornerRadius = default; track.BorderThickness = default;
        track.Background = new SolidColorBrush(Color.Parse("#26282F")); track.BorderBrush = null; track.ClipToBounds = true;
        StartupProgressFill.CornerRadius = default;
        StartupProgressFill.Background = new SolidColorBrush(Color.Parse("#E0525B"));
        StartupProgressFill.RenderTransformOrigin = new RelativePoint(0, 0.5, RelativeUnit.Relative);
        StartupProgressFill.RenderTransform = new ScaleTransform(rowWidth / 560.0, 1);
        StartupProgressShine.Opacity = 0; StartupProgressShine.IsHitTestVisible = false;

        // 19 gaps of 4 px in the ground colour over the track + fill: 20 equal blocks, a partly filled block shows naturally.
        var gaps = new Grid { IsHitTestVisible = false };
        for (int i = 0; i < 20; i++)
        {
            gaps.ColumnDefinitions.Add(new ColumnDefinition(1, GridUnitType.Star));
            if (i == 19) break;
            gaps.ColumnDefinitions.Add(new ColumnDefinition(4, GridUnitType.Pixel));
            var gap = new Rectangle { Fill = HotbarGround };
            Grid.SetColumn(gap, i * 2 + 1);
            gaps.Children.Add(gap);
        }
        var bar = new Grid { Width = rowWidth, Height = barHeight, Margin = new Thickness(0, 0, 0, 14), Children = { track, gaps } };

        StartupDetailText.Width = double.NaN; StartupDetailText.Height = double.NaN; StartupDetailText.Margin = default;
        StartupDetailText.FontSize = 18; StartupDetailText.Foreground = HotbarText; StartupDetailText.FontFeatures = tnum;
        StartupDetailText.VerticalAlignment = VerticalAlignment.Center; StartupDetailText.TextTrimming = TextTrimming.CharacterEllipsis;
        StartupEtaText = new TextBlock { FontSize = 18, Foreground = new SolidColorBrush(Color.Parse("#8E909B")), FontFeatures = tnum, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(16, 0, 0, 0) };
        var detailRow = new Grid { Width = rowWidth, Height = 26, ColumnDefinitions = new ColumnDefinitions("*,Auto"), Children = { StartupDetailText, StartupEtaText } };
        Grid.SetColumn(StartupEtaText, 1);

        LauncherStartupOverlay.Children.Add(new StackPanel
        {
            VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center,
            Children = { StartupLogoImage, StartupMining, titleRow, bar, detailRow },
        });
    }

    /// <summary>Time left on the startup screen, right of the detail line; empty when there is no estimate.</summary>
    internal TextBlock StartupEtaText { get; private set; } = new();

    /// <summary>QA only: LADS_QA_SPLASH_SHOT=&lt;dir&gt; (together with --preview-startup and the sandbox env, which supplies the fake download)
    /// saves built-loading.png and built-downloading.png (at 72%) from the real window, then exits.</summary>
    private async Task RunSplashShotsAsync(string dir)
    {
        Directory.CreateDirectory(dir);
        ShowStartupStatus("");
        await Task.Delay(250);
        SaveWindowScreenshot(System.IO.Path.Combine(dir, "built-loading.png"));
        for (int i = 0; i < 400 && StartupPercentText.Text != "72%"; i++) await Task.Delay(50);
        SaveWindowScreenshot(System.IO.Path.Combine(dir, "built-downloading.png"));
        Environment.Exit(0);
    }
}
