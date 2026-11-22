package lads.boost;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
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

/** Hook target called from the rewritten KnotClassDelegate (Fabric) or LaunchClassLoader (RetroFuturaBootstrap, 1.8.9).
 *  Every entry point is exception-safe. */
public final class BoostHooks {
    static final String P = "[LadsBoost] ";
    static final int MAGIC = 0x4C424332; // LBC2: index names a data file, per-entry CRC32
    static final String MARK = "#TRIGGER";

    static volatile boolean disabled;
    static boolean cacheOn, preloadOn, eager, cachedOnly, safeSupers, debug;
    static int preloadThreads;
    static String trigger;
    static Path dir;
    static byte[] key;
    static String keyHex = "";

    /** One mapped cache generation. Replaced as a whole after a flush; readers take one reference. */
    static final class Cache {
        final MappedByteBuffer buf; final Map<String, long[]> idx; final String file; // name -> {offset, len, crc}
        Cache(MappedByteBuffer b, Map<String, long[]> i, String f) { buf = b; idx = i; file = f; }
    }
    static volatile Cache cache;
    static boolean keyOk;
    // this run
    static final ConcurrentHashMap<String, byte[]> fresh = new ConcurrentHashMap<>();
    static final ConcurrentLinkedQueue<String> order = new ConcurrentLinkedQueue<>();
    static final Set<String> seen = ConcurrentHashMap.newKeySet();
    static final Set<String> repair = ConcurrentHashMap.newKeySet(); // cached entries that failed their CRC this run
    static List<String> loadedOrder; // non-null when a valid order file was read
    static final AtomicBoolean triggered = new AtomicBoolean(), eagerDone = new AtomicBoolean(), failLogged = new AtomicBoolean(), badLogged = new AtomicBoolean();
    static final LongAdder skipped = new LongAdder(), hits = new LongAdder(), misses = new LongAdder(), bad = new LongAdder(), lookupNs = new LongAdder();
    static final AtomicInteger pOk = new AtomicInteger(), pFail = new AtomicInteger(), pDone = new AtomicInteger(), pUnsafe = new AtomicInteger();
    static volatile long writtenBytes;
    static int lastWrittenOrder = 0;
    static boolean finishedOnce;
    static MethodHandle canTransform;
    static boolean canTransformResolved;
    static Field clField;
    static boolean clFieldResolved;
    static BufferedWriter dbg;
    // Mixin configs added while the game starts (a mod's plugin or entrypoint calling Mixins.addConfiguration, e.g. ReplayMod)
    // are prepared on the next transform, and Mixin refuses one whose target class is already loaded. A hit skips that
    // transform, so hits are served only while every config has been visited; pre-loads wait until an original transform has
    // finished with as many configs as the recorded start had (they would otherwise define a target ahead of its config).
    static MethodHandle mxConfigs, mxUnvisited;
    static volatile boolean mxResolved, mxPreloadOk;
    static volatile int mxCleanSize = -1;
    static int mxExpect = -1; // config count of the recorded start (order file), -1 = unknown / no Mixin
    static final LongAdder mxPending = new LongAdder();
    static final java.io.PrintStream ERR = System.err; // MC/log4j replaces System.err later; keep the real one

    static final class PreloadThread extends Thread {
        PreloadThread(Runnable r, String n) { super(r, n); setDaemon(true); }
    }

    // ---------------------------------------------------------------- init
    /** Kill switch: -Dlads.boost=false or a file named "off" in the data dir turns the agent off before anything is patched. */
    static boolean killed() {
        try {
            if (!bool("lads.boost", true)) { log("off (lads.boost=false)"); return true; }
            Path d = dataDir();
            if (Files.exists(d.resolve("off"))) { log("off (" + d.resolve("off") + " exists)"); return true; }
            return false;
        } catch (Throwable t) { return false; }
    }

    static Path dataDir() {
        String d = System.getProperty("lads.boost.dir");
        return d != null ? Paths.get(d) : gameDir().resolve(".lads-boost");
    }

