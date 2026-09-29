using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using Microsoft.Win32.SafeHandles;

namespace TheLadsLauncher.Services;

/// <summary>
/// File operations that never follow links by accident: final-path resolution, NTFS junctions without admin rights,
/// same-volume moves that never silently copy, verified copies and deletes that unlink reparse points first.
/// </summary>
public static class SafeFileOps
{
    private const uint GenericWrite = 0x40000000;
    private const uint ShareAll = 0x1 | 0x2 | 0x4;
    private const uint OpenExisting = 3;
    private const uint FlagBackupSemantics = 0x02000000;
    private const uint FlagOpenReparsePoint = 0x00200000;
    private const uint FsctlSetReparsePoint = 0x000900A4;
    private const uint ReparseTagMountPoint = 0xA0000003;
    private const uint FileSupportsReparsePoints = 0x80;
    private const int ErrorNotSameDevice = 17;

    public sealed record TreeListing(IReadOnlyList<string> Directories, IReadOnlyDictionary<string, long> Files, IReadOnlyList<string> Links);

    public static int Win32Error(Exception e) => e.HResult & 0xFFFF;

    /// <summary>True for junctions and symbolic links (not for cloud placeholders, which only carry the reparse attribute).</summary>
    public static bool IsLink(string path)
    {
        FileSystemInfo info = Directory.Exists(path) ? new DirectoryInfo(path) : new FileInfo(path);
        return info.Exists && info.LinkTarget != null;
    }

    /// <summary>Full path the link points to (relative symlink targets are resolved against the link's folder), or null.</summary>
    public static string? GetLinkTarget(string path)
    {
        FileSystemInfo info = Directory.Exists(path) ? new DirectoryInfo(path) : new FileInfo(path);
        var target = info.Exists ? info.LinkTarget : null;
        if (target == null) return null;
        var parent = Path.GetDirectoryName(Path.GetFullPath(path))!;
        return Path.TrimEndingDirectorySeparator(Path.GetFullPath(Path.Combine(parent, target)));
    }

