using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;

namespace TheLadsLauncher;

public sealed record ReleaseNote(string Version, string Markdown);

public static class ReleaseNotes
{
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
