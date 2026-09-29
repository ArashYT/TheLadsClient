using Avalonia.Controls;
using Avalonia.Input.Platform;
using Avalonia.Interactivity;
using System;
using System.Diagnostics;
using Microsoft.Identity.Client;

namespace TheLadsLauncher.Views;

public partial class DeviceCodeLoginDialog : Window
{
    private readonly DeviceCodeResult _result;
    private readonly Action? _onCancel;
    private bool _completed;
    private bool _cancelRequested;

    public DeviceCodeLoginDialog()
    {
        InitializeComponent();
        _result = null!;
    }

    public DeviceCodeLoginDialog(DeviceCodeResult result, Action? onCancel = null)
    {
        InitializeComponent();
        _result = result;
        _onCancel = onCancel;

        UserCodeText.Text = result.UserCode;

        // Auto copy to clipboard on open
        Opened += async (_, _) => await CopyTextToClipboard(result.UserCode);
    }

    protected override void OnClosing(WindowClosingEventArgs e)
    {
        base.OnClosing(e);
        if (!_completed && !_cancelRequested && !e.Cancel)
        {
            _cancelRequested = true;
            _onCancel?.Invoke();
        }
    }

    public void Complete()
    {
        _completed = true;
        Close();
    }

    private async System.Threading.Tasks.Task CopyTextToClipboard(string text)
    {
        try
        {
            if (Clipboard != null) await Clipboard.SetTextAsync(text);
        }
        catch { }
    }

    private async void CopyCodeOnly_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            await CopyTextToClipboard(_result.UserCode);
            CopyCodeOnlyBtn.Content = "✓ Copied";
        }
        catch { }
    }

    private async void OpenBrowser_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            await CopyTextToClipboard(_result.UserCode);

            string url = !string.IsNullOrWhiteSpace(_result.VerificationUrl) 
                ? _result.VerificationUrl 
                : "https://microsoft.com/link";

            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        }
        catch { }
    }

    private void Cancel_Click(object? sender, RoutedEventArgs e)
    {
        Close();
    }
}
