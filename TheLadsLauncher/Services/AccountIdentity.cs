using System;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using CmlLib.Core.Auth;

namespace TheLadsLauncher.Services;

public static class AccountIdentity
{
    public static string NormalizeOfflineName(string username)
    {
        string name = username.Trim();
        if (!Regex.IsMatch(name, "^[A-Za-z0-9_]{3,16}$"))
            throw new ArgumentException("Offline usernames must contain 3–16 letters, numbers or underscores.");
        return name;
    }

    public static string OfflineUuid(string username)
    {
        byte[] hash = MD5.HashData(Encoding.UTF8.GetBytes("OfflinePlayer:" + NormalizeOfflineName(username)));
        hash[6] = (byte)((hash[6] & 0x0f) | 0x30);
        hash[8] = (byte)((hash[8] & 0x3f) | 0x80);
        // Java UUID bytes use network order; new Guid(byte[]) reverses the first fields.
        return Guid.ParseExact(Convert.ToHexString(hash), "N").ToString("D");
    }

    public static MSession CreateOfflineSession(string username) => new()
    {
        Username = NormalizeOfflineName(username),
        UUID = OfflineUuid(username),
        AccessToken = "0",
        UserType = "legacy"
    };
}
