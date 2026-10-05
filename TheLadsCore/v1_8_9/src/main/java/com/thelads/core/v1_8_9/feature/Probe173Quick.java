package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

/**
 * QA only (LADS_VERIFY_189_ONLY=quick; 1.7.3 quick-wins lane): frame times of the QA world, uncapped and without VSync (the user's
 * benchmark exception), with the Lads HUD plain and with most HUD modules on, frame pacing on and off, each run twice in turn.
 * Logs one "quick probe RESULT" line per run: average FPS, p50 / p99 / max frame ms, the average of the worst 1% as FPS, and the
 * Lads HUD's microseconds per frame.
 */
final class Probe173Quick {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final String[] MODULES = {"FPS", "Coordinates", "Keystrokes", "CPS", "Paperdoll", "ArmorHUD", "Potion Effects", "Scoreboard",
        "BossBar", "Item Physics", "Chat Heads", "Raised", "Biome", "Time", "Direction", "Speed", "Day", "XP"};
    private static final String[] RUNS = new String[16];
    static {
        String[] set = {"plain/paced", "plain/unpaced", "hud/paced", "hud/unpaced"};
        for (int i = 0; i < RUNS.length; i++) RUNS[i] = set[i % 4];
    }
    private static final long WARM = 2_000_000_000L, MEASURE = 8_000_000_000L; // ns
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe173Quick::run);

    private static final Map<Module, Boolean> enabledWere = new LinkedHashMap<Module, Boolean>();
    private static final long[] DURATIONS = new long[400_000];
    private static int count, index = -1, ticks, limitWas, rdWas = -1;
    private static boolean registered, recording, vsyncWas, capWas, pacingWas;
    private static long last;
    private static long hudNanos0, hudFrames0, start;

    private Probe173Quick() {}

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        if (recording && last != 0 && count < DURATIONS.length) DURATIONS[count++] = now - last;
        last = now;
    }

    private static void apply(Minecraft mc, String run) {
        boolean hud = run.startsWith("hud");
        for (String name : MODULES) {
            Module module = ModuleManager.getInstance().getModule(name);
            if (module == null) continue;
            enabledWere.putIfAbsent(module, module.isEnabled());
            module.setEnabled(hud || enabledWere.get(module));
        }
        RawMouse189.pacing = run.endsWith("/paced");
    }

    private static boolean run(Minecraft mc) {
        if (index < 0) {
            if (!registered) {
                registered = true;
                MinecraftForge.EVENT_BUS.register(new Probe173Quick());
            }
            vsyncWas = mc.gameSettings.enableVsync;
            limitWas = mc.gameSettings.limitFramerate;
            pacingWas = RawMouse189.pacing;
            String rd = System.getenv("LADS_VERIFY_QUICK_RD");
            if (rd != null) { rdWas = mc.gameSettings.renderDistanceChunks; mc.gameSettings.renderDistanceChunks = Integer.parseInt(rd); mc.renderGlobal.loadRenderers(); }
            mc.gameSettings.enableVsync = false;
            Display.setVSyncEnabled(false);
            mc.gameSettings.limitFramerate = (int) GameSettings.Options.FRAMERATE_LIMIT.getValueMax(); // unlimited
            LOG.info("Lads 1.8.9 quick probe: uncapped (limit {}), window focused {}, unfocused cap bypass {}, pacing default {}",
                mc.gameSettings.limitFramerate, Display.isActive(), Boolean.getBoolean("thelads.noUnfocusedCap"), pacingWas);
            index = 0;
            ticks = 0;
            apply(mc, RUNS[0]);
            return retry(1);
        }
        if (index >= RUNS.length) return finish(mc);
        long now = System.nanoTime();
        if (ticks == 0) { ticks = 1; start = now; }
        else if (ticks == 1 && now - start >= (index == 0 && rdWas >= 0 ? 12 * WARM : WARM)) { // the first run waits for the new distance's chunks
            ticks = 2;
            count = 0;
            recording = true;
            hudNanos0 = NativeHud.hudNanos;
            hudFrames0 = NativeHud.frames;
            start = now;
        } else if (ticks == 2 && now - start >= MEASURE) {
            recording = false;
            report(mc, RUNS[index]);
            index++;
            ticks = 0;
            if (index < RUNS.length) apply(mc, RUNS[index]);
        }
        return index >= RUNS.length ? finish(mc) : retry(1);
    }

    private static void report(Minecraft mc, String run) {
        long[] sorted = Arrays.copyOf(DURATIONS, count);
        Arrays.sort(sorted);
        double total = 0;
        for (long d : sorted) total += d;
        int worst = Math.max(1, count / 100);
        double worstMs = 0;
        for (int i = count - worst; i < count; i++) worstMs += sorted[i] / 1e6;
        worstMs /= worst;
        long frames = NativeHud.frames - hudFrames0;
        double hudUs = frames > 0 ? (NativeHud.hudNanos - hudNanos0) / 1e3 / frames : 0;
        LOG.info("Lads 1.8.9 quick probe RESULT run={} frames={} fps={} p50ms={} p99ms={} maxms={} low1pctFps={} hudUs={} focused={} rd={}",
            run, count, String.format("%.1f", count * 1e9 / total), String.format("%.3f", sorted[count / 2] / 1e6),
            String.format("%.3f", sorted[Math.min(count - 1, (int) (count * 0.99))] / 1e6), String.format("%.3f", sorted[count - 1] / 1e6),
            String.format("%.1f", 1000 / worstMs), String.format("%.1f", hudUs), Display.isActive(), mc.gameSettings.renderDistanceChunks);
        check(count > 100, "run " + run + " recorded frames (" + count + ")");
    }

    private static boolean finish(Minecraft mc) {
        for (Map.Entry<Module, Boolean> was : enabledWere.entrySet()) was.getKey().setEnabled(was.getValue());
        RawMouse189.pacing = pacingWas;
        mc.gameSettings.enableVsync = vsyncWas;
        Display.setVSyncEnabled(vsyncWas);
        mc.gameSettings.limitFramerate = limitWas;
        if (rdWas >= 0) { mc.gameSettings.renderDistanceChunks = rdWas; mc.renderGlobal.loadRenderers(); }
        return after(1);
    }
}
