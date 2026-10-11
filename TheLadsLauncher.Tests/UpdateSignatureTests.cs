using System.IO;
using System.Security.Cryptography;
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

    private static string Package(TestDirectory dir, string content = "package bytes")
    {
        string path = Path.Combine(dir.Path, "TheLadsClient-1.9.0-full.nupkg");
        File.WriteAllText(path, content);
        return path;
    }

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
}
