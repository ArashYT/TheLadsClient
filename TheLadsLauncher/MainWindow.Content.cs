using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Media;
using Avalonia.VisualTree;
using TheLadsLauncher.Services;

namespace TheLadsLauncher;

// Browse Mods / Resource Packs search, the Update resource packs button, and the sandbox-only --preview-content capture.
public partial class MainWindow
{
    private ContentCatalog Catalog() => new(_httpClient, settings.ModrinthApiUrl, settings.CurseForgeApiUrl, settings.CurseForgeApiKey);

    /// <summary>Searches the tab's source for its Minecraft version; a failure (no CurseForge key, offline, refused) is shown in the list.</summary>
    private async Task SearchBrowseAsync(TextBox queryBox, ComboBox providerBox, ComboBox versionBox, bool isResourcePack, StackPanel list)
    {
        string provider = (providerBox.SelectedItem as ComboBoxItem)?.Content as string ?? "Modrinth";
        string mcVersion = versionBox.SelectedItem as string ?? ResolveMinecraftVersion();
        string loader = ContentCatalog.ModLoader(mcVersion);
        string? sort = null;
        string? category = null;

        if (!isResourcePack)
        {
            if (ModLoaderBox?.SelectedItem is ComboBoxItem item && item.Content as string is { } l && l != "All loaders")
                loader = l.ToLowerInvariant();
            sort = (ModSortBox?.SelectedItem as ComboBoxItem)?.Content as string;
            category = ModCategoryBox?.SelectedItem as string;
        }
        else
        {
            sort = (RpSortBox?.SelectedItem as ComboBoxItem)?.Content as string;
            category = RpCategoryBox?.SelectedItem as string;
        }
        if (category == "All categories") category = null;

        try
        {
            var results = await Catalog().SearchAsync(provider, isResourcePack ? ContentKind.ResourcePack : ContentKind.Mod, queryBox.Text ?? "", mcVersion,
                loader, category, sort);
            RenderSearchResults(results, mcVersion, isResourcePack, list);
        }
        catch (Exception ex) // an async void handler: never take the launcher down over a search
        {
            Log($"[Search Error] {provider}: {ex.Message}");
            ShowSearchMessage(list, ex.Message);
        }
    }

    private static void ShowSearchMessage(StackPanel list, string text)
    {
        list.Children.Clear();
        list.Children.Add(new TextBlock { Text = text, TextWrapping = TextWrapping.Wrap, MaxWidth = 560, Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")),
            HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Center, Margin = new Thickness(0, 20, 0, 0) });
    }

    // A new source, version, loader, sort or category searches again
    private void BrowseFilter_Changed(object? sender, SelectionChangedEventArgs e)
    {
        if (ModsPage?.IsVisible != true) return; // startup fills both lists itself
        if (sender == ModSearchProvider || sender == SearchModMcVersionDropdown || sender == ModLoaderBox || sender == ModSortBox || sender == ModCategoryBox)
            _ = SearchBrowseAsync(ModSearchBox, ModSearchProvider, SearchModMcVersionDropdown, isResourcePack: false, BrowseModsList);
        else
            _ = SearchBrowseAsync(RpSearchBox, RpSearchProvider, SearchRpMcVersionDropdown, isResourcePack: true, BrowseRpList);
    }

    /// <summary>The shared resource packs folder (every Lads version) for the Minecraft version chosen on the Resource Packs tab.</summary>
    private ContentTarget SharedPacksTarget(string mcVersion)
    {
        var shared = SharedContentService.Instance;
        return PackContentService.LoadTargets(ProfileService.Instance.GetProfiles(), PathService.Instance, shared)
            .First(t => SafeFileOps.PathsEqual(t.ResourcePacks, shared.ResourcePacksDirectory)) with { MinecraftVersion = mcVersion };
    }

    private async void UpdateResourcePacks_Click(object? sender, RoutedEventArgs e) => await UpdateResourcePacksAsync();

