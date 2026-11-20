package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Parkour and speedrun clock (PROTOCOL.md section 19). The server is the timer authority; this only shows it and sends nothing.
 * Nothing is stored on disk (the doc's optional local personal best is not implemented). Game thread only.
 */
public final class RunState {
    public static final long SPLIT_SHOW_MS = 3_000, FINISH_SHOW_MS = 120_000, IDLE_DROP_MS = 30 * 60_000L;

    /** {@code delta} = time - best[cp] (negative = faster); {@code hasDelta} false when the server sent no best for that cp. */
    public record Split(int cp, long timeMs, long deltaMs, boolean hasDelta) {
        /** Green when at or ahead of the best, red when behind. */
        public boolean ahead() {
            return deltaMs <= 0;
        }
    }

    public record Finish(long timeMs, long deltaMs, boolean hasDelta) {
        public boolean ahead() {
            return deltaMs <= 0;
        }
    }

    private boolean active, running;
    private String course = "", name = "";
    private int checkpoints, reached;
    private List<Long> best = List.of();
    private long bestTotal = -1;
    private long baseMs, baseAt, lastMsg;
    private long finalMs;
    private Split split;
    private long splitAt;
    private Finish finish;
    private long finishAt;

    public void clear() {
        active = running = false;
        course = name = "";
        best = List.of();
        split = null;
        finish = null;
    }

    /** A run is shown (running, or finished and its result still up). */
    public boolean active(long now) {
        prune(now);
        return active;
    }

    public boolean running() {
        return active && running;
    }

    public String course() {
        return course;
    }

    public String name() {
        return name;
    }

    public int checkpoints() {
        return checkpoints;
    }

    /** Checkpoints reached so far (the last split cp + 1; 0 = none yet). */
    public int reached() {
        return reached;
    }

    /** The run clock now: counts up from the last server time, frozen on finish. */
    public long elapsedMs(long now) {
        return running ? baseMs + Math.max(0, now - baseAt) : finalMs;
    }

    public Split split(long now) {
        return split != null && now - splitAt < SPLIT_SHOW_MS ? split : null;
    }

    public Finish finish(long now) {
        return finish != null && now - finishAt < FINISH_SHOW_MS ? finish : null;
    }

    /** Drops a run that heard nothing for 30 minutes, and results whose time is up. */
    public void prune(long now) {
        if (active && now - lastMsg >= IDLE_DROP_MS) clear();
        if (active && !running && finish != null && now - finishAt >= FINISH_SHOW_MS) clear();
    }

    public void apply(JsonObject json, long now) {
        String op = Wire.str(json, "op");
        String c = Wire.id(json, "course", 32);
        if (op == null) return;
        if (c == null) {
            if (op.equals("reset") && !json.has("course") && active) clear(); // reset without a course = whatever runs
            return;
        }
        switch (op) {
            case "start" -> start(json, c, now);
            case "split" -> {
                if (!active || !running || !c.equals(course)) return;
                Double cp = Wire.num(json, "cp"), t = Wire.num(json, "time_ms");
                if (cp == null || t == null) return;
                int i = (int) Math.round(Math.max(0, Math.min(254, cp)));
                long time = Math.round(Math.max(0, Math.min(86_400_000, t)));
                baseMs = time;
                baseAt = now;
                lastMsg = now;
                boolean has = i < best.size();
                reached = i + 1;
                split = new Split(i, time, has ? time - best.get(i) : 0, has);
                splitAt = now;
            }
            case "finish" -> {
                if (!active || !running || !c.equals(course)) return;
                Double t = Wire.num(json, "time_ms");
                if (t == null) return;
                long time = Math.round(Math.max(0, Math.min(86_400_000, t)));
                running = false;
                finalMs = time;
                lastMsg = now;
                finish = new Finish(time, bestTotal >= 0 ? time - bestTotal : 0, bestTotal >= 0);
                finishAt = now;
            }
            case "reset" -> {
                if (active && c.equals(course)) clear();
            }
            default -> { }
        }
    }

    private void start(JsonObject json, String c, long now) {
        course = c;
        String n = Wire.text(json, "name", 24);
        name = n == null ? "" : n;
        checkpoints = Wire.whole(json, "checkpoints", 0, 255, 0);
        reached = 0;
        List<Long> list = new ArrayList<>();
        JsonArray arr = Wire.arr(json, "best");
        if (arr != null) {
            for (JsonElement e : arr) {
                if (list.size() >= 255) break;
                if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) break;
                double d = e.getAsDouble();
                if (Double.isNaN(d) || Double.isInfinite(d)) break;
                list.add(Math.round(Math.max(0, Math.min(86_400_000, d))));
            }
        }
        best = list;
        Double bt = Wire.num(json, "best_total");
        bestTotal = bt == null ? -1 : Math.round(Math.max(0, Math.min(86_400_000, bt)));
        baseMs = Math.round(Wire.num(json, "time_ms", 0, 86_400_000, 0));
        baseAt = now;
        lastMsg = now;
        active = running = true;
        split = null;
        finish = null;
    }
}
