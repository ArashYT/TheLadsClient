using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Presenters;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Threading;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

/// <summary>
/// The Files tab's built-in viewer: a text editor (line numbers, find, word wrap, encoding- and line-ending-preserving atomic
/// save limited to the profile folder) for text files, a zoom/pan image viewer for images, and an "open externally" notice
/// for binary or very large files. MainWindow hosts it over the file list and supplies the dialogs and game state.
/// </summary>
public partial class FileEditorView : UserControl
{
    private string _path = "";
    private string _root = "";
    private TextDocument? _doc;
    private string _savedText = "";
    private bool _loading;
    private FileOpenKind _kind;
    private ScrollViewer? _editorScroll;
    private int _lineCount = -1;

    // Image viewer state: zoom is relative to the fitted size (1 = fit, never upscaled past the image's own pixels).
    private Bitmap? _bitmap;
    private double _zoom = 1;
    private Vector _pan;
    private Point? _dragFrom;

    /// <summary>(title, message, confirm button) → confirmed. Set by the host (its themed dialog).</summary>
    public Func<string, string, string, Task<bool>>? ConfirmAsync { get; set; }
    /// <summary>Whether Minecraft is running for the folder being browsed.</summary>
    public Func<bool>? IsGameRunning { get; set; }
    public event Action? CloseRequested;
    public event Action<string>? Saved;
    public event Action<string>? Log;

    public string CurrentPath => _path;
    public bool IsDirty => _kind == FileOpenKind.Text && _doc != null && (Editor.Text ?? "") != _savedText;

    public FileEditorView()
    {
        InitializeComponent();
        WrapToggle.Content = LadsIcons.Glyph(LadsIcons.Wrap, 16);
        FindButton.Content = LadsIcons.Glyph(LadsIcons.Search, 16);
        ZoomOutButton.Content = LadsIcons.Glyph(LadsIcons.Minus, 16);
        ZoomInButton.Content = LadsIcons.Glyph(LadsIcons.Plus, 16);
        FitButton.Content = LadsIcons.Glyph(LadsIcons.Fit, 16);
        ExternalButton.Content = LadsIcons.Glyph(LadsIcons.OpenExternal, 16);
        CloseButton.Content = LadsIcons.Glyph(LadsIcons.Close, 16);
        FindPrevButton.Content = LadsIcons.Glyph(LadsIcons.ChevronLeft, 18);
        FindNextButton.Content = LadsIcons.Glyph(LadsIcons.ChevronRight, 18);
        FindCloseButton.Content = LadsIcons.Glyph(LadsIcons.Close, 14);
        WarningIconHost.Child = LadsIcons.Glyph(LadsIcons.Warning, 16, new SolidColorBrush(Color.Parse("#E8B04B")));

        Editor.TemplateApplied += (_, e) =>
        {
            if (_editorScroll != null) _editorScroll.ScrollChanged -= EditorScroll_Changed;
            _editorScroll = e.NameScope.Find<ScrollViewer>("PART_ScrollViewer");
            if (_editorScroll != null) _editorScroll.ScrollChanged += EditorScroll_Changed;
        };
        Editor.TextChanged += (_, _) => { if (!_loading) { UpdateLineNumbers(); UpdateDirty(); } };
        Editor.PropertyChanged += (_, e) => { if (e.Property == TextBox.CaretIndexProperty) UpdateCaretStatus(); };
        AddHandler(KeyDownEvent, OnPreviewKeyDown, RoutingStrategies.Tunnel);

        ImagePane.PointerWheelChanged += ImagePane_Wheel;
        ImagePane.PointerPressed += (_, e) => { if (_zoom > 1.0001) { _dragFrom = e.GetPosition(ImagePane); e.Handled = true; } };
        ImagePane.PointerMoved += (_, e) =>
        {
            if (_dragFrom is not { } from) return;
            var p = e.GetPosition(ImagePane);
            _pan += p - from;
            _dragFrom = p;
            ApplyImageTransform();
        };
        ImagePane.PointerReleased += (_, _) => _dragFrom = null;
        ImagePane.SizeChanged += (_, _) => ApplyImageTransform();
    }

