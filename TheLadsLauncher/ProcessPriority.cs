using System;
using System.ComponentModel;
using System.Diagnostics;

namespace TheLadsLauncher;

public static class ProcessPriority
{
    private static readonly object Gate = new();

    /// <summary>The class this process had before the active <see cref="Scope"/> changed it; null while no scope is active.
    /// A child inherits a BelowNormal parent's class, so game processes use this to keep the priority they would have had.</summary>
    public static ProcessPriorityClass? ScopedFrom { get; private set; }

    /// <summary>Sets this process to <paramref name="priority"/> until the handle is disposed, then restores the previous class. Never throws.</summary>
    public static ScopeHandle Scope(ProcessPriorityClass priority)
    {
        lock (Gate)
        {
            try
            {
                using var process = Process.GetCurrentProcess();
                var previous = process.PriorityClass;
                process.PriorityClass = priority;
                bool owns = ScopedFrom == null;   // a nested scope keeps the outermost original
                if (owns) ScopedFrom = previous;
                return new ScopeHandle(previous, owns, active: true);
            }
            catch (Exception ex) when (ex is Win32Exception or InvalidOperationException) { return new ScopeHandle(default, false, active: false); }
        }
    }

    /// <summary>Changes the priority of a process without a scope; failures are ignored.</summary>
    public static void TrySet(Process process, ProcessPriorityClass priority)
    {
        try { process.PriorityClass = priority; }
        catch (Exception ex) when (ex is Win32Exception or InvalidOperationException) { }
    }

    public sealed class ScopeHandle(ProcessPriorityClass previous, bool owns, bool active) : IDisposable
    {
        private bool _disposed = !active;   // a scope that could not change the priority has nothing to lower or restore

        /// <summary>Moves the process to <paramref name="priority"/> while the scope is active; the original class is still restored on dispose.</summary>
        public void Lower(ProcessPriorityClass priority)
        {
            lock (Gate)
            {
                if (_disposed) return;
                using var process = Process.GetCurrentProcess();
                TrySet(process, priority);
            }
        }

        public void Dispose()
        {
            lock (Gate)
            {
                if (_disposed) return;
                _disposed = true;
                using var process = Process.GetCurrentProcess();
                TrySet(process, previous);
                if (owns) ScopedFrom = null;
            }
        }
    }
}
