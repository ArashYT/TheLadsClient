using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using Avalonia.Media.Imaging;
using SkiaSharp;

namespace TheLadsLauncher.Services;

/// <summary>Pictures and GIFs in the Friends page's chat, as the game's DmMedia: a screenshot goes as a JPEG under the backend's cap,
/// a GIF only as a Tenor/Giphy link (never uploaded); a received picture is fetched by id from /social/img/&lt;id&gt;.</summary>
public static class FriendsMedia
{
    public const string ScreenshotText = "[Screenshot]";
    private const string GifHosts = @"(?:media\d?\.tenor\.com|c\.tenor\.com|media\d?\.giphy\.com|i\.giphy\.com)";
    private static readonly Regex GifMedia = new($@"^https://{GifHosts}/[A-Za-z0-9_\-./%]{{1,256}}\.gif$");
    private static readonly Regex GiphyPage = new(@"^https://(?:www\.)?giphy\.com/(?:gifs|embed)/(?:[A-Za-z0-9-]*-)?([A-Za-z0-9]{5,40})/?(?:[?#]\S*)?$");
    private static readonly Regex TenorPage = new(@"^https://(?:www\.)?tenor\.com/(?:[a-z]{2}(?:-[A-Z]{2})?/)?view/[A-Za-z0-9%_-]{1,200}/?(?:[?#]\S*)?$");
    private static readonly Regex TenorOgImage = new(@"property=""og:image""\s+content=""(https://media\d?\.tenor\.com/[A-Za-z0-9_\-./%]+\.gif)""");
    private static readonly Regex ImageId = new("^[A-Za-z0-9_-]{22}$");

    /// <summary>A shown picture: one frame, or a GIF's frames and how long each stays (ms).</summary>
    public sealed record Media(IReadOnlyList<Bitmap> Frames, IReadOnlyList<int> Delays);

    private static readonly ConcurrentDictionary<string, Task<Media?>> Cache = new();

    /// <summary>The whole message when it is a Tenor or Giphy link (a GIF file or its page), else null.</summary>
    public static string? GifLink(string? text)
    {
        string t = text?.Trim() ?? "";
        if (t.Length == 0 || t.Length > FriendsService.MaxDm || t.Any(char.IsWhiteSpace)) return null;
        return GifMedia.IsMatch(t) || GiphyPage.IsMatch(t) || TenorPage.IsMatch(t) ? t : null;
    }

    /// <summary>The GIF file a link names: the link itself, Giphy's i.giphy.com/&lt;id&gt;.gif, or a Tenor page's og:image. Null when none.</summary>
    public static async Task<string?> ResolveGifAsync(HttpClient http, string link)
    {
        if (GifMedia.IsMatch(link)) return link;
        if (GiphyPage.Match(link) is { Success: true } giphy) return $"https://i.giphy.com/{giphy.Groups[1].Value}.gif";
        if (!TenorPage.IsMatch(link)) return null;
        try
        {
            var html = await http.GetStringAsync(link);
            return TenorOgImage.Match(html) is { Success: true } og && GifMedia.IsMatch(og.Groups[1].Value) ? og.Groups[1].Value : null;
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException) { return null; }
    }

    /// <summary>Where the backend serves a friend's picture (opened in the browser on click).</summary>
    public static string ImageUrl(string id)
    {
        var ws = FriendsService.EndpointUri();
        return $"{(ws.Scheme == "ws" ? "http" : "https")}://{ws.Authority}/social/img/{id}";
    }

    /// <summary>Our own copy of a sent picture (also where the game keeps its sent ones): .../cache/dm-media/sent-*.jpg.</summary>
    public static string KeepSent(byte[] jpeg)
    {
        string dir = Path.Combine(PathService.Instance.BaseDirectory, "cache", "dm-media");
        Directory.CreateDirectory(dir);
        string file = Path.Combine(dir, "sent-" + Convert.ToHexString(System.Security.Cryptography.SHA1.HashData(jpeg)).ToLowerInvariant() + ".jpg");
        if (!File.Exists(file)) File.WriteAllBytes(file, jpeg);
        return file;
    }

    /// <summary>A local "file:" picture the page may show: a .jpg/.png in a cache\dm-media folder (ours or the game's), nothing else.</summary>
    internal static string? LocalPicture(string img)
    {
        if (!img.StartsWith("file:", StringComparison.Ordinal)) return null;
        string path;
        try { path = Path.GetFullPath(img[5..]); } catch (Exception) { return null; }
        var dir = new DirectoryInfo(Path.GetDirectoryName(path) ?? "");
        string ext = Path.GetExtension(path).ToLowerInvariant();
        return dir.Name == "dm-media" && dir.Parent?.Name == "cache" && ext is ".jpg" or ".png" && File.Exists(path) ? path : null;
    }

