package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.hud.ArmorHudElement;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.qa.F1914Qa;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.gui.DraggableHudScreen189;
import com.thelads.core.v1_8_9.gui.GuiOptiFineSettings189;
import com.thelads.core.v1_8_9.gui.QuickplayScreen189;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1.8.9 driver for the 1.9.14 feature-capture probe (LADS_VERIFY_189_ONLY=f1914).
 * Executes inside the auto-world verification harness, photographs and validates all 1.9.14 features.
 */
final class Probe1914FeatureCapture189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final F1914Qa QA = new F1914Qa();
    private static ItemStack[] armorBefore;
    private static int cameraBefore;
    private static float pitchBefore;
    private static String activeBossName;
    private static IBossDisplayData activeBoss;

    private Probe1914FeatureCapture189() {}

    static boolean run(Minecraft mc) {
        if (activeBoss != null) {
            BossStatus.setBossStatus(activeBoss, false);
            BossStatus.bossName = activeBossName;
            BossStatus.healthScale = 0.8f;
            BossStatus.statusBarTime = 100_000;
        }
        return QA.tick(DRIVER);
    }

    private static final F1914Qa.Driver DRIVER = new F1914Qa.Driver() {
        @Override
        public void screenshot(String name) {
            Minecraft mc = Minecraft.getMinecraft();
            CoreProbe.screenshot(mc, name);
            // Also ensure copy exists in standard screenshots folder
            try {
                BufferedImage shot = CoreProbe.readShot(mc, name);
                File mainDir = new File(mc.mcDataDir, "screenshots");
                mainDir.mkdirs();
                ImageIO.write(shot, "png", new File(mainDir, name + ".png"));
            } catch (Exception ignored) {}
        }

        @Override
        public void cropArmorHud(String sourceShot, String destShot) {
            Minecraft mc = Minecraft.getMinecraft();
            try {
                BufferedImage full = CoreProbe.readShot(mc, sourceShot);
                HudElement armor = HudManager.getInstance().get("ArmorHUD");
                if (armor == null) return;

                ScaledResolution sr = new ScaledResolution(mc);
                int scale = sr.getScaleFactor();
                int pad = 4 * scale;

                int guiX = armor.getX(), guiY = armor.getY(), guiW = armor.getWidth(), guiH = armor.getHeight();
                HudGroupLayout.Rect bounds = HudManager.getInstance().lastBounds("ArmorHUD");
                if (bounds != null) {
                    guiX = bounds.x();
                    guiY = bounds.y();
                    guiW = bounds.width();
                    guiH = bounds.height();
                } else if (armor instanceof ArmorHudElement) {
                    GuiLadsAdapter g = new GuiLadsAdapter(mc.fontRendererObj, sr.getScaledWidth(), sr.getScaledHeight());
                    guiX = armor.getDisplayX(g);
                    guiY = armor.getDisplayY(g);
                }

                int px = Math.max(0, guiX * scale - pad);
                int py = Math.max(0, guiY * scale - pad);
                int pw = Math.min(full.getWidth() - px, guiW * scale + 2 * pad);
                int ph = Math.min(full.getHeight() - py, guiH * scale + 2 * pad);

                if (pw > 0 && ph > 0) {
                    BufferedImage cropped = full.getSubimage(px, py, pw, ph);
                    File folder1 = new File(mc.mcDataDir, "lads-qa/screenshots");
                    folder1.mkdirs();
                    ImageIO.write(cropped, "png", new File(folder1, destShot + ".png"));

                    File folder2 = new File(mc.mcDataDir, "screenshots");
                    folder2.mkdirs();
                    ImageIO.write(cropped, "png", new File(folder2, destShot + ".png"));
                }
            } catch (Exception e) {
                LOG.error("Failed to crop armor HUD for {}", destShot, e);
            }
        }

        @Override
        public void setPlayerDiamondArmor() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                armorBefore = mc.thePlayer.inventory.armorInventory.clone();
                mc.thePlayer.inventory.armorInventory[0] = new ItemStack(Items.diamond_boots);
                mc.thePlayer.inventory.armorInventory[1] = new ItemStack(Items.diamond_leggings);
                mc.thePlayer.inventory.armorInventory[2] = new ItemStack(Items.diamond_chestplate);
                mc.thePlayer.inventory.armorInventory[3] = new ItemStack(Items.diamond_helmet);
            }
        }

        @Override
        public void restorePlayerArmor() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null && armorBefore != null) {
                for (int i = 0; i < armorBefore.length; i++) {
                    mc.thePlayer.inventory.armorInventory[i] = armorBefore[i];
                }
            }
        }

        @Override
        public void setCamera(int view, float pitch) {
            Minecraft mc = Minecraft.getMinecraft();
            cameraBefore = mc.gameSettings.thirdPersonView;
            pitchBefore = mc.thePlayer.rotationPitch;
            mc.gameSettings.thirdPersonView = view;
            mc.thePlayer.rotationPitch = pitch;
            mc.thePlayer.prevRotationPitch = pitch;
        }

        @Override
        public void summonArmorStand(String name, double distance) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null) return;
            EntityArmorStand stand = new EntityArmorStand(mc.theWorld);
            stand.setCustomNameTag(name);
            stand.setAlwaysRenderNameTag(true);
            double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
            stand.setPosition(
                mc.thePlayer.posX - distance * Math.sin(yaw),
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - 1.0,
                mc.thePlayer.posZ + distance * Math.cos(yaw)
            );
            mc.theWorld.addEntityToWorld(-1914, stand);
        }

        @Override
        public void removeArmorStand() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != null) {
                mc.theWorld.removeEntityFromWorld(-1914);
            }
            mc.gameSettings.thirdPersonView = cameraBefore;
            if (mc.thePlayer != null) {
                mc.thePlayer.rotationPitch = pitchBefore;
                mc.thePlayer.prevRotationPitch = pitchBefore;
            }
        }

        @Override
        public void openQuickplay() {
            Minecraft.getMinecraft().displayGuiScreen(new QuickplayScreen189(null));
        }

        @Override
        public void openHudEditor() {
            Minecraft.getMinecraft().displayGuiScreen(new DraggableHudScreen189(null));
        }

        @Override
        public void openOptifine(String page) {
            GuiOptiFineSettings189.SubPage subPage = GuiOptiFineSettings189.SubPage.valueOf(page.toUpperCase(Locale.ROOT));
            Minecraft.getMinecraft().displayGuiScreen(new GuiOptiFineSettings189(null, subPage));
        }

        @Override
        public void closeScreen() {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }

        @Override
        public void setClouds(boolean on) {
            Minecraft.getMinecraft().gameSettings.clouds = on ? 2 : 0;
        }

        @Override
        public void setPlayerYAndPitch(double y, float pitch) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                mc.thePlayer.setPosition(mc.thePlayer.posX, y, mc.thePlayer.posZ);
                mc.thePlayer.rotationPitch = pitch;
                mc.thePlayer.prevRotationPitch = pitch;
            }
        }

        @Override
        public void showBossBar(String name) {
            IBossDisplayData fakeBoss = new IBossDisplayData() {
                @Override public float getMaxHealth() { return 100.0f; }
                @Override public float getHealth() { return 80.0f; }
                @Override public IChatComponent getDisplayName() { return new ChatComponentText(name); }
            };
            activeBoss = fakeBoss;
            activeBossName = name;
            BossStatus.setBossStatus(fakeBoss, false);
            BossStatus.bossName = name;
            BossStatus.healthScale = 0.8f;
            BossStatus.statusBarTime = 100_000;
        }

        @Override
        public void clearBossBar() {
            activeBoss = null;
            activeBossName = null;
            BossStatus.statusBarTime = 0;
            BossStatus.bossName = null;
        }

        @Override
        public boolean is189() {
            return true;
        }

        @Override
        public int getWorldMaxHeight() {
            return 256;
        }

        @Override
        public void done(int passed, int failed) {
            CoreProbe.check(failed == 0, "1.9.14 feature capture probe: " + passed + " passed, " + failed + " failed");
        }
    };
}
