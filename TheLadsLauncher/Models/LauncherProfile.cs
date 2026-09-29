using System;
using System.Text.Json.Serialization;

namespace TheLadsLauncher.Models;

public class LauncherProfile
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public string Name { get; set; } = "Default Profile";
    public string MinecraftVersion { get; set; } = "26.2";
    public string? FabricVersion { get; set; } = "0.19.3";
    public int JavaMajorVersion { get; set; } = 25;
    public string? CustomJavaPath { get; set; }
    public bool IsIsolated { get; set; } = false;
    public string? CustomGameDir { get; set; }
    public string? PackwizUrl { get; set; }
    public DateTime? LastPlayed { get; set; }
    public string IconKey { get; set; } = "default";

    public override string ToString() => $"{Name} ({MinecraftVersion})";
}
