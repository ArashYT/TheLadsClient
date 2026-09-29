using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>
/// Cross-process lock files shared with LadsCore (Java opens the same file and takes a FileChannel lock) and atomic writes.
/// Only sharing (32) and lock (33) violations are retried; any other I/O error fails immediately.
/// </summary>
public static class LockFiles
{
    public static bool IsSharingOrLockViolation(IOException e) => (e.HResult & 0xFFFF) is 32 or 33;

    public static async Task<FileStream> AcquireAsync(string lockPath, TimeSpan timeout, CancellationToken cancellationToken = default)
    {
        var fullPath = Path.GetFullPath(lockPath);
        Directory.CreateDirectory(Path.GetDirectoryName(fullPath)!);
        var deadline = DateTime.UtcNow + timeout;
        while (true)
        {
            try
            {
                return new FileStream(fullPath, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
            }
            catch (IOException e) when (IsSharingOrLockViolation(e))
            {
                if (DateTime.UtcNow >= deadline)
                    throw new IOException($"'{fullPath}' is in use by another Lads launcher or game. Close it and try again.", e);
                await Task.Delay(50, cancellationToken).ConfigureAwait(false);
            }
        }
    }

    /// <summary>Writes a temp file in the target directory, then File.Replace (or File.Move when the target is missing).</summary>
    public static async Task WriteAtomicallyAsync(string path, byte[] content, CancellationToken cancellationToken = default)
    {
        var fullPath = Path.GetFullPath(path);
        var directory = Path.GetDirectoryName(fullPath)!;
        Directory.CreateDirectory(directory);
        var temp = Path.Combine(directory, "." + Path.GetFileName(fullPath) + "." + Guid.NewGuid().ToString("N") + ".tmp");
        try
        {
            await using (var stream = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            {
                await stream.WriteAsync(content, cancellationToken).ConfigureAwait(false);
                await stream.FlushAsync(cancellationToken).ConfigureAwait(false);
                stream.Flush(flushToDisk: true);
            }
            for (var attempt = 0; ; attempt++)
            {
                try
                {
                    if (File.Exists(fullPath))
                        File.Replace(temp, fullPath, destinationBackupFileName: null);
                    else
                        File.Move(temp, fullPath);
                    return;
                }
                catch (IOException e) when (IsSharingOrLockViolation(e) && attempt < 20)
                {
                    await Task.Delay(50, cancellationToken).ConfigureAwait(false);
                }
            }
        }
        finally
        {
            if (File.Exists(temp))
                File.Delete(temp);
        }
    }
}
