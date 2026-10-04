package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.QaSkin;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.SkinLayersModule;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-skinlayers" from the harness's LADS_VERIFY_CAPTURE_SKINLAYERS): the player, wearing
 * QaSkin's checkerboard outer layers and nothing else, photographed from the front (Fullbright on) with the SkinLayers module on
 * (skinlayers-on.png) and off (skinlayers-off.png). Skin, modules, camera, pitch and the client-side equipment are put back.
 */
final class SkinLayersCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String[] SHOTS = {"skinlayers-on", "skinlayers-off"};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
        EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Map<EquipmentSlot, ItemStack> WORN = new EnumMap<>(EquipmentSlot.class);
    private static int step = -1, saved;
    private static long due;
    private static boolean capturing, enabledBefore, brightBefore, ownSkin;
    private static long modifiedBefore, brightModified;
    private static float pitchBefore;
    private static CameraType cameraBefore;
    private static CompletableFuture<String> skin;
    private SkinLayersCapture() {}

    static boolean busy() { return step >= 0 && step <= SHOTS.length; }

    static void tick(Path game, boolean ready) {
        Minecraft mc = Minecraft.getInstance();
        if (step < 0) {
            Path request = game.resolve(".lads-qa-capture-skinlayers");
            if (!ready || !Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            try {
                Files.delete(request);
                Module module = ModuleManager.getInstance().getModule(SkinLayersModule.NAME);
                enabledBefore = module.isEnabled();
                modifiedBefore = module.getLastModified();
                Module bright = ModuleManager.getInstance().getModule("Fullbright");
                brightBefore = bright.isEnabled();
                brightModified = bright.getLastModified();
                cameraBefore = mc.options.getCameraType();
                pitchBefore = mc.player.getXRot();
                for (EquipmentSlot slot : SLOTS) WORN.put(slot, mc.player.getItemBySlot(slot));
                ownSkin = LocalSkins.current() == null; // QA starts without one; a selected skin is left as it is
                if (ownSkin) {
                    Path png = game.resolve(".lads-qa-skinlayers.png");
                    Files.write(png, QaSkin.png());
                    skin = LocalSkins.load(png.toString(), false, false);
                } else skin = CompletableFuture.completedFuture("kept");
                LOGGER.info("Lads skin layers capture BEGIN: {} frames, QA skin, client-only equipment, all restored", SHOTS.length);
                step = 0;
            } catch (Exception failure) { LOGGER.error("Lads skin layers capture FAILED: start", failure); step = SHOTS.length + 1; }
            return;
        }
        if (!busy()) return;
        if (step == 0 && due == 0) {
            if (!skin.isDone()) return;
            if (skin.isCompletedExceptionally()) { LOGGER.error("Lads skin layers capture FAILED: QA skin did not load"); finish(); return; }
            pose(true);
        }
        for (EquipmentSlot slot : SLOTS) // a server slot update would cover the layers again
            if (!mc.player.getItemBySlot(slot).isEmpty()) mc.player.setItemSlot(slot, ItemStack.EMPTY);
    }

    static void frame(RenderTarget target, Path game) {
        if (step < 0 || step >= SHOTS.length || due == 0 || capturing || System.nanoTime() < due) return;
        if (Minecraft.getInstance().gui.screen() != null) return;
        capturing = true;
        String name = SHOTS[step];
        try {
            Path output = game.resolve("screenshots").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); saved++; LOGGER.info("Lads skin layers frame {}", output); }
                catch (Exception failure) { LOGGER.error("Lads skin layers capture FAILED: {}", name, failure); }
                finally { image.close(); Minecraft.getInstance().execute(SkinLayersCapture::next); }
            });
        } catch (Exception failure) {
            LOGGER.error("Lads skin layers capture FAILED: {}", name, failure);
            next();
        }
    }

    private static void next() {
        capturing = false;
        if (++step < SHOTS.length) pose(false);
        else finish();
    }

    /** Front view, level, nothing worn; the module as this shot needs it. */
    private static void pose(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        ModuleManager.getInstance().getModule(SkinLayersModule.NAME).setEnabled(on);
        ModuleManager.getInstance().getModule("Fullbright").setEnabled(true); // the QA world may be at night
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        mc.player.setXRot(0);
        due = System.nanoTime() + 1_500_000_000L;
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        Module module = ModuleManager.getInstance().getModule(SkinLayersModule.NAME);
        module.setEnabled(enabledBefore);
        module.setLastModified(modifiedBefore);
        Module bright = ModuleManager.getInstance().getModule("Fullbright");
        bright.setEnabled(brightBefore);
        bright.setLastModified(brightModified);
        mc.options.setCameraType(cameraBefore);
        mc.player.setXRot(pitchBefore);
        WORN.forEach(mc.player::setItemSlot);
        if (ownSkin) LocalSkins.reset();
        try { Files.deleteIfExists(mc.gameDirectory.toPath().resolve(".lads-qa-skinlayers.png")); }
        catch (Exception failure) { LOGGER.warn("Lads skin layers capture: QA skin file left behind", failure); }
        step = SHOTS.length + 1;
        LOGGER.info("Lads skin layers capture END: {} passed, {} failed", saved, SHOTS.length - saved);
    }
}
