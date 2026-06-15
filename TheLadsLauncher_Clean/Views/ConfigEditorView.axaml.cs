using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Avalonia.Controls;
using Avalonia.Interactivity;

namespace TheLadsLauncher.Views;

public class ConfigFileItem
{
    public string DisplayName { get; set; } = "";
    public string FullPath { get; set; } = "";
}

public partial class ConfigEditorView : UserControl
{
    private string? _currentFilePath;

    public ConfigEditorView()
    {
        InitializeComponent();
        LoadFileList();
    }

    private void LoadFileList()
    {
        var items = new List<ConfigFileItem>();

        // Launcher Settings
        string launcherSettings = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "settings.json");
        if (File.Exists(launcherSettings))
        {
            items.Add(new ConfigFileItem { DisplayName = "Launcher Settings (settings.json)", FullPath = launcherSettings });
        }

        // Instance Configs
        var settings = LauncherSettings.Load();
        if (!string.IsNullOrEmpty(settings.InstancePath) && Directory.Exists(settings.InstancePath))
        {
            string optionsTxt = Path.Combine(settings.InstancePath, "options.txt");
            if (File.Exists(optionsTxt))
            {
                items.Add(new ConfigFileItem { DisplayName = "options.txt", FullPath = optionsTxt });
            }
            
            string log4j = Path.Combine(settings.InstancePath, "log4j2.xml");
            if (File.Exists(log4j))
            {
                items.Add(new ConfigFileItem { DisplayName = "log4j2.xml", FullPath = log4j });
            }

            string configDir = Path.Combine(settings.InstancePath, "config");
            if (Directory.Exists(configDir))
            {
                var configFiles = Directory.GetFiles(configDir, "*.*", SearchOption.AllDirectories)
                    .Where(f => f.EndsWith(".json") || f.EndsWith(".properties") || f.EndsWith(".toml") || f.EndsWith(".txt"));
                
                foreach (var file in configFiles)
                {
                    string relative = Path.GetRelativePath(settings.InstancePath, file);
                    items.Add(new ConfigFileItem { DisplayName = relative, FullPath = file });
                }
            }
        }

        ConfigFilesList.ItemsSource = items;
    }

    private void ConfigFilesList_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (ConfigFilesList.SelectedItem is ConfigFileItem item)
        {
            _currentFilePath = item.FullPath;
            CurrentFileText.Text = $"Editing: {item.DisplayName}";
            LoadFileContent();
        }
    }

    private void LoadFileContent()
    {
        if (_currentFilePath != null && File.Exists(_currentFilePath))
        {
            try
            {
                EditorTextBox.Text = File.ReadAllText(_currentFilePath);
            }
            catch (Exception ex)
            {
                EditorTextBox.Text = $"Error loading file:\n{ex.Message}";
            }
        }
    }

    private void RefreshFilesBtn_Click(object? sender, RoutedEventArgs e)
    {
        LoadFileList();
    }

    private void ReloadBtn_Click(object? sender, RoutedEventArgs e)
    {
        LoadFileContent();
    }

    private void SaveBtn_Click(object? sender, RoutedEventArgs e)
    {
        if (_currentFilePath != null)
        {
            try
            {
                File.WriteAllText(_currentFilePath, EditorTextBox.Text);
                SaveBtn.Content = "💾 Saved!";
                var timer = new System.Timers.Timer(2000) { AutoReset = false };
                timer.Elapsed += (s, ev) => 
                {
                    Avalonia.Threading.Dispatcher.UIThread.InvokeAsync(() => { SaveBtn.Content = "💾 Save"; });
                };
                timer.Start();
            }
            catch (Exception ex)
            {
                CurrentFileText.Text = $"Error saving: {ex.Message}";
            }
        }
    }
}
