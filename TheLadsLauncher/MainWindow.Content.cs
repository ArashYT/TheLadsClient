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
        try
        {
            var results = await Catalog().SearchAsync(provider, isResourcePack ? ContentKind.ResourcePack : ContentKind.Mod, queryBox.Text ?? "", mcVersion,
                ContentCatalog.ModLoader(mcVersion));
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

    // A new source or version searches again; before, switching to CurseForge did nothing until you typed.
    private void BrowseFilter_Changed(object? sender, SelectionChangedEventArgs e)
    {
        if (ModsPage?.IsVisible != true) return; // startup fills both lists itself
        if (sender == ModSearchProvider || sender == SearchModMcVersionDropdown)
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
    /// Modrinth resource pack update (an old Faithful 32x is installed first), shader browsing for every Lads version and a modpack
    /// instance, and a data pack install into a world. Writes screenshots and preview-content.json, then exits.
    /// </summary>
    private async Task RunContentPreviewAsync(string output)
    {
        int exitCode = 0;
        var result = new JsonObject();
        async Task<bool> Until(Func<bool> done, int ms = 30000)
        {
            var clock = Stopwatch.StartNew();
            while (!done() && clock.ElapsedMilliseconds < ms) await Task.Delay(200);
            return done();
        }
        async Task Shot(string name) { await Task.Delay(700); SaveWindowScreenshot(Path.Combine(output, name)); }
        static string ListText(StackPanel list) => string.Join(" | ", list.GetVisualDescendants().OfType<TextBlock>().Select(t => t.Text).Where(t => !string.IsNullOrEmpty(t)).Take(12));
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
            File.WriteAllText(Path.Combine(instance, "instance.json"), JsonSerializer.Serialize(new { id = "qa-pack", name = "QA Pack", mcVersion = "1.21.1", loader = "fabric", loaderVersion = "0.16.14", createdUtc = DateTime.UtcNow }));

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

            // 2. Update resource packs: Faithful 32x for 1.20.1, enabled in every options.txt, updated for 1.21.1.
            var catalog = Catalog();
            var old = await catalog.LatestFileAsync(new ModSearchItem { Id = "w0TnApzs", Provider = "Modrinth" }, ContentKind.ResourcePack, "1.20.1", "fabric")
                ?? throw new InvalidOperationException("Modrinth lists no Faithful 32x for 1.20.1.");
            var oldPath = await catalog.InstallAsync(old, shared.ResourcePacksDirectory, shared);
            var options = SharedPacksTarget("1.21.1").OptionsFiles;
            foreach (var file in options)
            {
                Directory.CreateDirectory(Path.GetDirectoryName(file)!);
                File.WriteAllText(file, $"version:3955\nresourcePacks:[\"vanilla\",\"file/{Path.GetFileName(oldPath)}\"]\nincompatibleResourcePacks:[]\nlang:en_us\n");
            }
            SearchRpMcVersionDropdown.SelectedItem = "1.21.1";
            await Until(() => BrowseRpList.Children.Count > 0);
            await UpdateResourcePacksAsync();
            result["updateStatus"] = ModsStatusText.Text;
            result["resourcePacksAfter"] = new JsonArray(PackContentService.Installed(shared.ResourcePacksDirectory).Select(p => (JsonNode)Path.GetFileName(p)!).ToArray());
            result["optionsAfter"] = new JsonArray(options.Select(f => (JsonNode)$"{f}: {File.ReadAllLines(f)[1]}").ToArray());
            await Shot("resourcepacks-update.png");

            // 3. Shader packs: the filter of every target (Iris on Fabric, OptiFine on 1.8.9), then an install.
            ModsSubTabControl.SelectedItem = ShaderPacksTab;
            await Until(() => ShaderPacksView.TargetBox.ItemCount > 0);
            var shaders = new JsonArray();
            foreach (var target in ShaderPacksView.TargetBox.Items.OfType<ContentTarget>().ToList())
            {
                ShaderPacksView.TargetBox.SelectedItem = target;
                await Until(() => ShaderPacksView.Status.Text?.Contains(" on Modrinth") == true || ShaderPacksView.Status.Text?.Contains("answered") == true);
                shaders.Add(new JsonObject { ["target"] = target.Label, ["folder"] = ShaderPacksView.Folder, ["status"] = ShaderPacksView.Status.Text, ["first"] = ListText(ShaderPacksView.ResultsList) });
                await Shot($"shaders-{target.MinecraftVersion}{(target.Label.StartsWith("Modpack") ? "-modpack" : "")}.png");
            }
            result["shaders"] = shaders;
            ShaderPacksView.TargetBox.SelectedItem = ShaderPacksView.TargetBox.Items.OfType<ContentTarget>().First(t => t.MinecraftVersion == "1.21.11");
            ShaderPacksView.SearchBox.Text = "complementary reimagined";
            await Until(() => ListText(ShaderPacksView.ResultsList).Contains("Complementary"));
            ShaderPacksView.ResultsList.GetVisualDescendants().OfType<Button>().First().RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            await Until(() => ShaderPacksView.Status.Text?.StartsWith("Installed") == true || ShaderPacksView.Status.Text?.Contains(':') == true, 60000);
            result["shaderInstall"] = ShaderPacksView.Status.Text;
            result["shaderPacksAfter"] = new JsonArray(PackContentService.Installed(shared.ShaderPacksDirectory).Select(p => (JsonNode)Path.GetFileName(p)!).ToArray());
            await Shot("shaders-installed.png");

            // 4. Data packs: no 1.8.9 target; install into the shared QA World (1.21.1), then the modpack's world.
            ModsSubTabControl.SelectedItem = DataPacksTab;
            await Until(() => DataPacksView.TargetBox.ItemCount > 0);
            result["dataPackTargets"] = new JsonArray(DataPacksView.TargetBox.Items.OfType<ContentTarget>().Select(t => (JsonNode)t.Label).ToArray());
            var datapackLines = new JsonArray();
            foreach (var target in DataPacksView.TargetBox.Items.OfType<ContentTarget>().Where(t => t.MinecraftVersion == "1.21.1").ToList())
            {
                DataPacksView.TargetBox.SelectedItem = target;
                DataPacksView.SearchBox.Text = "veinminer";
                await Until(() => ListText(DataPacksView.ResultsList).Contains("VeinMiner"));
                DataPacksView.ResultsList.GetVisualDescendants().OfType<Button>().First().RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
                await Until(() => DataPacksView.Status.Text?.StartsWith("Installed") == true || DataPacksView.Status.Text?.Contains(':') == true, 60000);
                datapackLines.Add(new JsonObject { ["target"] = target.Label, ["world"] = DataPacksView.WorldBox.SelectedItem as string, ["status"] = DataPacksView.Status.Text,
                    ["installed"] = new JsonArray(PackContentService.Installed(DataPacksView.Folder!).Select(p => (JsonNode)Path.GetFileName(p)!).ToArray()) });
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
