package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.qa.F1914Qa;
import com.thelads.core.v26_2.gui.DraggableHudScreen26;
import com.thelads.core.v26_2.gui.QuickplayScreen26;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 26.3 driver for 1.9.14 feature-capture probe (".lads-qa-capture-f1914" from LADS_VERIFY_CAPTURE_F1914=1).
 * Runs inside the auto QA world, sets up and photographs every 1.9.14 feature.
 */
public final class F1914Capture26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static F1914Qa qa;
    private static boolean running, done;
    private static String pendingShot;
    private static ItemStack[] armorBefore;
    private static ArmorStand armorStand;
    private static CameraType cameraBefore;
    private static float pitchBefore;
    private static Path gamePath;

    private F1914Capture26() {}

    public static boolean busy() { return running; }

    public static void tick(Path game, boolean ready) {
        if (done) return;
        gamePath = game;
        if (!running) {
            Path request = game.resolve(".lads-qa-capture-f1914");
            if (!ready || !Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            try { Files.delete(request); } catch (Exception ignored) {}
            qa = new F1914Qa();
            running = true;
            LOGGER.info("Lads f1914 capture BEGIN (26.3)");
        }
        if (pendingShot != null) return; // wait for next frame to take pending screenshot
        try {
            if (qa.tick(DRIVER)) {
                running = false;
                done = true;
            }
        } catch (Throwable failure) {
            LOGGER.error("Lads f1914 capture FAILED: {}", failure.toString(), failure);
            running = false;
            done = true;
        }
    }

    /** Each completed frame (NativeWorldVerification.renderedFrame): saves the frame requested by the probe. */
    public static void frame(RenderTarget frame, Path game) {
        String name = pendingShot;
        if (name == null) return;
        try {
            Path output = game.resolve("screenshots").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            Screenshot.takeScreenshot(frame, image -> {
                try {
                    image.writeToFile(output);
                    LOGGER.info("Lads f1914 QA frame {}", output);
                } catch (Exception failure) {
                    LOGGER.warn("Lads f1914 QA frame {} not saved", name, failure);
                } finally {
                    image.close();
                }
            });
        } catch (Exception failure) {
            LOGGER.warn("Lads f1914 QA frame {} not saved", name, failure);
        }
        pendingShot = null;
    }

    private static final F1914Qa.Driver DRIVER = new F1914Qa.Driver() {
        @Override
        public void screenshot(String name) {
            pendingShot = name;
        }

        @Override
        public void cropArmorHud(String sourceShot, String destShot) {
            if (gamePath == null) return;
            try {
                Path sourcePath = gamePath.resolve("screenshots").resolve(sourceShot + ".png");
                File sourceFile = sourcePath.toFile();
                if (!sourceFile.isFile()) return;

                BufferedImage full = ImageIO.read(sourceFile);
                HudElement armor = HudManager.getInstance().get("ArmorHUD");
                if (armor == null) return;

                Minecraft mc = Minecraft.getInstance();
                double scale = mc.getWindow().getGuiScale();
                int pad = (int) (4 * scale);

                int px = Math.max(0, (int) (armor.getX() * scale) - pad);
                int py = Math.max(0, (int) (armor.getY() * scale) - pad);
                int pw = Math.min(full.getWidth() - px, (int) (armor.getWidth() * scale) + 2 * pad);
                int ph = Math.min(full.getHeight() - py, (int) (armor.getHeight() * scale) + 2 * pad);

                if (pw > 0 && ph > 0) {
                    BufferedImage cropped = full.getSubimage(px, py, pw, ph);
                    Path destPath = gamePath.resolve("screenshots").resolve(destShot + ".png");
                    ImageIO.write(cropped, "png", destPath.toFile());
                    LOGGER.info("Lads f1914 cropped armor HUD: {}", destPath);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to crop armor HUD for {}", destShot, e);
            }
        }

        @Override
        public void setPlayerDiamondArmor() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                armorBefore = new ItemStack[4];
                armorBefore[0] = mc.player.getItemBySlot(EquipmentSlot.FEET).copy();
                armorBefore[1] = mc.player.getItemBySlot(EquipmentSlot.LEGS).copy();
                armorBefore[2] = mc.player.getItemBySlot(EquipmentSlot.CHEST).copy();
                armorBefore[3] = mc.player.getItemBySlot(EquipmentSlot.HEAD).copy();

                mc.player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                mc.player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                mc.player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                mc.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            }
        }

        @Override
        public void restorePlayerArmor() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && armorBefore != null) {
                mc.player.setItemSlot(EquipmentSlot.FEET, armorBefore[0]);
                mc.player.setItemSlot(EquipmentSlot.LEGS, armorBefore[1]);
                mc.player.setItemSlot(EquipmentSlot.CHEST, armorBefore[2]);
                mc.player.setItemSlot(EquipmentSlot.HEAD, armorBefore[3]);
            }
        }

        @Override
        public void setCamera(int view, float pitch) {
            Minecraft mc = Minecraft.getInstance();
            cameraBefore = mc.options.getCameraType();
            pitchBefore = mc.player.getXRot();
            mc.options.setCameraType(view == 2 ? CameraType.THIRD_PERSON_FRONT : view == 1 ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
            mc.player.setXRot(pitch);
        }

        @Override
        public void summonArmorStand(String name, double distance) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            armorStand = new ArmorStand(mc.level, 0, 0, 0);
            armorStand.setCustomName(Component.literal(name));
            armorStand.setCustomNameVisible(true);
            double yaw = Math.toRadians(mc.player.getYRot());
            armorStand.setPos(
                mc.player.getX() - distance * Math.sin(yaw),
                mc.player.getY(),
                mc.player.getZ() + distance * Math.cos(yaw)
            );
            mc.level.addEntity(armorStand);
        }

        @Override
        public void removeArmorStand() {
            if (armorStand != null) {
                armorStand.discard();
                armorStand = null;
            }
            Minecraft mc = Minecraft.getInstance();
            if (cameraBefore != null) mc.options.setCameraType(cameraBefore);
            if (mc.player != null) mc.player.setXRot(pitchBefore);
        }

        @Override
        public void openQuickplay() {
            Minecraft.getInstance().setScreenAndShow(new QuickplayScreen26(null));
        }

        @Override
        public void openHudEditor() {
            Minecraft.getInstance().setScreenAndShow(new DraggableHudScreen26(null));
        }

        @Override
        public void openOptifine(String page) {
            // OptiFine settings are 1.8.9 only; no-op on 26.3
        }

        @Override
        public void closeScreen() {
            Minecraft.getInstance().setScreenAndShow(null);
        }

        @Override
        public void setClouds(boolean on) {
            Minecraft.getInstance().options.cloudStatus().set(on ? CloudStatus.FANCY : CloudStatus.OFF);
        }

        @Override
        public void setPlayerYAndPitch(double y, float pitch) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.setPos(mc.player.getX(), y, mc.player.getZ());
                mc.player.setXRot(pitch);
            }
        }

        @Override
        public void showBossBar(String name) {
            // 1.8.9 only; no-op on 26.3
        }

        @Override
        public void clearBossBar() {
            // 1.8.9 only; no-op on 26.3
        }

        @Override
        public boolean is189() {
            return false;
        }

        @Override
        public int getWorldMaxHeight() {
            Minecraft mc = Minecraft.getInstance();
            return mc.level != null ? mc.level.getMaxY() : 320;
        }

        @Override
        public void done(int passed, int failed) {
            running = false;
            done = true;
        }
    };
}
