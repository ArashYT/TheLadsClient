using System;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Media.Imaging;

namespace TheLadsLauncher;

public partial class MainWindow
{
    // Sandbox-only UI evidence: the update screen during a simulated download, plus exact mining-animation frames.
    private async Task RunStartupPreviewAsync(string output)
    {
        Directory.CreateDirectory(output);
        ShowStartupStatus("Checking for launcher updates…");
        await Task.Delay(900);
        SaveWindowScreenshot(Path.Combine(output, "startup-checking.png"));
        for (int percent = 0; percent <= 100; percent += 2)
        {
            ShowStartupStatus($"Downloading v1.3.5: {percent}%");
            await Task.Delay(40);
            if (percent == 46) { await Task.Delay(700); SaveWindowScreenshot(Path.Combine(output, "startup-downloading.png")); }
        }
        ShowStartupStatus("Installing v1.3.5 and restarting…");
        await Task.Delay(800);
        SaveWindowScreenshot(Path.Combine(output, "startup-installing.png"));
        // Drop-in, wind-up peak, downswing blur, impact, deep cracks, breaking, next block dropping, then the ore and log blocks.
        foreach (double t in new[] { 0.2, 0.81, 0.86, 0.93, 2.8, 4.4, 4.9, 12.24, 16.96 })
        {
            StartupMining.Freeze(t);
            await Task.Delay(120);
            using var bitmap = new RenderTargetBitmap(new PixelSize((int)(StartupMining.Bounds.Width * 2), (int)(StartupMining.Bounds.Height * 2)), new Vector(192, 192));
            bitmap.Render(StartupMining);
            bitmap.Save(Path.Combine(output, $"mining-{t:0.00}.png"));
        }
        // One full block cycle at 30 FPS, for reviewing motion (e.g. assembled into a GIF).
        Directory.CreateDirectory(Path.Combine(output, "cycle"));
        int count = (int)Math.Round(Controls.MiningAnimation.CycleSeconds * 30);
        for (int i = 0; i < count; i++)
        {
            StartupMining.Freeze(i / 30.0);
            await Task.Delay(15);
            using var bitmap = new RenderTargetBitmap(new PixelSize((int)StartupMining.Bounds.Width, (int)StartupMining.Bounds.Height), new Vector(96, 96));
            bitmap.Render(StartupMining);
            bitmap.Save(Path.Combine(output, "cycle", $"{i:000}.png"));
        }
        Close();
    }

    // Sandbox-only UI evidence: actual mounted screenshot cards plus collapsed and expanded release notes.
    private async Task RunDiscoveryPreviewAsync(string output)
    {
        int exitCode = 0;
        var result = new JsonObject();
        try
        {
            Directory.CreateDirectory(output);
            await LoadGalleryAsync();
            await _galleryThumbnails;
            NavigateTo("Gallery");
            await Task.Delay(500);
            SaveWindowScreenshot(Path.Combine(output, "gallery-discovery.png"));
            result["gallery"] = JsonSerializer.SerializeToNode(_gallerySources.Values);
            result["galleryStatus"] = GalleryStatusText.Text;
            var notes = ReleaseNotes.Read(Path.Combine(AppContext.BaseDirectory, "release-notes"));
            var dialog = new Views.ReleaseNotesWindow(notes, Program.Version);
            var closed = dialog.ShowDialog(this);
            await Task.Delay(500);
            void Capture(string name)
            {
                using var bitmap = new RenderTargetBitmap(new PixelSize((int)dialog.Bounds.Width, (int)dialog.Bounds.Height), new Vector(96, 96));
                bitmap.Render(dialog);
                bitmap.Save(Path.Combine(output, name));
            }
            Capture("whats-new.png");
            result["highlights"] = JsonSerializer.SerializeToNode(dialog.VisibleHighlights);
            result["detailsCollapsed"] = !dialog.Details.IsExpanded;
            result["historyCollapsed"] = !dialog.History.IsExpanded;
            result["historyCount"] = notes.Count;
            dialog.Details.IsExpanded = true;
            await Task.Delay(300);
            Capture("whats-new-details.png");
            dialog.Width = 440;
            dialog.Height = 440;
            dialog.Details.IsExpanded = false;
            await Task.Delay(300);
            Capture("whats-new-small.png");
            dialog.Close();
            await closed;
        }
        catch (Exception e) { exitCode = 1; result["error"] = e.ToString(); }
        File.WriteAllText(Path.Combine(output, "preview-discovery.json"), result.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
        Environment.ExitCode = exitCode;
        Close();
    }
}
