using System;
using System.Collections.Generic;
using System.Numerics;

namespace TheLadsLauncher.Services;

/// <summary>An ARGB (non-premultiplied, 0xAARRGGBB) image: a skin normalized to 64x64, or a cape.</summary>
public sealed class SkinImage
{
    public SkinImage(uint[] pixels, int width, int height)
    {
        if (width <= 0 || height <= 0 || pixels.Length < width * height) throw new ArgumentException("Pixel buffer does not match the size.");
        Pixels = pixels; Width = width; Height = height;
    }

    public uint[] Pixels { get; }
    public int Width { get; }
    public int Height { get; }
    /// <summary>True when the source was a pre-1.8 64x32 skin (left limbs mirrored from the right ones, no outer layer but the hat).</summary>
    public bool Legacy { get; init; }

    public uint this[int x, int y] => Pixels[y * Width + x];

    /// <summary>
    /// What Minecraft does to a downloaded skin (SkinTextureDownloader.processLegacySkin): a 64x32 skin is grown to 64x64 with the
    /// right arm/leg copied, face by face and mirrored, into the left limb slots; the base layer is made opaque; and on legacy skins
    /// a hat layer with no transparent pixel at all is cleared ("Notch transparency hack"). Returns null for other sizes.
    /// </summary>
    public static SkinImage? NormalizeSkin(uint[] pixels, int width, int height)
    {
        if (width != 64 || height is not (64 or 32) || pixels.Length < width * height) return null;
        bool legacy = height == 32;
        var px = new uint[64 * 64];
        Array.Copy(pixels, px, 64 * height);
        if (legacy)
        {
            // copyRect(xFrom, yFrom, xOffset, yOffset, w, h, mirrorX): the same twelve calls Minecraft makes.
            void Copy(int x, int y, int dx, int dy, int w, int h)
            {
                for (int j = 0; j < h; j++)
                    for (int i = 0; i < w; i++)
                        px[(y + dy + j) * 64 + x + dx + (w - 1 - i)] = px[(y + j) * 64 + x + i];
            }
            Copy(4, 16, 16, 32, 4, 4); Copy(8, 16, 16, 32, 4, 4);
            Copy(0, 20, 24, 32, 4, 12); Copy(4, 20, 16, 32, 4, 12); Copy(8, 20, 8, 32, 4, 12); Copy(12, 20, 16, 32, 4, 12);
            Copy(44, 16, -8, 32, 4, 4); Copy(48, 16, -8, 32, 4, 4);
            Copy(40, 20, 0, 32, 4, 12); Copy(44, 20, -8, 32, 4, 12); Copy(48, 20, -16, 32, 4, 12); Copy(52, 20, -8, 32, 4, 12);
        }
        void NoAlpha(int x0, int y0, int x1, int y1)
        {
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) px[y * 64 + x] |= 0xFF000000;
        }
        NoAlpha(0, 0, 32, 16);
        if (legacy) NotchHack(px);
        NoAlpha(0, 16, 64, 32);
        NoAlpha(16, 48, 48, 64);
        return new SkinImage(px, 64, 64) { Legacy = legacy };
    }

    private static void NotchHack(uint[] px)
    {
        for (int y = 0; y < 32; y++) for (int x = 32; x < 64; x++) if (px[y * 64 + x] >> 24 < 128) return;
        for (int y = 0; y < 32; y++) for (int x = 32; x < 64; x++) px[y * 64 + x] &= 0x00FFFFFF;
    }
}

public enum SkinPart { Head, Body, RightArm, LeftArm, RightLeg, LeftLeg, Cape }

/// <summary>
/// One flat face. Model space: 1 unit = 1 skin pixel, y up, +z is the player's front, +x the player's left (the viewer's right
/// when facing them), feet at y = 0. Origin is the 3D point of texel corner (U0, V0); EdgeU spans the texel rect's width and
/// EdgeV its height. Texture == null means a solid Color (a voxel face).
/// </summary>
public sealed class SkinQuad
{
    public Vector3 Origin, EdgeU, EdgeV, Normal;
    public SkinImage? Texture;
    public int U0, V0, Width = 1, Height = 1;
    public uint Color;
    /// <summary>May hold texels with 0 &lt; alpha &lt; 255, so the blended pass has to look at it.</summary>
    public bool Translucent;
}