    static void init() {
        try {
            cacheOn = bool("lads.boost.cache", true);
            preloadOn = bool("lads.boost.preload", true);
            eager = !"lazy".equalsIgnoreCase(System.getProperty("lads.boost.prepare", "eager"));
            cachedOnly = bool("lads.boost.preloadCachedOnly", true);
            safeSupers = bool("lads.boost.preloadSafeSupers", true);
            debug = bool("lads.boost.debug", false);
            preloadThreads = Math.max(1, Integer.getInteger("lads.boost.preloadThreads",
                    Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 2))));
            trigger = System.getProperty("lads.boost.preloadTrigger", "net.minecraft.client.main.Main");
            Path game = gameDir();
            dir = dataDir();
            Files.createDirectories(dir);
            // Crash guard: the marker lives from start to the first flush. Still there = the last run died before it
            // (crash, hang killed by the player): start from nothing once rather than replay what that run left.
            Path pend = dir.resolve("pending");
            String guard = "";
            if (Files.exists(pend)) {
                guard = " (last run ended before its flush: cache reset)";
                for (String f : new String[] {"cache.idx", "order.txt"}) Files.deleteIfExists(dir.resolve(f));
            }
            Files.write(pend, new byte[0]);
            key = computeKey(game);
            keyHex = hex(key);
            String load = "none";
            if (cacheOn) load = loadCache();
            sweepDataFiles();
            if (preloadOn) loadOrder();
            if (debug) dbg = Files.newBufferedWriter(dir.resolve("debug.log"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Cache c = cache;
            log("init cache=" + cacheOn + " (" + load + ", key " + (keyOk ? "ok" : "mismatch/none") + ", entries="
                    + (c == null ? 0 : c.idx.size()) + ", mapped=" + (c == null ? 0 : c.buf.capacity()) + ") prepare="
                    + (eager ? "eager" : "lazy") + " preload=" + preloadOn + " threads=" + preloadThreads
                    + " cachedOnly=" + cachedOnly + " safeSupers=" + safeSupers + " order=" + (loadedOrder == null ? 0 : loadedOrder.size())
                    + " trigger=" + trigger + " dir=" + dir + guard);
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
        l.add("agent=4"); // bump when cache semantics change
        // the fabric-loader / RFB jar is on java.class.path, so it is covered here
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
    /** Index check only (magic, key, data file name and length); the data file is mapped, not read. Each entry carries
     *  its own CRC32, checked when that entry is served. */
    static String loadCache() {
        Path ip = dir.resolve("cache.idx");
        if (!Files.exists(ip)) return "empty";
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(ip)));
            if (in.readInt() != MAGIC) throw new IOException("bad magic");
            byte[] k = new byte[32]; in.readFully(k);
            String file = in.readUTF();
            long dlen = in.readLong();
            int n = in.readInt();
            if (!Arrays.equals(k, key)) return "stale";
            if (file.contains("/") || file.contains("\\") || !file.startsWith("cache-")) throw new IOException("bad data file name " + file);
            Path dp = dir.resolve(file);
            if (Files.size(dp) != dlen) throw new IOException("data length " + Files.size(dp) + " != " + dlen);
            HashMap<String, long[]> m = new HashMap<>(n * 2);
            for (int i = 0; i < n; i++) {
                String nm = in.readUTF(); long off = in.readInt() & 0xFFFFFFFFL; int len = in.readInt(); long crc = in.readInt() & 0xFFFFFFFFL;
                if (len < 0 || off + len > dlen) throw new IOException("bad entry " + nm);
                m.put(nm, new long[] {off, len, crc});
            }
            cache = new Cache(map(dp, dlen), m, file); keyOk = true;
            return "mapped";
        } catch (Throwable t) {
            cache = null; keyOk = false;
            log("cache file unusable, rebuilding: " + t);
            return "corrupt";
        }
    }

    static MappedByteBuffer map(Path p, long len) throws IOException {
        try (FileChannel ch = FileChannel.open(p, StandardOpenOption.READ)) { return ch.map(FileChannel.MapMode.READ_ONLY, 0, len); }
    }

    /** Data files of older generations: a mapped file cannot be deleted on Windows, so they go at the next start. */
    static void sweepDataFiles() {
        Cache c = cache;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "cache*.dat*")) {
            for (Path f : ds) if (c == null || !f.getFileName().toString().equals(c.file)) try { Files.deleteIfExists(f); } catch (IOException ignored) { }
        } catch (Throwable ignored) { }
    }

    static void loadOrder() {
        Path p = dir.resolve("order.txt");
        try {
            if (!Files.exists(p)) return;
            List<String> ls = Files.readAllLines(p, StandardCharsets.UTF_8);
            if (ls.isEmpty() || !ls.get(0).equals("KEY " + keyHex)) return;
            int from = 1;
            if (ls.size() > 1 && ls.get(1).startsWith("MIXINCFG ")) { mxExpect = Integer.parseInt(ls.get(1).substring(9).trim()); from = 2; }
            loadedOrder = new ArrayList<>(ls.subList(from, ls.size()));
        } catch (Throwable t) { loadedOrder = null; log("order file unusable: " + t); }
    }

    /** The cached bytes of one class, CRC-checked; null on a miss or a bad entry (then the original path runs and stores fresh bytes). */
    static byte[] read(Cache c, String name) {
        long[] e = c.idx.get(name);
        if (e == null) return null;
        byte[] b = new byte[(int) e[1]];
        c.buf.get((int) e[0], b, 0, b.length);
        CRC32 x = new CRC32(); x.update(b);
        if (x.getValue() != e[2]) {
            bad.increment();
            repair.add(name);
            if (badLogged.compareAndSet(false, true)) log("bad cache entry " + name + ", served by the original path");
            return null;
        }
        return b;
    }

    // ---------------------------------------------------------------- hooks
    public static byte[] lookup(Object delegate, String name, boolean init) {
        if (disabled) return null;
        long s = System.nanoTime();
        try {
            if (preloadOn && name != null && !(Thread.currentThread() instanceof PreloadThread)) {
                if (trigger != null && !triggered.get() && name.startsWith(trigger) && triggered.compareAndSet(false, true)) {
                    order.add(MARK);
                    startPreload(delegate, name);
                }
                if (seen.add(name)) order.add(name);
            }
            if (!init || !cacheOn || name == null) return null; // pre-transform phase: exactly as before
            if (eager && !eagerDone.get() && canTransform(delegate, name) && eagerDone.compareAndSet(false, true)) {
                misses.increment();
                dbg("EAGER-ORIG " + name);
                return null;
            }
            Cache c = cache;
            byte[] b = c == null ? null : read(c, name);
            if (b != null && !mixinSettled(delegate)) {
                mxPending.increment();
                misses.increment();
                dbg("MIXIN-PENDING " + name);
                return null;
            }
            if (b != null) {
                hits.increment();
                dbg("HIT " + name);
                return b;
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
            // An original transform has finished here: every config it saw is prepared.
            if (!mxPreloadOk && mixinCount(delegate) >= mxExpect && mixinSettled(delegate)) mxPreloadOk = true;
            Cache c = cache;
            if (c != null && c.idx.containsKey(name) && !repair.contains(name)) return; // already cached (eager call): no rewrite
            if (refsGenerated(bytes)) { skipped.increment(); dbg("SKIP-GENERATED " + name); return; }
            fresh.putIfAbsent(name, bytes);
        } catch (Throwable t) { fail("store", t); }
    }

    // ---------------------------------------------------------------- RetroFuturaBootstrap (1.8.9 Forge)
    // LaunchClassLoader.findClass: raw bytes -> runTransformers (FML, Mixin, ...) -> runRfbTransformers (8to25, ...) -> define.
    // The patch wraps both: a hit in runTransformers returns the final bytes and makes the rfb step a no-op; a miss marks the
    // name so the rfb step stores its result. Nothing is served or stored before the trigger class (Main) has been loaded the
    // normal way: by then every tweaker has registered its transformers and Mixin is in its default phase.
    static volatile boolean armed;
    static final ThreadLocal<String> RFB = new ThreadLocal<>();

    public static byte[] rfbLookup(Object loader, String tname) {
        if (disabled || tname == null) { RFB.remove(); return null; }
        boolean init = armed;
        if (!init && trigger != null && tname.startsWith(trigger)) armed = true;
        byte[] c = lookup(loader, tname, init);
        if (c != null) RFB.set("H" + tname); else RFB.remove();
        return c;
    }

    /** After the original runTransformers returned (nested loads inside it have set and cleared their own marks). */
    public static void rfbPending(String tname) {
        if (!disabled && armed && tname != null) RFB.set("P" + tname); else RFB.remove();
    }

    /** 1 = cache hit, skip the rfb transformers; 2 = run them and store the result; 0 = run them, store nothing. */
    public static int rfbBefore(String tname) {
        String m = RFB.get();
        RFB.remove();
        if (m == null || tname == null || disabled || m.length() != tname.length() + 1 || !m.endsWith(tname)) return 0;
        return m.charAt(0) == 'H' ? 1 : 2;
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
    static void startPreload(Object delegate, String triggerName) {
        List<String> all = loadedOrder;
        if (all == null) return;
        int m = all.indexOf(MARK);
        if (m < 0) return;
        ClassLoader cl = knotLoader(delegate);
        if (cl == null) { log("preload skipped: no class loader"); return; }
        Cache c = cache;
        List<String> todo = new ArrayList<>();
        for (String n : all.subList(m + 1, all.size())) {
            if (cachedOnly && (c == null || !c.idx.containsKey(n))) continue;
            todo.add(n);
        }
        // Loaded (or being loaded by this thread's callers) before the trigger: a pre-load never has to load these.
        Set<String> before = new HashSet<>(seen);
        before.remove(triggerName);
        Map<String, Boolean> memo = new ConcurrentHashMap<>();
        long st = System.nanoTime();
        int nt = preloadThreads;
        for (int i = 0; i < nt; i++) {
            final int off = i;
            PreloadThread th = new PreloadThread(() -> {
                try {
                    // Nothing may load before Mixin prepared its configs (eager original call on the loading thread).
                    for (int w = 0; eager && !eagerDone.get() && !disabled && w < 20000; w++) Thread.sleep(1);
                    if (!eagerDone.get() && eager) { log("preload skipped: eager original call never came"); return; }
                    long ws = System.nanoTime();
                    // No original transform after the last config (all hits): accept a count that stayed settled for 200 ms.
                    for (int w = 0, steady = 0; !mxPreloadOk && !disabled && w < 20000; w++) {
                        Thread.sleep(1);
                        steady = mixinCount(delegate) >= mxExpect && mixinSettled(delegate) ? steady + 1 : 0;
                        if (steady >= 200) mxPreloadOk = true;
                    }
                    if (!mxPreloadOk) {
                        if (off == 0) log("preload skipped: mixin configs " + mixinCount(delegate) + " of " + mxExpect + " never settled");
                        return;
                    }
                    if (off == 0) mxWaitMs = (System.nanoTime() - ws) / 1_000_000;
                    for (int j = off; j < todo.size() && !disabled; j += nt) {
                        String n = todo.get(j);
                        // With several threads, a class whose supertypes would need the original transform path (Mixin, under its
                        // lock) is left to the main thread: a pre-load thread only ever defines cached bytes, so it never waits
                        // on the transformer while holding a class-loading lock the main thread needs.
                        if (cachedOnly && safeSupers && !safe(n, c, before, memo, 0)) { pUnsafe.incrementAndGet(); continue; }
                        // A config added after the wait: stop, the main thread loads the rest through the gate in lookup.
                        if (!mixinSettled(delegate)) { if (off == 0) log("preload stopped: a mixin config was added"); break; }
                        try { Class.forName(n, false, cl); pOk.incrementAndGet(); }
                        catch (Throwable t) { pFail.incrementAndGet(); dbg("PRELOAD-FAIL " + n + " " + t); }
                    }
                } catch (Throwable t) { fail("preload", t); }
                finally {
                    if (pDone.incrementAndGet() == nt)
                        log("preload done: tried=" + todo.size() + " ok=" + pOk + " failed=" + pFail + " unsafe=" + pUnsafe + " threads=" + nt + " mixinWaitMs=" + mxWaitMs + " ms=" + (System.nanoTime() - st) / 1_000_000);
                }
            }, "lads-boost-preload-" + i);
            th.start();
        }
    }

    static boolean safe(String n, Cache c, Set<String> before, Map<String, Boolean> memo, int depth) {
        if (n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("jdk.") || n.startsWith("sun.") || n.startsWith("com.sun.")) return true;
        if (before.contains(n)) return true;
        Boolean k = memo.get(n);
        if (k != null) return k;
        boolean ok = false;
        if (depth < 64 && c != null && c.idx.containsKey(n)) {
            byte[] b = read(c, n);
            String[] sup = b == null ? null : Supers.of(b);
            if (sup != null) {
                ok = true;
                for (String s : sup) if (!safe(s, c, before, memo, depth + 1)) { ok = false; break; }
            }
        }
        memo.put(n, ok);
        return ok;
    }

    static volatile long mxWaitMs;

    static void resolveMixin(Object d) {
        synchronized (BoostHooks.class) {
            if (mxResolved) return;
            try {
                // Knot: Mixin sits next to the delegate on the app class path. RFB: the hook owner is the LaunchClassLoader.
                ClassLoader l = d instanceof ClassLoader ? (ClassLoader) d : d.getClass().getClassLoader();
                Class<?> m = Class.forName("org.spongepowered.asm.mixin.Mixins", false, l);
                MethodHandles.Lookup lk = MethodHandles.publicLookup();
                mxConfigs = lk.findStatic(m, "getConfigs", MethodType.methodType(Set.class));
                mxUnvisited = lk.findStatic(m, "getUnvisitedCount", MethodType.methodType(int.class));
            } catch (Throwable t) { mxConfigs = null; mxUnvisited = null; } // no Mixin: nothing to wait for
            mxResolved = true;
        }
    }

    /** Number of registered Mixin configs, -1 without Mixin (d == null: only if already resolved). */
    static int mixinCount(Object d) {
        if (!mxResolved) { if (d == null) return -1; resolveMixin(d); }
        MethodHandle h = mxConfigs;
        if (h == null) return -1;
        try { return ((Set<?>) h.invoke()).size(); } catch (Throwable t) { return -1; }
    }

    /** True when no registered Mixin config waits to be visited (or there is no Mixin). */
    static boolean mixinSettled(Object d) {
        if (!mxResolved) resolveMixin(d);
        if (mxConfigs == null) return true;
        try {
            int n = ((Set<?>) mxConfigs.invoke()).size();
            if (n == mxCleanSize) return true;
            if ((int) mxUnvisited.invoke() != 0) return false;
            mxCleanSize = n;
            return true;
        } catch (Throwable t) { return false; } // e.g. a config being added right now: take the original path
    }

    static ClassLoader knotLoader(Object d) {
        if (d instanceof ClassLoader) return (ClassLoader) d; // RFB: the hook's owner is the LaunchClassLoader itself
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
            if (cacheOn && !fresh.isEmpty()) writeCache();
            if (preloadOn && loadedOrder == null && triggered.get()) {
                int osz = order.size();
                if (osz > lastWrittenOrder) { writeOrder(); lastWrittenOrder = osz; }
            }
            if (!finishedOnce) { finishedOnce = true; Files.deleteIfExists(dir.resolve("pending")); }
            dbgFlush();
            Cache c = cache;
            log("final hits=" + hits + " misses=" + misses + " bad=" + bad + " stored=" + (c == null ? 0 : c.idx.size()) + " skippedGenerated=" + skipped + " lookupMs=" + lookupNs.sum() / 1_000_000
                    + " written=" + writtenBytes + " finishMs=" + (System.nanoTime() - s) / 1_000_000 + " mixinPending=" + mxPending);
        } catch (Throwable t) { fail("finish", t); }
    }

    /** Writes a new data generation (old entries copied from the map, fresh ones appended), points the index at it, maps it,
     *  and drops the fresh bytes from the heap. */
    static void writeCache() throws Exception {
        Cache old = keyOk ? cache : null;
        String file = "cache-" + System.currentTimeMillis() + ".dat";
        Path dt = dir.resolve(file + ".tmp"), it = dir.resolve("cache.idx.tmp");
        LinkedHashMap<String, long[]> ent = new LinkedHashMap<>();
        Map<String, byte[]> wrote = new HashMap<>(fresh);
        long pos = 0;
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(dt), 1 << 20)) {
            if (old != null) {
                byte[] buf = new byte[1 << 16];
                for (Map.Entry<String, long[]> e : old.idx.entrySet()) {
                    if (wrote.containsKey(e.getKey())) continue;
                    long[] v = e.getValue(); int len = (int) v[1];
                    if (buf.length < len) buf = new byte[len];
                    old.buf.get((int) v[0], buf, 0, len);
                    out.write(buf, 0, len);
                    ent.put(e.getKey(), new long[] {pos, len, v[2]}); pos += len;
                }
            }
            CRC32 crc = new CRC32();
            for (Map.Entry<String, byte[]> e : wrote.entrySet()) {
                byte[] b = e.getValue();
                crc.reset(); crc.update(b);
                out.write(b);
                ent.put(e.getKey(), new long[] {pos, b.length, crc.getValue()}); pos += b.length;
            }
        }
        if (pos > 0xFFFFFFFFL) throw new IOException("cache too large");
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(it)))) {
            o.writeInt(MAGIC); o.write(key); o.writeUTF(file); o.writeLong(pos); o.writeInt(ent.size());
            for (Map.Entry<String, long[]> e : ent.entrySet()) { long[] v = e.getValue(); o.writeUTF(e.getKey()); o.writeInt((int) v[0]); o.writeInt((int) v[1]); o.writeInt((int) v[2]); }
        }
        Path dp = dir.resolve(file);
        Files.move(dt, dp, StandardCopyOption.ATOMIC_MOVE);
        Files.move(it, dir.resolve("cache.idx"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        cache = new Cache(map(dp, pos), ent, file); keyOk = true;
        for (Map.Entry<String, byte[]> e : wrote.entrySet()) fresh.remove(e.getKey(), e.getValue());
        if (old != null) try { Files.deleteIfExists(dir.resolve(old.file)); } catch (IOException ignored) { } // Windows: still mapped, swept next start
        writtenBytes = pos;
    }

    static void writeOrder() throws IOException {
        List<String> l = new ArrayList<>(); l.add("KEY " + keyHex);
        int n = mixinCount(null);
        if (n >= 0) l.add("MIXINCFG " + n);
        l.addAll(order);
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
