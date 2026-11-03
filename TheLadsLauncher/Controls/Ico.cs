using Avalonia;
using Avalonia.Controls;

namespace TheLadsLauncher.Controls;

/// <summary>
/// <c>c:Ico.Name="refresh"</c> on a Button puts that icon (Styles/Icons.axaml) before its text content, in the label's colour and
/// at its font size (the "Button:ico" style in LauncherTheme.axaml). A Button whose Content is not text builds its own layout with a Glyph.
/// </summary>
public static class Ico
{
    public static readonly AttachedProperty<string?> NameProperty = AvaloniaProperty.RegisterAttached<Button, string?>("Name", typeof(Ico));

    static Ico()
    {
        NameProperty.Changed.AddClassHandler<Button>((button, e) =>
        {
            ((IPseudoClasses)button.Classes).Set(":ico", e.NewValue != null);
            // A ContentPresenter builds nothing from its template without content: an icon-only button gets empty text.
            if (e.NewValue != null && button.Content == null) button.Content = string.Empty;
        });
    }

    public static string? GetName(Button button) => button.GetValue(NameProperty);
    public static void SetName(Button button, string? value) => button.SetValue(NameProperty, value);
}
