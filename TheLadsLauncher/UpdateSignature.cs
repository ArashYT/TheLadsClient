using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace TheLadsLauncher;

/// <summary>Launcher updates are installed only when signed with The Lads' release key, which never leaves the owner's PC
/// (tools/Sign-Release.ps1 signs a CI draft before it is published). Someone who gets into the GitHub repo or its CI can
/// publish a release, but not this signature, so no launcher installs it.</summary>
public static class UpdateSignature
{
    public const string ManifestName = "lads-update.json";
    public const string SignatureName = "lads-update.json.sig";
    /// <summary>The release key's public half (P-256, SubjectPublicKeyInfo).</summary>
    public const string ReleasePublicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfmUJlYtOxImIQnK6gxSsgElWPH5kL49R3gOrj/uq3VPb6ZumFo2HmVYyPJ2cIW/54VYoBjxE4RzQvTnT2ZFqxA==";

    /// <summary><c>sha256</c> is the package file's hash; <c>contentSha256</c> (see <see cref="ContentHash"/>) is the hash of what is
    /// inside it, which a package rebuilt from a Velopack delta keeps although its zip container bytes differ.</summary>
    public sealed record Manifest(string version, string package, string sha256, string? contentSha256 = null);

    /// <summary>SHA-256 over every zip entry's name and content, ignoring entry order, compression and timestamps. Each entry
    /// is the line "name TAB lowercase-hex SHA-256 of its bytes LF"; the lines are ordered by name (ordinal) and hashed as UTF-8.
    /// tools/Get-PackageContentHash.ps1 must stay byte-identical. Duplicate names are refused: extractors may pick either one.</summary>
    public static string ContentHash(string packagePath)
    {
        using var zip = ZipFile.OpenRead(packagePath);
        var lines = new SortedDictionary<string, string>(StringComparer.Ordinal);
        foreach (var entry in zip.Entries)
        {
            if (lines.ContainsKey(entry.FullName))
                throw new InvalidDataException($"Duplicate entry {entry.FullName} in {Path.GetFileName(packagePath)}.");
            using var stream = entry.Open();
            lines[entry.FullName] = $"{entry.FullName}\t{Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant()}\n";
        }
        return Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(string.Concat(lines.Values))));
    }

    /// <summary>The signed statement for one release package.</summary>
    public static byte[] CreateManifest(string version, string packagePath)
    {
        using var file = File.OpenRead(packagePath);
        return JsonSerializer.SerializeToUtf8Bytes(new Manifest(version, Path.GetFileName(packagePath), Convert.ToHexString(SHA256.HashData(file)), ContentHash(packagePath)));
    }

    public static byte[] Sign(byte[] manifest, string privateKeyPem)
    {
        using var key = ECDsa.Create();
        key.ImportFromPem(privateKeyPem);
        return key.SignData(manifest, HashAlgorithmName.SHA256);
    }

    /// <summary>Throws unless <paramref name="signature"/> is the release key's over <paramref name="manifest"/>, which names
    /// <paramref name="version"/> and <paramref name="packagePath"/>'s exact bytes, or (when the manifest has a content hash) its
    /// exact entries, as in a package Velopack rebuilt from a delta.</summary>
    public static void Verify(byte[] manifest, byte[] signature, string publicKey, string version, string packagePath)
    {
        using (var key = ECDsa.Create())
        {
            key.ImportSubjectPublicKeyInfo(Convert.FromBase64String(publicKey), out _);
            if (!key.VerifyData(manifest, signature, HashAlgorithmName.SHA256))
                throw new InvalidDataException($"v{version} is not signed by The Lads; it was not installed.");
        }
        var statement = JsonSerializer.Deserialize<Manifest>(manifest) ?? throw new InvalidDataException("Empty update signature.");
        if (statement.version != version || !string.Equals(statement.package, Path.GetFileName(packagePath), StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException($"The update signature is for v{statement.version} ({statement.package}), not v{version}.");
        using (var file = File.OpenRead(packagePath))
            if (string.Equals(Convert.ToHexString(SHA256.HashData(file)), statement.sha256, StringComparison.OrdinalIgnoreCase))
                return;
        if (statement.contentSha256 is not null && ContentMatches(packagePath, statement.contentSha256))
            return;
        throw new InvalidDataException($"The downloaded v{version} package differs from the signed one; it was not installed.");
    }

    /// <summary>False for a package that is not a readable zip or has duplicate entries, like for any other mismatch.</summary>
    private static bool ContentMatches(string packagePath, string expected)
    {
        try { return string.Equals(ContentHash(packagePath), expected, StringComparison.OrdinalIgnoreCase); }
        catch (InvalidDataException) { return false; }
    }
}
