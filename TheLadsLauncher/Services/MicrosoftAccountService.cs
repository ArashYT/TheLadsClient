using System;
using System.IO;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using CmlLib.Core;
using CmlLib.Core.Auth;
using CmlLib.Core.Auth.Microsoft;
using CmlLib.Core.Auth.Microsoft.Sessions;
using Microsoft.Identity.Client;
using XboxAuthNet.Game;
using XboxAuthNet.Game.Accounts;
using XboxAuthNet.Game.Msal;
using XboxAuthNet.OAuth;
using XboxAuthNet.XboxLive;

namespace TheLadsLauncher.Services;

/// <summary>Owns the launcher account cache and serializes token/cache mutations.</summary>
public sealed class MicrosoftAccountService
{
    private readonly IMicrosoftLoginBackend _handler;
    private readonly IPublicClientApplication? _app;
    private readonly ProtectedAccountStorage? _accountStorage;
    public string? CacheRecoveryNotice => _accountStorage?.RecoveryNotice;
    private readonly Task _cacheReady;
    private readonly SemaphoreSlim _gate = new(1, 1);
    private readonly Func<MSession, CancellationToken, Task>? _verifyLicense;
    private readonly Dictionary<string, string> _verifiedTokens = new(StringComparer.OrdinalIgnoreCase);
    public IXboxGameAccountManager AccountManager => _handler.AccountManager;

