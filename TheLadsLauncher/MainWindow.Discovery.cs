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
