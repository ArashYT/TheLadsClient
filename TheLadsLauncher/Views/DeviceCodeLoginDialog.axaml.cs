using Avalonia.Controls;
using Avalonia.Input.Platform;
using Avalonia.Interactivity;
using Avalonia.Media;
using System;
using System.Diagnostics;
using System.Linq;
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
        VerificationUrlText.Text = SafeVerificationUrl(result.VerificationUrl);

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

    public const string FallbackVerificationUrl = "https://microsoft.com/link";
    private static readonly string[] MicrosoftHosts = { "microsoft.com", "live.com", "microsoftonline.com" };

    /// <summary>The sign-in page from the device-code response when it is an https Microsoft page, else Microsoft's fixed one: a tampered
    /// response must not make the button shell-execute a file:// path or another protocol handler.</summary>
    public static string SafeVerificationUrl(string? url) =>
        Uri.TryCreate(url, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps
        && MicrosoftHosts.Any(h => uri.Host.Equals(h, StringComparison.OrdinalIgnoreCase) || uri.Host.EndsWith("." + h, StringComparison.OrdinalIgnoreCase))
            ? uri.AbsoluteUri : FallbackVerificationUrl;

    private async void OpenBrowser_Click(object? sender, RoutedEventArgs e)
    {
        await CopyTextToClipboard(_result.UserCode);
        try
        {
            Process.Start(new ProcessStartInfo(SafeVerificationUrl(_result.VerificationUrl)) { UseShellExecute = true });
        }
        catch (Exception)
        {
            // No browser opened: the link and code above still finish sign-in from any browser or device.
            StatusLabel.Text = "Could not open a browser. Open the link above yourself and enter the code.";
            StatusLabel.Foreground = new SolidColorBrush(Color.Parse("#FF6B6B"));
        }
    }

    private async void CopyLink_Click(object? sender, RoutedEventArgs e) =>
        await CopyTextToClipboard(SafeVerificationUrl(_result.VerificationUrl));

    private async void CopyCode_Click(object? sender, RoutedEventArgs e) => await CopyTextToClipboard(_result.UserCode);

    private void Cancel_Click(object? sender, RoutedEventArgs e)
    {
        Close();
    }
}
