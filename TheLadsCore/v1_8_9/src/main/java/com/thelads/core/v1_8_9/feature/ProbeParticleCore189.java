package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelads.core.client.ParticleCore;
import com.thelads.core.modules.ParticleCoreModule;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only (LADS_VERIFY_189_ONLY=particles): Particle Core in a heavy particle scene. Smoke, flames, explosions and potion swirls
 * round a cluster of 28 spots east of the player, spawned every tick (a few thousand particles alive). Eight phases, the view east
 * (the cluster in view) and west (all of it behind the camera), with Particle Core off, Culling only, Optimized rendering only
 * and both: frames, the time spent in EffectRenderer.renderParticles, particles alive, drawn and culled per frame, lightmap lookups
 * reused. Then the module's rates, potion switches and pages. Writes lads-qa/particle-core.json; everything is put back.
 */
final class ProbeParticleCore189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final int WARM_TICKS = 60, MEASURE_TICKS = 200, FILL_TICKS = 160, EMITTERS = 28;
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(ProbeParticleCore189::run);
    private static final String[] VIEWS = {"east", "west"}, CONFIGS = {"off", "culling", "optimized", "both"};
    private static final double[] EX = new double[EMITTERS], EZ = new double[EMITTERS];
    private static final Random RANDOM = new Random(135);
    private static final JsonArray PHASES = new JsonArray();
    private static final JsonObject CHECKS = new JsonObject();
    private static final Frames FRAMES = new Frames();
    private static int seq, ticks, phase, scrolls, difficultyWas;
    private static boolean feeding, measuring;
    private static double px, py, pz;
    private static float yawBefore, pitchBefore;
    private static boolean enabledBefore, cullBefore, optBefore, ownBefore, othersBefore;
    private static String ratesBefore;
    private static long aliveSum;
    private static int aliveSamples;
    private static LadsSettingsScreen189 menu;

    private ProbeParticleCore189() {}

    /** Counts frames and their gaps while a phase is measured. */
    public static final class Frames {
        long count, last;
        double[] gaps = new double[8192];
        long start;
        @SubscribeEvent
        public void render(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.START || !measuring) return;
            long now = System.nanoTime();
            if (last != 0 && count < gaps.length) gaps[(int) count] = (now - last) / 1e6;
            count++;
            last = now;
        }
        void reset() { count = 0; last = 0; start = System.nanoTime(); }
    }

    private static ParticleCoreModule module() { return (ParticleCoreModule) Options189.module(ParticleCore.MODULE); }

    private static void command(Minecraft mc, String command) {
        net.minecraft.server.MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
    }

    private static boolean run(Minecraft mc) throws Exception {
        ParticleCoreModule module = module();
        net.minecraft.client.settings.KeyBinding.unPressAllKeys(); // the player stays where the scene is built
        if (feeding) feed(mc);
        if (measuring) {
            if (ticks % 20 == 0) { aliveSum += alive(mc); aliveSamples++; }
            ticks++;
        }
        switch (seq) {
            case 0: // the scene, filled to its steady state with Particle Core off
                difficultyWas = mc.theWorld.getDifficulty().getDifficultyId();
                for (String command : new String[] {"time set 6000", "gamerule doDaylightCycle false", "weather clear 100000", "difficulty 0"}) command(mc, command);
                px = mc.thePlayer.posX; py = mc.thePlayer.posY; pz = mc.thePlayer.posZ;
                yawBefore = mc.thePlayer.rotationYaw; pitchBefore = mc.thePlayer.rotationPitch;
                enabledBefore = module.isEnabled(); cullBefore = module.culling.get(); optBefore = module.optimized.get();
                ownBefore = module.hideOwn.get(); othersBefore = module.hideOthers.get();
                ratesBefore = module.rates.save().toString();
                for (int i = 0; i < EMITTERS; i++) {
                    double angle = Math.toRadians(-52 + 104.0 * i / (EMITTERS - 1)), radius = 5 + (i % 4) * 1.5;
                    EX[i] = Math.cos(angle) * radius; EZ[i] = Math.sin(angle) * radius;
                }
                MinecraftForge.EVENT_BUS.register(FRAMES);
                module.setEnabled(false); ParticleCore.refresh();
                ParticleCore.timing = true;
                look(mc, -90);
                feeding = true;
                LOG.info("Lads particle core probe BEGIN: {} spots east of ({}, {}, {})", EMITTERS, px, py, pz);
                seq = 1; ticks = 0;
                return retry(1);
            case 1:
                if (++ticks < FILL_TICKS) return retry(1);
                seq = 2; ticks = 0; phase = 0;
                return retry(1);
            case 2: { // apply a phase's view and settings, let it settle
                String view = VIEWS[phase / CONFIGS.length], config = CONFIGS[phase % CONFIGS.length];
                look(mc, view.equals("east") ? -90 : 90);
                module.setEnabled(!config.equals("off"));
                module.culling.set(config.equals("culling") || config.equals("both"));
                module.optimized.set(config.equals("optimized") || config.equals("both"));
                ParticleCore.refresh();
                seq = 3; ticks = 0;
                return retry(WARM_TICKS);
            }
            case 3:
                ParticleCore.resetStats();
                FRAMES.reset();
                aliveSum = 0; aliveSamples = 0; ticks = 0; measuring = true;
                seq = 4;
                return retry(MEASURE_TICKS);
            case 4: {
                measuring = false;
                endPhase(mc, VIEWS[phase / CONFIGS.length], CONFIGS[phase % CONFIGS.length]);
                if (++phase < VIEWS.length * CONFIGS.length) { seq = 2; return retry(1); }
                feeding = false;
                look(mc, -90);
                mc.effectRenderer.clearEffects(mc.theWorld);
                module.setEnabled(true); ParticleCore.refresh();
                seq = 5;
                return retry(10);
            }
            case 5:
                functional(mc);
                seq = 6;
                return retry(1);
            case 6: { // the module's page
                module.rates.reset();
                module.rates.set("flame", 40); module.rates.set("smoke", 0); module.rates.set("mobspell", 20); module.rates.set("largesmoke", 60);
                module.hideOthers.set(true);
                menu = new LadsSettingsScreen189(null);
                mc.displayGuiScreen(menu);
                menu.openModule(ParticleCore.MODULE);
                seq = 7;
                return retry(20);
            }
            case 7:
                CoreProbe.screenshot(mc, "particlecore-card");
                seq = 8;
                return retry(5);
            case 8: { // the small QA window shows two rows of the page: scroll until the rates row is in view
                if (menu.ui().controlBounds("option:" + ParticleCore.RATES) != null || ++scrolls > 14) { seq = 9; return retry(3); }
                menu.ui().mouseScrolled(menu.width * 0.4, menu.height * 0.6, -1);
                return retry(2);
            }
            case 9:
                CoreProbe.screenshot(mc, "particlecore-card-rates");
                seq = 10;
                return retry(5);
            case 10: {
                com.thelads.core.client.gui.LadsSettingsScreen.Rect bounds = menu.ui().controlBounds("option:" + ParticleCore.RATES);
                check(bounds != null, "the Spawn rates row is on the module page");
                CoreProbe.click(bounds.x() + bounds.width() / 2, bounds.y() + bounds.height() / 2);
                seq = 11;
                return retry(20);
            }
            case 11:
                CoreProbe.screenshot(mc, "particlecore-editor");
                for (char c : "fire".toCharArray()) menu.ui().charTyped(c);
                seq = 12;
                return retry(20);
            case 12: {
                CoreProbe.screenshot(mc, "particlecore-editor-search");
                com.thelads.core.client.gui.LadsSettingsScreen.Rect track = menu.ui().particleRatesTrack("flame");
                check(track != null, "the flame row is listed while searching 'fire'");
                menu.ui().mouseClicked(track.x(), track.y() + track.height() / 2.0, 0);
                menu.ui().mouseReleased(track.x(), track.y() + track.height() / 2.0, 0);
                check(module.rates.percent("flame") == 0, "a click at the far left of a slider switches the type off");
                menu.ui().mouseClicked(track.x() + track.width() / 2.0, track.y() + track.height() / 2.0, 0);
                menu.ui().mouseReleased(track.x() + track.width() / 2.0, track.y() + track.height() / 2.0, 0);
                check(module.rates.percent("flame") == 50, "a click in the middle is 50%");
                menu.ui().keyPressed(1, 0); // Esc: the editor first
                check(mc.currentScreen == menu, "Esc closes the rates editor and leaves the module page");
                mc.displayGuiScreen(null);
                seq = 13;
                return retry(5);
            }
            default:
                finish(mc);
                return after(5);
        }
    }

    private static void look(Minecraft mc, float yaw) {
        mc.thePlayer.rotationYaw = yaw; mc.thePlayer.prevRotationYaw = yaw;
        mc.thePlayer.rotationPitch = 0; mc.thePlayer.prevRotationPitch = 0;
    }

    private static void spawn(Minecraft mc, EnumParticleTypes type, double x, double y, double z, double dx, double dy, double dz) {
        mc.effectRenderer.spawnEffectParticle(type.getParticleID(), x, y, z, dx, dy, dz);
    }

    /** One tick of the scene. */
    private static void feed(Minecraft mc) {
        for (int i = 0; i < EMITTERS; i++) {
            double x = px + EX[i], z = pz + EZ[i];
            for (int k = 0; k < 4; k++) spawn(mc, EnumParticleTypes.SMOKE_LARGE, x + RANDOM.nextDouble() - 0.5, py + 0.6, z + RANDOM.nextDouble() - 0.5, 0, 0.05, 0);
            for (int k = 0; k < 2; k++) spawn(mc, EnumParticleTypes.FLAME, x + RANDOM.nextDouble() - 0.5, py + 0.3, z + RANDOM.nextDouble() - 0.5, 0, 0.03, 0);
            spawn(mc, EnumParticleTypes.SMOKE_NORMAL, x, py + 0.8, z, 0, 0.04, 0);
        }
        for (int i = 0; i < 3; i++) {
            int e = RANDOM.nextInt(EMITTERS);
            spawn(mc, EnumParticleTypes.EXPLOSION_NORMAL, px + EX[e], py + 1 + RANDOM.nextDouble() * 2, pz + EZ[e], 0, 0, 0);
        }
        for (int i = 0; i < 8; i++) {
            int e = RANDOM.nextInt(EMITTERS);
            spawn(mc, EnumParticleTypes.SPELL_MOB, px + EX[e] + RANDOM.nextDouble() - 0.5, py + RANDOM.nextDouble() * 1.8, pz + EZ[e] + RANDOM.nextDouble() - 0.5,
                RANDOM.nextDouble(), RANDOM.nextDouble(), RANDOM.nextDouble());
        }
    }

    private static int alive(Minecraft mc) {
        String text = mc.effectRenderer.getStatistics();
        try { return Integer.parseInt(text.trim()); } catch (RuntimeException e) { return -1; }
    }

    private static double round(double v) { return Math.round(v * 100) / 100.0; }

    private static void endPhase(Minecraft mc, String view, String config) {
        double seconds = (System.nanoTime() - FRAMES.start) / 1e9;
        long frames = FRAMES.count;
        double[] sorted = Arrays.copyOf(FRAMES.gaps, (int) Math.min(frames, FRAMES.gaps.length));
        Arrays.sort(sorted);
        double mean = 0;
        for (double v : sorted) mean += v;
        mean = sorted.length == 0 ? 0 : mean / sorted.length;
        double p99 = sorted.length == 0 ? 0 : sorted[(int) Math.min(sorted.length - 1, Math.floor(sorted.length * 0.99))];
        JsonObject entry = new JsonObject();
        entry.addProperty("phase", view + "-" + config);
        entry.addProperty("view", view);
        entry.addProperty("config", config);
        entry.addProperty("frames", frames);
        entry.addProperty("avgFps", round(frames / seconds));
        entry.addProperty("frameMeanMs", round(mean));
        entry.addProperty("frameP99Ms", round(p99));
        entry.addProperty("particleCpuMsPerFrame", round(frames == 0 ? 0 : ParticleCore.particleNanos() / 1e6 / frames));
        entry.addProperty("aliveAvg", aliveSamples == 0 ? 0 : aliveSum / aliveSamples);
        entry.addProperty("drawnPerFrame", round(frames == 0 ? 0 : (double) ParticleCore.drawn / frames));
        entry.addProperty("culledPerFrame", round(frames == 0 ? 0 : (double) ParticleCore.culled / frames));
        entry.addProperty("lightReusedPerFrame", round(frames == 0 ? 0 : (double) ParticleCore.reused / frames));
        PHASES.add(entry);
        LOG.info("Lads particle core phase {}-{}: {}", view, config, entry);
    }

    private static double value(String phase, String key) {
        for (JsonElement p : PHASES) if (p.getAsJsonObject().get("phase").getAsString().equals(phase)) return p.getAsJsonObject().get(key).getAsDouble();
        return -1;
    }

    private static int count(Minecraft mc, EnumParticleTypes type, double x, double y, double z, int attempts) {
        int made = 0;
        for (int i = 0; i < attempts; i++) if (mc.effectRenderer.spawnEffectParticle(type.getParticleID(), x, y + 0.5, z, 0, 0, 0) != null) made++;
        return made;
    }

    /** The behaviours with no picture: rates, potion switches, light reuse. */
    private static void functional(Minecraft mc) {
        ParticleCoreModule module = module();
        module.rates.reset(); module.hideOwn.set(false); module.hideOthers.set(false);
        module.culling.set(true); module.optimized.set(true); ParticleCore.refresh();
        double x = mc.thePlayer.posX, y = mc.thePlayer.posY, z = mc.thePlayer.posZ;
        check(count(mc, EnumParticleTypes.FLAME, x + 3, y, z, 200) == 200, "vanilla: all 200 flames spawn");
        module.rates.set("flame", 0);
        check(count(mc, EnumParticleTypes.FLAME, x + 3, y, z, 200) == 0, "flame at 0%: none of 200 spawn");
        module.rates.set("flame", 50);
        int half = count(mc, EnumParticleTypes.FLAME, x + 3, y, z, 400);
        CHECKS.addProperty("flame50Spawned", half + " of 400");
        check(half > 160 && half < 240, "flame at 50%: " + half + " of 400 spawn");
        module.rates.reset();
        check(count(mc, EnumParticleTypes.SPELL_MOB, x, y, z, 50) == 50 && count(mc, EnumParticleTypes.SPELL_MOB, x + 6, y, z, 50) == 50, "potion swirls on you and on others spawn by default");
        module.hideOwn.set(true);
        check(count(mc, EnumParticleTypes.SPELL_MOB, x, y, z, 50) == 0 && count(mc, EnumParticleTypes.SPELL_MOB_AMBIENT, x + 6, y, z, 50) == 50, "Hide own potion particles: yours gone, others' stay");
        module.hideOwn.set(false); module.hideOthers.set(true);
        check(count(mc, EnumParticleTypes.SPELL_MOB, x, y, z, 50) == 50 && count(mc, EnumParticleTypes.SPELL_MOB_AMBIENT, x + 6, y, z, 50) == 0, "Hide others' potion particles: theirs gone, yours stay");
        module.hideOthers.set(false);
        check(count(mc, EnumParticleTypes.CRIT, x, y, z, 50) == 50, "other types are untouched by the potion switches");
        CHECKS.addProperty("lightReusedInOptimizedPhases", value("east-optimized", "lightReusedPerFrame") + " / " + value("east-both", "lightReusedPerFrame") + " per frame");
        check(value("east-optimized", "lightReusedPerFrame") > 0 && value("east-both", "lightReusedPerFrame") > 0, "Optimized rendering reuses lightmap lookups");
        check(value("east-culling", "lightReusedPerFrame") == 0 && value("east-off", "lightReusedPerFrame") == 0, "with Optimized rendering off no lookup is reused");
        check(value("west-both", "culledPerFrame") > value("west-both", "drawnPerFrame") * 5, "looking away, Culling leaves out nearly all of the cluster");
        check(value("west-optimized", "culledPerFrame") == 0 && value("west-optimized", "drawnPerFrame") > 0, "Culling off draws every particle");
        check(value("west-off", "culledPerFrame") == 0, "Particle Core off: nothing is culled");
    }

    private static void finish(Minecraft mc) throws Exception {
        ParticleCoreModule module = module();
        module.rates.load(new com.google.gson.JsonParser().parse(ratesBefore));
        module.setEnabled(enabledBefore); module.culling.set(cullBefore); module.optimized.set(optBefore);
        module.hideOwn.set(ownBefore); module.hideOthers.set(othersBefore);
        ParticleCore.refresh();
        ParticleCore.timing = false;
        MinecraftForge.EVENT_BUS.unregister(FRAMES);
        mc.effectRenderer.clearEffects(mc.theWorld);
        mc.thePlayer.rotationYaw = yawBefore; mc.thePlayer.rotationPitch = pitchBefore;
        command(mc, "difficulty " + difficultyWas); // the next runs' QA world as it was
        JsonObject report = new JsonObject();
        report.add("phases", PHASES);
        report.add("checks", CHECKS);
        File folder = new File(new File(mc.mcDataDir, "lads-qa"), "screenshots");
        folder.mkdirs();
        Files.write(new File(folder, "particle-core.json").toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
        LOG.info("Lads particle core probe END: {}", report);
    }

    /** Called when the probe run is cut short. */
    static void stop() {
        measuring = feeding = false;
        ParticleCore.timing = false;
        try { MinecraftForge.EVENT_BUS.unregister(FRAMES); } catch (RuntimeException ignored) {}
    }
}
