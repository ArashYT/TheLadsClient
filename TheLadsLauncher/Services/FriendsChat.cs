using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace TheLadsLauncher.Services;

/// <summary>The Friends page's chat history, per account (friends-chat-&lt;uuid&gt;.json): the backend keeps DMs only until they are
/// delivered, so the page keeps what the launcher's link and the running game saw, and what was read.</summary>
public sealed class FriendsChat
{
    public const int PerFriend = 300;
    private readonly Dictionary<string, List<FriendsService.Message>> _chats = new();
    private readonly Dictionary<string, long> _read = new();
    private readonly string? _file;

    /// <summary>Goes up with every change (the page redraws on it).</summary>
    public int Version { get; private set; }
    public string Self { get; }

    public FriendsChat(string self, string? folder)
    {
        Self = self;
        if (folder == null) return;
        _file = Path.Combine(folder, $"friends-chat-{self.Replace("-", "")}.json");
        try
        {
            if (!File.Exists(_file) || new FileInfo(_file).Length > 20_000_000 || JsonNode.Parse(File.ReadAllText(_file)) is not JsonObject o) return;
            foreach (var (friend, node) in o["chats"] as JsonObject ?? new())
                if (node is JsonArray arr)
                    _chats[friend] = arr.OfType<JsonObject>().Select(FriendsService.ParseMessage).OfType<FriendsService.Message>().ToList();
            foreach (var (friend, node) in o["read"] as JsonObject ?? new())
                if (node is JsonValue v && v.TryGetValue<long>(out var ts)) _read[friend] = ts;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException or InvalidOperationException) { }
    }

    public IReadOnlyList<FriendsService.Message> Get(string friend) =>
        _chats.TryGetValue(friend, out var list) ? list : Array.Empty<FriendsService.Message>();

    /// <summary>Adds the messages not kept yet (same sender, time and text); true when any was new.</summary>
    public bool Merge(string friend, IEnumerable<FriendsService.Message> messages)
    {
        if (!_chats.TryGetValue(friend, out var list)) list = new List<FriendsService.Message>();
        var known = list.Select(m => (m.From, m.Ts, m.Text)).ToHashSet();
        int before = list.Count;
        foreach (var m in messages)
            if (known.Add((m.From, m.Ts, m.Text))) list.Add(m);
        if (list.Count == before) return false;
        list.Sort((a, b) => a.Ts.CompareTo(b.Ts));
        if (list.Count > PerFriend) list.RemoveRange(0, list.Count - PerFriend);
        _chats[friend] = list;
        Version++;
        return true;
    }

    /// <summary>Messages from this friend newer than the last one read.</summary>
    public int Unread(string friend) =>
        Get(friend).Count(m => m.From != Self && m.Ts > _read.GetValueOrDefault(friend));

    public int UnreadTotal(IEnumerable<string> friends) => friends.Sum(Unread);

    public void MarkRead(string friend)
    {
        var last = Get(friend).LastOrDefault();
        if (last == null || _read.GetValueOrDefault(friend) >= last.Ts) return;
        _read[friend] = last.Ts;
        Version++;
        Save();
    }

    public void Save()
    {
        if (_file == null) return;
        var chats = new JsonObject();
        foreach (var (friend, list) in _chats)
            chats[friend] = new JsonArray(list.Select(m =>
            {
                var o = new JsonObject { ["from"] = m.From, ["text"] = m.Text, ["ts"] = m.Ts };
                if (m.Img != null) o["img"] = m.Img;
                if (m.Gif != null) o["gif"] = m.Gif;
                return (JsonNode)o;
            }).ToArray());
        var read = new JsonObject();
        foreach (var (friend, ts) in _read) read[friend] = ts;
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(_file)!);
            string tmp = _file + ".tmp";
            File.WriteAllText(tmp, new JsonObject { ["v"] = 1, ["chats"] = chats, ["read"] = read }.ToJsonString());
            File.Move(tmp, _file, overwrite: true);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
    }
}
