using System;
using System.IO;
using System.Linq;
using System.Text;

namespace TheLadsLauncher.Services;

/// <summary>What the Files editor knows about a file before showing it.</summary>
public enum FileOpenKind { Text, Image, Binary, TooLarge, Missing }

/// <summary>A decoded text file plus everything needed to write it back byte-compatible (encoding, BOM, line endings).</summary>
public sealed record TextDocument(string Text, Encoding Encoding, bool HasBom, string LineEnding, bool MixedLineEndings, DateTime LastWrite, long Length)
{
    public string EncodingLabel => Encoding.CodePage switch
    {
        65001 => HasBom ? "UTF-8 with BOM" : "UTF-8",
        1200 => "UTF-16 LE",
        1201 => "UTF-16 BE",
        28591 => "Latin-1",
        _ => Encoding.WebName.ToUpperInvariant()
    };

    public string LineEndingLabel => MixedLineEndings ? "Mixed" : LineEnding switch { "\r\n" => "CRLF", "\r" => "CR", _ => "LF" };
}

/// <summary>
/// Text/binary detection, encoding-preserving decode and atomic save for the Files tab's built-in editor. Pure file logic
/// (no UI), so it is unit-tested.
/// </summary>
public static class TextFileCodec
{
    public const long MaxEditableBytes = 5L * 1024 * 1024;
    public const long MaxImageBytes = 64L * 1024 * 1024;
    private const int SniffBytes = 8192;
    private static readonly string[] ImageExtensions = { ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".ico" };

    public static bool IsImageExtension(string path) =>
        ImageExtensions.Contains(Path.GetExtension(path), StringComparer.OrdinalIgnoreCase);

    /// <summary>Text, image, binary (a NUL byte in the first 8 KB without a UTF-16 BOM) or too large to edit.</summary>
    public static FileOpenKind Classify(string path)
    {
        var info = new FileInfo(path);
        if (!info.Exists) return FileOpenKind.Missing;
        if (IsImageExtension(path)) return info.Length > MaxImageBytes ? FileOpenKind.TooLarge : FileOpenKind.Image;
        if (info.Length > MaxEditableBytes) return FileOpenKind.TooLarge;
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        var head = new byte[(int)Math.Min(SniffBytes, info.Length)];
        int read = stream.ReadAtLeast(head, head.Length, throwOnEndOfStream: false);
        return LooksBinary(head.AsSpan(0, read)) ? FileOpenKind.Binary : FileOpenKind.Text;
    }

    public static bool LooksBinary(ReadOnlySpan<byte> head)
    {
        if (DetectBom(head) is { Encoding.CodePage: 1200 or 1201 }) return false; // UTF-16 text is full of NULs
        return head.IndexOf((byte)0) >= 0;
    }

    private static (Encoding Encoding, int Length)? DetectBom(ReadOnlySpan<byte> bytes)
    {
        if (bytes.Length >= 3 && bytes[0] == 0xEF && bytes[1] == 0xBB && bytes[2] == 0xBF) return (new UTF8Encoding(true), 3);
        if (bytes.Length >= 2 && bytes[0] == 0xFF && bytes[1] == 0xFE) return (new UnicodeEncoding(false, true), 2);
        if (bytes.Length >= 2 && bytes[0] == 0xFE && bytes[1] == 0xFF) return (new UnicodeEncoding(true, true), 2);
        return null;
    }

    /// <summary>
    /// BOM first; otherwise strict UTF-8; otherwise Latin-1, which maps every byte to one char so an unknown legacy file
    /// is written back byte-for-byte.
    /// </summary>
    public static TextDocument Decode(byte[] bytes, DateTime lastWrite = default)
    {
        Encoding encoding;
        bool bom = false;
        string text;
        if (DetectBom(bytes) is { } detected)
        {
            encoding = detected.Encoding;
            bom = true;
            text = encoding.GetString(bytes, detected.Length, bytes.Length - detected.Length);
        }
        else
        {
            try
            {
                encoding = new UTF8Encoding(false, true);
                text = encoding.GetString(bytes);
            }
            catch (DecoderFallbackException)
            {
                encoding = Encoding.Latin1;
                text = encoding.GetString(bytes);
            }
        }
        var (ending, mixed) = DetectLineEnding(text);
        return new TextDocument(text, encoding, bom, ending, mixed, lastWrite, bytes.LongLength);
    }