    private async Task UpdateResourcePacksAsync()
    {
        string mcVersion = SearchRpMcVersionDropdown.SelectedItem as string ?? ResolveMinecraftVersion();
        // A running game holds its packs open and writes its own resourcePacks list back into options.txt when it closes.
        if (ProfileService.Instance.GetProfiles().Any(p => IsGameRunningFor(PathService.Instance.GetProfileDirectory(p))))
        {
            ModsStatus("Close Minecraft first: a running game keeps its resource packs open. Nothing was changed.", true);
            return;
        }
        UpdateResourcePacksBtn.IsEnabled = false;
        try
        {
            var (updated, failed, lines) = await PackContentService.UpdateResourcePacksAsync(Catalog(), SharedPacksTarget(mcVersion), SharedContentService.Instance,
                path => SafeFileOps.DeleteToRecycleBin(path), new Progress<string>(message => ModsStatus(message)));
            foreach (var line in lines) Log($"[Resource packs] {line}");
            ModsStatus($"Resource packs for Minecraft {mcVersion}: {updated} updated{(failed > 0 ? $", {failed} failed" : "")}. {string.Join(" ", lines)}", failed > 0);
        }
        catch (Exception ex) when (ex is ContentSourceException or IOException or UnauthorizedAccessException)
        {
            ModsStatus($"Resource packs were not updated: {ex.Message}", true);
        }
        finally { UpdateResourcePacksBtn.IsEnabled = true; }
    }

