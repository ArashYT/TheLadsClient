using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;

namespace TheLadsLauncher;

public sealed record ReleaseNote(string Version, string Markdown);

public static class ReleaseNotes
{
    /// <summary>Short plain-text highlights; the original Markdown remains available in full.</summary>
    public static IReadOnlyList<string> Highlights(ReleaseNote note)
    {
        var sections = new Dictionary<string, List<string>>(StringComparer.OrdinalIgnoreCase);
        string heading = "";
        bool code = false;
        foreach (string raw in note.Markdown.Replace("\r", "").Split('\n'))
        {
            string line = raw.Trim();
            if (line.StartsWith("```", StringComparison.Ordinal)) { code = !code; continue; }
            if (code) continue;
            if (line.StartsWith("## ", StringComparison.Ordinal)) { heading = line[3..].Trim(); continue; }
            if (!line.StartsWith("- ", StringComparison.Ordinal) && !line.StartsWith("* ", StringComparison.Ordinal)) continue;
            if (!sections.TryGetValue(heading, out var bullets)) sections[heading] = bullets = new();
            string text = Regex.Replace(line[2..], @"\[([^\]]+)\]\([^)]*\)", "$1").Replace("**", "").Replace("`", "");
            text = Regex.Replace(text, @"\s+", " ").Trim();
            int sentence = text.IndexOf(". ", StringComparison.Ordinal);
            if (sentence >= 40 && sentence < 145) text = text[..(sentence + 1)];
            if (text.Length > 145)
            {
                int cut = text.LastIndexOf(' ', 142);
                text = text[..(cut > 65 ? cut : 142)].TrimEnd(',', ';', ':') + "…";
            }
            if (text.Length > 0) bullets.Add(text);
        }
        if (sections.TryGetValue("Highlights", out var highlights) && highlights.Count > 0) return highlights.Take(4).ToArray();
        var result = new List<string>();
        foreach (string category in new[] { "Added", "Changed", "Fixed" })
            if (sections.TryGetValue(category, out var bullets) && bullets.Count > 0) result.Add(bullets[0]);
        foreach (string category in new[] { "", "Added", "Changed", "Fixed" })
            if (sections.TryGetValue(category, out var bullets))
                foreach (string bullet in bullets) if (!result.Contains(bullet)) result.Add(bullet);
        return result.Take(4).ToArray();
    }

    // The complete history travels with the verified package and is readable offline.
    public static IReadOnlyList<ReleaseNote> Read(string directory)
    {
        if (!Directory.Exists(directory)) return Array.Empty<ReleaseNote>();
        return Directory.EnumerateFiles(directory, "*.md")
            .Select(path => (Path: path, Version: Path.GetFileNameWithoutExtension(path)))
            .Where(item => System.Version.TryParse(item.Version, out _))
            .OrderByDescending(item => System.Version.Parse(item.Version))
            .Select(item => new ReleaseNote(item.Version, File.ReadAllText(item.Path))).ToArray();
    }
}
