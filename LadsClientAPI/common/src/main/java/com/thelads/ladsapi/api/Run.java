package com.thelads.ladsapi.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thelads.ladsapi.session.Features;
import com.thelads.ladsapi.session.Wire;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Run timer and ghosts (PROTOCOL.md section 19). The server is the timer authority: the clock starts at {@link #start} and the
 * client counts by itself. Run calls are false without the {@code run} cap, ghost calls without the {@code ghost} cap.
 */
public final class Run {
    public static final int MAX_GHOSTS = 3;
    public static final int FRAMES_PER_PART = 600;
    public static final int MAX_PARTS = 4;
    private final Features features;
    private final java.util.function.LongSupplier clock;

    public Run(Features features, java.util.function.LongSupplier clock) {
        this.features = features;
        this.clock = clock;
    }

    /** Starts a run at 0 ms. {@code best}: the player's best, cumulative ms per checkpoint (<= 255, may be null). */
    public boolean start(String course, String name, int checkpoints, List<Long> best, long bestTotal) {
        return startAt(course, name, checkpoints, 0, best, bestTotal);
    }

    public boolean startAt(String course, String name, int checkpoints, long timeMs, List<Long> best, long bestTotal) {
        String cid = Wire.id(course, 32);
        if (cid == null) return false;
        JsonObject o = Wire.obj("run", "start");
        o.addProperty("course", cid);
        if (name != null) o.addProperty("name", Wire.text(name, 24));
        if (checkpoints > 0) o.addProperty("checkpoints", Math.min(255, checkpoints));
        long t = Math.max(0, Math.min(86_400_000L, timeMs));
        o.addProperty("time_ms", t);
        if (best != null && !best.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (Long b : best) {
                if (b != null && arr.size() < 255) arr.add(Math.max(0, Math.min(86_400_000L, b)));
            }
            o.add("best", arr);
        }
        if (bestTotal > 0) o.addProperty("best_total", Math.min(86_400_000L, bestTotal));
        if (!features.keep("run", "run", o, 0, false, Features.countup("time_ms"))) return false;
        features.removeAll("run", "run.", null);
        features.runClock(cid, clock.getAsLong() - t);
        return true;
    }

    /** Checkpoint {@code cp} (0-based) reached; the time is this server's own clock since {@link #start}. False when {@code course} is not running. */
    public boolean split(String course, int cp) {
        long t = features.runTime(course);
        return t >= 0 && split(course, cp, t);
    }

    public boolean split(String course, int cp, long timeMs) {
        String cid = Wire.id(course, 32);
        if (cid == null) return false;
        JsonObject o = Wire.obj("run", "split");
        o.addProperty("course", cid);
        o.addProperty("cp", Math.max(0, Math.min(254, cp)));
        o.addProperty("time_ms", Math.max(0, Math.min(86_400_000L, timeMs)));
        return features.keep("run", "run.split", o, 0, false, null);
    }

    public boolean finish(String course) {
        long t = features.runTime(course);
        return t >= 0 && finish(course, t);
    }

    public boolean finish(String course, long timeMs) {
        String cid = Wire.id(course, 32);
        if (cid == null) return false;
        JsonObject o = Wire.obj("run", "finish");
        o.addProperty("course", cid);
        o.addProperty("time_ms", Math.max(0, Math.min(86_400_000L, timeMs)));
        if (!features.keep("run", "run.finish", o, 0, false, null)) return false;
        features.endRunClock();
        return true;
    }

    /** Hides the run ({@code course} null = whatever runs). */
    public boolean reset(String course) {
        JsonObject m = Wire.obj("run", "reset");
        String cid = Wire.id(course, 32);
        if (cid != null) m.addProperty("course", cid);
        features.endRunClock();
        if (!features.ok("run")) return false;
        features.remove("run", "run", null);
        features.removeAll("run", "run.", null);
        return features.send("run", m);
    }

    /**
     * Plays a ghost along {@code frames}, each {@code {t_ms, x, y, z, yaw, pitch}} with rising t. More than 600 frames are sent
     * in parts, up to 2,400 in all; later frames are left out. {@code skin} may be null. It starts with a {@link #start} of the
     * same course. At most 3 ghosts per player.
     */
    public boolean ghost(String id, String course, String name, UUID skin, List<double[]> frames) {
        String gid = Wire.id(id, 32);
        String cid = Wire.id(course, 32);
        if (gid == null || cid == null || frames == null || !features.ok("ghost")) return false;
        JsonArray all = new JsonArray();
        double last = -1;
        for (double[] f : frames) {
            if (f == null || f.length < 6 || all.size() >= FRAMES_PER_PART * MAX_PARTS) break;
            boolean good = true;
            for (double v : f) good &= Wire.finite(v);
            if (!good || f[0] < 0 || f[0] > 3_600_000 || f[0] <= last) break;
            last = f[0];
            JsonArray fr = new JsonArray();
            fr.add((long) f[0]);
            fr.add(Wire.round(f[1], 2));
            fr.add(Wire.round(f[2], 2));
            fr.add(Wire.round(f[3], 2));
            fr.add(Wire.round(f[4], 1));
            fr.add(Wire.round(f[5], 1));
            all.add(fr);
        }
        if (all.size() == 0) return false;
        Set<String> ids = new HashSet<>();
        for (String k : features.keys("ghost:")) ids.add(k.split(":")[1]);
        if (!ids.contains(gid) && ids.size() >= MAX_GHOSTS) return false;
        features.removeAll("ghost", "ghost:" + gid + ":", null);
        int parts = (all.size() + FRAMES_PER_PART - 1) / FRAMES_PER_PART;
        boolean ok = true;
        for (int p = 0; p < parts; p++) {
            JsonObject o = Wire.obj("ghost", "play");
            o.addProperty("id", gid);
            o.addProperty("course", cid);
            if (name != null) o.addProperty("name", Wire.text(name, 24));
            if (skin != null) o.addProperty("skin", skin.toString());
            if (parts > 1) {
                o.addProperty("part", p);
                o.addProperty("parts", parts);
            }
            JsonArray slice = new JsonArray();
            for (int i = p * FRAMES_PER_PART; i < Math.min(all.size(), (p + 1) * FRAMES_PER_PART); i++) slice.add(all.get(i));
            o.add("frames", slice);
            ok &= features.keep("ghost", "ghost:" + gid + ":" + p, o, 0, false, null);
        }
        return ok;
    }

    /** Stops one ghost. */
    public boolean stopGhost(String id) {
        String gid = Wire.id(id, 32);
        if (gid == null || !features.ok("ghost")) return false;
        JsonObject m = Wire.obj("ghost", "stop");
        m.addProperty("id", gid);
        features.removeAll("ghost", "ghost:" + gid + ":", null);
        return features.send("ghost", m);
    }

    public boolean stopGhosts() {
        if (!features.ok("ghost")) return false;
        features.removeAll("ghost", "ghost:", null);
        return features.send("ghost", Wire.obj("ghost", "stop"));
    }
}
