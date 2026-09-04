using System.Collections.Generic;
using System.Threading.Tasks;
using CmlLib.Core.Auth;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public interface IAuthService
{
    string? ActiveAccount { get; set; }
    IReadOnlyList<AccountItem> GetAccounts();
    Task<AccountItem> AddOfflineAccountAsync(string username);
    bool RemoveAccount(string username);
    Task<MSession> ResolveSessionAsync(string username);
    Task SyncAccountsAsync();
    string? GetAccountUUID(string username);
}
