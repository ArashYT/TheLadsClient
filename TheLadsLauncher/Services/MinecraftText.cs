using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;

namespace TheLadsLauncher.Services;

/// <summary>A run of Minecraft-formatted text. <see cref="Color"/>: "#RRGGBB", or null for the default text colour.</summary>
public sealed record McTextSpan(string Text, string? Color, bool Bold = false, bool Italic = false, bool Underline = false,
    bool Strikethrough = false, bool Obfuscated = false);

/// <summary>
/// Minecraft's legacy § formatting (MOTDs, server names): §0-§f colours, §k-§o styles, §r reset, and the hex colours of
/// BungeeCord (§x§R§R§G§G§B§B) and some status APIs (§#RRGGBB). As in Java Edition, a colour code also ends any styles.
/// </summary>
public static class MinecraftText
{
    private static readonly string[] Palette =
    {
        "#000000", "#0000AA", "#00AA00", "#00AAAA", "#AA0000", "#AA00AA", "#FFAA00", "#AAAAAA",
        "#555555", "#5555FF", "#55FF55", "#55FFFF", "#FF5555", "#FF55FF", "#FFFF55", "#FFFFFF",
    };

    /// <summary>The text with every formatting code removed.</summary>
    public static string Strip(string text) => string.Concat(Lines(text, trim: false).Select((line, i) => (i > 0 ? "\n" : "") + string.Concat(line.Select(s => s.Text))));

    /// <summary>
    /// The text split into lines of spans (formatting carries over line breaks, as in the game). <paramref name="trim"/>: drop
    /// the spaces servers pad each line with to centre it in the game's list, and blank lines at either end.
    /// </summary>
    public static IReadOnlyList<IReadOnlyList<McTextSpan>> Lines(string text, bool trim = true)
    {
        var lines = new List<List<McTextSpan>> { new() };
        var run = new StringBuilder();
        string? color = null;
        bool bold = false, italic = false, underline = false, strike = false, obfuscated = false;
        void Flush()
        {
            if (run.Length > 0) lines[^1].Add(new McTextSpan(run.ToString(), color, bold, italic, underline, strike, obfuscated));
            run.Clear();
        }
        for (var i = 0; i < text.Length; i++)
        {
            var c = text[i];
            if (c == '\r') continue;
            if (c == '\n') { Flush(); lines.Add(new()); continue; }
            if (c != '§') { run.Append(c); continue; }
            if (i + 1 >= text.Length) break; // a dangling § shows nothing in the game either
            var code = char.ToLowerInvariant(text[++i]);
            string? hex = null;
            if (code == 'x' && i + 12 < text.Length && Enumerable.Range(0, 6).All(k => text[i + 1 + k * 2] == '§' && Uri.IsHexDigit(text[i + 2 + k * 2])))
            {
                hex = "#" + string.Concat(Enumerable.Range(0, 6).Select(k => text[i + 2 + k * 2])).ToUpperInvariant();
                i += 12;
            }
            else if (code == '#' && i + 6 < text.Length && text.Substring(i + 1, 6).All(Uri.IsHexDigit))
            {
                hex = "#" + text.Substring(i + 1, 6).ToUpperInvariant();
                i += 6;
            }
            var index = "0123456789abcdef".IndexOf(code);
            if (hex == null && index < 0 && "klmnor".IndexOf(code) < 0) continue; // unknown code: dropped, as the game does
            Flush();
            if (hex != null || index >= 0 || code == 'r')
            {
                color = hex ?? (index >= 0 ? Palette[index] : null);
                bold = italic = underline = strike = obfuscated = false;
            }
            else if (code == 'k') obfuscated = true;
            else if (code == 'l') bold = true;
            else if (code == 'm') strike = true;
            else if (code == 'n') underline = true;
            else if (code == 'o') italic = true;
        }
        Flush();
        if (!trim) return lines;
        var trimmed = lines.Select(TrimLine).ToList();
        while (trimmed.Count > 0 && trimmed[0].Count == 0) trimmed.RemoveAt(0);
        while (trimmed.Count > 0 && trimmed[^1].Count == 0) trimmed.RemoveAt(trimmed.Count - 1);
        return trimmed;
    }

    private static IReadOnlyList<McTextSpan> TrimLine(List<McTextSpan> line)
    {
        var spans = line.ToList();
        while (spans.Count > 0 && spans[0].Text.TrimStart() is var start && (start.Length == 0 || start != spans[0].Text))
        {
            if (start.Length > 0) { spans[0] = spans[0] with { Text = start }; break; }
            spans.RemoveAt(0);
        }
        while (spans.Count > 0 && spans[^1].Text.TrimEnd() is var end && (end.Length == 0 || end != spans[^1].Text))
        {
            if (end.Length > 0) { spans[^1] = spans[^1] with { Text = end }; break; }
            spans.RemoveAt(spans.Count - 1);
        }
        return spans;
    }

    /// <summary>
    /// The colour lifted so it reads on the launcher's near-black cards: the game shows MOTDs on a mid-grey dirt texture, so
    /// servers happily use black or dark blue there. Anything darker than about 12% luminance is mixed toward white (dark red
    /// and purple already read well enough and keep the server's look).
    /// </summary>
    public static string Legible(string hex)
    {
        if (hex.Length != 7 || hex[0] != '#' || !int.TryParse(hex[1..], NumberStyles.HexNumber, CultureInfo.InvariantCulture, out var rgb)) return hex;
        double r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        var luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
        if (luminance >= 0.12) return hex;
        var mix = 0.12 - luminance + 0.35; // black ends up mid-grey (#787878), dark blue a readable blue (#6C6CCE)
        int Lift(double channel) => (int)Math.Round(channel + (255 - channel) * mix);
        return $"#{Lift(r):X2}{Lift(g):X2}{Lift(b):X2}";
    }
}
