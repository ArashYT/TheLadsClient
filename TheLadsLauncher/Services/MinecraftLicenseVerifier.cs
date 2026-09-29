using System;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using CmlLib.Core.Auth;

namespace TheLadsLauncher.Services;

public sealed class AccountVerificationException(string message) : Exception(message);

public static class MinecraftLicenseVerifier
{
    public static async Task VerifyAsync(HttpClient http, MSession session, CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "https://api.minecraftservices.com/entitlements/license");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", session.AccessToken);
        using var response = await http.SendAsync(request, cancellationToken);
        if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)
            throw new AccountVerificationException($"Minecraft license verification failed (HTTP {(int)response.StatusCode}). Check this account and the launcher's Minecraft API approval, then sign in again. This status alone does not identify the cause.");
        response.EnsureSuccessStatusCode();
        using var json = await JsonDocument.ParseAsync(await response.Content.ReadAsStreamAsync(cancellationToken), cancellationToken: cancellationToken);
        if (!json.RootElement.TryGetProperty("items", out var items) || items.ValueKind != JsonValueKind.Array)
            throw new AccountVerificationException("Minecraft Services returned an incomplete license response. Try again later.");
        bool playable = items.EnumerateArray().Any(item => item.TryGetProperty("name", out var name)
            && name.ValueKind == JsonValueKind.String && name.GetString() is "game_minecraft" or "product_minecraft");
        if (!playable)
            throw new AccountVerificationException("This Microsoft account has no active Minecraft Java license. Use the account that owns Java Edition or has an active subscription that includes it.");
    }
}
