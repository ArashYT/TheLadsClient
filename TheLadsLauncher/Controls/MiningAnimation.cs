using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.InteropServices;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Media.Immutable;
using Avalonia.Platform;

namespace TheLadsLauncher.Controls;

/// <summary>
/// Startup/update animation: a pixel pickaxe winds up, strikes and recoils until the isometric block breaks, then the next one drops in.
/// The scene is a pure function of time (exact preview frames, no drift); all art is original 16x16 pixel art generated here.
/// </summary>
public sealed class MiningAnimation : Control
{
    private const int Hits = 6;
    private const double Drop = 0.55, Hit = 0.62, Impact = 0.36, Break = 0.45, Cycle = Drop + Hits * Hit + Break;
    private const double ImpactAngle = 20; // degrees; 0 rests just above the block, negative winds up
    private static readonly string[] Materials = ["grass", "stone", "ore", "log"];
    private static readonly Dictionary<int, WriteableBitmap[,]> Faces = new(); // material -> [top/left/right, crack stage 0..8]
    private static readonly Dictionary<int, Color[]> Chips = new();
    private static WriteableBitmap? _pickaxe;
    private static readonly IPen Seam = new ImmutablePen(new ImmutableSolidColorBrush(Color.FromArgb(150, 12, 12, 14)), 1);
    private readonly Stopwatch _clock = Stopwatch.StartNew();
    private readonly Action<TimeSpan> _frame;
    private double? _frozen;
    private bool _running;

    public MiningAnimation()
    {
        _frame = OnFrame;
        ClipToBounds = true; // the falling block and flying chips never cross the progress bar above
    }

    /// <summary>One block from drop-in to break.</summary>
    public static double CycleSeconds => Cycle;

    /// <summary>Shows one exact moment (previews, reduced motion) instead of the live clock.</summary>
    public void Freeze(double seconds) { _frozen = seconds; _running = false; InvalidateVisual(); }
    public void Stop() => _running = false;

    protected override void OnAttachedToVisualTree(VisualTreeAttachmentEventArgs e)
    {
        base.OnAttachedToVisualTree(e);
        if (_frozen != null || _running) return;
        _running = true;
        TopLevel.GetTopLevel(this)?.RequestAnimationFrame(_frame);
    }

    protected override void OnDetachedFromVisualTree(VisualTreeAttachmentEventArgs e) { _running = false; base.OnDetachedFromVisualTree(e); }

    private void OnFrame(TimeSpan _)
    {
        if (!_running) return;
        InvalidateVisual();
        TopLevel.GetTopLevel(this)?.RequestAnimationFrame(_frame);
    }

