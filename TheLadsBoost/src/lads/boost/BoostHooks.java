package lads.boost;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

/** Hook target called from the rewritten KnotClassDelegate. Every entry point is exception-safe. */
public final class BoostHooks {
    static final String P = "[LadsBoost] ";
    static final int MAGIC = 0x4C424331; // LBC1
    static final String MARK = "#TRIGGER";

    static volatile boolean disabled;
    static boolean cacheOn, preloadOn, eager, cachedOnly, debug;
    static int preloadThreads;
    static String trigger;
    static Path dir;
    static byte[] key;
    static String keyHex = "";

    // loaded cache
    static byte[] dat;
    static Map<String, int[]> idx; // name -> {offset,len}
    static boolean keyOk;
    // this run
    static final ConcurrentHashMap<String, byte[]> fresh = new ConcurrentHashMap<>();
    static final ConcurrentLinkedQueue<String> order = new ConcurrentLinkedQueue<>();
    static final Set<String> seen = ConcurrentHashMap.newKeySet();
    static List<String> loadedOrder; // non-null when a valid order file was read
    static final AtomicBoolean triggered = new AtomicBoolean(), eagerDone = new AtomicBoolean(), failLogged = new AtomicBoolean();
    static final LongAdder skipped = new LongAdder(), hits = new LongAdder(), misses = new LongAdder(), lookupNs = new LongAdder();
    static final AtomicInteger pOk = new AtomicInteger(), pFail = new AtomicInteger(), pDone = new AtomicInteger();
    static volatile long writtenBytes;
    static int lastWrittenFresh = 0, lastWrittenOrder = 0;
    static MethodHandle canTransform;
    static boolean canTransformResolved;
    static Field clField;
    static boolean clFieldResolved;
    static BufferedWriter dbg;
    static final java.io.PrintStream ERR = System.err; // MC/log4j replaces System.err later; keep the real one

    static final class PreloadThread extends Thread {
        PreloadThread(Runnable r, String n) { super(r, n); setDaemon(true); }
    }

