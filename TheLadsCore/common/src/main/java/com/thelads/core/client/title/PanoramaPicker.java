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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Title Screen's Panorama Version: the six panorama images of any release, fetched from Mojang once and cached in the shared folder
 * (never bundled). Newer releases keep them in Mojang's asset store (their client jar holds 1x1 placeholders), older ones in the jar. Network and disk work runs on one background thread; the versions' title screens call {@link #update()} each
 * frame on the render thread and rebuild their panorama textures from {@link #images()} when it returns true.
 */
public final class PanoramaPicker {
    public static final String DEFAULT = "Default";
    static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String ENTRY = "assets/minecraft/textures/gui/title/background/panorama_";
    private static final String OBJECT = "minecraft/textures/gui/title/background/panorama_";
    private static final String ASSETS = "https://resources.download.minecraft.net/";
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");

    private static volatile List<String> releases;            // from the worker, put into the option on the render thread
    private static volatile Map<String, String> urls = Collections.emptyMap(); // release -> its version JSON
    private static volatile Object[] finished;                 // {version, byte[][] images | String message}
    private static boolean started;
    private static String applied = DEFAULT, failed, loading;  // render thread only
    private static volatile byte[][] images;                  // read by the cube map's loader during resource reloads too
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

    /** Only HTTPS on Mojang's own hosts (piston-meta, piston-data, launchermeta... and the asset store). */
    static boolean mojang(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null && (host.equals("mojang.com") || host.endsWith(".mojang.com") || host.equals("resources.download.minecraft.net"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The sha1 names of the six panorama images in a release's asset index, null when it lacks one (the jar has them then). */
    static String[] assetHashes(String indexJson) {
        JsonElement objects = JsonParser.parseString(indexJson).getAsJsonObject().get("objects");
        if (objects == null || !objects.isJsonObject()) return null;
        String[] hashes = new String[6];
        for (int i = 0; i < 6; i++) {
            JsonElement entry = objects.getAsJsonObject().get(OBJECT + i + ".png");
            if (entry == null || !entry.isJsonObject() || !entry.getAsJsonObject().has("hash")) return null;
            hashes[i] = entry.getAsJsonObject().get("hash").getAsString();
            if (!hashes[i].matches("[0-9a-f]{40}")) return null;
        }
        return hashes;
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
                LOG.warn("Could not get the {} panorama: {}", wanted, e.toString()); // the screen's note cannot say why
                result = "Could not get the " + wanted + " panorama";
            }
            finished = new Object[] {wanted, result};
        });
        return false;
    }

    /** The title screen's Change panorama button: the next release family's panorama, the choice kept like the setting's. */
    public static void next() {
        DropdownOption option = option();
        if (option == null) return;
        String picked = nextChoice(option.getChoices(), option.getValue());
        option.select(picked);
        com.thelads.core.config.ConfigManager.save();
        message = option.getChoices().length < 2 ? "The panorama list is still loading" : "Panorama: " + picked;
        messageUntil = System.currentTimeMillis() + 4000;
    }

    /**
     * The choice after current, one per release family (1.21.4 and 1.21.11 are both 1.21: hotfixes share a panorama).
     * Default, then the second family on: the newest family comes last, as on its own release it is what Default shows.
     */
    static String nextChoice(String[] choices, String current) {
        List<String> firsts = families(choices);
        if (firsts.isEmpty()) return DEFAULT;
        if (DEFAULT.equals(current)) return firsts.get(firsts.size() > 1 ? 1 : 0);
        int at = 0;
        while (at < firsts.size() && !family(firsts.get(at)).equals(family(current))) at++;
        if (at == 0 || at >= firsts.size()) return DEFAULT;
        return firsts.get(at + 1 < firsts.size() ? at + 1 : 0);
    }

    /** The first (newest) release of each family in the choices after Default. */
    private static List<String> families(String[] choices) {
        List<String> firsts = new ArrayList<>();
        for (int i = 1; i < choices.length; i++) {
            if (firsts.isEmpty() || !family(firsts.get(firsts.size() - 1)).equals(family(choices[i]))) firsts.add(choices[i]);
        }
        return firsts;
    }

    /** One row of the title screen's Panorama list: the choice it picks, its name and the version it shows. */
    public record Entry(String choice, String name, String version) {}

    /** Mojang's names for the 1.x update families; a family without one (the 26.x line) is just "Minecraft <family>". */
    private static final Map<String, String> NAMES = Map.ofEntries(
        Map.entry("1.21", "Tricky Trials"), Map.entry("1.20", "Trails & Tales"), Map.entry("1.19", "The Wild Update"),
        Map.entry("1.18", "Caves & Cliffs: Part II"), Map.entry("1.17", "Caves & Cliffs: Part I"), Map.entry("1.16", "Nether Update"),
        Map.entry("1.15", "Buzzy Bees"), Map.entry("1.14", "Village & Pillage"), Map.entry("1.13", "Update Aquatic"),
        Map.entry("1.12", "World of Color"), Map.entry("1.11", "Exploration Update"), Map.entry("1.10", "Frostburn Update"),
        Map.entry("1.9", "Combat Update"), Map.entry("1.8", "Bountiful Update"), Map.entry("1.7", "The Update that Changed the World"),
        Map.entry("1.6", "Horse Update"), Map.entry("1.5", "Redstone Update"), Map.entry("1.4", "Pretty Scary Update"));

    /** Every panorama the list offers, Default first, then one row per release family, newest first. */
    static List<Entry> entries(String[] choices) {
        List<Entry> rows = new ArrayList<>();
        rows.add(new Entry(DEFAULT, "Default", "Your game's own"));
        for (String release : families(choices)) {
            String family = family(release);
            rows.add(new Entry(release, NAMES.getOrDefault(family, "Minecraft " + family), release));
        }
        return rows;
    }

    public static List<Entry> entries() {
        DropdownOption option = option();
        return entries(option == null ? new String[] {DEFAULT} : option.getChoices());
    }

    /** Whether the row is the panorama picked now (a hotfix picked in the settings marks its family's row). */
    public static boolean isCurrent(Entry entry, String value) {
        return entry.choice().equals(DEFAULT) ? DEFAULT.equals(value) : !DEFAULT.equals(value) && family(entry.choice()).equals(family(value));
    }

    public static String current() {
        DropdownOption option = option();
        return option == null ? DEFAULT : option.getValue();
    }

    /** Whether the release list has loaded (a Default alone is the list still loading). */
    public static boolean ready() {
        DropdownOption option = option();
        return option != null && option.getChoices().length >= 2;
    }

    /** The title screen's note while the list has not loaded. */
    public static void notifyLoading() {
        message = "The panorama list is still loading";
        messageUntil = System.currentTimeMillis() + 4000;
    }

    /** What pick() runs to keep the choice (tests swap it out so they never write the player's config). */
    public static Runnable saver = com.thelads.core.config.ConfigManager::save;

    /** A row of the Panorama list picked: applied and kept like the setting (the next title frame loads its images). */
    public static void pick(String choice) {
        DropdownOption option = option();
        if (option == null || choice == null) return;
        option.select(choice);
        saver.run();
        message = "Panorama: " + choice;
        messageUntil = System.currentTimeMillis() + 4000;
    }

    private static String family(String release) {
        String[] parts = release.split("\\.");
        return parts.length < 2 ? release : parts[0] + "." + parts[1];
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

    /** The version's six images: the cache, else Mojang's asset store, else its client jar. Null: that version has none. */
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
        JsonObject json = JsonParser.parseString(new String(get(url), StandardCharsets.UTF_8)).getAsJsonObject();
        Files.createDirectories(root);
        byte[][] found = fromAssets(json.getAsJsonObject("assetIndex"));
        if (found == null) found = fromJar(json.getAsJsonObject("downloads").getAsJsonObject("client"), root);
        if (found == null) return null;
        Path temp = Files.createTempDirectory(root, version + ".part");
        try {
            for (int i = 0; i < 6; i++) Files.write(temp.resolve("panorama_" + i + ".png"), found[i]);
            if (readCache(dir) == null) clear(dir); // the jar's placeholders, cached by an older build
            try {
                Files.move(temp, dir, StandardCopyOption.ATOMIC_MOVE);
                temp = null;
            } catch (IOException raced) {
                if (readCache(dir) == null) throw raced; // another game cached it first
            }
            return found;
        } finally {
            if (temp != null) clear(temp);
        }
    }

    /** The six images from Mojang's asset store, each checked against its sha1 name. Null: the release's index lacks them. */
    private static byte[][] fromAssets(JsonObject index) throws Exception {
        if (index == null || !index.has("url") || !index.has("sha1")) return null;
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] list = get(index.get("url").getAsString());
        if (!index.get("sha1").getAsString().equalsIgnoreCase(hex(sha1.digest(list)))) throw new IOException("asset index checksum mismatch");
        String[] hashes = assetHashes(new String(list, StandardCharsets.UTF_8));
        if (hashes == null) return null;
        byte[][] found = new byte[6][];
        for (int i = 0; i < 6; i++) {
            found[i] = get(ASSETS + hashes[i].substring(0, 2) + "/" + hashes[i]);
            if (!hashes[i].equals(hex(sha1.digest(found[i])))) throw new IOException("panorama checksum mismatch");
        }
        return decodes(found) ? found : null;
    }

    /** The six images from the release's client jar (sha1-checked, deleted after). Null: it has none, or only placeholders. */
    private static byte[][] fromJar(JsonObject client, Path root) throws Exception {
        String sha1 = client.get("sha1").getAsString();
        Path jar = Files.createTempFile(root, "client", ".jar");
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
            return decodes(found) ? found : null;
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    private static void clear(Path dir) {
        try {
            for (int i = 0; i < 6; i++) Files.deleteIfExists(dir.resolve("panorama_" + i + ".png"));
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {}
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

    /** All six decode, as squares of one size (the cube map needs that) and not the 1x1 placeholders of a newer client jar. */
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
        return size > 1;
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
        return hex(sha1.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
