using System;
using System.Globalization;
using System.IO;
using System.Linq;

namespace TheLadsLauncher.Services;

/// <summary>
/// The user's own jars in a profile's Mods folder: adding, and replacing one with a newer download. Pack and LadsCore
/// files are never touched: a jar whose Fabric id the pack or LadsCore owns is refused (switch the pack mod instead),
/// nothing is ever overwritten, and a replaced jar is kept in <c>.lads-mod-cache\user-mod-backups\&lt;time&gt;\</c>.
/// </summary>
public static class UserModFiles
{
    public const string BackupFolder = "user-mod-backups";
    private const string DisabledSuffix = ".disabled";

    /// <summary>The pack or LadsCore entry that owns <paramref name="id"/> for this profile's Minecraft version, or null.
    /// Mods the pack ships only for other versions do not count: the user may add those.</summary>
    public static ModInventoryEntry? PackOwner(ModInventory inventory, string id) => inventory.Entries.FirstOrDefault(e => e.Id == id
        && (e.Ownership == ModOwnership.Core || (e.Ownership == ModOwnership.Pack && e.Status != ModEntryStatus.Unavailable)));

    /// <summary>Copies a jar into Mods under its own name. Refuses a pack/LadsCore mod, a mod that is already there under
    /// another name, and a name that is taken (enabled or disabled). Returns the new path.</summary>
    public static string Add(ModInventory inventory, string sourceJar)
    {
        var name = Path.GetFileName(sourceJar);
        if (!name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase))
            throw new InvalidOperationException($"'{name}' is not a .jar file.");
        var info = Read(sourceJar);
        RefusePackMod(inventory, info);
        var copy = inventory.Entries.FirstOrDefault(e => e.Id == info.Id && e.FilePath != null);
        if (copy != null)
            throw new InvalidOperationException($"{copy.DisplayName} ({info.Id}) is already in Mods as '{copy.FileName}'. " +
                "Delete that copy first, or use Update on its row.");
        var destination = FreeDestination(inventory, name, null);
        var temp = TempPath(inventory.GameDirectory);
        try
        {
            File.Copy(sourceJar, temp);
            File.Move(temp, destination);
        }
        finally
        {
            if (File.Exists(temp)) File.Delete(temp);
        }
        return destination;
    }

    /// <summary>
    /// Puts a downloaded jar in place: it replaces your copy of the same Fabric id (read from the download; the old file
    /// goes to a backup and the disabled state is kept) or is added when there is none. <paramref name="expectedId"/>, when
    /// given, must match the download. The downloaded file is moved, never copied. Returns the new path.
    /// </summary>
    public static string Install(ModInventory inventory, string downloadedJar, string fileName, string? expectedId = null)
    {
        var info = Read(downloadedJar);
        if (expectedId != null && info.Id != expectedId)
            throw new InvalidDataException($"The download is the Fabric mod '{info.Id}', not '{expectedId}'. Nothing was changed.");
        RefusePackMod(inventory, info);
        var name = fileName;
        if (!ClientModInstaller.ValidFileName(name))
        {
            // The jar's own version string is untrusted text: keep only file-name-safe characters.
            var version = new string((info.Version ?? "download").Select(c => char.IsAsciiLetterOrDigit(c) || c is '.' or '+' or '-' or '_' ? c : '_').ToArray()).Replace("..", "_");
            name = info.Id + "-" + (version.Length is > 0 and <= 64 ? version : "download") + ".jar";
            if (!ClientModInstaller.ValidFileName(name))
                throw new InvalidDataException($"No safe file name could be made for {info.Id}. Nothing was changed.");
        }
        var copies = inventory.Entries.Where(e => e.Id == info.Id && e.FilePath != null).ToList();
        if (copies.Any(c => c.Ownership != ModOwnership.User))
            throw new InvalidOperationException($"'{copies.First(c => c.Ownership != ModOwnership.User).FileName}' holds {info.Id} and is not a mod you added. It was left alone.");
        if (copies.Count > 1)
            throw new InvalidOperationException($"Mods holds {copies.Count} copies of {info.Id} ({string.Join(", ", copies.Select(c => c.FileName))}). Delete the extra copies first.");
        var old = copies.SingleOrDefault();
        if (old == null)
        {
            var destination = FreeDestination(inventory, name, null);
            File.Move(downloadedJar, destination);
            return destination;
        }

        var oldPath = old.FilePath!;
        var stamp = DateTime.UtcNow.ToString("yyyyMMdd-HHmmss", CultureInfo.InvariantCulture);
        var backupDirectory = Path.Combine(inventory.GameDirectory, ".lads-mod-cache", BackupFolder, stamp);
        Directory.CreateDirectory(backupDirectory);
        var backup = Path.Combine(backupDirectory, Path.GetFileName(oldPath));
        if (File.Exists(backup)) backup = Path.Combine(backupDirectory, Guid.NewGuid().ToString("N") + "-" + Path.GetFileName(oldPath));
        var target = FreeDestination(inventory, old.EnabledOnDisk ? name : name + DisabledSuffix, oldPath);
        File.Move(oldPath, backup);
        try
        {
            File.Move(downloadedJar, target);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            try { File.Move(backup, oldPath); }
            catch (Exception restore) when (restore is IOException or UnauthorizedAccessException)
            {
                throw new IOException($"{e.Message} Your previous copy is in '{backup}'; move it back into Mods ({restore.Message}).", e);
            }
            throw;
        }
        return target;
    }

    private static FabricModInfo Read(string jar)
    {
        FabricModInfo? info;
        try { info = FabricModMetadata.ReadJar(jar); }
        catch (InvalidDataException e) { throw new InvalidDataException($"'{Path.GetFileName(jar)}' is not a readable Fabric mod ({e.Message}).", e); }
        return info ?? throw new InvalidDataException($"'{Path.GetFileName(jar)}' is not a Fabric mod (it has no fabric.mod.json).");
    }

    private static void RefusePackMod(ModInventory inventory, FabricModInfo info)
    {
        if (PackOwner(inventory, info.Id) is { } owner)
            throw new InvalidOperationException($"{owner.DisplayName} ({info.Id}) is part of the Lads pack for Minecraft {inventory.MinecraftVersion}. " +
                "Switch the pack mod on or off on the Mods page instead of adding another copy.");
    }

    // Never overwrites: the name and its enabled/disabled twin must be free (except the file being replaced), and a pack
    // mod's reserved file name (downloaded at the next launch) is not taken either.
    private static string FreeDestination(ModInventory inventory, string name, string? replacing)
    {
        var mods = Path.GetFullPath(Path.Combine(inventory.GameDirectory, "mods"));
        if (!SafeFileOps.PathsEqual(Path.GetDirectoryName(Path.GetFullPath(Path.Combine(mods, name)))!, mods))
            throw new InvalidDataException($"'{name}' is not a plain file name inside Mods. Nothing was changed.");
        Directory.CreateDirectory(mods);
        var enabledName = name.EndsWith(DisabledSuffix, StringComparison.OrdinalIgnoreCase) ? name[..^DisabledSuffix.Length] : name;
        foreach (var candidate in new[] { enabledName, enabledName + DisabledSuffix })
        {
            var path = Path.Combine(mods, candidate);
            if ((File.Exists(path) || Directory.Exists(path)) && !(replacing != null && SafeFileOps.PathsEqual(path, replacing)))
                throw new IOException($"'{candidate}' already exists in Mods. Nothing was overwritten.");
        }
        var reserved = inventory.Entries.FirstOrDefault(e => e.Ownership == ModOwnership.Pack && e.Status != ModEntryStatus.Unavailable
            && string.Equals(e.FileName, enabledName, StringComparison.OrdinalIgnoreCase));
        if (reserved != null)
            throw new IOException($"'{enabledName}' is the file name of the pack mod {reserved.DisplayName}. Nothing was changed.");
        return Path.Combine(mods, name);
    }

    private static string TempPath(string gameDirectory)
    {
        var cache = Path.Combine(gameDirectory, ".lads-mod-cache");
        Directory.CreateDirectory(cache);
        return Path.Combine(cache, "incoming-" + Guid.NewGuid().ToString("N") + ".tmp");
    }
}
