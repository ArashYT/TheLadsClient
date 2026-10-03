using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public enum NbtTagType : byte { End, Byte, Short, Int, Long, Float, Double, ByteArray, String, List, Compound, IntArray, LongArray }

public abstract class NbtTag
{
    public abstract NbtTagType Type { get; }
}

/// <summary>Byte, Short, Int, Long, Float or Double. Floats keep their raw IEEE bits so a round trip is byte-identical.</summary>
public sealed class NbtNumber : NbtTag
{
    public NbtNumber(NbtTagType type, long rawValue)
    {
        if (type is < NbtTagType.Byte or > NbtTagType.Double) throw new ArgumentOutOfRangeException(nameof(type));
        Type = type;
        RawValue = rawValue;
    }

    public override NbtTagType Type { get; }
    public long RawValue { get; }
}

public sealed class NbtString : NbtTag
{
    public NbtString(string value) => Value = value;
    public override NbtTagType Type => NbtTagType.String;
    public string Value { get; set; }
}

/// <summary>ByteArray, IntArray or LongArray, kept as its big-endian payload.</summary>
public sealed class NbtArray : NbtTag
{
    public NbtArray(NbtTagType type, byte[] payload)
    {
        if (type is not (NbtTagType.ByteArray or NbtTagType.IntArray or NbtTagType.LongArray)) throw new ArgumentOutOfRangeException(nameof(type));
        Type = type;
        Payload = payload;
    }

    public override NbtTagType Type { get; }
    public byte[] Payload { get; }
}

public sealed class NbtList : NbtTag
{
    public NbtList(NbtTagType elementType) => ElementType = elementType;
    public override NbtTagType Type => NbtTagType.List;
    /// <summary>Minecraft writes End for an empty list; it is kept so the file round-trips exactly.</summary>
    public NbtTagType ElementType { get; set; }
    public List<NbtTag> Items { get; } = new();
}

public sealed class NbtCompound : NbtTag
{
    public override NbtTagType Type => NbtTagType.Compound;
    /// <summary>Entries in file order (order is preserved on write).</summary>
    public List<KeyValuePair<string, NbtTag>> Entries { get; } = new();

    public NbtTag? this[string name]
    {
        get => Entries.FirstOrDefault(e => e.Key == name).Value;
        set
        {
            var index = Entries.FindIndex(e => e.Key == name);
            if (value == null) { if (index >= 0) Entries.RemoveAt(index); }
            else if (index >= 0) Entries[index] = new(name, value);
            else Entries.Add(new(name, value));
        }
    }
}

/// <summary>
/// Full-fidelity reader/writer for Minecraft's NBT format: big-endian, Java modified UTF-8 strings, gzip detected on read.
/// Unmodified input written back is byte-identical. Anything the writer could not reproduce exactly is rejected as invalid.
/// </summary>
public static class Nbt
{
    private const int MaxDepth = 512;
    // A real server list with ~30 servers and icons is ~220 KB; this bounds corrupt or hostile (gzip-bomb) input.
    public const int MaxBytes = 32 * 1024 * 1024;

    internal static byte[] ReadCapped(Stream source)
    {
        using var copy = new MemoryStream();
        var buffer = new byte[81920];
        int read;
        while ((read = source.Read(buffer, 0, buffer.Length)) > 0)
        {
            if (copy.Length + read > MaxBytes)
                throw new InvalidDataException($"The NBT data is larger than {MaxBytes / (1024 * 1024)} MB, which no server list reaches.");
            copy.Write(buffer, 0, read);
        }
        return copy.ToArray();
    }

    public static (string RootName, NbtCompound Root) Read(byte[] data)
    {
        if (data.Length >= 2 && data[0] == 0x1F && data[1] == 0x8B)
        {
            using var gzip = new GZipStream(new MemoryStream(data), CompressionMode.Decompress);
            data = ReadCapped(gzip);
        }
        var reader = new Reader(data);
        if ((NbtTagType)reader.Byte() != NbtTagType.Compound) throw new InvalidDataException("The NBT root is not a compound tag.");
        var name = reader.String();
        var root = (NbtCompound)reader.Payload(NbtTagType.Compound, 0);
        if (reader.Position != data.Length) throw new InvalidDataException($"Unexpected data after the NBT root at byte {reader.Position}.");
        return (name, root);
    }

    public static byte[] Write(NbtCompound root, string rootName = "")
    {
        using var stream = new MemoryStream();
        stream.WriteByte((byte)NbtTagType.Compound);
        WriteString(stream, rootName);
        WritePayload(stream, root);
        return stream.ToArray();
    }

