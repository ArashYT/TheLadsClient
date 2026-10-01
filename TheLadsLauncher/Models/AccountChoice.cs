using Avalonia.Media.Imaging;
using TheLadsLauncher.ViewModels;

namespace TheLadsLauncher.Models;

/// <summary>One entry of the Play screen's "Playing as" picker: the account and its skin head, which loads after the list shows.</summary>
public sealed class AccountChoice : ViewModelBase
{
    private Bitmap? _head;

    public AccountChoice(string name, string label) { Name = name; Label = label; }

    public string Name { get; }
    public string Label { get; }
    public Bitmap? Head { get => _head; set => SetProperty(ref _head, value); }
}
