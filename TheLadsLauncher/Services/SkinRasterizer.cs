using System;
using System.Collections.Generic;
using System.Numerics;

namespace TheLadsLauncher.Services;

/// <summary>Where the camera looks from. Angles in degrees; Zoom 1 fits the whole player in the viewport.</summary>
public record struct SkinCamera(float Yaw, float Pitch, float Zoom)
{
    /// <summary>A three-quarter front view, slightly from above: the default in the Skins tab.</summary>
    public static SkinCamera Default => new(-28, 12, 1);
}

/// <summary>Joint angles in radians. Arms/legs X swings forward, arms Z lifts them out to the side, CapeX lifts the cape back.</summary>
public record struct SkinPose(float RightArmX, float RightArmZ, float LeftArmX, float LeftArmZ, float RightLegX, float LeftLegX, float HeadYaw, float CapeX)
{
    public static SkinPose Rest => new(0, 0.05f, 0, 0.05f, 0, 0, 0, 0.11f);

    /// <summary>Vanilla's idle breathing (arms drift out and sway a little) and a cape stirring in the wind; seconds since start.</summary>
    public static SkinPose Idle(double seconds)
    {
        float ticks = (float)(seconds * 20);
        float spread = MathF.Cos(ticks * 0.09f) * 0.05f + 0.05f, sway = MathF.Sin(ticks * 0.067f) * 0.05f;
        return new(sway, spread, -sway, spread, 0, 0, 0, 0.11f + MathF.Sin(ticks * 0.05f) * 0.035f);
    }
}

/// <summary>
/// A small software rasterizer for the skin viewer: perspective projection, a z-buffer, nearest-neighbour texturing with
/// perspective-correct UVs, flat per-face shading and a blended pass for see-through texels. It renders into a premultiplied
/// BGRA buffer (one uint per pixel, 0xAARRGGBB) that the UI copies into a bitmap. No GPU, no packages, crisp pixel art.
/// </summary>
public sealed class SkinRasterizer
{
    private const float Distance = 80; // camera distance in skin pixels: a mild, natural perspective
    private const int Sub = 16;        // sub-pixel precision of the fixed-point edge functions
    private static readonly Vector3 Light = Vector3.Normalize(new(-0.45f, 0.8f, 0.55f)); // upper left front, in view space
    private static readonly Vector3 Target = new(0, 16.4f, 0);

    private float[] _depth = Array.Empty<float>();
    private Matrix4x4 _view;
    private float _focal, _cx, _cy;

    public int Width { get; private set; }
    public int Height { get; private set; }
    public uint[] Pixels { get; private set; } = Array.Empty<uint>();

    public void Resize(int width, int height)
    {
        width = Math.Max(1, width); height = Math.Max(1, height);
        if (width == Width && height == Height) return;
        Width = width; Height = height;
        Pixels = new uint[width * height];
        _depth = new float[width * height];
    }

    /// <summary>Pixels per skin pixel at the player's centre, for the floor shadow and tests.</summary>
    public float Scale => _focal / Distance;

    /// <summary>Screen position (pixels) of a model-space point under the last camera.</summary>
    public Vector2 Project(Vector3 model)
    {
        var v = Vector3.Transform(model, _view);
        float z = Distance - v.Z;
        return new(_cx + _focal * v.X / z, _cy - _focal * v.Y / z);
    }

    public void Render(IReadOnlyList<SkinMeshPart> parts, SkinCamera camera, SkinPose pose)
    {
        Array.Clear(Pixels);
        Array.Clear(_depth);
        float rad = MathF.PI / 180;
        _view = Matrix4x4.CreateTranslation(-Target) * Matrix4x4.CreateRotationY(camera.Yaw * rad) * Matrix4x4.CreateRotationX(camera.Pitch * rad);
        // Fit ~35 x 22 skin pixels (the player plus a margin for the hat and a swinging cape) into the viewport.
        float perUnit = MathF.Min(Height / 37f, Width / 23f) * camera.Zoom;
        _focal = perUnit * Distance;
        _cx = Width / 2f; _cy = Height / 2f;
        for (int pass = 0; pass < 2; pass++)
            foreach (var part in parts)
            {
                var m = PartMatrix(part, pose) * _view;
                foreach (var q in part.Quads)
                    if (pass == 0 ? !(q.Texture == null && q.Translucent) : q.Translucent) DrawQuad(q, m, pass == 1);
            }
    }

