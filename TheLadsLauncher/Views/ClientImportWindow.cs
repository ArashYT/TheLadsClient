using TheLadsLauncher.Controls;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Platform.Storage;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

/// <summary>Settings → Import from another client: the clients found, a preview per category, Import (with a backup) and Undo.</summary>
public sealed class ClientImportWindow : Window
{
    private readonly ImportTargets _targets;
    private readonly LauncherSettings _settings;
    private readonly Action _settingsChanged;
    private readonly StackPanel _clients = new() { Spacing = 6 };
    private readonly StackPanel _categories = new() { Spacing = 6 };
    private readonly StackPanel _preview = new() { Spacing = 2 };
    private readonly TextBlock _note = new() { Foreground = Brush("#A0A1AA"), FontSize = 12, TextWrapping = TextWrapping.Wrap };
    private readonly TextBlock _status = new() { Foreground = Brush("#A4BAA7"), FontSize = 12, TextWrapping = TextWrapping.Wrap, VerticalAlignment = VerticalAlignment.Center };
    private readonly Button _import = new() { Content = "Import", Classes = { "action" }, IsEnabled = false };
    private bool _undoArmed;
    private readonly Button _undo = new() { Content = "Undo last import", Classes = { "action" }, IsEnabled = false };
    private readonly List<(ImportCategory Category, CheckBox Box)> _boxes = new();
    private readonly ImportEnvironment _environment;
    private ImportPlan? _plan;

