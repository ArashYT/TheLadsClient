using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using CmlLib.Core.Auth;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public class AuthService : IAuthService
{
    private static AuthService? _instance;
    public static AuthService Instance => _instance ??= new AuthService(PathService.Instance);

    private readonly IPathService _pathService;
    private readonly List<AccountItem> _accounts = new();
    private string? _activeAccount;

    public AuthService(IPathService pathService)
    {
        _pathService = pathService;
        LoadAccounts();
    }

    public string? ActiveAccount
    {
        get => _activeAccount;
        set
        {
            _activeAccount = value;
            foreach (var acc in _accounts)
            {
                acc.Selected = string.Equals(acc.Username, value, StringComparison.OrdinalIgnoreCase);
            }
            SaveAccounts();
        }
    }

    public IReadOnlyList<AccountItem> GetAccounts()
    {
        lock (_accounts)
        {
            return _accounts.ToList();
        }
    }

    public Task<AccountItem> AddOfflineAccountAsync(string username)
    {
        if (string.IsNullOrWhiteSpace(username))
            throw new ArgumentException("Username cannot be empty", nameof(username));

        lock (_accounts)
        {
            var existing = _accounts.FirstOrDefault(a => string.Equals(a.Username, username, StringComparison.OrdinalIgnoreCase));
            if (existing != null)
            {
                ActiveAccount = existing.Username;
                return Task.FromResult(existing);
            }

            var uuid = GenerateOfflineUuid(username);
            var account = new AccountItem
            {
                Username = username,
                Uuid = uuid,
                AccessToken = Guid.NewGuid().ToString("N"),
                AccountType = "offline",
                Selected = true
            };

            foreach (var a in _accounts) a.Selected = false;
            _accounts.Add(account);
            _activeAccount = username;
            SaveAccounts();
            return Task.FromResult(account);
        }
    }

    public bool RemoveAccount(string username)
    {
        lock (_accounts)
        {
            var acc = _accounts.FirstOrDefault(a => string.Equals(a.Username, username, StringComparison.OrdinalIgnoreCase));
            if (acc != null)
            {
                _accounts.Remove(acc);
                if (string.Equals(_activeAccount, username, StringComparison.OrdinalIgnoreCase))
                {
                    _activeAccount = _accounts.FirstOrDefault()?.Username;
                    if (_activeAccount != null)
                    {
                        var first = _accounts.First();
                        first.Selected = true;
                    }
                }
                SaveAccounts();
                return true;
            }
            return false;
        }
    }

    public Task<MSession> ResolveSessionAsync(string username)
    {
        lock (_accounts)
        {
            var acc = _accounts.FirstOrDefault(a => string.Equals(a.Username, username, StringComparison.OrdinalIgnoreCase));
            if (acc != null)
            {
                return Task.FromResult(new MSession(acc.Username, acc.AccessToken, acc.Uuid));
            }

            // Fallback to offline session
            return Task.FromResult(MSession.CreateOfflineSession(username));
        }
    }

    public string? GetAccountUUID(string username)
    {
        lock (_accounts)
        {
            var acc = _accounts.FirstOrDefault(a => string.Equals(a.Username, username, StringComparison.OrdinalIgnoreCase));
            return acc?.Uuid ?? GenerateOfflineUuid(username);
        }
    }

    public async Task SyncAccountsAsync()
    {
        await Task.Run(() =>
        {
            SaveAccounts();
            try
            {
                var accountsFile = _pathService.AccountsFile;
                var sharedFile = _pathService.SharedAccountsFile;
                if (File.Exists(accountsFile))
                {
                    File.Copy(accountsFile, sharedFile, true);
                }
            }
            catch { }
        });
    }

    private void LoadAccounts()
    {
        lock (_accounts)
        {
            _accounts.Clear();
            var path = _pathService.AccountsFile;
            if (File.Exists(path))
            {
                try
                {
                    var json = File.ReadAllText(path);
                    var list = JsonSerializer.Deserialize<List<AccountItem>>(json);
                    if (list != null && list.Count > 0)
                    {
                        _accounts.AddRange(list);
                        var selected = _accounts.FirstOrDefault(a => a.Selected);
                        _activeAccount = selected?.Username ?? _accounts[0].Username;
                        return;
                    }
                }
                catch { }
            }

            // Fallback from shared file if base file did not have accounts
            var sharedPath = _pathService.SharedAccountsFile;
            if (File.Exists(sharedPath))
            {
                try
                {
                    var json = File.ReadAllText(sharedPath);
                    var list = JsonSerializer.Deserialize<List<AccountItem>>(json);
                    if (list != null && list.Count > 0)
                    {
                        _accounts.AddRange(list);
                        var selected = _accounts.FirstOrDefault(a => a.Selected);
                        _activeAccount = selected?.Username ?? _accounts[0].Username;
                        SaveAccounts();
                        return;
                    }
                }
                catch { }
            }
        }
    }

    private void SaveAccounts()
    {
        try
        {
            _pathService.EnsureDirectories();
            var options = new JsonSerializerOptions { WriteIndented = true };
            string json;
            lock (_accounts)
            {
                json = JsonSerializer.Serialize(_accounts, options);
            }

            File.WriteAllText(_pathService.AccountsFile, json);
            try
            {
                File.WriteAllText(_pathService.SharedAccountsFile, json);
            }
            catch { }
        }
        catch { }
    }

    private static string GenerateOfflineUuid(string username)
    {
        using var md5 = MD5.Create();
        var hash = md5.ComputeHash(Encoding.UTF8.GetBytes("OfflinePlayer:" + username));
        hash[6] = (byte)((hash[6] & 0x0f) | 0x30); // Version 3
        hash[8] = (byte)((hash[8] & 0x3f) | 0x80); // IETF variant
        var hex = BitConverter.ToString(hash).Replace("-", "").ToLowerInvariant();
        return $"{hex.Substring(0, 8)}-{hex.Substring(8, 4)}-{hex.Substring(12, 4)}-{hex.Substring(16, 4)}-{hex.Substring(20, 12)}";
    }
}