    public static TextDocument Load(string path)
    {
        byte[] bytes;
        using (var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
        {
            bytes = new byte[stream.Length];
            stream.ReadExactly(bytes);
        }
        return Decode(bytes, File.GetLastWriteTimeUtc(path));
    }

    /// <summary>The most common line ending (CRLF, LF or CR); LF for a file without line breaks on non-Windows content.</summary>
    public static (string Ending, bool Mixed) DetectLineEnding(string text)
    {
        int crlf = 0, lf = 0, cr = 0;
        for (int i = 0; i < text.Length; i++)
        {
            if (text[i] == '\r')
            {
                if (i + 1 < text.Length && text[i + 1] == '\n') { crlf++; i++; }
                else cr++;
            }
            else if (text[i] == '\n') lf++;
        }
        int kinds = (crlf > 0 ? 1 : 0) + (lf > 0 ? 1 : 0) + (cr > 0 ? 1 : 0);
        string ending = crlf == 0 && lf == 0 && cr == 0 ? Environment.NewLine
            : crlf >= lf && crlf >= cr ? "\r\n" : lf >= cr ? "\n" : "\r";
        return (ending, kinds > 1);
    }

    /// <summary>Every line break as <paramref name="ending"/>.</summary>
    public static string NormalizeLineEndings(string text, string ending) =>
        text.Replace("\r\n", "\n").Replace('\r', '\n').Replace("\n", ending);

    /// <summary>
    /// The bytes to write: the document's encoding and BOM, and its line ending applied to every break unless the original
    /// file mixed them (then the text is written as edited, so untouched lines keep theirs).
    /// </summary>
    public static byte[] Encode(TextDocument original, string text)
    {
        if (!original.MixedLineEndings) text = NormalizeLineEndings(text, original.LineEnding);
        var body = original.Encoding.GetBytes(text);
        if (!original.HasBom) return body;
        var preamble = original.Encoding.GetPreamble();
        if (preamble.Length == 0) return body;
        var result = new byte[preamble.Length + body.Length];
        preamble.CopyTo(result, 0);
        body.CopyTo(result, preamble.Length);
        return result;
    }

    /// <summary>
    /// Writes next to the file and swaps it in, so a crash or a full disk never leaves a half-written file. Refuses paths
    /// outside <paramref name="root"/> (the profile's game folder the Files tab is limited to).
    /// </summary>
    public static void SaveAtomic(string path, byte[] bytes, string root)
    {
        if (!SafeFileOps.IsSameOrInside(path, root) || SafeFileOps.PathsEqual(path, root))
            throw new UnauthorizedAccessException($"'{path}' is outside the profile folder.");
        string directory = Path.GetDirectoryName(Path.GetFullPath(path))!;
        string staging = Path.Combine(directory, "." + Path.GetFileName(path) + "." + Guid.NewGuid().ToString("N")[..8] + ".lads-tmp");
        try
        {
            using (var stream = new FileStream(staging, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            {
                stream.Write(bytes);
                stream.Flush(true);
            }
            if (File.Exists(path))
            {
                try { File.Replace(staging, path, null, ignoreMetadataErrors: true); } // keeps the original's attributes and ACL
                catch (PlatformNotSupportedException) { File.Move(staging, path, true); }
            }
            else File.Move(staging, path);
        }
        finally
        {
            try { if (File.Exists(staging)) File.Delete(staging); } catch (IOException) { } catch (UnauthorizedAccessException) { }
        }
    }
}
