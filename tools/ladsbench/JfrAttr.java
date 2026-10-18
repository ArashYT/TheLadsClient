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
        try (RecordingFile rf = new RecordingFile(Path.of(a[0]))) {
            while (rf.hasMoreEvents()) {
                RecordedEvent e = rf.readEvent();
                String type = e.getEventType().getName();
                if (!type.equals("jdk.ExecutionSample") && !type.equals("jdk.NativeMethodSample")) continue;
                long t = e.getStartTime().toEpochMilli();
                String scene = null;
                for (Object[] s : scenes) if (t >= (long) s[1] && t <= (long) s[2]) scene = (String) s[0];
                if (scene == null || e.getStackTrace() == null) continue;
                var th = e.getThread("sampledThread");
                String name = th == null ? "?" : th.getJavaName();
                String group = name == null ? "other" : name.equals("Render thread") ? "render" : name.equals("Server thread") ? "server" : "other";
                List<RecordedFrame> frames = e.getStackTrace().getFrames();
                if (frames.isEmpty()) continue;
                totals.computeIfAbsent(scene, k -> new TreeMap<>()).merge(group, 1, Integer::sum);
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
        for (String scene : totals.keySet()) {
            System.out.println("== " + scene + " samples " + totals.get(scene));
            for (String g : List.of("render", "server", "other")) {
                Integer total = totals.get(scene).get(g);
                if (total == null) continue;
                System.out.println("  [" + g + "] self: " + top(self.get(scene).get(g), total, 10));
                System.out.println("  [" + g + "] incl: " + top(incl.get(scene).get(g), total, 12));
                if (!g.equals("other")) System.out.println("  [" + g + "] hot:  " + top(methods.get(scene).get(g), total, 8));
            }
        }
    }

    static String top(Map<String, Integer> m, int total, int n) {
        StringBuilder sb = new StringBuilder();
        m.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(n)
            .forEach(x -> sb.append(String.format(Locale.ROOT, "%s %.1f%%, ", x.getKey(), 100.0 * x.getValue() / total)));
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
