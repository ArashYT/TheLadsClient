package com.thelads.ladsbench;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * QA-only FPS benchmark driver (1.9.12 perf lane), shared by the 26.3 and 1.20.1 builds. Inert unless the game folder has
 * ladsbench/bench.properties (only the bench sandboxes do). Opens the bench world from the title screen, runs fixed scenes
 * (static spawn view, HUD hidden, entity crowd, flight through pre-generated terrain), records every frame time, writes
 * ladsbench/result.json and exits. mode=prep creates the world (fixed seed) and pre-generates the flight path instead.
 * Shows the Lads QA boss bar (owner rule) with step i/N, current scene, OK/SLOW/FAIL/CRASH and ETA.
 */
public final class Bench {
    /** The few game calls the driver needs; one implementation per Minecraft version. */
    public interface Game {
        boolean loading();
        boolean screenOpen();
        String screenName();
        /** The game window has the OS focus (an unfocused window can be throttled by Windows or capped by mods). */
        boolean focused();
        boolean inWorld();
        boolean worldExists(String name);
        void openWorld(String name);
        void createWorld(String name, long seed);
        /** Runs a command on the integrated server (server thread, full permissions). */
        void command(String command);
        void pose(double x, double y, double z, float yaw, float pitch);
        double[] pos();
        void hud(boolean hidden);
        /** The Lads QA boss bar for every player of the integrated server. */
        void bar(String text, float progress, boolean red);
        Path gameDir();
        String info();
        /** screenshots/ladsbench-<name>.png (evidence of each scene; taken before measuring). */
        void screenshot(String name);
    }

    private record Scene(String name, int warmTicks, int measureTicks) { }

    private static final double FLY_SPEED = 0.8;  // blocks per tick = 16 blocks/s along +X
    private static final double PREP_SPEED = 0.3;
    private static final int PREP_BLOCKS = 480;   // > (3 s warm + 25 s measure) * 16 b/s
    private static final long T0 = System.nanoTime();

    private static Game game;
    private static boolean off, prep, shots;
    private static String label = "bench", world = "ladsbench", error;
    private static long seed = 1912L;
    private static final List<Scene> scenes = new ArrayList<>();
    private static final List<String> results = new ArrayList<>();
    private static int phase; // 0 title, 1 opening, 2 scenes, 3 prep fly, 4 prep save, 5 done
    private static int sceneIndex, sceneTick, waitTicks;
    private static long titleMs = -1, loadedMs = -1, openNs, joinMs = -1;
    private static double sx, sy, sz, flyX;
    private static float yaw = -90f, pitch = 12f;
    private static volatile boolean recording;
    private static long lastFrame;
    private static long[] frames = new long[1 << 16];
    private static int frameCount;
    private static long sceneStartEpoch, gcCount0, gcMs0;
    private static int focusTicks, recTicks;

    private Bench() { }

    /** Every frame (start of Minecraft.runTick). */
    public static void frame() {
        long now = System.nanoTime();
        if (recording && lastFrame != 0) {
            if (frameCount == frames.length) frames = Arrays.copyOf(frames, frameCount * 2);
            frames[frameCount++] = now - lastFrame;
        }
        lastFrame = now;
    }

    /** Every client tick (end of Minecraft.tick). */
    public static void tick(Game g) {
        if (off) return;
        try {
            if (game == null && !init(g)) return;
            step();
        } catch (Throwable t) {
            fail("driver error: " + t);
        }
    }

