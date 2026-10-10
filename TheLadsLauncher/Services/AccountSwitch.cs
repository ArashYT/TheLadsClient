using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;

namespace TheLadsLauncher.Services;

public record AccountSwitchRequest(string Username, string Uuid, string Type);

/// <summary>
/// The in-game account switcher hand-off, shared by every way the launcher starts a game (a profile, Zero SMP, a modpack):
/// the game writes <see cref="RequestFile"/> (lads_next_account.json) into its game directory (THELADS_DIR);
/// when it has exited the launcher takes the request and applies it so the next launch uses the chosen account.
/// </summary>
public static class AccountSwitch
{
    public const string RequestFile = "lads_next_account.json";

    /// <summary>Called right before a game starts: a request left over from an earlier run must not switch this one at its exit.</summary>
    public static void Prepare(string gameDirectory)
    {
        Directory.CreateDirectory(gameDirectory);
        TryDelete(Path.Combine(gameDirectory, RequestFile));
    }

    /// <summary>The game's request, read and deleted: the requested account info, or null when there is none or it cannot be read
    /// (<paramref name="log"/> says why).</summary>
    public static AccountSwitchRequest? TakeRequest(string gameDirectory, Action<string> log)
    {
        string file = Path.Combine(gameDirectory, RequestFile);
        if (!File.Exists(file)) return null;
        AccountSwitchRequest request;
        try
        {
            using var doc = JsonDocument.Parse(File.ReadAllText(file));
            string name = doc.RootElement.GetProperty("username").GetString()?.Trim() ?? "";
            string uuid = doc.RootElement.TryGetProperty("uuid", out var u) && u.ValueKind != JsonValueKind.Null ? u.GetString()?.Trim() ?? "" : "";
            string type = doc.RootElement.TryGetProperty("type", out var t) && t.ValueKind != JsonValueKind.Null ? t.GetString()?.Trim() ?? "" : "";
            request = new AccountSwitchRequest(name, uuid, type);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or JsonException or KeyNotFoundException or InvalidOperationException)
        {
            log($"[Launcher] Could not read the game's account switch request: {ex.Message}");
            TryDelete(file);
            return null;
        }
        TryDelete(file);
        log($"[Launcher] Account switch requested to: {request.Username}");
        if (string.IsNullOrWhiteSpace(request.Username))
        {
            log("[Launcher] Account switch refused: username is empty.");
            return null;
        }
        return request;
    }

    /// <summary>After a game exited from <paramref name="gameDirectory"/>: when it asked for another account, validates the request
    /// against saved accounts (or adds a new valid offline account to <paramref name="settings"/>). Returns the matched or added
    /// account summary; null when the game asked for nothing (or something refused).</summary>
    public static AccountSummary? Apply(string gameDirectory, IEnumerable<AccountSummary> accounts, LauncherSettings settings, Action<string> log)
    {
        if (TakeRequest(gameDirectory, log) is not { } request) return null;
        return ValidateAndApply(request, accounts, settings, log);
    }

    public static AccountSummary? ValidateAndApply(AccountSwitchRequest request, IEnumerable<AccountSummary> accounts, LauncherSettings settings, Action<string> log)
    {
        string name = request.Username;
        string uuid = request.Uuid;
        string type = request.Type;

        var accountList = accounts.ToList();
        var existing = accountList.FirstOrDefault(a =>
            string.Equals(a.username, name, StringComparison.OrdinalIgnoreCase) &&
            string.Equals(a.uuid.Replace("-", ""), uuid.Replace("-", ""), StringComparison.OrdinalIgnoreCase));

        if (existing == null && type == "offline")
        {
            try
            {
                if (string.Equals(AccountIdentity.OfflineUuid(name).Replace("-", ""), uuid.Replace("-", ""), StringComparison.OrdinalIgnoreCase) &&
                    !accountList.Any(a => string.Equals(a.username, name, StringComparison.OrdinalIgnoreCase)))
                {
                    string normalized = AccountIdentity.NormalizeOfflineName(name);
                    settings.OfflineAccounts.Add(normalized);
                    settings.Save();
                    existing = new AccountSummary(normalized, uuid, "offline");
                }
            }
            catch (ArgumentException ex)
            {
                log($"[Accounts] Could not apply the game's account selection: {ex.Message}");
                return null;
            }
        }

        if (existing == null)
        {
            log("[Accounts] Could not apply the game's account selection. Select an account in the launcher.");
            return null;
        }

        log($"[Accounts] Next launch will use {existing.username}.");
        return existing;
    }

    private static void TryDelete(string file)
    {
        try { File.Delete(file); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { }
    }
}
