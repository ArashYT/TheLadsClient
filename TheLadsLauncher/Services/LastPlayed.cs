using System;
using System.Collections.Generic;

namespace TheLadsLauncher.Services;

/// <summary>Text for the Play page: the hero's "Last played" and the status line of each JUMP IN card.</summary>
public static class LastPlayed
{
    /// <summary>"just now", "5 min ago", "2 h ago", "3 d ago", then the date. A time in the future (clock moved) reads "just now".</summary>
    public static string Relative(DateTime playedUtc, DateTime nowUtc)
    {
        var span = nowUtc - playedUtc;
        if (span.TotalMinutes < 1) return "just now";
        if (span.TotalHours < 1) return $"{(int)span.TotalMinutes} min ago";
        if (span.TotalDays < 1) return $"{(int)span.TotalHours} h ago";
        if (span.TotalDays < 7) return $"{(int)span.TotalDays} d ago";
        return playedUtc.ToLocalTime().ToString("MMM d, yyyy", System.Globalization.CultureInfo.InvariantCulture);
    }

    /// <summary>The hero sub-line part, or null when the profile was never played (the part is hidden).</summary>
    public static string? HeroText(IReadOnlyDictionary<string, DateTime>? played, string? profileId, DateTime nowUtc) =>
        profileId != null && played != null && played.TryGetValue(profileId, out var at) && at > DateTime.MinValue
            ? "Last played " + Relative(at, nowUtc) : null;

    /// <summary>One-line status of an Experience card from data the launcher has: install state, running, pack version, current step.</summary>
    public static string ExperienceLine(bool installed, bool running, bool busy, string? packVersion, string? message)
    {
        if (busy) return string.IsNullOrWhiteSpace(message) ? "Working..." : message.Trim();
        if (running) return "Running";
        if (!installed) return "Not installed";
        return string.IsNullOrWhiteSpace(packVersion) ? "Installed" : "Installed · v" + packVersion.TrimStart('v', 'V');
    }

    public static string HorizonLine(bool installed, bool enabled) =>
        !installed ? (enabled ? "Not installed · installs on Play" : "Not installed")
        : enabled ? "Installed · on" : "Installed · off";

    /// <summary>"Play" once installed, "Install" before; while the button is disabled (busy, game running) the Experiences page's own wording.</summary>
    public static string ButtonLabel(bool installed, bool enabled, string stateText) =>
        enabled ? (installed ? "Play" : "Install") : stateText;
}