    public override void Render(DrawingContext context)
    {
        base.Render(context);
        double w = Bounds.Width, h = Bounds.Height;
        if (w < 40 || h < 40) return;
        double t = _frozen ?? _clock.Elapsed.TotalSeconds;
        int cycle = (int)Math.Floor(t / Cycle), material = ((cycle % Materials.Length) + Materials.Length) % Materials.Length;
        double local = t - cycle * Cycle;
        var art = Art(material); // also fills the chip palette
        // F is the cube's front-top vertex; the cube spans cy-L..cy+L vertically and cx±0.866L horizontally.
        // Wind-up lifts the head ~1.47L above cy, so the scene sits low enough to never touch the progress bar above it.
        double L = Math.Min(h * 0.3, w * 0.2), cx = w * 0.55, cy = h * 0.56, ps = L * 1.25 / 16;
        var impactAt = new Point(cx - 0.25 * L, cy - 0.55 * L);
        double deg, dropY = 0, scale = 1, shake = 0, fade = 1, sinceImpact = double.MaxValue;
        int stage = 0;
        var chips = new List<(Point at, double size, Color color, double alpha)>();
        if (local < Drop)
        {
            dropY = -(1 - EaseOutBounce(local / Drop)) * h * 0.8;
            deg = -10 + 4 * Math.Sin(t * 3);
        }
        else if (local < Drop + Hits * Hit)
        {
            double m = local - Drop, ph = m % Hit;
            int hit = (int)(m / Hit), done = hit + (ph >= Impact ? 1 : 0);
            stage = (int)Math.Round(done * 8.0 / Hits);
            deg = Swing(ph);
            if (done > 0) sinceImpact = m - ((done - 1) * Hit + Impact);
            if (sinceImpact < 0.14) shake = Math.Sin(sinceImpact * 90) * (1 - sinceImpact / 0.14) * L * 0.04;
            for (int k = Math.Max(0, done - 2); k < done; k++)
                AddChips(chips, material, cycle * 37 + k, m - (k * Hit + Impact), impactAt, 7, L, false);
        }
        else
        {
            double b = local - Drop - Hits * Hit, u = Math.Min(1, b / (Break * 0.7));
            stage = 8; scale = 1 - u * u; fade = 1 - u;
            deg = -10 + 4 * Math.Sin(t * 3);
            AddChips(chips, material, cycle * 37 + 91, b, new Point(cx, cy), 26, L, true);
        }

        using (context.PushRenderOptions(new RenderOptions { BitmapInterpolationMode = BitmapInterpolationMode.None }))
        {
            // Ground shadow shrinks while the block falls in and while it breaks.
            double lift = Math.Min(1, -dropY / (h * 0.8));
            using (context.PushOpacity(0.35 * (1 - lift) * fade))
                context.DrawEllipse(Brushes.Black, null, new Point(cx, cy + L * 1.02), 0.95 * L * scale * (1 - 0.5 * lift), 0.2 * L * scale);
            if (scale > 0.01)
            {
                var place = Matrix.CreateTranslation(-cx, -cy) * Matrix.CreateScale(scale, scale) * Matrix.CreateTranslation(cx + shake, cy + dropY);
                using (context.PushOpacity(fade))
                using (context.PushTransform(place))
                    DrawCube(context, art, stage, cx, cy, L);
            }
            foreach (var chip in chips)
                using (context.PushOpacity(chip.alpha))
                    context.FillRectangle(new ImmutableSolidColorBrush(chip.color), new Rect(chip.at.X - chip.size / 2, chip.at.Y - chip.size / 2, chip.size, chip.size));

            // The grip sits where a strike at ImpactAngle puts the lower tip of the head exactly on the impact point.
            var tip = Rotate(new Vector(10.9 * ps, -2.1 * ps), ImpactAngle);
            var grip = new Point(impactAt.X - tip.X, impactAt.Y - tip.Y);
            if (local >= Drop && local < Drop + Hits * Hit)
            {
                double ph = (local - Drop) % Hit;
                if (ph >= 0.26 && ph < Impact) // motion trail on the fast downswing
                {
                    DrawPickaxe(context, Swing(ph - 0.035), ps, grip, 0.16);
                    DrawPickaxe(context, Swing(ph - 0.018), ps, grip, 0.3);
                }
            }
            DrawPickaxe(context, deg, ps, grip, 1);
            if (sinceImpact < 0.09) // impact sparkle
            {
                double a = 1 - sinceImpact / 0.09, s = L * (0.05 + 0.1 * (1 - a));
                var brush = new ImmutableSolidColorBrush(Color.FromArgb((byte)(230 * a), 255, 248, 225));
                foreach (var (dx, dy) in new[] { (-1.4, 0.0), (1.4, 0.0), (0.0, -1.4), (0.0, 1.4) })
                    context.FillRectangle(brush, new Rect(impactAt.X + dx * s - L * 0.03, impactAt.Y + dy * s - L * 0.03, L * 0.06, L * 0.06));
            }
        }
    }

    private static double Swing(double ph)
    {
        if (ph < 0.26) return -38 * (1 - Math.Pow(1 - ph / 0.26, 3));            // wind-up, easing out
        if (ph < Impact) { double u = (ph - 0.26) / (Impact - 0.26); return -38 + (ImpactAngle + 38) * u * u; } // strike, accelerating
        if (ph < 0.40) return ImpactAngle;                                        // contact
        double r = (ph - 0.40) / (Hit - 0.40), c = 1.70158;
        return ImpactAngle * (-(c + 1) * Math.Pow(r - 1, 3) - c * Math.Pow(r - 1, 2)); // recoil: ease-out-back, lifting slightly past rest
    }

