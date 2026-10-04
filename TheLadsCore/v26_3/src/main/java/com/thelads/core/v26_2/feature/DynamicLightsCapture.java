package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.DynamicLights;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.modules.DynamicLightsModule;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-lights" from the harness's LADS_VERIFY_CAPTURE_LIGHTS): Dynamic Lights at midnight in the QA
 * world, each scene with the module off and on: a held torch, a dropped glowstone and a burning zombie (both client-only), saved as
 * lights-*.png with the frame's mean brightness. Then a torch circles the player for 8 s with the module on and 8 s off to measure
 * the frame rate. Held items are this client's only; the time of day, module settings and Fullbright are put back afterwards.
 */
final class DynamicLightsCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private enum Scene { HELD, DROPPED, BURNING }
    private record Shot(String name, boolean on, Scene scene) {}
    private static final Shot[] SHOTS = {new Shot("torch-off", false, Scene.HELD), new Shot("torch-on", true, Scene.HELD),
        new Shot("glowstone-off", false, Scene.DROPPED), new Shot("glowstone-on", true, Scene.DROPPED),
        new Shot("burning-off", false, Scene.BURNING), new Shot("burning-on", true, Scene.BURNING)};
    private static final long SETTLE = 1_500_000_000L, MEASURE = 8_000_000_000L;
    private static final int FIRST_ID = Integer.MAX_VALUE - 512;
    private static final ItemStack TORCH = new ItemStack(Items.TORCH);
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static final double[] BRIGHTNESS = new double[SHOTS.length];
    private static int step = -1, passed, timeBefore = Integer.MIN_VALUE;
    private static long due, measureStart, frames, worstFrame, lastFrame, ticksBefore, nanosBefore, rebuildsBefore;
    private static boolean capturing, enabledBefore, fullbrightBefore;
    private static long modifiedBefore, fullbrightModified;
    private static float pitchBefore;
    private static ItemStack mainBefore, offBefore;
    private static ItemEntity dropped, orbit;
    private static Zombie burning;
    private static final List<String> RESULTS = new ArrayList<>();
    private DynamicLightsCapture() {}

    /** Shots, then the two measured runs (on, off). */
    static boolean busy() { return step >= 0 && step < SHOTS.length + 2; }

    static void tick(Path game, boolean ready) {
        if (busy()) { hold(); return; }
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-lights");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads dynamic lights capture FAILED: request", failure); return; }
        Minecraft mc = Minecraft.getInstance();
        DynamicLightsModule module = module();
        Module fullbright = NativeQualityOfLife.module("Fullbright");
        enabledBefore = module.isEnabled();
        modifiedBefore = module.getLastModified();
        fullbrightBefore = fullbright.isEnabled();
        fullbrightModified = fullbright.getLastModified();
        for (Option option : module.getOptions()) OPTIONS.put(option, option.save().deepCopy());
        module.getOptions().forEach(Option::reset); // Fancy, radius 15, entities and dropped items on
        fullbright.setEnabled(false);
        mainBefore = mc.player.getItemBySlot(EquipmentSlot.MAINHAND);
        offBefore = mc.player.getItemBySlot(EquipmentSlot.OFFHAND);
        pitchBefore = mc.player.getXRot();
        mc.player.setXRot(50);
        midnight(mc);
        LOGGER.info("Lads dynamic lights capture BEGIN: {} frames and an 8 s + 8 s frame-rate run; Sodium {}; client-only items and mobs",
            SHOTS.length, FabricLoader.getInstance().getModContainer("sodium").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("not loaded"));
        step = 0;
        fire();
        due = System.nanoTime() + 2 * SETTLE; // the night sky settles first
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame). */
    static void frame(RenderTarget target, Path game) {
        if (!busy() || capturing) return;
        long now = System.nanoTime();
        if (step >= SHOTS.length) { measure(now); return; }
        if (now < due) return;
        capturing = true;
        Shot shot = SHOTS[step];
        checkLight(shot);
        int index = step;
        try {
            Path output = game.resolve("screenshots").resolve("lights-" + shot.name() + ".png");
            Files.createDirectories(output.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { BRIGHTNESS[index] = brightness(image); image.writeToFile(output); LOGGER.info("Lads dynamic lights frame {} (mean brightness {})", output, String.format("%.1f", BRIGHTNESS[index])); }
                catch (Exception failure) { fail(shot.name() + ": " + failure); }
                finally { image.close(); Minecraft.getInstance().execute(DynamicLightsCapture::next); }
            });
        } catch (Exception failure) {
            fail(shot.name() + ": " + failure);
            next();
        }
    }

    private static void next() {
        capturing = false;
        if (++step < SHOTS.length) { fire(); due = System.nanoTime() + SETTLE; return; }
        for (int i = 1; i < SHOTS.length; i += 2)
            check(BRIGHTNESS[i] > BRIGHTNESS[i - 1] + 1, SHOTS[i].name() + " is brighter than " + SHOTS[i - 1].name() + " ("
                + String.format("%.1f > %.1f", BRIGHTNESS[i], BRIGHTNESS[i - 1]) + ")");
        clearScene();
        Minecraft mc = Minecraft.getInstance();
        mc.player.setItemSlot(EquipmentSlot.MAINHAND, TORCH);
        orbit = new ItemEntity(mc.level, mc.player.getX(), mc.player.getY() + 0.5, mc.player.getZ(), new ItemStack(Items.TORCH), 0, 0, 0);
        orbit.setNoGravity(true);
        orbit.setId(FIRST_ID + 3);
        mc.level.addEntity(orbit);
        startRun(true);
    }

    private static void startRun(boolean on) {
        module().setEnabled(on);
        measureStart = 0;
        due = System.nanoTime() + SETTLE;
    }

    /** The two runs: frames counted from the first frame after the settle time, the slowest frame noted. */
    private static void measure(long now) {
        if (now < due) return;
        if (measureStart == 0) {
            measureStart = lastFrame = now;
            frames = worstFrame = 0;
            ticksBefore = NativeDynamicLights.ticks;
            nanosBefore = NativeDynamicLights.tickNanos;
            rebuildsBefore = NativeDynamicLights.rebuilds;
            return;
        }
        frames++;
        worstFrame = Math.max(worstFrame, now - lastFrame);
        lastFrame = now;
        if (now - measureStart < MEASURE) return;
        boolean on = step == SHOTS.length;
        double seconds = (now - measureStart) / 1e9;
        long ticks = NativeDynamicLights.ticks - ticksBefore;
        String result = String.format("module %s: %.1f FPS, slowest frame %.1f ms, source scan %.1f us per tick, %.1f section rebuild boxes per second",
            on ? "on" : "off", frames / seconds, worstFrame / 1e6, ticks == 0 ? 0 : (NativeDynamicLights.tickNanos - nanosBefore) / 1e3 / ticks,
            (NativeDynamicLights.rebuilds - rebuildsBefore) / seconds);
        RESULTS.add(result);
        LOGGER.info("Lads dynamic lights capture moving torch, {}", result);
        if (on) check(NativeDynamicLights.rebuilds > rebuildsBefore, "a moving torch rebuilds the sections around it");
        else check(NativeDynamicLights.rebuilds == rebuildsBefore, "with the module off nothing is rebuilt");
        if (++step == SHOTS.length + 1) startRun(false);
        else finish();
    }

    /** Puts the shot's scene in place: the module, the held item, a dropped glowstone or a burning zombie in front. */
    private static void fire() {
        Minecraft mc = Minecraft.getInstance();
        Shot shot = SHOTS[step];
        module().setEnabled(shot.on());
        if (step > 0 && SHOTS[step - 1].scene() == shot.scene()) return;
        clearScene();
        LocalPlayer player = mc.player;
        Vec3 look = player.getViewVector(1);
        Vec3 ahead = new Vec3(look.x, 0, look.z).normalize().scale(2.5).add(player.position());
        switch (shot.scene()) {
            case HELD -> player.setItemSlot(EquipmentSlot.MAINHAND, TORCH);
            case DROPPED -> {
                dropped = new ItemEntity(mc.level, ahead.x, ahead.y + 0.25, ahead.z, new ItemStack(Items.GLOWSTONE), 0, 0, 0);
                dropped.setNoGravity(true);
                dropped.setId(FIRST_ID + 1);
                mc.level.addEntity(dropped);
            }
            case BURNING -> {
                burning = new Zombie(mc.level);
                burning.snapTo(ahead.x, ahead.y, ahead.z, player.getYRot() + 180, 0);
                burning.setNoGravity(true);
                burning.setId(FIRST_ID + 2);
                mc.level.addEntity(burning);
            }
        }
        hold();
    }

    /** Each tick: the scene's held item stays (a server slot update puts the real one back), the zombie keeps burning, the torch circles. */
    private static void hold() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean torch = step >= SHOTS.length || SHOTS[step].scene() == Scene.HELD;
        ItemStack expected = torch ? TORCH : ItemStack.EMPTY;
        if (player.getItemBySlot(EquipmentSlot.MAINHAND) != expected) player.setItemSlot(EquipmentSlot.MAINHAND, expected);
        if (player.getItemBySlot(EquipmentSlot.OFFHAND) != ItemStack.EMPTY) player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        if (burning != null) {
            burning.setRemainingFireTicks(100);
            burning.setSharedFlagOnFire(true);
        }
        if (orbit != null) { // 5 blocks a second on a 4-block circle
            double angle = System.nanoTime() / 1e9 * 1.25;
            orbit.setPos(player.getX() + Math.cos(angle) * 4, player.getY() + 0.5, player.getZ() + Math.sin(angle) * 4);
        }
    }

    /** The light lookups themselves: the world's (LightCoordsUtil, as chunk meshing) and the entity renderer's (as the held item). */
    private static void checkLight(Shot shot) {
        Minecraft mc = Minecraft.getInstance();
        Entity source = shot.scene() == Scene.HELD ? mc.player : shot.scene() == Scene.DROPPED ? dropped : burning;
        if (source == null) { fail(shot.name() + ": no light source in the scene"); return; }
        BlockPos at = BlockPos.containing(source.getX(), source.getEyeY(), source.getZ());
        int world = mc.level.getBrightness(LightLayer.BLOCK, at), lit = LightCoordsUtil.block(LightCoordsUtil.getLightCoords(mc.level, at));
        int expected = shot.scene() == Scene.HELD ? 14 : 15; // a torch; glowstone, and anything burning
        if (shot.on()) check(lit >= expected - 1 && DynamicLights.WORLD.at(at.getX(), at.getY(), at.getZ()) >= expected - 1,
            shot.name() + ": the light at the source is " + lit + " (world " + world + ")");
        else check(lit == Math.max(world, mc.level.getBlockState(at).getLightEmission()), shot.name() + ": only the world's own light (" + lit + ")");
        if (shot.scene() == Scene.HELD) {
            int hand = LightCoordsUtil.block(mc.getEntityRenderDispatcher().getPackedLightCoords(mc.player, 1));
            int probe = mc.level.getBrightness(LightLayer.BLOCK, BlockPos.containing(mc.player.getLightProbePosition(1)));
            if (shot.on()) check(hand >= 13, shot.name() + ": the player and the held torch are lit " + hand);
            else check(hand == probe, shot.name() + ": the player is lit only by the world (" + hand + ")");
        }
        if (shot.scene() == Scene.BURNING) check(burning.isOnFire(), shot.name() + ": the zombie is burning");
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        clearScene();
        mc.player.setItemSlot(EquipmentSlot.MAINHAND, mainBefore);
        mc.player.setItemSlot(EquipmentSlot.OFFHAND, offBefore);
        mc.player.setXRot(pitchBefore);
        restoreTime(mc);
        DynamicLightsModule module = module();
        OPTIONS.forEach(Option::load);
        module.setEnabled(enabledBefore);
        module.setLastModified(modifiedBefore);
        Module fullbright = NativeQualityOfLife.module("Fullbright");
        fullbright.setEnabled(fullbrightBefore);
        fullbright.setLastModified(fullbrightModified);
        step = SHOTS.length + 2;
        String summary = String.join("; ", RESULTS);
        if (FAILURES.isEmpty()) LOGGER.info("Lads dynamic lights capture END: {} passed, 0 failed; {}", passed, summary);
        else LOGGER.error("Lads dynamic lights capture FAILED: {} | {}", String.join(" | ", FAILURES), summary);
    }

    private static void clearScene() {
        var level = Minecraft.getInstance().level;
        for (int i = 1; i <= 3; i++) level.removeEntity(FIRST_ID + i, Entity.RemovalReason.DISCARDED);
        dropped = null;
        burning = null;
        orbit = null;
    }

    /** Midnight through the integrated server's own time command; the clock is set back afterwards. */
    private static void midnight(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) { fail("no integrated server to set the time"); return; }
        server.execute(() -> {
            try {
                var source = server.createCommandSourceStack().withSuppressedOutput();
                var commands = server.getCommands().getDispatcher();
                timeBefore = commands.execute("time query time", source);
                commands.execute("time add " + Math.floorMod(18000 - timeBefore, 24000), source);
            } catch (Exception failure) { fail("midnight: " + failure); }
        });
    }

    private static void restoreTime(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null || timeBefore == Integer.MIN_VALUE) return;
        int time = timeBefore;
        server.execute(() -> {
            try { server.getCommands().getDispatcher().execute("time set " + time, server.createCommandSourceStack().withSuppressedOutput()); }
            catch (Exception failure) { LOGGER.error("Lads dynamic lights capture: the time could not be set back to {}", time, failure); }
        });
    }

    /** Mean brightness (0-255) of the middle of the frame, where the lit ground is; the hotbar and HUD edges are left out. */
    private static double brightness(NativeImage image) {
        int w = image.getWidth(), h = image.getHeight();
        long sum = 0, count = 0;
        for (int y = h / 5; y < h * 4 / 5; y += 2)
            for (int x = w / 4; x < w * 3 / 4; x += 2) {
                int c = image.getPixel(x, y);
                sum += (c & 0xFF) + (c >> 8 & 0xFF) + (c >> 16 & 0xFF);
                count += 3;
            }
        return (double) sum / count;
    }

    private static DynamicLightsModule module() { return (DynamicLightsModule) NativeQualityOfLife.module(DynamicLightsModule.NAME); }
    private static void check(boolean result, String description) {
        if (result) { passed++; LOGGER.info("Lads dynamic lights capture PASS: {}", description); }
        else fail(description);
    }
    private static void fail(String failure) {
        FAILURES.add(failure);
        LOGGER.error("Lads dynamic lights capture check failed: {}", failure);
    }
}