/// <summary>A posable part: its quads in rest pose and the joint it rotates around.</summary>
public sealed class SkinMeshPart
{
    public SkinMeshPart(SkinPart part, Vector3 pivot) { Part = part; Pivot = pivot; }
    public SkinPart Part { get; }
    public Vector3 Pivot { get; }
    public List<SkinQuad> Quads { get; } = new();
}

/// <summary>
/// Builds the Minecraft player model from a skin: box UVs exactly like vanilla's ModelPart cubes, and the outer layer either as
/// vanilla's inflated flat boxes or, like the 3D Skin Layers mod, as one small cube per opaque overlay pixel.
/// </summary>
public static class SkinMesh
{
    /// <summary>Overlay texels at or above this alpha are solid; below it (but above 0) they are drawn as see-through glass.</summary>
    public const uint SolidAlpha = 250;

    private enum Face { Front, Back, Right, Left, Top, Bottom }

    public static IReadOnlyList<SkinMeshPart> Build(SkinImage skin, bool slim, bool layers3D, SkinImage? cape = null)
    {
        float arm = slim ? 3 : 4;
        var parts = new List<SkinMeshPart>();
        SkinMeshPart Part(SkinPart part, Vector3 pivot, Vector3 min, Vector3 size, int u, int v, int ou, int ov, float flatInflate, float voxelOut, bool skipTop = false, bool skipBottom = false)
        {
            var p = new SkinMeshPart(part, pivot);
            AddBox(p.Quads, skin, min, size, u, v, 0, false);
            if (ou >= 0)
            {
                if (layers3D) AddVoxels(p.Quads, skin, min, size, ou, ov, voxelOut, skipTop, skipBottom);
                else AddBox(p.Quads, skin, min, size, ou, ov, flatInflate, true);
            }
            parts.Add(p);
            return p;
        }
        bool modern = !skin.Legacy;
        // Outer-layer offsets: vanilla inflates the hat by 0.5 and the rest by 0.25; voxels stick out a bit further, as in the mod.
        Part(SkinPart.Head, new(0, 24, 0), new(-4, 24, -4), new(8, 8, 8), 0, 0, 32, 0, 0.5f, 0.7f);
        Part(SkinPart.Body, new(0, 24, 0), new(-4, 12, -2), new(8, 12, 4), 16, 16, modern ? 16 : -1, 32, 0.25f, 0.3f, skipBottom: true);
        Part(SkinPart.RightArm, new(-5, 22, 0), new(-4 - arm, 12, -2), new(arm, 12, 4), 40, 16, modern ? 40 : -1, 32, 0.25f, 0.3f);
        Part(SkinPart.LeftArm, new(5, 22, 0), new(4, 12, -2), new(arm, 12, 4), 32, 48, modern ? 48 : -1, 48, 0.25f, 0.3f);
        Part(SkinPart.RightLeg, new(-2, 12, 0), new(-4, 0, -2), new(4, 12, 4), 0, 16, modern ? 0 : -1, 32, 0.25f, 0.3f, skipTop: true);
        Part(SkinPart.LeftLeg, new(2, 12, 0), new(0, 0, -2), new(4, 12, 4), 16, 48, modern ? 0 : -1, 48, 0.25f, 0.3f, skipTop: true);
        if (cape != null)
        {
            // Vanilla: a 10x16x1 box hanging from the shoulders, turned 180 degrees so the art (1,1) faces away from the back.
            // It hangs a little further back than vanilla so the outer layer never pokes through it.
            float back = layers3D ? 0.45f : 0.3f;
            var p = new SkinMeshPart(SkinPart.Cape, new(0, 24, -2 - back));
            var quads = new List<SkinQuad>();
            AddBox(quads, cape, new(-5, 8, -3 - back), new(10, 16, 1), 0, 0, 0, true, Math.Max(1, cape.Width / 64));
            foreach (var q in quads) p.Quads.Add(Turn(q, -2.5f - back));
            parts.Add(p);
        }
        return parts;
    }

    /// <summary>Rotates a quad 180 degrees around the vertical line x = 0, z = <paramref name="centerZ"/>.</summary>
    private static SkinQuad Turn(SkinQuad q, float centerZ)
    {
        static Vector3 Dir(Vector3 v) => new(-v.X, v.Y, -v.Z);
        q.Origin = new(-q.Origin.X, q.Origin.Y, 2 * centerZ - q.Origin.Z);
        q.EdgeU = Dir(q.EdgeU); q.EdgeV = Dir(q.EdgeV); q.Normal = Dir(q.Normal);
        return q;
    }

