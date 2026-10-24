using System;
using System.IO;
using System.Runtime.Versioning;
using Microsoft.Win32;

namespace TheLadsLauncher.Services;

public enum DeepLinkActionType
{
    None,
    JoinServer,
    LaunchProfile,
    InstallPack
}

public sealed record DeepLinkAction(DeepLinkActionType Type, string? Target, int Port = 25565, string? Extra = null);

/// <summary>
/// Handles custom lads:// URL protocol registration and parsing for deep linking (e.g. lads://join/play.thelads.net).
/// </summary>
public static class DeepLinkService
{
    public const string ProtocolScheme = "lads";

    /// <summary>
    /// Parses a lads:// URI string into a typed DeepLinkAction.
    /// Supports:
    /// - lads://join/&lt;server[:port]&gt; or lads://connect/&lt;server[:port]&gt;
    /// - lads://launch/&lt;profileName&gt; or lads://profile/&lt;profileName&gt;
    /// - lads://install/&lt;urlOrPath&gt;
    /// </summary>
    public static DeepLinkAction ParseUri(string? uriString)
    {
        if (string.IsNullOrWhiteSpace(uriString))
            return new DeepLinkAction(DeepLinkActionType.None, null);

        uriString = uriString.Trim();
        if (!uriString.StartsWith(ProtocolScheme + "://", StringComparison.OrdinalIgnoreCase))
            return new DeepLinkAction(DeepLinkActionType.None, null);

        string remainder = uriString[(ProtocolScheme.Length + 3)..];
        remainder = remainder.TrimStart('/');

        int slash = remainder.IndexOf('/');
        string action = slash >= 0 ? remainder[..slash].ToLowerInvariant() : remainder.ToLowerInvariant();
        string payload = slash >= 0 ? Uri.UnescapeDataString(remainder[(slash + 1)..].Trim('/')) : "";

        return action switch
        {
            "join" or "connect" or "server" => ParseServerAction(payload),
            "launch" or "profile" or "play" => new DeepLinkAction(DeepLinkActionType.LaunchProfile, payload),
            "install" or "modpack" => new DeepLinkAction(DeepLinkActionType.InstallPack, payload),
            _ => new DeepLinkAction(DeepLinkActionType.None, null)
        };
    }

    private static DeepLinkAction ParseServerAction(string payload)
    {
        if (string.IsNullOrWhiteSpace(payload))
            return new DeepLinkAction(DeepLinkActionType.None, null);

        int port = 25565;
        string host = payload;

        int colon = payload.LastIndexOf(':');
        if (colon > 0 && int.TryParse(payload[(colon + 1)..], out int p))
        {
            host = payload[..colon];
            port = p;
        }

        return new DeepLinkAction(DeepLinkActionType.JoinServer, host, port);
    }

    /// <summary>
    /// Registers the lads:// protocol in the Windows registry (HKCU\Software\Classes\lads).
    /// </summary>
    [SupportedOSPlatform("windows")]
    public static bool RegisterProtocol(string? executablePath = null)
    {
        if (!OperatingSystem.IsWindows()) return false;
        try
        {
            string exe = executablePath ?? Environment.ProcessPath ?? "";
            if (string.IsNullOrEmpty(exe) || !File.Exists(exe)) return false;

            using var key = Registry.CurrentUser.CreateSubKey(@"Software\Classes\" + ProtocolScheme);
            if (key == null) return false;

            key.SetValue("", "URL:The Lads Client Protocol");
            key.SetValue("URL Protocol", "");

            using var iconKey = key.CreateSubKey("DefaultIcon");
            iconKey?.SetValue("", $"\"{exe}\",0");

            using var cmdKey = key.CreateSubKey(@"shell\open\command");
            cmdKey?.SetValue("", $"\"{exe}\" \"%1\"");

            return true;
        }
        catch
        {
            return false;
        }
    }

    /// <summary>
    /// Checks whether the lads:// protocol is currently registered in Windows registry.
    /// </summary>
    [SupportedOSPlatform("windows")]
    public static bool IsProtocolRegistered()
    {
        if (!OperatingSystem.IsWindows()) return false;
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(@"Software\Classes\" + ProtocolScheme);
            return key != null && key.GetValue("URL Protocol") != null;
        }
        catch
        {
            return false;
        }
    }
}
