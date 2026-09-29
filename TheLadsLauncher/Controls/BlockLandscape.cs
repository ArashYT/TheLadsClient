using System;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;

namespace TheLadsLauncher.Controls;

// A small, deterministic block illustration. Vector geometry stays crisp at every UI scale.
public sealed class BlockLandscape : Control
{
    private static readonly IBrush Top = Brush.Parse("#454348");
    private static readonly IBrush Left = Brush.Parse("#292A2F");
    private static readonly IBrush Right = Brush.Parse("#34343A");
    private static readonly Pen Edge = new(Brush.Parse("#5C5358"), .6);
    private static readonly IBrush RedTop = Brush.Parse("#B84C4D");
    private static readonly IBrush RedLeft = Brush.Parse("#622D34");
    private static readonly IBrush RedRight = Brush.Parse("#89393E");

    public override void Render(DrawingContext context)
    {
        base.Render(context);
        double unit = Math.Min(Bounds.Width / 22, Bounds.Height / 16);
        double cx = Bounds.Width * .51, cy = Bounds.Height * .27;
        using (context.PushClip(new Rect(Bounds.Size)))
        for (int z = 0; z < 10; z++)
        for (int x = 0; x < 10; x++)
        {
            if ((x < 2 && z < 3) || (x > 7 && z > 7) || (x == 0 && z > 7)) continue;
            int height = 1 + (int)(2.6 * Math.Exp(-(Math.Pow(x - 6, 2) + Math.Pow(z - 3, 2)) / 13));
            if ((x == 6 && z == 3) || (x == 5 && z == 3)) height++;
            double px = cx + (x - z) * unit, py = cy + (x + z) * unit * .48 - height * unit * .8;
            double floor = cy + (x + z) * unit * .48 + unit * 1.8;
            bool red = (x == 5 && z < 6) || (z == 5 && x > 4);
            Face(context, red ? RedLeft : Left, new(px-unit,py+unit*.48), new(px,py+unit*.96), new(px,floor+unit*.96), new(px-unit,floor+unit*.48));
            Face(context, red ? RedRight : Right, new(px,py+unit*.96), new(px+unit,py+unit*.48), new(px+unit,floor+unit*.48), new(px,floor+unit*.96));
            Face(context, red ? RedTop : Top, new(px,py), new(px+unit,py+unit*.48), new(px,py+unit*.96), new(px-unit,py+unit*.48));
        }
    }

    private static void Face(DrawingContext context, IBrush fill, params Point[] points)
    {
        var geometry = new StreamGeometry();
        using (var path = geometry.Open())
        {
            path.BeginFigure(points[0], true);
            for (int i = 1; i < points.Length; i++) path.LineTo(points[i]);
            path.EndFigure(true);
        }
        context.DrawGeometry(fill, Edge, geometry);
    }
}
