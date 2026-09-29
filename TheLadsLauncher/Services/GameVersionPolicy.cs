using System;
using System.Text.RegularExpressions;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public static class GameVersionPolicy
{
    private static readonly Regex VersionComponent = new(@"\A[A-Za-z0-9][A-Za-z0-9._+-]*\z");
    private static readonly Regex LoaderVersion = new(@"\A[0-9]+(?:\.[0-9]+)+(?:[-+][A-Za-z0-9.-]+)?\z");

    public static bool RequiresBundledCore(string minecraftVersion) =>
        minecraftVersion is "1.21.11" or "26.2";

    public static string ResolveVersionId(LauncherProfile profile)
    {
        ArgumentNullException.ThrowIfNull(profile);
        ValidateMinecraftVersion(profile.MinecraftVersion);
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
            "26.2" => 25,
            "1.21.11" or "1.21.1" => 21,
            _ => 0
        };
        if (minimum == 0 && manifestJavaMajor == null)
            throw new InvalidOperationException($"Java requirement for Minecraft {minecraftVersion} must come from its version manifest.");
        return Math.Max(minimum, manifestJavaMajor ?? 0);
    }

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
