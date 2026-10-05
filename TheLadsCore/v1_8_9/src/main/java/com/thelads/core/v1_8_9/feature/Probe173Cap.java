package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.thelads.core.config.Module;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

/**
 * QA only (LADS_VERIFY_189_ONLY=unfocusedcap): the unfocused FPS cap: its limit function, then in the QA world (capped at 60 by the harness,
 * minimized) with the player limit set unlimited, where the cap keeps it at 60; and a player limit of 30 stays 30. The focused case needs
 * a focused window and is not tested in QA.
 */
final class Probe173Cap {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe173Cap::run);
    private static final long WARM = 1_500_000_000L, MEASURE = 4_000_000_000L;
    private static final String[] RUNS = {"on", "own30"};
    private static int index = -1, phase, limitWas;
    private static long start, frames, counted;
    private static boolean registered, bypassWas, moduleWas;

    private Probe173Cap() {}

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) frames++;
    }

    /** The limit function itself, without measuring a frame rate (no uncapped game, no focus needed). */
    private static void limitLogic() {
        Module module = com.thelads.core.config.ModuleManager.getInstance().getModule(UnfocusedFpsCap189.NAME);
        boolean was = module.isEnabled();
        UnfocusedFpsCap189.forceUnfocused = true;
        module.setEnabled(true);
        check(UnfocusedFpsCap189.limit(260) == 60 && UnfocusedFpsCap189.limit(120) == 60, "unfocused: an unlimited or higher player limit becomes 60");
        check(UnfocusedFpsCap189.limit(30) == 30 && UnfocusedFpsCap189.limit(60) == 60, "unfocused: a lower player limit is never raised");
        module.setEnabled(false);
        check(UnfocusedFpsCap189.limit(260) == 260, "module off: the limit is the player's");
        module.setEnabled(true);
        UnfocusedFpsCap189.bypass = true;
        check(UnfocusedFpsCap189.limit(260) == 260, "QA bypass: the limit is the player's");
        UnfocusedFpsCap189.bypass = false;
        UnfocusedFpsCap189.forceUnfocused = false;
        if (Display.isActive()) check(UnfocusedFpsCap189.limit(260) == 260, "window focused: the limit is the player's");
        else LOG.info("Lads 1.8.9 cap probe: window focused case UNTESTED (the QA window is minimized and never focused)");
        module.setEnabled(was);
    }

    private static boolean run(Minecraft mc) {
        Module module = com.thelads.core.config.ModuleManager.getInstance().getModule(UnfocusedFpsCap189.NAME);
        long now = System.nanoTime();
        if (index < 0) {
            check(module != null && module.isEnabled(), "the unfocused FPS cap is a module and on by default");
            check(module.getOption("FPS limit") != null, "the module has an FPS limit option");
            check(GlState189.agreesWithDriver(), "GlStateManager's tracked blend, depth and alpha switches were captured and match the driver");
            check(!((com.thelads.core.v1_8_9.mixin.MinecraftGlErrorsAccessor) mc).ladsGlErrors(), "vanilla GL error checking is off in the world");
            if (!registered) { registered = true; MinecraftForge.EVENT_BUS.register(new Probe173Cap()); }
            limitWas = mc.gameSettings.limitFramerate;
            bypassWas = UnfocusedFpsCap189.bypass;
            moduleWas = module.isEnabled();
            UnfocusedFpsCap189.bypass = false;
            limitLogic();
            index = 0;
            phase = 0;
        }
        if (index >= RUNS.length) {
            UnfocusedFpsCap189.bypass = bypassWas;
            UnfocusedFpsCap189.forceUnfocused = false;
            module.setEnabled(moduleWas);
            mc.gameSettings.limitFramerate = limitWas;
            return after(1);
        }
        String name = RUNS[index];
        if (phase == 0) {
            module.setEnabled(true);
            UnfocusedFpsCap189.forceUnfocused = true; // the QA window is minimized, so it is unfocused anyway
            // "focused" is the real window state, which a QA window is not guaranteed to have: the cap only applies when it is unfocused.
            mc.gameSettings.limitFramerate = name.equals("own30") ? 30 : (int) GameSettings.Options.FRAMERATE_LIMIT.getValueMax(); // unlimited by the option, held at 60 by the cap
            phase = 1;
            start = now;
        } else if (phase == 1 && now - start >= WARM) {
            phase = 2;
            start = now;
            counted = frames;
        } else if (phase == 2 && now - start >= MEASURE) {
            double fps = (frames - counted) * 1e9 / (now - start);
            LOG.info("Lads 1.8.9 cap probe RESULT run={} fps={} limit={} unfocusedByWindow={}", name, String.format("%.1f", fps), mc.gameSettings.limitFramerate, !Display.isActive());
            switch (name) {
                case "on": check(fps >= 45 && fps <= 64, "unlimited limit, window unfocused: the cap holds the frame rate near 60 (" + fps + ")"); break;
                case "own30": check(fps >= 20 && fps <= 32, "a player limit of 30 is not raised to 60 (" + fps + ")"); break;
            }
            index++;
            phase = 0;
        }
        return retry(1);
    }
}