    private static boolean init(Game g) throws IOException {
        Path cfg = g.gameDir().resolve("ladsbench").resolve("bench.properties");
        if (!Files.isRegularFile(cfg)) { off = true; return false; }
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) { p.load(r); }
        game = g;
        prep = "prep".equals(p.getProperty("mode"));
        shots = "true".equals(p.getProperty("shots")); // evidence screenshots: never in measured runs (the save + toast cost frames)
        label = p.getProperty("label", label);
        world = p.getProperty("world", world);
        seed = Long.parseLong(p.getProperty("seed", String.valueOf(seed)));
        int measure = 20 * Integer.parseInt(p.getProperty("seconds", "20"));
        for (String s : p.getProperty("scenes", "settle,spawn,hudoff,entities,fly").split(",")) {
            switch (s.trim()) {
                case "settle" -> scenes.add(new Scene("settle", 20 * 20, 0));
                case "spawn", "hudoff" -> scenes.add(new Scene(s.trim(), 40, measure));
                case "entities" -> scenes.add(new Scene("entities", 5 * 20, measure));
                case "fly" -> scenes.add(new Scene("fly", 3 * 20, measure + 5 * 20));
                default -> { }
            }
        }
        log("ladsbench " + label + (prep ? " PREP" : "") + " scenes " + scenes);
        return true;
    }

    private static void step() {
        Game g = game;
        long nowMs = (System.nanoTime() - T0) / 1_000_000;
        switch (phase) {
            case 0 -> {
                if (!g.loading() && loadedMs < 0) loadedMs = jvmMs();
                if (g.loading() || !g.screenOpen() || g.inWorld()) { waitTicks = 0; timeout(300 * 20, "title screen"); return; }
                if (titleMs < 0) titleMs = jvmMs();
                if (++waitTicks < 20) return; // title stable for 1 s
                openNs = System.nanoTime();
                if (prep && !g.worldExists(world)) g.createWorld(world, seed);
                else if (!g.worldExists(world)) { fail("bench world '" + world + "' missing"); return; }
                else g.openWorld(world);
                phase = 1; waitTicks = 0;
            }
            case 1 -> {
                if (!g.inWorld() || g.screenOpen()) { timeout(300 * 20, "world join"); return; }
                joinMs = (System.nanoTime() - openNs) / 1_000_000;
                double[] p = g.pos();
                sx = p[0]; sy = p[1]; sz = p[2];
                if (prep) {
                    for (String c : new String[]{"gamemode creative @a", "time set 6000", "weather clear 999999", "gamerule doDaylightCycle false",
                        "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamerule doMobLoot false", "gamerule advance_time false",
                        "gamerule advance_weather false", "gamerule spawn_mobs false", "gamerule mob_drops false"}) g.command(c);
                    sy = Math.max(sy, 64) + 40; // bench pose: 40 blocks above the spawn, kept in the world folder for every run
                    try { Files.writeString(posePath(g), sx + " " + sy + " " + sz); } catch (IOException e) { fail("pose: " + e); return; }
                    phase = 3; sceneTick = 0; flyX = sx;
                } else {
                    try {
                        String[] v = Files.readString(posePath(g)).trim().split(" ");
                        sx = Double.parseDouble(v[0]); sy = Double.parseDouble(v[1]); sz = Double.parseDouble(v[2]);
                    } catch (IOException | RuntimeException e) { fail("bench pose missing in the template: " + e); return; }
                    phase = 2; sceneIndex = 0; sceneTick = 0;
                }
                log("ladsbench joined in " + joinMs + " ms at " + (int) sx + " " + (int) sy + " " + (int) sz);
            }
            case 2 -> scenes();
            case 3 -> { // prep: fly the bench path slowly so every chunk of it is generated and saved
                if (sceneTick++ == 100 && shots) g.screenshot(label + "-prep");
                flyX += PREP_SPEED;
                g.pose(flyX, sy, sz, yaw, pitch);
                bar("prep fly", (float) ((flyX - sx) / PREP_BLOCKS), 0, 2);
                if (flyX - sx >= PREP_BLOCKS + 16) { g.pose(sx, sy, sz, yaw, pitch); phase = 4; sceneTick = 0; }
            }
            case 4 -> {
                g.pose(sx, sy, sz, yaw, pitch);
                if (++sceneTick == 200) g.command("save-all flush");
                bar("prep save", sceneTick / 400f, 1, 2);
                if (sceneTick >= 400) finish();
            }
            default -> { }
        }
    }

    private static void scenes() {
        Game g = game;
        if (sceneIndex >= scenes.size()) { finish(); return; }
        Scene s = scenes.get(sceneIndex);
        int t = sceneTick++;
        if (t == 0) {
            log("ladsbench scene " + s.name);
            g.hud("hudoff".equals(s.name));
            if (s.name.equals("entities")) summon(g);
            flyX = sx;
        }
        if (s.name.equals("fly")) { flyX += FLY_SPEED; g.pose(flyX, sy, sz, yaw, pitch); }
        else g.pose(sx, sy, sz, yaw, pitch);
        if (t == s.warmTicks - 10 && s.measureTicks > 0 && shots) { log("ladsbench screenshot " + s.name); g.screenshot(label + "-" + s.name); }
        if (t == s.warmTicks && s.measureTicks > 0) startRecording();
        if (recording) { recTicks++; if (g.focused()) focusTicks++; }
        bar(s.name, (float) sceneIndex / scenes.size(), sceneIndex, scenes.size());
        if (t >= s.warmTicks + s.measureTicks) {
            if (s.measureTicks > 0) results.add(stopRecording(s.name));
            if (s.name.equals("entities")) { g.command("kill @e[type=!player]"); g.command("kill @e[type=item]"); }
            g.hud(false);
            sceneIndex++; sceneTick = 0;
        }
    }

    /** 200 still mobs (NoAI) in a 20 x 10 grid 6..25 blocks ahead (+X), slightly below eye height. */
    private static void summon(Game g) {
        String[] types = {"cow", "sheep", "pig", "villager"};
        int n = 0;
        for (int dx = 0; dx < 20; dx++)
            for (int dz = -5; dz < 5; dz++)
                g.command(String.format(Locale.ROOT, "summon minecraft:%s %.1f %.1f %.1f {NoAI:1b,Silent:1b,PersistenceRequired:1b}",
                    types[n++ % types.length], sx + 6 + dx, sy - 1.5, sz + dz * 2 + 0.5));
    }

    private static void startRecording() {
        frameCount = 0; lastFrame = 0; focusTicks = 0; recTicks = 0;
        sceneStartEpoch = System.currentTimeMillis();
        long[] gc = gc(); gcCount0 = gc[0]; gcMs0 = gc[1];
        recording = true;
    }

    private static String stopRecording(String name) {
        recording = false;
        long end = System.currentTimeMillis();
        long[] gc = gc();
        long[] f = Arrays.copyOf(frames, frameCount);
        double totalMs = Arrays.stream(f).sum() / 1e6;
        Arrays.sort(f);
        int n = f.length;
        String json = String.format(Locale.ROOT,
            "{\"scene\":\"%s\",\"frames\":%d,\"seconds\":%.2f,\"avgFps\":%.1f,\"low1Fps\":%.1f,\"low01Fps\":%.1f,"
                + "\"p50Ms\":%.3f,\"p90Ms\":%.3f,\"p95Ms\":%.3f,\"p99Ms\":%.3f,\"p999Ms\":%.3f,\"maxMs\":%.2f,\"gcCount\":%d,\"gcMs\":%d,"
                + "\"focused\":%.2f,\"startEpochMs\":%d,\"endEpochMs\":%d}",
            name, n, totalMs / 1000, n == 0 ? 0 : n / (totalMs / 1000), lowFps(f, 0.01), lowFps(f, 0.001),
            pct(f, 0.50), pct(f, 0.90), pct(f, 0.95), pct(f, 0.99), pct(f, 0.999), n == 0 ? 0 : f[n - 1] / 1e6,
            gc[0] - gcCount0, gc[1] - gcMs0, recTicks == 0 ? 0.0 : (double) focusTicks / recTicks, sceneStartEpoch, end);
        log("ladsbench result " + json);
        return json;
    }

    /** "1 % low": 1000 / mean of the slowest 1 % frame times (sorted ascending). */
    private static double lowFps(long[] sorted, double share) {
        int k = Math.max(1, (int) Math.round(sorted.length * share));
        if (sorted.length == 0) return 0;
        double sum = 0;
        for (int i = sorted.length - k; i < sorted.length; i++) sum += sorted[i];
        return 1e9 / (sum / k);
    }

    private static double pct(long[] sorted, double q) {
        return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) Math.floor(q * sorted.length))] / 1e6;
    }

    private static long[] gc() {
        long c = 0, t = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) { c += Math.max(0, b.getCollectionCount()); t += Math.max(0, b.getCollectionTime()); }
        return new long[]{c, t};
    }

    private static Path posePath(Game g) { return g.gameDir().resolve("saves").resolve(world).resolve("ladsbench-pose.txt"); }

    private static long jvmMs() { return System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime(); }

    private static int barTick;

    private static void bar(String scene, float progress, int done, int total) {
        if (barTick++ % 20 != 0) return;
        int remaining = 0;
        if (phase == 2) for (int i = sceneIndex; i < scenes.size(); i++) remaining += scenes.get(i).warmTicks + scenes.get(i).measureTicks;
        remaining = Math.max(0, remaining - (phase == 2 ? sceneTick : 0)) / 20;
        game.bar(String.format(Locale.ROOT, "Lads QA · %s · step %d/%d · %s · OK %d · SLOW 0 · FAIL 0 · CRASH 0 · ETA %d:%02d",
            label, Math.min(done + 1, total), total, scene, done, remaining / 60, remaining % 60), Math.max(0f, Math.min(1f, progress)), false);
    }

    private static void timeout(int ticks, String what) {
        if (++waitTicks % 600 == 0) log("ladsbench still waiting for " + what + ", screen " + game.screenName());
        // a confirmation screen (e.g. Fabric's "Missing content detected!" after a mod was removed) never goes away by itself
        if (waitTicks == 200 && game.screenName().contains("Confirm")) { fail("blocked by " + game.screenName()); return; }
        if (waitTicks > ticks) fail("timeout waiting for " + what + " (screen " + game.screenName() + ")");
    }

    private static void fail(String why) {
        error = why;
        log("ladsbench FAIL " + why);
        finish();
    }

    private static void finish() {
        phase = 5; off = true; recording = false;
        Runtime rt = Runtime.getRuntime();
        String json = String.format(Locale.ROOT,
            "{\"label\":\"%s\",\"mode\":\"%s\",\"error\":%s,\"startupMs\":%d,\"resourcesMs\":%d,\"joinMs\":%d,\"heapUsedMb\":%d,\"heapMaxMb\":%d,"
                + "\"info\":\"%s\",\"scenes\":[%s]}",
            label, prep ? "prep" : "run", error == null ? "null" : "\"" + esc(error) + "\"", titleMs, loadedMs, joinMs,
            (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20, esc(game.info()), String.join(",", results));
        try {
            Path dir = game.gameDir().resolve("ladsbench");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("result.json.tmp"), json, StandardCharsets.UTF_8);
            Files.move(dir.resolve("result.json.tmp"), dir.resolve("result.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log("ladsbench could not write result: " + e);
        }
        log("ladsbench done, exiting");
        System.exit(error == null ? 0 : 2);
    }

    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "); }

    private static void log(String s) { System.out.println("[ladsbench] " + s); }
}
