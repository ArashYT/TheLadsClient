using System;
using System.IO;
using System.IO.Pipes;
using System.Linq;
using System.Net;
using System.Runtime.Versioning;
using System.Security.Principal;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Win32;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public enum DeepLinkActionType
{
    None,
    JoinServer,
    LaunchProfile,
    InstallPack,
    Unknown
}

public sealed record DeepLinkAction(DeepLinkActionType Type, string? Target, int Port = 25565, string? Extra = null);

/// <summary>
/// Handles custom lads:// URL protocol registration, single-instance forwarding, host validation,
/// and URI parsing for deep linking (e.g. lads://join/play.thelads.net:25565).
/// </summary>
public static class DeepLinkService
{
    public const string ProtocolScheme = "lads";
    /// <summary>Pipe names are machine-wide: one per Windows user (SID), so another user's launcher can neither take it nor get these links.</summary>
    public static readonly string PipeName = "TheLadsClient-DeepLinkPipe-" + CurrentUserSid();

    private static string CurrentUserSid()
    {
        using var identity = WindowsIdentity.GetCurrent();
        return identity.User?.Value ?? Environment.UserName;
    }

    public static event Action<DeepLinkAction>? DeepLinkReceived;
    public static Action<string>? Log { get; set; }
    public static Func<string, NamedPipeServerStream> ServerFactory { get; set; } = CreateIpcServer;

    /// <summary>
    /// Validates that a host name is a valid DNS hostname, IPv4, or IPv6 address,
    /// without injection characters or whitespace.
    /// </summary>
    public static bool IsValidHost(string? host)
    {
        if (string.IsNullOrWhiteSpace(host)) return false;
        host = host.Trim();
        if (host.Length > 255) return false;
        if (host.Contains(' ') || host.Contains('\t') || host.Contains('/') || host.Contains('\\')
            || host.Contains('"') || host.Contains('\'') || host.Contains(';') || host.Contains('&'))
        {
            return false;
        }

        var hostType = Uri.CheckHostName(host);
        return hostType is UriHostNameType.Dns or UriHostNameType.IPv4 or UriHostNameType.IPv6;
    }

    /// <summary>
    /// Validates that a network port is between 1 and 65535.
    /// </summary>
    public static bool IsValidPort(int port) => port is >= 1 and <= 65535;

    /// <summary>A profile name or id a link may name: 1-100 characters, none of them control characters (it reaches the log and a dialog).</summary>
    public static bool IsValidProfileName(string? name) =>
        !string.IsNullOrWhiteSpace(name) && name.Length <= 100 && !name.Any(char.IsControl);

    /// <summary>Why a link may not make <paramref name="profile"/> the active one (the version picker's own checks), or null when it may.</summary>
    public static string? ProfileSwitchRefusal(LauncherProfile profile)
    {
        if (GameVersionPolicy.IsComingNever(profile.MinecraftVersion)) return GameVersionPolicy.ComingNeverMessage;
        if (GameVersionPolicy.IsPaused(profile.MinecraftVersion)) return GameVersionPolicy.PausedMessage;
        if (!ProfileTools.PlayableVersions.Contains(profile.MinecraftVersion)) return $"Minecraft {profile.MinecraftVersion} is not offered on the Play screen.";
        try { GameVersionPolicy.ResolveVersionId(profile); return null; }
        catch (ArgumentException ex) { return ex.Message; } // a profile kept for repair: switching to it would throw in ApplyProfile
    }