    /// <summary>Shows <paramref name="path"/>: text in the editor, an image in the viewer, anything else as a notice.</summary>
    public async Task OpenAsync(string path, string root)
    {
        _path = path;
        _root = root;
        _doc = null;
        DisposeImage();
        FileNameText.Text = Path.GetFileName(path);
        string relative = SafeFileOps.IsSameOrInside(path, root) ? Path.GetRelativePath(root, path) : path;
        FilePathText.Text = relative;
        ToolTip.SetTip(FilePathText, path);
        FileIconHost.Child = LadsIcons.Glyph(FileIconData(path, false), 18, FileIconBrush(path, false));
        FindBar.IsVisible = false;
        StatusLeft.Text = "Opening…";
        StatusRight.Text = "";

        try { _kind = await Task.Run(() => TextFileCodec.Classify(path)); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            ShowMessage("Could not open the file", ex.Message, LadsIcons.Warning);
            return;
        }

        UpdateWarning();
        switch (_kind)
        {
            case FileOpenKind.Text: await OpenTextAsync(); break;
            case FileOpenKind.Image: await OpenImageAsync(); break;
            case FileOpenKind.Binary:
                ShowMessage("Binary file — open externally", "This file is not text, so the built-in editor would damage it. Open it with the program made for it.", LadsIcons.File);
                break;
            case FileOpenKind.TooLarge:
                ShowMessage("Large file — open externally", $"This file is {FormatBytes(SafeLength(path))}. The built-in editor opens text files up to {FormatBytes(TextFileCodec.MaxEditableBytes)}.", LadsIcons.FileText);
                break;
            default:
                ShowMessage("File not found", "It was moved or deleted. Refresh the folder.", LadsIcons.Warning);
                break;
        }
    }

    private void SetMode(FileOpenKind kind)
    {
        TextPane.IsVisible = kind == FileOpenKind.Text;
        ImagePane.IsVisible = kind == FileOpenKind.Image;
        MessagePane.IsVisible = kind is not (FileOpenKind.Text or FileOpenKind.Image);
        TextTools.IsVisible = kind == FileOpenKind.Text;
        ImageTools.IsVisible = kind == FileOpenKind.Image;
        DirtyChip.IsVisible = false;
        KindChipText.Text = kind switch
        {
            FileOpenKind.Text => KindLabel(_path),
            FileOpenKind.Image => "Image",
            FileOpenKind.Binary => "Binary",
            FileOpenKind.TooLarge => "Large file",
            _ => "Missing"
        };
    }

    private void ShowMessage(string title, string text, string icon)
    {
        SetMode(_kind is FileOpenKind.Binary or FileOpenKind.TooLarge ? _kind : FileOpenKind.Missing);
        MessageTitle.Text = title;
        MessageText.Text = text;
        MessageIconHost.Child = LadsIcons.Glyph(icon, 26, new SolidColorBrush(Color.Parse("#9EA0AA")));
        StatusLeft.Text = File.Exists(_path) ? $"{FormatBytes(SafeLength(_path))} · modified {File.GetLastWriteTime(_path):g}" : "";
    }

    // ─── Text ─────────────────────────────────────────

    private async Task OpenTextAsync()
    {
        TextDocument doc;
        try { doc = await Task.Run(() => TextFileCodec.Load(_path)); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            _kind = FileOpenKind.Missing;
            ShowMessage("Could not read the file", ex.Message, LadsIcons.Warning);
            return;
        }
        SetMode(FileOpenKind.Text);
        LoadDocument(doc);
        Dispatcher.UIThread.Post(() => { Editor.Focus(); Editor.CaretIndex = 0; }, DispatcherPriority.Background);
    }

