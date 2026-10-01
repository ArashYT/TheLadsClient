using System;
using System.Text.RegularExpressions;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public static class GameVersionPolicy
{
    private static readonly Regex VersionComponent = new(@"\A[A-Za-z0-9][A-Za-z0-9._+-]*\z");
    private static readonly Regex LoaderVersion = new(@"\A[0-9]+(?:\.[0-9]+)+(?:[-+][A-Za-z0-9.-]+)?\z");

    /// <summary>Minecraft 1.8.9 runs on this Forge build only (installed by CmlLib.Core.Installer.Forge from the build number).</summary>
    public const string ForgeMinecraftVersion = "1.8.9", ForgeBuild = "11.15.1.2318";
    public const string ForgeVersionId = "1.8.9-forge1.8.9-11.15.1.2318-1.8.9";

    public static bool RequiresBundledCore(string minecraftVersion) =>
        minecraftVersion is "1.21.1" or "1.21.11" or "26.2" or "26.3" or ForgeMinecraftVersion;

    /// <summary>A bundled-Core version whose Core is a Fabric mod: all of them but 1.8.9, whose Core is a Forge mod.</summary>
    public static bool RequiresFabric(string minecraftVersion) => RequiresBundledCore(minecraftVersion) && !UsesForge(minecraftVersion);

    /// <summary>The loader is Forge, never Fabric: only 1.8.9.</summary>
    public static bool UsesForge(string minecraftVersion) => minecraftVersion == ForgeMinecraftVersion;

    /// <summary>1.8.9 now participates in full shared parity: saves, resourcepacks, shaders, and configs are unified.</summary>
    public static bool KeepsOwnWorlds(string minecraftVersion) => false;

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
            ForgeMinecraftVersion => 8,
            _ => 0
        };
        if (minimum == 0 && manifestJavaMajor == null)
            throw new InvalidOperationException($"Java requirement for Minecraft {minecraftVersion} must come from its version manifest.");
        var required = Math.Max(minimum, manifestJavaMajor ?? 0);
        // A stale profile value (e.g. 21 on a 1.8.9 profile) must never pick a runtime the game cannot start on.
        return GetMaximumJavaMajor(minecraftVersion) is int maximum ? Math.Min(required, maximum) : required;
    }

    /// <summary>The newest Java the version starts on, or null for no limit. Forge 1.8.9's LaunchWrapper casts the system class
    /// loader to URLClassLoader, which it is not since Java 9: Java 9 and newer crash before the game starts.</summary>
    public static int? GetMaximumJavaMajor(string minecraftVersion) => UsesForge(minecraftVersion) ? 8 : null;

    /// <summary>Whether a runtime reporting <paramref name="actualJava"/> (null: unknown) can run the version: at least the
    /// required Java and, for 1.8.9, not newer than its maximum.</summary>
    public static bool AcceptsJava(string minecraftVersion, int requiredJava, int? actualJava) =>
        actualJava >= requiredJava && !(actualJava > GetMaximumJavaMajor(minecraftVersion));

    /// <summary>"Java 21 or newer", or "Java 8 exactly" for a version with a maximum.</summary>
    public static string DescribeJava(string minecraftVersion, int requiredJava) => GetMaximumJavaMajor(minecraftVersion) switch
    {
        null => $"Java {requiredJava} or newer",
        int maximum when maximum == requiredJava => $"Java {requiredJava} exactly",
        int maximum => $"Java {requiredJava} to {maximum}"
    };

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