    /// <summary>
    /// Resolves every link in the path (GetFinalPathNameByHandle). Missing trailing components, including a link whose
    /// target is gone, are appended to the resolved parent unchanged.
    /// </summary>
    public static string GetFinalPath(string path)
    {
        var full = Path.TrimEndingDirectorySeparator(Path.GetFullPath(path));
        using (var handle = CreateFileW(full, 0, ShareAll, IntPtr.Zero, OpenExisting, FlagBackupSemantics, IntPtr.Zero))
        {
            if (!handle.IsInvalid)
            {
                var buffer = new StringBuilder(512);
                while (true)
                {
                    var length = GetFinalPathNameByHandleW(handle, buffer, buffer.Capacity, 0);
                    if (length == 0) throw Win32IOException(Marshal.GetLastWin32Error(), $"Could not resolve '{full}'");
                    if (length < buffer.Capacity) break;
                    buffer.Capacity = length + 1;
                }
                var result = buffer.ToString();
                if (result.StartsWith(@"\\?\UNC\", StringComparison.OrdinalIgnoreCase)) return @"\\" + result[8..];
                return result.StartsWith(@"\\?\", StringComparison.Ordinal) ? result[4..] : result;
            }
            var error = Marshal.GetLastWin32Error();
            if (error is not (2 or 3)) throw Win32IOException(error, $"Could not resolve '{full}'");
        }
        var parent = Path.GetDirectoryName(full)
            ?? throw new DirectoryNotFoundException($"The drive of '{full}' does not exist.");
        return Path.Combine(GetFinalPath(parent), Path.GetFileName(full));
    }

    public static bool PathsEqual(string a, string b) =>
        string.Equals(Path.TrimEndingDirectorySeparator(Path.GetFullPath(a)), Path.TrimEndingDirectorySeparator(Path.GetFullPath(b)),
            StringComparison.OrdinalIgnoreCase);

    public static bool IsSameOrInside(string path, string root)
    {
        var p = Path.TrimEndingDirectorySeparator(Path.GetFullPath(path));
        var r = Path.TrimEndingDirectorySeparator(Path.GetFullPath(root));
        if (string.Equals(p, r, StringComparison.OrdinalIgnoreCase)) return true;
        var prefix = r.EndsWith(Path.DirectorySeparatorChar) ? r : r + Path.DirectorySeparatorChar;
        return p.StartsWith(prefix, StringComparison.OrdinalIgnoreCase);
    }

    public static bool IsNetworkPath(string finalPath)
    {
        if (finalPath.StartsWith(@"\\", StringComparison.Ordinal)) return true;
        var root = Path.GetPathRoot(finalPath);
        return !string.IsNullOrEmpty(root) && new DriveInfo(root).DriveType == DriveType.Network;
    }

    /// <summary>Whether the volume holding <paramref name="path"/> supports reparse points (NTFS/ReFS yes, FAT32/exFAT no).</summary>
    public static bool VolumeSupportsReparsePoints(string path)
    {
        var volume = new StringBuilder(1024);
        if (!GetVolumePathNameW(Path.GetFullPath(path), volume, volume.Capacity))
            throw Win32IOException(Marshal.GetLastWin32Error(), $"Could not find the volume of '{path}'");
        if (!GetVolumeInformationW(volume.ToString(), null, 0, out _, out _, out var flags, null, 0))
            throw Win32IOException(Marshal.GetLastWin32Error(), $"Could not read the file system of '{volume}'");
        return (flags & FileSupportsReparsePoints) != 0;
    }

    /// <summary>Creates an NTFS junction (no admin or Developer Mode needed). <paramref name="link"/> must be missing or an empty folder.</summary>
    public static void CreateJunction(string link, string target)
    {
        var targetFull = Path.GetFullPath(target);
        if (targetFull.StartsWith(@"\\", StringComparison.Ordinal))
            throw new IOException($"A folder link can only point to a local drive, not '{targetFull}'.");
        if (Path.GetPathRoot(targetFull) != targetFull) targetFull = Path.TrimEndingDirectorySeparator(targetFull);
        var created = !Directory.Exists(link);
        Directory.CreateDirectory(link);
        var done = false;
        try
        {
            using var handle = CreateFileW(link, GenericWrite, 0, IntPtr.Zero, OpenExisting, FlagBackupSemantics | FlagOpenReparsePoint, IntPtr.Zero);
            if (handle.IsInvalid) throw Win32IOException(Marshal.GetLastWin32Error(), $"Could not open '{link}'");
            var substitute = Encoding.Unicode.GetBytes(@"\??\" + targetFull);
            var print = Encoding.Unicode.GetBytes(targetFull);
            using var buffer = new MemoryStream();
            using (var writer = new BinaryWriter(buffer, Encoding.Unicode, leaveOpen: true))
            {
                writer.Write(ReparseTagMountPoint);
                writer.Write((ushort)(8 + substitute.Length + 2 + print.Length + 2));
                writer.Write((ushort)0);
                writer.Write((ushort)0);
                writer.Write((ushort)substitute.Length);
                writer.Write((ushort)(substitute.Length + 2));
                writer.Write((ushort)print.Length);
                writer.Write(substitute); writer.Write((ushort)0);
                writer.Write(print); writer.Write((ushort)0);
            }
            var data = buffer.ToArray();
            if (!DeviceIoControl(handle, FsctlSetReparsePoint, data, data.Length, IntPtr.Zero, 0, out _, IntPtr.Zero))
                throw Win32IOException(Marshal.GetLastWin32Error(), $"Could not link '{link}' to '{targetFull}'");
            done = true;
        }
        finally
        {
            if (!done && created) Directory.Delete(link);
        }
    }

    /// <summary>Removes a junction or symbolic link itself; the content it points to is never touched.</summary>
    public static void RemoveLink(string link)
    {
        if (!IsLink(link)) throw new InvalidOperationException($"'{link}' is not a link.");
        if (Directory.Exists(link)) Directory.Delete(link, recursive: false);
        else File.Delete(link);
    }

    /// <summary>
    /// Renames a file or folder without ever copying or replacing (MoveFileExW flags 0). Returns false, changing nothing,
    /// when the destination is on another volume; sharing violations (antivirus) are retried.
    /// </summary>
    public static bool MoveNoCopy(string source, string destination)
    {
        for (var attempt = 0; ; attempt++)
        {
            if (MoveFileExW(source, destination, 0)) return true;
            var error = Marshal.GetLastWin32Error();
            if (error == ErrorNotSameDevice) return false;
            if (error is 32 or 33 && attempt < 5) { Thread.Sleep(200); continue; }
            throw Win32IOException(error, $"Could not move '{source}' to '{destination}'");
        }
    }

    /// <summary>
    /// Moves <paramref name="source"/> into <paramref name="directory"/> as <paramref name="fileName"/>, or as
    /// "name (2).ext", "name (3).ext"... when that name is taken. Never replaces anything; returns the final path.
    /// </summary>
    public static string MoveToFreeName(string source, string directory, string fileName)
    {
        if (!IsPlainFileName(fileName))
            throw new ArgumentException($"'{fileName}' is not a plain file name inside '{directory}'; nothing was moved.", nameof(fileName));
        var stem = Path.GetFileNameWithoutExtension(fileName);
        var extension = Path.GetExtension(fileName);
        for (var n = 1; ; n++)
        {
            var candidate = Path.Combine(directory, n == 1 ? fileName : $"{stem} ({n}){extension}");
            if (File.Exists(candidate) || Directory.Exists(candidate)) continue;
            try
            {
                if (MoveNoCopy(source, candidate)) return candidate;
                throw new IOException($"'{source}' is on another drive than '{directory}'; nothing was moved.");
            }
            catch (IOException e) when (Win32Error(e) is 80 or 183)
            {
                // Taken between the check and the move: the next number is tried.
            }
        }
    }

    /// <summary>One file name that stays in the folder it is combined with: no folder parts, not "." or "..", no invalid characters.</summary>
    public static bool IsPlainFileName(string? name) =>
        !string.IsNullOrWhiteSpace(name) && name.Trim('.', ' ').Length > 0 && name.IndexOfAny(Path.GetInvalidFileNameChars()) < 0;

    /// <summary>Lists a file tree without following links; links are reported instead of entered.</summary>
    public static TreeListing ListTree(string root)
    {
        var directories = new List<string>();
        var files = new Dictionary<string, long>(StringComparer.OrdinalIgnoreCase);
        var links = new List<string>();
        var options = new EnumerationOptions { AttributesToSkip = 0, IgnoreInaccessible = false };
        void Walk(string dir)
        {
            foreach (var entry in new DirectoryInfo(dir).EnumerateFileSystemInfos("*", options))
            {
                var relative = Path.GetRelativePath(root, entry.FullName);
                if (entry.LinkTarget != null) links.Add(relative);
                else if (entry is DirectoryInfo sub) { directories.Add(relative); Walk(sub.FullName); }
                else files[relative] = ((FileInfo)entry).Length;
            }
        }
        Walk(root);
        return new TreeListing(directories, files, links);
    }

    public static string Sha256(string file)
    {
        using var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete, 1 << 16, FileOptions.SequentialScan);
        return Convert.ToHexString(SHA256.HashData(stream));
    }

    /// <summary>Copies a file or folder to a new path and proves the copy (same files, sizes and SHA-256) before returning.</summary>
    public static void CopyVerified(string source, string destination)
    {
        if (IsLink(source)) throw new IOException($"'{source}' is a link; move it by hand.");
        if (File.Exists(source))
        {
            File.Copy(source, destination, overwrite: false);
            if (new FileInfo(source).Length != new FileInfo(destination).Length || Sha256(source) != Sha256(destination))
                throw new InvalidDataException($"The copy of '{source}' at '{destination}' does not match the original.");
            return;
        }
        var tree = ListTree(source);
        if (tree.Links.Count > 0)
            throw new IOException($"'{source}' contains links ({string.Join(", ", tree.Links.Take(3))}); move it by hand.");
        Directory.CreateDirectory(destination);
        foreach (var dir in tree.Directories) Directory.CreateDirectory(Path.Combine(destination, dir));
        foreach (var file in tree.Files.Keys) File.Copy(Path.Combine(source, file), Path.Combine(destination, file), overwrite: false);
        var copy = ListTree(destination);
        if (copy.Links.Count > 0 || copy.Files.Count != tree.Files.Count
            || tree.Files.Any(f => !copy.Files.TryGetValue(f.Key, out var length) || length != f.Value
                || Sha256(Path.Combine(source, f.Key)) != Sha256(Path.Combine(destination, f.Key))))
            throw new InvalidDataException($"The copy of '{source}' at '{destination}' does not match the original.");
    }

    /// <summary>Recursive delete that removes links (not their targets) before descending, so shared content is never reached.</summary>
    public static void DeleteTree(string path)
    {
        if (IsLink(path)) { RemoveLink(path); return; }
        if (File.Exists(path))
        {
            File.SetAttributes(path, FileAttributes.Normal);
            File.Delete(path);
            return;
        }
        if (!Directory.Exists(path)) return;
        foreach (var entry in Directory.EnumerateFileSystemEntries(path, "*", new EnumerationOptions { AttributesToSkip = 0 }))
            DeleteTree(entry);
        var info = new DirectoryInfo(path);
        info.Attributes &= ~FileAttributes.ReadOnly;
        info.Delete(recursive: false);
    }

    // SHFileOperation flags (shellapi.h).
    public const int FofSilent = 0x4, FofNoConfirmation = 0x10, FofAllowUndo = 0x40, FofNoErrorUi = 0x400, FofNoConnectedElements = 0x2000,
        FofWantNukeWarning = 0x4000;

    /// <summary>Recycle with no shell prompts of its own (the caller already asked), except the one that matters: the shell warns
    /// before destroying an item it cannot recycle (too big for the bin, bin turned off, a drive without one).</summary>
    public const int RecycleFlags = FofAllowUndo | FofNoConfirmation | FofSilent | FofNoConnectedElements | FofWantNukeWarning;

    /// <summary>
    /// Sends a file or folder to the Recycle Bin. Refuses shared-folder links and migration folders that still hold
    /// pending content. Asking the user for confirmation is the caller's job. The real path (links resolved) goes to the shell,
    /// so an item reached through a junction is recycled on its own drive. When the shell cannot recycle it, the user is
    /// warned before a permanent delete; answering No throws <see cref="OperationCanceledException"/> and nothing is deleted.
    /// </summary>
    /// <param name="shell">Test seam for <see cref="ShellDelete"/>: (path, flags) → (shell result, cancelled by the user).</param>
    public static void DeleteToRecycleBin(string path, Func<string, int, (int Result, bool Aborted)>? shell = null)
    {
        var full = Path.TrimEndingDirectorySeparator(Path.GetFullPath(path));
        if (IsLink(full))
            throw new InvalidOperationException($"'{full}' is a shared folder link, not a folder of its own. Open the shared folder to manage its content.");
        if (HoldsPendingMigration(full))
            throw new InvalidOperationException($"'{full}' still holds content waiting to be moved into the shared folders. Launch the profile (or use Sync) to finish the move first.");
        var target = GetFinalPath(full);
        var (result, aborted) = (shell ?? ShellDelete)(target, RecycleFlags);
        if (aborted)
            throw new OperationCanceledException($"'{Path.GetFileName(full)}' was not deleted: Windows could not move it to the Recycle Bin, and it was not deleted permanently.");
        if (result != 0)
            throw new IOException($"Windows could not move '{target}' to the Recycle Bin (shell error 0x{result:X}). Nothing was deleted.");
        if (File.Exists(target) || Directory.Exists(target))
            throw new IOException($"'{target}' is still there after moving it to the Recycle Bin. Check it in File Explorer.");
    }

    /// <summary>SHFileOperation FO_DELETE on one path: (result, the user cancelled at a shell prompt). x64 structure layout
    /// (the launcher ships win-x64 only).</summary>
    public static (int Result, bool Aborted) ShellDelete(string path, int flags)
    {
        var operation = new ShFileOperation { Func = 3 /* FO_DELETE */, From = path + "\0", Flags = (ushort)flags };
        var result = SHFileOperationW(ref operation);
        return (result, operation.AnyOperationsAborted);
    }

    private static bool HoldsPendingMigration(string full)
    {
        var parts = full.Split(Path.DirectorySeparatorChar);
        var index = Array.FindIndex(parts, p => p.Equals(SharedContentService.MigrationFolderName, StringComparison.OrdinalIgnoreCase));
        if (index < 0) return false;
        if (parts.Skip(index + 1).Any(p => p.Equals("pending", StringComparison.OrdinalIgnoreCase))) return true;
        if (!Directory.Exists(full)) return false;
        var options = new EnumerationOptions { RecurseSubdirectories = true, MaxRecursionDepth = 2, AttributesToSkip = FileAttributes.ReparsePoint };
        return Directory.EnumerateDirectories(full, "pending", options).Any(p => Directory.EnumerateFileSystemEntries(p).Any());
    }

    private static IOException Win32IOException(int error, string action) =>
        new($"{action}: {new Win32Exception(error).Message}", unchecked((int)0x80070000) | error);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct ShFileOperation
    {
        public IntPtr Hwnd;
        public uint Func;
        [MarshalAs(UnmanagedType.LPWStr)] public string From; // double-null terminated
        [MarshalAs(UnmanagedType.LPWStr)] public string? To;
        public ushort Flags;
        [MarshalAs(UnmanagedType.Bool)] public bool AnyOperationsAborted;
        public IntPtr NameMappings;
        [MarshalAs(UnmanagedType.LPWStr)] public string? ProgressTitle;
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern int SHFileOperationW(ref ShFileOperation operation);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern SafeFileHandle CreateFileW(string name, uint access, uint share, IntPtr security, uint disposition, uint flags, IntPtr template);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern int GetFinalPathNameByHandleW(SafeFileHandle handle, StringBuilder path, int length, uint flags);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool DeviceIoControl(SafeFileHandle handle, uint code, byte[] input, int inputLength, IntPtr output, int outputLength,
        out int returned, IntPtr overlapped);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool MoveFileExW(string existing, string destination, uint flags);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetVolumePathNameW(string path, StringBuilder volume, int length);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetVolumeInformationW(string root, StringBuilder? name, int nameLength, out uint serial, out uint maxComponent,
        out uint flags, StringBuilder? fileSystem, int fileSystemLength);
}
