using Avalonia.Controls;
using Avalonia.Interactivity;
using System;
using System.Diagnostics;
using Microsoft.Identity.Client;

namespace TheLadsLauncher.Views;

public partial class DeviceCodeLoginDialog : Window
{
    private readonly DeviceCodeResult _result;
    private readonly Action? _onCancel;

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
        CopyTextToClipboard(result.UserCode);
    }

    protected override void OnClosing(WindowClosingEventArgs e)
    {
        base.OnClosing(e);
        try { _onCancel?.Invoke(); } catch { }
    }

    private void CopyTextToClipboard(string text)
    {

        try
        {
            var p = new Process();
            p.StartInfo.FileName = "clip.exe";
            p.StartInfo.UseShellExecute = false;
            p.StartInfo.RedirectStandardInput = true;
            p.StartInfo.CreateNoWindow = true;
            p.Start();
            p.StandardInput.Write(text);
            p.StandardInput.Close();
            p.WaitForExit(500);
        }
        catch { }
    }

    private void CopyCodeOnly_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            CopyTextToClipboard(_result.UserCode);
            CopyCodeOnlyBtn.Content = "✓ Copied";
        }
        catch { }
    }

    private void OpenBrowser_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            CopyTextToClipboard(_result.UserCode);

            string url = !string.IsNullOrWhiteSpace(_result.VerificationUrl) 
                ? _result.VerificationUrl 
                : "https://microsoft.com/link";

            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        }
        catch { }
    }

    private void Cancel_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            _onCancel?.Invoke();
        }
        catch { }
        Close();
    }
}