    public static byte[] EncodeModifiedUtf8(string value)
    {
        var bytes = new List<byte>(value.Length);
        foreach (var c in value)
        {
            if (c is >= '\u0001' and <= '\u007F') bytes.Add((byte)c);
            else if (c <= '߿') { bytes.Add((byte)(0xC0 | (c >> 6))); bytes.Add((byte)(0x80 | (c & 0x3F))); }
            else { bytes.Add((byte)(0xE0 | (c >> 12))); bytes.Add((byte)(0x80 | ((c >> 6) & 0x3F))); bytes.Add((byte)(0x80 | (c & 0x3F))); }
        }
        return bytes.ToArray();
    }

    public static string DecodeModifiedUtf8(ReadOnlySpan<byte> bytes)
    {
        var text = new StringBuilder(bytes.Length);
        for (var i = 0; i < bytes.Length;)
        {
            int b = bytes[i];
            if (b is > 0 and < 0x80) { text.Append((char)b); i++; continue; }
            if ((b & 0xE0) == 0xC0 && i + 1 < bytes.Length && (bytes[i + 1] & 0xC0) == 0x80)
            {
                var c = ((b & 0x1F) << 6) | (bytes[i + 1] & 0x3F);
                if (c is not 0 and < 0x80) throw new InvalidDataException("Overlong modified UTF-8 string.");
                text.Append((char)c); i += 2; continue;
            }
            if ((b & 0xF0) == 0xE0 && i + 2 < bytes.Length && (bytes[i + 1] & 0xC0) == 0x80 && (bytes[i + 2] & 0xC0) == 0x80)
            {
                var c = ((b & 0x0F) << 12) | ((bytes[i + 1] & 0x3F) << 6) | (bytes[i + 2] & 0x3F);
                if (c < 0x800) throw new InvalidDataException("Overlong modified UTF-8 string.");
                text.Append((char)c); i += 3; continue;
            }
            throw new InvalidDataException($"Invalid modified UTF-8 byte 0x{b:X2} in an NBT string.");
        }
        return text.ToString();
    }

    private static void WriteString(Stream stream, string value)
    {
        var bytes = EncodeModifiedUtf8(value);
        if (bytes.Length > ushort.MaxValue) throw new InvalidDataException("An NBT string is longer than 65535 bytes.");
        WriteBigEndian(stream, bytes.Length, 2);
        stream.Write(bytes);
    }

    private static void WritePayload(Stream stream, NbtTag tag)
    {
        switch (tag)
        {
            case NbtNumber number:
                WriteBigEndian(stream, number.RawValue, number.Type switch
                {
                    NbtTagType.Byte => 1, NbtTagType.Short => 2, NbtTagType.Int or NbtTagType.Float => 4, _ => 8
                });
                break;
            case NbtString text:
                WriteString(stream, text.Value);
                break;
            case NbtArray array:
                WriteBigEndian(stream, array.Payload.Length / ElementSize(array.Type), 4);
                stream.Write(array.Payload);
                break;
            case NbtList list:
                if (list.Items.Any(item => item.Type != list.ElementType))
                    throw new InvalidDataException($"An NBT list of {list.ElementType} holds another tag type.");
                stream.WriteByte((byte)list.ElementType);
                WriteBigEndian(stream, list.Items.Count, 4);
                foreach (var item in list.Items) WritePayload(stream, item);
                break;
            case NbtCompound compound:
                foreach (var (name, value) in compound.Entries)
                {
                    stream.WriteByte((byte)value.Type);
                    WriteString(stream, name);
                    WritePayload(stream, value);
                }
                stream.WriteByte((byte)NbtTagType.End);
                break;
        }
    }

    private static void WriteBigEndian(Stream stream, long value, int size)
    {
        for (var shift = (size - 1) * 8; shift >= 0; shift -= 8) stream.WriteByte((byte)(value >> shift));
    }

    private static int ElementSize(NbtTagType type) => type switch { NbtTagType.ByteArray => 1, NbtTagType.IntArray => 4, _ => 8 };

    private sealed class Reader
    {
        private readonly byte[] _data;
        public Reader(byte[] data) => _data = data;
        public int Position { get; private set; }

        private ReadOnlySpan<byte> Take(long count)
        {
            if (count < 0 || count > _data.Length - Position) throw new InvalidDataException($"The NBT data ends early at byte {Position}.");
            var span = _data.AsSpan(Position, (int)count);
            Position += (int)count;
            return span;
        }

        public byte Byte() => Take(1)[0];

        public long BigEndian(int size)
        {
            long value = 0;
            foreach (var b in Take(size)) value = (value << 8) | b;
            // Sign-extend so Short/Int values keep their meaning; writing masks back to the same bytes.
            var unused = 64 - size * 8;
            return unused == 0 ? value : (value << unused) >> unused;
        }

        public string String() => DecodeModifiedUtf8(Take(BigEndian(2) & 0xFFFF));

