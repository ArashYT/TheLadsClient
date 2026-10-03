using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;

namespace TheLadsLauncher.Services;

/// <summary>A saved skin. Model is the Minecraft variant: "classic" or "slim".</summary>
public sealed record SavedSkin(string Id, string Name, string Model, DateTime AddedUtc);

/// <summary>A one-click outfit: a saved skin, the model to upload it with, and a cape (null = no cape).</summary>
public sealed record SkinCombo(string Id, string Name, string SkinId, string Model, string? CapeId, string? CapeName);

/// <summary>
/// The Skins tab library, local only: PNGs in &lt;data dir&gt;/skins/&lt;id&gt;.png, names and combos in &lt;data dir&gt;/skins.json.
/// Nothing here talks to Mojang; uploading happens only when the user clicks Apply.
/// </summary>
public sealed class SkinLibrary
{
    private sealed class Index
    {
        public List<SavedSkin> Skins { get; set; } = new();
        public List<SkinCombo> Combos { get; set; } = new();
    }

    private static readonly JsonSerializerOptions Json = new() { WriteIndented = true };
    private readonly string _folder, _indexFile;
    private readonly Index _index;

    public SkinLibrary(string baseDirectory)
    {
        _folder = Path.Combine(baseDirectory, "skins");
        _indexFile = Path.Combine(baseDirectory, "skins.json");
        _index = Load();
        // Ids name files: keep only ids this class could have made, and only skins whose PNG still exists.
        _index.Skins.RemoveAll(s => !Guid.TryParseExact(s.Id, "N", out _) || !File.Exists(PathOf(s)));
        _index.Combos.RemoveAll(c => _index.Skins.All(s => s.Id != c.SkinId));
        if (!File.Exists(_indexFile)) ImportPresets(Path.Combine(baseDirectory, "presets"));
    }

    public IReadOnlyList<SavedSkin> Skins => _index.Skins;
    public IReadOnlyList<SkinCombo> Combos => _index.Combos;
    public string PathOf(SavedSkin skin) => Path.Combine(_folder, skin.Id + ".png");
    public SavedSkin? Find(string id) => _index.Skins.FirstOrDefault(s => s.Id == id);

    public SavedSkin Add(byte[] png, string name, string model)
    {
        CheckSkinPng(png);
        var skin = new SavedSkin(Guid.NewGuid().ToString("N"), CleanName(name, "Skin"), NormalizeModel(model), DateTime.UtcNow);
        Directory.CreateDirectory(_folder);
        File.WriteAllBytes(PathOf(skin), png);
        _index.Skins.Add(skin);
        Save();
        return skin;
    }

    public SavedSkin Update(string id, string name, string model)
    {
        int at = _index.Skins.FindIndex(s => s.Id == id);
        if (at < 0) throw new InvalidOperationException("That skin is no longer in the library.");
        var skin = _index.Skins[at] with { Name = CleanName(name, _index.Skins[at].Name), Model = NormalizeModel(model) };
        _index.Skins[at] = skin;
        Save();
        return skin;
    }

    /// <summary>Removes the skin, its PNG and every combo that uses it.</summary>
    public void Remove(string id)
    {
        var skin = Find(id);
        if (skin == null) return;
        _index.Skins.Remove(skin);
        _index.Combos.RemoveAll(c => c.SkinId == id);
        Save();
        File.Delete(PathOf(skin));
    }

    public SkinCombo AddCombo(string name, SavedSkin skin, string model, string? capeId, string? capeName)
    {
        if (Find(skin.Id) == null) throw new InvalidOperationException("That skin is no longer in the library.");
        var combo = new SkinCombo(Guid.NewGuid().ToString("N"), CleanName(name, skin.Name), skin.Id, NormalizeModel(model),
            string.IsNullOrWhiteSpace(capeId) ? null : capeId, string.IsNullOrWhiteSpace(capeId) ? null : capeName);
        _index.Combos.Add(combo);
        Save();
        return combo;
    }

    public void RemoveCombo(string id)
    {
        if (_index.Combos.RemoveAll(c => c.Id == id) > 0) Save();
    }

    public static string NormalizeModel(string? model) => string.Equals(model, "slim", StringComparison.OrdinalIgnoreCase) ? "slim" : "classic";

    /// <summary>Minecraft accepts 64x64 and legacy 64x32 PNG skins. Reads the size from the PNG header; throws a user-facing message.</summary>
    public static void CheckSkinPng(byte[] png)
    {
        ReadOnlySpan<byte> signature = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];
        if (png.Length < 24 || !png.AsSpan(0, 8).SequenceEqual(signature) || png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R')
            throw new InvalidDataException("That file is not a PNG image.");
        int width = (png[16] << 24) | (png[17] << 16) | (png[18] << 8) | png[19];
        int height = (png[20] << 24) | (png[21] << 16) | (png[22] << 8) | png[23];
        if (width != 64 || height is not (64 or 32))
            throw new InvalidDataException($"A Minecraft skin is 64x64 (or legacy 64x32) pixels; this image is {width}x{height}.");
    }

    private static string CleanName(string? name, string fallback)
    {
        string clean = (name ?? "").Trim();
        if (clean.Length > 48) clean = clean[..48];
        return clean.Length == 0 ? fallback : clean;
    }

    private Index Load()
    {
        if (!File.Exists(_indexFile)) return new Index();
        try { return JsonSerializer.Deserialize<Index>(File.ReadAllText(_indexFile)) ?? new Index(); }
        catch (JsonException)
        {
            // Keep the unreadable file for the user instead of overwriting it on the next save.
            File.Copy(_indexFile, _indexFile + ".bad", overwrite: true);
            return new Index();
        }
    }

    private void Save()
    {
        string temp = _indexFile + ".tmp";
        File.WriteAllText(temp, JsonSerializer.Serialize(_index, Json));
        File.Move(temp, _indexFile, true);
    }

    // 1.5 and older kept editor presets in presets/<account>/*.png; show them in the library once.
    private void ImportPresets(string presets)
    {
        if (!Directory.Exists(presets)) return;
        foreach (string file in Directory.GetFiles(presets, "*.png", SearchOption.AllDirectories).Order())
        {
            try { Add(File.ReadAllBytes(file), Path.GetFileNameWithoutExtension(file), "classic"); }
            catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException) { }
        }
    }
}
