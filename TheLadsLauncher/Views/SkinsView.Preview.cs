using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

public partial class SkinsView
{
    /// <summary>
    /// --preview-skins (sandbox only, see Program.Main): seeds the library, drives every Skins action against an
    /// in-process fake of Minecraft Services (no request leaves the machine, no real account is used) and writes
    /// screenshots, requests.txt and result.txt to <paramref name="output"/>.
    /// </summary>
    public async Task RunPreviewAsync(string output, Action<string> screenshot)
    {
        var fake = new FakeMinecraftServices();
        var http = new HttpClient(fake);
        var passed = new List<string>();
        void Check(bool ok, string what)
        {
            if (!ok) throw new InvalidOperationException($"{what} (status: {Status.Text})");
            passed.Add(what);
        }
        try
        {
            var red = Library.Add(SampleSkin(Color.Parse("#B8322E"), Color.Parse("#2B2D42")), "Red hoodie", "classic");
            var blue = Library.Add(SampleSkin(Color.Parse("#2E6FD0"), Color.Parse("#3A3A3A")), "Blue slim", "slim");
            await ShowAsync("PreviewLad", _ => Task.FromResult(FakeMinecraftServices.Token), http);
            Check(_profile?.Capes.Count == 2 && CapeList.ItemCount == 3, "profile read: No cape + 2 owned capes listed");
            Check(fake.Requests[0] == "GET /minecraft/profile" && fake.Requests.All(r => r.StartsWith("GET ")), "opening the tab only reads (nothing uploaded)");
            await Task.Delay(500);
            screenshot(Path.Combine(output, "skins-tab.png"));

            Select(red.Id);
            await ApplySkinAsync();
            Check(fake.LastService == $"POST /minecraft/profile/skins variant=classic file=skin.png image/png {File.ReadAllBytes(Library.PathOf(red)).Length} bytes", "Apply skin uploads multipart variant + PNG");
            Check(fake.SkinVariant == "classic" && Status.Text!.Contains("applied"), "skin applied");

            CapeList.SelectedIndex = 0;
            await ApplyCapeAsync();
            Check(fake.LastService == "DELETE /minecraft/profile/capes/active" && fake.ActiveCape == null, "No cape hides the active cape");
            CapeList.SelectedIndex = 2;
            await ApplyCapeAsync();
            Check(fake.LastService == "PUT /minecraft/profile/capes/active {\"capeId\":\"preview-cape-b\"}" && fake.ActiveCape == "preview-cape-b", "Apply cape equips the chosen cape");
            await Task.Delay(400);
            screenshot(Path.Combine(output, "skin-and-cape-applied.png"));

            Select(blue.Id);
            CapeList.SelectedIndex = 1;
            ComboName.Text = "Weekend fit";
            await SaveComboAsync();
            Check(Library.Combos.Count == 1 && Library.Combos[0] is { SkinId: var s, Model: "slim", CapeId: "preview-cape-a" } && s == blue.Id, "combo saved (skin + slim + cape)");
            int before = fake.Requests.Count;
            await ApplyComboAsync(Library.Combos[0]);
            Check(fake.Requests.Skip(before).Where(r => !r.EndsWith(")")).Select(r => r.Split(' ')[0] + " " + r.Split(' ')[1]).SequenceEqual(new[] { "POST /minecraft/profile/skins", "PUT /minecraft/profile/capes/active" })
                && fake.SkinVariant == "slim" && fake.ActiveCape == "preview-cape-a", "combo applies skin, model and cape in one click");
            ComboRows.BringIntoView();
            await Task.Delay(400);
            screenshot(Path.Combine(output, "combo-applied.png"));

            ImportText.Text = "PreviewFriend";
            await ImportTextAsync();
            Check(Library.Skins.Any(k => k.Name == "PreviewFriend" && k.Model == "slim"), "username import keeps the player's slim model");
            ImportText.Text = "https://skins.example.invalid/green-skin.png";
            await ImportTextAsync();
            Check(Library.Skins.Any(k => k.Name == "green-skin"), "https link import");
            int count = Library.Skins.Count;
            ImportText.Text = "https://skins.example.invalid/not-a-skin.png";
            await ImportTextAsync();
            Check(Library.Skins.Count == count && Status.Text!.Contains("64x64"), "a non-skin image is refused with a reason");
            ImportText.Text = "http://skins.example.invalid/green-skin.png";
            await ImportTextAsync();
            Check(Library.Skins.Count == count && Status.Text!.Contains("https://"), "plain http links are refused");
            await Task.Delay(300);
            screenshot(Path.Combine(output, "imported.png"));

            before = fake.Requests.Count;
            await ShowAsync("LocalLad", null, http);
            Select(red.Id);
            await ApplySkinAsync();
            Check(File.ReadAllBytes(PathService.Instance.SkinFile).SequenceEqual(File.ReadAllBytes(Library.PathOf(red))), "local account: Apply writes skin.png (old behaviour)");
            Check(fake.Requests.Count == before, "local account sends no Minecraft Services request");
            await Task.Delay(300);
            screenshot(Path.Combine(output, "local-account.png"));

            Select(blue.Id);
            EditSkin_Click(null, new Avalonia.Interactivity.RoutedEventArgs());
            await Task.Delay(400);
            screenshot(Path.Combine(output, "editor.png"));
            count = Library.Skins.Count;
            Editor.SaveEditorBtn.RaiseEvent(new Avalonia.Interactivity.RoutedEventArgs(Button.ClickEvent));
            for (int i = 0; i < 40 && Library.Skins.Count == count; i++) await Task.Delay(50);
            Check(Library.Skins.Count == count + 1 && Library.Skins[^1].Name.StartsWith("Edited "), "editor Save to library adds a skin");

            if (TopLevel.GetTopLevel(this) is Window window) { window.Width = 960; window.Height = 600; }
            SkinList.SelectedIndex = 0;
            await Task.Delay(500);
            screenshot(Path.Combine(output, "minimum-size.png"));
            Check(fake.Requests.All(r => !r.StartsWith("UNEXPECTED")), "every request matched the fake service");
            await File.WriteAllTextAsync(Path.Combine(output, "result.txt"), "PASS: " + string.Join("; ", passed));
        }
        catch (Exception e) { await File.WriteAllTextAsync(Path.Combine(output, "result.txt"), "FAIL: " + e + "\nPassed before failing: " + string.Join("; ", passed)); }
        await File.WriteAllLinesAsync(Path.Combine(output, "requests.txt"), fake.Requests);
    }

