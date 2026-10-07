using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Layout;
using Avalonia.Media;
using TheLadsLauncher.Services;
using TheLadsLauncher.Views;

namespace TheLadsLauncher;

/// <summary>
/// Files tab: an explorer limited to the active profile's game folder (breadcrumb, folders first, type icons, size and
/// modified columns) and the built-in editor/viewer (<see cref="FileEditorView"/>) that opens a clicked file in place.
/// </summary>
public partial class MainWindow
{
    // ═══════════════════════════════════════
    //  FILES EXPLORER
    // ═══════════════════════════════════════

    private string _filesCurrentDir = "";
    private string _filesRootDir = "";
    private bool _filesEditorWired;
    private bool _filesPreviewOpened;

    /// <summary>The Files page follows the active profile: back to its game folder on every profile switch.</summary>
    private void ResetFilesRoot()
    {
        _filesRootDir = settings.InstancePath;
        _filesCurrentDir = settings.InstancePath;
        if (FilesEditor.IsVisible && !FilesEditor.IsDirty) CloseFilesEditor();
        if (FilesPage.IsVisible) LoadFiles(_filesRootDir);
    }

    private static TextBlock FilesNote(string text) => new()
    {
        Text = text, Foreground = Brush.Parse("#A0A1AA"), FontSize = 13, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(4)
    };

    private void LoadFiles(string dir)
    {
        if (string.IsNullOrWhiteSpace(_filesRootDir))
            _filesRootDir = settings.InstancePath;
        if (string.IsNullOrWhiteSpace(dir) || !Directory.Exists(dir) || !SafeFileOps.IsSameOrInside(dir, _filesRootDir))
            dir = _filesRootDir;

        WireFilesEditor();
        _filesCurrentDir = dir;
        bool atRoot = SafeFileOps.PathsEqual(dir, _filesRootDir);
        FilesUpBtn.IsEnabled = !atRoot;
        FilesPathText.Text = "Game folder: " + _filesRootDir;
        ToolTip.SetTip(FilesPathText, _filesRootDir);
        FilesSharedChip.IsVisible = false;
        BuildFilesBreadcrumb(dir);
        FilesList.Children.Clear();

        if (!Directory.Exists(dir))
        {
            FilesList.Children.Add(FilesEmptyState("This profile has no game files yet", "Launch it once to create its game folder."));
            return;
        }

        try
        {
            // Worlds, resource packs and shader packs are links to the shared folders: badge them and say where their content lives.
            IReadOnlyList<SharedFolderStatus> statuses = SharedContentService.Instance.GetStatus(_filesRootDir);
            var sharedArea = statuses.FirstOrDefault(s => s.State is SharedFolderState.Shared or SharedFolderState.GlobalFolder
                && SafeFileOps.IsSameOrInside(dir, s.ProfilePath));
            if (sharedArea != null)
            {
                FilesSharedChip.IsVisible = true;
                FilesSharedChipText.Text = "Shared with every version";
                ToolTip.SetTip(FilesSharedChip, $"This folder's content lives in {sharedArea.SharedPath}");
            }

            foreach (var d in Directory.GetDirectories(dir).OrderBy(p => Path.GetFileName(p), StringComparer.OrdinalIgnoreCase))
                FilesList.Children.Add(BuildFileRow(d, true, atRoot ? statuses.FirstOrDefault(s => SafeFileOps.PathsEqual(s.ProfilePath, d)) : null));
            foreach (var f in Directory.GetFiles(dir).Where(f => !f.EndsWith(".lads-tmp", StringComparison.OrdinalIgnoreCase))
                         .OrderBy(p => Path.GetFileName(p), StringComparer.OrdinalIgnoreCase))
                FilesList.Children.Add(BuildFileRow(f, false, null));

            if (FilesList.Children.Count == 0)
                FilesList.Children.Add(FilesEmptyState("Empty folder", "Nothing here yet."));
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            Log($"[Files] Could not list '{dir}': {ex.Message}");
            FilesList.Children.Add(FilesEmptyState("Could not list this folder", ex.Message));
        }
        TryOpenPreviewFile();
    }