    /// <summary>Any picture file made into a JPEG of at most <see cref="FriendsService.MaxPicture"/> bytes (long side 1920 or less),
    /// or null when it is no picture.</summary>
    public static byte[]? EncodePicture(byte[] input)
    {
        using var codec = SKCodec.Create(new SKMemoryStream(input)); // null for no picture (SKBitmap.Decode(byte[]) throws then)
        if (codec == null) return null;
        using var source = SKBitmap.Decode(codec);
        if (source == null) return null;
        int side = 1920;
        for (int quality = 88; ; )
        {
            float k = Math.Min(1f, side / (float)Math.Max(source.Width, source.Height));
            var size = new SKImageInfo(Math.Max(1, (int)(source.Width * k)), Math.Max(1, (int)(source.Height * k)));
            using var scaled = k < 1f ? source.Resize(size, new SKSamplingOptions(SKFilterMode.Linear, SKMipmapMode.Linear)) : null;
            using var image = SKImage.FromBitmap(scaled ?? source);
            using var data = image.Encode(SKEncodedImageFormat.Jpeg, quality);
            if (data.Size <= FriendsService.MaxPicture) return data.ToArray();
            if (quality > 60) quality -= 14;
            else if (side > 480) side = side * 3 / 4;
            else return null;
        }
    }

    /// <summary>A message's picture or GIF, fetched once (null when there is none or it is gone).</summary>
    public static Task<Media?> LoadAsync(HttpClient http, FriendsService.Message m)
    {
        string? key = m.Gif ?? m.Img;
        if (key == null) return Task.FromResult<Media?>(null);
        return Cache.GetOrAdd(key, _ => Task.Run(async () =>
        {
            try
            {
                if (m.Gif != null)
                    return GifMedia.IsMatch(m.Gif) ? Frames(await Fetch(http, m.Gif, 8_000_000)) : null;
                if (LocalPicture(m.Img!) is { } file) return Still(await File.ReadAllBytesAsync(file));
                return ImageId.IsMatch(m.Img!) ? Still(await Fetch(http, ImageUrl(m.Img!), 2_000_000)) : null;
            }
            catch (Exception e) when (e is HttpRequestException or TaskCanceledException or IOException or UnauthorizedAccessException or ArgumentException or InvalidOperationException)
            {
                return null;
            }
        }));
    }

    private static async Task<byte[]> Fetch(HttpClient http, string url, int cap)
    {
        using var reply = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead);
        reply.EnsureSuccessStatusCode();
        if (reply.Content.Headers.ContentLength > cap) throw new IOException("too big");
        await using var stream = await reply.Content.ReadAsStreamAsync();
        using var copy = new MemoryStream();
        var buffer = new byte[81920];
        int read;
        while ((read = await stream.ReadAsync(buffer)) > 0)
        {
            copy.Write(buffer, 0, read);
            if (copy.Length > cap) throw new IOException("too big");
        }
        return copy.ToArray();
    }

    private static Media? Still(byte[] bytes)
    {
        using var stream = new MemoryStream(bytes);
        var bitmap = Bitmap.DecodeToWidth(stream, 640);
        return new Media(new[] { bitmap }, new[] { 0 });
    }

    /// <summary>A GIF's frames (at most 80, at most 320 wide), each drawn over the frame it builds on.</summary>
    internal static Media? Frames(byte[] bytes)
    {
        var decoded = DecodeFrames(bytes);
        if (decoded == null) return null;
        var frames = new List<Bitmap>();
        foreach (var (png, _) in decoded)
            using (var stream = new MemoryStream(png))
                frames.Add(new Bitmap(stream));
        return new Media(frames, decoded.Select(d => d.Delay).ToList());
    }

    /// <summary>A GIF's frames as PNGs with how long each stays (ms); null when it is no picture.</summary>
    internal static List<(byte[] Png, int Delay)>? DecodeFrames(byte[] bytes)
    {
        using var codec = SKCodec.Create(new SKMemoryStream(bytes));
        if (codec == null) return null;
        var info = new SKImageInfo(codec.Info.Width, codec.Info.Height, SKColorType.Bgra8888, SKAlphaType.Premul);
        var frameInfo = codec.FrameInfo;
        int count = Math.Clamp(codec.FrameCount, 1, 80);
        float k = Math.Min(1f, 320f / info.Width);
        var size = new SKImageInfo(Math.Max(1, (int)(info.Width * k)), Math.Max(1, (int)(info.Height * k)));
        var frames = new List<(byte[], int)>();
        var drawn = new List<SKBitmap>();
        try
        {
            for (int i = 0; i < count; i++)
            {
                var frame = new SKBitmap(info);
                int prior = i < frameInfo.Length ? frameInfo[i].RequiredFrame : -1;
                if (prior >= 0 && prior < drawn.Count) drawn[prior].CopyTo(frame);
                else frame.Erase(SKColors.Transparent);
                var result = codec.GetPixels(info, frame.GetPixels(), new SKCodecOptions(i, prior >= 0 && prior < drawn.Count ? prior : -1));
                if (result is not (SKCodecResult.Success or SKCodecResult.IncompleteInput)) { frame.Dispose(); break; }
                drawn.Add(frame);
                using (var scaled = k < 1f ? frame.Resize(size, new SKSamplingOptions(SKFilterMode.Linear)) : null)
                using (var image = SKImage.FromBitmap(scaled ?? frame))
                using (var png = image.Encode(SKEncodedImageFormat.Png, 100))
                    frames.Add((png.ToArray(), i < frameInfo.Length && frameInfo[i].Duration >= 20 ? frameInfo[i].Duration : 100));
            }
        }
        finally { foreach (var b in drawn) b.Dispose(); }
        return frames.Count == 0 ? null : frames;
    }
}