    /// <summary>
    /// --preview-content &lt;outputDir&gt; (sandbox only; Program.Main refuses it otherwise): CurseForge search without a key, a real
    /// Modrinth resource pack update (Faithful 32x for 26.2, Update for 26.3, then for 1.8.9: kept while another profile plays it), shader browsing and installs for the
    /// focus versions (1.8.9, 26.2, 26.3) and a 26.2 modpack instance, and data pack installs into a world of each. Writes
    /// screenshots and preview-content.json, then exits.
    /// </summary>
    private async Task RunContentPreviewAsync(string output)
    {
        int exitCode = 0;
        var result = new JsonObject();
        var focus = new[] { "1.8.9", "26.2", "26.3" };
        async Task<bool> Until(Func<bool> done, int ms = 30000)
        {
            var clock = Stopwatch.StartNew();
            while (!done() && clock.ElapsedMilliseconds < ms) await Task.Delay(200);
            return done();
        }
        async Task Shot(string name) { await Task.Delay(700); SaveWindowScreenshot(Path.Combine(output, name)); }
        static string ListText(StackPanel list) => string.Join(" | ", list.GetVisualDescendants().OfType<TextBlock>().Select(t => t.Text).Where(t => !string.IsNullOrEmpty(t)).Take(12));
        static JsonArray Names(string folder) => new(PackContentService.Installed(folder).Select(p => (JsonNode)Path.GetFileName(p)!).ToArray());
        // Searches, waits until the first result is the expected project (not the popular list still showing), and installs it.
        async Task<string?> Install(Views.PackBrowserView view, string query, string first)
        {
            view.SearchBox.Text = query;
            await Task.Delay(700); // past the 450 ms search-as-you-type delay: the search for the query is running
            await Until(() => view.ResultsList.GetVisualDescendants().OfType<TextBlock>().FirstOrDefault()?.Text?.StartsWith(first) == true
                && view.Status.Text?.Contains(" on Modrinth") == true);
            view.ResultsList.GetVisualDescendants().OfType<Button>().First().RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            await Until(() => view.Status.Text?.StartsWith("Installed") == true || view.Status.Text?.Contains(':') == true, 60000);
            return view.Status.Text;
        }
        try
        {
            Directory.CreateDirectory(output);
            var shared = SharedContentService.Instance;
            var paths = PathService.Instance;
            // Sandbox fixtures: a world in the shared saves, and a modpack instance (instances convention) with its own world.
            Directory.CreateDirectory(Path.Combine(shared.SavesDirectory, "QA World"));
            File.WriteAllBytes(Path.Combine(shared.SavesDirectory, "QA World", "level.dat"), Array.Empty<byte>());
            var instance = Path.Combine(paths.BaseDirectory, "instances", "qa-pack");
            Directory.CreateDirectory(Path.Combine(instance, "minecraft", "saves", "Pack World"));
            File.WriteAllBytes(Path.Combine(instance, "minecraft", "saves", "Pack World", "level.dat"), Array.Empty<byte>());
            File.WriteAllText(Path.Combine(instance, "instance.json"), JsonSerializer.Serialize(new { id = "qa-pack", name = "QA Pack", mcVersion = "26.2", loader = "fabric", loaderVersion = "0.17.2", createdUtc = DateTime.UtcNow }));

            // 1. CurseForge search on the Mods and Resource Packs tabs (without a key: the reason, not "No results").
            result["curseForgeKeySet"] = Catalog().HasCurseForgeKey;
            NavigateTo("Mods");
            ModsSubTabControl.SelectedItem = ModsBrowseTab;
            ModSearchBox.Text = "sodium";
            ModSearchProvider.SelectedIndex = 1;
            await Until(() => BrowseModsList.Children.Count > 0 && !ListText(BrowseModsList).Contains("Sodium") || ListText(BrowseModsList).Contains("CurseForge"));
            await Shot("mods-curseforge.png");
            result["modsCurseForge"] = ListText(BrowseModsList);
            NavigateTo("Packs");
            RpSearchProvider.SelectedIndex = 1;
            await Until(() => BrowseRpList.Children.Count > 0);
            await Shot("resourcepacks-curseforge.png");
            result["resourcePacksCurseForge"] = ListText(BrowseRpList);
            RpSearchProvider.SelectedIndex = 0;

            // 2. Update resource packs: Faithful 32x for 26.2, enabled in every options.txt (1.8.9's without "file/"), Update for 26.3, then for 1.8.9:
            // a release that does not support a version another Lads profile plays leaves the pack as it is.
            var catalog = Catalog();
            var old = await catalog.LatestFileAsync(new ModSearchItem { Id = "w0TnApzs", Provider = "Modrinth" }, ContentKind.ResourcePack, "26.2", "fabric")
                ?? throw new InvalidOperationException("Modrinth lists no Faithful 32x for 26.2.");
            var oldName = Path.GetFileName(await catalog.InstallAsync(old, shared.ResourcePacksDirectory, shared));
            var legacyOptions = ProfileService.Instance.GetProfiles().Where(p => GameVersionPolicy.UsesForge(p.MinecraftVersion))
                .Select(p => Path.Combine(paths.GetProfileDirectory(p), "options.txt")).ToList();
            var options = SharedPacksTarget("26.3").OptionsFiles;
            foreach (var file in options)
            {
                Directory.CreateDirectory(Path.GetDirectoryName(file)!);
                File.WriteAllText(file, legacyOptions.Contains(file, StringComparer.OrdinalIgnoreCase) ? $"resourcePacks:[\"{oldName}\"]\nlang:en_US\n"
                    : $"version:4554\nresourcePacks:[\"vanilla\",\"file/{oldName}\"]\nincompatibleResourcePacks:[]\nlang:en_us\n");
            }
            var updates = new JsonArray();
            foreach (var version in new[] { "26.3", "1.8.9" })
            {
                SearchRpMcVersionDropdown.SelectedItem = version;
                await UpdateResourcePacksAsync();
                updates.Add(new JsonObject { ["version"] = version, ["status"] = ModsStatusText.Text, ["packs"] = Names(shared.ResourcePacksDirectory),
                    ["options"] = new JsonArray(options.Select(f => (JsonNode)$"{f}: {File.ReadAllLines(f).First(l => l.StartsWith("resourcePacks:"))}").ToArray()) });
                await Shot($"resourcepacks-update-{version}.png");
            }
            result["resourcePackUpdates"] = updates;

            // 3. Shader packs: the filter of each focus target (Iris on Fabric, OptiFine on 1.8.9), then installs for 26.3, 1.8.9 and the modpack.
            ModsSubTabControl.SelectedItem = ShaderPacksTab;
            await Until(() => ShaderPacksView.TargetBox.ItemCount > 0);
            var targets = ShaderPacksView.TargetBox.Items.OfType<ContentTarget>().Where(t => focus.Contains(t.MinecraftVersion)).ToList();
            var shaders = new JsonArray();
            foreach (var target in targets)
            {
                ShaderPacksView.TargetBox.SelectedItem = target;
                await Until(() => ShaderPacksView.Status.Text?.Contains(" on Modrinth") == true || ShaderPacksView.Status.Text?.Contains("answered") == true);
                shaders.Add(new JsonObject { ["target"] = target.Label, ["folder"] = ShaderPacksView.Folder, ["status"] = ShaderPacksView.Status.Text, ["first"] = ListText(ShaderPacksView.ResultsList) });
                await Shot($"shaders-{target.MinecraftVersion}{(target.Label.StartsWith("Modpack") ? "-modpack" : "")}.png");
            }
            result["shaders"] = shaders;
            var shaderInstalls = new JsonArray();
            foreach (var (version, query, first, modpack) in new[] { ("26.3", "complementary reimagined", "Complementary Shaders - Reimagined", false),
                         ("1.8.9", "bsl", "BSL Shaders", false), ("26.2", "complementary reimagined", "Complementary Shaders - Reimagined", true) })
            {
                ShaderPacksView.TargetBox.SelectedItem = targets.First(t => t.MinecraftVersion == version && t.Label.StartsWith("Modpack") == modpack);
                var status = await Install(ShaderPacksView, query, first);
                shaderInstalls.Add(new JsonObject { ["target"] = ShaderPacksView.TargetBox.SelectedItem?.ToString(), ["status"] = status, ["installed"] = Names(ShaderPacksView.Folder!) });
                await Shot($"shaders-installed-{version}{(modpack ? "-modpack" : "")}.png");
                ShaderPacksView.SearchBox.Text = "";
            }
            result["shaderInstalls"] = shaderInstalls;

            // 4. Data packs: no 1.8.9 target (data packs need 1.13+); install into the shared QA World (26.3), then the 26.2 modpack's world.
            ModsSubTabControl.SelectedItem = DataPacksTab;
            await Until(() => DataPacksView.TargetBox.ItemCount > 0);
            result["dataPackTargets"] = new JsonArray(DataPacksView.TargetBox.Items.OfType<ContentTarget>().Select(t => (JsonNode)t.Label).ToArray());
            var datapackLines = new JsonArray();
            foreach (var target in DataPacksView.TargetBox.Items.OfType<ContentTarget>()
                         .Where(t => t.Label.StartsWith("Modpack") ? t.MinecraftVersion == "26.2" : t.MinecraftVersion == "26.3").ToList())
            {
                DataPacksView.TargetBox.SelectedItem = target;
                var status = await Install(DataPacksView, "veinminer", "VeinMiner");
                datapackLines.Add(new JsonObject { ["target"] = target.Label, ["world"] = DataPacksView.WorldBox.SelectedItem as string, ["status"] = status,
                    ["installed"] = Names(DataPacksView.Folder!) });
                await Shot($"datapacks-{(target.Label.StartsWith("Modpack") ? "modpack" : "lads")}.png");
                DataPacksView.SearchBox.Text = "";
            }
            result["dataPacks"] = datapackLines;
        }
        catch (Exception e) { exitCode = 1; result["error"] = e.ToString(); }
        File.WriteAllText(Path.Combine(output, "preview-content.json"), result.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
        Environment.ExitCode = exitCode;
        Close();
    }
}