    // ---------------------------------------------------------------- init
    static void init() {
        try {
            cacheOn = bool("lads.boost.cache", true);
            preloadOn = bool("lads.boost.preload", true);
            eager = !"lazy".equalsIgnoreCase(System.getProperty("lads.boost.prepare", "eager"));
            cachedOnly = bool("lads.boost.preloadCachedOnly", true);
            debug = bool("lads.boost.debug", false);
            preloadThreads = Math.max(1, Integer.getInteger("lads.boost.preloadThreads", 1));
            trigger = System.getProperty("lads.boost.preloadTrigger", "net.minecraft.client.main.Main");
            Path game = gameDir();
            String d = System.getProperty("lads.boost.dir");
            dir = d != null ? Paths.get(d) : game.resolve(".lads-boost");
            Files.createDirectories(dir);
            key = computeKey(game);
            keyHex = hex(key);
            String load = "none";
            if (cacheOn) load = loadCache();
            if (preloadOn) loadOrder();
            if (debug) dbg = Files.newBufferedWriter(dir.resolve("debug.log"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log("init cache=" + cacheOn + " (" + load + ", key " + (keyOk ? "ok" : "mismatch/none") + ", entries="
                    + (idx == null ? 0 : idx.size()) + ", bytes=" + (dat == null ? 0 : dat.length) + ") prepare="
                    + (eager ? "eager" : "lazy") + " preload=" + preloadOn + " threads=" + preloadThreads
                    + " cachedOnly=" + cachedOnly + " order=" + (loadedOrder == null ? 0 : loadedOrder.size())
                    + " trigger=" + trigger + " dir=" + dir);
            Runtime.getRuntime().addShutdownHook(new Thread(BoostHooks::finish, "lads-boost-flush"));
            int sec = Integer.getInteger("lads.boost.flushAfterSec", 90);
            Thread tm = new Thread(() -> {
                try { Thread.sleep(sec * 1000L); } catch (InterruptedException e) { return; }
                finish();
            }, "lads-boost-timer");
            tm.setDaemon(true);
            tm.start();
        } catch (Throwable t) {
            fail("init", t);
        }
    }

    static boolean bool(String k, boolean def) { return Boolean.parseBoolean(System.getProperty(k, String.valueOf(def))); }

    static Path gameDir() {
        String g = System.getProperty("lads.boost.gameDir");
        if (g == null) g = System.getProperty("fabric.gameDir");
        if (g == null) {
            String[] a = System.getProperty("sun.java.command", "").split(" ");
            for (int i = 0; i < a.length - 1; i++) if (a[i].equals("--gameDir")) { g = a[i + 1]; break; }
        }
        return Paths.get(g != null ? g : System.getProperty("user.dir")).toAbsolutePath();
    }

    // ---------------------------------------------------------------- key
    static byte[] computeKey(Path game) throws Exception {
        List<String> l = new ArrayList<>();
        l.add("jv=" + System.getProperty("java.version") + "|" + System.getProperty("java.vm.version"));
        l.add("salt=" + System.getProperty("lads.boost.salt", ""));
        l.add("agent=2"); // bump when cache semantics change
        // the fabric-loader jar is on java.class.path (Knot is started from it), so it is covered here
        for (String e : System.getProperty("java.class.path", "").split(File.pathSeparator)) if (!e.isEmpty()) l.add("cp=" + fp(Paths.get(e)));
        for (String p : new String[] {"fabric.gameJarPath", "fabric.gameJarPath.client"}) {
            String v = System.getProperty(p);
            if (v != null) for (String e : v.split(File.pathSeparator)) l.add(p + "=" + fp(Paths.get(e)));
        }
        Set<Path> roots = new LinkedHashSet<>();
        String mods = System.getProperty("lads.boost.mods");
        roots.add(mods != null ? Paths.get(mods) : game.resolve("mods"));
        String mf = System.getProperty("fabric.modsFolder");
        if (mf != null) roots.add(Paths.get(mf));
        String am = System.getProperty("fabric.addMods");
        if (am != null) for (String e : am.split(File.pathSeparator)) if (!e.isEmpty()) roots.add(Paths.get(e));
        for (Path r : roots) walk(r, l, "mods=");
        Path cfg = game.resolve("config");
        if (Files.isDirectory(cfg)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(cfg)) {
                for (Path f : ds) {
                    String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (Files.isRegularFile(f) && (n.endsWith("mixins.properties") || n.contains("mixin"))) l.add("cfg=" + f.toAbsolutePath().normalize() + "|" + hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f)))); // content, not mtime: mods rewrite these on every launch
                }
            }
        }
        Collections.sort(l);
        if (debug) { Path kp = dir.resolve("key.txt"); if (Files.exists(kp)) Files.move(kp, dir.resolve("key.prev.txt"), StandardCopyOption.REPLACE_EXISTING); Files.write(kp, l, StandardCharsets.UTF_8); }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String s : l) { md.update(s.getBytes(StandardCharsets.UTF_8)); md.update((byte) '\n'); }
        return md.digest();
    }

    static void walk(Path r, List<String> out, String pre) throws IOException {
        if (Files.isRegularFile(r)) { out.add(pre + fp(r)); return; }
        if (!Files.isDirectory(r)) { out.add(pre + r.toAbsolutePath() + "|absent"); return; }
        Files.walkFileTree(r, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                out.add(pre + f.toAbsolutePath() + "|" + a.size() + "|" + a.lastModifiedTime().toMillis());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static String fp(Path p) {
        try {
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
            return p.toAbsolutePath().normalize() + "|" + a.size() + "|" + a.lastModifiedTime().toMillis();
        } catch (IOException e) { return p.toAbsolutePath().normalize() + "|absent"; }
    }

    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }

    // ---------------------------------------------------------------- load
    static String loadCache() {
        Path ip = dir.resolve("cache.idx"), dp = dir.resolve("cache.dat");
        if (!Files.exists(ip) || !Files.exists(dp)) return "empty";
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(ip)));
            if (in.readInt() != MAGIC) throw new IOException("bad magic");
            byte[] k = new byte[32]; in.readFully(k);
            long dlen = in.readLong(), crc = in.readLong();
            int n = in.readInt();
            if (!Arrays.equals(k, key)) return "stale";
            byte[] d = Files.readAllBytes(dp);
            if (d.length != dlen) throw new IOException("data length " + d.length + " != " + dlen);
            CRC32 c = new CRC32(); c.update(d);
            if (c.getValue() != crc) throw new IOException("crc mismatch");
            HashMap<String, int[]> m = new HashMap<>(n * 2);
            for (int i = 0; i < n; i++) {
                String nm = in.readUTF(); int off = in.readInt(), len = in.readInt();
                if (off < 0 || len < 0 || (long) off + len > d.length) throw new IOException("bad entry " + nm);
                m.put(nm, new int[] {off, len});
            }
            dat = d; idx = m; keyOk = true;
            return "loaded";
        } catch (Throwable t) {
            idx = null; dat = null; keyOk = false;
            log("cache file unusable, rebuilding: " + t);
            return "corrupt";
        }
    }

    static void loadOrder() {
        Path p = dir.resolve("order.txt");
        try {
            if (!Files.exists(p)) return;
            List<String> ls = Files.readAllLines(p, StandardCharsets.UTF_8);
            if (ls.isEmpty() || !ls.get(0).equals("KEY " + keyHex)) return;
            loadedOrder = new ArrayList<>(ls.subList(1, ls.size()));
        } catch (Throwable t) { loadedOrder = null; log("order file unusable: " + t); }
    }

    // ---------------------------------------------------------------- hooks
    public static byte[] lookup(Object delegate, String name, boolean init) {
        if (disabled) return null;
        long s = System.nanoTime();
        try {
            if (preloadOn && name != null && !(Thread.currentThread() instanceof PreloadThread)) {
                if (trigger != null && !triggered.get() && name.startsWith(trigger) && triggered.compareAndSet(false, true)) {
                    order.add(MARK);
                    startPreload(delegate);
                }
                if (seen.add(name)) order.add(name);
            }
            if (!init || !cacheOn || name == null) return null; // pre-transform phase: exactly as before
            if (eager && !eagerDone.get() && canTransform(delegate, name) && eagerDone.compareAndSet(false, true)) {
                misses.increment();
                dbg("EAGER-ORIG " + name);
                return null;
            }
            int[] e = idx == null ? null : idx.get(name);
            if (e != null) {
                hits.increment();
                dbg("HIT " + name);
                return Arrays.copyOfRange(dat, e[0], e[0] + e[1]);
            }
            misses.increment();
            dbg("MISS " + name);
            return null;
        } catch (Throwable t) {
            fail("lookup", t);
            return null;
        } finally {
            lookupNs.add(System.nanoTime() - s);
        }
    }

    public static void store(Object delegate, String name, byte[] bytes, boolean init) {
        if (disabled || !init || !cacheOn || bytes == null || name == null) return;
        try {
            if (refsGenerated(bytes)) { skipped.increment(); dbg("SKIP-GENERATED " + name); return; }
            fresh.putIfAbsent(name, bytes);
        } catch (Throwable t) { fail("store", t); }
    }

    /** Transformed bytes that reference a class generated at runtime (MixinExtras ref/generated/*) must not be replayed:
     *  the generator only runs while the class is being transformed, so a cache hit leaves the generated class undefined. */
    static boolean refsGenerated(byte[] b) {
        final byte[] p = GEN;
        outer:
        for (int i = 0, n = b.length - p.length; i <= n; i++) {
            if (b[i] != p[0]) continue;
            for (int j = 1; j < p.length; j++) if (b[i + j] != p[j]) continue outer;
            return true;
        }
        return false;
    }
    static final byte[] GEN = "/generated/".getBytes(StandardCharsets.US_ASCII);

    static boolean canTransform(Object d, String name) {
        try {
            if (!canTransformResolved) {
                synchronized (BoostHooks.class) {
                    if (!canTransformResolved) {
                        try {
                            MethodHandles.Lookup lk = MethodHandles.privateLookupIn(d.getClass(), MethodHandles.lookup());
                            canTransform = lk.findStatic(d.getClass(), "canTransformClass", MethodType.methodType(boolean.class, String.class));
                        } catch (Throwable t) { canTransform = null; }
                        canTransformResolved = true;
                    }
                }
            }
            return canTransform == null || (boolean) canTransform.invoke(name);
        } catch (Throwable t) { return true; }
    }

    // ---------------------------------------------------------------- preload
    static void startPreload(Object delegate) {
        List<String> all = loadedOrder;
        if (all == null) return;
        int m = all.indexOf(MARK);
        if (m < 0) return;
        ClassLoader cl = knotLoader(delegate);
        if (cl == null) { log("preload skipped: no class loader"); return; }
        List<String> todo = new ArrayList<>();
        for (String n : all.subList(m + 1, all.size())) {
            if (cachedOnly && (idx == null || !idx.containsKey(n))) continue;
            todo.add(n);
        }
        long st = System.nanoTime();
        int nt = preloadThreads;
        for (int i = 0; i < nt; i++) {
            final int off = i;
            PreloadThread th = new PreloadThread(() -> {
                try {
                    for (int j = off; j < todo.size() && !disabled; j += nt) {
                        try { Class.forName(todo.get(j), false, cl); pOk.incrementAndGet(); }
                        catch (Throwable t) { pFail.incrementAndGet(); dbg("PRELOAD-FAIL " + todo.get(j) + " " + t); }
                    }
                } catch (Throwable t) { fail("preload", t); }
                if (pDone.incrementAndGet() == nt)
                    log("preload done: tried=" + todo.size() + " ok=" + pOk + " failed=" + pFail + " ms=" + (System.nanoTime() - st) / 1_000_000);
            }, "lads-boost-preload-" + i);
            th.start();
        }
    }

    static ClassLoader knotLoader(Object d) {
        try {
            if (!clFieldResolved) { clField = d.getClass().getDeclaredField("classLoader"); clField.setAccessible(true); clFieldResolved = true; }
            return (ClassLoader) clField.get(d);
        } catch (Throwable t) { return Thread.currentThread().getContextClassLoader(); }
    }

    // ---------------------------------------------------------------- finalize
    static synchronized void finish() {
        if (disabled || dir == null) return;
        long s = System.nanoTime();
        try {
            int fsz = fresh.size();
            if (cacheOn && fsz > lastWrittenFresh) { writeCache(); lastWrittenFresh = fsz; }
            if (preloadOn && loadedOrder == null && triggered.get()) {
                int osz = order.size();
                if (osz > lastWrittenOrder) { writeOrder(); lastWrittenOrder = osz; }
            }
            dbgFlush();
            log("final hits=" + hits + " misses=" + misses + " stored=" + fresh.size() + " skippedGenerated=" + skipped + " lookupMs=" + lookupNs.sum() / 1_000_000
                    + " written=" + writtenBytes + " finishMs=" + (System.nanoTime() - s) / 1_000_000);
        } catch (Throwable t) { fail("finish", t); }
    }

    static void writeCache() throws Exception {
        Path dt = dir.resolve("cache.dat.tmp"), it = dir.resolve("cache.idx.tmp");
        LinkedHashMap<String, long[]> ent = new LinkedHashMap<>();
        CRC32 crc = new CRC32();
        long pos = 0;
        try (CheckedOutputStream out = new CheckedOutputStream(new BufferedOutputStream(Files.newOutputStream(dt), 1 << 20), crc)) {
            if (keyOk && idx != null) for (Map.Entry<String, int[]> e : idx.entrySet()) {
                if (fresh.containsKey(e.getKey())) continue;
                out.write(dat, e.getValue()[0], e.getValue()[1]);
                ent.put(e.getKey(), new long[] {pos, e.getValue()[1]}); pos += e.getValue()[1];
            }
            for (Map.Entry<String, byte[]> e : fresh.entrySet()) {
                out.write(e.getValue());
                ent.put(e.getKey(), new long[] {pos, e.getValue().length}); pos += e.getValue().length;
            }
        }
        if (pos > Integer.MAX_VALUE) throw new IOException("cache too large");
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(it)))) {
            o.writeInt(MAGIC); o.write(key); o.writeLong(pos); o.writeLong(crc.getValue()); o.writeInt(ent.size());
            for (Map.Entry<String, long[]> e : ent.entrySet()) { o.writeUTF(e.getKey()); o.writeInt((int) e.getValue()[0]); o.writeInt((int) e.getValue()[1]); }
        }
        Files.move(dt, dir.resolve("cache.dat"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        Files.move(it, dir.resolve("cache.idx"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        writtenBytes = pos;
    }

    static void writeOrder() throws IOException {
        List<String> l = new ArrayList<>(); l.add("KEY " + keyHex); l.addAll(order);
        Path t = dir.resolve("order.txt.tmp");
        Files.write(t, l, StandardCharsets.UTF_8);
        Files.move(t, dir.resolve("order.txt"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ---------------------------------------------------------------- util
    static void fail(String where, Throwable t) {
        disabled = true;
        if (failLogged.compareAndSet(false, true)) {
            log("DISABLED after error in " + where + ": " + t);
            try { t.printStackTrace(ERR); } catch (Throwable ignored) { }
        }
    }

    static void log(String s) { ERR.println(P + s); }

    static void dbg(String s) {
        BufferedWriter w = dbg;
        if (w == null) return;
        try { synchronized (w) { w.write(s); w.newLine(); } } catch (Throwable ignored) { }
    }

    static void dbgFlush() { BufferedWriter w = dbg; if (w != null) try { synchronized (w) { w.flush(); } } catch (Throwable ignored) { } }
}
