package com.thelads.core.client.title;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.TitleScreenModule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import java.awt.image.BufferedImage;

/**
 * Title Screen's Panorama Version: the six panorama images of any release, fetched from Mojang once and cached in the shared folder
 * (never bundled). Network and disk work runs on one background thread; the versions' title screens call {@link #update()} each
 * frame on the render thread and rebuild their panorama textures from {@link #images()} when it returns true.
 */
public final class PanoramaPicker {
    public static final String DEFAULT = "Default";
    static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String ENTRY = "assets/minecraft/textures/gui/title/background/panorama_";

    private static volatile List<String> releases;            // from the worker, put into the option on the render thread
    private static volatile Map<String, String> urls = Collections.emptyMap(); // release -> its version JSON
    private static volatile Object[] finished;                 // {version, byte[][] images | String message}
    private static boolean started;
    private static String applied = DEFAULT, failed, loading;  // render thread only
    private static byte[][] images;
    private static volatile String message;
    private static volatile long messageUntil;

    private PanoramaPicker() {}

    /** The release ids of a version manifest, newest first: releases only, ids safe as folder names. */
    static List<String> releases(String manifestJson, Map<String, String> urlsOut) {
        List<String> out = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(manifestJson).getAsJsonObject().getAsJsonArray("versions")) {
            JsonObject version = element.getAsJsonObject();
            String id = version.has("id") ? version.get("id").getAsString() : "";
            if (!"release".equals(version.has("type") ? version.get("type").getAsString() : "") || !id.matches("[0-9A-Za-z._-]+")) continue;
            out.add(id);
            if (urlsOut != null && version.has("url")) urlsOut.put(id, version.get("url").getAsString());
        }
        return out;
    }

    /** Only HTTPS on Mojang's own hosts (piston-meta, piston-data, launchermeta...). */
    static boolean mojang(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null && (host.equals("mojang.com") || host.endsWith(".mojang.com"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The picked images for a panorama texture path ("textures/gui/title/background/panorama_3.png"), null: the normal one. */
    public static byte[] image(String path) {
        byte[][] current = images;
        String prefix = "textures/gui/title/background/panorama_";
        if (current == null || path == null || !path.startsWith(prefix) || path.length() != prefix.length() + 5 || !path.endsWith(".png")) return null;
        int side = path.charAt(prefix.length()) - '0';
        return side >= 0 && side < 6 ? current[side] : null;
    }

    /** The six picked images (panorama_0 to panorama_5), null for the normal panorama. */
    public static byte[][] images() {
        return images;
    }

    /** A short note for the title screen (a failed or missing panorama), null for none. */
    public static String message() {
        return message != null && System.currentTimeMillis() < messageUntil ? message : null;
    }

    /** Render thread, every title frame: true when {@link #images()} changed and the panorama textures must be rebuilt. */
    public static boolean update() {
        DropdownOption option = option();
        if (!started) {
            started = true;
            String qa = System.getenv("LADS_QA_PANORAMA"); // scripted QA picks a release without the settings screen
            if (qa != null && option != null) option.select(qa);
            run(() -> loadManifest(true));
        }
        List<String> list = releases;
        if (list != null && option != null) {
            releases = null;
            List<String> choices = new ArrayList<>(list);
            choices.add(0, DEFAULT);
            option.setChoices(choices.toArray(new String[0]));
        }
        Object[] done = finished;
        if (done != null) {
            finished = null;
            loading = null;
            if (done[1] instanceof byte[][]) {
                images = (byte[][]) done[1];
                applied = (String) done[0];
                return true;
            }
            failed = (String) done[0];
            message = (String) done[1];
            messageUntil = System.currentTimeMillis() + 8000;
        }
        String wanted = option == null ? DEFAULT : option.getValue();
        if (DEFAULT.equals(wanted)) {
            failed = null;
            if (images == null) return false;
            images = null;
            applied = DEFAULT;
            return true;
        }
        if (!wanted.equals(failed)) failed = null;
        if (wanted.equals(applied) || wanted.equals(failed) || loading != null || !TitleScreenModule.panorama()) return false;
        loading = wanted;
        run(() -> {
            Object result;
            try {
                byte[][] fetched = fetch(wanted);
                result = fetched != null ? fetched : wanted + " has no panorama";
            } catch (Exception | LinkageError e) {
                result = "Could not get the " + wanted + " panorama";
            }
            finished = new Object[] {wanted, result};
        });
        return false;
    }

    private static DropdownOption option() {
        Module module = ModuleManager.getInstance().getModule("TitleScreen");
        return module != null && module.getOption(TitleScreenModule.PANORAMA_VERSION) instanceof DropdownOption d ? d : null;
    }

    private static void run(Runnable task) {
        Thread thread = new Thread(task, "Lads panorama");
        thread.setDaemon(true);
        thread.start();
    }

    private static Path root() {
        return ClientPaths.getSharedDir().resolve("panoramas");
    }

    /** The cached list first (offline), then Mojang's, which replaces the cache. */
    private static synchronized void loadManifest(boolean cachedFirst) {
        Path cache = root().resolve("version_manifest_v2.json");
        if (cachedFirst) {
            try {
                if (Files.isRegularFile(cache)) publish(new String(Files.readAllBytes(cache), StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }
        try {
            String live = new String(get(MANIFEST), StandardCharsets.UTF_8);
            publish(live);
            Files.createDirectories(root());
            Path temp = Files.createTempFile(root(), "manifest", ".part");
            Files.write(temp, live.getBytes(StandardCharsets.UTF_8));
            Files.move(temp, cache, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}
    }

    private static void publish(String json) {
        Map<String, String> found = new HashMap<>();
        List<String> list = releases(json, found);
        if (list.isEmpty()) return;
        urls = found;
        releases = list;
    }

    /** The version's six images: the cache, else its client jar (sha1-checked, deleted after). Null: that version has none. */
    private static byte[][] fetch(String version) throws Exception {
        if (!version.matches("[0-9A-Za-z._-]+")) throw new IOException("bad version id");
        Path root = root(), dir = root.resolve(version);
        byte[][] cached = readCache(dir);
        if (cached != null) return cached;
        String url = urls.get(version);
        if (url == null) {
            loadManifest(false);
            url = urls.get(version);
        }
        if (url == null) throw new IOException("unknown version " + version);
        JsonObject client = JsonParser.parseString(new String(get(url), StandardCharsets.UTF_8)).getAsJsonObject()
            .getAsJsonObject("downloads").getAsJsonObject("client");
        String sha1 = client.get("sha1").getAsString();
        Files.createDirectories(root);
        Path jar = Files.createTempFile(root, "client", ".jar"), temp = null;
        try {
            if (!sha1.equalsIgnoreCase(download(client.get("url").getAsString(), jar))) throw new IOException("client jar checksum mismatch");
            byte[][] found = new byte[6][];
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (int i = 0; i < 6; i++) {
                    ZipEntry entry = zip.getEntry(ENTRY + i + ".png");
                    if (entry == null) return null;
                    try (InputStream in = zip.getInputStream(entry)) { found[i] = readAll(in); }
                }
            }
            if (!decodes(found)) return null;
            temp = Files.createTempDirectory(root, version + ".part");
            for (int i = 0; i < 6; i++) Files.write(temp.resolve("panorama_" + i + ".png"), found[i]);
            try {
                Files.move(temp, dir, StandardCopyOption.ATOMIC_MOVE);
                temp = null;
            } catch (IOException raced) {
                if (readCache(dir) == null) throw raced; // another game cached it first
            }
            return found;
        } finally {
            Files.deleteIfExists(jar);
            if (temp != null) {
                for (int i = 0; i < 6; i++) Files.deleteIfExists(temp.resolve("panorama_" + i + ".png"));
                Files.deleteIfExists(temp);
            }
        }
    }

    private static byte[][] readCache(Path dir) {
        try {
            byte[][] found = new byte[6][];
            for (int i = 0; i < 6; i++) {
                Path file = dir.resolve("panorama_" + i + ".png");
                if (!Files.isRegularFile(file)) return null;
                found[i] = Files.readAllBytes(file);
            }
            return decodes(found) ? found : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** All six decode, as squares of one size (the cube map needs that). */
    static boolean decodes(byte[][] found) {
        int size = -1;
        for (byte[] bytes : found) {
            try {
                BufferedImage image = bytes == null ? null : ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null || image.getWidth() != image.getHeight() || size != -1 && image.getWidth() != size) return false;
                size = image.getWidth();
            } catch (IOException e) {
                return false;
            }
        }
        return size > 0;
    }

    private static HttpsURLConnection open(String url) throws IOException {
        if (!mojang(url)) throw new IOException("not a Mojang address");
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(url).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("User-Agent", "TheLadsClient");
        if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
        return connection;
    }

    private static byte[] get(String url) throws IOException {
        try (InputStream in = open(url).getInputStream()) { return readAll(in); }
    }

    /** Streams the file to disk; returns its sha1 in hex. */
    private static String download(String url, Path target) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] buffer = new byte[65536];
        try (InputStream in = open(url).getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            for (int n; (n = in.read(buffer)) > 0; ) {
                sha1.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : sha1.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
