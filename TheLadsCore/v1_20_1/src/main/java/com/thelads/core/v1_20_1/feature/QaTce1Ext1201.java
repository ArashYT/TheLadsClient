package com.thelads.core.v1_20_1.feature;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** QA-only script steps for the tce1 feature pass: lookat, bind, fakeplayer, state. */
final class QaTce1Ext1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");
    private static final int FAKE_ID = -7001;
    private static RemotePlayer fake;

    private QaTce1Ext1201() {}

    static void step(Minecraft mc, String[] s) {
        switch (s[0]) {
            case "lookat" -> {
                if (fake == null) throw new IllegalStateException("no fake player");
                double dx = fake.getX() - mc.player.getX(), dz = fake.getZ() - mc.player.getZ();
                mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
                mc.player.setXRot(0);
            }
            case "bind" -> { // bind <part of the key binding's name> <one character>
                KeyMapping k = null;
                for (KeyMapping c : mc.options.keyMappings) if (c.getName().toLowerCase(Locale.ROOT).contains(s[1].toLowerCase(Locale.ROOT))) { k = c; break; }
                if (k == null) throw new IllegalArgumentException("bind: no key binding named like '" + s[1] + "'");
                k.setKey(InputConstants.Type.KEYSYM.getOrCreate(Character.toUpperCase(s[2].charAt(0))));
                KeyMapping.resetMapping();
                LOGGER.info("Lads qa bind: {} -> {}", s[1], k.saveString());
            }
            case "fakeplayer" -> {
                if (fake != null) { mc.level.removeEntity(FAKE_ID, Entity.RemovalReason.DISCARDED); fake = null; }
                if (s.length > 1 && !"off".equalsIgnoreCase(s[1])) {
                    fake = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "QaFar"));
                    float yaw = mc.player.getYRot() + 180;
                    fake.moveTo(mc.player.getX() + Double.parseDouble(s[1]), mc.player.getY(), mc.player.getZ() + Double.parseDouble(s[2]), yaw, 0);
                    fake.setYHeadRot(yaw); fake.setYBodyRot(yaw);
                    fake.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                    fake.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    fake.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    mc.level.addPlayer(FAKE_ID, fake);
                    LOGGER.info("Lads qa fakeplayer: at {} {} {} ({} blocks)", fake.getX(), fake.getY(), fake.getZ(), Math.sqrt(fake.distanceToSqr(mc.player)));
                }
            }
            case "mapzero" -> { // mapzero <id>: the left half of that map made unexplored (client copy)
                var d = net.minecraft.world.item.MapItem.getSavedData(Integer.parseInt(s[1]), mc.level);
                for (int i = 0; i < d.colors.length; i++) if (i % 128 < 64) d.colors[i] = 0;
                LOGGER.info("Lads qa mapzero: map {} left half cleared", s[1]);
            }
            case "safety" -> LOGGER.info("Lads qa safety: {} on {} -> allowed={}", s[1], s[2], com.thelads.core.client.HypixelSafetyManager.isAllowedOn(s[1], s[2]));
            case "fakemove" -> { // fakemove <dx> <dz>: the fake player to that offset from the local player, same facing
                if (fake == null) throw new IllegalStateException("no fake player");
                fake.moveTo(mc.player.getX() + Double.parseDouble(s[1]), mc.player.getY(), mc.player.getZ() + Double.parseDouble(s[2]), fake.getYRot(), 0);
                LOGGER.info("Lads qa fakemove: {} blocks", Math.sqrt(fake.distanceToSqr(mc.player)));
            }
            case "state" -> {
                int thrown = 0;
                for (Entity e : mc.level.entitiesForRendering()) if (e.getClass().getSimpleName().contains("Snowball") || e.getClass().getSimpleName().contains("EnderPearl")) thrown++;
                LOGGER.info("Lads qa state: selected={} main={}x{} off={}x{} using={} hand={} thrown={} pos={},{},{}", mc.player.getInventory().selected,
                    mc.player.getMainHandItem().getItem(), mc.player.getMainHandItem().getCount(), mc.player.getOffhandItem().getItem(), mc.player.getOffhandItem().getCount(),
                    mc.player.isUsingItem(), mc.player.getUsedItemHand(), thrown, (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
            }
            default -> throw new IllegalArgumentException("unknown ext step " + s[0]);
        }
    }
}
