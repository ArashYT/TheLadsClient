using System;
using System.Text.RegularExpressions;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public static class GameVersionPolicy
{
    private static readonly Regex VersionComponent = new(@"\A[A-Za-z0-9][A-Za-z0-9._+-]*\z");
    private static readonly Regex LoaderVersion = new(@"\A[0-9]+(?:\.[0-9]+)+(?:[-+][A-Za-z0-9.-]+)?\z");

    /// <summary>Minecraft 1.8.9 runs on this Forge build only (installed by LaunchService.InstallForgeAsync).</summary>
    public const string ForgeMinecraftVersion = "1.8.9", ForgeBuild = "11.15.1.2318";
    public const string ForgeVersionId = "1.8.9-forge1.8.9-11.15.1.2318-1.8.9";

    /// <summary>Compares Minecraft versions descending: 26.x or newer at the top (sorted numerically), then 1.21.x, down to 1.8.9.</summary>
    public static int CompareDescending(string? v1, string? v2)
    {
        if (string.Equals(v1, v2, StringComparison.OrdinalIgnoreCase)) return 0;
        if (string.IsNullOrWhiteSpace(v1)) return 1;
        if (string.IsNullOrWhiteSpace(v2)) return -1;

        var parts1 = v1.Split(new[] { '.', '-', '+' }, StringSplitOptions.RemoveEmptyEntries);
        var parts2 = v2.Split(new[] { '.', '-', '+' }, StringSplitOptions.RemoveEmptyEntries);

        int maxLen = Math.Max(parts1.Length, parts2.Length);
        for (int i = 0; i < maxLen; i++)
        {
            string s1 = i < parts1.Length ? parts1[i] : "";
            string s2 = i < parts2.Length ? parts2[i] : "";
            bool isNum1 = int.TryParse(s1, out int n1);
            bool isNum2 = int.TryParse(s2, out int n2);

            if (isNum1 && isNum2)
            {
                if (n1 != n2) return n2.CompareTo(n1); // descending: higher numbers first
            }
            else if (isNum1 != isNum2)
            {
                return isNum1 ? -1 : 1;
            }
            else
            {
                int cmp = string.Compare(s2, s1, StringComparison.OrdinalIgnoreCase);
                if (cmp != 0) return cmp;
            }
        }
        return string.Compare(v2, v1, StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>All release versions supported for modpacks and searches on Modrinth, sorted newest/highest first.</summary>
    public static readonly string[] ModrinthGameVersions = new[]
    {
        "26.3", "26.2", "26.1.2", "26.1.1", "26.1",
        "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
        "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
        "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
        "1.18.2", "1.18.1", "1.18",
        "1.17.1", "1.17",
        "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
        "1.15.2", "1.15.1", "1.15",
        "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
        "1.13.2", "1.13.1", "1.13",
        "1.12.2", "1.12.1", "1.12",
        "1.11.2", "1.11.1", "1.11",
        "1.10.2", "1.10.1", "1.10",
        "1.9.4", "1.9.3", "1.9.2", "1.9.1", "1.9",
        "1.8.9", "1.8.8", "1.8.7", "1.8",
        "1.7.10"
    };

    public static bool RequiresBundledCore(string minecraftVersion) =>
        minecraftVersion is "1.20.1" or "1.21.11" or "26.2" or "26.3" or ForgeMinecraftVersion;

    /// <summary>A version The Lads Client no longer offers (1.21.1, dropped in 1.6.0). Saved profiles of it are kept in
    /// profiles.json, and their game folders untouched, but they are not listed or launched (see ProfileService).</summary>
    public static bool IsDropped(string minecraftVersion) => minecraftVersion == "1.21.1";

    /// <summary>26.2 support is paused (2026-10-08): its profile stays and is listed greyed out, but it is never launched, made
    /// active at startup or created anew. Its game folder and settings are left alone.</summary>
    public const string PausedMessage = "26.2 support is paused — use 26.3";
    public static bool IsPaused(string minecraftVersion) => minecraftVersion == "26.2";

    /// <summary>1.7.10 is never coming: listed greyed out in the version selector ("COMING NEVER!"), never launched or created.</summary>
    public const string ComingNeverMessage = "COMING NEVER!";
    public static bool IsComingNever(string? minecraftVersion) => minecraftVersion == "1.7.10";

    /// <summary>A bundled-Core version whose Core is a Fabric mod: all of them but 1.8.9, whose Core is a Forge mod.</summary>
    public static bool RequiresFabric(string minecraftVersion) => RequiresBundledCore(minecraftVersion) && !UsesForge(minecraftVersion);

    /// <summary>The loader is Forge, never Fabric: only 1.8.9.</summary>
    public static bool UsesForge(string minecraftVersion) => minecraftVersion == ForgeMinecraftVersion;

    public static string ResolveVersionId(LauncherProfile profile)
    {
        ArgumentNullException.ThrowIfNull(profile);
        ValidateMinecraftVersion(profile.MinecraftVersion);
        if (UsesForge(profile.MinecraftVersion))
            return string.IsNullOrWhiteSpace(profile.FabricVersion) ? ForgeVersionId
                : throw new ArgumentException($"Minecraft {profile.MinecraftVersion} runs on Forge. Remove the Fabric loader '{profile.FabricVersion}' from this profile.", nameof(profile));
        if (string.IsNullOrWhiteSpace(profile.FabricVersion))
            return profile.MinecraftVersion;

        var fabric = profile.FabricVersion;
        if (fabric.StartsWith("fabric-loader-", StringComparison.Ordinal))
        {
            // Match the selected MC suffix first: snapshot IDs can themselves contain hyphens.
            var suffix = "-" + profile.MinecraftVersion;
            if (!fabric.EndsWith(suffix, StringComparison.Ordinal)
                || fabric.Length <= "fabric-loader-".Length + suffix.Length)
                throw new ArgumentException($"Fabric version '{fabric}' does not match Minecraft {profile.MinecraftVersion}.", nameof(profile));
            var loader = fabric.Substring("fabric-loader-".Length,
                fabric.Length - "fabric-loader-".Length - suffix.Length);
            ValidateLoader(loader);
            return fabric;
        }

        ValidateLoader(fabric);
        return $"fabric-loader-{fabric}-{profile.MinecraftVersion}";
    }

    public static int GetRequiredJavaMajor(string minecraftVersion, int? manifestJavaMajor = null)
    {
        ValidateMinecraftVersion(minecraftVersion);
        if (manifestJavaMajor is <= 0)
            throw new ArgumentOutOfRangeException(nameof(manifestJavaMajor));
        var minimum = minecraftVersion switch
        {
            "26.2" or "26.3" => 25,
            "1.21.11" or "1.21.1" => 21,
            "1.20.1" => 17,
            ForgeMinecraftVersion => 25, // Forge on RetroFuturaBootstrap via 8to25 (LaunchService.With8to25)
            _ => 0
        };
        if (minimum == 0 && manifestJavaMajor == null)
            throw new InvalidOperationException($"Java requirement for Minecraft {minecraftVersion} must come from its version manifest.");
        return Math.Max(minimum, manifestJavaMajor ?? 0);
    }

    /// <summary>Whether a runtime reporting <paramref name="actualJava"/> (null: unknown) can run the version.</summary>
    public static bool AcceptsJava(string minecraftVersion, int requiredJava, int? actualJava) => actualJava >= requiredJava;

    /// <summary>"Java 21 or newer".</summary>
    public static string DescribeJava(string minecraftVersion, int requiredJava) => $"Java {requiredJava} or newer";

    internal static void ValidateMinecraftVersion(string minecraftVersion)
    {
        if (string.IsNullOrWhiteSpace(minecraftVersion) || !VersionComponent.IsMatch(minecraftVersion)
            || minecraftVersion.StartsWith("fabric-loader-", StringComparison.Ordinal)
            || minecraftVersion is "latest.release" or "latest.snapshot" or "latest-release" or "latest-snapshot")
            throw new ArgumentException("Select an explicit Minecraft version (for example 1.21.11 or 26.2).", nameof(minecraftVersion));
    }

    private static void ValidateLoader(string loader)
    {
        if (!LoaderVersion.IsMatch(loader))
            throw new ArgumentException($"Invalid Fabric loader version '{loader}'.", nameof(loader));
    }
}
