package com.thelads.core.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;

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

    private static final int[][] CLASSIC_ONLY_ARM_REGIONS = {
        {50, 16, 52, 20}, // Right arm base top/bottom
        {54, 20, 56, 32}, // Right arm base faces
        {50, 32, 52, 36}, // Right arm overlay top/bottom
        {54, 36, 56, 48}, // Right arm overlay faces
        {42, 48, 44, 52}, // Left arm base top/bottom
        {46, 52, 48, 64}, // Left arm base faces
        {58, 48, 60, 52}, // Left arm overlay top/bottom
        {62, 52, 64, 64}  // Left arm overlay faces
    };

    /** Functional interface for accessing ARGB pixel values (alpha in bits 24..31). */
    @FunctionalInterface
    public interface PixelAccessor {
        int getArgb(int x, int y);
    }

    /**
     * Auto-detects whether a skin uses the slim (Alex, 3 px arm) model or classic (Steve, 4 px arm).
     * 64x32 legacy skins (or 2:1 ratio) are always classic. 64x64 skins (and scaled HD skins) are
     * slim when the arm columns that only exist on 4 px arms are fully transparent (alpha < 128).
     */
    public static boolean isSlim(PixelAccessor pixels, int width, int height) {
        if (pixels == null || width < 64 || height < 64) return false;
        if (height == 32 || width == height * 2) return false;
        if (width != height || width % 64 != 0) return false;
        int scale = width / 64;

        for (int[] r : CLASSIC_ONLY_ARM_REGIONS) {
            int x0 = r[0] * scale, y0 = r[1] * scale;
            int x1 = r[2] * scale, y1 = r[3] * scale;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int pixel = pixels.getArgb(x, y);
                    if (((pixel >>> 24) & 0xFF) >= 128) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static boolean isSlim(int[] argb, int width, int height) {
        if (argb == null || width <= 0 || height <= 0 || argb.length < width * height) return false;
        return isSlim((x, y) -> argb[y * width + x], width, height);
    }

    public static boolean isSlim(BufferedImage image) {
        if (image == null) return false;
        return isSlim((x, y) -> image.getRGB(x, y), image.getWidth(), image.getHeight());
    }

    public static boolean detectModel(byte[] png, boolean fallback) {
        if (png == null || png.length < 24) return fallback;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img != null) return isSlim(img);
        } catch (Exception ignored) {}
        return fallback;
    }

    private LocalSkinSource() {}

    /** Upgrades Mojang texture URLs from http:// to https://. */
    public static String upgradeTextureUrl(String url) {
        if (url != null && url.startsWith("http://textures.minecraft.net/")) {
            return "https://" + url.substring("http://".length());
        }
        return url;
    }

    public static Skin resolve(String input) throws IOException {
        return resolve(input, false);
    }

    /** The skin for what the player typed. If a profile is resolved, its model is used; for files and URLs, the model is auto-detected. */
    public static Skin resolve(String input, boolean fallbackSlim) throws IOException {
        String source = upgradeTextureUrl(input.strip());
        if (source.startsWith("https://") || source.startsWith("http://")) {
            byte[] bytes = fetch(source);
            return new Skin(bytes, detectModel(bytes, fallbackSlim));
        }
        if (source.matches("[A-Za-z0-9_]{1,16}") || source.matches("[a-fA-F0-9-]{32,36}")) return profile(source);
        Path file;
        try {
            file = Path.of(source);
        } catch (java.nio.file.InvalidPathException notAPath) {
            throw new IOException("Enter a username, UUID, skin URL or the path of a PNG file");
        }
        if (!Files.isRegularFile(file)) throw new IOException("No file " + source);
        if (Files.size(file) > MAX_BYTES) throw new IOException("Skin file exceeds 1 MB");
        byte[] bytes = Files.readAllBytes(file);
        return new Skin(bytes, detectModel(bytes, fallbackSlim));
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
        byte[] bytes = fetch(upgradeTextureUrl(texture.url()));
        boolean model = texture.slim() || detectModel(bytes, false);
        return new Skin(bytes, model);
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

    /** Validates that a host resolves only to public unicast addresses (blocking loopback, link-local, site-local, multicast, CGNAT, and IPv6 ULA). */
    static void validatePublicHost(String host) throws IOException {
        if (host == null || host.isBlank()) throw new IOException("Use an HTTPS image URL");
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) throw new IOException("Could not resolve host " + host);
        for (InetAddress addr : addresses) {
            if (isNonPublicAddress(addr)) {
                throw new IOException("Refusing connection to private or local address");
            }
        }
    }

    static boolean isNonPublicAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            int b0 = raw[0] & 0xFF;
            int b1 = raw[1] & 0xFF;
            // 100.64.0.0/10 (Carrier-Grade NAT) or 169.254.0.0/16 (Link-local / Cloud Metadata) or 0.0.0.0/8
            if (b0 == 0 || (b0 == 100 && (b1 & 0xC0) == 64) || (b0 == 169 && b1 == 254)) {
                return true;
            }
        } else if (raw.length == 16) {
            int b0 = raw[0] & 0xFF;
            // fc00::/7 (IPv6 Unique Local Addresses)
            if ((b0 & 0xFE) == 0xFC) {
                return true;
            }
        }
        return false;
    }

    /** A page or image from an HTTPS web server, at most 1 MB; redirects are validated on every hop. */
    public static byte[] fetch(String url) throws IOException {
        return fetch(url, false);
    }

    static byte[] fetch(String url, boolean allowLoopbackHttpForTest) throws IOException {
        URI uri;
        try {
            uri = URI.create(upgradeTextureUrl(url));
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Use an HTTPS image URL");
        }
        for (int redirects = 0; redirects <= 5; redirects++) {
            String scheme = uri.getScheme();
            if (allowLoopbackHttpForTest) {
                if (!"https".equals(scheme) && !"http".equals(scheme)) throw new IOException("Use an HTTPS image URL");
            } else {
                if (!"https".equals(scheme)) throw new IOException("Use an HTTPS image URL");
                validatePublicHost(uri.getHost());
            }
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
                    uri = uri.resolve(upgradeTextureUrl(location));
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