    private void Select(string skinId)
    {
        SkinList.SelectedItem = SkinList.Items.OfType<ListBoxItem>().First(i => (i.Tag as SavedSkin)?.Id == skinId);
    }

    /// <summary>A plain person: skin-tone head with hair and eyes, a shirt, trousers. Enough to read in a screenshot.</summary>
    internal static byte[] SampleSkin(Color shirt, Color trousers, int height = 64)
    {
        var skinTone = Color.Parse("#D9A066");
        var hair = Color.Parse("#4A2F1B");
        var eye = Color.Parse("#2D4B9A");
        Color? At(int x, int y)
        {
            if (y < 16) return x >= 32 ? null : y < 10 ? hair : y == 12 && x is 10 or 13 ? eye : skinTone; // head, no hat layer
            if (y < 32) return x < 16 ? trousers : x < 40 ? shirt : x < 56 ? (y >= 30 ? skinTone : shirt) : null; // right leg, body, right arm
            if (y >= 48) return x is >= 16 and < 32 ? trousers : x is >= 32 and < 48 ? (y >= 62 ? skinTone : shirt) : null; // left leg, left arm
            return null; // no outer layer
        }
        return Png(64, height, At);
    }

    private static byte[] SampleCape(Color color) =>
        Png(64, 32, (x, y) => x < 22 && y < 17 ? (x is >= 4 and <= 7 && y is >= 5 and <= 8 ? Colors.White : color) : null);

    private static byte[] Png(int width, int height, Func<int, int, Color?> pixel)
    {
        using var bitmap = new WriteableBitmap(new PixelSize(width, height), new Vector(96, 96), Avalonia.Platform.PixelFormat.Bgra8888, Avalonia.Platform.AlphaFormat.Unpremul);
        using (var buffer = bitmap.Lock())
        {
            var raw = new byte[width * height * 4];
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    if (pixel(x, y) is { } c)
                    {
                        int at = (y * width + x) * 4;
                        raw[at] = c.B; raw[at + 1] = c.G; raw[at + 2] = c.R; raw[at + 3] = 255;
                    }
            System.Runtime.InteropServices.Marshal.Copy(raw, 0, buffer.Address, raw.Length);
        }
        var png = new MemoryStream();
        bitmap.Save(png);
        return png.ToArray();
    }

