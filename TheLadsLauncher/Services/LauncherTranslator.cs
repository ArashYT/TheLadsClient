using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Documents;
using Avalonia.Threading;
using Avalonia.VisualTree;

namespace TheLadsLauncher.Services;

/// <summary>
/// The launcher in the player's language (Settings → General → Language), translated at runtime by Google Translate's free
/// web endpoint: no API key and no resource files. Every TextBlock and Run keeps the English its XAML or code gave it; the
/// translation is only shown (SetCurrentValue), so code and bindings that set Text later still work and are translated again.
/// Translations are cached in lang-cache/&lt;code&gt;.json under the launcher folder. Offline or refused: English, retried later.
/// Privacy: text inside an element with the "notranslate" class (accounts, profiles, mods, servers, worlds, files...) is never
/// sent or translated; that text, links, paths, quoted names, numbers and product names are sent as {0}, {1}... elsewhere.
/// </summary>
public static class LauncherTranslator
{
    public const string NoTranslate = "notranslate";

    /// <summary>Google Translate codes and native names. English is the launcher's own text.</summary>
    public static readonly (string Code, string Name)[] Languages =
    {
        ("en", "English"), ("es", "Español"), ("pt", "Português (Brasil)"), ("fr", "Français"), ("de", "Deutsch"),
        ("it", "Italiano"), ("nl", "Nederlands"), ("pl", "Polski"), ("ru", "Русский"), ("uk", "Українська"), ("tr", "Türkçe"),
        ("ar", "العربية"), ("hi", "हिन्दी"), ("bn", "বাংলা"), ("id", "Bahasa Indonesia"), ("vi", "Tiếng Việt"), ("th", "ไทย"),
        ("ja", "日本語"), ("ko", "한국어"), ("zh-CN", "简体中文"), ("zh-TW", "繁體中文"), ("sv", "Svenska"), ("cs", "Čeština"),
        ("ro", "Română"), ("el", "Ελληνικά"), ("tl", "Filipino"),
    };

    // translate_a/t takes one q per text and answers one translation per q, in order: no joining and re-splitting.
    // ponytail: two unofficial keyless endpoints, because Google rate-limits each per IP (HTTP 429); the official API needs a key.
    private static readonly string[] Endpoints =
    {
        "https://translate.googleapis.com/translate_a/t?client=gtx&sl=en&tl={0}",
        "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=en&tl={0}",
    };
    private static readonly TimeSpan RetryDelay = TimeSpan.FromMinutes(2);

    public static string Language { get; private set; } = "en";
    public static HttpClient Http { get; set; } = new() { Timeout = TimeSpan.FromSeconds(15) };
    public static Action<string>? Log { get; set; }
    /// <summary>A translation is being fetched or waits to be (QA waits for this before a screenshot).</summary>
    public static bool Busy => _fetching || _passQueued || (Wanted.Count > 0 && DateTime.UtcNow >= _retryAt);

    private static readonly ConditionalWeakTable<StyledElement, string> Originals = new();
    // Text shown in "notranslate" elements this session: masked wherever else it appears ("Account removed: {0}").
    private static readonly HashSet<string> Private = new(StringComparer.Ordinal);
    private static readonly HashSet<string> Wanted = new(StringComparer.Ordinal);
    private static Dictionary<string, string> _cache = new(StringComparer.Ordinal);
    private static bool _installed, _setting, _fetching, _passQueued, _failureLogged;
    private static DateTime _retryAt;

