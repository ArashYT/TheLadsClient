package com.thelads.core.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Translate Chat (Chat module, off by default): incoming chat in another language gets its translation, fetched on a background
 * thread from Google Translate's free endpoints, the ones the launcher's own translation uses (LauncherTranslator.cs), with the same
 * language list. Never on the render thread: the adapters get the result through a callback and put it into the chat on the client
 * thread. Only the message's text is sent, and only while the player has the option on.
 */
public final class ChatTranslator {
    /** Translate To: the game's own language first, then the launcher's languages (Google Translate codes). */
    public static final String[] CODES = {"", "en", "es", "pt", "fr", "de", "it", "nl", "pl", "ru", "uk", "tr", "ar", "hi", "bn", "id", "vi",
        "th", "ja", "ko", "zh-CN", "zh-TW", "sv", "cs", "ro", "el", "tl"};
    public static final String[] NAMES = {"Game Language", "English", "Español", "Português (Brasil)", "Français", "Deutsch",
        "Italiano", "Nederlands", "Polski", "Русский",
        "Українська", "Türkçe", "العربية",
        "हिन्दी", "বাংলা", "Bahasa Indonesia", "Tiếng Việt",
        "ไทย", "日本語", "한국어", "简体中文", "繁體中文", "Svenska",
        "Čeština", "Română", "Ελληνικά", "Filipino"};
    // ponytail: two unofficial keyless endpoints as in the launcher (Google rate-limits each per IP); the official API needs a key.
    private static final String[] ENDPOINTS = {
        "https://translate.googleapis.com/translate_a/t?client=gtx&sl=auto&tl=",
        "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=",
    };
    static final int MAX_LENGTH = 500;
    /** A busy server's chat must not pile up requests: at most this many wait, newer ones are dropped. */
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), task -> {
        Thread thread = new Thread(task, "Lads chat translator");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.DiscardPolicy());
    private static final Map<String, String> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > 256; }
    };
    /** Cached for text that needs no translation (already in the wanted language). */
    private static final String SAME = "";

    static {
        WORKER.allowCoreThreadTimeOut(true);
    }

    private ChatTranslator() {}

    /** Translate To's code for a dropdown index; null for the game's language. */
    static String code(int index) {
        return index <= 0 || index >= CODES.length ? null : CODES[index];
    }

    /** Google Translate's code for a Minecraft language ("es_mx" to "es", "zh_cn" to "zh-CN", "pt_br" to "pt"). */
    public static String fromGame(String language) {
        if (language == null || language.length() < 2) return "en";
        String lower = language.toLowerCase(Locale.ROOT);
        if (lower.startsWith("zh_")) return lower.equals("zh_tw") || lower.equals("zh_hk") ? "zh-TW" : "zh-CN";
        int cut = lower.indexOf('_');
        return cut > 0 ? lower.substring(0, cut) : lower;
    }

    /** Worth asking: letters to translate and not too long. */
    static boolean wanted(String text) {
        if (text == null || text.length() > MAX_LENGTH) return false;
        int letters = 0;
        for (int i = 0; i < text.length() && letters < 2; i++) if (Character.isLetter(text.charAt(i))) letters++;
        return letters >= 2;
    }

    /**
     * Asks for the translation of an incoming message (when Translate Chat is on); {@code done} gets it on the translator thread,
     * only when the message was in another language. {@code gameLanguage} is Minecraft's language code.
     */
    public static void request(String text, String gameLanguage, Consumer<String> done) {
        if (!ChatTweaks.translate || !wanted(text)) return;
        String target = ChatTweaks.translateTo != null ? ChatTweaks.translateTo : fromGame(gameLanguage);
        String key = target + '\n' + text;
        String cached;
        synchronized (CACHE) { cached = CACHE.get(key); }
        if (cached != null) {
            if (cached != SAME) done.accept(cached);
            return;
        }
        WORKER.execute(() -> {
            String translated = fetch(text, target);
            if (translated == null) return;
            synchronized (CACHE) { CACHE.put(key, translated); }
            if (translated != SAME) done.accept(translated);
        });
    }

    private static String fetch(String text, String target) {
        for (String endpoint : ENDPOINTS) {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint + target + "&q="
                    + URLEncoder.encode(text, StandardCharsets.UTF_8)).toURL().openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                if (connection.getResponseCode() != 200) continue; // 429: the other endpoint
                try (InputStream in = connection.getInputStream()) {
                    return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), text, target);
                }
            } catch (Exception e) {
                // offline or blocked: the next endpoint, then no translation
            }
        }
        return null;
    }

    /** translate_a/t answers [["translation","detected language"]]: SAME when nothing changes, null when unreadable. */
    static String parse(String json, String original, String target) {
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonElement first = root.isJsonArray() && root.getAsJsonArray().size() > 0 ? root.getAsJsonArray().get(0) : null;
            if (first == null) return null;
            String translated, language = null;
            if (first.isJsonArray()) {
                JsonArray pair = first.getAsJsonArray();
                translated = pair.get(0).getAsString();
                if (pair.size() > 1) language = pair.get(1).getAsString();
            } else translated = first.getAsString();
            if (language != null && language.equalsIgnoreCase(target)) return SAME;
            if (language != null && target.startsWith(language + "-")) return SAME;
            return translated.isBlank() || translated.trim().equalsIgnoreCase(original.trim()) ? SAME : translated;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
