package com.thelads.core.client.social;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.util.ClientPaths;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DM pictures. Screenshots: picked only from the game's screenshots folder, sent as a JPEG to the social backend in
 * "dm_img" parts and fetched back from it by id. GIFs: only a Tenor or Giphy link, never an upload; clients fetch the GIF
 * from those hosts only. Everything is fetched and decoded off the render thread and kept as a file the versions draw
 * ({@link LadsGraphics#drawImageCell}).
 */
public final class DmMedia {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/DmMedia");
    /** Under the backend's 1 MB per picture and 48 000 base64 characters per "dm_img" part (DM_IMG_MAX_BYTES, DM_IMG_PART_B64). */
    public static final int MAX_UPLOAD_BYTES = 950_000, PART_B64 = 45_000;
    /** What old clients show for a screenshot DM (the "text" next to "img"). */
    public static final String SCREENSHOT_TEXT = "[Screenshot]";
    static final int MAX_SIDE = 1600, THUMB_SIDE = 320, SHEET_SIDE = 1024, MAX_FRAMES = 48, MAX_LISTED = 60;
    static final int MAX_GIF_BYTES = 8 << 20, MAX_IMG_BYTES = 1_100_000, MAX_PAGE_BYTES = 1 << 20, TIMEOUT_MS = 8000;
    static final long MAX_GIF_PIXELS = 1_000_000L, MAX_SHOT_PIXELS = 100_000_000L;
    private static final String GIF_HOSTS = "(?:media\\d?\\.tenor\\.com|c\\.tenor\\.com|media\\d?\\.giphy\\.com|i\\.giphy\\.com)";
    /** The GIF files a DM may name: the backend's DM_GIF_URL_RE. */
    static final Pattern GIF_MEDIA = Pattern.compile("https://" + GIF_HOSTS + "/[A-Za-z0-9_\\-./%]{1,256}\\.gif");
    private static final Pattern GIF_HOST = Pattern.compile(GIF_HOSTS);
    private static final Pattern GIPHY_PAGE = Pattern.compile("https://(?:www\\.)?giphy\\.com/(?:gifs|embed)/(?:[A-Za-z0-9-]*-)?([A-Za-z0-9]{5,40})/?(?:[?#]\\S*)?");
    private static final Pattern TENOR_PAGE = Pattern.compile("https://(?:www\\.)?tenor\\.com/(?:[a-z]{2}(?:-[A-Z]{2})?/)?view/[A-Za-z0-9%_-]{1,200}/?(?:[?#]\\S*)?");
    private static final Pattern TENOR_OG_IMAGE = Pattern.compile("property=\"og:image\"\\s+content=\"(https://media\\d?\\.tenor\\.com/[A-Za-z0-9_\\-./%]+\\.gif)\"");
    private static final Pattern IMG_ID = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final String FILE = "file:";
    /** The social socket the pictures' backend sits behind (SocialClient sets it). */
    static volatile URI endpoint = URI.create(SocialProtocol.URL_DEFAULT);

    private static final Map<String, Media> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Media> eldest) { return size() > 96; }
    });
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Lads DM pictures");
        t.setDaemon(true);
        return t;
    });

    private DmMedia() {}

    /** One picture as the DM pane draws it: a file the versions read; a GIF is a grid of frames in that file. */
    public static final class Media {
        volatile boolean ready, failed;
        volatile String file;
        volatile int width = 16, height = 9; // one frame
        volatile int cols = 1, rows = 1, frames = 1, totalMs;
        volatile int[] delays = {0};

        public boolean ready() { return ready; }
        public boolean failed() { return failed; }
        public int width() { return width; }
        public int height() { return height; }
        public int frames() { return frames; }

        /** The frame to show nowMs into the loop. */
        public int frame(long nowMs) {
            int[] d = delays;
            if (frames <= 1 || totalMs <= 0) return 0;
            long t = Math.floorMod(nowMs, (long) totalMs);
            for (int i = 0; i < frames; i++) if ((t -= d[i]) < 0) return i;
            return frames - 1;
        }
    }

    private interface Loader { void load(Media m) throws Exception; }

    private static Media get(String key, Loader loader) {
        synchronized (CACHE) {
            Media m = CACHE.get(key);
            if (m != null) return m;
            Media created = new Media();
            CACHE.put(key, created);
            EXEC.execute(() -> {
                try {
                    loader.load(created);
                    created.ready = created.file != null;
                    created.failed = !created.ready;
                } catch (Exception | LinkageError | OutOfMemoryError e) { // a bad picture must not take the game down
                    created.failed = true;
                    LOGGER.warn("DM picture {} unavailable: {}", key, e.toString());
                }
            });
            return created;
        }
    }

    // ---- What a DM shows ----

    /** A friend's screenshot by backend id, or our own sent copy ("file:" + a file in the cache folder); null for anything else. */
    public static Media image(String ref) {
        if (ref == null) return null;
        if (ref.startsWith(FILE)) {
            Path file = Path.of(ref.substring(FILE.length())).toAbsolutePath().normalize();
            if (!file.startsWith(cacheDir())) return null;
            return get(ref, m -> setPicture(m, file));
        }
        if (!IMG_ID.matcher(ref).matches()) return null;
        return get("img:" + ref, m -> {
            Path file = cacheDir().resolve("img-" + ref);
            if (!Files.isRegularFile(file)) {
                String host = endpoint.getHost();
                write(file, fetch(imageUrl(ref), MAX_IMG_BYTES, h -> h.equalsIgnoreCase(host)));
            }
            setPicture(m, file);
        });
    }

    /** A Tenor/Giphy GIF file, decoded into a grid of at most MAX_FRAMES frames; null for any other link. */
    public static Media gif(String url) {
        if (url == null || !GIF_MEDIA.matcher(url).matches()) return null;
        return get("gif:" + url, m -> {
            Sheet s = sheet(fetch(url, MAX_GIF_BYTES, h -> GIF_HOST.matcher(h.toLowerCase(Locale.ROOT)).matches()));
            Path file = cacheDir().resolve("gif-" + sha1(url) + ".png");
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            ImageIO.write(s.image, "png", tmp.toFile());
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            m.width = s.cellW;
            m.height = s.cellH;
            m.cols = s.cols;
            m.rows = s.rows;
            m.delays = s.delays;
            int total = 0;
            for (int d : s.delays) total += d;
            m.totalMs = total;
            m.frames = s.frames;
            m.file = file.toString();
        });
    }

    /** A small copy of a screenshot for the picker. */
    public static Media thumbnail(Path shot) {
        if (shot == null) return null;
        return get("thumb:" + shot, m -> {
            Path file = cacheDir().resolve("thumb-" + sha1(shot + "|" + Files.getLastModifiedTime(shot).toMillis()) + ".jpg");
            if (!Files.isRegularFile(file)) write(file, jpeg(scaled(readPicture(shot, THUMB_SIDE), THUMB_SIDE), 0.85f));
            setPicture(m, file);
        });
    }

    /** Draws the picture (a GIF's frame for nowMs) in the box; false while it loads, when it failed, or where the version can't. */
    public static boolean draw(LadsGraphics g, Media m, int x, int y, int w, int h, long nowMs) {
        return m != null && m.ready && g.drawImageCell(m.file, m.cols, m.rows, m.frame(nowMs), x, y, w, h);
    }

    /** The size a picture is drawn at: fitted inside maxW by maxH keeping its shape (16:9 until it is known). */
    public static int[] fit(Media m, int maxW, int maxH) {
        int w = m != null && m.width > 0 ? m.width : 16, h = m != null && m.height > 0 ? m.height : 9;
        double k = Math.min(maxW / (double) w, maxH / (double) h);
        return new int[]{Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k))};
    }

    /** Where a friend's screenshot is served (opened in the browser on click). */
    public static String imageUrl(String id) {
        URI ws = endpoint;
        int port = ws.getPort();
        return ("ws".equals(ws.getScheme()) ? "http" : "https") + "://" + ws.getHost() + (port > 0 ? ":" + port : "") + "/social/img/" + id;
    }

    // ---- Sending ----

    private static Path screenshotsDir(Path gameDir) {
        return (gameDir != null ? gameDir : Path.of("")).toAbsolutePath().normalize().resolve("screenshots");
    }

    /** The game's screenshots, newest first: the only pictures a DM can send. */
    public static List<Path> screenshots(Path gameDir) {
        try (Stream<Path> files = Files.list(screenshotsDir(gameDir))) {
            return files.filter(p -> isPictureName(p) && Files.isRegularFile(p))
                    .sorted(Comparator.comparingLong(DmMedia::modified).reversed()).limit(MAX_LISTED).toList();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** True only for a PNG/JPG directly in the game's screenshots folder: no other file can be sent. */
    public static boolean isScreenshot(Path gameDir, Path file) {
        if (file == null || !isPictureName(file)) return false;
        Path parent = file.toAbsolutePath().normalize().getParent();
        return parent != null && parent.equals(screenshotsDir(gameDir)) && Files.isRegularFile(file);
    }

    private static boolean isPictureName(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    private static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /** The screenshot as the JPEG a DM carries: at most MAX_SIDE on its long side and MAX_UPLOAD_BYTES. */
    public static byte[] encodeScreenshot(Path shot) throws IOException {
        BufferedImage image = readPicture(shot, MAX_SIDE);
        for (int side = MAX_SIDE; side >= 400; side = side * 3 / 4) {
            BufferedImage s = scaled(image, side);
            for (float q : new float[]{0.85f, 0.7f, 0.55f}) {
                byte[] out = jpeg(s, q);
                if (out.length <= MAX_UPLOAD_BYTES) return out;
            }
        }
        throw new IOException("screenshot too large to send");
    }

    /** Keeps the sent JPEG so our side of the conversation shows it too; the "file:" reference {@link #image} takes. */
    public static String keepSent(byte[] jpeg) throws IOException {
        Path file = cacheDir().resolve("sent-" + sha1(Base64.getEncoder().encodeToString(jpeg)) + ".jpg");
        if (!Files.isRegularFile(file)) write(file, jpeg);
        return FILE + file;
    }

    /** The base64 "data" of each "dm_img" part, in order. */
    public static List<String> parts(byte[] data) {
        String b64 = Base64.getEncoder().encodeToString(data);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < b64.length(); i += PART_B64) out.add(b64.substring(i, Math.min(b64.length(), i + PART_B64)));
        return out;
    }

    /** Runs a slow step (encoding, a Tenor page) off the render thread. */
    public static void background(Runnable task) {
        EXEC.execute(task);
    }

    /** The whole message when it is a Tenor or Giphy link (a GIF file or its page), else null. */
    public static String gifLink(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty() || t.length() > SocialProtocol.MAX_DM || t.chars().anyMatch(Character::isWhitespace)) return null;
        return GIF_MEDIA.matcher(t).matches() || GIPHY_PAGE.matcher(t).matches() || TENOR_PAGE.matcher(t).matches() ? t : null;
    }

    /**
     * The GIF file a link names: the link itself, Giphy's i.giphy.com/&lt;id&gt;.gif, or a Tenor page's og:image (fetched from
     * tenor.com: call off the render thread). Null when there is none.
     */
    public static String resolveGif(String link) {
        if (link == null) return null;
        if (GIF_MEDIA.matcher(link).matches()) return link;
        Matcher giphy = GIPHY_PAGE.matcher(link);
        if (giphy.matches()) return "https://i.giphy.com/" + giphy.group(1) + ".gif";
        if (TENOR_PAGE.matcher(link).matches()) {
            try {
                String html = new String(fetch(link, MAX_PAGE_BYTES, h -> h.equalsIgnoreCase("tenor.com") || h.equalsIgnoreCase("www.tenor.com")), StandardCharsets.UTF_8);
                Matcher og = TENOR_OG_IMAGE.matcher(html);
                if (og.find() && GIF_MEDIA.matcher(og.group(1)).matches()) return og.group(1);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Tenor link {} unreadable: {}", link, e.toString());
            }
        }
        return null;
    }

    /** The system clipboard as text, "" when there is none: DMs take pasted links. SDL on 26.x, GLFW on 1.20 and 1.21. */
    public static String clipboard() {
        try {
            Object text = Class.forName("org.lwjgl.sdl.SDLClipboard").getMethod("SDL_GetClipboardText").invoke(null);
            if (text instanceof String s && !s.isEmpty()) return s;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {} // no SDL: an older version
        try {
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            long window = (long) glfw.getMethod("glfwGetCurrentContext").invoke(null);
            Object text = glfw.getMethod("glfwGetClipboardString", long.class).invoke(null, window);
            return text instanceof String s ? s : "";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return "";
        }
    }

    // ---- Files and network ----

    static Path cacheDir() {
        Path dir = ClientPaths.getSharedDir().resolve("cache").resolve("dm-media").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
        return dir;
    }

    private static void write(Path file, byte[] data) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** GET with no automatic redirects: HTTPS only (HTTP to localhost for a QA backend), each hop's host checked, at most cap bytes. */
    static byte[] fetch(String url, int cap, Predicate<String> hostAllowed) throws IOException {
        String current = url;
        for (int hop = 0; hop <= 2; hop++) {
            URI uri = URI.create(current);
            String host = uri.getHost();
            boolean local = "http".equals(uri.getScheme()) && ("localhost".equals(host) || "127.0.0.1".equals(host));
            if (host == null || !("https".equals(uri.getScheme()) || local) || !hostAllowed.test(host)) throw new IOException("host not allowed: " + host);
            HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
            try {
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(TIMEOUT_MS);
                c.setReadTimeout(TIMEOUT_MS);
                c.setRequestProperty("User-Agent", "LadsClient");
                int code = c.getResponseCode();
                if (code / 100 == 3 && c.getHeaderField("Location") != null) {
                    current = uri.resolve(c.getHeaderField("Location")).toString();
                    continue;
                }
                if (code != 200) throw new IOException("HTTP " + code);
                if (c.getContentLengthLong() > cap) throw new IOException("over " + cap + " bytes");
                try (InputStream in = c.getInputStream()) {
                    byte[] data = in.readNBytes(cap + 1);
                    if (data.length > cap) throw new IOException("over " + cap + " bytes");
                    return data;
                }
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }

    private static void setPicture(Media m, Path file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) throw new IOException("not a picture");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                m.width = reader.getWidth(0);
                m.height = reader.getHeight(0);
            } finally {
                reader.dispose();
            }
        }
        m.file = file.toString();
    }

    /** A PNG/JPG read with every n-th pixel so its long side stays near 2 x side (a 4K screenshot never decodes in full). */
    static BufferedImage readPicture(Path file, int side) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) throw new IOException("not a PNG or JPG picture");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w * h > MAX_SHOT_PIXELS) throw new IOException("picture too large (" + w + "x" + h + ")");
                ImageReadParam param = reader.getDefaultReadParam();
                int every = (int) Math.max(1, Math.max(w, h) / (2L * side));
                param.setSourceSubsampling(every, every, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    /** An opaque copy at most side pixels on its long side (JPEG has no alpha). */
    static BufferedImage scaled(BufferedImage image, int side) {
        int w = image.getWidth(), h = image.getHeight();
        double k = Math.min(1, side / (double) Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * k)), th = Math.max(1, (int) Math.round(h * k));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    static byte[] jpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /** A GIF's frames on one picture, row by row, and how long each shows (ms). */
    record Sheet(BufferedImage image, int cols, int rows, int frames, int cellW, int cellH, int[] delays) {}

    /** Plays the GIF onto a canvas (offsets, disposal) and keeps every n-th frame so at most MAX_FRAMES fit a SHEET_SIDE square. */
    static Sheet sheet(byte[] gif) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IOException("no GIF reader");
        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
            reader.setInput(in, false);
            int n = Math.min(reader.getNumImages(true), 1000);
            if (n <= 0) throw new IOException("no frames");
            int w = reader.getWidth(0), h = reader.getHeight(0);
            try {
                IIOMetadataNode screen = child((IIOMetadataNode) reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
                if (screen != null) {
                    w = Math.max(w, number(screen, "logicalScreenWidth"));
                    h = Math.max(h, number(screen, "logicalScreenHeight"));
                }
            } catch (RuntimeException ignored) {}
            if ((long) w * h > MAX_GIF_PIXELS) throw new IOException("GIF too large (" + w + "x" + h + ")");
            int step = (n + MAX_FRAMES - 1) / MAX_FRAMES, kept = (n + step - 1) / step;
            int cols = (int) Math.ceil(Math.sqrt(kept)), rows = (kept + cols - 1) / cols;
            double k = Math.min(1, Math.min(SHEET_SIDE / (double) (cols * w), SHEET_SIDE / (double) (rows * h)));
            int cw = Math.max(1, (int) (w * k)), ch = Math.max(1, (int) (h * k));
            BufferedImage sheet = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_ARGB);
            BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int[] delays = new int[kept];
            Graphics2D sg = sheet.createGraphics();
            try {
                sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                for (int i = 0; i < n; i++) {
                    BufferedImage frame = reader.read(i);
                    IIOMetadataNode meta = (IIOMetadataNode) reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                    IIOMetadataNode desc = child(meta, "ImageDescriptor"), control = child(meta, "GraphicControlExtension");
                    int x = desc != null ? number(desc, "imageLeftPosition") : 0, y = desc != null ? number(desc, "imageTopPosition") : 0;
                    String disposal = control != null ? control.getAttribute("disposalMethod") : "none";
                    int delay = control != null ? number(control, "delayTime") * 10 : 100;
                    BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
                    Graphics2D cg = canvas.createGraphics();
                    cg.drawImage(frame, x, y, null);
                    cg.dispose();
                    int slot = i / step;
                    delays[slot] += delay < 20 ? 100 : delay; // browsers play 0-1 cs frames at 100 ms
                    if (i % step == 0) sg.drawImage(canvas, (slot % cols) * cw, (slot / cols) * ch, cw, ch, null);
                    if ("restoreToBackgroundColor".equals(disposal)) {
                        cg = canvas.createGraphics();
                        cg.setComposite(AlphaComposite.Clear);
                        cg.fillRect(x, y, frame.getWidth(), frame.getHeight());
                        cg.dispose();
                    } else if (before != null) {
                        canvas = before;
                    }
                }
            } finally {
                sg.dispose();
            }
            return new Sheet(sheet, cols, rows, kept, cw, ch, delays);
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }

    private static IIOMetadataNode child(IIOMetadataNode node, String name) {
        for (int i = 0; i < node.getLength(); i++) {
            if (node.item(i) instanceof IIOMetadataNode c && name.equals(c.getNodeName())) return c;
        }
        return null;
    }

    private static int number(IIOMetadataNode node, String attr) {
        try {
            return Integer.parseInt(node.getAttribute(attr));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String sha1(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
