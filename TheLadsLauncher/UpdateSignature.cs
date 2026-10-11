using System;
using System.IO;
using System.Security.Cryptography;
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

    public sealed record Manifest(string version, string package, string sha256);

    /// <summary>The signed statement for one release package.</summary>
    public static byte[] CreateManifest(string version, string packagePath)
    {
        using var file = File.OpenRead(packagePath);
        return JsonSerializer.SerializeToUtf8Bytes(new Manifest(version, Path.GetFileName(packagePath), Convert.ToHexString(SHA256.HashData(file))));
    }

    public static byte[] Sign(byte[] manifest, string privateKeyPem)
    {
        using var key = ECDsa.Create();
        key.ImportFromPem(privateKeyPem);
        return key.SignData(manifest, HashAlgorithmName.SHA256);
    }

    /// <summary>Throws unless <paramref name="signature"/> is the release key's over <paramref name="manifest"/>, which names
    /// <paramref name="version"/> and the exact bytes of <paramref name="packagePath"/>.</summary>
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
        using var file = File.OpenRead(packagePath);
        if (!string.Equals(Convert.ToHexString(SHA256.HashData(file)), statement.sha256, StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException($"The downloaded v{version} package differs from the signed one; it was not installed.");
    }
}