    private static void DrawCube(DrawingContext context, WriteableBitmap[,] faces, int stage, double cx, double cy, double L)
    {
        double k = 0.866 * L / 16, half = 0.5 * L / 16, unit = L / 16;
        var back = new Point(cx, cy - L); var left = new Point(cx - 0.866 * L, cy - 0.5 * L); var front = new Point(cx, cy); var right = new Point(cx + 0.866 * L, cy - 0.5 * L);
        Face(context, faces[0, stage], k, half, -k, half, back);
        Face(context, faces[1, stage], k, half, 0, unit, left);
        Face(context, faces[2, stage], k, -half, 0, unit, front);
        // Dark seams hide anti-aliased gaps between faces and give the pixel-art outline.
        context.DrawLine(Seam, left, front); context.DrawLine(Seam, front, right); context.DrawLine(Seam, front, new Point(cx, cy + L));
    }

    private static void Face(DrawingContext context, IImage texture, double ux, double uy, double vx, double vy, Point origin)
    {
        using (context.PushTransform(new Matrix(ux, uy, vx, vy, origin.X, origin.Y)))
            context.DrawImage(texture, new Rect(0, 0, 16, 16), new Rect(-0.03, -0.03, 16.06, 16.06));
    }

    private static void DrawPickaxe(DrawingContext context, double deg, double ps, Point grip, double opacity)
    {
        var pose = Matrix.CreateTranslation(-2.5 * ps, -13.5 * ps) * Matrix.CreateRotation(deg * Math.PI / 180) * Matrix.CreateTranslation(grip.X, grip.Y);
        using (context.PushOpacity(opacity))
        using (context.PushTransform(pose))
            context.DrawImage(_pickaxe ??= MakePickaxe(), new Rect(0, 0, 16, 16), new Rect(0, 0, 16 * ps, 16 * ps));
    }

    private static Vector Rotate(Vector v, double deg)
    {
        double a = deg * Math.PI / 180, c = Math.Cos(a), s = Math.Sin(a);
        return new Vector(v.X * c - v.Y * s, v.X * s + v.Y * c);
    }

    private static void AddChips(List<(Point at, double size, Color color, double alpha)> chips, int material, int seed, double age, Point from, int count, double L, bool burst)
    {
        if (age < 0) return;
        var palette = Chips[material];
        for (int i = 0; i < count; i++)
        {
            double life = 0.45 + Rand(seed, i, 3) * 0.35;
            if (age > life) continue;
            double vx = (Rand(seed, i, 1) - 0.5) * (burst ? 5.5 : 3.2) * L, vy = -(0.8 + Rand(seed, i, 2) * (burst ? 2.4 : 1.6)) * L;
            var at = new Point(from.X + vx * age, from.Y + vy * age + 2.9 * L * age * age);
            double alpha = age < life * 0.6 ? 1 : 1 - (age - life * 0.6) / (life * 0.4);
            chips.Add((at, L * (0.06 + Rand(seed, i, 4) * 0.05), palette[(int)(Rand(seed, i, 5) * palette.Length)], alpha));
        }
    }

    private static double EaseOutBounce(double x)
    {
        const double n = 7.5625, d = 2.75;
        if (x < 1 / d) return n * x * x;
        if (x < 2 / d) return n * (x -= 1.5 / d) * x + 0.75;
        if (x < 2.5 / d) return n * (x -= 2.25 / d) * x + 0.9375;
        return n * (x -= 2.625 / d) * x + 0.984375;
    }

    /// <summary>Deterministic hash noise in [0, 1).</summary>
    private static double Rand(int a, int b, int c)
    {
        uint h = (uint)(a * 73856093) ^ (uint)(b * 19349663) ^ (uint)(c * 83492791);
        h ^= h >> 13; h *= 0x5bd1e995; h ^= h >> 15;
        return (h & 0xFFFFFF) / (double)0x1000000;
    }

    // ── Art ─────────────────────────────────────────────────────────────────────

