using System;
using Avalonia;

namespace TheLadsLauncher.Services;

/// <summary>Screen rectangles for the launcher's animated maximize (pixels, like Screen.WorkingArea).</summary>
public static class WindowGeometry
{
    /// <summary>The working area, or with the aspect lock the largest <paramref name="aspect"/> rectangle centred in it.</summary>
    public static PixelRect Maximized(PixelRect workingArea, bool lockAspect, double aspect)
    {
        if (!lockAspect) return workingArea;
        int width = workingArea.Width, height = (int)Math.Round(width / aspect);
        if (height > workingArea.Height) { height = workingArea.Height; width = (int)Math.Round(height * aspect); }
        return new PixelRect(workingArea.X + (workingArea.Width - width) / 2, workingArea.Y + (workingArea.Height - height) / 2, width, height);
    }

    public static PixelRect Lerp(PixelRect from, PixelRect to, double t) => new(
        (int)Math.Round(from.X + (to.X - from.X) * t), (int)Math.Round(from.Y + (to.Y - from.Y) * t),
        (int)Math.Round(from.Width + (to.Width - from.Width) * t), (int)Math.Round(from.Height + (to.Height - from.Height) * t));
}
