package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (LADS_QA_ANIM1910=1): 1.9.10's 1.7 Animations in the QA world. A crossbow held, charged and loaded is drawn by the 1.7
 * recipes (OldAnimations.hand, Feature.BOW), and Hold to swing keeps the arm swinging while the attack key is held at the air.
 * Photographed to screenshots/anim1910/*.png.
 */
final class Anim1910Capture26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static int tick = -1, restarts, passed, failed;
    private static float lastSwing;
    private static String pendingShot;
    private static boolean done, chargedBow;

    private Anim1910Capture26() {}

    static boolean busy() { return tick >= 0 && !done; }

    static void tick(boolean ready) {
        if (done || System.getenv("LADS_QA_ANIM1910") == null || (tick < 0 && !ready)) return;
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) return;
        var id = mc.player.getUUID();
        switch (++tick) {
            case 0 -> { com.thelads.core.client.qa.QaProgress.begin("anim 1.9.10", 4); server.execute(() -> {
                var p = server.getPlayerList().getPlayer(id);
                p.getInventory().clearContent(); // the QA world's earlier probes fill it: no room for arrows
                p.removeEffect(net.minecraft.world.effect.MobEffects.SPEED); // left by HudOverrideCapture26 runs before its cleanup
                p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                p.getInventory().add(new ItemStack(Items.ARROW, 16));
            }); }
            case 20 -> { NativeOldAnimations.APPLIED.clear(); mc.options.keyUse.setDown(true); } // vanilla releases a use once the key is up
            case 35 -> {
                chargedBow = mc.player.isUsingItem() && NativeOldAnimations.APPLIED.contains(Feature.BOW);
                check(chargedBow, "a charging crossbow is drawn as 1.7 draws a bow (using " + mc.player.isUsingItem() + ", "
                    + NativeOldAnimations.APPLIED + ")");
                pendingShot = "crossbow-charging";
            }
            case 60 -> mc.options.keyUse.setDown(false);
            case 70 -> { NativeOldAnimations.APPLIED.clear(); }
            case 75 -> {
                check(!NativeOldAnimations.APPLIED.contains(Feature.BOW), "a loaded crossbow keeps vanilla's aimed hold (1.9.11; "
                    + NativeOldAnimations.APPLIED + ")");
                pendingShot = "crossbow-loaded";
                mc.player.setXRot(-60); // at the sky: nothing to mine or hit
                mc.options.keyAttack.setDown(true);
                lastSwing = mc.player.getSwingAnimation(0);
            }
            case 115 -> {
                mc.options.keyAttack.setDown(false);
                check(restarts >= 5, "Hold to swing: the arm swung again and again while attack was held at the air (" + restarts + " restarts in 2 s)");
                pendingShot = "after-hold";
            }
            // Vanilla blocking (26.x): a server-made sword with blocks_attacks blocks itself; 1.7 draws its sword block and blockhit.
            case 120 -> server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "item replace entity @p weapon.mainhand with minecraft:diamond_sword[minecraft:blocks_attacks={}]"));
            case 130 -> { NativeOldAnimations.APPLIED.clear(); mc.options.keyUse.setDown(true); }
            case 135 -> mc.options.keyAttack.setDown(true);
            case 150 -> {
                check(mc.player.isUsingItem() && NativeOldAnimations.APPLIED.contains(Feature.BLOCK_POSE)
                    && NativeOldAnimations.APPLIED.contains(Feature.BLOCKHIT), "a sword the server lets block (blocks_attacks) gets the 1.7 "
                    + "sword block and blockhit (using " + mc.player.isUsingItem() + ", " + NativeOldAnimations.APPLIED + ")");
                pendingShot = "sword-vanilla-blockhit";
            }
            case 155 -> { mc.options.keyAttack.setDown(false); mc.options.keyUse.setDown(false); }
            case 160 -> {
                mc.player.setXRot(0); // the QA world keeps its player: later captures (UiCapture's Fullbright preview) must not start on the sky
                mc.player.xRotO = 0;
                done = true;
                com.thelads.core.client.qa.QaProgress.done();
                LOGGER.info("Lads 1.7 animations 1.9.10 capture END: {} passed, {} failed", passed, failed);
            }
            default -> {}
        }
        if (tick > 75 && tick < 115) {
            float swing = mc.player.getSwingAnimation(0);
            if (swing < lastSwing) restarts++;
            lastSwing = swing;
            if (tick == 95) pendingShot = "holding-attack";
        }
    }

    private static void check(boolean ok, String what) {
        com.thelads.core.client.qa.QaProgress.check(what, ok);
        if (ok) passed++; else failed++;
        LOGGER.info("Lads 1.7 animations 1.9.10 capture {}: {}", ok ? "PASS" : "FAIL", what);
    }

    /** Each completed frame (NativeWorldVerification.renderedFrame): a requested screenshot. */
    static void frame(RenderTarget target, Path game) {
        String name = pendingShot;
        if (name == null) return;
        pendingShot = null;
        Path output = game.resolve("screenshots").resolve("anim1910").resolve(name + ".png");
        net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
            try (image) {
                Files.createDirectories(output.getParent());
                image.writeToFile(output);
            } catch (Exception failure) {
                LOGGER.error("Lads 1.7 animations 1.9.10 capture {} failed", name, failure);
            }
        });
    }
}
