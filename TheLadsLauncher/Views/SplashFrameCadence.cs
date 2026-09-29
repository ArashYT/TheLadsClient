using System;
using System.Diagnostics;
using System.IO;
using System.Text.Json;

namespace TheLadsLauncher.Views;

/// <summary>Opt-in preview diagnostics of UI animation callbacks, not presented display frames.</summary>
internal sealed class SplashFrameCadence
{
    private const double WarmupSeconds = 2;
    private const double SampleSeconds = 10;
    private readonly string _outputPath;
    private readonly double[] _intervals = new double[8192];
    private double _first = -1;
    private double _last;
    private int _intervalCount;
    private bool _completed;

    public SplashFrameCadence(string outputPath) => _outputPath = Path.GetFullPath(outputPath);

    public void Sample(double seconds)
    {
        if (_completed || seconds < WarmupSeconds) return;
        if (_first < 0)
        {
            _first = _last = seconds;
            return;
        }
        if (seconds - _first >= SampleSeconds)
        {
            _completed = true;
            return;
        }
        if (_intervalCount < _intervals.Length)
            _intervals[_intervalCount++] = (seconds - _last) * 1000;
        _last = seconds;
    }

    public void WriteReport()
    {
        try
        {
            Array.Sort(_intervals, 0, _intervalCount);
            double duration = _first < 0 ? 0 : _last - _first;
            double? Percentile(double p) => _intervalCount == 0 ? null
                : _intervals[Math.Clamp((int)Math.Ceiling(_intervalCount * p) - 1, 0, _intervalCount - 1)];
            var report = new
            {
                metric = "Avalonia RequestAnimationFrame callback cadence; not presented display FPS",
                recordedAtUtc = DateTime.UtcNow,
                warmupSeconds = WarmupSeconds,
                requestedSampleSeconds = SampleSeconds,
                completed = _completed,
                sampleDurationSeconds = duration,
                callbackCount = _first < 0 ? 0 : _intervalCount + 1,
                callbacksPerSecond = duration > 0 ? _intervalCount / duration : 0,
                intervalMilliseconds = new { p50 = Percentile(0.5), p95 = Percentile(0.95), p99 = Percentile(0.99), max = Percentile(1) },
                intervalBufferFilled = _intervalCount == _intervals.Length
            };
            Directory.CreateDirectory(Path.GetDirectoryName(_outputPath)!);
            File.WriteAllText(_outputPath, JsonSerializer.Serialize(report, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch (Exception error)
        {
            Debug.WriteLine($"Could not save splash preview cadence: {error.Message}");
        }
    }
}
