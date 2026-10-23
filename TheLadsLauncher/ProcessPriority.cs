using System;
using System.ComponentModel;
using System.Diagnostics;

namespace TheLadsLauncher;

public static class ProcessPriority
{
    /// <summary>Sets this process to <paramref name="priority"/> until the scope is disposed, then restores the previous class. Never throws.</summary>
    public static IDisposable Scope(ProcessPriorityClass priority)
    {
        try
        {
            var process = Process.GetCurrentProcess();
            var previous = process.PriorityClass;
            process.PriorityClass = priority;
            return new Restore(process, previous);
        }
        catch (Exception ex) when (ex is Win32Exception or InvalidOperationException) { return new Restore(null, priority); }
    }

    private sealed class Restore(Process? process, ProcessPriorityClass previous) : IDisposable
    {
        public void Dispose()
        {
            try { if (process != null) process.PriorityClass = previous; }
            catch (Exception ex) when (ex is Win32Exception or InvalidOperationException) { }
        }
    }
}