    private static Control FilesEmptyState(string title, string hint)
    {
        var panel = new StackPanel { Spacing = 4, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 48, 0, 48) };
        panel.Children.Add(new TextBlock { Text = title, FontSize = 14, FontWeight = FontWeight.SemiBold, Foreground = Brush.Parse("#E9E9EA"), HorizontalAlignment = HorizontalAlignment.Center });
        panel.Children.Add(new TextBlock { Text = hint, FontSize = 12.5, Foreground = Brush.Parse("#8F919C"), HorizontalAlignment = HorizontalAlignment.Center, TextWrapping = TextWrapping.Wrap, TextAlignment = TextAlignment.Center });
        return panel;
    }

    /// <summary>Root › sub › sub, each segment clickable; the root is named after the profile folder.</summary>
    private void BuildFilesBreadcrumb(string dir)
    {
        FilesBreadcrumb.Children.Clear();
        var segments = new List<string> { _filesRootDir };
        if (SafeFileOps.IsSameOrInside(dir, _filesRootDir) && !SafeFileOps.PathsEqual(dir, _filesRootDir))
        {
            string current = _filesRootDir;
            foreach (string part in Path.GetRelativePath(_filesRootDir, dir).Split(Path.DirectorySeparatorChar, StringSplitOptions.RemoveEmptyEntries))
                segments.Add(current = Path.Combine(current, part));
        }
        for (int i = 0; i < segments.Count; i++)
        {
            string target = segments[i];
            bool last = i == segments.Count - 1;
            if (i > 0) FilesBreadcrumb.Children.Add(new TextBlock { Text = "›", Foreground = Brush.Parse("#5D5F69"), FontSize = 14, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(2, 0, 2, 1) });
            var label = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
            if (i == 0) label.Children.Add(LadsIcons.Glyph(LadsIcons.Home, 14, Brush.Parse(last ? "#FFFFFF" : "#A0A1AA")));
            label.Children.Add(new TextBlock { Text = i == 0 ? Path.GetFileName(Path.TrimEndingDirectorySeparator(target)) : Path.GetFileName(target), VerticalAlignment = VerticalAlignment.Center }.Untranslated());
            var crumb = new Button { Content = label, Classes = { "crumb" } };
            if (last) crumb.Classes.Add("current");
            ToolTip.SetTip(crumb, target);
            crumb.Click += (_, _) => LoadFiles(target);
            FilesBreadcrumb.Children.Add(crumb);
        }
    }

    private static (string Label, string Tip)? SharedBadge(SharedFolderStatus status) => status.State switch
    {
        SharedFolderState.Shared => ("SHARED", status.Detail),
        SharedFolderState.GlobalFolder => ("GLOBAL FOLDER", status.Detail),
        SharedFolderState.SeparateFolder => ("NOT SHARED YET", status.Detail),
        SharedFolderState.LinkedElsewhere => ("LINKED ELSEWHERE", status.Detail),
        SharedFolderState.BrokenLink => ("BROKEN LINK", status.Detail),
        _ => null
    };

    private Border BuildFileRow(string path, bool isDir, SharedFolderStatus? shared)
    {
        string name = Path.GetFileName(path);
        var row = new Border { Classes = { "fileRow" } };
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,96,150,76") };

        var iconBox = new Border
        {
            Width = 30, Height = 30, CornerRadius = new CornerRadius(7), Background = Brush.Parse(isDir ? "#2A2619" : "#22232A"),
            Margin = new Thickness(0, 0, 12, 0), VerticalAlignment = VerticalAlignment.Center,
            Child = LadsIcons.Glyph(FileEditorView.FileIconData(path, isDir), 16, FileEditorView.FileIconBrush(path, isDir))
        };
        grid.Children.Add(iconBox);

        var nameLine = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        nameLine.Children.Add(new TextBlock
        {
            Text = name, Foreground = Brush.Parse(isDir ? "#E9E9EA" : "#D2D3D8"), FontSize = 13, FontWeight = isDir ? FontWeight.SemiBold : FontWeight.Normal,
            VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis
        }.Untranslated());
        if (shared != null && SharedBadge(shared) is { } badge)
        {
            var chip = new Border { Classes = { "chip", "accent" }, Child = new TextBlock { Text = badge.Label, FontSize = 9.5, FontWeight = FontWeight.Bold } };
            ToolTip.SetTip(chip, badge.Tip);
            nameLine.Children.Add(chip);
        }
        Grid.SetColumn(nameLine, 1);
        grid.Children.Add(nameLine);

        string size = "", modified = "";
        try
        {
            if (isDir)
            {
                int count = Directory.EnumerateFileSystemEntries(path).Take(1000).Count();
                size = count == 0 ? "Empty" : count >= 1000 ? "999+ items" : count == 1 ? "1 item" : $"{count} items";
                modified = Directory.GetLastWriteTime(path).ToString("g");
            }
            else
            {
                var info = new FileInfo(path);
                size = FormatBytes(info.Length);
                modified = info.LastWriteTime.ToString("g");
            }
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { size = isDir ? "" : "size unknown"; }
        var sizeText = new TextBlock { Text = size, Foreground = Brush.Parse("#8F919C"), FontSize = 12, VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(8, 0, 16, 0) };
        Grid.SetColumn(sizeText, 2);
        grid.Children.Add(sizeText);
        var dateText = new TextBlock { Text = modified, Foreground = Brush.Parse("#8F919C"), FontSize = 12, VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis };
        Grid.SetColumn(dateText, 3);
        grid.Children.Add(dateText);

        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Center };
        var openBtn = FileRowAction(isDir ? LadsIcons.FolderOpen : LadsIcons.OpenExternal, isDir ? "Open in Explorer" : "Open with the default Windows app", false);
        openBtn.Click += (_, _) => OpenPath(path);
        actions.Children.Add(openBtn);
        var delBtn = FileRowAction(LadsIcons.Delete, "Move to the Recycle Bin", true);
        delBtn.Click += async (_, _) => await DeleteFileEntryAsync(path);
        actions.Children.Add(delBtn);
        Grid.SetColumn(actions, 4);
        grid.Children.Add(actions);

        row.Child = grid;
        ToolTip.SetTip(row, isDir ? null : (TextFileCodec.IsImageExtension(path) ? "Click to view" : "Click to open in the editor"));
        row.PointerPressed += (_, _) =>
        {
            if (isDir) LoadFiles(path);
            else _ = OpenFileInEditorAsync(path);
        };
        return row;
    }

    private static Button FileRowAction(string icon, string tip, bool danger)
    {
        var button = new Button { Classes = { "icon", "rowAction" }, Width = 28, Height = 28, Content = LadsIcons.Glyph(icon, 14, Brush.Parse(danger ? "#E08A8A" : "#B5B7C0")) };
        if (danger) button.Classes.Add("danger");
        ToolTip.SetTip(button, tip);
        return button;
    }

    /// <summary>
    /// Every delete asks first and goes to the Recycle Bin. Shared-folder links are refused (deleting them would not remove
    /// content, and deleting through them would remove it for every version); content inside them is confirmed as shared.
    /// </summary>
    private async Task DeleteFileEntryAsync(string path)
    {
        string name = Path.GetFileName(path);
        try
        {
            if (SafeFileOps.IsLink(path))
            {
                await ShowLadsDialogAsync("Shared folder link",
                    $"'{name}' is a link to '{SafeFileOps.GetLinkTarget(path)}', not a folder of its own. Worlds, resource packs and shader packs are shared by every version, so this link cannot be deleted. Open the shared folder (Home: Worlds, Resource packs, Shader packs) to manage its content.");
                return;
            }
            var shared = SharedContentService.Instance.GetStatus(_filesRootDir).FirstOrDefault(s =>
                s.State is SharedFolderState.Shared or SharedFolderState.GlobalFolder && SafeFileOps.IsSameOrInside(path, s.ProfilePath));
            bool confirmed = shared != null
                ? await ShowLadsDialogAsync("Delete shared content",
                    $"'{name}' is shared content used by every version (it lives in '{shared.SharedPath}'). Deleting it removes it for all versions. Move it to the Recycle Bin?",
                    "Move to Recycle Bin", "Cancel", danger: true)
                : await ShowLadsDialogAsync("Delete", $"Move '{name}' to the Recycle Bin?", "Move to Recycle Bin", "Cancel", danger: true);
            if (!confirmed) return;
            await Task.Run(() => SafeFileOps.DeleteToRecycleBin(path));
            Log($"[Files] Moved to the Recycle Bin: {path}");
        }
        catch (Exception ex)
        {
            Log($"[Files] Delete failed for '{path}': {ex.Message}");
            await ShowLadsDialogAsync("Could not delete", ex.Message);
        }
        LoadFiles(_filesCurrentDir);
    }

    private static string FormatBytes(long b) => FileEditorView.FormatBytes(b);

    private void OpenPath(string path)
    {
        try
        {
            Process.Start(new ProcessStartInfo { FileName = path, UseShellExecute = true });
        }
        catch (Exception ex) when (ex is System.ComponentModel.Win32Exception or IOException or UnauthorizedAccessException or InvalidOperationException)
        {
            Log($"[Files] Open failed for '{path}': {ex.Message}");
            _ = ShowLadsDialogAsync("Could not open", $"'{path}': {ex.Message}");
        }
    }

    private void FilesUp_Click(object? sender, RoutedEventArgs e)
    {
        var parent = Directory.GetParent(_filesCurrentDir);
        if (parent != null) LoadFiles(parent.FullName);
    }

    private void FilesRefresh_Click(object? sender, RoutedEventArgs e) => LoadFiles(_filesCurrentDir);

    private void FilesOpenExplorer_Click(object? sender, RoutedEventArgs e) => OpenPath(_filesCurrentDir);

    // ─── Built-in editor / viewer ───────────────────────

    private void WireFilesEditor()
    {
        if (_filesEditorWired) return;
        _filesEditorWired = true;
        FilesUpBtn.Content = LadsIcons.Glyph(LadsIcons.ArrowUp, 16);
        FilesRefreshBtn.Content = LadsIcons.Glyph(LadsIcons.Refresh, 16);
        FilesEditor.ConfirmAsync = (title, message, confirm) => string.IsNullOrEmpty(confirm)
            ? ShowLadsDialogAsync(title, message)
            : ShowLadsDialogAsync(title, message, confirm, "Cancel", danger: true); // overwrite, discard and revert all lose text
        FilesEditor.IsGameRunning = () => !string.IsNullOrEmpty(_filesRootDir) && IsGameRunningFor(_filesRootDir);
        FilesEditor.Log += Log;
        FilesEditor.CloseRequested += CloseFilesEditor;
    }

    private async Task OpenFileInEditorAsync(string path)
    {
        if (!SafeFileOps.IsSameOrInside(path, _filesRootDir)) return;
        WireFilesEditor();
        if (FilesEditor.IsVisible && FilesEditor.IsDirty && !await FilesEditor.RequestCloseAsync()) return;
        FilesEditor.IsVisible = true;
        FilesBrowser.IsVisible = false;
        await FilesEditor.OpenAsync(path, _filesRootDir);
    }

    private void CloseFilesEditor()
    {
        FilesEditor.IsVisible = false;
        FilesBrowser.IsVisible = true;
        if (FilesPage.IsVisible) LoadFiles(_filesCurrentDir); // sizes and dates of a saved file
    }

    /// <summary>
    /// Sandbox QA only (--preview-page Files): LADS_PREVIEW_FILES_OPEN=&lt;path relative to the game folder&gt; opens that file in
    /// the editor/viewer so its states can be captured; LADS_PREVIEW_FILES_DIR=&lt;relative folder&gt; browses into a folder.
    /// </summary>
    private void TryOpenPreviewFile()
    {
        if (_filesPreviewOpened || !Environment.GetCommandLineArgs().Contains("--preview-page")) return;
        _filesPreviewOpened = true;
        if (Environment.GetEnvironmentVariable("LADS_PREVIEW_FILES_DIR") is { Length: > 0 } dir)
            Avalonia.Threading.Dispatcher.UIThread.Post(() => LoadFiles(Path.Combine(_filesRootDir, dir)));
        if (Environment.GetEnvironmentVariable("LADS_PREVIEW_FILES_OPEN") is { Length: > 0 } file)
            Avalonia.Threading.Dispatcher.UIThread.Post(() => _ = OpenFileInEditorAsync(Path.Combine(_filesRootDir, file)));
    }
}
