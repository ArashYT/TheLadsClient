using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;
using CmlLib.Core.Auth;

namespace TheLadsLauncher.Services;

public record AccountSummary(string username, string uuid, string type, bool selected = false);

public static class AccountExportService
{
    public static async Task WriteLaunchAsync(string gameDirectory, MSession session, bool offline,
        IEnumerable<AccountSummary> accounts)
    {
        var summaries = accounts.Select(a => a with
        {
            selected = string.Equals(a.uuid.Replace("-", ""), session.UUID?.Replace("-", ""), StringComparison.OrdinalIgnoreCase)
        }).ToArray();
        await WriteJsonAsync(Path.Combine(gameDirectory, "lads_accounts.json"), summaries);
        // The JVM receives the access token through its launch arguments. Shared UI files contain metadata only.
        await WriteJsonAsync(Path.Combine(gameDirectory, "lads_profile.json"), new
        {
            username = session.Username, uuid = session.UUID, type = offline ? "offline" : "microsoft",
            lastUpdated = DateTimeOffset.UtcNow
        });
    }

    public static async Task WriteJsonAsync<T>(string path, T value)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        string temporary = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            await File.WriteAllTextAsync(temporary, JsonSerializer.Serialize(value));
            File.Move(temporary, path, true);
        }
        finally { if (File.Exists(temporary)) File.Delete(temporary); }
    }
}