    private static readonly Regex Words = new(@"\p{L}{2}", RegexOptions.Compiled);
    // Gamers' words kept as a whole label (in a sentence Google has context): alone Google made Skins "Batería", Mods "改造".
    private static readonly Regex Loanword = new(@"^(?i:mods?|modpacks?|skins?)$", RegexOptions.Compiled);
    private static readonly Regex Placeholder = new(@"[{｛]\s*(\d+)\s*[}｝]", RegexOptions.Compiled);
    // Never sent: links, e-mail addresses, paths, host names, quoted names, placeholders, file names, product names, numbers/versions.
    private static readonly Regex Kept = new(
        @"https?://\S*|www\.\S+|[\w.+-]+@[\w-]+\.[\w.]+"
        + @"|(?<!\p{L})[A-Za-z]:[\\/][^'""\r\n()]*|\\\\[^'""\r\n()]*|%\w+%[^'""\s()]*"
        + @"|\b(?:[\w-]+\.)+(?:com|net|org|io|gg|me|co|uk|de|eu|us|tv|xyz|fun|club|pro|dev|app|land|network)\b|\b[\w-]+(?:\.[\w-]+){2,}"
        + @"|(?<!\p{L})'[^'\r\n]+'(?!\p{L})|""[^""\r\n]+""|\{[^{}\r\n]*\}"
        + @"|(?<![\w.])\.\w(?:[\w-]|\.\w)*|[\w.-]+\.(?:jar|zip|png|jpe?g|json|txt|toml|dat|log|mrpack|exe|nbt|cfg|ini|properties)\b"
        + @"|\b(?i:the lads client|lads client|lads ?core|lads|minecraft|fabric|neoforge|forge|optifine|modrinth|curseforge|microsoft"
        + @"|xbox|mojang|java|jdk|adoptium|vulkan|opengl|iris|sodium|lunar client|lunar|prism|imgur|packwiz|discord|windows)\b"
        + @"|[\w.+-]*\d(?:[\w+-]|[.,:]\w)*", // any token with a digit (counts, sizes, v0.7.7+fix3+26.3), not a sentence's full stop
        RegexOptions.Compiled);

    private static string CacheFile(string code) => Path.Combine(PathService.Instance.BaseDirectory, "lang-cache", code + ".json");

    /// <summary>Called once before any window: from here on every TextBlock and Run remembers its English text.</summary>
    public static void Install()
    {
        if (_installed) return;
        _installed = true;
        TextBlock.TextProperty.Changed.AddClassHandler<TextBlock>((block, e) => Changed(block, e.NewValue as string));
        Run.TextProperty.Changed.AddClassHandler<Run>((run, e) => Changed(run, e.NewValue as string));
    }

    /// <summary>Marks player or game content (a name, a path, a server's MOTD...): it and everything in it is never sent or
    /// translated. The XAML equivalent is Classes="notranslate".</summary>
    public static T Untranslated<T>(this T element, bool when = true) where T : StyledElement
    {
        if (when) element.Classes.Add(NoTranslate);
        return element;
    }

    /// <summary>Switches every open and later window to <paramref name="code"/> (English restores the original text).</summary>
    public static void SetLanguage(string? code)
    {
        code = Languages.Any(l => l.Code == code) ? code! : "en";
        if (code == Language) return;
        Language = code;
        _cache = code == "en" ? new(StringComparer.Ordinal) : LoadCache(CacheFile(code));
        Wanted.Clear();
        _retryAt = default;
        QueuePass();
    }

    private static void Changed(StyledElement element, string? text)
    {
        if (_setting || element is TextBlock { Inlines.Count: > 0 }) return; // our own set; formatted text is done by its Runs
        bool known = Originals.TryGetValue(element, out _);
        Originals.AddOrUpdate(element, text ?? "");
        // Shown before it is first drawn: rows and tab pages are filled before they join the window.
        if (!known && element is TextBlock block) block.AttachedToVisualTree += (_, _) => Show(block);
        if (Language == "en") return;
        if (IsAttached(element)) Show(element);
        else if (element is Run) QueuePass();
    }

    private static void Show(StyledElement element)
    {
        if (!Originals.TryGetValue(element, out var source) || source.Length == 0) return;
        string shown = source;
        if (Language != "en")
        {
            if (IsMarked(element)) Remember(source);
            else if (Translated(source) is { } translated) shown = translated;
            else QueuePass(); // fetched by the pass, once every name on screen is known
        }
        var property = element is Run ? Run.TextProperty : TextBlock.TextProperty;
        if (element.GetValue(property) == shown) return;
        _setting = true;
        try { element.SetCurrentValue(property, shown); }
        finally { _setting = false; }
        // The panels around it must take the new size: Avalonia left tab headers at the previous language's width.
        for (var visual = element as Visual ?? element.Parent as Visual; visual != null; visual = visual.GetVisualParent())
            (visual as Avalonia.Layout.Layoutable)?.InvalidateMeasure();
    }

    private static void QueuePass()
    {
        if (_passQueued) return;
        _passQueued = true;
        Dispatcher.UIThread.Post(Pass, DispatcherPriority.Background);
    }

