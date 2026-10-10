using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls.Primitives;
using Avalonia.Interactivity;
using Avalonia.Media.Imaging;
using Avalonia.VisualTree;

namespace TheLadsLauncher;

/// <summary>
/// --preview-page &lt;Page&gt; &lt;outputDir&gt;: sandbox-only QA capture of one page (Program.Main refuses it unless THELADS_DIR and
/// LADS_GLOBAL_MINECRAFT_DIR point at sandbox folders). Opens the page through its sidebar handler, waits, saves
/// &lt;outputDir&gt;/&lt;page&gt;.png and closes. LADS_PREVIEW_SIZE=1280x720 resizes first; LADS_PREVIEW_DELAY_MS sets the wait
/// (default 1500). Pages: Home, PlayPicker (Home with the Play profile list open), Experiences (Home's Experiences tab), Accounts, Skins, Profiles, Modpacks, Worlds, Servers, Mods, Packs, Gallery, Files, Settings, Logs.
/// </summary>
public partial class MainWindow
{
    private async Task RunPagePreviewAsync(string page, string output)
    {
        Directory.CreateDirectory(output);
        if (Environment.GetEnvironmentVariable("LADS_PREVIEW_SIZE") is { Length: > 0 } size && size.Split('x') is [var w, var h]
            && double.TryParse(w, out double width) && double.TryParse(h, out double height))
        {
            Width = width;
            Height = height;
        }
        int delay = int.TryParse(Environment.GetEnvironmentVariable("LADS_PREVIEW_DELAY_MS"), out int ms) ? ms : 1500;
        var none = new RoutedEventArgs();
        switch (page)
        {
            case "Home": NavHome_Click(null, none); break;
            case "PlayPicker": NavHome_Click(null, none); LaunchProfileSelector.IsDropDownOpen = true; break;
            case "Experiences": NavHome_Click(null, none); PlaySubTabs.SelectedItem = PlayExperiencesTab; break;
            case "Accounts": NavAccounts_Click(null, none); break;
            case "Skins": NavSkins_Click(null, none); break;
            case "Profiles": NavProfiles_Click(null, none); break;
            case "Modpacks": NavModpacks_Click(null, none); break;
            case "Worlds": NavWorlds_Click(null, none); break;
            case "Servers": NavServers_Click(null, none); break;
            case "Mods": NavMods_Click(null, none); break;
            case "Packs": NavPacks_Click(null, none); break;
            case "Gallery": NavGallery_Click(null, none); break;
            case "Files": NavFiles_Click(null, none); break;
            case "Settings": NavSettings_Click(null, none); break;
            case "Logs": NavLogs_Click(null, none); break;
            default: Log($"[Preview] Unknown page '{page}'."); break;
        }
        await Task.Delay(delay);
        SaveWindowScreenshot(Path.Combine(output, page.ToLowerInvariant() + ".png"));
        if (page == "PlayPicker") SavePlayPickerListScreenshot(output);
        Close();
    }

    // The window capture leaves out the open picker: its list is a popup. Render that list on its own.
    private void SavePlayPickerListScreenshot(string output)
    {
        if (LaunchProfileSelector.GetVisualDescendants().OfType<Popup>().FirstOrDefault()?.Child is not Visual list || list.Bounds.Width <= 0)
        {
            Log("[Preview] Play picker list not found.");
            return;
        }
        using var bitmap = new RenderTargetBitmap(new PixelSize((int)list.Bounds.Width, (int)list.Bounds.Height), new Vector(96, 96));
        bitmap.Render(list);
        bitmap.Save(Path.Combine(output, "playpicker-list.png"));
    }
}
