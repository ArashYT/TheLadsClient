using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public sealed record ExperienceDefinition(
    string Id,
    string Title,
    string Subtitle,
    string Description,
    string McVersion,
    string Loader,
    string LoaderVersion,
    int ModCount,
    string ServerAddress,
    string IconAsset,
    int RecommendedRamMb
);

public static class ExperiencesService
{
    public static readonly ExperienceDefinition ZeroSmp = new(
        Id: "zero-smp-1.20.1",
        Title: "Zero SMP",
        Subtitle: "Official The Lads Modpack",
        Description: "An expansive adventure featuring Alex's Caves, Create machinery, immersive sound physics, dynamic animations, and proximity voice chat. Pre-configured with the official Zero SMP server.",
        McVersion: "1.20.1",
        Loader: "forge",
        LoaderVersion: "47.4.10",
        ModCount: 128,
        ServerAddress: "nikzerosmp.my.pebble.host",
        IconAsset: "avares://TheLadsLauncher/Assets/zero_smp_icon.png",
        RecommendedRamMb: 6144
    );

    public static readonly IReadOnlyList<ExperienceDefinition> AllExperiences = new[] { ZeroSmp };

    public static string InstanceDirectory(string baseDirectory, string experienceId) =>
        Path.Combine(Modpacks.Root(baseDirectory), experienceId);

    public static bool IsInstalled(string baseDirectory, string experienceId)
    {
        var dir = InstanceDirectory(baseDirectory, experienceId);
        var json = Path.Combine(dir, "instance.json");
        var modsDir = Path.Combine(dir, "minecraft", "mods");
        return File.Exists(json) && Directory.Exists(modsDir) && Directory.EnumerateFiles(modsDir, "*.jar").Any();
    }

    public static ModpackInstance? GetInstance(string baseDirectory, string experienceId) =>
        Modpacks.List(baseDirectory).FirstOrDefault(i => i.Id == experienceId);

    public static async Task<ModpackInstance> InstallZeroSmpAsync(string baseDirectory, Action<string>? status, CancellationToken token)
    {
        var exp = ZeroSmp;
        var instDir = InstanceDirectory(baseDirectory, exp.Id);
        var gameDir = Path.Combine(instDir, "minecraft");
        var modsDir = Path.Combine(gameDir, "mods");
        var rpDir = Path.Combine(gameDir, "resourcepacks");

        Directory.CreateDirectory(modsDir);
        Directory.CreateDirectory(rpDir);

        status?.Invoke("Setting up Zero SMP instance...");

        string localSource = @"C:\Users\Arash\Downloads\Zero SMP - 1.20.1";
        if (Directory.Exists(localSource))
        {
            status?.Invoke("Installing modpack files and configurations...");
            await Task.Run(() =>
            {
                var overrides = Path.Combine(localSource, "overrides");
                if (Directory.Exists(overrides))
                {
                    var iconSrc = Path.Combine(overrides, "icon.png");
                    if (File.Exists(iconSrc))
                    {
                        try { File.Copy(iconSrc, Path.Combine(instDir, "icon.png"), overwrite: true); } catch { }
                    }

                    CopyDirectoryContents(overrides, gameDir, skipSubdirs: new[] { "mods", ".mixin.out", "immersive_paintings_cache", "particular_cache" },
                        skipFilePrefixes: new[] { "hs_err_pid" }, skipFileExtensions: new[] { ".log" });

                    var localMods = Path.Combine(overrides, "mods");
                    if (Directory.Exists(localMods))
                    {
                        foreach (var jar in Directory.EnumerateFiles(localMods, "*.jar"))
                            File.Copy(jar, Path.Combine(modsDir, Path.GetFileName(jar)), overwrite: true);
                    }
                }

                var allMods = Path.Combine(localSource, "all_mods");
                if (Directory.Exists(allMods))
                {
                    foreach (var jar in Directory.EnumerateFiles(allMods, "*.jar"))
                        File.Copy(jar, Path.Combine(modsDir, Path.GetFileName(jar)), overwrite: true);
                    foreach (var zip in Directory.EnumerateFiles(allMods, "*.zip"))
                        File.Copy(zip, Path.Combine(rpDir, Path.GetFileName(zip)), overwrite: true);
                }
            }, token);
        }

        CheckAndInjectLadsCore(baseDirectory, exp.Id);

        var instance = new ModpackInstance
        {
            Id = exp.Id,
            Name = exp.Title,
            McVersion = exp.McVersion,
            Loader = exp.Loader,
            LoaderVersion = exp.LoaderVersion,
            IconPath = "icon.png",
            CreatedUtc = DateTime.UtcNow,
            Author = "The Lads",
            PackVersion = "1.0.0",
            MaxRamMb = exp.RecommendedRamMb,
            Directory = instDir
        };
        Modpacks.Save(instance);

        status?.Invoke("Zero SMP instance ready.");
        return instance;
    }

    public static void CheckAndInjectLadsCore(string baseDirectory, string experienceId)
    {
        var gameDir = Path.Combine(InstanceDirectory(baseDirectory, experienceId), "minecraft", "mods");
        if (!Directory.Exists(gameDir)) return;

        var searchDirs = new[]
        {
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "game-mods"),
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "mods"),
            baseDirectory
        };

        foreach (var dir in searchDirs)
        {
            if (!Directory.Exists(dir)) continue;
            foreach (var file in Directory.EnumerateFiles(dir, "*TheLadsCore*1.20.1*.jar"))
            {
                var target = Path.Combine(gameDir, Path.GetFileName(file));
                try { File.Copy(file, target, overwrite: true); return; } catch { }
            }
        }
    }

    private static void CopyDirectoryContents(string source, string target, string[] skipSubdirs, string[] skipFilePrefixes, string[] skipFileExtensions)
    {
        Directory.CreateDirectory(target);
        foreach (var dir in Directory.EnumerateDirectories(source))
        {
            var name = Path.GetFileName(dir);
            if (skipSubdirs.Contains(name, StringComparer.OrdinalIgnoreCase)) continue;
            CopyDirectoryRecursive(dir, Path.Combine(target, name));
        }
        foreach (var file in Directory.EnumerateFiles(source))
        {
            var name = Path.GetFileName(file);
            if (name.Equals("icon.png", StringComparison.OrdinalIgnoreCase)) continue;
            if (skipFilePrefixes.Any(p => name.StartsWith(p, StringComparison.OrdinalIgnoreCase))) continue;
            if (skipFileExtensions.Any(e => name.EndsWith(e, StringComparison.OrdinalIgnoreCase))) continue;
            File.Copy(file, Path.Combine(target, name), overwrite: true);
        }
    }

    private static void CopyDirectoryRecursive(string source, string target)
    {
        Directory.CreateDirectory(target);
        foreach (var dir in Directory.EnumerateDirectories(source))
            CopyDirectoryRecursive(dir, Path.Combine(target, Path.GetFileName(dir)));
        foreach (var file in Directory.EnumerateFiles(source))
            File.Copy(file, Path.Combine(target, Path.GetFileName(file)), overwrite: true);
    }
}
