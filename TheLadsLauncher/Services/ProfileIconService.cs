using System;
using System.Collections.Generic;
using System.IO;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public sealed record IconGradient(string Name, string StartColor, string EndColor);

/// <summary>
/// Profile icon customization editor service supporting symbol libraries, icon gradients,
/// randomized designs, and custom image uploads (Lunar client parity).
/// </summary>
public static class ProfileIconService
{
    public static readonly IReadOnlyList<string> BuiltInSymbols = new[]
    {
        "default",
        "sword",
        "pickaxe",
        "axe",
        "shield",
        "bow",
        "compass",
        "heart",
        "star",
        "flame",
        "skull",
        "potion",
        "chest",
        "creeper",
        "ender_eye",
        "stable",
        "nextgen"
    };

    public static readonly IReadOnlyList<IconGradient> PresetGradients = new[]
    {
        new IconGradient("Crimson", "#E0525E", "#8B0000"),
        new IconGradient("Ocean", "#52A5E0", "#1E3A8A"),
        new IconGradient("Emerald", "#50C878", "#14532D"),
        new IconGradient("Amethyst", "#A855F7", "#581C87"),
        new IconGradient("Amber", "#F59E0B", "#78350F"),
        new IconGradient("Obsidian", "#374151", "#111827"),
        new IconGradient("Rose", "#FB7185", "#BE123C"),
        new IconGradient("Cyan", "#06B6D4", "#0E7490")
    };

    private static readonly Random Rnd = new();

    /// <summary>
    /// Randomizes a profile's icon symbol and gradient colors.
    /// </summary>
    public static void Randomize(LauncherProfile profile)
    {
        int symIdx = Rnd.Next(BuiltInSymbols.Count);
        int gradIdx = Rnd.Next(PresetGradients.Count);

        profile.IconKey = BuiltInSymbols[symIdx];
        profile.IconColor = PresetGradients[gradIdx].StartColor;
        profile.IconSecondaryColor = PresetGradients[gradIdx].EndColor;
        profile.CustomIconPath = null;
    }

    /// <summary>
    /// Validates and normalizes hex color strings (e.g. "E0525E" or "#E0525E" -> "#E0525E").
    /// </summary>
    public static bool TryNormalizeHexColor(string? input, out string normalized)
    {
        normalized = "";
        if (string.IsNullOrWhiteSpace(input)) return false;
        string s = input.Trim();
        if (!s.StartsWith("#") && (s.Length == 6 || s.Length == 8) && System.Text.RegularExpressions.Regex.IsMatch(s, "^[0-9A-Fa-f]+$"))
            s = "#" + s;
        if (s.StartsWith("#") && (s.Length == 7 || s.Length == 9) && System.Text.RegularExpressions.Regex.IsMatch(s[1..], "^[0-9A-Fa-f]+$"))
        {
            normalized = s.ToUpperInvariant();
            return true;
        }
        return false;
    }

    /// <summary>
    /// Customizes a profile's icon with a chosen symbol and colors.
    /// </summary>
    public static void SetSymbol(LauncherProfile profile, string symbol, string? primaryColor = null, string? secondaryColor = null)
    {
        profile.IconKey = string.IsNullOrWhiteSpace(symbol) ? "default" : symbol.Trim().ToLowerInvariant();
        profile.IconColor = primaryColor;
        profile.IconSecondaryColor = secondaryColor;
        profile.CustomIconPath = null;
    }

    /// <summary>
    /// Customizes a profile with a local image file (png, webp, jpg).
    /// </summary>
    public static void SetCustomImage(LauncherProfile profile, string imagePath)
    {
        if (!File.Exists(imagePath))
            throw new FileNotFoundException("Custom profile icon image not found.", imagePath);

        string ext = Path.GetExtension(imagePath).ToLowerInvariant();
        if (ext is not (".png" or ".jpg" or ".jpeg" or ".webp" or ".ico"))
            throw new ArgumentException($"Unsupported image format '{ext}'. Must be PNG, WebP, JPG, or ICO.", nameof(imagePath));

        profile.CustomIconPath = imagePath;
        profile.IconKey = "custom";
    }

    /// <summary>
    /// Resets the profile icon to defaults.
    /// </summary>
    public static void ResetToDefault(LauncherProfile profile)
    {
        profile.IconKey = "default";
        profile.IconColor = null;
        profile.IconSecondaryColor = null;
        profile.CustomIconPath = null;
    }

    /// <summary>
    /// Returns the UI Unicode symbol glyph for a given icon key.
    /// </summary>
    public static string GetGlyph(string? iconKey) => (iconKey?.ToLowerInvariant()) switch
    {
        "sword" => "⚔",
        "pickaxe" => "⛏",
        "axe" => "🪓",
        "shield" => "🛡",
        "bow" => "🏹",
        "compass" => "🧭",
        "heart" => "❤",
        "star" => "★",
        "flame" => "🔥",
        "skull" => "💀",
        "potion" => "🧪",
        "chest" => "📦",
        "creeper" => "👾",
        "ender_eye" => "👁",
        "stable" => "💎",
        "nextgen" => "⚡",
        _ => "🎮"
    };
}