    /// <summary>The texel rect of a face of a box with texture offset (u, v), in skin pixels (vanilla's cube UV layout).</summary>
    private static (int U, int V, int W, int H) Rect(Face face, int u, int v, int w, int h, int d) => face switch
    {
        Face.Top => (u + d, v, w, d),
        Face.Bottom => (u + d + w, v, w, d),
        Face.Right => (u, v + d, d, h),
        Face.Front => (u + d, v + d, w, h),
        Face.Left => (u + d + w, v + d, d, h),
        _ => (u + 2 * d + w, v + d, w, h),
    };

    /// <summary>Where texel corner (0, 0) of each face sits and which way its rows and columns run (see Rect for the texels).</summary>
    private static (Vector3 Origin, Vector3 EdgeU, Vector3 EdgeV, Vector3 Normal) Frame(Face face, Vector3 min, Vector3 max) => face switch
    {
        // Seen from the front: columns run to the viewer's right (+x), rows downwards.
        Face.Front => (new(min.X, max.Y, max.Z), new(max.X - min.X, 0, 0), new(0, min.Y - max.Y, 0), Vector3.UnitZ),
        // Seen from behind the texture still reads left to right, so it starts at +x.
        Face.Back => (new(max.X, max.Y, min.Z), new(min.X - max.X, 0, 0), new(0, min.Y - max.Y, 0), -Vector3.UnitZ),
        // The player's right side (-x) starts at the back; its last column meets the front's first.
        Face.Right => (new(min.X, max.Y, min.Z), new(0, 0, max.Z - min.Z), new(0, min.Y - max.Y, 0), -Vector3.UnitX),
        Face.Left => (new(max.X, max.Y, max.Z), new(0, 0, min.Z - max.Z), new(0, min.Y - max.Y, 0), Vector3.UnitX),
        // Top and bottom both run +x by columns and back-to-front by rows: the row touching the front texture is the last one.
        Face.Top => (new(min.X, max.Y, min.Z), new(max.X - min.X, 0, 0), new(0, 0, max.Z - min.Z), Vector3.UnitY),
        _ => (new(min.X, min.Y, min.Z), new(max.X - min.X, 0, 0), new(0, 0, max.Z - min.Z), -Vector3.UnitY),
    };

    private static readonly Face[] Faces = { Face.Front, Face.Back, Face.Right, Face.Left, Face.Top, Face.Bottom };

    private static void AddBox(List<SkinQuad> quads, SkinImage tex, Vector3 min, Vector3 size, int u, int v, float inflate, bool translucent, int scale = 1)
    {
        int w = (int)size.X, h = (int)size.Y, d = (int)size.Z;
        Vector3 lo = min - new Vector3(inflate), hi = min + size + new Vector3(inflate);
        foreach (var face in Faces)
        {
            var r = Rect(face, u, v, w, h, d);
            if ((r.U + r.W) * scale > tex.Width || (r.V + r.H) * scale > tex.Height) continue; // short or odd cape images
            var f = Frame(face, lo, hi);
            quads.Add(new SkinQuad
            {
                Origin = f.Origin, EdgeU = f.EdgeU, EdgeV = f.EdgeV, Normal = f.Normal, Texture = tex,
                U0 = r.U * scale, V0 = r.V * scale, Width = r.W * scale, Height = r.H * scale, Translucent = translucent
            });
        }
    }

