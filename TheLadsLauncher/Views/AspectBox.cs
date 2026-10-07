using System;
using Avalonia;
using Avalonia.Controls;

namespace TheLadsLauncher.Views;

/// <summary>Sizes its child to the available width and a fixed aspect ratio (16:9 thumbnails in the responsive gallery grid).</summary>
public sealed class AspectBox : Decorator
{
    public static readonly StyledProperty<double> RatioProperty = AvaloniaProperty.Register<AspectBox, double>(nameof(Ratio), 16.0 / 9.0);

    static AspectBox() => AffectsMeasure<AspectBox>(RatioProperty);

    /// <summary>Width divided by height.</summary>
    public double Ratio { get => GetValue(RatioProperty); set => SetValue(RatioProperty, value); }

    protected override Size MeasureOverride(Size availableSize)
    {
        double width = double.IsInfinity(availableSize.Width) ? 320 : availableSize.Width;
        var size = new Size(width, Math.Floor(width / Math.Max(0.01, Ratio)));
        Child?.Measure(size);
        return size;
    }

    protected override Size ArrangeOverride(Size finalSize)
    {
        Child?.Arrange(new Rect(finalSize));
        return finalSize;
    }
}
