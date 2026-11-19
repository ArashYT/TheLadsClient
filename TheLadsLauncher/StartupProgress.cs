using System;

namespace TheLadsLauncher;

/// <summary>
/// The startup/update screen's percentage: a pure, frame-stepped easing of whatever the real work reports, so the bar and the number
/// move continuously from 0 to 100 and never jump. Real progress (a download) fills the bar up to 90%; with nothing reported it eases
/// toward 90% over about two seconds. Past ~80% it slows down, and if the work stalls it keeps creeping asymptotically toward 100%
/// without ever claiming to be done. <see cref="Step"/> with complete=true then finishes smoothly to exactly 1 in <see cref="FinishSeconds"/>.
/// </summary>
public sealed class StartupProgress
{
    public const double FinishSeconds = 0.4;
    private const double RealShare = 0.9, MaxSpeed = 0.45, Follow = 2.5, Creep = 0.06, EaseInSeconds = 2.2, MaxStep = 0.05;
    private double _value, _time, _finishFrom = -1, _finishTime;

    /// <param name="start">0 for a fresh screen; 1 for the launcher that starts after an update (the bridge left it at 100%).</param>
    public StartupProgress(double start = 0) => _value = Math.Clamp(start, 0, 1);

    /// <summary>Shown progress, 0..1; never decreases.</summary>
    public double Value => _value;

    /// <summary>The finish animation has reached 100%.</summary>
    public bool Finished => _value >= 1;

    /// <summary>The whole percent that matches the bar: 100 only once the bar is full.</summary>
    public static int Percent(double value) => (int)Math.Clamp(Math.Floor(value * 100 + 1e-9), 0, 100);

    /// <param name="seconds">Time since the previous step (clamped, so a stalled UI thread cannot make a jump).</param>
    /// <param name="real">Reported progress 0..1, or null while nothing is reported.</param>
    /// <param name="complete">The work is done: animate the rest of the way to 100%.</param>
    public double Step(double seconds, double? real, bool complete)
    {
        double dt = Math.Clamp(seconds, 0, MaxStep);
        _time += dt;
        if (_value >= 1) return _value;
        if (complete)
        {
            if (_finishFrom < 0) _finishFrom = _value;
            _finishTime += dt;
            double u = Math.Min(1, _finishTime / FinishSeconds);
            _value = Math.Max(_value, u >= 1 ? 1 : _finishFrom + (1 - _finishFrom) * u * u * (3 - 2 * u));
            return _value;
        }
        double target = real is { } r ? RealShare * Math.Clamp(r, 0, 1)
            : RealShare * (1 - Math.Pow(1 - Math.Min(1, _time / EaseInSeconds), 3));
        double slow = 1 - 0.6 * Smooth(0.8, 0.92, _value);   // slows down around 90%, never stops
        double speed = (_value < target ? Math.Min(MaxSpeed, (target - _value) * Follow) : 0) + Creep * (1 - _value);
        double ramp = 0.15 + 0.85 * Smooth(0, 0.5, _time);    // eases in from standstill
        _value = Math.Min(0.999, _value + speed * slow * ramp * dt);
        return _value;
    }

    private static double Smooth(double from, double to, double x)
    {
        double u = Math.Clamp((x - from) / (to - from), 0, 1);
        return u * u * (3 - 2 * u);
    }
}