    // Every element on screen: names first (so they are masked everywhere), then the text, then one fetch for what is missing.
    // _passQueued stays set until the end, so the Shows below queue no further pass.
    private static void Pass()
    {
        var attached = Originals.Select(pair => pair.Key).Where(IsAttached).ToList();
        if (Language != "en")
            foreach (var element in attached.Where(IsMarked))
                if (Originals.TryGetValue(element, out var text)) Remember(text);
        foreach (var element in attached) Show(element);
        _passQueued = false;
        if (!_fetching && Wanted.Count > 0 && DateTime.UtcNow >= _retryAt && Language != "en")
        {
            _fetching = true;
            _ = FetchAsync(Language);
        }
    }

    private static async Task FetchAsync(string language)
    {
        int added = 0;
        try
        {
            // A template masked before a name was first shown still holds it: never sent (the next pass masks it anew).
            var batch = Wanted.Where(t => !Private.Any(p => WordHits(t, p).Any())).ToList();
            Wanted.Clear();
            foreach (var chunk in Chunks(batch))
            {
                var translated = await TranslateAsync(Http, language, chunk, CancellationToken.None);
                if (language != Language) return;
                for (int i = 0; i < chunk.Count; i++) _cache[chunk[i]] = translated[i];
                added += chunk.Count;
            }
            _failureLogged = false;
        }
        catch (Exception e) // whatever went wrong, the launcher stays usable in English and tries again later
        {
            _retryAt = DateTime.UtcNow + RetryDelay;
            if (!_failureLogged) Log?.Invoke($"[Language] Google Translate is unavailable ({e.Message}). Untranslated text stays in English; retrying in {RetryDelay.TotalMinutes:0} minutes.");
            _failureLogged = true;
            DispatcherTimer.RunOnce(QueuePass, RetryDelay);
        }
        finally
        {
            _fetching = false;
            if (added > 0 && language == Language)
            {
                var file = CacheFile(language);
                var snapshot = new Dictionary<string, string>(_cache, StringComparer.Ordinal);
                _ = Task.Run(() => SaveCacheAsync(file, snapshot));
            }
            QueuePass();
        }
    }

    private static bool IsAttached(StyledElement element)
    {
        for (StyledElement? e = element; e != null; e = e.Parent)
            if (e is Visual visual) return visual.IsAttachedToVisualTree();
        return false;
    }

    // Up the logical tree (the visual one where an element has no logical parent, e.g. inside a popup), ending at the window:
    // a window's visual parent is its host, whose logical parent is the window again (that loop once hung the launcher; the
    // depth limit keeps any other such loop from doing it again).
    private static bool IsMarked(StyledElement element)
    {
        int depth = 0;
        for (StyledElement? e = element; e != null && depth++ < 500; e = e is TopLevel ? null : e.Parent ?? (e as Visual)?.GetVisualParent() as StyledElement)
            if (e.Classes.Contains(NoTranslate)) return true;
        return false;
    }

    private static void Remember(string text)
    {
        foreach (var line in text.Split('\n'))
        {
            var value = line.Trim();
            if (value.Length is >= 3 and <= 64 && Words.IsMatch(value)) Private.Add(value);
        }
    }

    // Line by line; null while any line still waits for Google (it is added to Wanted).
    private static string? Translated(string source)
    {
        var lines = source.Split('\n');
        bool missing = false;
        for (int i = 0; i < lines.Length; i++)
        {
            string line = lines[i], core = line.Trim();
            if (!Words.IsMatch(core)) continue;
            var (template, values) = Mask(core, Private);
            if (!Words.IsMatch(template) || Loanword.IsMatch(template)) continue;
            if (!_cache.TryGetValue(template, out var translated)) { Wanted.Add(template); missing = true; continue; }
            if (Unmask(translated, values) is not { } text) continue; // Google lost a placeholder: this line stays English
            int start = line.IndexOf(core, StringComparison.Ordinal);
            lines[i] = line[..start] + text + line[(start + core.Length)..];
        }
        return missing ? null : string.Join('\n', lines);
    }

    /// <summary>The text Google may see: everything private or untranslatable replaced by {0}, {1}... (the values, in order).
    /// <paramref name="privateValues"/> are masked as whole words.</summary>
    public static (string Template, List<string> Values) Mask(string text, IEnumerable<string> privateValues)
    {
        var spans = Kept.Matches(text).Select(m => (Start: m.Index, End: m.Index + m.Length)).ToList();
        foreach (var value in privateValues)
            foreach (int at in WordHits(text, value)) spans.Add((at, at + value.Length));
        spans.Sort();
        var template = new StringBuilder();
        var values = new List<string>();
        int done = 0, last = 0;
        foreach (var (start, end) in spans)
        {
            if (start <= done && values.Count > 0) // overlaps or touches the previous one: a single placeholder for both
            {
                if (end > done) { values[^1] = text[last..end]; done = end; }
                continue;
            }
            template.Append(text, done, start - done).Append('{').Append(values.Count).Append('}');
            values.Add(text[start..end]);
            (last, done) = (start, end);
        }
        return (template.Append(text, done, text.Length - done).ToString(), values);
    }

