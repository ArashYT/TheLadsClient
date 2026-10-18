import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * CPU attribution of one ladsbench JFR recording per scene (1.9.12 perf lane).
 * usage: java JfrAttr.java <run.jfr> <run result.json> <game mods dir>
 * Owner of a frame: the mod whose jar holds the class (nested jars count for their outer mod), or the mod named in a mixin
 * handler's merged method name ($modid$), else minecraft / jdk / lwjgl. Per scene and thread group: self (top frame owner) and
 * inclusive (owner anywhere on the stack) shares of Java + native samples, and the top self methods.
 */
public class JfrAttr {
    static final Map<String, String> owners = new HashMap<>();
    static final Set<String> ids = new HashSet<>();
    static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    public static void main(String[] a) throws Exception {
        for (Path jar : Files.list(Path.of(a[2])).filter(p -> p.toString().endsWith(".jar")).toList()) index(jar);
        String json = Files.readString(Path.of(a[1]));
        Matcher m = Pattern.compile("\"scene\":\"(\\w+)\".*?\"startEpochMs\":(\\d+),\"endEpochMs\":(\\d+)").matcher(json);
        List<Object[]> scenes = new ArrayList<>();
        while (m.find()) scenes.add(new Object[]{m.group(1), Long.parseLong(m.group(2)), Long.parseLong(m.group(3))});
        // scene -> group -> counters
        Map<String, Map<String, Map<String, Integer>>> self = new LinkedHashMap<>(), incl = new LinkedHashMap<>(), methods = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> totals = new LinkedHashMap<>();
        Map<String, Map<String, Long>> allocOwner = new LinkedHashMap<>(), allocSite = new LinkedHashMap<>();
        Map<String, long[]> gcPause = new LinkedHashMap<>(); // scene -> {count, total ns, max ns}
        // optional 4th argument: method name fragments; prints the call chains that reach them (CPU samples and allocation bytes)
        List<String> probes = a.length > 3 ? List.of(a[3].split(",")) : List.of();
        Map<String, Map<String, Long>> chains = new TreeMap<>();
        try (RecordingFile rf = new RecordingFile(Path.of(a[0]))) {
            while (rf.hasMoreEvents()) {
                RecordedEvent e = rf.readEvent();
                String type = e.getEventType().getName();
                long t = e.getStartTime().toEpochMilli();
                String scene = null;
                for (Object[] s : scenes) if (t >= (long) s[1] && t <= (long) s[2]) scene = (String) s[0];
                if (scene == null) continue;
                if (type.equals("jdk.GCPhasePause")) {
                    long d = e.getDuration().toNanos();
                    long[] g = gcPause.computeIfAbsent(scene, k -> new long[3]);
                    g[0]++; g[1] += d; g[2] = Math.max(g[2], d);
                    continue;
                }
                if (type.equals("jdk.ObjectAllocationSample") && e.getStackTrace() != null) {
                    long w = e.getLong("weight");
                    List<RecordedFrame> fr = e.getStackTrace().getFrames();
                    String own = "jdk", site = "?";
                    for (RecordedFrame f : fr) {
                        String o = owner(f);
                        if (o.equals("jdk")) continue;
                        own = o; site = f.getMethod().getType().getName() + "." + f.getMethod().getName(); break;
                    }
                    probe(probes, chains, "alloc", fr, w);
                    allocOwner.computeIfAbsent(scene, k -> new HashMap<>()).merge(own, w, Long::sum);
                    allocSite.computeIfAbsent(scene, k -> new HashMap<>()).merge(site, w, Long::sum);
                    continue;
                }
                if (!type.equals("jdk.ExecutionSample") && !type.equals("jdk.NativeMethodSample")) continue;
                if (e.getStackTrace() == null) continue;
                var th = e.getThread("sampledThread");
                String name = th == null ? "?" : th.getJavaName();
                String group = name == null ? "other" : name.equals("Render thread") ? "render" : name.equals("Server thread") ? "server" : "other";
                List<RecordedFrame> frames = e.getStackTrace().getFrames();
                if (frames.isEmpty()) continue;
                totals.computeIfAbsent(scene, k -> new TreeMap<>()).merge(group, 1, Integer::sum);
                if (group.equals("render")) probe(probes, chains, "cpu", frames, 1);
                String top = owner(frames.get(0));
                self.computeIfAbsent(scene, k -> new TreeMap<>()).computeIfAbsent(group, k -> new HashMap<>()).merge(top, 1, Integer::sum);
                Set<String> seen = new HashSet<>();
                for (RecordedFrame f : frames) seen.add(owner(f));
                for (String o : seen) incl.computeIfAbsent(scene, k -> new TreeMap<>()).computeIfAbsent(group, k -> new HashMap<>()).merge(o, 1, Integer::sum);
                var f0 = frames.get(0).getMethod();
                methods.computeIfAbsent(scene, k -> new TreeMap<>()).computeIfAbsent(group, k -> new HashMap<>())
                    .merge(f0.getType().getName() + "." + f0.getName(), 1, Integer::sum);
            }
        }
        for (var c : chains.entrySet()) {
            long tot = c.getValue().values().stream().mapToLong(Long::longValue).sum();
            System.out.println("## " + c.getKey() + " total " + tot);
            c.getValue().entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).limit(10)
                .forEach(x -> System.out.printf(Locale.ROOT, "   %5.1f%%  %s%n", 100.0 * x.getValue() / tot, x.getKey()));
        }
        for (String scene : totals.keySet()) {
            System.out.println("== " + scene + " samples " + totals.get(scene));
            for (String g : List.of("render", "server", "other")) {
                Integer total = totals.get(scene).get(g);
                if (total == null) continue;
                System.out.println("  [" + g + "] self: " + top(self.get(scene).get(g), total, 10));
                System.out.println("  [" + g + "] incl: " + top(incl.get(scene).get(g), total, 12));
                if (!g.equals("other")) System.out.println("  [" + g + "] hot:  " + top(methods.get(scene).get(g), total, 8));
            }
            long[] gc = gcPause.getOrDefault(scene, new long[3]);
            System.out.printf(Locale.ROOT, "  [gc] pauses %d, total %.1f ms, max %.1f ms%n", gc[0], gc[1] / 1e6, gc[2] / 1e6);
            Map<String, Long> ao = allocOwner.getOrDefault(scene, Map.of());
            long at = ao.values().stream().mapToLong(Long::longValue).sum();
            System.out.println("  [alloc] " + at / (1 << 20) + " MB sampled; by owner: " + topL(ao, at, 10));
            System.out.println("  [alloc] sites: " + topL(allocSite.getOrDefault(scene, Map.of()), at, 12));
        }
    }

    static String top(Map<String, Integer> m, int total, int n) {
        StringBuilder sb = new StringBuilder();
        m.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(n)
            .forEach(x -> sb.append(String.format(Locale.ROOT, "%s %.1f%%, ", x.getKey(), 100.0 * x.getValue() / total)));
        return sb.toString();
    }

    /** For each probe fragment found in a frame's method: the 6 frames below it (its callers) as one chain. */
    static void probe(List<String> probes, Map<String, Map<String, Long>> chains, String kind, List<RecordedFrame> frames, long w) {
        for (String p : probes) {
            for (int i = 0; i < frames.size(); i++) {
                var m = frames.get(i).getMethod();
                if (!(m.getType().getName() + "." + m.getName()).contains(p)) continue;
                StringBuilder sb = new StringBuilder();
                for (int j = i + 1; j < Math.min(frames.size(), i + 7); j++) {
                    var c = frames.get(j).getMethod();
                    String cls = c.getType().getName();
                    sb.append(cls.substring(cls.lastIndexOf('.') + 1)).append('.').append(c.getName()).append(" < ");
                }
                chains.computeIfAbsent(kind + " " + p, k -> new HashMap<>()).merge(sb.toString(), w, Long::sum);
                break;
            }
        }
    }

    static String topL(Map<String, Long> m, long total, int n) {
        StringBuilder sb = new StringBuilder();
        m.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).limit(n)
            .forEach(x -> sb.append(String.format(Locale.ROOT, "%s %.1f%%, ", x.getKey(), 100.0 * x.getValue() / Math.max(1, total))));
        return sb.toString();
    }

    static String owner(RecordedFrame f) {
        String cls = f.getMethod().getType().getName(), meth = f.getMethod().getName();
        int i = meth.indexOf('$');
        if (i >= 0) for (String part : meth.split("\\$")) if (ids.contains(part) && !part.equals("minecraft")) return part; // mixin handler
        String o = owners.get(cls);
        if (o != null) return o;
        if (cls.startsWith("net.minecraft.") || cls.startsWith("com.mojang.")) return "minecraft";
        if (cls.startsWith("org.lwjgl.")) return "lwjgl";
        if (cls.startsWith("java.") || cls.startsWith("jdk.") || cls.startsWith("sun.") || cls.startsWith("com.sun.")) return "jdk";
        if (cls.startsWith("net.fabricmc.") || cls.startsWith("org.spongepowered.")) return "fabric-loader";
        int d = cls.indexOf('.', cls.indexOf('.') + 1);
        return "?" + (d > 0 ? cls.substring(0, d) : cls);
    }

    static void index(Path jar) throws IOException {
        try (ZipFile z = new ZipFile(jar.toFile())) {
            String id = modId(z.getEntry("fabric.mod.json") == null ? null : z.getInputStream(z.getEntry("fabric.mod.json")));
            if (id == null) id = jar.getFileName().toString();
            ids.add(id);
            for (ZipEntry e : Collections.list(z.entries())) {
                String n = e.getName();
                if (n.endsWith(".class")) owners.putIfAbsent(n.substring(0, n.length() - 6).replace('/', '.'), id);
                else if (n.startsWith("META-INF/jars/") && n.endsWith(".jar")) nested(z.getInputStream(e).readAllBytes(), id);
            }
        }
    }

    static void nested(byte[] data, String id) throws IOException {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(data))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                String n = e.getName();
                if (n.endsWith(".class")) owners.putIfAbsent(n.substring(0, n.length() - 6).replace('/', '.'), id);
                else if (n.equals("fabric.mod.json")) { String inner = modId(new ByteArrayInputStream(z.readAllBytes())); if (inner != null) ids.add(inner); }
                else if (n.startsWith("META-INF/jars/") && n.endsWith(".jar")) nested(z.readAllBytes(), id);
            }
        }
    }

    static String modId(InputStream in) throws IOException {
        if (in == null) return null;
        Matcher m = ID.matcher(new String(in.readAllBytes()));
        return m.find() ? m.group(1) : null;
    }
}