    private void LoadDocument(TextDocument doc)
    {
        _doc = doc;
        _savedText = doc.Text;
        _loading = true;
        try
        {
            Editor.NewLine = doc.LineEnding; // Enter inserts the file's own line ending
            Editor.Text = doc.Text;
        }
        finally { _loading = false; }
        _lineCount = -1;
        UpdateLineNumbers();
        UpdateDirty();
        UpdateCaretStatus();
    }

    private void UpdateLineNumbers()
    {
        string text = Editor.Text ?? "";
        int lines = 1;
        foreach (char c in text) if (c == '\n') lines++;
        if (lines == _lineCount) return;
        _lineCount = lines;
        var sb = new StringBuilder(lines * 4);
        for (int i = 1; i <= lines; i++) { if (i > 1) sb.Append('\n'); sb.Append(i); }
        LineNumbers.Text = sb.ToString();
    }

    private void EditorScroll_Changed(object? sender, ScrollChangedEventArgs e) =>
        LineNumbers.RenderTransform = new TranslateTransform(0, -(_editorScroll?.Offset.Y ?? 0));

    private void UpdateDirty()
    {
        bool dirty = IsDirty;
        DirtyChip.IsVisible = dirty;
        SaveButton.IsEnabled = dirty;
        RevertButton.IsEnabled = dirty;
    }

    private void UpdateCaretStatus()
    {
        if (_doc == null || _kind != FileOpenKind.Text) return;
        string text = Editor.Text ?? "";
        int caret = Math.Clamp(Editor.CaretIndex, 0, text.Length);
        int line = 1, lineStart = 0;
        for (int i = 0; i < caret; i++) if (text[i] == '\n') { line++; lineStart = i + 1; }
        StatusLeft.Text = $"Ln {line}, Col {caret - lineStart + 1} · {_lineCount} lines";
        StatusRight.Text = $"{_doc.EncodingLabel} · {_doc.LineEndingLabel} · {FormatBytes(_doc.Length)}";
    }

    private void WrapToggle_Changed(object? sender, RoutedEventArgs e)
    {
        bool wrap = WrapToggle.IsChecked == true;
        Editor.TextWrapping = wrap ? TextWrapping.Wrap : TextWrapping.NoWrap;
        // Wrapped lines no longer match one number per row.
        GutterHost.IsVisible = !wrap;
    }

    private async void Save_Click(object? sender, RoutedEventArgs e) => await SaveAsync();
    private async void Revert_Click(object? sender, RoutedEventArgs e) => await RevertAsync();

    public async Task<bool> SaveAsync()
    {
        if (_doc == null || _kind != FileOpenKind.Text || !IsDirty) return true;
        string text = Editor.Text ?? "";
        try
        {
            if (File.Exists(_path) && File.GetLastWriteTimeUtc(_path) != _doc.LastWrite && ConfirmAsync != null
                && !await ConfirmAsync("File changed on disk", $"'{Path.GetFileName(_path)}' was changed by another program after you opened it. Overwrite it with your version?", "Overwrite"))
                return false;
            byte[] bytes = TextFileCodec.Encode(_doc, text);
            string path = _path, root = _root;
            await Task.Run(() => TextFileCodec.SaveAtomic(path, bytes, root));
            _doc = _doc with { Text = text, LastWrite = File.GetLastWriteTimeUtc(path), Length = bytes.LongLength };
            _savedText = text;
            UpdateDirty();
            UpdateCaretStatus();
            StatusLeft.Text = $"Saved at {DateTime.Now:T}";
            Log?.Invoke($"[Files] Saved {path} ({_doc.EncodingLabel}, {_doc.LineEndingLabel})");
            Saved?.Invoke(path);
            return true;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or EncoderFallbackException)
        {
            Log?.Invoke($"[Files] Save failed for '{_path}': {ex.Message}");
            if (ConfirmAsync != null) await ConfirmAsync("Could not save", ex.Message, "");
            return false;
        }
    }

