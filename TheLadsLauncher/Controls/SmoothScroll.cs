using System;
using System.Linq;
using System.Runtime.CompilerServices;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.VisualTree;

namespace TheLadsLauncher.Controls;

/// <summary>
/// Eased mouse-wheel scrolling for every ScrollViewer in the launcher (lists, pages, popups): one class handler on each window, no
/// per-control setup. A wheel notch moves a target offset and the view eases toward it; notches in quick succession add up, so a spin
/// of the wheel glides instead of jumping. A touchpad sends many small pixel deltas: those are applied at once (no easing, so no lag)
/// and cancel any glide in progress. Anything that sets the offset itself (ScrollToHome, a drag, keys) also cancels the glide.
/// </summary>
public static class SmoothScroll
{
    /// <summary>Pixels one wheel notch moves.</summary>
    public const double NotchPixels = 64;
    /// <summary>Time constant of the ease-out: about 90% of a notch is done after 2.3 times this.</summary>
    public const double TimeConstantSeconds = 0.09;

    private static bool _installed;
    private static readonly ConditionalWeakTable<ScrollViewer, Glide> Glides = new();

    /// <summary>Call once at startup, before the first window.</summary>
    public static void Install()
    {
        if (_installed) return;
        _installed = true;
        InputElement.PointerWheelChangedEvent.AddClassHandler<TopLevel>(OnWheel, RoutingStrategies.Tunnel);
    }

    /// <summary>A notch of a wheel is a delta of 1; a touchpad (or a high-resolution wheel) sends fractions of it.</summary>
    public static bool IsPrecise(Vector delta) => Math.Abs(delta.X) < 1 && Math.Abs(delta.Y) < 1;

    /// <summary>One frame of the ease: the offset moves toward the target by 1 - e^(-dt/tau) of the way, so it is frame-rate independent.</summary>
    public static double Step(double current, double target, double seconds)
    {
        double next = current + (target - current) * (1 - Math.Exp(-Math.Max(0, seconds) / TimeConstantSeconds));
        return Math.Abs(target - next) < 0.5 ? target : next;
    }

    private sealed class Glide
    {
        public Vector Target;
        public Vector LastSet;
        public bool Running;
        public TimeSpan LastFrame;
    }

    private static void OnWheel(TopLevel top, PointerWheelEventArgs e)
    {
        if (e.Handled || e.KeyModifiers.HasFlag(KeyModifiers.Control) || e.Source is not Visual source) return;
        var delta = e.Delta;
        if (e.KeyModifiers.HasFlag(KeyModifiers.Shift) && delta.X == 0) delta = new Vector(delta.Y, 0);
        if (delta == default) return;

        foreach (var control in source.GetSelfAndVisualAncestors())
        {
            // These use the wheel themselves (zoom, value, selection): leave them alone.
            if (control is SkinModel3D or Slider or NumericUpDown or ComboBox) return;
            if (control is not ScrollViewer viewer) continue;
            var step = new Vector(-delta.X * NotchPixels, -delta.Y * NotchPixels);
            if (!CanMove(viewer, step)) continue;
            Scroll(viewer, step, IsPrecise(delta));
            e.Handled = true;
            return;
        }
    }

    private static Vector Range(ScrollViewer viewer) =>
        new(Math.Max(0, viewer.Extent.Width - viewer.Viewport.Width), Math.Max(0, viewer.Extent.Height - viewer.Viewport.Height));

    private static Vector Clamp(Vector v, Vector max) => new(Math.Clamp(v.X, 0, max.X), Math.Clamp(v.Y, 0, max.Y));

    private static bool CanMove(ScrollViewer viewer, Vector step)
    {
        var max = Range(viewer);
        var from = Glides.TryGetValue(viewer, out var glide) && glide.Running ? glide.Target : viewer.Offset;
        bool x = step.X != 0 && viewer.HorizontalScrollBarVisibility != ScrollBarVisibility.Disabled
            && (step.X < 0 ? from.X > 0.5 : from.X < max.X - 0.5);
        bool y = step.Y != 0 && viewer.VerticalScrollBarVisibility != ScrollBarVisibility.Disabled
            && (step.Y < 0 ? from.Y > 0.5 : from.Y < max.Y - 0.5);
        return x || y;
    }

    private static void Scroll(ScrollViewer viewer, Vector step, bool precise)
    {
        var glide = Glides.GetOrCreateValue(viewer);
        var max = Range(viewer);
        if (precise)
        {
            glide.Running = false;
            viewer.Offset = Clamp(viewer.Offset + step, max);
            return;
        }
        glide.Target = Clamp((glide.Running ? glide.Target : viewer.Offset) + step, max);
        if (glide.Running) return;
        glide.Running = true;
        glide.LastSet = viewer.Offset;
        glide.LastFrame = TimeSpan.Zero;
        Frame(viewer, glide, TimeSpan.Zero);
    }

    private static void Frame(ScrollViewer viewer, Glide glide, TimeSpan now)
    {
        if (!glide.Running) return;
        var current = viewer.Offset;
        // Something else moved the view (ScrollToHome, a scrollbar drag, a key): its position wins, the glide ends.
        if (Math.Abs(current.X - glide.LastSet.X) > 1.5 || Math.Abs(current.Y - glide.LastSet.Y) > 1.5) { glide.Running = false; return; }
        double seconds = glide.LastFrame == TimeSpan.Zero ? 1.0 / 60 : (now - glide.LastFrame).TotalSeconds;
        glide.LastFrame = now == TimeSpan.Zero ? glide.LastFrame : now;
        var max = Range(viewer);
        glide.Target = Clamp(glide.Target, max);
        var next = new Vector(Step(current.X, glide.Target.X, seconds), Step(current.Y, glide.Target.Y, seconds));
        viewer.Offset = next;
        glide.LastSet = viewer.Offset; // the viewer may clamp what it was given
        if (glide.LastSet == glide.Target || glide.LastSet == current && next != current) { glide.Running = false; return; }
        if (TopLevel.GetTopLevel(viewer) is not { } top) { glide.Running = false; return; }
        top.RequestAnimationFrame(t => Frame(viewer, glide, t));
    }
}
