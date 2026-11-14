using System.Text.Json.Nodes;
using SkiaSharp;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>The Friends page's chat, pictures and party.</summary>
public class FriendsChatTests : IDisposable
{
    private const string Me = "11111111-1111-1111-1111-111111111111", Bob = "01234567-89ab-cdef-0123-456789abcdef";
    private readonly string folder = Path.Combine(Path.GetTempPath(), "lads-chat-" + Guid.NewGuid().ToString("N"));

    public void Dispose()
    {
        if (Directory.Exists(folder)) Directory.Delete(folder, true);
    }

    [Fact] public void HistoryMergesWithoutDoublesCountsUnreadAndIsKept()
    {
        var chat = new FriendsChat(Me, folder);
        var hi = new FriendsService.Message(Bob, "hi", 10, null, null);
        Assert.True(chat.Merge(Bob, new[] { hi, new FriendsService.Message(Me, "yo", 20, null, null) }));
        Assert.False(chat.Merge(Bob, new[] { hi }));
        Assert.True(chat.Merge(Bob, new[] { new FriendsService.Message(Bob, "gif", 5, null, "https://media.tenor.com/x/a.gif") }));
        Assert.Equal(new[] { "gif", "hi", "yo" }, chat.Get(Bob).Select(m => m.Text));
        Assert.Equal(2, chat.Unread(Bob));
        chat.MarkRead(Bob);
        Assert.Equal(0, chat.Unread(Bob));
        chat.Save();

        var again = new FriendsChat(Me, folder);
        Assert.Equal(3, again.Get(Bob).Count);
        Assert.Equal("https://media.tenor.com/x/a.gif", again.Get(Bob)[0].Gif);
        Assert.Equal(0, again.Unread(Bob));
    }

    [Fact] public void LinkKeepsDmsAndTheParty()
    {
        using var friends = new FriendsService(new HttpClient(), _ => { });
        friends.Handle(new JsonObject { ["t"] = "friend_added", ["uuid"] = Bob, ["name"] = "Bob" });
        friends.Handle(new JsonObject { ["t"] = "dm", ["from"] = Bob.Replace("-", ""), ["text"] = "look", ["ts"] = 42L, ["img"] = "abcdefghijklmnopqrstuv" });
        friends.Handle(new JsonObject { ["t"] = "dm", ["from"] = "99999999-9999-9999-9999-999999999999", ["text"] = "stranger" });
        friends.Handle(new JsonObject { ["t"] = "party_invite_in", ["from"] = Bob, ["name"] = "Bob" });
        Assert.Equal("Bob", Assert.Single(friends.Current.PartyInvites).Name);
        friends.Handle(new JsonObject
        {
            ["t"] = "party_state", ["leader"] = Bob,
            ["members"] = new JsonArray(new JsonObject { ["id"] = Bob, ["name"] = "Bob" }, new JsonObject { ["id"] = Me, ["name"] = "Me" }),
        });
        friends.Handle(new JsonObject { ["t"] = "party_warp", ["from"] = Bob, ["server"] = "mc.arashyt.ca", ["name"] = "Lads" });
        friends.Handle(new JsonObject { ["t"] = "error", ["code"] = "rate", ["ref"] = Bob });

        var snap = friends.Current;
        var dm = Assert.Single(Assert.Single(snap.Chats).Value);
        Assert.Equal((Bob, "look", 42L, "abcdefghijklmnopqrstuv"), (dm.From, dm.Text, dm.Ts, dm.Img));
        Assert.Empty(snap.PartyInvites); // joined: the invite is answered
        Assert.Equal(Bob, snap.Party!.Leader);
        Assert.Equal(new[] { "Bob", "Me" }, snap.Party.Members.Select(m => m.Name));
        Assert.Equal(new FriendsService.PartyWarp(Bob, "mc.arashyt.ca", "Lads"), snap.Warp);
        Assert.Contains("Slow down", snap.Error);

        friends.Handle(new JsonObject { ["t"] = "party_ended" });
        Assert.Null(friends.Current.Party);
        Assert.Null(friends.Current.Warp);
    }

    [Fact] public void GameSnapshotCarriesChatsPartyAndSelf()
    {
        string game = Path.Combine(folder, "game");
        Directory.CreateDirectory(Path.Combine(game, FriendsService.Folder));
        File.WriteAllText(Path.Combine(game, FriendsService.Folder, FriendsService.SnapshotFile), new JsonObject
        {
            ["connected"] = true, ["self"] = Me,
            ["friends"] = new JsonArray(new JsonObject
            {
                ["uuid"] = Bob, ["name"] = "Bob", ["presence"] = new JsonObject { ["state"] = "menu" },
                ["dms"] = new JsonArray(new JsonObject { ["from"] = Me, ["text"] = "sup", ["ts"] = 7L }),
            }),
            ["party"] = new JsonObject { ["leader"] = Me, ["members"] = new JsonArray(new JsonObject { ["id"] = Me, ["name"] = "Me" }) },
            ["partyInvites"] = new JsonArray(new JsonObject { ["from"] = Bob, ["name"] = "Bob" }),
        }.ToJsonString());

        var snap = FriendsService.ReadGame(game)!;
        Assert.Equal(Me, snap.Self);
        Assert.Equal("sup", Assert.Single(snap.Chats[Bob]).Text);
        Assert.Equal(Me, snap.Party!.Leader);
        Assert.Equal(Bob, Assert.Single(snap.PartyInvites).From);
    }

