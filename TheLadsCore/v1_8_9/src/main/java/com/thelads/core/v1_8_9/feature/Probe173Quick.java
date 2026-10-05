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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

/**
 * QA only (LADS_VERIFY_189_ONLY=quick; 1.7.3 quick-wins lane): ms of work per frame in the QA world at the QA frame cap (60 FPS), with the
 * Lads HUD plain and with most HUD modules on, frame pacing on and off, each run twice in turn. Work is the time between the
 * RenderTickEvent START and END (everything but the buffer swap and the limiter's sleep). One "quick probe RESULT" line per run: work
 * p50 / p99 / max ms, the frame interval's p99 and the Lads HUD's microseconds per frame.
 */
final class Probe173Quick {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final String[] MODULES = {"FPS", "Coordinates", "Keystrokes", "CPS", "Paperdoll", "ArmorHUD", "Potion Effects", "Scoreboard",
        "BossBar", "Item Physics", "Chat Heads", "Raised", "Biome", "Time", "Direction", "Speed", "Day", "XP"};
    private static final String[] RUNS = new String[12];
    static {
        String[] set = {"plain", "hud"}; // frame pacing as the build has it: on in 1.7.2, off in 1.7.3
        for (int i = 0; i < RUNS.length; i++) RUNS[i] = set[i % 2];
    }
    private static final long WARM = 2_000_000_000L, MEASURE = 8_000_000_000L; // ns
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe173Quick::run);

    private static final Map<Module, Boolean> enabledWere = new LinkedHashMap<Module, Boolean>();
    private static final long[] DURATIONS = new long[400_000], WORK = new long[400_000];
    private static int count, index = -1, ticks;
    private static boolean registered, recording, pacingWas;
    private static long last, frameStart;
    private static long hudNanos0, hudFrames0, start;

    private Probe173Quick() {}

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        long now = System.nanoTime();
        if (event.phase == TickEvent.Phase.END) { // the work of the frame: everything between the two events, before the buffer swap and the frame limit's sleep
            if (recording && count > 0 && count <= WORK.length) WORK[count - 1] = now - frameStart;
            return;
        }
        if (recording && last != 0 && count < DURATIONS.length) DURATIONS[count++] = now - last;
        last = frameStart = now;
    }

    private static void apply(Minecraft mc, String run) {
        boolean hud = run.startsWith("hud");
        for (String name : MODULES) {
            Module module = ModuleManager.getInstance().getModule(name);
            if (module == null) continue;
            enabledWere.putIfAbsent(module, module.isEnabled());
            module.setEnabled(hud || enabledWere.get(module));
        }
    }

    private static boolean run(Minecraft mc) {
        if (index < 0) {
            if (!registered) {
                registered = true;
                MinecraftForge.EVENT_BUS.register(new Probe173Quick());
            }
            pacingWas = RawMouse189.pacing;
            LOG.info("Lads 1.8.9 quick probe: limit {} (QA: 60), window focused {}, unfocused cap bypass {}, pacing default {}",
                mc.gameSettings.limitFramerate, Display.isActive(), Boolean.getBoolean("thelads.noUnfocusedCap"), pacingWas);
            index = 0;
            ticks = 0;
            apply(mc, RUNS[0]);
            return retry(1);
        }
        if (index >= RUNS.length) return finish(mc);
        long now = System.nanoTime();
        if (ticks == 0) { ticks = 1; start = now; }
        else if (ticks == 1 && now - start >= WARM) {
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
        long[] work = Arrays.copyOf(WORK, count), interval = Arrays.copyOf(DURATIONS, count);
        Arrays.sort(work);
        Arrays.sort(interval);
        double total = 0;
        for (long d : interval) total += d;
        long frames = NativeHud.frames - hudFrames0;
        double hudUs = frames > 0 ? (NativeHud.hudNanos - hudNanos0) / 1e3 / frames : 0;
        LOG.info("Lads 1.8.9 quick probe RESULT run={} frames={} fps={} workP50ms={} workP99ms={} workMaxMs={} intervalP99ms={} hudUs={} focused={}",
            run, count, String.format("%.1f", count * 1e9 / total), String.format("%.3f", work[count / 2] / 1e6),
            String.format("%.3f", work[Math.min(count - 1, (int) (count * 0.99))] / 1e6), String.format("%.3f", work[count - 1] / 1e6),
            String.format("%.3f", interval[Math.min(count - 1, (int) (count * 0.99))] / 1e6), String.format("%.1f", hudUs), Display.isActive());
        check(count > 100, "run " + run + " recorded frames (" + count + ")");
    }

    private static boolean finish(Minecraft mc) {
        for (Map.Entry<Module, Boolean> was : enabledWere.entrySet()) was.getKey().setEnabled(was.getValue());
        return after(1);
    }
}