    private async Task RevertAsync()
    {
        if (_kind != FileOpenKind.Text) return;
        if (IsDirty && ConfirmAsync != null && !await ConfirmAsync("Revert changes", $"Discard your changes to '{Path.GetFileName(_path)}' and reload it from disk?", "Revert"))
            return;
        try { LoadDocument(await Task.Run(() => TextFileCodec.Load(_path))); StatusLeft.Text = "Reloaded from disk"; }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { StatusLeft.Text = "Could not reload: " + ex.Message; }
    }

    // ─── Find ─────────────────────────────────────────

    private void Find_Click(object? sender, RoutedEventArgs e) => ShowFind();

    private void ShowFind()
    {
        if (_kind != FileOpenKind.Text) return;
        FindBar.IsVisible = true;
        if (!string.IsNullOrEmpty(Editor.SelectedText) && !Editor.SelectedText.Contains('\n')) FindBox.Text = Editor.SelectedText;
        FindBox.Focus();
        FindBox.SelectAll();
    }

    private void FindClose_Click(object? sender, RoutedEventArgs e) { FindBar.IsVisible = false; Editor.Focus(); }
    private void FindNext_Click(object? sender, RoutedEventArgs e) => FindStep(true);
    private void FindPrev_Click(object? sender, RoutedEventArgs e) => FindStep(false);
    private void FindBox_TextChanged(object? sender, TextChangedEventArgs e) => FindStep(true, fromSelectionStart: true);

