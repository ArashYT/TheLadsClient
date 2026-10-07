using System;
using System.IO;
using System.Threading.Tasks;
using Avalonia.Interactivity;

namespace TheLadsLauncher;

/// <summary>
/// --preview-page &lt;Page&gt; &lt;outputDir&gt;: sandbox-only QA capture of one page (Program.Main refuses it unless THELADS_DIR and
/// LADS_GLOBAL_MINECRAFT_DIR point at sandbox folders). Opens the page through its sidebar handler, waits, saves
/// &lt;outputDir&gt;/&lt;page&gt;.png and closes. LADS_PREVIEW_SIZE=1280x720 resizes first; LADS_PREVIEW_DELAY_MS sets the wait
/// (default 1500). Pages: Home, Accounts, Skins, Profiles, Modpacks, Worlds, Servers, Mods, Packs, Gallery, Files, Settings, Logs.
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
        Close();
    }
}
