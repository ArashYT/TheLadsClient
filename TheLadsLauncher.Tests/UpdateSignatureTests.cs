using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Text.Json;
using TheLadsLauncher;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>Only a package signed with the release key installs.</summary>
public class UpdateSignatureTests
{
    private static (string pem, string pub) NewKey()
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        return (key.ExportPkcs8PrivateKeyPem(), System.Convert.ToBase64String(key.ExportSubjectPublicKeyInfo()));
    }

    private static string Package(TestDirectory dir, string content = "package bytes") =>
        Zip(dir, "TheLadsClient-1.9.0-full.nupkg", CompressionLevel.Optimal, ("lib/app.txt", content));

    /// <summary>A zip package with these entries, in this order; <paramref name="file"/> may sit in a subfolder of the test directory.</summary>
    private static string Zip(TestDirectory dir, string file, CompressionLevel level, params (string name, string text)[] entries)
    {
        string path = Path.Combine(dir.Path, file);
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        using var zip = ZipFile.Open(path, ZipArchiveMode.Create);
        foreach (var (name, text) in entries)
        {
            using var writer = new StreamWriter(zip.CreateEntry(name, level).Open());
            writer.Write(text);
        }
        return path;
    }

    private static string Sha256(string path) => System.Convert.ToHexString(SHA256.HashData(File.ReadAllBytes(path)));

    /// <summary>The statement tools/Sign-Release.ps1 signs for one release package.</summary>
    private static byte[] CreateManifest(string version, string packagePath) => JsonSerializer.SerializeToUtf8Bytes(
        new UpdateSignature.Manifest(version, Path.GetFileName(packagePath), Sha256(packagePath), UpdateSignature.ContentHash(packagePath)));

    private static byte[] Sign(byte[] manifest, string privateKeyPem)
    {
        using var key = ECDsa.Create();
        key.ImportFromPem(privateKeyPem);
        return key.SignData(manifest, HashAlgorithmName.SHA256);
    }

    /// <summary>What a launcher checks: the statement before the download, the package after it.</summary>
    private static void Verify(byte[] manifest, byte[] signature, string publicKey, string version, string packagePath, bool rebuiltFromDelta = false) =>
        UpdateSignature.VerifyPackage(UpdateSignature.VerifyStatement(manifest, signature, new[] { publicKey }, version, Path.GetFileName(packagePath)), packagePath, rebuiltFromDelta);

    [Fact]
    public void ASignedPackageVerifies()
    {
        using var dir = new TestDirectory();
        var (pem, pub) = NewKey();
        string package = Package(dir);
        byte[] manifest = CreateManifest("1.9.0", package);

        Verify(manifest, Sign(manifest, pem), pub, "1.9.0", package);
    }

    [Fact]
    public void AnotherKeyAChangedPackageOrAnotherVersionIsRefused()
    {
        using var dir = new TestDirectory();
        var (pem, pub) = NewKey();
        var (otherPem, _) = NewKey();
        string package = Package(dir);
        byte[] manifest = CreateManifest("1.9.0", package);
        byte[] signature = Sign(manifest, pem);

        Assert.Throws<InvalidDataException>(() => Verify(manifest, Sign(manifest, otherPem), pub, "1.9.0", package));
        Assert.Throws<InvalidDataException>(() => Verify(manifest, signature, pub, "1.9.1", package));
        File.WriteAllText(package, "attacker bytes");
        Assert.Throws<InvalidDataException>(() => Verify(manifest, signature, pub, "1.9.0", package));
    }

    [Fact]
    public void TheStatementIsCheckedBeforeThereIsAPackage()
    {
        using var dir = new TestDirectory();
        var (pem, pub) = NewKey();
        var (otherPem, _) = NewKey();
        string package = Package(dir), sha256 = Sha256(package);
        byte[] manifest = CreateManifest("1.9.0", package);
        byte[] signature = Sign(manifest, pem);
        File.Delete(package);   // launchers check the statement first and download only when it holds

        Assert.Equal(sha256, UpdateSignature.VerifyStatement(manifest, signature, new[] { pub }, "1.9.0", "TheLadsClient-1.9.0-full.nupkg").sha256);
        Assert.Throws<InvalidDataException>(() => UpdateSignature.VerifyStatement(manifest, Sign(manifest, otherPem), new[] { pub }, "1.9.0", "TheLadsClient-1.9.0-full.nupkg"));
        Assert.Throws<InvalidDataException>(() => UpdateSignature.VerifyStatement(manifest, signature, new[] { pub }, "1.9.1", "TheLadsClient-1.9.0-full.nupkg"));
        Assert.Throws<InvalidDataException>(() => UpdateSignature.VerifyStatement(manifest, signature, new[] { pub }, "1.9.0", "TheLadsClient-1.9.0-delta.nupkg"));
        Assert.Throws<InvalidDataException>(() => UpdateSignature.VerifyStatement(manifest, signature, System.Array.Empty<string>(), "1.9.0", "TheLadsClient-1.9.0-full.nupkg"));
    }

    [Fact]
    public void ASignatureByAnyPinnedKeyVerifies()
    {
        using var dir = new TestDirectory();
        var (currentPem, current) = NewKey();
        var (nextPem, next) = NewKey();
        var (otherPem, _) = NewKey();
        byte[] manifest = CreateManifest("1.9.0", Package(dir));

        foreach (string pem in new[] { currentPem, nextPem })
            UpdateSignature.VerifyStatement(manifest, Sign(manifest, pem), new[] { current, next }, "1.9.0", "TheLadsClient-1.9.0-full.nupkg");
        Assert.Throws<InvalidDataException>(() => UpdateSignature.VerifyStatement(manifest, Sign(manifest, otherPem), new[] { current, next }, "1.9.0", "TheLadsClient-1.9.0-full.nupkg"));
    }

    [Fact]
    public void TheShippedKeysAreValidP256PublicKeys()
    {
        Assert.NotEmpty(UpdateSignature.ReleasePublicKeys);
        Assert.Equal(UpdateSignature.ReleasePublicKeys.Count, UpdateSignature.ReleasePublicKeys.Distinct().Count());
        foreach (string publicKey in UpdateSignature.ReleasePublicKeys)
        {
            using var key = ECDsa.Create();
            key.ImportSubjectPublicKeyInfo(System.Convert.FromBase64String(publicKey), out _);
            Assert.Equal(256, key.KeySize);
        }
    }

    /// <summary>The published v1.11.1 lads-update.json and its signature: the current release key must stay pinned until every launcher
    /// in use trusts its successor, or no launcher can update again.</summary>
    [Fact]
    public void ThePublishedReleaseSignatureVerifiesWithThePinnedKeys()
    {
        byte[] manifest = System.Text.Encoding.UTF8.GetBytes("{\"version\":\"1.11.1\",\"package\":\"TheLadsClient-1.11.1-full.nupkg\",\"sha256\":\"1680E8D8C0781201B16CED6B0EE01160121DD33D587A8675FC2211D83D042FC8\",\"contentSha256\":\"F3F6DC043A400FC2B22802B73B4A06758A50345DEFF59EC981D6C3B5718F2624\"}");
        byte[] signature = System.Convert.FromBase64String("rQD+euWr+Kc5wLzbQ6vuwRcZJFYG0hsvEAlJEex5Z79m1b5oRj0gEHLeO4Sr4a9ePD9F0hMvBUI4HCI1bT4Tlg==");

        var statement = UpdateSignature.VerifyStatement(manifest, signature, UpdateSignature.ReleasePublicKeys, "1.11.1", "TheLadsClient-1.11.1-full.nupkg");
        Assert.Equal("1680E8D8C0781201B16CED6B0EE01160121DD33D587A8675FC2211D83D042FC8", statement.sha256);
    }

    [Fact]
    public void ContentHashIgnoresZipContainerBytes()
    {
        using var dir = new TestDirectory();
        string a = Zip(dir, "a.nupkg", CompressionLevel.Optimal, ("lib/app/x.dll", "X"), ("y.nuspec", "Y"));
        string b = Zip(dir, "b.nupkg", CompressionLevel.NoCompression, ("y.nuspec", "Y"), ("lib/app/x.dll", "X"));

        Assert.NotEqual(Sha256(a), Sha256(b));
        Assert.Equal(UpdateSignature.ContentHash(a), UpdateSignature.ContentHash(b));
    }

    [Fact]
    public void ARebuiltPackageVerifiesByContent()
    {
        using var dir = new TestDirectory();
        var (pem, pub) = NewKey();
        string a = Zip(dir, "TheLadsClient-1.9.0-full.nupkg", CompressionLevel.Optimal, ("lib/app/x.dll", "X"), ("y.nuspec", "Y"));
        string rebuilt = Zip(dir, Path.Combine("rebuilt", "TheLadsClient-1.9.0-full.nupkg"), CompressionLevel.NoCompression, ("y.nuspec", "Y"), ("lib/app/x.dll", "X"));
        byte[] manifest = CreateManifest("1.9.0", a);

        Verify(manifest, Sign(manifest, pem), pub, "1.9.0", rebuilt, rebuiltFromDelta: true);
    }

    /// <summary>Only a package Velopack rebuilt from a delta may differ in its zip container: a full download must be the signed bytes,
    /// or any difference between .NET's zip reader and Update.exe's would let a re-packed container through.</summary>
    [Fact]
    public void AFullDownloadMustBeTheSignedBytes()
    {
        using var dir = new TestDirectory();
        var (pem, pub) = NewKey();
        string a = Zip(dir, "TheLadsClient-1.9.0-full.nupkg", CompressionLevel.Optimal, ("lib/app/x.dll", "X"), ("y.nuspec", "Y"));
        string repacked = Zip(dir, Path.Combine("repacked", "TheLadsClient-1.9.0-full.nupkg"), CompressionLevel.NoCompression, ("y.nuspec", "Y"), ("lib/app/x.dll", "X"));
        byte[] manifest = CreateManifest("1.9.0", a);
        byte[] signature = Sign(manifest, pem);

        var error = Assert.Throws<InvalidDataException>(() => Verify(manifest, signature, pub, "1.9.0", repacked));
        Assert.Contains("differs from the signed one", error.Message);
        Verify(manifest, signature, pub, "1.9.0", a);
    }

    [Fact]
    public void AChangedEntryFailsBothHashes()
    {
        using var dir = new TestDirectory();
        var (pem, pub) = NewKey();
        string a = Zip(dir, "TheLadsClient-1.9.0-full.nupkg", CompressionLevel.Optimal, ("lib/app/x.dll", "X"), ("y.nuspec", "Y"));
        string changed = Zip(dir, Path.Combine("changed", "TheLadsClient-1.9.0-full.nupkg"), CompressionLevel.Optimal, ("lib/app/x.dll", "X2"), ("y.nuspec", "Y"));
        byte[] manifest = CreateManifest("1.9.0", a);

        var error = Assert.Throws<InvalidDataException>(() => Verify(manifest, Sign(manifest, pem), pub, "1.9.0", changed, rebuiltFromDelta: true));
        Assert.Contains("differs from the signed one", error.Message);
    }

    [Fact]
    public void DuplicateEntryNamesAreRefused()
    {
        using var dir = new TestDirectory();
        string duplicated = Zip(dir, "d.nupkg", CompressionLevel.Optimal, ("x.dll", "X"), ("x.dll", "evil"));

        var error = Assert.Throws<InvalidDataException>(() => UpdateSignature.ContentHash(duplicated));
        Assert.Contains("Duplicate entry", error.Message);
    }

    [Fact]
    public void AnOldManifestWithoutContentHashStillVerifiesByFileHash()
    {
        using var dir = new TestDirectory();
        var (pem, pub) = NewKey();
        string a = Zip(dir, "TheLadsClient-1.9.0-full.nupkg", CompressionLevel.Optimal, ("lib/app/x.dll", "X"), ("y.nuspec", "Y"));
        string rebuilt = Zip(dir, Path.Combine("rebuilt", "TheLadsClient-1.9.0-full.nupkg"), CompressionLevel.NoCompression, ("y.nuspec", "Y"), ("lib/app/x.dll", "X"));
        byte[] manifest = JsonSerializer.SerializeToUtf8Bytes(new { version = "1.9.0", package = Path.GetFileName(a), sha256 = Sha256(a) });
        byte[] signature = Sign(manifest, pem);

        Verify(manifest, signature, pub, "1.9.0", a);
        Assert.Throws<InvalidDataException>(() => Verify(manifest, signature, pub, "1.9.0", rebuilt, rebuiltFromDelta: true));
    }

    [Fact]
    public void PowerShellContentHashMatchesCSharp()
    {
        string root = AppContext.BaseDirectory;
        while (!File.Exists(Path.Combine(root, "tools", "Get-PackageContentHash.ps1")))
            root = Path.GetDirectoryName(root) ?? throw new FileNotFoundException("tools/Get-PackageContentHash.ps1 not found above the test output folder.");
        using var dir = new TestDirectory();
        string a = Zip(dir, "a.nupkg", CompressionLevel.Optimal, ("lib/app/x.dll", "X"), ("Y.nuspec", "Y"), ("lib/\u00e9.txt", "caf\u00e9"), ("empty/", ""));

        var start = new ProcessStartInfo("pwsh", new[] { "-NoProfile", "-NonInteractive", "-File", Path.Combine(root, "tools", "Get-PackageContentHash.ps1"), "-Path", a })
        {
            RedirectStandardOutput = true, RedirectStandardError = true, UseShellExecute = false, CreateNoWindow = true
        };
        using var process = Process.Start(start)!;
        Task<string> error = process.StandardError.ReadToEndAsync();
        string output = process.StandardOutput.ReadToEnd();
        process.WaitForExit();

        Assert.True(process.ExitCode == 0, error.Result);
        Assert.Equal(UpdateSignature.ContentHash(a), output.Trim());
    }
}