        public NbtTag Payload(NbtTagType type, int depth)
        {
            if (depth > MaxDepth) throw new InvalidDataException("The NBT data is nested too deeply.");
            switch (type)
            {
                case NbtTagType.Byte: return new NbtNumber(type, BigEndian(1));
                case NbtTagType.Short: return new NbtNumber(type, BigEndian(2));
                case NbtTagType.Int: case NbtTagType.Float: return new NbtNumber(type, BigEndian(4));
                case NbtTagType.Long: case NbtTagType.Double: return new NbtNumber(type, BigEndian(8));
                case NbtTagType.String: return new NbtString(String());
                case NbtTagType.ByteArray: case NbtTagType.IntArray: case NbtTagType.LongArray:
                    var count = BigEndian(4);
                    if (count < 0) throw new InvalidDataException("Negative NBT array length.");
                    return new NbtArray(type, Take(count * ElementSize(type)).ToArray());
                case NbtTagType.List:
                    var elementType = (NbtTagType)Byte();
                    var length = BigEndian(4);
                    if (elementType > NbtTagType.LongArray || length < 0 || (elementType == NbtTagType.End && length > 0))
                        throw new InvalidDataException($"Invalid NBT list header ({elementType}, {length}).");
                    var list = new NbtList(elementType);
                    for (var i = 0; i < length; i++) list.Items.Add(Payload(elementType, depth + 1));
                    return list;
                case NbtTagType.Compound:
                    var compound = new NbtCompound();
                    while (true)
                    {
                        var entryType = (NbtTagType)Byte();
                        if (entryType == NbtTagType.End) return compound;
                        if (entryType > NbtTagType.LongArray) throw new InvalidDataException($"Unknown NBT tag type {(byte)entryType}.");
                        var name = String();
                        compound.Entries.Add(new(name, Payload(entryType, depth + 1)));
                    }
                default:
                    throw new InvalidDataException($"Unknown NBT tag type {(byte)type}.");
            }
        }
    }
}

public sealed record ServerListEntry(string Ip, string Name, bool Hidden, NbtCompound Raw);

/// <summary>Minecraft's servers.dat: a root compound with a "servers" list of {name, ip, icon, hidden, ...} compounds.</summary>
public sealed class ServerListFile
{
    public ServerListFile(string rootName, NbtCompound root)
    {
        if (root["servers"] is { } servers && (servers is not NbtList list || list.ElementType is not (NbtTagType.Compound or NbtTagType.End)))
            throw new InvalidDataException("'servers' is not a list of server entries.");
        RootName = rootName;
        Root = root;
    }

    public string RootName { get; }
    public NbtCompound Root { get; }

    public IReadOnlyList<ServerListEntry> Entries => Root["servers"] is NbtList list
        ? list.Items.Cast<NbtCompound>().Select(c => new ServerListEntry(Text(c, "ip"), Text(c, "name"),
            c["hidden"] is NbtNumber { Type: NbtTagType.Byte, RawValue: not 0 }, c)).ToList()
        : Array.Empty<ServerListEntry>();

    public static ServerListFile CreateEmpty() => new("", new NbtCompound { ["servers"] = new NbtList(NbtTagType.End) });

    public static ServerListFile Parse(byte[] data)
    {
        var (name, root) = Nbt.Read(data);
        return new ServerListFile(name, root);
    }

    /// <summary>Reads without blocking Minecraft's atomic replace (FileShare.ReadWrite | Delete). Invalid content throws InvalidDataException.</summary>
    public static ServerListFile Read(string path)
    {
        var data = ReadBytes(path);
        try
        {
            return Parse(data);
        }
        catch (InvalidDataException e)
        {
            throw new InvalidDataException($"The server list '{path}' could not be read: {e.Message}", e);
        }
    }

    public static byte[] ReadBytes(string path)
    {
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        return Nbt.ReadCapped(stream);
    }

    public void Add(NbtCompound entry)
    {
        if (Root["servers"] is not NbtList list) Root["servers"] = list = new NbtList(NbtTagType.Compound);
        list.ElementType = NbtTagType.Compound;
        list.Items.Add(entry);
    }

    /// <summary>Removes this exact entry (one of <see cref="Entries"/>' Raw compounds). False when it is not in the list.</summary>
    public bool Remove(NbtCompound entry) => Root["servers"] is NbtList list && list.Items.Remove(entry);

    public byte[] ToBytes() => Nbt.Write(Root, RootName);

    /// <summary>Atomic write: temp file next to the target, then File.Replace.</summary>
    public Task WriteAsync(string path, CancellationToken cancellationToken = default) =>
        LockFiles.WriteAtomicallyAsync(path, ToBytes(), cancellationToken);

    private static string Text(NbtCompound compound, string key) => compound[key] is NbtString s ? s.Value : "";
}
