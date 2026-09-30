using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.InteropServices;

namespace TheLadsLauncher.Services;

/// <summary>Reads only Modrinth's custom_dir setting, using the SQLite runtime shipped with Windows 10+.</summary>
internal static class ModrinthScreenshotSettings
{
    internal static string? ReadCustomDirectory(string file, ICollection<string>? warnings)
    {
        if (!File.Exists(file)) return null;
        IntPtr database = IntPtr.Zero, statement = IntPtr.Zero;
        try
        {
            if (sqlite3_open_v2(file, out database, 1 /* SQLITE_OPEN_READONLY */, IntPtr.Zero) != 0)
                throw new IOException("Could not open Modrinth settings read-only.");
            sqlite3_busy_timeout(database, 150);
            const string query = "SELECT custom_dir FROM settings LIMIT 1";
            if (sqlite3_prepare_v2(database, query, -1, out statement, IntPtr.Zero) != 0)
                throw new IOException("Modrinth settings use an unrecognized database schema. Add its instance folder manually.");
            int result = sqlite3_step(statement);
            if (result == 100 /* SQLITE_ROW */)
            {
                string? path = Marshal.PtrToStringUTF8(sqlite3_column_text(statement, 0));
                if (!string.IsNullOrWhiteSpace(path) && Path.IsPathFullyQualified(path)) return path;
            }
            else if (result != 101 /* SQLITE_DONE */) throw new IOException("Modrinth settings are busy or unreadable. Rescan after closing Modrinth, or add its instance folder.");
        }
        catch (Exception e) when (e is IOException or DllNotFoundException or EntryPointNotFoundException or UnauthorizedAccessException)
        { warnings?.Add("Modrinth: " + e.Message); }
        finally
        {
            if (statement != IntPtr.Zero) sqlite3_finalize(statement);
            if (database != IntPtr.Zero) sqlite3_close(database);
        }
        return null;
    }

    [DllImport("winsqlite3", CallingConvention = CallingConvention.Cdecl)] private static extern int sqlite3_open_v2([MarshalAs(UnmanagedType.LPUTF8Str)] string filename, out IntPtr db, int flags, IntPtr vfs);
    [DllImport("winsqlite3", CallingConvention = CallingConvention.Cdecl)] private static extern int sqlite3_busy_timeout(IntPtr db, int milliseconds);
    [DllImport("winsqlite3", CallingConvention = CallingConvention.Cdecl)] private static extern int sqlite3_prepare_v2(IntPtr db, [MarshalAs(UnmanagedType.LPUTF8Str)] string sql, int bytes, out IntPtr statement, IntPtr tail);
    [DllImport("winsqlite3", CallingConvention = CallingConvention.Cdecl)] private static extern int sqlite3_step(IntPtr statement);
    [DllImport("winsqlite3", CallingConvention = CallingConvention.Cdecl)] private static extern IntPtr sqlite3_column_text(IntPtr statement, int column);
    [DllImport("winsqlite3", CallingConvention = CallingConvention.Cdecl)] private static extern int sqlite3_finalize(IntPtr statement);
    [DllImport("winsqlite3", CallingConvention = CallingConvention.Cdecl)] private static extern int sqlite3_close(IntPtr db);
}