    public MicrosoftAccountService(IPublicClientApplication app, IAuthenticationProvider provider,
        string baseDirectory, HttpClient httpClient)
    {
        _app = app;
        _verifyLicense = (session, ct) => MinecraftLicenseVerifier.VerifyAsync(httpClient, session, ct);
        string authDir = Path.Combine(baseDirectory, "auth");
        Directory.CreateDirectory(authDir);
        var cacheSettings = new MsalCacheSettings { CacheDir = authDir, CacheFileName = "msal.cache" };
        // Import the previous library cache once. Never delete another launcher's cache.
        string previousMsal = Path.Combine(new MsalCacheSettings().CacheDir, new MsalCacheSettings().CacheFileName);
        string currentMsal = Path.Combine(authDir, cacheSettings.CacheFileName);
        bool importLegacy = string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("THELADS_DIR"));
        if (importLegacy && !File.Exists(currentMsal) && File.Exists(previousMsal)) File.Copy(previousMsal, currentMsal);
        _cacheReady = MsalClientHelper.RegisterCache(app, cacheSettings);
        var storage = new ProtectedAccountStorage(Path.Combine(authDir, "accounts.dat"),
            importLegacy ? Path.Combine(MinecraftPath.GetOSDefaultPath(), "cml_accounts.json") : null);
        _accountStorage = storage;
        var handler = new JELoginHandlerBuilder { HttpClient = httpClient }
            .WithOAuthProvider(provider)
            .WithAccountManager(new JsonXboxGameAccountManager(storage, JEGameAccount.FromSessionStorage,
                JsonXboxGameAccountManager.DefaultSerializerOption))
            .Build();
        _handler = new CmlMicrosoftLoginBackend(handler, provider);
    }

    public MicrosoftAccountService(IMicrosoftLoginBackend backend, Task? cacheReady = null,
        Func<MSession, CancellationToken, Task>? verifyLicense = null)
    {
        _handler = backend;
        _cacheReady = cacheReady ?? Task.CompletedTask;
        _verifyLicense = verifyLicense;
    }

    public Task<MSession> AuthenticateInteractively(CancellationToken cancellationToken = default) =>
        AuthenticateInteractively(null, cancellationToken);

    public async Task<MSession> AuthenticateInteractively(IXboxGameAccount? expectedAccount,
        CancellationToken cancellationToken = default)
    {
        await _gate.WaitAsync(cancellationToken);
        try
        {
            await _cacheReady.WaitAsync(cancellationToken);
            // A new account preserves the previous identity when the browser selects a different user.
            var session = await _handler.AuthenticateInteractively(cancellationToken);
            ValidateSession(session);
            if (expectedAccount?.Identifier is { } expected &&
                !string.Equals(expected.Replace("-", ""), session.UUID?.Replace("-", ""), StringComparison.OrdinalIgnoreCase))
                throw new InvalidOperationException("You signed in to a different Minecraft account. Select that account in the launcher or sign in again with the selected account.");
            await VerifyLicenseOnceAsync(session, cancellationToken);
            return session;
        }
        finally { _gate.Release(); }
    }

    public async Task<MSession> AuthenticateSilently(IXboxGameAccount account,
        CancellationToken cancellationToken = default, bool forceRefresh = false)
    {
        await _gate.WaitAsync(cancellationToken);
        try
        {
            await _cacheReady.WaitAsync(cancellationToken);
            // SaveAccounts reloads account objects. Always resolve the current copy under the gate.
            var current = AccountManager.GetAccounts().FirstOrDefault(a => a.Identifier == account.Identifier)
                ?? throw new InvalidOperationException("This account was removed. Add it again before launching.");
            if (!forceRefresh && current is JEGameAccount cached &&
                cached.Token?.Validate() == true && cached.Token.ExpiresOn > DateTime.UtcNow.AddMinutes(2))
            {
                var ready = cached.ToLauncherSession();
                ValidateSession(ready);
                await VerifyLicenseOnceAsync(ready, cancellationToken);
                return ready;
            }
            var session = await _handler.AuthenticateSilently(current, cancellationToken);
            ValidateSession(session);
            await VerifyLicenseOnceAsync(session, cancellationToken);
            return session;
        }
        finally { _gate.Release(); }
    }

    public async Task Signout(IXboxGameAccount account)
    {
        await _gate.WaitAsync();
        try
        {
            await _cacheReady;
            var current = AccountManager.GetAccounts().FirstOrDefault(a => a.Identifier == account.Identifier);
            if (current != null) await _handler.Signout(current);
            if (account.Identifier != null) _verifiedTokens.Remove(account.Identifier.Replace("-", ""));
        }
        finally { _gate.Release(); }
    }

    /// <summary>Refreshes the saved identity, reopening sign-in only when Microsoft requires it.</summary>
    public async Task<MSession> RefreshOrSignInAsync(IXboxGameAccount account,
        CancellationToken cancellationToken = default)
    {
        try { return await AuthenticateSilently(account, cancellationToken, forceRefresh: true); }
        catch (Exception error) when (NeedsInteractiveLogin(error))
        {
            cancellationToken.ThrowIfCancellationRequested();
            return await AuthenticateInteractively(account, cancellationToken);
        }
    }

    public async Task ClearAsync()
    {
        await _gate.WaitAsync();
        try
        {
            await _cacheReady;
            if (_app != null)
                foreach (var account in await _app.GetAccountsAsync()) await _app.RemoveAsync(account);
            AccountManager.GetAccounts(); // Ensure lazy loading cannot restore deleted accounts.
            AccountManager.ClearAccounts();
            _verifiedTokens.Clear();
        }
        finally { _gate.Release(); }
    }

    public static void ValidateSession(MSession session)
    {
        if (string.IsNullOrWhiteSpace(session.Username) ||
            !Guid.TryParse(session.UUID, out _) || string.IsNullOrWhiteSpace(session.AccessToken) ||
            session.AccessToken == "0")
            throw new InvalidOperationException("Microsoft sign-in did not return a complete Minecraft Java profile. Check that this account owns Java Edition and has a Minecraft username.");
    }

    private async Task VerifyLicenseOnceAsync(MSession session, CancellationToken cancellationToken)
    {
        string id = session.UUID!.Replace("-", "");
        if (_verifyLicense == null || (_verifiedTokens.TryGetValue(id, out var token) && token == session.AccessToken)) return;
        await _verifyLicense(session, cancellationToken);
        _verifiedTokens[id] = session.AccessToken!;
    }

    public static bool NeedsInteractiveLogin(Exception error)
    {
        for (Exception? current = error; current != null; current = current.InnerException)
            if (current is MsalUiRequiredException || current is MsalException { ErrorCode: "invalid_grant" }) return true;
        return false;
    }

    public static string DescribeError(Exception error)
    {
        for (Exception? current = error; current != null; current = current.InnerException)
        {
            if (current is AccountVerificationException) return current.Message;
            if (current is OperationCanceledException) return "Microsoft sign-in cancelled or timed out. Try again when ready.";
            if (current is MsalException msal)
                return msal.ErrorCode switch
                {
                    "authorization_declined" => "Microsoft sign-in was declined. Start sign-in again to continue.",
                    "expired_token" or "code_expired" => "The sign-in code expired. Start sign-in again for a new code.",
                    "invalid_client" or "unauthorized_client" or "invalid_scope" => "Microsoft rejected this launcher's application registration. Configure an approved Microsoft client ID in Settings.",
                    _ when current is MsalUiRequiredException => "This account needs to sign in again. Use Add Microsoft Account.",
                    _ => $"Microsoft sign-in failed{DescribeServiceCodes(msal is MsalServiceException service ? service.StatusCode : 0)}. Try again or check the launcher's application registration."
                };
            // These types identify a service stage, not its exact endpoint or underlying cause.
            // Never include their raw error strings, response messages, redirects or user IDs.
            if (current is XboxAuthException xbox)
                return $"Xbox Live/XSTS authentication failed{DescribeServiceCodes(xbox.StatusCode, xbox.Error)}. Check the account's Xbox profile, family permissions and the launcher's application setup.";
            if (current is JEAuthException minecraft)
                return $"Minecraft Services authentication/profile failed{DescribeServiceCodes(minecraft.StatusCode)}. Check Java Edition access, Xbox profile/family permissions and the launcher's Minecraft API approval. This status alone does not identify the cause.";
            if (current is MicrosoftOAuthException oauth)
                return $"Microsoft OAuth sign-in failed{DescribeServiceCodes(oauth.StatusCode)}. Try signing in again or check the launcher's application registration.";
            if (current is HttpRequestException http)
                return $"Microsoft or Minecraft services request failed{DescribeServiceCodes((int?)http.StatusCode ?? 0)}. Check your connection and try again.";
        }
        // Do not expose raw OAuth responses, authorization codes or tokens in the UI/log.
        if (error is InvalidOperationException && error.Message.StartsWith("You signed in to a different")) return error.Message;
        if (error is InvalidOperationException && error.Message.StartsWith("Microsoft sign-in did not")) return error.Message;
        return "Minecraft account verification failed. Check Java Edition ownership, create an Xbox profile if needed, and check family permissions. If Microsoft reports success but this persists, the launcher's client ID may need Minecraft Services approval.";
    }

    private static string DescribeServiceCodes(int statusCode, string? xboxError = null)
    {
        var codes = new List<string>();
        if (statusCode is >= 100 and <= 599) codes.Add($"HTTP {statusCode}");
        // XErr is an unsigned numeric code. Parse and reformat it rather than echoing API text.
        if (!string.IsNullOrEmpty(xboxError) && xboxError.Length <= 10)
        {
            bool hex = xboxError.StartsWith("0x", StringComparison.OrdinalIgnoreCase);
            if (uint.TryParse(hex ? xboxError.AsSpan(2) : xboxError.AsSpan(),
                hex ? NumberStyles.AllowHexSpecifier : NumberStyles.None, CultureInfo.InvariantCulture, out var code))
                codes.Add($"XErr 0x{code:X8}");
        }
        return codes.Count == 0 ? "" : " (" + string.Join(", ", codes) + ")";
    }
}

