package com.thelads.core.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal skin fetching and caching engine ported from TheLadsLauncher C# logic.
 * Tiers:
 * 1. Local custom skin (%APPDATA%/.theladsclient/skin.png)
 * 2. Local skin cache (%APPDATA%/.theladsclient/cache/skins/<uuid>.png)
 * 3. Mojang Session API (https://sessionserver.mojang.com/session/minecraft/profile/<uuid>)
 * 4. Minotar Skin API fallback (https://minotar.net/skin/<username>)
 */
public class SkinFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkinFetcher.class);
    private static final Map<String, CompletableFuture<Path>> PENDING_FETCHES = new ConcurrentHashMap<>();

    public static CompletableFuture<Path> getOrFetchSkin(String uuid, String username) {
        String key = (uuid != null && !uuid.isBlank()) ? uuid.replace("-", "").toLowerCase() : (username != null ? username.toLowerCase() : "unknown");

        // 1. Check custom user skin
        Path customSkin = ClientPaths.getSkinFile().toPath();
        if (Files.exists(customSkin)) {
            return CompletableFuture.completedFuture(customSkin);
        }

        // 2. Check local disk cache
        Path cacheDir = ClientPaths.getSkinCacheDir();
        Path cachedFile = cacheDir.resolve(key + ".png");
        if (Files.exists(cachedFile) && cachedFile.toFile().length() > 0) {
            return CompletableFuture.completedFuture(cachedFile);
        }

        return PENDING_FETCHES.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
            try {
                // 3. Try Mojang Session API if UUID is present
                if (uuid != null && !uuid.isBlank()) {
                    String cleanUuid = uuid.replace("-", "");
                    String mojangUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + cleanUuid;
                    String jsonResp = httpGetString(mojangUrl);
                    if (jsonResp != null && !jsonResp.isBlank()) {
                        JsonObject obj = JsonParser.parseString(jsonResp).getAsJsonObject();
                        if (obj.has("properties")) {
                            var props = obj.getAsJsonArray("properties");
                            for (var propElem : props) {
                                JsonObject prop = propElem.getAsJsonObject();
                                if ("textures".equals(prop.get("name").getAsString())) {
                                    String base64Val = prop.get("value").getAsString();
                                    String decoded = new String(Base64.getDecoder().decode(base64Val), StandardCharsets.UTF_8);
                                    JsonObject texObj = JsonParser.parseString(decoded).getAsJsonObject();
                                    if (texObj.has("textures") && texObj.getAsJsonObject("textures").has("SKIN")) {
                                        String skinUrl = texObj.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
                                        downloadFile(skinUrl, cachedFile);
                                        return cachedFile;
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Fallback to Minotar Skin API by username
                if (username != null && !username.isBlank()) {
                    String minotarUrl = "https://minotar.net/skin/" + username;
                    downloadFile(minotarUrl, cachedFile);
                    if (Files.exists(cachedFile) && cachedFile.toFile().length() > 0) {
                        return cachedFile;
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to fetch skin for {}: {}", username, e.getMessage());
            } finally {
                PENDING_FETCHES.remove(key);
            }
            return null;
        }));
    }

    private static String httpGetString(String urlStr) {
        try {
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "TheLadsClient/1.0");

            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void downloadFile(String urlStr, Path dest) throws Exception {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "TheLadsClient/1.0");

        if (conn.getResponseCode() == 200) {
            Files.createDirectories(dest.getParent());
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
