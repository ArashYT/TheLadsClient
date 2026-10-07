using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Numerics;
using System.Runtime.InteropServices;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Platform;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Controls;

/// <summary>
/// The Skins tab's interactive 3D player: drag to turn, wheel to zoom, double-click to reset. The outer skin layer is drawn
/// as small cubes (like the 3D Skin Layers mod) or, with <see cref="Layers3D"/> off, as vanilla's flat inflated boxes.
/// Rendering is a software rasterizer (Services/SkinRasterizer) into a bitmap at the control's device-pixel size, so the
/// pixel art stays crisp at any DPI. Frames are only requested while something moves and the control is on screen.
/// </summary>
public sealed class SkinModel3D : Control
{
    private const float MinPitch = -55, MaxPitch = 80, MinZoom = 0.6f, MaxZoom = 2.6f;
    private static readonly Cursor GrabCursor = new(StandardCursorType.Hand), DragCursor = new(StandardCursorType.SizeAll);
    private static readonly IBrush ShadowBrush = new RadialGradientBrush
    {
        GradientStops = { new GradientStop(Color.FromArgb(120, 0, 0, 0), 0), new GradientStop(Color.FromArgb(60, 0, 0, 0), 0.55), new GradientStop(Color.FromArgb(0, 0, 0, 0), 1) }
    };

    private readonly SkinRasterizer _raster = new();
    private readonly Stopwatch _clock = Stopwatch.StartNew();
    private IReadOnlyList<SkinMeshPart>? _mesh;
    private SkinImage? _skin, _cape;
    private bool _slim, _layers3D = true, _autoRotate = true;
    private WriteableBitmap? _bitmap;
    private uint[] _resolved = Array.Empty<uint>();
    private SkinCamera _camera = SkinCamera.Default;
    private float _yawSpeed, _pitchSpeed, _spin; // degrees per second: drag inertia, and the eased auto-rotate speed
    private bool _dragging, _resetting, _frameRequested;
    private Point _last;
    private double _lastMove, _lastFrame, _lastInteraction = double.NegativeInfinity;

    public SkinModel3D()
    {
        Focusable = true;
        ClipToBounds = true;
        Cursor = GrabCursor;
        RenderOptions.SetBitmapInterpolationMode(this, BitmapInterpolationMode.None);
    }

    /// <summary>Outer layer as voxels (on) or flat vanilla boxes (off).</summary>
    public bool Layers3D
    {
        get => _layers3D;
        set { if (_layers3D == value) return; _layers3D = value; Rebuild(); }
    }

    /// <summary>Slowly turn the player when nobody has touched it for a few seconds (never with Reduce motion).</summary>
    public bool AutoRotate
    {
        get => _autoRotate;
        set { _autoRotate = value; Wake(); }
    }

    /// <summary>Idle breathing and auto-rotate; the QA preview turns this off for stable screenshots.</summary>
    public bool Animate { get; set; } = true;

    public bool HasSkin => _skin != null;
    public SkinCamera Camera => _camera;

    public void SetSkin(SkinImage? skin, bool slim)
    {
        _skin = skin; _slim = slim;
        Rebuild();
    }

    public void SetCape(SkinImage? cape)
    {
        if (ReferenceEquals(cape, _cape)) return;
        _cape = cape;
        Rebuild();
    }

    public void SetView(SkinCamera camera)
    {
        _camera = camera with { Pitch = Math.Clamp(camera.Pitch, MinPitch, MaxPitch), Zoom = Math.Clamp(camera.Zoom, MinZoom, MaxZoom) };
        _yawSpeed = _pitchSpeed = 0; _resetting = false;
        InvalidateVisual();
    }

    /// <summary>Back to the three-quarter view: eased unless Reduce motion is on.</summary>
    public void ResetView()
    {
        _yawSpeed = _pitchSpeed = _spin = 0;
        _lastInteraction = Now;
        if (!MotionAllowed) { SetView(SkinCamera.Default); return; }
        _resetting = true;
        Wake();
    }