    /// <summary>
    /// Parses a lads:// URI string into a typed DeepLinkAction.
    /// Supports:
    /// - lads://join/&lt;server[:port]&gt; or lads://connect/&lt;server[:port]&gt;
    /// - lads://launch/&lt;profileName&gt; or lads://profile/&lt;profileName&gt;
    /// - lads://install/&lt;urlOrPath&gt;
    /// Unknown or malformed URIs return DeepLinkActionType.Unknown or None.
    /// </summary>
    public static DeepLinkAction ParseUri(string? uriString)
    {
        if (string.IsNullOrWhiteSpace(uriString))
            return new DeepLinkAction(DeepLinkActionType.None, null);

        uriString = uriString.Trim();
        if (uriString.Length > 2048 || !uriString.StartsWith(ProtocolScheme + "://", StringComparison.OrdinalIgnoreCase))
            return new DeepLinkAction(DeepLinkActionType.None, null);

        string remainder = uriString[(ProtocolScheme.Length + 3)..];
        remainder = remainder.TrimStart('/');

        int slash = remainder.IndexOf('/');
        string action = slash >= 0 ? remainder[..slash].ToLowerInvariant() : remainder.ToLowerInvariant();
        string payload = slash >= 0 ? Uri.UnescapeDataString(remainder[(slash + 1)..].Trim('/')) : "";

        return action switch
        {
            "join" or "connect" or "server" => ParseServerAction(payload),
            "launch" or "profile" or "play" => IsValidProfileName(payload)
                ? new DeepLinkAction(DeepLinkActionType.LaunchProfile, payload)
                : new DeepLinkAction(DeepLinkActionType.None, null, Extra: "Invalid profile name"),
            "install" or "modpack" => new DeepLinkAction(DeepLinkActionType.InstallPack, payload),
            _ => new DeepLinkAction(DeepLinkActionType.Unknown, action, Extra: uriString)
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

        if (!IsValidHost(host) || !IsValidPort(port))
            return new DeepLinkAction(DeepLinkActionType.None, null, Extra: $"Invalid host or port: '{payload}'");

        return new DeepLinkAction(DeepLinkActionType.JoinServer, host, port);
    }

    /// <summary>
    /// Tries to forward a lads:// URI to an already-running instance over a local named pipe.
    /// Returns true if delivered to an active instance; false if no instance was listening.
    /// </summary>
    public static async Task<bool> TryForwardToRunningInstanceAsync(string uriString, string? pipeName = null)
    {
        try
        {
            // CurrentUserOnly: refuses a pipe another user created under this name.
            using var client = new NamedPipeClientStream(".", pipeName ?? PipeName, PipeDirection.Out, PipeOptions.CurrentUserOnly);
            await client.ConnectAsync(300);
            using var writer = new StreamWriter(client) { AutoFlush = true };
            await writer.WriteLineAsync(uriString);
            return true;
        }
        catch
        {
            return false;
        }
    }

    /// <summary>
    /// Starts background named pipe listener for deep links arriving while the launcher is open.
    /// </summary>
    public static void StartIpcListener(Action<DeepLinkAction> onActionReceived, string? pipeName = null, CancellationToken cancellationToken = default)
    {
        _ = Task.Run(async () =>
        {
            bool unauthorizedLogged = false;
            string pipe = pipeName ?? PipeName;
            while (!cancellationToken.IsCancellationRequested)
            {
                try
                {
                    using var server = ServerFactory(pipe);
                    await server.WaitForConnectionAsync(cancellationToken);
                    using var reader = new StreamReader(server);
                    string? line = await reader.ReadLineAsync(cancellationToken);
                    if (!string.IsNullOrWhiteSpace(line))
                    {
                        var action = ParseUri(line);
                        onActionReceived(action);
                        DeepLinkReceived?.Invoke(action);
                    }
                }
                catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
                {
                    break;
                }
                catch (UnauthorizedAccessException ex)
                {
                    if (!unauthorizedLogged)
                    {
                        unauthorizedLogged = true;
                        Log?.Invoke($"[DeepLink] Pipe listener unauthorized: pipe already taken (another launcher instance, or a program squatting on it): {ex.Message}");
                    }
                    try { await Task.Delay(500, cancellationToken); }
                    catch (OperationCanceledException) { break; }
                }
                catch
                {
                    try { await Task.Delay(500, cancellationToken); }
                    catch (OperationCanceledException) { break; }
                }
            }
        });
    }

    /// <summary>The listening end: one instance, and only the current Windows user may open it (PipeOptions.CurrentUserOnly).</summary>
    public static NamedPipeServerStream CreateIpcServer(string pipeName) =>
        new(pipeName, PipeDirection.In, 1, PipeTransmissionMode.Byte, PipeOptions.Asynchronous | PipeOptions.CurrentUserOnly);

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

    /// <summary>The command lads:// links run now, or null when the protocol is not registered.</summary>
    [SupportedOSPlatform("windows")]
    public static string? RegisteredCommand()
    {
        if (!OperatingSystem.IsWindows()) return null;
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(@"Software\Classes\" + ProtocolScheme + @"\shell\open\command");
            return key?.GetValue("") as string;
        }
        catch
        {
            return null;
        }
    }

    /// <summary>
    /// The installed launcher's stub ("&lt;root&gt;\The Lads Client.exe") when this process is a Velopack install
    /// (&lt;root&gt;\current\TheLadsLauncher.exe with Update.exe beside current and no .portable marker); null for dev, QA and portable builds,
    /// which must never own lads:// links (a QA build registered first kept them pointing into a temp folder).
    /// </summary>
    public static string? InstalledStub(string? processPath)
    {
        string? current = Path.GetDirectoryName(processPath);
        if (current == null || !string.Equals(Path.GetFileName(current), "current", StringComparison.OrdinalIgnoreCase)) return null;
        string? root = Path.GetDirectoryName(current);
        if (root == null || !File.Exists(Path.Combine(root, "Update.exe")) || File.Exists(Path.Combine(root, ".portable"))) return null;
        string stub = Path.Combine(root, "The Lads Client.exe");
        return File.Exists(stub) ? stub : null;
    }

    /// <summary>Whether lads:// must be (re)written so it runs <paramref name="exe"/>: missing, or pointing at another exe.</summary>
    public static bool NeedsRegistration(string? registeredCommand, string exe) =>
        registeredCommand == null || !registeredCommand.StartsWith("\"" + exe + "\"", StringComparison.OrdinalIgnoreCase);
}
