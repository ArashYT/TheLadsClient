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

    [Fact]
    public void ASignedPackageVerifies()
    {
        using var dir = new TestDirectory();
        var (pem, pub) = NewKey();
        string package = Package(dir);
        byte[] manifest = UpdateSignature.CreateManifest("1.9.0", package);

        UpdateSignature.Verify(manifest, UpdateSignature.Sign(manifest, pem), pub, "1.9.0", package);
    }

    [Fact]
    public void AnotherKeyAChangedPackageOrAnotherVersionIsRefused()
    {
        using var dir = new TestDirectory();
        var (pem, pub) = NewKey();
        var (otherPem, _) = NewKey();
        string package = Package(dir);
        byte[] manifest = UpdateSignature.CreateManifest("1.9.0", package);
        byte[] signature = UpdateSignature.Sign(manifest, pem);

        Assert.Throws<InvalidDataException>(() => UpdateSignature.Verify(manifest, UpdateSignature.Sign(manifest, otherPem), pub, "1.9.0", package));
        Assert.Throws<InvalidDataException>(() => UpdateSignature.Verify(manifest, signature, pub, "1.9.1", package));
        File.WriteAllText(package, "attacker bytes");
        Assert.Throws<InvalidDataException>(() => UpdateSignature.Verify(manifest, signature, pub, "1.9.0", package));
    }

    [Fact]
    public void TheShippedKeyIsAValidP256PublicKey()
    {
        using var key = ECDsa.Create();
        key.ImportSubjectPublicKeyInfo(System.Convert.FromBase64String(UpdateSignature.ReleasePublicKey), out _);
        Assert.Equal(256, key.KeySize);
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
        byte[] manifest = UpdateSignature.CreateManifest("1.9.0", a);

        UpdateSignature.Verify(manifest, UpdateSignature.Sign(manifest, pem), pub, "1.9.0", rebuilt);
    }

    [Fact]
    public void AChangedEntryFailsBothHashes()
    {
        using var dir = new TestDirectory();
        var (pem, pub) = NewKey();
        string a = Zip(dir, "TheLadsClient-1.9.0-full.nupkg", CompressionLevel.Optimal, ("lib/app/x.dll", "X"), ("y.nuspec", "Y"));
        string changed = Zip(dir, Path.Combine("changed", "TheLadsClient-1.9.0-full.nupkg"), CompressionLevel.Optimal, ("lib/app/x.dll", "X2"), ("y.nuspec", "Y"));
        byte[] manifest = UpdateSignature.CreateManifest("1.9.0", a);

        var error = Assert.Throws<InvalidDataException>(() => UpdateSignature.Verify(manifest, UpdateSignature.Sign(manifest, pem), pub, "1.9.0", changed));
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
        byte[] signature = UpdateSignature.Sign(manifest, pem);

        UpdateSignature.Verify(manifest, signature, pub, "1.9.0", a);
        Assert.Throws<InvalidDataException>(() => UpdateSignature.Verify(manifest, signature, pub, "1.9.0", rebuilt));
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
