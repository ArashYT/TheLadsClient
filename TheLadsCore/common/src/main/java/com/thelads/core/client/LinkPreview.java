package com.thelads.core.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Link image preview & safe click system (parity-1023).
 * Fetches image thumbnails from chat URL links asynchronously ONLY after explicit hover or click
 * (anti-ban rule: no automatic requests to arbitrary URLs from chat).
 * Strictly enforced size cap (<= 2MB) and timeout (<= 3s).
 */
public final class LinkPreview {
    public static final int MAX_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB
    public static final int TIMEOUT_MS = 3000; // 3 seconds
    public static final int THUMB_MAX_WIDTH = 160;
    public static final int THUMB_MAX_HEIGHT = 100;

    public enum Status {
        PENDING,
        LOADED,
        FAILED,
        NOT_IMAGE
    }

    public static final class Entry {
        private final String url;
        private volatile Status status;
        private volatile BufferedImage thumbnail;
        private volatile int originalWidth;
        private volatile int originalHeight;

        public Entry(String url) {
            this.url = url;
            this.status = Status.PENDING;
        }

        public String getUrl() { return url; }
        public Status getStatus() { return status; }
        public BufferedImage getThumbnail() { return thumbnail; }
        public int getOriginalWidth() { return originalWidth; }
        public int getOriginalHeight() { return originalHeight; }
    }

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Entry> CACHE = Collections.synchronizedMap(new LinkedHashMap<String, Entry>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, LinkPreview.Entry> eldest) {
            return size() > 32;
        }
    });

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Lads-LinkPreview-Worker");
        t.setDaemon(true);
        return t;
    });

    private LinkPreview() {}

    /** Extracts the first HTTP/HTTPS URL from a string, or null if none found. */
    public static String extractUrl(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = URL_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    /** Returns true if the URL appears to be an image link by its extension or domain. */
    public static boolean isLikelyImage(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        int q = lower.indexOf('?');
        if (q >= 0) lower = lower.substring(0, q);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
            || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")
            || lower.contains("i.imgur.com") || lower.contains("media.discordapp.net")
            || lower.contains("cdn.discordapp.com");
    }

    public static Entry getEntry(String url) {
        if (url == null) return null;
        return CACHE.get(url);
    }

    public static boolean hasPreview(String url) {
        Entry e = getEntry(url);
        return e != null && e.status == Status.LOADED && e.thumbnail != null;
    }

    public static BufferedImage getThumbnail(String url) {
        Entry e = getEntry(url);
        return e != null ? e.thumbnail : null;
    }

    /**
     * Explicit request to fetch link preview thumbnail.
     * Enforces explicit invocation, <=3s timeout, and <=2MB size limit.
     */
    public static Entry request(String rawUrl, Consumer<Entry> onComplete) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return null;
        final String url = rawUrl.trim();
        Entry existing = CACHE.get(url);
        if (existing != null) {
            if (existing.status != Status.PENDING && onComplete != null) {
                onComplete.accept(existing);
            }
            return existing;
        }

        final Entry entry = new Entry(url);
        CACHE.put(url, entry);

        EXECUTOR.submit(() -> {
            try {
                fetch(entry);
            } catch (Throwable t) {
                entry.status = Status.FAILED;
            }
            if (onComplete != null) {
                onComplete.accept(entry);
            }
        });

        return entry;
    }

    private static void fetch(Entry entry) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            URI uri = new URI(entry.url);
            URL url = uri.toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) TheLadsClient/1.10.0");
            conn.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,image/*,*/*;q=0.8");
            conn.connect();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                entry.status = Status.FAILED;
                return;
            }

            int contentLength = conn.getContentLength();
            if (contentLength > MAX_SIZE_BYTES) {
                entry.status = Status.FAILED;
                return;
            }

            String contentType = conn.getContentType();
            if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image/") && !isLikelyImage(entry.url)) {
                entry.status = Status.NOT_IMAGE;
                return;
            }

            in = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            int total = 0;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > MAX_SIZE_BYTES) {
                    entry.status = Status.FAILED;
                    return;
                }
                baos.write(buf, 0, read);
            }

            byte[] imageBytes = baos.toByteArray();
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                entry.status = Status.NOT_IMAGE;
                return;
            }

            entry.originalWidth = original.getWidth();
            entry.originalHeight = original.getHeight();
            entry.thumbnail = createThumbnail(original, THUMB_MAX_WIDTH, THUMB_MAX_HEIGHT);
            entry.status = Status.LOADED;
        } catch (Exception e) {
            entry.status = Status.FAILED;
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    public static BufferedImage createThumbnail(BufferedImage src, int maxW, int maxH) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxW && h <= maxH) return src;

        double scale = Math.min((double) maxW / w, (double) maxH / h);
        int targetW = Math.max(1, (int) Math.round(w * scale));
        int targetH = Math.max(1, (int) Math.round(h * scale));

        BufferedImage thumb = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return thumb;
    }
}
