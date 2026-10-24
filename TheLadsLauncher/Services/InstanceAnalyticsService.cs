using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;

namespace TheLadsLauncher.Services;

public sealed class InstanceAnalyticsRecord
{
    public string InstanceId { get; set; } = "";
    public int LaunchCount { get; set; }
    public long TotalPlaySeconds { get; set; }
    public long LastSessionDurationSeconds { get; set; }
    public DateTime? LastPlayedUtc { get; set; }
}

/// <summary>
/// Tracks per-instance and per-profile playtime and launch analytics across game sessions.
/// </summary>
public static class InstanceAnalyticsService
{
    private static readonly ConcurrentDictionary<string, InstanceAnalyticsRecord> Cache = new(StringComparer.OrdinalIgnoreCase);
    private static readonly object FileGate = new();

    public static string AnalyticsFilePath(string dataOrGameDirectory) =>
        Path.Combine(dataOrGameDirectory, "instance-analytics.json");

    public static InstanceAnalyticsRecord GetAnalytics(string dataOrGameDirectory, string instanceId)
    {
        string key = $"{dataOrGameDirectory}::{instanceId}";
        if (Cache.TryGetValue(key, out var cached)) return cached;

        var loaded = LoadRecord(dataOrGameDirectory, instanceId);
        Cache[key] = loaded;
        return loaded;
    }

    public static void RecordLaunch(string dataOrGameDirectory, string instanceId)
    {
        var record = GetAnalytics(dataOrGameDirectory, instanceId);
        record.LaunchCount++;
        record.LastPlayedUtc = DateTime.UtcNow;
        SaveRecord(dataOrGameDirectory, record);
    }

    public static void RecordSessionExit(string dataOrGameDirectory, string instanceId, TimeSpan sessionDuration)
    {
        var record = GetAnalytics(dataOrGameDirectory, instanceId);
        long seconds = Math.Max(0, (long)sessionDuration.TotalSeconds);
        record.TotalPlaySeconds += seconds;
        record.LastSessionDurationSeconds = seconds;
        record.LastPlayedUtc = DateTime.UtcNow;
        SaveRecord(dataOrGameDirectory, record);
    }

    public static string FormatPlayTime(long totalSeconds)
    {
        if (totalSeconds < 60) return $"{totalSeconds}s";
        var span = TimeSpan.FromSeconds(totalSeconds);
        if (span.TotalHours >= 1)
            return $"{(int)span.TotalHours}h {span.Minutes}m";
        return $"{span.Minutes}m";
    }

    private static InstanceAnalyticsRecord LoadRecord(string directory, string instanceId)
    {
        string path = AnalyticsFilePath(directory);
        if (File.Exists(path))
        {
            try
            {
                lock (FileGate)
                {
                    string json = File.ReadAllText(path);
                    var dict = JsonSerializer.Deserialize<Dictionary<string, InstanceAnalyticsRecord>>(json);
                    if (dict != null && dict.TryGetValue(instanceId, out var rec) && rec != null)
                        return rec;
                }
            }
            catch { }
        }

        return new InstanceAnalyticsRecord { InstanceId = instanceId };
    }

    private static void SaveRecord(string directory, InstanceAnalyticsRecord record)
    {
        string path = AnalyticsFilePath(directory);
        lock (FileGate)
        {
            try
            {
                Dictionary<string, InstanceAnalyticsRecord> dict;
                if (File.Exists(path))
                {
                    try
                    {
                        dict = JsonSerializer.Deserialize<Dictionary<string, InstanceAnalyticsRecord>>(File.ReadAllText(path))
                               ?? new Dictionary<string, InstanceAnalyticsRecord>(StringComparer.OrdinalIgnoreCase);
                    }
                    catch
                    {
                        dict = new Dictionary<string, InstanceAnalyticsRecord>(StringComparer.OrdinalIgnoreCase);
                    }
                }
                else
                {
                    dict = new Dictionary<string, InstanceAnalyticsRecord>(StringComparer.OrdinalIgnoreCase);
                }

                dict[record.InstanceId] = record;
                var dir = Path.GetDirectoryName(path);
                if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
                File.WriteAllText(path, JsonSerializer.Serialize(dict, new JsonSerializerOptions { WriteIndented = true }));
            }
            catch { }
        }
    }
}
