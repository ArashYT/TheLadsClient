package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.LinkPreviewModule;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Link image preview & safe click system (parity-1023).
 * Fetches image thumbnails ONLY after explicit user click on "Load preview"
 * (privacy & anti-ban rules: never automatic, HTTPS only, safe public hosts only,
 * image content-types only, max 2 redirects, 2MB size cap, dimension check before decoding).
 */
public final class LinkPreview {
    public static final int MAX_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB
    public static final int TIMEOUT_MS = 3000; // 3 seconds
    public static final int MAX_REDIRECTS = 2; // max 2 redirects
    public static final int MAX_DIMENSION = 4096; // max 4096px width or height
    public static final long MAX_PIXELS = 16_000_000L; // max 16 megapixels before decode
    public static final int THUMB_MAX_WIDTH = 160;
    public static final int THUMB_MAX_HEIGHT = 100;

    public enum Status {
        PENDING,
        LOADED,
        FAILED,
        NOT_IMAGE,
        NOT_ALLOWED
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

    private static final Pattern URL_PATTERN = Pattern.compile("https://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Entry> CACHE = Collections.synchronizedMap(new LinkedHashMap<String, Entry>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, LinkPreview.Entry> eldest) {
            return size() > 32;
        }
    });

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Lads-LinkPreview-Worker");
        t.setDaemon(true);
        return t;
    });

    private LinkPreview() {}

    /** True if Link Preview module is enabled in ModuleManager. Default is OFF. */
    public static boolean isEnabled() {
        Module m = ModuleManager.getInstance().getModule(LinkPreviewModule.NAME);
        return m != null && m.isEnabled();
    }

    /** Extracts the first HTTPS URL from a string, or null if none found. */
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
        return CACHE.get(url.trim());
    }

    public static boolean hasPreview(String url) {
        Entry e = getEntry(url);
        return e != null && e.status == Status.LOADED && e.thumbnail != null;
    }

    public static BufferedImage getThumbnail(String url) {
        Entry e = getEntry(url);
        return e != null ? e.thumbnail : null;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * Explicit user-requested fetch of a link preview thumbnail.
     * Enforces explicit click invocation, HTTPS only, safe public host check,
     * image Content-Type only, <=2 redirects, <=3s timeout, <=2MB size cap,
     * and dimension bounds check before decoding.
     */
    public static Entry request(String rawUrl, Consumer<Entry> onComplete) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return null;
        final String url = rawUrl.trim();

        // HTTPS only check
        if (!url.toLowerCase(Locale.ROOT).startsWith("https://")) {
            Entry failed = new Entry(url);
            failed.status = Status.NOT_ALLOWED;
            if (onComplete != null) onComplete.accept(failed);
            return failed;
        }

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
        int redirects = 0;
        String currentUrl = entry.url;

        while (true) {
            HttpURLConnection conn = null;
            InputStream in = null;
            try {
                URI uri = new URI(currentUrl);
                String scheme = uri.getScheme();
                if (scheme == null || !scheme.equalsIgnoreCase("https")) {
                    entry.status = Status.NOT_ALLOWED;
                    return;
                }

                String host = uri.getHost();
                if (host == null || host.isEmpty()) {
                    entry.status = Status.NOT_ALLOWED;
                    return;
                }

                // Resolve and pin DNS address once; verify all returned addresses are safe public hosts
                InetAddress[] addrs = InetAddress.getAllByName(host);
                if (addrs == null || addrs.length == 0) {
                    entry.status = Status.NOT_ALLOWED;
                    return;
                }
                for (InetAddress a : addrs) {
                    if (!isSafePublicAddress(a)) {
                        entry.status = Status.NOT_ALLOWED;
                        return;
                    }
                }
                final InetAddress pinnedAddr = addrs[0];

                URL url = uri.toURL();
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setInstanceFollowRedirects(false); // follow manually to inspect target IP and scheme
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) TheLadsClient/1.10.0");
                conn.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,image/gif;q=0.9");
                conn.setRequestProperty("Host", host);

                if (conn instanceof javax.net.ssl.HttpsURLConnection httpsConn) {
                    javax.net.ssl.SSLSocketFactory defaultFactory = (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
                    httpsConn.setSSLSocketFactory(new PinnedSSLSocketFactory(defaultFactory, pinnedAddr, host));
                }
                conn.connect();

                int code = conn.getResponseCode();

                // Redirect handling (max 2)
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    redirects++;
                    if (redirects > MAX_REDIRECTS) {
                        entry.status = Status.FAILED;
                        return;
                    }
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        entry.status = Status.FAILED;
                        return;
                    }
                    URI nextUri = uri.resolve(location);
                    currentUrl = nextUri.toString();
                    conn.disconnect();
                    continue;
                }

                if (code < 200 || code >= 300) {
                    entry.status = Status.FAILED;
                    return;
                }

                // Header checks: Content-Type must be an image
                String contentType = conn.getContentType();
                if (contentType == null) {
                    entry.status = Status.NOT_IMAGE;
                    return;
                }
                String lowerCt = contentType.toLowerCase(Locale.ROOT).trim();
                int semi = lowerCt.indexOf(';');
                if (semi >= 0) lowerCt = lowerCt.substring(0, semi).trim();

                // Reject non-image or scriptable SVG
                if (!lowerCt.startsWith("image/") || lowerCt.equals("image/svg+xml")) {
                    entry.status = Status.NOT_IMAGE;
                    return;
                }

                // Size cap check
                int contentLength = conn.getContentLength();
                if (contentLength > MAX_SIZE_BYTES) {
                    entry.status = Status.FAILED;
                    return;
                }

                // Stream reading with byte cap enforcement
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
                if (imageBytes.length == 0) {
                    entry.status = Status.FAILED;
                    return;
                }

                // Dimension check before decoding full raster to prevent decompression bombs / OOM
                try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
                    if (iis == null) {
                        entry.status = Status.NOT_IMAGE;
                        return;
                    }
                    Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                    if (!readers.hasNext()) {
                        entry.status = Status.NOT_IMAGE;
                        return;
                    }
                    ImageReader reader = readers.next();
                    BufferedImage original;
                    try {
                        reader.setInput(iis, true, true);
                        int width = reader.getWidth(0);
                        int height = reader.getHeight(0);
                        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                            || ((long) width * height) > MAX_PIXELS) {
                            entry.status = Status.FAILED;
                            return;
                        }
                        original = reader.read(0);
                    } finally {
                        reader.dispose();
                    }

                    if (original == null) {
                        entry.status = Status.NOT_IMAGE;
                        return;
                    }

                    entry.originalWidth = original.getWidth();
                    entry.originalHeight = original.getHeight();
                    entry.thumbnail = createThumbnail(original, THUMB_MAX_WIDTH, THUMB_MAX_HEIGHT);
                    entry.status = Status.LOADED;
                    return;
                }
            } catch (Exception e) {
                entry.status = Status.FAILED;
                return;
            } finally {
                if (in != null) {
                    try { in.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        }
    }

    /**
     * Resolves host and verifies that NONE of the resolved IP addresses are
     * loopback, site-local (private RFC 1918), link-local, multicast, or reserved ranges.
     */
    public static boolean isSafePublicHost(String host) {
        if (host == null || host.trim().isEmpty()) return false;
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) return false;
            for (InetAddress addr : addresses) {
                if (!isSafePublicAddress(addr)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Returns true only if the given address is a globally routable public address. */
    public static boolean isSafePublicAddress(InetAddress addr) {
        if (addr == null) return false;
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
            || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return false;
        }

        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF;
            int b1 = b[1] & 0xFF;
            // 0.0.0.0/8 (broadcast/this host)
            if (b0 == 0) return false;
            // 10.0.0.0/8 (private)
            if (b0 == 10) return false;
            // 100.64.0.0/10 (CGNAT RFC 6598: 100.64.0.0 to 100.127.255.255)
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) return false;
            // 127.0.0.0/8 (loopback)
            if (b0 == 127) return false;
            // 169.254.0.0/16 (link-local)
            if (b0 == 169 && b1 == 254) return false;
            // 172.16.0.0/12 (private: 172.16.0.0 - 172.31.255.255)
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return false;
            // 192.0.0.0/24 (IETF protocol assignments)
            if (b0 == 192 && b1 == 0 && (b[2] & 0xFF) == 0) return false;
            // 192.0.2.0/24 (TEST-NET-1)
            if (b0 == 192 && b1 == 0 && (b[2] & 0xFF) == 2) return false;
            // 192.88.99.0/24 (6to4 relay anycast)
            if (b0 == 192 && b1 == 88 && (b[2] & 0xFF) == 99) return false;
            // 192.168.0.0/16 (private)
            if (b0 == 192 && b1 == 168) return false;
            // 198.18.0.0/15 (benchmarking RFC 2544)
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return false;
            // 198.51.100.0/24 (TEST-NET-2)
            if (b0 == 198 && b1 == 51 && (b[2] & 0xFF) == 100) return false;
            // 203.0.113.0/24 (TEST-NET-3)
            if (b0 == 203 && b1 == 0 && (b[2] & 0xFF) == 113) return false;
            // 224.0.0.0/4 (multicast)
            if (b0 >= 224 && b0 <= 239) return false;
            // 240.0.0.0/4 (reserved)
            if (b0 >= 240) return false;
            // 255.255.255.255 (broadcast)
            if (b0 == 255 && b1 == 255 && (b[2] & 0xFF) == 255 && (b[3] & 0xFF) == 255) return false;
        } else if (b.length == 16) {
            // IPv6 IPv4-mapped ::ffff:0:0/96
            if (b[0] == 0 && b[1] == 0 && b[2] == 0 && b[3] == 0 &&
                b[4] == 0 && b[5] == 0 && b[6] == 0 && b[7] == 0 &&
                b[8] == 0 && b[9] == 0 && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
                byte[] ipv4 = new byte[] { b[12], b[13], b[14], b[15] };
                try {
                    return isSafePublicAddress(InetAddress.getByAddress(ipv4));
                } catch (Exception e) {
                    return false;
                }
            }
            // IPv6 NAT64 64:ff9b::/96
            if (b[0] == 0 && b[1] == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B &&
                b[4] == 0 && b[5] == 0 && b[6] == 0 && b[7] == 0 &&
                b[8] == 0 && b[9] == 0 && b[10] == 0 && b[11] == 0) {
                byte[] ipv4 = new byte[] { b[12], b[13], b[14], b[15] };
                try {
                    return isSafePublicAddress(InetAddress.getByAddress(ipv4));
                } catch (Exception e) {
                    return false;
                }
            }
            // IPv6 6to4 2002::/16
            if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x02) {
                byte[] ipv4 = new byte[] { b[2], b[3], b[4], b[5] };
                try {
                    return isSafePublicAddress(InetAddress.getByAddress(ipv4));
                } catch (Exception e) {
                    return false;
                }
            }
            // IPv6 Unique Local fc00::/7 (0xfc or 0xfd)
            int b0 = b[0] & 0xFF;
            if (b0 == 0xFC || b0 == 0xFD) return false;
        }
        return true;
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

    private static final class PinnedSSLSocketFactory extends javax.net.ssl.SSLSocketFactory {
        private final javax.net.ssl.SSLSocketFactory delegate;
        private final InetAddress pinnedAddress;
        private final String targetHost;

        PinnedSSLSocketFactory(javax.net.ssl.SSLSocketFactory delegate, InetAddress pinnedAddress, String targetHost) {
            this.delegate = delegate;
            this.pinnedAddress = pinnedAddress;
            this.targetHost = targetHost;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return delegate.createSocket(s, targetHost, port, autoClose);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            Socket raw = new Socket();
            raw.connect(new InetSocketAddress(pinnedAddress, port), TIMEOUT_MS);
            return delegate.createSocket(raw, targetHost, port, true);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            Socket raw = new Socket();
            raw.bind(new InetSocketAddress(localHost, localPort));
            raw.connect(new InetSocketAddress(pinnedAddress, port), TIMEOUT_MS);
            return delegate.createSocket(raw, targetHost, port, true);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            Socket raw = new Socket();
            raw.connect(new InetSocketAddress(pinnedAddress, port), TIMEOUT_MS);
            return delegate.createSocket(raw, targetHost, port, true);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            Socket raw = new Socket();
            raw.bind(new InetSocketAddress(localAddress, localPort));
            raw.connect(new InetSocketAddress(pinnedAddress, port), TIMEOUT_MS);
            return delegate.createSocket(raw, targetHost, port, true);
        }
    }
}
