using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using System;

namespace TheLadsLauncher.Views;

public partial class AddOfflineAccountDialog : Window
{
    public string? ResultUsername { get; private set; }

    public AddOfflineAccountDialog()
    {
        InitializeComponent();
        UsernameBox.Focus();
    }

    private void Add_Click(object? sender, RoutedEventArgs e)
    {
        Submit();
    }

    private void Cancel_Click(object? sender, RoutedEventArgs e)
    {
        ResultUsername = null;
        Close(null);
    }

    private void UsernameBox_KeyDown(object? sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter)
        {
            Submit();
        }
        else if (e.Key == Key.Escape)
        {
            Close(null);
        }
    }

    private void Submit()
    {
        string username = (UsernameBox.Text ?? "").Trim();
        if (string.IsNullOrWhiteSpace(username))
        {
            ErrorLabel.Text = "Please enter a valid username.";
            ErrorLabel.IsVisible = true;
            return;
        }

        if (username.Length < 3 || username.Length > 16)
        {
            ErrorLabel.Text = "Minecraft username must be between 3 and 16 characters.";
            ErrorLabel.IsVisible = true;
            return;
        }

        ResultUsername = username;
        Close(username);
    }
}
