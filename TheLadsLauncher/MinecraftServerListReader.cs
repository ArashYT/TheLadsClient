using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace TheLadsLauncher;

/// <summary>
/// Reads Minecraft's servers.dat (GZip-compressed NBT) and returns server names + IPs.
/// Only reads the subset of NBT needed — Tag_Compound root → Tag_List "servers" → {String "name", String "ip"}.
/// </summary>
public static class MinecraftServerListReader
{
    public record ServerEntry(string Name, string Ip);

    public static List<ServerEntry> Read(string instancePath)
    {
        string path = Path.Combine(instancePath, "servers.dat");
        if (!File.Exists(path)) return new();

        try
        {
            byte[] raw = File.ReadAllBytes(path);
            using var ms = new MemoryStream(raw);
            using var gz = new GZipStream(ms, CompressionMode.Decompress);
            using var decompressed = new MemoryStream();
            gz.CopyTo(decompressed);
            decompressed.Position = 0;
            using var reader = new BinaryReader(decompressed);
            return ParseServersFromNbt(reader);
        }
        catch
        {
            return new();
        }
    }

    // ── NBT reader (big-endian) ──────────────────────────────────────────────

    private static List<ServerEntry> ParseServersFromNbt(BinaryReader r)
    {
        // Root is always TAG_Compound with an empty name
        byte rootType = r.ReadByte();
        if (rootType != 10) return new(); // not TAG_Compound
        SkipNbtString(r); // root name (empty)

        // Walk compound entries looking for "servers" TAG_List
        while (true)
        {
            byte tagType = r.ReadByte();
            if (tagType == 0) break; // TAG_End

            string name = ReadNbtString(r);

            if (tagType == 9 && name == "servers")
                return ReadServerList(r);
            else
                SkipNbtPayload(r, tagType);
        }
        return new();
    }

    private static List<ServerEntry> ReadServerList(BinaryReader r)
    {
        var result = new List<ServerEntry>();
        byte elementType = r.ReadByte();
        int count = ReadInt32BE(r);
        if (elementType != 10) return result; // expected TAG_Compound list

        for (int i = 0; i < count; i++)
        {
            string ip = "", name = "";
            // read compound entries until TAG_End
            while (true)
            {
                byte t = r.ReadByte();
                if (t == 0) break;
                string key = ReadNbtString(r);
                if (t == 8 && key == "ip")   ip = ReadNbtString(r);
                else if (t == 8 && key == "name") name = ReadNbtString(r);
                else SkipNbtPayload(r, t);
            }
            if (!string.IsNullOrWhiteSpace(ip))
                result.Add(new ServerEntry(string.IsNullOrWhiteSpace(name) ? ip : name, ip));
        }
        return result;
    }

    private static string ReadNbtString(BinaryReader r)
    {
        ushort len = ReadUInt16BE(r);
        if (len == 0) return "";
        byte[] bytes = r.ReadBytes(len);
        return Encoding.UTF8.GetString(bytes);
    }

    private static void SkipNbtString(BinaryReader r)
    {
        ushort len = ReadUInt16BE(r);
        if (len > 0) r.ReadBytes(len);
    }

    private static void SkipNbtPayload(BinaryReader r, byte type)
    {
        switch (type)
        {
            case 1: r.ReadByte(); break;
            case 2: r.ReadBytes(2); break;
            case 3: r.ReadBytes(4); break;
            case 4: r.ReadBytes(8); break;
            case 5: r.ReadBytes(4); break;
            case 6: r.ReadBytes(8); break;
            case 7: r.ReadBytes(ReadInt32BE(r)); break;     // byte array
            case 8: SkipNbtString(r); break;
            case 9:
            {
                byte et = r.ReadByte();
                int cnt = ReadInt32BE(r);
                for (int j = 0; j < cnt; j++) SkipNbtPayload(r, et);
                break;
            }
            case 10:
            {
                while (true)
                {
                    byte t = r.ReadByte();
                    if (t == 0) break;
                    SkipNbtString(r);
                    SkipNbtPayload(r, t);
                }
                break;
            }
            case 11: r.ReadBytes(ReadInt32BE(r) * 4); break;   // int array
            case 12: r.ReadBytes(ReadInt32BE(r) * 8); break;   // long array
        }
    }

    private static int ReadInt32BE(BinaryReader r)
    {
        byte[] b = r.ReadBytes(4);
        return (b[0] << 24) | (b[1] << 16) | (b[2] << 8) | b[3];
    }

    private static ushort ReadUInt16BE(BinaryReader r)
    {
        byte[] b = r.ReadBytes(2);
        return (ushort)((b[0] << 8) | b[1]);
    }
}
