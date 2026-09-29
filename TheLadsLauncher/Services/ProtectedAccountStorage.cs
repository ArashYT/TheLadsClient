using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using XboxAuthNet.Game.Accounts.JsonStorage;

namespace TheLadsLauncher.Services;

public sealed class ProtectedAccountStorage : IJsonStorage
{
    private readonly string _path;
    private readonly string? _legacyPath;
    public string? RecoveryNotice { get; private set; }
    public ProtectedAccountStorage(string path, string? legacyPath = null)
    {
        _path = path;
        _legacyPath = legacyPath;
    }

    public JsonNode? ReadAsJsonNode()
    {
        if (File.Exists(_path))
        {
            try
            {
                var bytes = ProtectedData.Unprotect(File.ReadAllBytes(_path), null, DataProtectionScope.CurrentUser);
                try { return JsonNode.Parse(bytes); }
                finally { CryptographicOperations.ZeroMemory(bytes); }
            }
            catch (Exception error) when (error is CryptographicException or JsonException)
            {
                // Preserve damaged or other-Windows-user data before creating a usable cache.
                File.Move(_path, _path + ".unreadable-" + Guid.NewGuid().ToString("N"));
                Write(new JsonObject(), null);
                RecoveryNotice = "The saved Microsoft account cache could not be read. Its original data was preserved; please add your Microsoft account again.";
                return new JsonObject();
            }
        }
        if (_legacyPath != null && File.Exists(_legacyPath))
        {
            try
            {
                var node = JsonNode.Parse(File.ReadAllText(_legacyPath)) ?? new JsonObject();
                Write(node, null);
                return node;
            }
            catch (JsonException)
            {
                Write(new JsonObject(), null);
                RecoveryNotice = "The previous Microsoft account cache could not be imported. The original file was preserved; please add your Microsoft account again.";
                return new JsonObject();
            }
        }
        return new JsonObject();
    }

    public void Write(JsonNode node, JsonSerializerOptions? serializerOptions)
    {
        byte[] plain = Encoding.UTF8.GetBytes(node.ToJsonString(serializerOptions));
        try
        {
            byte[] encrypted = ProtectedData.Protect(plain, null, DataProtectionScope.CurrentUser);
            Directory.CreateDirectory(Path.GetDirectoryName(_path)!);
            string temp = _path + "." + Guid.NewGuid().ToString("N") + ".tmp";
            try
            {
                File.WriteAllBytes(temp, encrypted);
                File.Move(temp, _path, true);
            }
            finally { if (File.Exists(temp)) File.Delete(temp); }
        }
        finally { CryptographicOperations.ZeroMemory(plain); }
    }
}
