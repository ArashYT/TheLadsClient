package com.thelads.core.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Where a local skin (the in-game Skin changer) comes from on every game version: a username, UUID, skin URL or PNG file becomes
 * the skin's PNG bytes and its model. Blocking: callers run it off the game thread.
 */
public final class LocalSkinSource {
    /** A skin PNG and whether it uses the slim (3 pixel arm) model. */
    public record Skin(byte[] png, boolean slim) {}

    /** Where a Mojang profile keeps its skin: the texture URL and its model. */
    public record Texture(String url, boolean slim) {}

    public static final int MAX_BYTES = 1024 * 1024;
    private static final String USER_AGENT = "TheLadsClient/" + com.thelads.core.LadsVersion.VERSION;

    private LocalSkinSource() {}

    /** The skin for what the player typed. {@code slim} stays as asked, except for a profile, which has its own model. */
    public static Skin resolve(String input, boolean slim) throws IOException {
        String source = input.strip();
        if (source.startsWith("https://") || source.startsWith("http://")) return new Skin(fetch(source), slim);
        if (source.matches("[A-Za-z0-9_]{1,16}") || source.matches("[a-fA-F0-9-]{32,36}")) return profile(source);
        Path file;
        try {
            file = Path.of(source);
        } catch (java.nio.file.InvalidPathException notAPath) {
            throw new IOException("Enter a username, UUID, skin URL or the path of a PNG file");
        }
        if (!Files.isRegularFile(file)) throw new IOException("No file " + source);
        if (Files.size(file) > MAX_BYTES) throw new IOException("Skin file exceeds 1 MB");
        return new Skin(Files.readAllBytes(file), slim);
    }

    /** The skin of a Minecraft account, by name or UUID (Mojang's session server). */
    private static Skin profile(String nameOrUuid) throws IOException {
        String uuid = nameOrUuid.replace("-", "");
        if (!uuid.matches("[a-fA-F0-9]{32}")) {
            try {
                uuid = JsonParser.parseString(text(fetch("https://api.mojang.com/users/profiles/minecraft/" + nameOrUuid))).getAsJsonObject()
                    .get("id").getAsString();
            } catch (IOException | RuntimeException unknown) { // 204 or 404 for a name nobody has
                throw new IOException("No Minecraft account named " + nameOrUuid);
            }
        }
        Texture texture = texture(text(fetch("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)));
        return new Skin(fetch(texture.url()), texture.slim());
    }

    /** The skin in a session server profile's {@code textures} property. */
    public static Texture texture(String profileJson) throws IOException {
        JsonObject skin = null;
        JsonArray properties = JsonParser.parseString(profileJson).getAsJsonObject().getAsJsonArray("properties");
        if (properties != null) for (var entry : properties) {
            JsonObject property = entry.getAsJsonObject();
            if (!property.has("name") || !property.get("name").getAsString().equals("textures") || !property.has("value")) continue;
            JsonObject textures = JsonParser.parseString(text(Base64.getDecoder().decode(property.get("value").getAsString())))
                .getAsJsonObject().getAsJsonObject("textures");
            if (textures != null && textures.has("SKIN")) skin = textures.getAsJsonObject("SKIN");
        }
        if (skin == null) throw new IOException("This profile has no custom skin");
        JsonObject metadata = skin.has("metadata") ? skin.getAsJsonObject("metadata") : null;
        return new Texture(skin.get("url").getAsString(), metadata != null && metadata.has("model") && metadata.get("model").getAsString().equals("slim"));
    }

    /** A page or image from a web server, at most 1 MB; redirects (also http to https, which Mojang's texture server uses) are followed. */
    public static byte[] fetch(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Use an HTTP(S) image URL");
        }
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) throw new IOException("Use an HTTP(S) image URL");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Server redirected nowhere");
                    uri = uri.resolve(location);
                    continue;
                }
                if (status != 200) throw new IOException("Server returned " + status);
                try (InputStream body = connection.getInputStream()) {
                    byte[] bytes = body.readNBytes(MAX_BYTES + 1);
                    if (bytes.length > MAX_BYTES) throw new IOException("Response exceeds 1 MB");
                    return bytes;
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }

    /** A PNG that is 64 x 64 (or the old 64 x 32) pixels, judged by its header alone. */
    public static void validate(byte[] bytes) throws IOException {
        if (bytes.length < 24 || ByteBuffer.wrap(bytes).getLong() != 0x89504E470D0A1A0AL) throw new IOException("Select a PNG skin");
        ByteBuffer header = ByteBuffer.wrap(bytes);
        int width = header.getInt(16), height = header.getInt(20);
        if (width != 64 || (height != 64 && height != 32)) throw new IOException("Skin must be 64 x 64 or 64 x 32 pixels");
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
