package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Incoming chat translation using keyless Google Translate API with in-memory caching.
 */
public final class ChatTranslator {
    public static final String[] LANGUAGES = {"en", "es", "fr", "de", "ru", "zh-CN", "ja"};
    private static final Map<String, String> CACHE = Collections.synchronizedMap(new LinkedHashMap<String, String>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 500;
        }
    });

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Chat-Translator");
        t.setDaemon(true);
        return t;
    });

    private ChatTranslator() {}

    public static boolean enabled() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption("Chat Translation") instanceof BoolOption opt && opt.get();
    }

    public static String getTargetLanguage() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        if (chat != null && chat.getOption("Target Language") instanceof DropdownOption drop) {
            int sel = drop.getIndex();
            if (sel >= 0 && sel < LANGUAGES.length) return LANGUAGES[sel];
        }
        return "en";
    }

    public static String getCached(String text) {
        return CACHE.get(text);
    }

    public static void requestTranslation(String text, Consumer<String> onTranslated) {
        if (!enabled() || text == null || text.trim().isEmpty()) return;
        String cached = CACHE.get(text);
        if (cached != null) {
            if (onTranslated != null) onTranslated.accept(cached);
            return;
        }

        String targetLang = getTargetLanguage();
        EXECUTOR.execute(() -> {
            try {
                String translated = fetchTranslation(text, targetLang);
                if (translated != null && !translated.equalsIgnoreCase(text)) {
                    CACHE.put(text, translated);
                    if (onTranslated != null) {
                        onTranslated.accept(translated);
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    public static String fetchTranslation(String text, String targetLang) throws Exception {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String endpoint = "https://translate.googleapis.com/translate_a/t?client=gtx&sl=auto&tl=" + targetLang + "&q=" + encoded;
        HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        if (conn.getResponseCode() == 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                return parseGoogleResponse(response.toString());
            }
        }
        return null;
    }

    public static String parseGoogleResponse(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        // Responses are typically JSON arrays like ["translated text"] or [["translated text", ...]]
        if (s.startsWith("[\"") && s.endsWith("\"]")) {
            return s.substring(2, s.length() - 2).replace("\\\"", "\"");
        }
        if (s.startsWith("[[\"")) {
            int end = s.indexOf("\",");
            if (end > 3) return s.substring(3, end).replace("\\\"", "\"");
        }
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"");
        }
        return s;
    }
}
