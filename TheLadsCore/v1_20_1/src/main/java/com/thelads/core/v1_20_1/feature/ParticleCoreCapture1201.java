package com.thelads.core.v1_20_1.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.ParticleCore;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ParticleCoreModule;
import com.thelads.core.v1_20_1.gui.LadsSettingsScreen1201;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (-Dthelads.verifyAutoWorld from the 1.20.1 harness plus LADS_PARTICLE_CORE_CAPTURE=1, only under artifacts/verification;
 * plain Fabric and Zero SMP alike): Particle Core in a heavy particle scene, as 26.3's ParticleCoreCapture. Campfire smoke (each
 * lives 280 to 330 ticks), flames, explosions and potion swirls round a cluster east of the player on a peaceful noon with the
 * player held still. The view east and west with Particle Core off, with Culling only, with Optimized rendering only and with
 * both, three rounds interleaved: frames, the game's particle drawing time where it can be measured (ParticleCore.timing), particles
 * alive, drawn and culled per frame, lightmap lookups reused. Then rates, potion switches and the module's pages. Writes
 * screenshots/particle-core.json and the pictures, logs "Lads particle core capture END:", and asks the harness to stop the game.
 */
public final class ParticleCoreCapture1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final int WARM_TICKS = 50, MEASURE_TICKS = 200, EMITTERS = 36, ROUNDS = 3;
    private static final String[] VIEWS = {"east", "west"}, CONFIGS = {"off", "culling", "optimized", "both"};
    private interface Step { int run(Minecraft mc) throws Exception; }
    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static final JsonArray PHASES = new JsonArray();
    private static final JsonObject CHECKS = new JsonObject();
    private static final double[] EX = new double[EMITTERS], EZ = new double[EMITTERS];
    private static final java.util.Random RANDOM = new java.util.Random(135);
    private static final double[] FRAME_MS = new double[8192];
    private static int index = -1, wait, ticks, saved, aliveSamples, scrolls;
    private static boolean started, measuring, feeding, enabledBefore, cullBefore, optBefore, ownBefore, othersBefore;
    private static Path game;
    private static double px, py, pz;
    private static float yawBefore, pitchBefore;
    private static long frames, lastFrame, startNanos, aliveSum;
    private static com.google.gson.JsonElement ratesBefore;

    private ParticleCoreCapture1201() {}

    /** Armed by the 1.20.1 entrypoint; does nothing unless the QA flags are set. */
    static void register() {
        if (!Boolean.getBoolean("thelads.verifyAutoWorld") || !"1".equals(System.getenv("LADS_PARTICLE_CORE_CAPTURE"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(ParticleCoreCapture1201::tick);
        LOGGER.info("Lads particle core capture armed");
    }

    /** One frame of the level was drawn (LevelRendererMixin). */
    public static void frame() {
        if (!measuring) return;
        long now = System.nanoTime();
        if (lastFrame != 0 && frames < FRAME_MS.length) FRAME_MS[(int) frames] = (now - lastFrame) / 1e6;
        frames++;
        lastFrame = now;
    }

    private static ParticleCoreModule module() { return (ParticleCoreModule) ModuleManager.getInstance().getModule(ParticleCore.MODULE); }

    private static void tick(Minecraft mc) {
        if (index >= STEPS.size()) return;
        if (!started) {
            if (mc.level == null || mc.player == null || mc.screen != null || mc.player.tickCount < 200 || !mc.levelRenderer.hasRenderedAllChunks()) return;
            try {
                game = FabricLoader.getInstance().getGameDir().toRealPath();
                if (!game.toString().replace('\\', '/').toLowerCase(Locale.ROOT).contains("/artifacts/verification/")) throw new IOException("not a QA folder: " + game);
            } catch (IOException failure) { LOGGER.error("Lads particle core capture FAILED: {}", failure.toString()); index = Integer.MAX_VALUE; return; }
            started = true;
            ParticleCoreModule module = module();
            enabledBefore = module.isEnabled(); cullBefore = module.culling.get(); optBefore = module.optimized.get();
            ownBefore = module.hideOwn.get(); othersBefore = module.hideOthers.get();
            ratesBefore = module.rates.save().deepCopy();
            yawBefore = mc.player.getYRot(); pitchBefore = mc.player.getXRot();
            buildSteps();
            ParticleCore.timing = true;
            LOGGER.info("Lads particle core capture BEGIN: {} emitters, {} steps; vertex work {}", EMITTERS, STEPS.size(), ParticleCore1201.deferVertices() ? "left to Sodium/Embeddium" : "ours");
            index = 0; wait = 2;
            return;
        }
        releaseKeys(mc);
        if (mc.player.isDeadOrDying()) mc.player.respawn();
        if (feeding) feed(mc);
        if (measuring) {
            if (ticks % 20 == 0) { aliveSum += alive(mc); aliveSamples++; }
            ticks++;
        }
        if (wait > 0) { wait--; return; }
        try {
            int next = STEPS.get(index).run(mc);
            if (next < 0) { wait = 2; return; } // the step asked to run again
            wait = next;
        } catch (Exception failure) {
            fail("step " + index + ": " + failure);
            LOGGER.error("Lads particle core capture step failed", failure);
            wait = 0;
        }
        if (++index >= STEPS.size()) finish(mc);
    }

    private static void releaseKeys(Minecraft mc) {
        var o = mc.options;
        o.keyUp.setDown(false); o.keyDown.setDown(false); o.keyLeft.setDown(false); o.keyRight.setDown(false);
        o.keyJump.setDown(false); o.keyShift.setDown(false); o.keySprint.setDown(false);
    }

    private static void command(Minecraft mc, String text) { mc.player.connection.sendCommand(text); }

    private static void shot(Minecraft mc, String name) {
        try {
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(folder.resolve(name + ".png")); }
            saved++;
            LOGGER.info("Lads particle core frame {}", name);
        } catch (IOException failure) { fail(name + ": " + failure); }
    }

    private static void buildSteps() {
        STEPS.clear();
        STEPS.add(mc -> { command(mc, "difficulty peaceful"); return 3; });
        STEPS.add(mc -> { command(mc, "time set noon"); return 3; });
        STEPS.add(mc -> { command(mc, "weather clear"); return 3; });
        STEPS.add(mc -> { command(mc, "tp @s ~ ~ ~ -90 0"); return 20; });
        STEPS.add(mc -> {
            px = mc.player.getX(); py = mc.player.getY(); pz = mc.player.getZ();
            for (int i = 0; i < EMITTERS; i++) { // an arc east of the player, 5 to 9.5 blocks out
                double angle = Math.toRadians(-52 + 104.0 * i / (EMITTERS - 1)), radius = 5 + (i % 4) * 1.5;
                EX[i] = Math.cos(angle) * radius; EZ[i] = Math.sin(angle) * radius;
            }
            module().setEnabled(false); ParticleCore.refresh();
            look(mc, -90);
            feeding = true;
            LOGGER.info("Lads particle core capture scene at ({}, {}, {})", px, py, pz);
            return 420; // filled to its steady state, Particle Core off
        });
        for (int round = 1; round <= ROUNDS; round++)
            for (String view : VIEWS)
                for (String config : CONFIGS) {
                    String name = view + "-" + config, picture = round == ROUNDS ? "particlecore-" + name : null;
                    int r = round;
                    STEPS.add(mc -> {
                        look(mc, view.equals("east") ? -90 : 90);
                        ParticleCoreModule module = module();
                        module.setEnabled(!config.equals("off"));
                        module.culling.set(config.equals("culling") || config.equals("both"));
                        module.optimized.set(config.equals("optimized") || config.equals("both"));
                        ParticleCore.refresh();
                        return WARM_TICKS;
                    });
                    STEPS.add(mc -> { begin(); return MEASURE_TICKS; });
                    STEPS.add(mc -> { end(mc, name, view, config, r); if (picture != null && (name.equals("east-both") || name.equals("west-both") || name.equals("west-off") || name.equals("east-off"))) shot(mc, picture); return 1; });
                }
        STEPS.add(mc -> { feeding = false; look(mc, -90); mc.particleEngine.setLevel(mc.level); module().setEnabled(true); ParticleCore.refresh(); return 10; });
        STEPS.add(mc -> { functional(mc); return 1; });
        STEPS.add(mc -> {
            var rates = module().rates;
            rates.reset(); rates.set("flame", 40); rates.set("smoke", 0); rates.set("entity_effect", 20); rates.set("campfire_cosy_smoke", 60);
            module().hideOthers.set(true);
            var menu = new LadsSettingsScreen1201(null);
            mc.setScreen(menu);
            menu.openModule(ParticleCore.MODULE);
            return 20;
        });
        STEPS.add(mc -> { shot(mc, "particlecore-card"); return 2; });
        STEPS.add(mc -> { // the small QA window shows two rows of the page: scroll until the rates row is in view
            var ui = ((LadsSettingsScreen1201) mc.screen).ui();
            if (ui.controlBounds("option:" + ParticleCore.RATES) != null || ++scrolls > 14) return 3;
            ui.mouseScrolled(mc.getWindow().getGuiScaledWidth() * 0.4, mc.getWindow().getGuiScaledHeight() * 0.6, -1);
            return -1;
        });
        STEPS.add(mc -> { shot(mc, "particlecore-card-rates"); return 2; });
        STEPS.add(mc -> {
            var ui = ((LadsSettingsScreen1201) mc.screen).ui();
            var bounds = ui.controlBounds("option:" + ParticleCore.RATES);
            check(bounds != null, "the Spawn rates row is on the module page");
            if (bounds != null) ui.mouseClicked(bounds.x() + bounds.width() / 2.0, bounds.y() + bounds.height() / 2.0, 0);
            return 20;
        });
        STEPS.add(mc -> { shot(mc, "particlecore-editor"); return 2; });
        STEPS.add(mc -> {
            var ui = ((LadsSettingsScreen1201) mc.screen).ui();
            for (char c : "flam".toCharArray()) ui.charTyped(c);
            return 20;
        });
        STEPS.add(mc -> { shot(mc, "particlecore-editor-search"); return 2; });
        STEPS.add(mc -> {
            var ui = ((LadsSettingsScreen1201) mc.screen).ui();
            var track = ui.particleRatesTrack("flame");
            check(track != null, "the flame row is listed while searching 'flam'");
            if (track != null) {
                ui.mouseClicked(track.x(), track.y() + track.height() / 2.0, 0);
                ui.mouseReleased(track.x(), track.y() + track.height() / 2.0, 0);
                check(module().rates.percent("flame") == 0, "a click at the far left of a slider switches the type off");
                ui.mouseClicked(track.x() + track.width() / 2.0, track.y() + track.height() / 2.0, 0);
                ui.mouseReleased(track.x() + track.width() / 2.0, track.y() + track.height() / 2.0, 0);
                check(module().rates.percent("flame") == 50, "a click in the middle is 50%");
            }
            return 10;
        });
        STEPS.add(mc -> {
            var ui = ((LadsSettingsScreen1201) mc.screen).ui();
            ui.keyPressed(256, 0);
            check(mc.screen instanceof LadsSettingsScreen1201, "Esc closes the rates editor and leaves the module page");
            mc.setScreen(null);
            return 5;
        });
    }

    private static void look(Minecraft mc, float yaw) {
        mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setXRot(0); mc.player.xRotO = 0;
        mc.player.setYHeadRot(yaw);
    }

    /** One tick of the scene. */
    private static void feed(Minecraft mc) {
        var engine = mc.particleEngine;
        var random = RANDOM;
        for (int i = 0; i < EMITTERS; i++) {
            double x = px + EX[i], z = pz + EZ[i];
            engine.createParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, py + 0.5, z, 0, 0.07, 0);
            if (i % 2 == 0) engine.createParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, py + 0.5, z, 0, 0.05, 0);
            if (i % 3 == 0) engine.createParticle(ParticleTypes.FLAME, x, py + 0.2, z, 0, 0.02, 0);
        }
        for (int i = 0; i < 3; i++) {
            int e = random.nextInt(EMITTERS);
            engine.createParticle(ParticleTypes.EXPLOSION, px + EX[e], py + 1 + random.nextDouble() * 2, pz + EZ[e], 0, 0, 0);
        }
        for (int i = 0; i < 8; i++) { // a potion swirl takes its colour from its velocity
            int e = random.nextInt(EMITTERS);
            engine.createParticle(ParticleTypes.ENTITY_EFFECT, px + EX[e] + random.nextDouble() - 0.5, py + random.nextDouble() * 1.8, pz + EZ[e] + random.nextDouble() - 0.5,
                random.nextDouble(), random.nextDouble(), random.nextDouble());
        }
    }

    private static int alive(Minecraft mc) {
        try { return Integer.parseInt(mc.particleEngine.countParticles().trim()); } catch (RuntimeException e) { return -1; }
    }

    private static void begin() {
        ParticleCore.resetStats();
        frames = 0; lastFrame = 0; aliveSum = 0; aliveSamples = 0; ticks = 0;
        startNanos = System.nanoTime();
        measuring = true;
    }

    private static double round(double v) { return Math.round(v * 100) / 100.0; }

    private static void end(Minecraft mc, String name, String view, String config, int round) {
        measuring = false;
        double seconds = (System.nanoTime() - startNanos) / 1e9;
        double[] sorted = Arrays.copyOf(FRAME_MS, (int) Math.min(frames, FRAME_MS.length));
        Arrays.sort(sorted);
        double mean = 0; for (double v : sorted) mean += v;
        mean = sorted.length == 0 ? 0 : mean / sorted.length;
        double p99 = sorted.length == 0 ? 0 : sorted[(int) Math.min(sorted.length - 1, Math.floor(sorted.length * 0.99))];
        JsonObject phase = new JsonObject();
        phase.addProperty("phase", name);
        phase.addProperty("round", round);
        phase.addProperty("view", view);
        phase.addProperty("config", config);
        phase.addProperty("frames", frames);
        phase.addProperty("avgFps", round(frames / seconds));
        phase.addProperty("frameMeanMs", round(mean));
        phase.addProperty("frameP99Ms", round(p99));
        phase.addProperty("particleCpuMsPerFrame", round(frames == 0 ? 0 : ParticleCore.particleNanos() / 1e6 / frames));
        phase.addProperty("aliveAvg", aliveSamples == 0 ? 0 : aliveSum / aliveSamples);
        phase.addProperty("drawnPerFrame", round(frames == 0 ? 0 : (double) ParticleCore.drawn / frames));
        phase.addProperty("culledPerFrame", round(frames == 0 ? 0 : (double) ParticleCore.culled / frames));
        phase.addProperty("lightReusedPerFrame", round(frames == 0 ? 0 : (double) ParticleCore.reused / frames));
        phase.addProperty("playerX", round(mc.player.getX()));
        phase.addProperty("playerZ", round(mc.player.getZ()));
        phase.addProperty("yaw", round(mc.player.getYRot()));
        PHASES.add(phase);
        LOGGER.info("Lads particle core phase {} round {}: {}", name, round, phase);
    }

    private static double median(String phase, String key) {
        List<Double> values = new ArrayList<>();
        for (var p : PHASES) if (p.getAsJsonObject().get("phase").getAsString().equals(phase)) values.add(p.getAsJsonObject().get(key).getAsDouble());
        if (values.isEmpty()) return -1;
        values.sort(null);
        return values.get(values.size() / 2);
    }

    private static int count(net.minecraft.client.particle.ParticleEngine engine, ParticleOptions options, Vec3 at, int attempts) {
        int made = 0;
        for (int i = 0; i < attempts; i++) if (engine.createParticle(options, at.x, at.y + 0.5, at.z, 0.2, 0.4, 1.0) != null) made++;
        return made;
    }

    private static void functional(Minecraft mc) {
        ParticleCoreModule module = module();
        module.rates.reset(); module.hideOwn.set(false); module.hideOthers.set(false);
        module.culling.set(true); module.optimized.set(true); ParticleCore.refresh();
        var engine = mc.particleEngine;
        Vec3 here = mc.player.position(), other = here.add(6, 0, 0);
        check(count(engine, ParticleTypes.FLAME, here.add(3, 0, 0), 200) == 200, "vanilla: all 200 flames spawn");
        module.rates.set("flame", 0);
        check(count(engine, ParticleTypes.FLAME, here.add(3, 0, 0), 200) == 0, "flame at 0%: none of 200 spawn");
        module.rates.set("flame", 50);
        int half = count(engine, ParticleTypes.FLAME, here.add(3, 0, 0), 400);
        CHECKS.addProperty("flame50Spawned", half + " of 400");
        check(half > 160 && half < 240, "flame at 50%: " + half + " of 400 spawn");
        module.rates.reset();
        ParticleOptions swirl = ParticleTypes.ENTITY_EFFECT;
        check(count(engine, swirl, here, 50) == 50 && count(engine, swirl, other, 50) == 50, "potion swirls on you and on others spawn by default");
        module.hideOwn.set(true); ParticleCore.refresh();
        check(count(engine, swirl, here, 50) == 0 && count(engine, swirl, other, 50) == 50, "Hide own potion particles: yours gone, others' stay");
        module.hideOwn.set(false); module.hideOthers.set(true); ParticleCore.refresh();
        check(count(engine, swirl, here, 50) == 50 && count(engine, swirl, other, 50) == 0, "Hide others' potion particles: theirs gone, yours stay");
        module.hideOthers.set(false); ParticleCore.refresh();
        check(count(engine, ParticleTypes.CRIT, here, 50) == 50, "other types are untouched by the potion switches");
        CHECKS.addProperty("lightReusedPerFrameOptimizedPhases", median("east-optimized", "lightReusedPerFrame") + " / " + median("east-both", "lightReusedPerFrame"));
        check(median("east-optimized", "lightReusedPerFrame") > 0 && median("east-both", "lightReusedPerFrame") > 0, "Optimized rendering reuses lightmap lookups");
        check(median("east-culling", "lightReusedPerFrame") == 0 && median("east-off", "lightReusedPerFrame") == 0, "with Optimized rendering off no lookup is reused");
        check(median("west-both", "culledPerFrame") > median("west-both", "drawnPerFrame") * 5, "looking away, Culling leaves out nearly all of the cluster");
        check(median("west-optimized", "culledPerFrame") == 0 && median("west-optimized", "drawnPerFrame") > 0, "Culling off draws every particle");
    }

    private static void finish(Minecraft mc) {
        ParticleCoreModule module = module();
        module.rates.load(ratesBefore);
        module.setEnabled(enabledBefore); module.culling.set(cullBefore); module.optimized.set(optBefore);
        module.hideOwn.set(ownBefore); module.hideOthers.set(othersBefore);
        ParticleCore.refresh();
        ParticleCore.timing = false;
        mc.particleEngine.setLevel(mc.level); // clears every particle
        mc.player.setYRot(yawBefore); mc.player.setXRot(pitchBefore);
        command(mc, "difficulty peaceful");
        JsonObject medians = new JsonObject();
        for (String view : VIEWS)
            for (String config : CONFIGS) {
                String name = view + "-" + config;
                JsonObject m = new JsonObject();
                for (String key : new String[] {"avgFps", "frameMeanMs", "frameP99Ms", "particleCpuMsPerFrame", "aliveAvg", "drawnPerFrame", "culledPerFrame", "lightReusedPerFrame"})
                    m.addProperty(key, median(name, key));
                medians.add(name, m);
            }
        JsonObject report = new JsonObject();
        report.addProperty("vertexWorkLeftToSodiumOrEmbeddium", ParticleCore1201.deferVertices());
        report.addProperty("rounds", ROUNDS);
        report.addProperty("measureTicks", MEASURE_TICKS);
        report.add("medians", medians);
        report.add("phases", PHASES);
        report.add("checks", CHECKS);
        try {
            Files.writeString(game.resolve("screenshots").resolve("particle-core.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
        } catch (Exception failure) { FAILURES.add("particle-core.json: " + failure); }
        if (FAILURES.isEmpty()) LOGGER.info("Lads particle core capture END: {} frames saved, 0 failed; medians {}", saved, medians);
        else LOGGER.error("Lads particle core capture FAILED: {} | medians {}", String.join(" | ", FAILURES), medians);
        try { Files.writeString(game.resolve(".lads-qa-stop"), "particle core capture finished"); } catch (IOException ignored) { }
    }

    private static void check(boolean result, String description) {
        if (result) LOGGER.info("Lads particle core capture PASS: {}", description);
        else fail(description);
    }

    private static void fail(String failure) {
        FAILURES.add(failure);
        LOGGER.error("Lads particle core capture check failed: {}", failure);
    }
}
