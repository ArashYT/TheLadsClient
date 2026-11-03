using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading;
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

    /// <summary>What every game's account switcher lists: the launcher's accounts, once each, in the launcher's order, its chosen one marked.</summary>
    public static AccountSummary[] PublishedList(IEnumerable<AccountSummary> accounts, List<string> order, string? chosen) => accounts
        .DistinctBy(a => a.uuid.Replace("-", "") is { Length: > 0 } id ? id.ToLowerInvariant() : a.username.ToLowerInvariant())
        .DistinctBy(a => a.username, StringComparer.OrdinalIgnoreCase)
        .OrderBy(a =>
        {
            int index = order.FindIndex(name => string.Equals(name, a.username, StringComparison.OrdinalIgnoreCase));
            return index < 0 ? int.MaxValue : index;
        })
        .Select(a => a with { selected = string.Equals(a.username, chosen, StringComparison.OrdinalIgnoreCase) })
        .ToArray();

    /// <summary>Rewrites the published list (<see cref="IPathService.SharedAccountsFile"/>), which a game reads each time its switcher opens: names and ids only,
    /// the tokens stay in the DPAPI store. A game reading the file at that moment can hold it up, so a few tries; the next account change writes it again anyway.</summary>
    public static void Publish(string path, IReadOnlyCollection<AccountSummary> list, Action<string> log)
    {
        string temporary = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            File.WriteAllText(temporary, JsonSerializer.Serialize(list));
            for (int attempt = 0; ; attempt++)
            {
                try
                {
                    File.Move(temporary, path, true);
                    return;
                }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException && attempt < 3)
                {
                    Thread.Sleep(30);
                }
            }
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            log($"[Accounts] Could not publish the account list for the game: {ex.Message}");
        }
        finally
        {
            try { File.Delete(temporary); }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { }
        }
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