    private static Matrix4x4 PartMatrix(SkinMeshPart part, SkinPose pose)
    {
        Matrix4x4 r = part.Part switch
        {
            SkinPart.RightArm => Matrix4x4.CreateRotationX(-pose.RightArmX) * Matrix4x4.CreateRotationZ(-pose.RightArmZ),
            SkinPart.LeftArm => Matrix4x4.CreateRotationX(-pose.LeftArmX) * Matrix4x4.CreateRotationZ(pose.LeftArmZ),
            SkinPart.RightLeg => Matrix4x4.CreateRotationX(-pose.RightLegX),
            SkinPart.LeftLeg => Matrix4x4.CreateRotationX(-pose.LeftLegX),
            SkinPart.Head => Matrix4x4.CreateRotationY(pose.HeadYaw),
            SkinPart.Cape => Matrix4x4.CreateRotationX(pose.CapeX),
            _ => Matrix4x4.Identity
        };
        return r.IsIdentity ? r : Matrix4x4.CreateTranslation(-part.Pivot) * r * Matrix4x4.CreateTranslation(part.Pivot);
    }

    private struct Vert { public int X, Y; public float Iz, U, V; }

    private void DrawQuad(SkinQuad q, Matrix4x4 m, bool blendPass)
    {
        var o = Vector3.Transform(q.Origin, m);
        var n = Vector3.TransformNormal(q.Normal, m);
        if (Vector3.Dot(n, new Vector3(0, 0, Distance) - o) <= 0) return; // facing away
        var eu = Vector3.TransformNormal(q.EdgeU, m);
        var ev = Vector3.TransformNormal(q.EdgeV, m);
        Span<Vert> c = stackalloc Vert[4];
        if (!Vertex(o, 0, 0, out c[0]) || !Vertex(o + eu, q.Width, 0, out c[1]) || !Vertex(o + eu + ev, q.Width, q.Height, out c[2]) || !Vertex(o + ev, 0, q.Height, out c[3])) return;
        // Flat shading in view space, so the lit side stays the same while the player turns.
        int shade = (int)(256 * (0.52f + 0.48f * MathF.Max(0, Vector3.Dot(n, Light))));
        Triangle(c[0], c[1], c[2], q, shade, blendPass);
        Triangle(c[0], c[2], c[3], q, shade, blendPass);
    }

    private bool Vertex(Vector3 v, float u, float t, out Vert vert)
    {
        float z = Distance - v.Z;
        vert = default;
        if (z < 1) return false; // behind the camera (never at the allowed zoom levels)
        float iz = 1 / z;
        vert.X = (int)MathF.Round((_cx + _focal * v.X * iz) * Sub);
        vert.Y = (int)MathF.Round((_cy - _focal * v.Y * iz) * Sub);
        vert.Iz = iz; vert.U = u * iz; vert.V = t * iz;
        return true;
    }

