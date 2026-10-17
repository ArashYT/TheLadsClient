package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.bridge.LadsGameBridge;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (LADS_QA_HUD1910=1): 1.9.10's HUD defaults in the QA world. Armor, a shield and Speed, then the offhand emptied; each
 * photographed (screenshots/hud1910/*.png): the Armor HUD beside the offhand slot, then in its place; Potion Effects without a plate.
 */
final class HudOverrideCapture26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static int tick = -1;
    private static String pendingShot;
    private static boolean done;
    private HudOverrideCapture26() {}

    static boolean busy() { return tick >= 0 && !done; }

    static void tick(boolean ready) {
        if (done || System.getenv("LADS_QA_HUD1910") == null || (tick < 0 && !ready)) return;
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) return;
        var id = mc.player.getUUID();
        switch (++tick) {
            case 0 -> server.execute(() -> {
                var p = server.getPlayerList().getPlayer(id);
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 2400, 1));
            });
            case 40 -> shot("armor-with-offhand");
            case 45 -> server.execute(() -> server.getPlayerList().getPlayer(id).setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY));
            case 80 -> shot("armor-no-offhand");
            case 85 -> { done = true; LOGGER.info("Lads hud 1.9.10 capture END"); }
            default -> {}
        }
    }

    private static void shot(String name) {
        LOGGER.info("Lads hud 1.9.10 capture {}: offhand slot left {}, armor {}", name, LadsGameBridge.get().offhandSlotLeft(), LadsGameBridge.get().getArmor().size());
        pendingShot = name;
    }

    /** Each completed frame (NativeWorldVerification.renderedFrame): a requested screenshot. */
    static void frame(RenderTarget target, Path game) {
        String name = pendingShot;
        if (name == null) return;
        pendingShot = null;
        Path output = game.resolve("screenshots").resolve("hud1910").resolve(name + ".png");
        net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
            try (image) {
                Files.createDirectories(output.getParent());
                image.writeToFile(output);
            } catch (Exception failure) {
                LOGGER.error("Lads hud 1.9.10 capture {} failed", name, failure);
            }
        });
    }
}