    /// <summary>Restart the frame loop (after the page becomes visible again or the window is re-activated).</summary>
    public void Wake()
    {
        InvalidateVisual();
        RequestFrame();
    }

    private void Rebuild()
    {
        _mesh = _skin == null ? null : SkinMesh.Build(_skin, _slim, _layers3D, _cape);
        Wake();
    }

    private double Now => _clock.Elapsed.TotalSeconds;

    /// <summary>The main window carries the "motion" class unless Settings → Reduce motion is on.</summary>
    private bool MotionAllowed => Animate && (TopLevel.GetTopLevel(this) is not Window w || w.Classes.Contains("motion"));

    private bool Animating => _dragging || _resetting || MathF.Abs(_yawSpeed) > 0.5f || MathF.Abs(_pitchSpeed) > 0.5f
        || (_mesh != null && MotionAllowed && (TopLevel.GetTopLevel(this) is not Window w || w.IsActive));

    private void RequestFrame()
    {
        if (_frameRequested || !Animating || !IsEffectivelyVisible || TopLevel.GetTopLevel(this) is not { } top) return;
        _frameRequested = true;
        top.RequestAnimationFrame(_ => OnFrame());
    }

    private void OnFrame()
    {
        _frameRequested = false;
        if (!IsEffectivelyVisible || VisualRoot == null) return; // hidden or detached: stop until Wake
        double now = Now;
        float dt = (float)Math.Clamp(now - _lastFrame, 0, 0.1);
        _lastFrame = now;
        if (_resetting)
        {
            var target = SkinCamera.Default;
            float k = 1 - MathF.Exp(-dt * 9);
            float dyaw = ((target.Yaw - _camera.Yaw) % 360 + 540) % 360 - 180;
            _camera = new(_camera.Yaw + dyaw * k, _camera.Pitch + (target.Pitch - _camera.Pitch) * k, _camera.Zoom + (target.Zoom - _camera.Zoom) * k);
            if (MathF.Abs(dyaw) < 0.2f && MathF.Abs(target.Pitch - _camera.Pitch) < 0.2f && MathF.Abs(target.Zoom - _camera.Zoom) < 0.005f)
            { _camera = target; _resetting = false; }
        }
        else if (!_dragging)
        {
            if (MathF.Abs(_yawSpeed) > 0.5f || MathF.Abs(_pitchSpeed) > 0.5f)
            {
                // Inertia after a flick, decaying smoothly.
                _camera = _camera with { Yaw = _camera.Yaw + _yawSpeed * dt, Pitch = Math.Clamp(_camera.Pitch + _pitchSpeed * dt, MinPitch, MaxPitch) };
                float decay = MathF.Exp(-dt * 3.2f);
                _yawSpeed *= decay; _pitchSpeed *= decay;
            }
            else _yawSpeed = _pitchSpeed = 0;
            bool spin = _autoRotate && MotionAllowed && now - _lastInteraction > 3;
            _spin += ((spin ? 14f : 0) - _spin) * (1 - MathF.Exp(-dt * 1.5f)); // ease in and out
            if (MathF.Abs(_spin) > 0.05f) _camera = _camera with { Yaw = _camera.Yaw + _spin * dt };
            else _spin = 0;
        }
        InvalidateVisual();
        RequestFrame();
    }

    protected override void OnAttachedToVisualTree(VisualTreeAttachmentEventArgs e)
    {
        base.OnAttachedToVisualTree(e);
        if (TopLevel.GetTopLevel(this) is Window w) w.Activated += WindowActivated;
        _lastFrame = Now;
        Wake();
    }

    protected override void OnDetachedFromVisualTree(VisualTreeAttachmentEventArgs e)
    {
        if (e.Root is Window w) w.Activated -= WindowActivated;
        base.OnDetachedFromVisualTree(e);
        _bitmap?.Dispose();
        _bitmap = null;
    }

    private void WindowActivated(object? sender, EventArgs e) => Wake();

