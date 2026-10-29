using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace TheLadsLauncher;

/// <summary>Launcher updates are installed only when signed with The Lads' release key, which never leaves the owner's PC
/// (tools/Sign-Release.ps1 signs a CI draft before it is published, verifying that draft files carry the release workflow's
/// attestation for the owner's tag). Someone who gets into the GitHub repo or its CI can publish a release, but not this
/// signature, so no launcher installs it. Publish-Release.ps1 extracts pinned public keys from this file to verify release signatures before publishing.</summary>
public static class UpdateSignature
{
    public const string ManifestName = "lads-update.json";
    public const string SignatureName = "lads-update.json.sig";
    /// <summary>Public halves (P-256, SubjectPublicKeyInfo) of the release keys an update may be signed with. A launcher trusts only the
    /// keys it shipped with, so a new key is added a release ahead of signing with it (and kept offline until then), and an old one is
    /// dropped only once the launchers still in use trust its successor.</summary>
    public static IReadOnlyList<string> ReleasePublicKeys { get; } = new[]
    {
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfmUJlYtOxImIQnK6gxSsgElWPH5kL49R3gOrj/uq3VPb6ZumFo2HmVYyPJ2cIW/54VYoBjxE4RzQvTnT2ZFqxA==",
    };

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

    /// <summary>True when <paramref name="signature"/> (ECDSA P-256 over SHA-256) is one of <paramref name="publicKeys"/>' over <paramref name="data"/>.</summary>
    public static bool IsSigned(byte[] data, byte[] signature, IEnumerable<string> publicKeys) => publicKeys.Any(publicKey =>
    {
        using var key = ECDsa.Create();
        key.ImportSubjectPublicKeyInfo(Convert.FromBase64String(publicKey), out _);
        return key.VerifyData(data, signature, HashAlgorithmName.SHA256);
    });

    /// <summary>Throws unless <paramref name="signature"/> is a release key's over <paramref name="manifest"/>, which names
    /// <paramref name="version"/> and <paramref name="packageName"/>; returns the signed statement. Needs no package, so launchers
    /// check it before downloading one.</summary>
    public static Manifest VerifyStatement(byte[] manifest, byte[] signature, IEnumerable<string> publicKeys, string version, string packageName)
    {
        if (!IsSigned(manifest, signature, publicKeys))
            throw new InvalidDataException($"v{version} is not signed by The Lads; it was not installed.");
        var statement = JsonSerializer.Deserialize<Manifest>(manifest) ?? throw new InvalidDataException("Empty update signature.");
        if (statement.version != version || !string.Equals(statement.package, packageName, StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException($"The update signature is for v{statement.version} ({statement.package}), not v{version}.");
        return statement;
    }

    /// <summary>Throws unless <paramref name="packagePath"/> holds the signed package's exact bytes or, only for a package Velopack
    /// rebuilt from a delta (<paramref name="rebuiltFromDelta"/>) and a statement with a content hash, its exact entries.</summary>
    public static void VerifyPackage(Manifest statement, string packagePath, bool rebuiltFromDelta)
    {
        using (var file = File.OpenRead(packagePath))
            if (string.Equals(Convert.ToHexString(SHA256.HashData(file)), statement.sha256, StringComparison.OrdinalIgnoreCase))
                return;
        if (rebuiltFromDelta && statement.contentSha256 is not null && ContentMatches(packagePath, statement.contentSha256))
            return;
        throw new InvalidDataException($"The downloaded v{statement.version} package differs from the signed one; it was not installed.");
    }

    /// <summary>False for a package that is not a readable zip or has duplicate entries, like for any other mismatch.</summary>
    private static bool ContentMatches(string packagePath, string expected)
    {
        try { return string.Equals(ContentHash(packagePath), expected, StringComparison.OrdinalIgnoreCase); }
        catch (InvalidDataException) { return false; }
    }
}
