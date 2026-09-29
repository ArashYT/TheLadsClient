using System.IO.Compression;
using System.Text;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class NbtTests
{
    // Java's DataOutputStream.writeUTF (modified UTF-8) bytes, written out by hand so the reader is checked against Java, not itself.
    private static readonly byte[] SectionSign = { 0xC2, 0xA7 };
    private static readonly byte[] Nul = { 0xC0, 0x80 };
    private static readonly byte[] GrinningFace = { 0xED, 0xA0, 0xBD, 0xED, 0xB8, 0x80 }; // U+1F600 as two 3-byte surrogates
    private static readonly byte[] GlowingStar = { 0xED, 0xA0, 0xBC, 0xED, 0xBC, 0x9F }; // U+1F31F
    private static readonly byte[] Snowman = { 0xE2, 0x98, 0x83 };

    /// <summary>Minimal big-endian NBT builder for fixtures.</summary>
    private sealed class Bytes
    {
        private readonly MemoryStream _s = new();
        public Bytes B(params byte[] b) { _s.Write(b); return this; }
        public Bytes Ascii(string text) => B(Encoding.ASCII.GetBytes(text));
        public Bytes Short(int v) => B((byte)(v >> 8), (byte)v);
        public Bytes Int(long v) => B((byte)(v >> 24), (byte)(v >> 16), (byte)(v >> 8), (byte)v);
        public Bytes Long(long v) => Int(v >> 32).Int(v);
        public Bytes Name(string ascii) => Short(ascii.Length).Ascii(ascii);
        public Bytes Str(params byte[][] parts) { var all = parts.SelectMany(p => p).ToArray(); return Short(all.Length).B(all); }
        public Bytes Tag(byte type, string name) => B(type).Name(name);
        public byte[] ToArray() => _s.ToArray();
    }

    private static byte[] A(string ascii) => Encoding.ASCII.GetBytes(ascii);

    private static byte[] Fixture() => new Bytes()
        .Tag(10, "")
        .Tag(1, "b").B(0xFB)
        .Tag(2, "s").Short(-300)
        .Tag(3, "i").Int(123456789)
        .Tag(4, "l").Long(-1234567890123)
        .Tag(5, "f").Int(0x7FC00001) // NaN with a payload: must survive bit for bit
        .Tag(6, "d").Long(unchecked((long)0x8000000000000000)) // -0.0
        .Tag(7, "ba").Int(3).B(1, 255, 0)
        .Tag(8, "text").Str(SectionSign, A("a Caf"), new byte[] { 0xC3, 0xA9 }, A(" "), GrinningFace, Nul, A(" "), Snowman)
        .Tag(9, "empty").B(0).Int(0)
        .Tag(9, "nested").B(9).Int(2).B(3).Int(2).Int(1).Int(2).B(0).Int(0)
        .Tag(10, "sub").Tag(8, "k").Short(0).B(0)
        .Tag(11, "ia").Int(2).Int(1).Int(-1)
        .Tag(12, "la").Int(1).Long(long.MinValue)
        .Tag(9, "servers").B(10).Int(2)
            .Tag(8, "ip").Str(A("mc.example.com"))
            .Tag(8, "name").Str(SectionSign, A("6Gold "), GlowingStar, A(" Server"))
            .Tag(1, "hidden").B(0)
            .B(0)
            .Tag(8, "name").Str(new byte[] { 0xC3, 0x9C }, A("n"), new byte[] { 0xC3, 0xAF }, A("c"), new byte[] { 0xC3, 0xB6 }, A("d"), new byte[] { 0xC3, 0xA9 })
            .Tag(8, "ip").Str(A("10.0.0.1:25566"))
            .Tag(8, "icon").Str(A("iVBORw0KGgo="))
            .Tag(1, "hidden").B(1)
            .B(0)
        .B(0)
        .ToArray();

    [Fact]
    public void RoundTripIsByteIdenticalIncludingJavaStringsAndRawFloats()
    {
        var original = Fixture();
        var (name, root) = Nbt.Read(original);
        Assert.Equal("", name);
        Assert.Equal("§a Café 😀\0 ☃", ((NbtString)root["text"]!).Value);
        Assert.Equal(-300, ((NbtNumber)root["s"]!).RawValue);
        Assert.Equal(NbtTagType.End, ((NbtList)root["empty"]!).ElementType);
        Assert.Equal(original, Nbt.Write(root, name));
    }

    [Fact]
    public void GzipBombIsRejectedAsUnreadableInsteadOfExhaustingMemory()
    {
        using var packed = new MemoryStream();
        using (var gzip = new GZipStream(packed, CompressionLevel.SmallestSize, leaveOpen: true))
        {
            var zeros = new byte[1024 * 1024];
            for (var i = 0; i <= Nbt.MaxBytes / zeros.Length; i++) gzip.Write(zeros);
        }
        Assert.True(packed.Length < 1024 * 1024); // a small file that would expand past the cap
        Assert.Throws<InvalidDataException>(() => Nbt.Read(packed.ToArray()));
    }

    [Fact]
    public void ServerEntriesKeepNamesHiddenFlagAndUnknownFields()
    {
        var file = ServerListFile.Parse(Fixture());
        Assert.Equal(2, file.Entries.Count);
        Assert.Equal(("mc.example.com", "§6Gold 🌟 Server", false), (file.Entries[0].Ip, file.Entries[0].Name, file.Entries[0].Hidden));
        Assert.Equal(("10.0.0.1:25566", "Ünïcödé", true), (file.Entries[1].Ip, file.Entries[1].Name, file.Entries[1].Hidden));
        Assert.Equal("iVBORw0KGgo=", ((NbtString)file.Entries[1].Raw["icon"]!).Value);
        Assert.Equal(Fixture(), file.ToBytes());
    }

    [Fact]
    public void GzipInputIsReadAndWrittenBackUncompressed()
    {
        var original = Fixture();
        using var compressed = new MemoryStream();
        using (var gzip = new GZipStream(compressed, CompressionLevel.Optimal, leaveOpen: true)) gzip.Write(original);
        var file = ServerListFile.Parse(compressed.ToArray());
        Assert.Equal(original, file.ToBytes());
    }

    [Fact]
    public void MinecraftsNineteenByteEmptyListRoundTrips()
    {
        var empty = new Bytes().Tag(10, "").Tag(9, "servers").B(0).Int(0).B(0).ToArray();
        Assert.Equal(19, empty.Length);
        var file = ServerListFile.Parse(empty);
        Assert.Empty(file.Entries);
        Assert.Equal(empty, file.ToBytes());
        Assert.Equal(empty, ServerListFile.CreateEmpty().ToBytes());
    }

    [Fact]
    public void AddingToAnEmptyListMakesItACompoundList()
    {
        var file = ServerListFile.CreateEmpty();
        file.Add(new NbtCompound { ["name"] = new NbtString("A"), ["ip"] = new NbtString("a.example") });
        var reread = ServerListFile.Parse(file.ToBytes());
        Assert.Equal("a.example", Assert.Single(reread.Entries).Ip);
    }

    [Fact]
    public void ModifiedUtf8MatchesJava()
    {
        Assert.Equal(Nul, Nbt.EncodeModifiedUtf8("\0"));
        Assert.Equal(GrinningFace, Nbt.EncodeModifiedUtf8("😀"));
        Assert.Equal(SectionSign, Nbt.EncodeModifiedUtf8("§"));
        Assert.Equal("😀", Nbt.DecodeModifiedUtf8(GrinningFace));
    }

    [Theory]
    [InlineData("standard-utf8-emoji")]
    [InlineData("raw-nul")]
    [InlineData("overlong")]
    [InlineData("truncated")]
    [InlineData("trailing")]
    [InlineData("not-compound")]
    [InlineData("list-of-end-with-items")]
    public void InvalidDataIsRejectedNotGuessed(string kind)
    {
        byte[] data = kind switch
        {
            "standard-utf8-emoji" => new Bytes().Tag(10, "").Tag(8, "n").Str(new byte[] { 0xF0, 0x9F, 0x98, 0x80 }).B(0).ToArray(),
            "raw-nul" => new Bytes().Tag(10, "").Tag(8, "n").Str(new byte[] { 0x00 }).B(0).ToArray(),
            "overlong" => new Bytes().Tag(10, "").Tag(8, "n").Str(new byte[] { 0xC1, 0x81 }).B(0).ToArray(),
            "truncated" => Fixture()[..^1],
            "trailing" => Fixture().Concat(new byte[] { 0 }).ToArray(),
            "not-compound" => new Bytes().Tag(8, "").Str(A("x")).ToArray(),
            _ => new Bytes().Tag(10, "").Tag(9, "l").B(0).Int(1).B(0).ToArray()
        };
        Assert.Throws<InvalidDataException>(() => Nbt.Read(data));
    }

    [Fact]
    public void ReaderListsOnlyVisibleServersAndReportsUnreadableFiles()
    {
        using var dir = new TestDirectory();
        var path = Path.Combine(dir.Path, "servers.dat");
        File.WriteAllBytes(path, Fixture());
        var visible = Assert.Single(MinecraftServerListReader.ReadFile(path));
        Assert.Equal(("§6Gold 🌟 Server", "mc.example.com"), (visible.Name, visible.Ip));
        Assert.Empty(MinecraftServerListReader.ReadFile(Path.Combine(dir.Path, "missing.dat")));
        File.WriteAllBytes(path, new byte[] { 0x0A, 0x00 });
        var error = Assert.Throws<InvalidDataException>(() => MinecraftServerListReader.ReadFile(path));
        Assert.Contains(path, error.Message);
    }
}