    /// <summary>The translation with the values back in; null when a placeholder was lost, invented or repeated.</summary>
    public static string? Unmask(string translated, IReadOnlyList<string> values)
    {
        var used = new bool[values.Count];
        bool bad = false;
        string text = Placeholder.Replace(translated, m =>
        {
            if (!int.TryParse(m.Groups[1].Value, out int i) || i >= values.Count || used[i]) { bad = true; return m.Value; }
            used[i] = true;
            return values[i];
        });
        return bad || used.Contains(false) ? null : text;
    }

    // Where value occurs in text as a whole word (case-sensitive).
    private static IEnumerable<int> WordHits(string text, string value)
    {
        for (int at = text.IndexOf(value, StringComparison.Ordinal); at >= 0; at = text.IndexOf(value, at + 1, StringComparison.Ordinal))
            if (!IsWordChar(text, at - 1) && !IsWordChar(text, at + value.Length)) yield return at;
    }

    private static bool IsWordChar(string text, int index) => index >= 0 && index < text.Length && char.IsLetterOrDigit(text[index]);

    /// <summary>Requests of at most <paramref name="maxCount"/> texts and about <paramref name="maxChars"/> characters.</summary>
    public static IEnumerable<List<string>> Chunks(IEnumerable<string> texts, int maxChars = 3000, int maxCount = 100)
    {
        var chunk = new List<string>();
        int chars = 0;
        foreach (var text in texts)
        {
            if (chunk.Count > 0 && (chunk.Count == maxCount || chars + text.Length > maxChars)) { yield return chunk; chunk = new(); chars = 0; }
            chunk.Add(text);
            chars += text.Length;
        }
        if (chunk.Count > 0) yield return chunk;
    }

    /// <summary>One POST with a q per text; the next endpoint is tried when one fails. Throws when all fail.</summary>
    public static async Task<string[]> TranslateAsync(HttpClient http, string language, IReadOnlyList<string> texts, CancellationToken token)
    {
        Exception? failure = null;
        foreach (var endpoint in Endpoints)
        {
            try
            {
                using var body = new FormUrlEncodedContent(texts.Select(t => new KeyValuePair<string, string>("q", t)));
                using var response = await http.PostAsync(string.Format(endpoint, Uri.EscapeDataString(language)), body, token).ConfigureAwait(false);
                response.EnsureSuccessStatusCode();
                return ParseTranslations(await response.Content.ReadAsStringAsync(token).ConfigureAwait(false), texts.Count);
            }
            catch (Exception e) when (!token.IsCancellationRequested && e is HttpRequestException or TaskCanceledException or JsonException or InvalidDataException)
            {
                failure = e;
            }
        }
        throw failure!;
    }

    /// <summary>["texto", ...] or [["texto", "en"], ...]; a single text may come back bare.</summary>
    public static string[] ParseTranslations(string json, int count)
    {
        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;
        var items = root.ValueKind == JsonValueKind.Array ? root.EnumerateArray().ToList() : new List<JsonElement> { root };
        if (items.Count != count) throw new InvalidDataException($"Google Translate answered {items.Count} texts for {count}.");
        return items.Select(item => (item.ValueKind == JsonValueKind.Array ? item[0] : item).GetString()
            ?? throw new InvalidDataException("Google Translate answered null.")).ToArray();
    }

    public static Dictionary<string, string> LoadCache(string file)
    {
        try { return new(JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllBytes(file)) ?? new(), StringComparer.Ordinal); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException) { return new(StringComparer.Ordinal); }
    }

    public static async Task SaveCacheAsync(string file, IReadOnlyDictionary<string, string> cache)
    {
        var options = new JsonSerializerOptions { WriteIndented = true, Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping };
        try { await LockFiles.WriteAtomicallyAsync(file, JsonSerializer.SerializeToUtf8Bytes(cache, options)).ConfigureAwait(false); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { Log?.Invoke($"[Language] Could not save {file}: {e.Message}"); }
    }
}