    private static WriteableBitmap[,] Art(int material)
    {
        if (Faces.TryGetValue(material, out var faces)) return faces;
        faces = new WriteableBitmap[3, 9];
        var cracks = CrackOrder(material * 11 + 5);
        var colors = new List<Color>();
        for (int face = 0; face < 3; face++)
        {
            double light = face == 0 ? 1.0 : face == 1 ? 0.8 : 0.64;
            var texels = new Color[256];
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) texels[y * 16 + x] = Texel(material, face, x, y);
            if (face == 1) colors.AddRange(texels);
            for (int stage = 0; stage <= 8; stage++)
            {
                var pixels = (Color[])texels.Clone();
                int n = (int)Math.Round(cracks.Count * stage / 8.0);
                for (int i = 0; i < n; i++) { var (x, y) = cracks[i]; pixels[y * 16 + x] = Shade(pixels[y * 16 + x], 0.3); }
                for (int i = 0; i < 256; i++) pixels[i] = Shade(pixels[i], light);
                faces[face, stage] = Bitmap(pixels);
            }
        }
        Chips[material] = [colors[3], colors[40], colors[77], colors[130], colors[201], colors[250]];
        return Faces[material] = faces;
    }

    private static Color Texel(int material, int face, int x, int y)
    {
        double n = Rand(material * 5 + face, x, y);
        Color Pick(params string[] shades) => Color.Parse(shades[(int)(n * shades.Length)]);
        switch (Materials[material])
        {
            case "grass":
                if (face == 0) return Pick("#6DBA45", "#5DA83C", "#4F9632", "#7CC553");
                int jag = 2 + (Rand(90, x, 1) > 0.55 ? 1 : 0) + (Rand(91, x, 2) > 0.85 ? 1 : 0);
                if (y < jag) return Pick("#5DA83C", "#4F9632", "#468A2C");
                return n > 0.94 ? Color.Parse("#A77A52") : Pick("#8C5B37", "#7C4F2F", "#9A6841", "#6D4427");
            case "log":
                if (face == 0)
                {
                    double ring = Math.Max(Math.Abs(x - 7.5), Math.Abs(y - 7.5));
                    if (ring > 6.5) return Pick("#5C3E22", "#6E4B2A");
                    if (ring < 1) return Color.Parse("#C99A5E");
                    return (int)ring % 2 == 0 ? Color.Parse("#B98A50") : Color.Parse("#A67A43");
                }
                if (Rand(92, x, 3) > 0.8) return Color.Parse("#4A321B");
                return Color.Parse(((x + (int)(Rand(93, x, y / 5) * 2)) % 3) switch { 0 => "#6E4B2A", 1 => "#5C3E22", _ => "#7F5933" });
            default:
                var stone = n > 0.93 ? Color.Parse("#5E5E62") : n < 0.05 ? Color.Parse("#A9A9AC") : Pick("#8E8E91", "#7F7F83", "#9B9B9E", "#737377");
                if (Materials[material] != "ore") return stone;
                // Three ruby clusters per face; the Lads red with a bright facet.
                for (int c = 0; c < 3; c++)
                {
                    double ox = 3 + Rand(face, c, 7) * 10, oy = 3 + Rand(face, c, 8) * 10, d = Math.Sqrt((x + 0.5 - ox) * (x + 0.5 - ox) + (y + 0.5 - oy) * (y + 0.5 - oy));
                    if (d < 0.8) return Color.Parse("#FF9AA0");
                    if (d < 1.7 && n > 0.15) return Pick("#D13A45", "#B42A35", "#E8646C");
                    if (d < 2.3 && n > 0.55) return Color.Parse("#5C1F24");
                }
                return stone;
        }
    }

    /// <summary>Crack pixels radiating from the centre, nearest first: stage k shows the first k/8 of them.</summary>
    private static List<(int x, int y)> CrackOrder(int seed)
    {
        var order = new List<(int x, int y)>(); var seen = new HashSet<int>();
        for (int b = 0; b < 7; b++)
        {
            int x = 7 + (int)(Rand(seed, b, 1) * 3) - 1, y = 7 + (int)(Rand(seed, b, 2) * 3) - 1;
            double dir = Rand(seed, b, 3) * Math.PI * 2;
            for (int step = 0, len = 5 + (int)(Rand(seed, b, 4) * 6); step < len && x >= 0 && y >= 0 && x < 16 && y < 16; step++)
            {
                if (seen.Add(y * 16 + x)) order.Add((x, y));
                dir += (Rand(seed, b * 31 + step, 5) - 0.5) * 1.1;
                x += (int)Math.Round(Math.Cos(dir)); y += (int)Math.Round(Math.Sin(dir));
            }
        }
        order.Sort((a, c) => ((a.x - 7.5) * (a.x - 7.5) + (a.y - 7.5) * (a.y - 7.5)).CompareTo((c.x - 7.5) * (c.x - 7.5) + (c.y - 7.5) * (c.y - 7.5)));
        return order;
    }

    /// <summary>Handle and a curved head rasterised onto 16x16, then a dark pixel outline.</summary>
    private static WriteableBitmap MakePickaxe()
    {
        var pixels = new Color[256];
        for (int y = 0; y < 16; y++)
        for (int x = 0; x < 16; x++)
        {
            double px = x + 0.5, py = y + 0.5, dx = px - 3.2, dy = py - 12.8, r = Math.Sqrt(dx * dx + dy * dy);
            double off = Math.Abs(Math.Atan2(dy, dx) * 180 / Math.PI + 44);
            if (off <= 37 && Math.Abs(r - 10.3) <= 1.3 * (1 - 0.5 * off / 37))
                pixels[y * 16 + x] = Color.Parse(r > 10.8 ? "#E1E7EC" : r < 9.6 ? "#6F7780" : "#A9B1BA");
            else
            {
                double ax = 2.2, ay = 13.8, bx = 9.4, by = 6.6, len2 = (bx - ax) * (bx - ax) + (by - ay) * (by - ay);
                double u = Math.Clamp(((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / len2, 0, 1), qx = ax + u * (bx - ax) - px, qy = ay + u * (by - ay) - py;
                if (qx * qx + qy * qy < 0.8)
                    pixels[y * 16 + x] = Color.Parse(((bx - ax) * (py - ay) - (by - ay) * (px - ax)) > 0 ? "#8A5A2E" : "#B37B45");
            }
        }
        var outlined = (Color[])pixels.Clone();
        for (int y = 0; y < 16; y++)
        for (int x = 0; x < 16; x++)
        {
            if (pixels[y * 16 + x].A != 0) continue;
            bool edge = (x > 0 && pixels[y * 16 + x - 1].A != 0) || (x < 15 && pixels[y * 16 + x + 1].A != 0)
                || (y > 0 && pixels[(y - 1) * 16 + x].A != 0) || (y < 15 && pixels[(y + 1) * 16 + x].A != 0);
            if (edge) outlined[y * 16 + x] = Color.Parse("#1F2124");
        }
        return Bitmap(outlined);
    }

    private static Color Shade(Color c, double f) => c.A == 0 ? c : Color.FromArgb(c.A, (byte)(c.R * f), (byte)(c.G * f), (byte)(c.B * f));

    private static WriteableBitmap Bitmap(Color[] pixels)
    {
        var bitmap = new WriteableBitmap(new PixelSize(16, 16), new Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Premul);
        var raw = new byte[16 * 16 * 4];
        for (int i = 0; i < 256; i++)
        {
            var c = pixels[i]; // premultiplied: every texel is fully opaque or fully transparent
            raw[i * 4] = c.A == 0 ? (byte)0 : c.B; raw[i * 4 + 1] = c.A == 0 ? (byte)0 : c.G; raw[i * 4 + 2] = c.A == 0 ? (byte)0 : c.R; raw[i * 4 + 3] = c.A;
        }
        using (var buffer = bitmap.Lock())
            for (int y = 0; y < 16; y++) Marshal.Copy(raw, y * 64, buffer.Address + y * buffer.RowBytes, 64);
        return bitmap;
    }
}