    /// <summary>Stands in for Minecraft Services, Mojang's lookups and the texture server. Records one line per request.</summary>
    private sealed class FakeMinecraftServices : HttpMessageHandler
    {
        public const string Token = "preview-token";
        public readonly List<string> Requests = new();
        /// <summary>The last Minecraft Services call (other lines end with their host in brackets).</summary>
        public string LastService => Requests.Last(r => !r.EndsWith(")"));
        public string SkinVariant = "classic";
        public string? ActiveCape = "preview-cape-a";
        private byte[] _skin = SampleSkin(Color.Parse("#4E8A3E"), Color.Parse("#2B2D42"));
        private int _uploads; // Mojang gives every uploaded texture a new url

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var uri = request.RequestUri!;
            string line = $"{request.Method} {uri.AbsolutePath}";
            HttpResponseMessage Bytes(byte[] body) => new(HttpStatusCode.OK) { Content = new ByteArrayContent(body) };
            HttpResponseMessage Text(string body) => new(HttpStatusCode.OK) { Content = new StringContent(body, Encoding.UTF8, "application/json") };
            if (uri.Host == "api.minecraftservices.com")
            {
                if (request.Headers.Authorization?.Parameter != Token) { Requests.Add("UNEXPECTED token " + line); return new HttpResponseMessage(HttpStatusCode.Unauthorized); }
                if (request.Content is MultipartFormDataContent parts)
                    foreach (var part in parts)
                    {
                        string name = part.Headers.ContentDisposition!.Name!.Trim('"');
                        if (name == "variant") line += " variant=" + (SkinVariant = await part.ReadAsStringAsync(cancellationToken));
                        else { _skin = await part.ReadAsByteArrayAsync(cancellationToken); _uploads++; line += $" {name}={part.Headers.ContentDisposition.FileName!.Trim('"')} {part.Headers.ContentType} {_skin.Length} bytes"; }
                    }
                else if (request.Content != null)
                {
                    string body = await request.Content.ReadAsStringAsync(cancellationToken);
                    line += " " + body;
                    ActiveCape = JsonDocument.Parse(body).RootElement.GetProperty("capeId").GetString();
                }
                if (request.Method == HttpMethod.Delete) ActiveCape = null;
                Requests.Add(line);
                return Text(Profile());
            }
            Requests.Add(uri.Host is "textures.minecraft.net" or "api.mojang.com" or "sessionserver.mojang.com" or "skins.example.invalid" ? $"{line} ({uri.Host})" : "UNEXPECTED " + uri);
            string friend = "0123456789abcdef0123456789abcdef";
            if (uri.AbsoluteUri.StartsWith("https://textures.minecraft.net/texture/preview-skin-")) return Bytes(_skin);
            return uri.AbsoluteUri switch
            {
                "https://api.mojang.com/users/profiles/minecraft/PreviewFriend" => Text($"{{\"id\":\"{friend}\",\"name\":\"PreviewFriend\"}}"),
                "https://sessionserver.mojang.com/session/minecraft/profile/0123456789abcdef0123456789abcdef" => Text(
                    $"{{\"id\":\"{friend}\",\"name\":\"PreviewFriend\",\"properties\":[{{\"name\":\"textures\",\"value\":\"{Convert.ToBase64String(Encoding.UTF8.GetBytes("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/preview-friend\",\"metadata\":{\"model\":\"slim\"}}}}"))}\"}}]}}"),
                "https://textures.minecraft.net/texture/preview-friend" => Bytes(SampleSkin(Color.Parse("#E0B030"), Color.Parse("#5A3A20"))),
                "https://textures.minecraft.net/texture/preview-cape-a" => Bytes(SampleCape(Color.Parse("#7B2CBF"))),
                "https://textures.minecraft.net/texture/preview-cape-b" => Bytes(SampleCape(Color.Parse("#2A9D8F"))),
                "https://skins.example.invalid/green-skin.png" => Bytes(SampleSkin(Color.Parse("#3E9A4E"), Color.Parse("#222222"))),
                "https://skins.example.invalid/not-a-skin.png" => Bytes(Png(32, 32, (_, _) => Colors.Orange)),
                _ => new HttpResponseMessage(HttpStatusCode.NotFound)
            };
        }

        private string Profile() => JsonSerializer.Serialize(new
        {
            id = "fedcba9876543210fedcba9876543210",
            name = "PreviewLad",
            skins = new[] { new { id = "preview-skin", state = "ACTIVE", url = "http://textures.minecraft.net/texture/preview-skin-" + _uploads, variant = SkinVariant.ToUpperInvariant() } },
            capes = new[] { ("preview-cape-a", "Migrator"), ("preview-cape-b", "Vanilla") }
                .Select(c => new { id = c.Item1, state = ActiveCape == c.Item1 ? "ACTIVE" : "INACTIVE", url = "http://textures.minecraft.net/texture/" + c.Item1, alias = c.Item2 })
        });
    }
}