    private void Triangle(Vert a, Vert b, Vert c, SkinQuad q, int shade, bool blendPass)
    {
        long area = (long)(b.X - a.X) * (c.Y - a.Y) - (long)(b.Y - a.Y) * (c.X - a.X);
        if (area == 0) return;
        if (area < 0) { (b, c) = (c, b); area = -area; }
        int minX = Math.Max(0, Math.Min(a.X, Math.Min(b.X, c.X)) / Sub), maxX = Math.Min(Width - 1, (Math.Max(a.X, Math.Max(b.X, c.X)) + Sub - 1) / Sub);
        int minY = Math.Max(0, Math.Min(a.Y, Math.Min(b.Y, c.Y)) / Sub), maxY = Math.Min(Height - 1, (Math.Max(a.Y, Math.Max(b.Y, c.Y)) + Sub - 1) / Sub);
        if (minX > maxX || minY > maxY) return;
        // Edge functions in fixed point; the tie rule gives a shared edge's pixels to exactly one of the two triangles.
        static long Bias(Vert p, Vert q) => q.Y - p.Y > 0 || (q.Y == p.Y && q.X - p.X < 0) ? 0 : -1;
        long Edge(Vert p, Vert q, int x, int y) => (long)(q.X - p.X) * (y - p.Y) - (long)(q.Y - p.Y) * (x - p.X);
        int sx = minX * Sub + Sub / 2, sy = minY * Sub + Sub / 2;
        long w0Row = Edge(b, c, sx, sy) + Bias(b, c), w1Row = Edge(c, a, sx, sy) + Bias(c, a), w2Row = Edge(a, b, sx, sy) + Bias(a, b);
        long dx0 = -(long)(c.Y - b.Y) * Sub, dx1 = -(long)(a.Y - c.Y) * Sub, dx2 = -(long)(b.Y - a.Y) * Sub;
        long dy0 = (long)(c.X - b.X) * Sub, dy1 = (long)(a.X - c.X) * Sub, dy2 = (long)(b.X - a.X) * Sub;
        float inv = 1f / area;
        var tex = q.Texture;
        uint solid = q.Color;
        for (int y = minY; y <= maxY; y++, w0Row += dy0, w1Row += dy1, w2Row += dy2)
        {
            long w0 = w0Row, w1 = w1Row, w2 = w2Row;
            int row = y * Width;
            for (int x = minX; x <= maxX; x++, w0 += dx0, w1 += dx1, w2 += dx2)
            {
                if ((w0 | w1 | w2) < 0) continue;
                float l0 = w0 * inv, l1 = w1 * inv, l2 = w2 * inv;
                float iz = l0 * a.Iz + l1 * b.Iz + l2 * c.Iz;
                int at = row + x;
                if (iz <= _depth[at]) continue;
                uint color = solid;
                if (tex != null)
                {
                    float z = 1 / iz;
                    int u = Math.Clamp((int)((l0 * a.U + l1 * b.U + l2 * c.U) * z), 0, q.Width - 1);
                    int v = Math.Clamp((int)((l0 * a.V + l1 * b.V + l2 * c.V) * z), 0, q.Height - 1);
                    color = tex.Pixels[(q.V0 + v) * tex.Width + q.U0 + u];
                }
                uint alpha = color >> 24;
                if (!blendPass)
                {
                    if (alpha < SkinMesh.SolidAlpha) continue;
                    Pixels[at] = 0xFF000000 | Shade(color, shade);
                    _depth[at] = iz;
                }
                else if (alpha is > 0 and < SkinMesh.SolidAlpha) Pixels[at] = Blend(Pixels[at], Shade(color, shade), alpha);
            }
        }
    }

    private static uint Shade(uint c, int shade)
    {
        uint r = (uint)(((c >> 16) & 255) * shade) >> 8, g = (uint)(((c >> 8) & 255) * shade) >> 8, b = (uint)((c & 255) * shade) >> 8;
        return (Math.Min(r, 255u) << 16) | (Math.Min(g, 255u) << 8) | Math.Min(b, 255u);
    }

    /// <summary>Straight-alpha rgb over a premultiplied pixel.</summary>
    private static uint Blend(uint dst, uint rgb, uint alpha)
    {
        uint keep = 255 - alpha;
        uint Ch(int s) => (((rgb >> s) & 255) * alpha + ((dst >> s) & 255) * keep + 127) / 255;
        uint a = alpha + ((dst >> 24) * keep + 127) / 255;
        return (a << 24) | (Ch(16) << 16) | (Ch(8) << 8) | Ch(0);
    }

    /// <summary>Box-filters this (supersampled) buffer down by <paramref name="factor"/> into <paramref name="target"/>.</summary>
    public void Downsample(uint[] target, int factor)
    {
        int w = Width / factor, h = Height / factor, n = factor * factor;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
            {
                uint a = 0, r = 0, g = 0, b = 0;
                for (int j = 0; j < factor; j++)
                {
                    int row = (y * factor + j) * Width + x * factor;
                    for (int i = 0; i < factor; i++)
                    {
                        uint p = Pixels[row + i];
                        a += p >> 24; r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255;
                    }
                }
                target[y * w + x] = (a / (uint)n << 24) | (r / (uint)n << 16) | (g / (uint)n << 8) | b / (uint)n;
            }
    }
}