    /// <summary>
    /// The 3D Skin Layers look: the outer layer of a box becomes a one-voxel-thick shell, one cube per opaque texel, scaled per
    /// axis so it sticks out <paramref name="outset"/> pixels past the base box (its inner part hides inside the base). Faces
    /// between two solid voxels and faces pointing into the body are dropped. Corner voxels are shared by the faces that meet
    /// there; each outward side shows that face's own texel when it has one. Semi-transparent texels become one flat glass quad.
    /// </summary>
    internal static void AddVoxels(List<SkinQuad> quads, SkinImage skin, Vector3 min, Vector3 size, int u, int v, float outset, bool skipTop, bool skipBottom)
    {
        int w = (int)size.X, h = (int)size.Y, d = (int)size.Z;
        var cells = new Cell[w, h, d];
        var glass = new List<(int X, int Y, int Z, Face Face, uint Color)>();
        Vector3 max = min + size;
        foreach (var face in Faces)
        {
            if ((face == Face.Top && skipTop) || (face == Face.Bottom && skipBottom)) continue;
            var r = Rect(face, u, v, w, h, d);
            var f = Frame(face, min, max);
            for (int j = 0; j < r.H; j++)
                for (int i = 0; i < r.W; i++)
                {
                    uint c = skin[r.U + i, r.V + j];
                    uint a = c >> 24;
                    if (a == 0) continue;
                    // The texel's centre on the face picks its cell; clamping keeps face-plane points inside the grid.
                    var p = f.Origin + f.EdgeU * ((i + 0.5f) / r.W) + f.EdgeV * ((j + 0.5f) / r.H) - min;
                    int x = Math.Clamp((int)MathF.Floor(p.X), 0, w - 1), y = Math.Clamp((int)MathF.Floor(p.Y), 0, h - 1), z = Math.Clamp((int)MathF.Floor(p.Z), 0, d - 1);
                    if (a < SolidAlpha) { glass.Add((x, y, z, face, c)); continue; }
                    ref var cell = ref cells[x, y, z];
                    cell.Solid = true;
                    if (cell.Primary == 0) cell.Primary = c;
                    cell.SetSide(face, c);
                }
        }
        // Per-axis voxel size: the shell spans the box grown by `outset` on every side.
        var k = new Vector3((w + 2 * outset) / w, (h + 2 * outset) / h, (d + 2 * outset) / d);
        var center = min + size / 2;
        Vector3 Corner(int x, int y, int z) => center + (new Vector3(x, y, z) + min - center) * k;
        bool Shell(int x, int y, int z) => x == 0 || y == 0 || z == 0 || x == w - 1 || y == h - 1 || z == d - 1;
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                for (int z = 0; z < d; z++)
                {
                    var cell = cells[x, y, z];
                    if (!cell.Solid) continue;
                    Vector3 lo = Corner(x, y, z), hi = Corner(x + 1, y + 1, z + 1);
                    foreach (var face in Faces)
                    {
                        var n = Step(face);
                        int nx = x + n.X, ny = y + n.Y, nz = z + n.Z;
                        bool outside = nx < 0 || ny < 0 || nz < 0 || nx >= w || ny >= h || nz >= d;
                        if (!outside && (cells[nx, ny, nz].Solid || !Shell(nx, ny, nz))) continue; // covered, or facing into the body
                        uint color = outside && cell.Side(face) is var own && own != 0 ? own : cell.Primary;
                        quads.Add(Solid(face, lo, hi, color, false));
                    }
                }
        foreach (var (x, y, z, face, color) in glass)
        {
            if (cells[x, y, z].Solid) continue;
            // Only the outer side of the voxel: a thin pane of glass where the texel sits.
            quads.Add(Solid(face, Corner(x, y, z), Corner(x + 1, y + 1, z + 1), color, true));
        }
    }

    private static SkinQuad Solid(Face face, Vector3 lo, Vector3 hi, uint color, bool translucent)
    {
        var f = Frame(face, lo, hi);
        return new SkinQuad { Origin = f.Origin, EdgeU = f.EdgeU, EdgeV = f.EdgeV, Normal = f.Normal, Color = color, Translucent = translucent };
    }

    private static (int X, int Y, int Z) Step(Face face) => face switch
    {
        Face.Front => (0, 0, 1), Face.Back => (0, 0, -1), Face.Right => (-1, 0, 0), Face.Left => (1, 0, 0), Face.Top => (0, 1, 0), _ => (0, -1, 0)
    };

    private struct Cell
    {
        public bool Solid;
        public uint Primary;
        private uint _front, _back, _right, _left, _top, _bottom;
        public void SetSide(Face face, uint c)
        {
            switch (face)
            {
                case Face.Front: _front = c; break;
                case Face.Back: _back = c; break;
                case Face.Right: _right = c; break;
                case Face.Left: _left = c; break;
                case Face.Top: _top = c; break;
                default: _bottom = c; break;
            }
        }
        public readonly uint Side(Face face) => face switch
        {
            Face.Front => _front, Face.Back => _back, Face.Right => _right, Face.Left => _left, Face.Top => _top, _ => _bottom
        };
    }
}