public interface IMicrosoftLoginBackend
{
    IXboxGameAccountManager AccountManager { get; }
    Task<MSession> AuthenticateInteractively(CancellationToken cancellationToken);
    Task<MSession> AuthenticateSilently(IXboxGameAccount account, CancellationToken cancellationToken);
    Task Signout(IXboxGameAccount account);
}

internal sealed class CmlMicrosoftLoginBackend(JELoginHandler handler, IAuthenticationProvider provider) : IMicrosoftLoginBackend
{
    public IXboxGameAccountManager AccountManager => handler.AccountManager;
    public Task<MSession> AuthenticateInteractively(CancellationToken cancellationToken) => handler.AuthenticateInteractively(cancellationToken);
    public Task<MSession> AuthenticateSilently(IXboxGameAccount account, CancellationToken cancellationToken)
    {
        // The service already chose to refresh. CmlLib's ordinary silent flow can
        // reuse a Minecraft token until its exact expiry, defeating early/manual refresh.
        var authenticator = handler.CreateAuthenticator(account, cancellationToken);
        authenticator.AddAuthenticatorWithoutValidator(provider.AuthenticateSilently());
        authenticator.AddForceXboxAuthForJE(xbox => xbox.Basic());
        authenticator.AddForceJEAuthenticator();
        return authenticator.ExecuteForLauncherAsync();
    }
    public Task Signout(IXboxGameAccount account) => handler.Signout(account);
}