    // ── Input ────────────────────────────────────────────────────────────────

    protected override void OnPointerPressed(PointerPressedEventArgs e)
    {
        base.OnPointerPressed(e);
        if (!e.GetCurrentPoint(this).Properties.IsLeftButtonPressed) return;
        e.Handled = true;
        Focus();
        if (e.ClickCount >= 2) { _dragging = false; ResetView(); return; }
        _dragging = true; _resetting = false;
        _yawSpeed = _pitchSpeed = _spin = 0;
        _last = e.GetPosition(this);
        _lastMove = _lastInteraction = Now;
        e.Pointer.Capture(this);
        Cursor = DragCursor;
    }

    protected override void OnPointerMoved(PointerEventArgs e)
    {
        base.OnPointerMoved(e);
        if (!_dragging) return;
        var p = e.GetPosition(this);
        var d = p - _last;
        _last = p;
        double now = Now;
        float dt = (float)Math.Max(now - _lastMove, 1 / 240.0);
        _lastMove = _lastInteraction = now;
        float dyaw = (float)d.X * 0.55f, dpitch = (float)d.Y * 0.4f;
        _camera = _camera with { Yaw = _camera.Yaw + dyaw, Pitch = Math.Clamp(_camera.Pitch + dpitch, MinPitch, MaxPitch) };
        // Smoothed release speed for the inertia.
        _yawSpeed = _yawSpeed * 0.6f + dyaw / dt * 0.4f;
        _pitchSpeed = _pitchSpeed * 0.6f + dpitch / dt * 0.4f;
        InvalidateVisual();
    }

    protected override void OnPointerReleased(PointerReleasedEventArgs e)
    {
        base.OnPointerReleased(e);
        if (!_dragging) return;
        EndDrag();
        e.Pointer.Capture(null);
    }

    protected override void OnPointerCaptureLost(PointerCaptureLostEventArgs e)
    {
        base.OnPointerCaptureLost(e);
        if (_dragging) EndDrag();
    }

    private void EndDrag()
    {
        _dragging = false;
        Cursor = GrabCursor;
        _lastInteraction = Now;
        // A pause before letting go means no flick; and no inertia at all with Reduce motion.
        if (Now - _lastMove > 0.08 || !MotionAllowed) _yawSpeed = _pitchSpeed = 0;
        _yawSpeed = Math.Clamp(_yawSpeed, -900, 900);
        _pitchSpeed = Math.Clamp(_pitchSpeed, -600, 600);
        Wake();
    }

    protected override void OnPointerWheelChanged(PointerWheelEventArgs e)
    {
        base.OnPointerWheelChanged(e);
        e.Handled = true;
        Zoom(MathF.Pow(1.12f, (float)e.Delta.Y));
    }

    private void Zoom(float factor)
    {
        _lastInteraction = Now;
        _camera = _camera with { Zoom = Math.Clamp(_camera.Zoom * factor, MinZoom, MaxZoom) };
        InvalidateVisual();
    }