    private void FindBox_KeyDown(object? sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter) { FindStep((e.KeyModifiers & KeyModifiers.Shift) == 0); e.Handled = true; }
        else if (e.Key == Key.Escape) { FindBar.IsVisible = false; Editor.Focus(); e.Handled = true; }
    }

    private void FindStep(bool forward, bool fromSelectionStart = false)
    {
        string needle = FindBox.Text ?? "", text = Editor.Text ?? "";
        if (needle.Length == 0) { FindStatus.Text = ""; return; }
        int count = 0;
        for (int i = text.IndexOf(needle, StringComparison.OrdinalIgnoreCase); i >= 0; i = text.IndexOf(needle, i + needle.Length, StringComparison.OrdinalIgnoreCase)) count++;
        if (count == 0) { FindStatus.Text = "No matches"; return; }
        int selStart = Math.Min(Editor.SelectionStart, Editor.SelectionEnd), selEnd = Math.Max(Editor.SelectionStart, Editor.SelectionEnd);
        int at = forward
            ? text.IndexOf(needle, Math.Min(text.Length, fromSelectionStart ? selStart : selEnd), StringComparison.OrdinalIgnoreCase)
            : selStart > 0 ? text.LastIndexOf(needle, selStart - 1, StringComparison.OrdinalIgnoreCase) : -1;
        if (at < 0) at = forward ? text.IndexOf(needle, StringComparison.OrdinalIgnoreCase) : text.LastIndexOf(needle, StringComparison.OrdinalIgnoreCase); // wrap around
        int index = 0;
        for (int i = text.IndexOf(needle, StringComparison.OrdinalIgnoreCase); i >= 0 && i <= at; i = text.IndexOf(needle, i + needle.Length, StringComparison.OrdinalIgnoreCase)) index++;
        Editor.SelectionStart = at;
        Editor.SelectionEnd = at + needle.Length;
        int line = 0;
        for (int i = 0; i < at; i++) if (text[i] == '\n') line++;
        try { Editor.ScrollToLine(line); } catch (ArgumentOutOfRangeException) { }
        FindStatus.Text = $"{index} of {count}";
    }

    // ─── Image ────────────────────────────────────────

    private async Task OpenImageAsync()
    {
        try
        {
            string path = _path;
            _bitmap = await Task.Run(() =>
            {
                using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
                return new Bitmap(stream);
            });
        }
        catch (Exception ex) // the decoder throws plain exceptions for formats or files it cannot read
        {
            _kind = FileOpenKind.Binary;
            ShowMessage("Preview unavailable — open externally", $"This image could not be decoded ({ex.Message}).", LadsIcons.Image);
            return;
        }
        SetMode(FileOpenKind.Image);
        Viewer.Source = _bitmap;
        _zoom = 1;
        _pan = default;
        ApplyImageTransform();
        var px = _bitmap.PixelSize;
        StatusLeft.Text = $"{px.Width} × {px.Height} px · {Path.GetExtension(_path).TrimStart('.').ToUpperInvariant()} · {FormatBytes(SafeLength(_path))}";
        StatusRight.Text = $"Modified {File.GetLastWriteTime(_path):g} · scroll to zoom, drag to pan";
    }

    /// <summary>On-screen size of the image at zoom 1 (fitted into the pane, never larger than its own pixels).</summary>
    private double FitScale()
    {
        if (_bitmap == null) return 1;
        double w = Math.Max(1, ImagePane.Bounds.Width - 32), h = Math.Max(1, ImagePane.Bounds.Height - 32);
        return Math.Min(1, Math.Min(w / _bitmap.PixelSize.Width, h / _bitmap.PixelSize.Height));
    }

    private void ApplyImageTransform()
    {
        if (_bitmap == null) return;
        if (_zoom <= 1.0001) _pan = default;
        var group = new TransformGroup();
        group.Children.Add(new ScaleTransform(_zoom, _zoom));
        group.Children.Add(new TranslateTransform(_pan.X, _pan.Y));
        Viewer.RenderTransform = group;
        double actual = FitScale() * _zoom;
        // Pixel art (block textures) stays sharp when enlarged; photos and screenshots stay smooth when reduced.
        RenderOptions.SetBitmapInterpolationMode(Viewer, actual > 1.5 ? BitmapInterpolationMode.None : BitmapInterpolationMode.HighQuality);
        ZoomText.Text = $"{actual * 100:0}%";
        Viewer.Cursor = new Cursor(_zoom > 1.0001 ? StandardCursorType.SizeAll : StandardCursorType.Arrow);
    }

    private void SetZoom(double zoom)
    {
        double max = Math.Max(8, 32 / FitScale());
        _zoom = Math.Clamp(zoom, 1, max);
        ApplyImageTransform();
    }

    private void ImagePane_Wheel(object? sender, PointerWheelEventArgs e)
    {
        SetZoom(_zoom * (e.Delta.Y > 0 ? 1.2 : 1 / 1.2));
        e.Handled = true;
    }

    private void ZoomIn_Click(object? sender, RoutedEventArgs e) => SetZoom(_zoom * 1.25);
    private void ZoomOut_Click(object? sender, RoutedEventArgs e) => SetZoom(_zoom / 1.25);
    private void Fit_Click(object? sender, RoutedEventArgs e) => SetZoom(1);
    private void ActualSize_Click(object? sender, RoutedEventArgs e) => SetZoom(1 / FitScale());

    private void DisposeImage()
    {
        Viewer.Source = null;
        _bitmap?.Dispose();
        _bitmap = null;
    }

    // ─── Shared ───────────────────────────────────────

    private void UpdateWarning()
    {
        bool running = IsGameRunning?.Invoke() == true;
        string name = Path.GetFileName(_path).ToLowerInvariant();
        bool rewritten = name.StartsWith("options", StringComparison.Ordinal) && name.EndsWith(".txt", StringComparison.Ordinal);
        WarningBar.IsVisible = running && _kind == FileOpenKind.Text;
        WarningText.Text = rewritten
            ? "Minecraft is running for this profile. It rewrites this file when it closes, so changes saved now will be overwritten — close the game first."
            : "Minecraft is running for this profile. Most changes take effect after the game restarts.";
    }

    private void OnPreviewKeyDown(object? sender, KeyEventArgs e)
    {
        bool ctrl = (e.KeyModifiers & KeyModifiers.Control) != 0;
        if (ctrl && e.Key == Key.S) { _ = SaveAsync(); e.Handled = true; }
        else if (ctrl && e.Key == Key.F) { ShowFind(); e.Handled = true; }
        else if (e.Key == Key.Escape && !FindBar.IsVisible) { _ = RequestCloseAsync(); e.Handled = true; }
    }

    private async void Close_Click(object? sender, RoutedEventArgs e) => await RequestCloseAsync();

    /// <summary>Asks before discarding unsaved changes; true when the panel may close.</summary>
    public async Task<bool> RequestCloseAsync()
    {
        if (IsDirty && ConfirmAsync != null
            && !await ConfirmAsync("Unsaved changes", $"Close '{Path.GetFileName(_path)}' without saving your changes?", "Discard changes"))
            return false;
        DisposeImage();
        _doc = null;
        _loading = true;
        try { Editor.Text = ""; } finally { _loading = false; }
        CloseRequested?.Invoke();
        return true;
    }

    private void OpenExternal_Click(object? sender, RoutedEventArgs e)
    {
        try { Process.Start(new ProcessStartInfo { FileName = _path, UseShellExecute = true }); }
        catch (Exception ex) when (ex is System.ComponentModel.Win32Exception or IOException or InvalidOperationException)
        {
            Log?.Invoke($"[Files] Open failed for '{_path}': {ex.Message}");
            StatusLeft.Text = "Could not open externally: " + ex.Message;
        }
    }

    private static long SafeLength(string path)
    {
        try { return new FileInfo(path).Length; } catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { return 0; }
    }

    private static string KindLabel(string path) => Path.GetExtension(path).ToLowerInvariant() switch
    {
        ".json" or ".json5" or ".mcmeta" => "JSON",
        ".toml" => "TOML",
        ".properties" or ".cfg" or ".ini" or ".conf" => "Config",
        ".log" => "Log",
        ".xml" => "XML",
        ".yml" or ".yaml" => "YAML",
        ".md" => "Markdown",
        "" => "Text",
        var ext => ext.TrimStart('.').ToUpperInvariant()
    };

    public static string FormatBytes(long b)
    {
        if (b >= 1024L * 1024 * 1024) return $"{b / (1024.0 * 1024 * 1024):F1} GB";
        if (b >= 1024L * 1024) return $"{b / (1024.0 * 1024):F1} MB";
        if (b >= 1024L) return $"{b / 1024.0:F0} KB";
        return $"{b} B";
    }

    /// <summary>Icon for a row in the Files list and the editor header.</summary>
    public static string FileIconData(string path, bool isDir)
    {
        if (isDir) return LadsIcons.Folder;
        string ext = Path.GetExtension(path).ToLowerInvariant();
        if (TextFileCodec.IsImageExtension(path)) return LadsIcons.Image;
        return ext switch
        {
            ".jar" or ".zip" or ".mrpack" or ".gz" or ".7z" or ".rar" => LadsIcons.Archive,
            ".json" or ".json5" or ".toml" or ".xml" or ".yml" or ".yaml" or ".js" or ".mcmeta" or ".properties" or ".cfg" or ".ini" or ".conf" => LadsIcons.Code,
            ".txt" or ".log" or ".md" => LadsIcons.FileText,
            _ => LadsIcons.File
        };
    }

    public static IBrush FileIconBrush(string path, bool isDir)
    {
        if (isDir) return new SolidColorBrush(Color.Parse("#E0B45A"));
        string data = FileIconData(path, false);
        string color = data == LadsIcons.Image ? "#6FBF8E"
            : data == LadsIcons.Archive ? "#B48CE0"
            : data == LadsIcons.Code ? "#6FA8DC"
            : data == LadsIcons.FileText ? "#C9CBD3"
            : "#8F919C";
        return new SolidColorBrush(Color.Parse(color));
    }
}
