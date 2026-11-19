package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ghost replays (PROTOCOL.md section 19). A ghost is no entity: it is a pose the version hook draws as a translucent player
 * model, on the run clock, for the shot of one course. It carries no live position of anyone. Game thread only.
 */
public final class GhostState {
    public static final int MAX_GHOSTS = 3, MAX_PER_MESSAGE = 600, MAX_FRAMES = 2400, MAX_T = 3_600_000;
    public static final long PART_TIMEOUT_MS = 10_000, LINGER_MS = 1_000;

    /** {@code skin} is null unless the server named one: the hook uses it only if the client already knows that skin. */
    public record Pose(String id, String name, UUID skin, double x, double y, double z, double yaw, double pitch) {}

    private static final class Ghost {
        String id, course, name;
        UUID skin;
        int parts, next;
        long startedAt;
        double[] t = new double[0];
        final List<double[]> frames = new ArrayList<>(); // t, x, y, z, yaw, pitch
        boolean armed;
    }

    private final Map<String, Ghost> ghosts = new LinkedHashMap<>(), pending = new LinkedHashMap<>();

    public void clear() {
        ghosts.clear();
        pending.clear();
    }

    /** All ghosts end (run finish, reset, drop). */
    public void stopAll() {
        ghosts.clear();
    }

    public int armedCount() {
        return ghosts.size();
    }

    public int pendingCount() {
        return pending.size();
    }

    public void apply(JsonObject json, long now) {
        pending.values().removeIf(g -> now - g.startedAt >= PART_TIMEOUT_MS);
        String op = Wire.str(json, "op");
        if (op == null) return;
        switch (op) {
            case "stop" -> {
                if (json.has("id")) {
                    String id = Wire.id(json, "id", 32);
                    if (id != null) {
                        ghosts.remove(id);
                        pending.remove(id);
                    }
                } else clear();
            }
            case "play" -> play(json, now);
            default -> { }
        }
    }

    private void play(JsonObject json, long now) {
        String id = Wire.id(json, "id", 32), course = Wire.id(json, "course", 32);
        if (id == null || course == null) return;
        int parts = Wire.whole(json, "parts", 1, 4, 1), part = Wire.whole(json, "part", 0, 3, 0);
        if (part >= parts) return;
        Ghost g;
        if (part == 0) {
            if (!ghosts.containsKey(id) && pending.get(id) == null && ghosts.size() + pending.size() >= MAX_GHOSTS) return;
            g = new Ghost();
            g.id = id;
            g.course = course;
            String n = Wire.text(json, "name", 24);
            g.name = n == null ? "" : n;
            g.skin = Wire.uuid(json, "skin");
            g.parts = parts;
            g.startedAt = now;
            pending.put(id, g);
        } else {
            g = pending.get(id);
            if (g == null || g.next != part || g.parts != parts || !g.course.equals(course)) { // out of order or unknown: dropped
                pending.remove(id);
                return;
            }
        }
        boolean ended = readFrames(g, Wire.arr(json, "frames"));
        g.next = part + 1;
        if (ended || g.next >= g.parts) {
            pending.remove(id);
            if (!g.frames.isEmpty()) {
                g.armed = true;
                ghosts.put(id, g); // the same id replaces
            }
        }
    }

    /** Adds frames until the first bad one; true when a bad frame (or the cap) ended the list. */
    private static boolean readFrames(Ghost g, JsonArray arr) {
        if (arr == null) return false;
        int taken = 0;
        double last = g.frames.isEmpty() ? -1 : g.frames.get(g.frames.size() - 1)[0];
        for (JsonElement e : arr) {
            if (taken >= MAX_PER_MESSAGE || g.frames.size() >= MAX_FRAMES) return true;
            double[] f = frame(e);
            if (f == null || f[0] <= last || f[0] > MAX_T) return true;
            g.frames.add(f);
            last = f[0];
            taken++;
        }
        return false;
    }

    private static double[] frame(JsonElement e) {
        if (!e.isJsonArray()) return null;
        JsonArray a = e.getAsJsonArray();
        if (a.size() < 6) return null;
        double[] f = new double[6];
        for (int i = 0; i < 6; i++) {
            JsonElement v = a.get(i);
            if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) return null;
            f[i] = v.getAsDouble();
            if (Double.isNaN(f[i]) || Double.isInfinite(f[i])) return null;
        }
        f[5] = Math.max(-90, Math.min(90, f[5]));
        return f;
    }

    /** The ghosts to draw now: only while the run of their course is running; each on the run clock. */
    public List<Pose> visible(RunState run, long now) {
        List<Pose> out = new ArrayList<>();
        if (!run.running()) return out;
        long clock = run.elapsedMs(now);
        for (Ghost g : ghosts.values()) {
            if (!g.course.equals(run.course())) continue;
            Pose p = poseAt(g, clock);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Pure: the interpolated pose at {@code clockMs}, null once it passed its last frame by 1 s. Before the first frame it holds. */
    private static Pose poseAt(Ghost g, long clockMs) {
        List<double[]> f = g.frames;
        double[] first = f.get(0), last = f.get(f.size() - 1);
        if (clockMs > last[0] + LINGER_MS) return null;
        if (clockMs <= first[0]) return pose(g, first);
        if (clockMs >= last[0]) return pose(g, last);
        int lo = 0, hi = f.size() - 1; // f[lo].t <= clock < f[hi].t
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (f.get(mid)[0] <= clockMs) lo = mid;
            else hi = mid;
        }
        double[] a = f.get(lo), b = f.get(hi);
        double k = (clockMs - a[0]) / (b[0] - a[0]);
        return new Pose(g.id, g.name, g.skin, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k, a[3] + (b[3] - a[3]) * k,
            Geo.lerpAngle(a[4], b[4], k), a[5] + (b[5] - a[5]) * k);
    }

    private static Pose pose(Ghost g, double[] f) {
        return new Pose(g.id, g.name, g.skin, f[1], f[2], f[3], f[4], f[5]);
    }
}