    protected override void OnKeyDown(KeyEventArgs e)
    {
        base.OnKeyDown(e);
        _lastInteraction = Now;
        switch (e.Key)
        {
            case Key.Left: _camera = _camera with { Yaw = _camera.Yaw - 15 }; break;
            case Key.Right: _camera = _camera with { Yaw = _camera.Yaw + 15 }; break;
            case Key.Up: _camera = _camera with { Pitch = Math.Clamp(_camera.Pitch - 10, MinPitch, MaxPitch) }; break;
            case Key.Down: _camera = _camera with { Pitch = Math.Clamp(_camera.Pitch + 10, MinPitch, MaxPitch) }; break;
            case Key.OemPlus or Key.Add: Zoom(1.15f); break;
            case Key.OemMinus or Key.Subtract: Zoom(1 / 1.15f); break;
            case Key.Home: ResetView(); break;
            default: return;
        }
        e.Handled = true;
        InvalidateVisual();
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    public override void Render(DrawingContext context)
    {
        var bounds = new Rect(Bounds.Size);
        context.FillRectangle(Brushes.Transparent, bounds); // hit-testable everywhere, not just on the player
        if (_mesh == null || bounds.Width < 2 || bounds.Height < 2) return;
        double scaling = TopLevel.GetTopLevel(this)?.RenderScaling ?? 1;
        int w = Math.Max(1, (int)Math.Ceiling(bounds.Width * scaling)), h = Math.Max(1, (int)Math.Ceiling(bounds.Height * scaling));
        // 2x supersampling smooths the silhouette on standard-DPI screens; high-DPI screens are sharp enough without it.
        int ss = scaling >= 1.75 || w * h > 1_400_000 ? 1 : 2;
        var pose = MotionAllowed ? SkinPose.Idle(Now) : SkinPose.Rest;
        RenderInto(w, h, ss, _camera, pose);
        // Soft contact shadow on the floor, drawn as a vector ellipse under the player.
        var feet = _raster.Project(Vector3.Zero) / (float)(ss * scaling);
        double rx = 10.5 * _raster.Scale / (ss * scaling), ry = rx * (0.16 + 0.55 * Math.Sin(Math.Clamp(_camera.Pitch, 0, 80) * Math.PI / 180));
        if (_camera.Pitch > -5) context.DrawEllipse(ShadowBrush, null, new Point(feet.X, feet.Y), rx, ry);
        context.DrawImage(_bitmap!, new Rect(0, 0, w, h), bounds);
    }

    private void RenderInto(int w, int h, int ss, SkinCamera camera, SkinPose pose)
    {
        if (_bitmap == null || _bitmap.PixelSize.Width != w || _bitmap.PixelSize.Height != h)
        {
            _bitmap?.Dispose();
            _bitmap = new WriteableBitmap(new PixelSize(w, h), new Avalonia.Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Premul);
        }
        _raster.Resize(w * ss, h * ss);
        _raster.Render(_mesh!, camera, pose);
        uint[] source = _raster.Pixels;
        if (ss > 1)
        {
            if (_resolved.Length != w * h) _resolved = new uint[w * h];
            _raster.Downsample(_resolved, ss);
            source = _resolved;
        }
        using var fb = _bitmap.Lock();
        var bytes = MemoryMarshal.AsBytes(source.AsSpan());
        for (int y = 0; y < h; y++)
            Marshal.Copy(bytes.Slice(y * w * 4, w * 4).ToArray(), 0, fb.Address + y * fb.RowBytes, w * 4);
    }

    /// <summary>Renders the current skin off-screen to a PNG (QA screenshots and UV checks).</summary>
    public void SaveRender(string path, SkinCamera camera, int width, int height)
    {
        if (_mesh == null) return;
        RenderInto(width, height, 2, camera, SkinPose.Rest);
        _bitmap!.Save(path);
        _bitmap.Dispose();
        _bitmap = null;
        InvalidateVisual();
    }

    /// <summary>Reads a decoded bitmap as non-premultiplied ARGB.</summary>
    public static SkinImage? ReadPixels(Bitmap bitmap)
    {
        var size = bitmap.PixelSize;
        if (size.Width <= 0 || size.Height <= 0 || size.Width * size.Height > 4096 * 4096) return null;
        using var copy = new WriteableBitmap(size, new Avalonia.Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Unpremul);
        var pixels = new uint[size.Width * size.Height];
        using (var fb = copy.Lock())
        {
            bitmap.CopyPixels(new PixelRect(0, 0, size.Width, size.Height), fb.Address, fb.RowBytes * size.Height, fb.RowBytes);
            var row = new int[size.Width];
            for (int y = 0; y < size.Height; y++)
            {
                Marshal.Copy(fb.Address + y * fb.RowBytes, row, 0, size.Width);
                Buffer.BlockCopy(row, 0, pixels, y * size.Width * 4, size.Width * 4);
            }
        }
        return new SkinImage(pixels, size.Width, size.Height);
    }
}
