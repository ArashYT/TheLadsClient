using System.Text.Json.Nodes;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class FriendsServiceTests : IDisposable
{
    private readonly string game = Path.Combine(Path.GetTempPath(), "lads-friends-" + Guid.NewGuid().ToString("N"));

    public void Dispose()
    {
        if (Directory.Exists(game)) Directory.Delete(game, true);
    }

    [Fact] public void ServerIdIsMinecraftsSignedHexDigest()
    {
        Assert.Equal("4ed1f46bbe04bc756bcb17c0c7ce3e4632f06a48", FriendsService.HexDigest("Notch"));
        Assert.Equal("-7c9d5b0044c130109a5d7b5fb5c317c02b4e28c1", FriendsService.HexDigest("jeb_"));
        Assert.Equal("88e16a1019277b15d58faf0541e11910eb756f6", FriendsService.HexDigest("simon"));
    }

    [Fact] public void LinkMessagesUpdateFriendsAndInvites()
    {
        using var friends = new FriendsService(new HttpClient(), _ => { });
        friends.Handle(new JsonObject { ["t"] = "friend_added", ["uuid"] = "0123456789abcdef0123456789abcdef", ["name"] = "Bob" });
        friends.Handle(new JsonObject
        {
            ["t"] = "presence", ["uuid"] = "01234567-89ab-cdef-0123-456789abcdef", ["state"] = "hosting",
            ["world"] = new JsonObject { ["name"] = "Base", ["scope"] = "friends", ["slots"] = 8, ["used"] = 1 },
        });
        friends.Handle(new JsonObject { ["t"] = "invite", ["inviteId"] = "abc123", ["from"] = "0123456789abcdef0123456789abcdef", ["world"] = new JsonObject { ["name"] = "Base" } });
        friends.Handle(new JsonObject { ["t"] = "invite", ["inviteId"] = "bad id!", ["from"] = "0123456789abcdef0123456789abcdef" });

        var snap = friends.Current;
        var bob = Assert.Single(snap.Friends);
        Assert.True(bob.Joinable);
        Assert.Equal("Hosting Base (1/8)", FriendsService.Describe(bob));
        var invite = Assert.Single(snap.Invites);
        Assert.Equal(("abc123", "01234567-89ab-cdef-0123-456789abcdef", "Base"), (invite.InviteId, invite.From, invite.World));

        friends.Handle(new JsonObject { ["t"] = "presence", ["uuid"] = bob.Uuid, ["state"] = "hosting",
            ["world"] = new JsonObject { ["name"] = "Base", ["scope"] = "invite", ["slots"] = 8, ["used"] = 1 } });
        Assert.False(Assert.Single(friends.Current.Friends).Joinable); // invite-only: no Join
        friends.Handle(new JsonObject { ["t"] = "friend_removed", ["uuid"] = bob.Uuid });
        Assert.Empty(friends.Current.Friends);
    }

    [Fact] public void GameListIsReadAndCommandsAreHandedOver()
    {
        Assert.Null(FriendsService.ReadGame(game));
        Directory.CreateDirectory(Path.Combine(game, FriendsService.Folder));
        File.WriteAllText(Path.Combine(game, FriendsService.Folder, FriendsService.SnapshotFile), """
            {"v":1,"connected":true,"canInvite":true,
             "friends":[{"uuid":"11111111-1111-1111-1111-111111111111","name":"Zed","presence":{"state":"offline"}},
                        {"uuid":"22222222-2222-2222-2222-222222222222","name":"Amy","presence":{"state":"server","server":"play.example.com"}}],
             "invites":[{"inviteId":"x1","from":"22222222-2222-2222-2222-222222222222","server":"play.example.com"}]}
            """);
        var snap = FriendsService.ReadGame(game)!;
        Assert.True(snap.FromGame && snap.Connected && snap.CanInvite);
        Assert.Equal(new[] { "Amy", "Zed" }, snap.Friends.Select(f => f.Name)); // online first
        Assert.Equal("Playing on play.example.com", FriendsService.Describe(snap.Friends[0]));
        Assert.Equal("play.example.com", Assert.Single(snap.Invites).Server);

        FriendsService.SendGame(game, new JsonObject { ["t"] = "invite", ["to"] = "22222222-2222-2222-2222-222222222222" });
        var sent = JsonNode.Parse(File.ReadAllText(Path.Combine(game, FriendsService.Folder, FriendsService.CommandFile)))!;
        Assert.Equal("invite", (string?)sent["t"]);
    }

    /// <summary>QA only (LADS_SOCIAL_URL = a dev backend on this PC with QaBob, cosmetics-server QA peer): the real link sees Bob's
    /// world and his invite.</summary>
    [Fact] public async Task DevBackendLinkShowsFriendsAndInvites()
    {
        if (Environment.GetEnvironmentVariable("LADS_SOCIAL_URL") is not { Length: > 0 }) return;
        var lines = new List<string>();
        using var friends = new FriendsService(new HttpClient(), l => { lock (lines) lines.Add(l); });
        friends.Start(_ => Task.FromResult<(string, string, string)?>(("QaLauncher", "1a1a1a1a-1a1a-1a1a-1a1a-1a1a1a1a1a1a", "x")));
        var end = DateTime.UtcNow.AddSeconds(30);
        while (DateTime.UtcNow < end && friends.Current.Invites.Count == 0) await Task.Delay(250);
        var snap = friends.Current;
        Console.WriteLine($"QA link: {snap.Status}; friends: {string.Join(", ", snap.Friends.Select(f => f.Shown + " = " + FriendsService.Describe(f)))}; invites: {snap.Invites.Count}");
        Assert.True(snap.Connected, string.Join(" | ", lines));
        var bob = Assert.Single(snap.Friends);
        Assert.True(bob.Joinable);
        Assert.Equal(bob.Uuid, Assert.Single(snap.Invites).From);

        // The game signs in with the same account: the launcher's link is replaced, does not come back, and the invite it
        // received is still there for the game to accept (the Friends page's Accept, handed over by -Dlads.social.invite)
        using var game = new System.Net.WebSockets.ClientWebSocket();
        await game.ConnectAsync(FriendsService.EndpointUri(), CancellationToken.None);
        var challenge = await Receive(game);
        await Send(game, new JsonObject { ["t"] = "auth", ["name"] = "QaLauncher", ["uuid"] = "1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a",
            ["serverId"] = FriendsService.ServerId((string)challenge["nonce"]!) });
        Assert.Equal("hello", (string?)(await Receive(game))["t"]);
        end = DateTime.UtcNow.AddSeconds(10);
        while (DateTime.UtcNow < end && friends.Running) await Task.Delay(100);
        Assert.False(friends.Running);
        Assert.Equal("Your game took over the friends link", friends.Current.Status);
        await Task.Delay(6000); // longer than the first retry: a launcher that reconnected would replace the game now
        Assert.Equal(System.Net.WebSockets.WebSocketState.Open, game.State);
        await Send(game, new JsonObject { ["t"] = "presence", ["state"] = "menu" });
        await Send(game, new JsonObject { ["t"] = "invite_accept", ["inviteId"] = snap.Invites[0].InviteId });
        JsonObject reply;
        do reply = await Receive(game); while ((string?)reply["t"] == "presence");
        Assert.Equal("p2p_start", (string?)reply["t"]);
    }

    private static async Task Send(System.Net.WebSockets.ClientWebSocket ws, JsonObject msg) =>
        await ws.SendAsync(System.Text.Encoding.UTF8.GetBytes(msg.ToJsonString()), System.Net.WebSockets.WebSocketMessageType.Text, true, CancellationToken.None);

    private static async Task<JsonObject> Receive(System.Net.WebSockets.ClientWebSocket ws)
    {
        var buffer = new byte[64 * 1024];
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        var result = await ws.ReceiveAsync(buffer, timeout.Token);
        return (JsonObject)JsonNode.Parse(System.Text.Encoding.UTF8.GetString(buffer, 0, result.Count))!;
    }
}