    public ClientImportWindow(ImportTargets targets, LauncherSettings settings, Action settingsChanged, ImportEnvironment? environment = null)
    {
        _environment = environment ?? ImportEnvironment.Current;
        _targets = targets;
        _settings = settings;
        _settingsChanged = settingsChanged;
        Title = "Import from another client";
        Width = 820;
        Height = 620;
        MinWidth = 620;
        MinHeight = 440;
        WindowStartupLocation = WindowStartupLocation.CenterOwner;
        Background = Brush("#1D1E22");

        var chooseFolder = new Button { Content = "Choose Folder...", Classes = { "action" }, HorizontalAlignment = HorizontalAlignment.Stretch };
        Ico.SetName(chooseFolder, "folder-open");
        ToolTip.SetTip(chooseFolder, "Pick a .minecraft, Lunar, Badlion, Feather, LabyMod, Modrinth, CurseForge, Prism, MultiMC or custom instance folder.");
        chooseFolder.Click += async (_, _) => await PickFolder();
        var left = new StackPanel { Spacing = 10, Width = 210, Children =
        {
            Label("CLIENTS FOUND"), _clients, chooseFolder,
            new TextBlock { Text = "Other clients' folders are only read. Accounts and logins are never touched.", Foreground = Brush("#868994"), FontSize = 11, TextWrapping = TextWrapping.Wrap }
        } };

        var right = new Grid { RowDefinitions = new RowDefinitions("Auto,Auto,*"), Margin = new Thickness(18, 0, 0, 0) };
        right.Children.Add(new StackPanel { Spacing = 8, Children = { Label("WHAT TO IMPORT"), _note } });
        Grid.SetRow(_categories, 1);
        _categories.Margin = new Thickness(0, 10);
        right.Children.Add(_categories);
        var scroll = new ScrollViewer { Content = _preview, Background = Brush("#17181B") };
        Grid.SetRow(scroll, 2);
        right.Children.Add(scroll);
        var body = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*") };
        body.Children.Add(left);
        Grid.SetColumn(right, 1);
        body.Children.Add(right);

        var close = new Button { Content = "Close", Classes = { "action" } };
        Ico.SetName(close, "cross");
        close.Click += (_, _) => Close();
        _import.Click += async (_, _) => await ImportChosen();
        _undo.Click += async (_, _) => await UndoLast();
        var footer = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), Margin = new Thickness(0, 14, 0, 0) };
        footer.Children.Add(_status);
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Children = { _undo, _import, close } };
        Grid.SetColumn(buttons, 1);
        footer.Children.Add(buttons);

        var root = new Grid { RowDefinitions = new RowDefinitions("Auto,*,Auto"), Margin = new Thickness(24) };
        root.Children.Add(new TextBlock { Text = "Import from another client", Foreground = Brushes.White, FontSize = 18, FontWeight = FontWeight.Bold, Margin = new Thickness(0, 0, 0, 14) });
        Grid.SetRow(body, 1);
        root.Children.Add(body);
        Grid.SetRow(footer, 2);
        root.Children.Add(footer);
        Content = root;

        Opened += (_, _) => Refresh();
    }

    private void Refresh()
    {
        _clients.Children.Clear();
        _undo.IsEnabled = ClientImport.LatestBackup(_targets) != null;
        var found = ClientImport.Detect(_environment);
        if (found.Count == 0) _clients.Children.Add(new TextBlock { Text = "No other client was found.", Foreground = Brush("#A0A1AA"), FontSize = 12, TextWrapping = TextWrapping.Wrap });
        foreach (var source in found) _clients.Children.Add(MakeClientRow(source));
        if (_clients.Children.OfType<RadioButton>().FirstOrDefault() is { } first) first.IsChecked = true;
    }

    private RadioButton MakeClientRow(ImportSource source)
    {
        var row = new RadioButton { GroupName = "client", Content = source.Basic ? source.Name + " (basic)" : source.Name, Foreground = Brushes.White };
        row.IsCheckedChanged += async (_, _) => { if (row.IsChecked == true) await Select(source); };
        return row;
    }

    private async Task PickFolder()
    {
        var folders = await StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions { Title = "Choose client or .minecraft folder", AllowMultiple = false });
        if (folders.Count == 0 || folders[0].TryGetLocalPath() is not { } path) return;
        await ChooseCustomFolder(path);
    }

    public async Task ChooseCustomFolder(string path)
    {
        ImportSource source;
        try { source = ClientImport.DetectCustomFolder(path); }
        catch (ArgumentException e) { _status.Text = e.Message; return; } // a network path
        foreach (var existing in _clients.Children.OfType<TextBlock>().ToList()) _clients.Children.Remove(existing);
        foreach (var row in _clients.Children.OfType<RadioButton>()) row.IsChecked = false;
        var customRow = MakeClientRow(source);
        _clients.Children.Insert(0, customRow);
        customRow.IsChecked = true;
        await Task.CompletedTask;
    }

    private async Task Select(ImportSource source)
    {
        _undoArmed = false; // the warning must be on screen for the second click to count
        _import.IsEnabled = false;
        _status.Text = $"Reading {source.Name}…";
        var targets = _targets;
        _plan = await Task.Run(() => ClientImport.Plan(source, targets));
        _status.Text = "";
        ShowPlan();
    }

    private void ShowPlan()
    {
        if (_plan == null) return;
        _note.Text = _plan.Source.Note;
        _categories.Children.Clear();
        _boxes.Clear();
        foreach (var category in _plan.Categories.Values)
        {
            string summary = category.Problem != null ? category.Problem : $"{category.Changes.Count} to change, {category.SkippedCount} skipped";
            var box = new CheckBox
            {
                Content = $"{CategoryTitle(category.Category)}: {summary}",
                IsChecked = category.Problem == null && category.Changes.Count > 0,
                IsEnabled = category.Problem == null && category.Changes.Count > 0
            };
            box.IsCheckedChanged += (_, _) => _import.IsEnabled = _boxes.Any(b => b.Box.IsChecked == true);
            _boxes.Add((category.Category, box));
            _categories.Children.Add(box);
        }
        _import.IsEnabled = _boxes.Any(b => b.Box.IsChecked == true);
        _preview.Children.Clear();
        foreach (var category in _plan.Categories.Values.Where(c => c.Changes.Count + c.SkippedCount > 0))
        {
            Add(CategoryTitle(category.Category).ToUpperInvariant(), "#A0A1AA");
            foreach (var line in category.Changes.Take(300)) Add("+ " + line, "#CCCCCC");
            foreach (var line in category.Skipped.Take(300)) Add("skipped: " + line, "#868994");
        }
        void Add(string text, string color) => _preview.Children.Add(new TextBlock { Text = text, Foreground = Brush(color), FontSize = 11, Margin = new Thickness(8, 1), TextWrapping = TextWrapping.Wrap });
    }

    private async Task ImportChosen()
    {
        if (_plan == null) return;
        var plan = _plan;
        var chosen = _boxes.Where(b => b.Box.IsChecked == true).Select(b => b.Category).ToList();
        _import.IsEnabled = false;
        _status.Text = "Importing…";
        var settings = _settings;
        var result = await Task.Run(() => ClientImport.Apply(plan, chosen, _targets, settings));
        _settingsChanged();
        string applied = result.Applied.Count == 0 ? "Nothing changed." : "Imported " + string.Join(", ", result.Applied.Select(a => $"{a.Value} {CategoryTitle(a.Key).ToLowerInvariant()}"));
        _status.Text = applied + (result.Errors.Count > 0 ? " Problems: " + string.Join(" ", result.Errors) : "") + (result.RestartNeeded ? " A running game takes options at its next start." : "");
        _undo.IsEnabled = ClientImport.LatestBackup(_targets) != null;
        await Select(plan.Source);
        if (result.Applied.Count > 0 || result.Errors.Count > 0)
            _status.Text = applied + (result.Errors.Count > 0 ? " Problems: " + string.Join(" ", result.Errors) : "");
    }

    private async Task UndoLast()
    {
        if (ClientImport.LatestBackup(_targets) is not { } backup) return;
        if (!_undoArmed)
        {
            _undoArmed = true;
            _status.Text = "Undo puts your servers, options and settings back as they were before the import. Anything you changed since is lost. Click Undo again to go ahead.";
            return;
        }
        _undoArmed = false;
        var settings = _settings;
        var result = await Task.Run(() => ClientImport.Undo(backup, _targets, settings));
        _settingsChanged();
        _undo.IsEnabled = ClientImport.LatestBackup(_targets) != null;
        if (_plan != null) await Select(_plan.Source);
        _status.Text = result.Errors.Count == 0 ? "The last import was undone." : "Undone, with problems: " + string.Join(" ", result.Errors);
    }

    private static string CategoryTitle(ImportCategory category) => category switch
    {
        ImportCategory.Options => "Keybinds, video and audio",
        ImportCategory.Servers => "Server list",
        ImportCategory.Packs => "Resource packs",
        ImportCategory.Mods => "Mods and HUD",
        _ => "Launcher settings"
    };

    private static TextBlock Label(string text) => new() { Text = text, Foreground = Brush("#FF4D4D"), FontSize = 12, FontWeight = FontWeight.Bold, LetterSpacing = 1 };
    private static IBrush Brush(string color) => new SolidColorBrush(Color.Parse(color));
}