    [Theory]
    [InlineData("https://media.tenor.com/abc/dance.gif", true)]
    [InlineData("https://giphy.com/gifs/cat-funny-AbCdE12345", true)]
    [InlineData("https://tenor.com/view/dance-gif-123", true)]
    [InlineData("https://evil.example/a.gif", false)]
    [InlineData("look https://media.tenor.com/abc/dance.gif", false)]
    public void GifLinksAreOnlyTenorOrGiphy(string text, bool gif) => Assert.Equal(gif, FriendsMedia.GifLink(text) != null);

    [Fact] public async Task GiphyPagesResolveWithoutAFetch() =>
        Assert.Equal("https://i.giphy.com/AbCdE12345.gif", await FriendsMedia.ResolveGifAsync(new HttpClient(), "https://giphy.com/gifs/cat-funny-AbCdE12345"));

    [Fact] public void PicturesBecomeAJpegUnderTheCapInParts()
    {
        using var bitmap = new SKBitmap(3000, 2000);
        var random = new Random(1);
        for (int y = 0; y < 2000; y += 4)
            for (int x = 0; x < 3000; x += 4) bitmap.SetPixel(x, y, new SKColor((byte)random.Next(256), (byte)random.Next(256), (byte)random.Next(256)));
        using var png = SKImage.FromBitmap(bitmap).Encode(SKEncodedImageFormat.Png, 100);
        var jpeg = FriendsMedia.EncodePicture(png.ToArray())!;
        Assert.True(jpeg.Length <= FriendsService.MaxPicture);
        Assert.Equal(new byte[] { 0xFF, 0xD8, 0xFF }, jpeg[..3]);
        using (var decoded = SKBitmap.Decode(jpeg)) Assert.True(Math.Max(decoded.Width, decoded.Height) <= 1920);
        var parts = FriendsService.PictureParts(jpeg);
        Assert.All(parts, p => Assert.True(p.Length <= FriendsService.PicturePart));
        Assert.Equal(jpeg, Convert.FromBase64String(string.Concat(parts)));
        Assert.Null(FriendsMedia.EncodePicture(new byte[] { 1, 2, 3 }));
    }

    [Fact] public void OnlyDmMediaCacheFilesAreShownLocally()
    {
        string cache = Path.Combine(folder, "cache", "dm-media");
        Directory.CreateDirectory(cache);
        File.WriteAllBytes(Path.Combine(cache, "sent-1.jpg"), new byte[] { 1 });
        File.WriteAllBytes(Path.Combine(folder, "secret.jpg"), new byte[] { 1 });
        Assert.NotNull(FriendsMedia.LocalPicture("file:" + Path.Combine(cache, "sent-1.jpg")));
        Assert.Null(FriendsMedia.LocalPicture("file:" + Path.Combine(folder, "secret.jpg")));
        Assert.Null(FriendsMedia.LocalPicture("file:" + Path.Combine(cache, "..", "..", "secret.jpg")));
    }
}

public class FriendsGifTests
{
    /// <summary>A 1x1 GIF with two frames: black, then white (100 ms each).</summary>
    private static readonly byte[] TwoFrames =
    {
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 1, 0, 1, 0, 0x80, 0, 0, 0, 0, 0, 0xFF, 0xFF, 0xFF,
        0x21, 0xF9, 4, 0, 10, 0, 0, 0, 0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 1, 0,
        0x21, 0xF9, 4, 0, 10, 0, 0, 0, 0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x4C, 1, 0,
        0x3B,
    };

    [Fact] public void EveryGifFrameIsDecoded()
    {
        using (var codec = SKCodec.Create(new SKMemoryStream(TwoFrames)))
            Assert.Equal((2, 2), (codec.FrameCount, codec.FrameInfo.Length));
        var frames = FriendsMedia.DecodeFrames(TwoFrames)!;
        Assert.Equal(2, frames.Count);
        Assert.All(frames, f => Assert.Equal(100, f.Delay));
        var colors = frames.Select(f => { using var b = SKBitmap.Decode(f.Png); return b.GetPixel(0, 0); }).ToList();
        Assert.Equal(SKColors.Black, colors[0]);
        Assert.Equal(SKColors.White, colors[1]);
    }
}
