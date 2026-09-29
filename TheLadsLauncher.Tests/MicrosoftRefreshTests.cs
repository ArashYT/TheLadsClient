using System.Net;
using System.Text;
using System.Text.Json;
using CmlLib.Core.Auth.Microsoft;
using CmlLib.Core.Auth.Microsoft.Sessions;
using TheLadsLauncher.Services;
using XboxAuthNet.Game;
using XboxAuthNet.Game.Accounts;
using XboxAuthNet.Game.Authenticators;
using XboxAuthNet.OAuth;
using Xunit;

namespace TheLadsLauncher.Tests;

public class MicrosoftRefreshTests
{
    [Theory]
    [InlineData(60, true)]
    [InlineData(1, false)]
    public async Task RealBackendRenewsMinecraftTokenOnRequestedOrEarlyRefresh(int minutesRemaining, bool forceRefresh)
    {
        using var dir = new TestDirectory();
        var manager = new JsonXboxGameAccountManager(Path.Combine(dir.Path, "accounts.json"), JEGameAccount.FromSessionStorage, null);
        manager.GetAccounts();
        var account = (JEGameAccount)manager.NewAccount();
        string uuid = Guid.NewGuid().ToString("N");
        var expires = DateTime.UtcNow.AddMinutes(minutesRemaining);
        string oldToken = Token(expires);
        account.SessionStorage.Set("JEProfile", new JEProfile { Username = "Tester", UUID = uuid });
        account.SessionStorage.Set("JEToken", new JEToken { AccessToken = oldToken, ExpiresOn = expires });
        using var responses = new RefreshResponses(uuid);
        using var http = new HttpClient(responses);
        var provider = new SyntheticOAuthProvider();
        var handler = new JELoginHandlerBuilder { HttpClient = http }.WithOAuthProvider(provider).WithAccountManager(manager).Build();
        // Exercise the production library adapter without touching the user's MSAL/account caches.
        var backendType = typeof(MicrosoftAccountService).Assembly.GetType("TheLadsLauncher.Services.CmlMicrosoftLoginBackend")!;
        var backend = (IMicrosoftLoginBackend)Activator.CreateInstance(backendType, handler, provider)!;
        var service = new MicrosoftAccountService(backend);

        var session = await service.AuthenticateSilently(account, forceRefresh: forceRefresh);

        Assert.Equal(responses.NewToken, session.AccessToken);
        Assert.NotEqual(oldToken, session.AccessToken);
        Assert.Equal(1, responses.MinecraftTokenRequests);
        Assert.Equal(uuid, session.UUID);
        Assert.Equal("UpdatedName", session.Username);
        // The renewed token is reusable, including when the old account object is stale.
        await service.AuthenticateSilently(account);
        Assert.Equal(1, responses.MinecraftTokenRequests);
    }

    private static string Token(DateTime expires)
    {
        string body = Convert.ToBase64String(Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new { exp = new DateTimeOffset(expires).ToUnixTimeSeconds() })))
            .TrimEnd('=').Replace('+', '-').Replace('/', '_');
        return "e30." + body + ".c2ln";
    }

    private sealed class SyntheticOAuthProvider : IAuthenticationProvider, IAuthenticator
    {
        public IAuthenticator Authenticate() => this;
        public IAuthenticator AuthenticateInteractively() => this;
        public IAuthenticator AuthenticateSilently() => this;
        public ISessionValidator CreateSessionValidator() => StaticValidator.Invalid;
        public IAuthenticator ClearSession() => this;
        public IAuthenticator Signout() => this;
        public ValueTask ExecuteAsync(AuthenticateContext context)
        {
            context.SessionStorage.Set("MicrosoftOAuth", new MicrosoftOAuthResponse { AccessToken = "synthetic-oauth-token" });
            return ValueTask.CompletedTask;
        }
    }

    private sealed class RefreshResponses(string uuid) : HttpMessageHandler
    {
        public string NewToken { get; } = Token(DateTime.UtcNow.AddHours(4));
        public int MinecraftTokenRequests;

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            string body;
            switch (request.RequestUri!.AbsoluteUri)
            {
                case "https://user.auth.xboxlive.com/user/authenticate":
                case "https://xsts.auth.xboxlive.com/xsts/authorize":
                    body = JsonSerializer.Serialize(new { IssueInstant = DateTime.UtcNow, NotAfter = DateTime.UtcNow.AddHours(4), Token = "synthetic-xbox-token", DisplayClaims = new { xui = new[] { new { uhs = "123", xid = "456", gtg = "Tester" } } } });
                    break;
                case "https://api.minecraftservices.com/authentication/login_with_xbox":
                    MinecraftTokenRequests++;
                    body = JsonSerializer.Serialize(new { access_token = NewToken, expires_in = 14400, token_type = "Bearer" });
                    break;
                case "https://api.minecraftservices.com/minecraft/profile":
                    body = JsonSerializer.Serialize(new { id = uuid, name = "UpdatedName", skins = Array.Empty<object>(), capes = Array.Empty<object>() });
                    break;
                default:
                    throw new InvalidOperationException("Unexpected authentication endpoint: " + request.RequestUri.GetLeftPart(UriPartial.Path));
            }
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(body, Encoding.UTF8, "application/json") });
        }
    }
}
