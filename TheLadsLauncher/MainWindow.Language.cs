using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.LogicalTree;
using Avalonia.VisualTree;
using TheLadsLauncher.Services;

namespace TheLadsLauncher;

// Settings → General → Language (LauncherTranslator translates the whole launcher) and the --preview-language QA capture.
public partial class MainWindow
{
    /// <summary>Fills the picker and applies the saved language. Called after LoadSettingsUI, before auto-save is hooked.</summary>
    private void InitializeLanguageSetting()
    {
        LauncherTranslator.Log = Log;
        LanguageSelector.ItemsSource = LauncherTranslator.Languages.Select(l => l.Name).ToArray();
        LanguageSelector.SelectedIndex = Math.Max(0, Array.FindIndex(LauncherTranslator.Languages, l => l.Code == settings.Language));
        LauncherTranslator.SetLanguage(settings.Language);
        LanguageSelector.SelectionChanged += (_, _) =>
        {
            settings.Language = LauncherTranslator.Languages[Math.Max(0, LanguageSelector.SelectedIndex)].Code;
            LauncherTranslator.SetLanguage(settings.Language); // live, no restart
            ScheduleSettingsSave();
        };
    }

    /// <summary>
    /// --preview-language &lt;outputDir&gt; [codes] (sandbox only; Program.Main refuses it otherwise): the main pages in English,
    /// in each language (default es,de,ja) picked in Settings like a player would, and in English again. language-report.json
    /// lists per language "notranslate" text that changed (must be none) and text still in English; back in English, text
    /// that is not its original (must be none).
    /// </summary>
    private async Task RunLanguagePreviewAsync(string output, string[] codes)
    {
        Directory.CreateDirectory(output);
        var report = new JsonObject();
        int exitCode = 0;
        try
        {
            Width = DEFAULT_WIDTH; Height = DEFAULT_HEIGHT;
            await WorldsPage.LoadAsync();
            await ServersPage.LoadAsync();
            await ReloadModsInventoryAsync();
            await ModpacksPage.LoadAsync();
            ShowSkins(); // the library with its skin names
            LoadFiles(settings.InstancePath);
            await LoadGalleryAsync();
            var english = new Dictionary<TextBlock, string?>();
            var runs = codes.Where(c => c != "en").Prepend("en").Append("en").ToArray();
            for (int run = 0; run < runs.Length; run++)
            {
                string code = runs[run];
                NavigateTo("Settings");
                LanguageSelector.SelectedIndex = Array.FindIndex(LauncherTranslator.Languages, l => l.Code == code);
                var privateChanged = new SortedSet<string>(StringComparer.Ordinal);
                var stillEnglish = new SortedSet<string>(StringComparer.Ordinal);
                var notRestored = new SortedSet<string>(StringComparer.Ordinal);
                int changed = 0;
                foreach (var page in new[] { "Settings", "Home", "Accounts", "Skins", "Profiles", "Mods", "Worlds", "Servers", "Modpacks", "Gallery", "Files" })
                {
                    NavigateTo(page);
                    await Task.Delay(300); // newly shown rows ask for their text first
                    for (int i = 0; i < 150 && LauncherTranslator.Busy; i++) await Task.Delay(200);
                    await Task.Delay(300);
                    SaveWindowScreenshot(Path.Combine(output, $"{(run == 0 ? "en" : run == runs.Length - 1 ? "en-again" : code)}-{page.ToLowerInvariant()}.png"));
                    // The picker shows the chosen language's own name: that change is the point.
                    foreach (var (block, text, isPrivate) in Shown().Where(t => t.Block.IsEffectivelyVisible && !LanguageSelector.IsVisualAncestorOf(t.Block)))
                    {
                        if (run == 0) { english.TryAdd(block, text); continue; }
                        if (!english.TryGetValue(block, out var was)) continue; // rebuilt since: not comparable
                        if (was != text)
                        {
                            changed++;
                            if (isPrivate) privateChanged.Add($"{was} -> {text}");
                            else if (code == "en") notRestored.Add($"{was} -> {text}"); // live values (CPU, RAM) differ too; Japanese must not
                        }
                        else if (!isPrivate && code != "en" && was != null && Regex.IsMatch(was, @"\p{L}{2}")) stillEnglish.Add(was);
                    }
                }
                if (run == 0) continue;
                if (code != "en") // formatted text (Runs) in another window
                {
                    var notes = new Views.ReleaseNotesWindow(ReleaseNotes.Read(Path.Combine(AppContext.BaseDirectory, "release-notes")), Program.Version);
                    notes.Details.IsExpanded = true;
                    notes.Show(this);
                    await Task.Delay(300);
                    for (int i = 0; i < 150 && LauncherTranslator.Busy; i++) await Task.Delay(200);
                    await Task.Delay(300);
                    SaveProductivityDialog(notes, Path.Combine(output, $"{code}-release-notes.png"));
                    notes.Close();
                }
                var entry = new JsonObject
                {
                    ["visibleTextBlocksCompared"] = english.Count,
                    ["changedFromEnglish"] = changed,
                    ["notranslateChanged"] = new JsonArray(privateChanged.Select(s => (JsonNode)s).ToArray()),
                };
                if (code != "en") entry["unchangedText"] = new JsonArray(stillEnglish.Select(s => (JsonNode)s).ToArray());
                else entry["notRestored"] = new JsonArray(notRestored.Select(s => (JsonNode)s).ToArray());
                report[run == runs.Length - 1 ? "en-again" : code] = entry;
                // Switching back must restore every original; a "notranslate" text must never change.
                if (privateChanged.Count > 0 || notRestored.Any(t => Regex.IsMatch(t, @"\p{IsHiragana}|\p{IsKatakana}|\p{IsCJKUnifiedIdeographs}"))) exitCode = 1;
            }
        }
        catch (Exception ex)
        {
            report["error"] = ex.ToString();
            exitCode = 1;
        }
        File.WriteAllText(Path.Combine(output, "language-report.json"), report.ToJsonString(new JsonSerializerOptions
            { WriteIndented = true, Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping }));
        Environment.ExitCode = exitCode;
        Close();
    }

    // Every TextBlock in the window now, and whether it is inside a "notranslate" element.
    private List<(TextBlock Block, string? Text, bool Private)> Shown() =>
        this.GetVisualDescendants().OfType<TextBlock>()
            .Select(b => (b, b.Text, b.GetSelfAndLogicalAncestors().OfType<StyledElement>().Concat(b.GetVisualAncestors().OfType<StyledElement>())
                .Any(a => a.Classes.Contains(LauncherTranslator.NoTranslate))))
            .ToList();
}
