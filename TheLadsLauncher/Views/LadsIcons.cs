using Avalonia.Layout;
using Avalonia.Media;

namespace TheLadsLauncher.Views;

/// <summary>
/// Names of the Flaticon glyphs (Styles/Icons.axaml, tools/icons/icons.json) the Files, Gallery and editor toolbars use, and Glyph() to
/// draw one, so actions render crisply at any scale instead of as emoji.
/// </summary>
public static class LadsIcons
{
    public const string Copy = "copy";
    public const string FolderOpen = "folder-open";
    public const string Folder = "folder";
    public const string Star = "star";
    public const string StarOutline = "star"; // the family has one star: the caller's colour tells favourite from not
    public const string Delete = "trash";
    public const string Refresh = "refresh";
    public const string ArrowUp = "arrow-up";
    public const string ChevronLeft = "angle-left";
    public const string ChevronRight = "angle-right";
    public const string File = "file";
    public const string FileText = "document";
    public const string Image = "picture";
    public const string Archive = "archive";
    public const string Code = "file-code";
    public const string OpenExternal = "external-link";
    public const string Close = "cross-small";
    public const string Search = "search";
    public const string Save = "disk";
    public const string Undo = "undo";
    public const string Wrap = "text-align";
    public const string Fit = "expand";
    public const string Plus = "plus";
    public const string Minus = "minus";
    public const string Home = "home";
    public const string Link = "link";
    public const string Cog = "settings";
    public const string Warning = "triangle-warning";

    /// <summary>The glyph in <paramref name="fill"/>, or in the text colour of whatever it sits in (a button's hover and disabled colours) when none is given.</summary>
    public static Controls.Glyph Glyph(string kind, double size = 16, IBrush? fill = null)
    {
        var glyph = new Controls.Glyph { Kind = kind, Size = size, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        if (fill != null) glyph.Foreground = fill;
        return glyph;
    }
}
