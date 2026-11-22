package com.thelads.core.v1_8_9.perf;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.management.ObjectName;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Faster 1.8.9 loading without OptiFine (theladscore.v1_8_9.perf189.mixins.json):
 * the block atlas reads and decodes its PNGs on worker threads a little ahead of Minecraft's own loop (Prefetch, consumed in the
 * same order, Minecraft still does everything else on the main thread) and builds every sprite's mipmaps at once on those threads
 * (TextureUtil's blend is a thread-safe, bit-identical lookup table, TextureUtilMipmapMixin189);
 * cached skins and capes decode off the main thread (ThreadDownloadImageDataMixin189).
 * Anything that goes wrong on a worker falls back to Minecraft's own code on the main thread, so errors look exactly like vanilla.
 * QA: -Dlads.qa.loadstats=1 logs the time to the title screen, then the heap after a full GC (and a class histogram in
 * lads-qa/perf/histogram-title.txt).
 */
public final class LoadPerf189 {
    public static final Logger LOG = LogManager.getLogger("TheLadsCore-1.8.9");
    public static final boolean ATLAS = LoadPerfPlugin189.ATLAS;
    private static final int THREADS = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() - 1));
    private static ExecutorService atlasPool, skinPool;

    private LoadPerf189() {}

    private static ExecutorService pool(String name, int threads, int priority) {
        AtomicInteger n = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), r -> {
            Thread t = new Thread(r, name + " #" + n.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(priority);
            return t;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    static synchronized ExecutorService atlasPool() {
        if (atlasPool == null) atlasPool = pool("Lads Atlas", THREADS, Thread.NORM_PRIORITY);
        return atlasPool;
    }

    public static synchronized ExecutorService skinPool() {
        if (skinPool == null) skinPool = pool("Lads Skins", 2, Thread.MIN_PRIORITY);
        return skinPool;
    }

    /** One atlas load's read-ahead: the main thread asks in the order the locations were given, the workers stay WINDOW ahead. */
    public static final class Prefetch {
        private static final int WINDOW = THREADS * 6;
        private final IResourceManager manager;
        private final List<ResourceLocation> order;
        private final Map<ResourceLocation, Integer> index = new HashMap<>();
        private final Map<ResourceLocation, Future<Loaded>> pending = new HashMap<>();
        private final Map<InputStream, BufferedImage> images = new IdentityHashMap<>();
        private int next;
        public int used;
        public final long start = System.nanoTime();

        public Prefetch(IResourceManager manager, List<ResourceLocation> order) {
            this.manager = manager;
            this.order = order;
            for (int i = 0; i < order.size(); i++) index.put(order.get(i), i);
            fill(0);
        }

        private void fill(int position) {
            int until = Math.min(order.size(), position + WINDOW);
            while (next < until) {
                ResourceLocation location = order.get(next++);
                pending.put(location, atlasPool().submit(() -> load(manager, location)));
            }
        }

        /** The resource read ahead for this location, or null (not read ahead or it failed: Minecraft reads it itself). */
        public IResource take(ResourceLocation location) {
            Integer at = index.get(location);
            if (at != null) fill(at + 1);
            Future<Loaded> f = pending.remove(location);
            if (f == null) return null;
            try {
                Loaded loaded = f.get();
                images.put(loaded.stream, loaded.image);
                used++;
                return loaded.resource;
            } catch (Exception failed) {
                return null;
            }
        }

        /** The decoded image for a stream take handed out (already read and closed), or null. */
        public BufferedImage image(InputStream stream) {
            return images.remove(stream);
        }

        public void close() {
            for (Future<Loaded> f : pending.values()) f.cancel(false);
            pending.clear();
            images.clear();
        }
    }

    private static final class Loaded {
        final IResource resource;
        final InputStream stream;
        final BufferedImage image;

        Loaded(IResource resource, InputStream stream, BufferedImage image) {
            this.resource = resource;
            this.stream = stream;
            this.image = image;
        }
    }

    /** What Minecraft's loop does before loadSprite: the PNG and both metadata sections (parsed once, kept by the resource). */
    private static Loaded load(IResourceManager manager, ResourceLocation location) throws IOException {
        IResource resource = manager.getResource(location);
        InputStream stream = resource.getInputStream();
        BufferedImage image;
        try {
            image = ImageIO.read(stream);
        } finally {
            IOUtils.closeQuietly(stream);
        }
        if (image == null) throw new IOException("no image");
        resource.getMetadata("texture");
        resource.getMetadata("animation");
        return new Loaded(resource, stream, image);
    }

    /** Every sprite's mipmaps on the atlas workers; returns the sprites done (the rest go through Minecraft's loop as usual). */
    public static Set<TextureAtlasSprite> mipmapAll(Collection<TextureAtlasSprite> sprites, int levels) {
        Set<TextureAtlasSprite> done = Collections.newSetFromMap(new IdentityHashMap<TextureAtlasSprite, Boolean>());
        if (!LoadPerfPlugin189.mipmapThreadSafe || levels <= 0 || sprites.size() < 64) return done;
        List<TextureAtlasSprite> all = new ArrayList<>(sprites);
        int chunks = THREADS * 4, per = (all.size() + chunks - 1) / chunks;
        List<Callable<List<TextureAtlasSprite>>> tasks = new ArrayList<>();
        for (int from = 0; from < all.size(); from += per) {
            List<TextureAtlasSprite> part = all.subList(from, Math.min(all.size(), from + per));
            tasks.add(() -> {
                List<TextureAtlasSprite> ok = new ArrayList<>(part.size());
                for (TextureAtlasSprite sprite : part) {
                    try {
                        sprite.generateMipmaps(levels);
                        ok.add(sprite);
                    } catch (Throwable failed) {
                        // Minecraft's loop runs it again and reports it.
                    }
                }
                return ok;
            });
        }
        try {
            for (Future<List<TextureAtlasSprite>> f : atlasPool().invokeAll(tasks)) {
                try {
                    done.addAll(f.get());
                } catch (Exception failed) {
                    // that chunk goes through Minecraft's loop
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return done;
    }

    // TextureUtil.blendColors with each pow(c / 255, 2.2) looked up: the same floats, added in the same order.
    private static final float[] LINEAR = new float[256];

    static {
        for (int c = 0; c < 256; c++) LINEAR[c] = (float) Math.pow((double) ((float) c / 255.0F), 2.2D);
    }

    public static int blendColors(int a, int b, int c, int d, boolean transparent) {
        if (!transparent) {
            return component(a, b, c, d, 24) << 24 | component(a, b, c, d, 16) << 16 | component(a, b, c, d, 8) << 8 | component(a, b, c, d, 0);
        }
        float fa = 0.0F, fr = 0.0F, fg = 0.0F, fb = 0.0F;
        for (int i = 0; i < 4; i++) {
            int p = i == 0 ? a : i == 1 ? b : i == 2 ? c : d;
            if (p >> 24 != 0) {
                fa += LINEAR[p >> 24 & 255];
                fr += LINEAR[p >> 16 & 255];
                fg += LINEAR[p >> 8 & 255];
                fb += LINEAR[p & 255];
            }
        }
        fa /= 4.0F;
        fr /= 4.0F;
        fg /= 4.0F;
        fb /= 4.0F;
        int ia = (int) (Math.pow((double) fa, 0.45454545454545453D) * 255.0D);
        int ir = (int) (Math.pow((double) fr, 0.45454545454545453D) * 255.0D);
        int ig = (int) (Math.pow((double) fg, 0.45454545454545453D) * 255.0D);
        int ib = (int) (Math.pow((double) fb, 0.45454545454545453D) * 255.0D);
        if (ia < 96) ia = 0;
        return ia << 24 | ir << 16 | ig << 8 | ib;
    }

    private static int component(int a, int b, int c, int d, int shift) {
        float f = LINEAR[a >> shift & 255] + LINEAR[b >> shift & 255] + LINEAR[c >> shift & 255] + LINEAR[d >> shift & 255];
        float mixed = (float) Math.pow((double) f * 0.25D, 0.45454545454545453D);
        return (int) ((double) mixed * 255.0D);
    }

    public static void logAtlas(String atlas, int sprites, int readAhead, int mipmapped, long mipmapNanos, long totalNanos) {
        LOG.info("[Lads] atlas {}: {} sprites in {} ms ({} read ahead on {} threads, {} mipmapped at once in {} ms){}", atlas, sprites,
            totalNanos / 1000000L, readAhead, THREADS, mipmapped, mipmapNanos / 1000000L, ATLAS ? "" : ", parallel atlas off");
    }

    // Quad interning while the models bake (ModelManagerDedupeMixin189 opens and closes it; BakedQuadInternMixin189 and
    // UnpackedBakedQuadInternMixin189 feed it): quads with the same vertex numbers share one array. Baked quads are never written
    // to after they are built. Client thread only, and nothing is kept once the bake is over.
    private static Map<ArrayKey, Object> interned;
    private static Thread internThread;
    private static int arrays, shared;
    private static long sharedBytes;

    private static final class ArrayKey {
        final Object array;
        final int hash;

        ArrayKey(int[] array) { this.array = array; hash = Arrays.hashCode(array); }
        ArrayKey(float[] array) { this.array = array; hash = ~Arrays.hashCode(array); }

        @Override public int hashCode() { return hash; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ArrayKey) || ((ArrayKey) o).hash != hash) return false;
            Object other = ((ArrayKey) o).array;
            return array instanceof int[] ? other instanceof int[] && Arrays.equals((int[]) array, (int[]) other)
                : other instanceof float[] && Arrays.equals((float[]) array, (float[]) other);
        }
    }

    public static void internStart() {
        interned = LoadPerfPlugin189.QUADS ? new HashMap<ArrayKey, Object>() : null;
        internThread = Thread.currentThread();
        arrays = shared = 0;
        sharedBytes = 0;
    }

    public static String internEnd() {
        if (interned == null) return "quad interning off";
        interned = null;
        internThread = null;
        return "shared " + shared + " of " + arrays + " quad vertex arrays (" + sharedBytes / 1024 + " KB)";
    }

    public static int[] intern(int[] data) {
        if (interned == null || data == null || !isClientThread()) return data;
        arrays++;
        Object first = interned.putIfAbsent(new ArrayKey(data), data);
        if (first == null) return data;
        shared++;
        sharedBytes += 16 + 4L * data.length;
        return (int[]) first;
    }

    public static void intern(float[][][] data) {
        if (interned == null || data == null || !isClientThread()) return;
        for (float[][] vertex : data) {
            for (int e = 0; e < vertex.length; e++) {
                float[] element = vertex[e];
                if (element == null) continue;
                arrays++;
                Object first = interned.putIfAbsent(new ArrayKey(element), element);
                if (first != null) {
                    vertex[e] = (float[]) first;
                    shared++;
                    sharedBytes += 16 + 4L * element.length;
                }
            }
        }
    }

    /** The bake runs on the thread that opened the interning; a quad some mod builds on another thread meanwhile is left alone. */
    private static boolean isClientThread() {
        return Thread.currentThread() == internThread;
    }

    // QA: -Dlads.qa.loadstats=1
    private static final boolean STATS = System.getProperty("lads.qa.loadstats") != null;
    private static boolean titleSeen;

    public static void titleShown(File gameDir) {
        if (!STATS || titleSeen) return;
        titleSeen = true;
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        LOG.info("[Lads] load stats: title screen after {} ms of JVM uptime", uptime);
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(15000);
                System.gc();
                System.gc();
                Runtime rt = Runtime.getRuntime();
                long used = rt.totalMemory() - rt.freeMemory();
                LOG.info("[Lads] load stats: heap after full GC at the title {} MB (committed {} MB, max {} MB)",
                    used >> 20, rt.totalMemory() >> 20, rt.maxMemory() >> 20);
                Object histogram = ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"),
                    "gcClassHistogram", new Object[] {new String[0]}, new String[] {String[].class.getName()});
                File dir = new File(gameDir, "lads-qa/perf");
                dir.mkdirs();
                Files.write(new File(dir, "histogram-title.txt").toPath(), String.valueOf(histogram).getBytes(StandardCharsets.UTF_8));
                LOG.info("[Lads] load stats: class histogram in lads-qa/perf/histogram-title.txt");
            } catch (Throwable failed) {
                LOG.warn("[Lads] load stats failed", failed);
            }
        }, "Lads load stats");
        t.setDaemon(true);
        t.start();
    }
}
