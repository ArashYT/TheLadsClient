package com.thelads.core.v26_2.feature;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
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

/** QA-only script steps for the tce1 feature pass: module, key, hold, look, lookat, bind, fakeplayer, state. */
final class QaTce1Ext26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-26.3");
    private static final int FAKE_ID = -7001;
    private static RemotePlayer fake;

    private QaTce1Ext26() {}

    /** True when the step was handled. */
    static boolean step(Minecraft mc, String[] s) {
        switch (s[0]) {
            case "module" -> {
                Module m = null;
                for (Module c : ModuleManager.getInstance().getModules()) if (c.getName().equalsIgnoreCase(s[1])) m = c;
                if (m == null) throw new IllegalArgumentException("no module '" + s[1] + "'");
                m.setEnabled("on".equalsIgnoreCase(s[2]));
                LOGGER.info("Lads qa module: {} enabled={}", m.getName(), m.isEnabled());
            }
            case "key" -> KeyMapping.click(keyOf(mc, s[1])); // a press through KeyMapping.click, as the keyboard handler does
            case "hold" -> {
                boolean down = !"off".equalsIgnoreCase(s.length > 2 ? s[2] : "on");
                KeyMapping k = switch (s[1].toLowerCase(Locale.ROOT)) {
                    case "use" -> mc.options.keyUse; case "attack" -> mc.options.keyAttack; case "forward" -> mc.options.keyUp;
                    case "back" -> mc.options.keyDown; case "left" -> mc.options.keyLeft; case "right" -> mc.options.keyRight;
                    case "sneak" -> mc.options.keyShift; case "jump" -> mc.options.keyJump; case "sprint" -> mc.options.keySprint;
                    default -> throw new IllegalArgumentException("hold: use attack forward back left right sneak jump sprint");
                };
                k.setDown(down);
                if (down && k == mc.options.keyUse) KeyMapping.click(mc.options.keyUse.getDefaultKey());
            }
            case "look" -> { mc.player.setYRot(Float.parseFloat(s[1])); mc.player.setXRot(Float.parseFloat(s[2])); }
            case "lookat" -> {
                if (fake == null) throw new IllegalStateException("no fake player");
                double dx = fake.getX() - mc.player.getX(), dz = fake.getZ() - mc.player.getZ();
                mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
                mc.player.setXRot(0);
            }
            case "bind" -> { // bind <part of the key binding's name> <key>
                KeyMapping k = null;
                for (KeyMapping c : mc.options.keyMappings) if (c.getName().toLowerCase(Locale.ROOT).contains(s[1].toLowerCase(Locale.ROOT))) { k = c; break; }
                if (k == null) throw new IllegalArgumentException("bind: no key binding named like '" + s[1] + "'");
                k.setKey(keyOf(mc, s[2]));
                KeyMapping.resetMapping();
                LOGGER.info("Lads qa bind: {} -> {}", s[1], k.saveString());
            }
            case "fakeplayer" -> {
                if (fake != null) { mc.level.removeEntity(FAKE_ID, Entity.RemovalReason.DISCARDED); fake = null; }
                if (s.length > 1 && !"off".equalsIgnoreCase(s[1])) {
                    fake = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "QaFar"));
                    fake.setId(FAKE_ID);
                    float yaw = mc.player.getYRot() + 180;
                    fake.snapTo(mc.player.getX() + Double.parseDouble(s[1]), mc.player.getY(), mc.player.getZ() + Double.parseDouble(s[2]), yaw, 0);
                    fake.setYHeadRot(yaw); fake.setYBodyRot(yaw);
                    fake.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                    fake.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    fake.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    mc.level.addEntity(fake);
                    LOGGER.info("Lads qa fakeplayer: at {} {} {} ({} blocks)", fake.getX(), fake.getY(), fake.getZ(), Math.sqrt(fake.distanceToSqr(mc.player)));
                }
            }
            case "mapzero" -> { // mapzero <id>: the left half of that map made unexplored (client copy)
                var d = mc.level.getMapData(new net.minecraft.world.level.saveddata.maps.MapId(Integer.parseInt(s[1])));
                for (int i = 0; i < d.colors.length; i++) if (i % 128 < 64) d.colors[i] = 0;
                LOGGER.info("Lads qa mapzero: map {} left half cleared", s[1]);
            }
            case "safety" -> LOGGER.info("Lads qa safety: {} on {} -> allowed={}", s[1], s[2], com.thelads.core.client.HypixelSafetyManager.isAllowedOn(s[1], s[2]));
            case "fakemove" -> { // fakemove <dx> <dz>: the fake player to that offset from the local player, same facing
                if (fake == null) throw new IllegalStateException("no fake player");
                fake.snapTo(mc.player.getX() + Double.parseDouble(s[1]), mc.player.getY(), mc.player.getZ() + Double.parseDouble(s[2]), fake.getYRot(), 0);
                LOGGER.info("Lads qa fakemove: {} blocks", Math.sqrt(fake.distanceToSqr(mc.player)));
            }
            case "state" -> {
                int snow = 0;
                for (Entity e : mc.level.entitiesForRendering()) if (e.getClass().getSimpleName().contains("Snowball") || e.getClass().getSimpleName().contains("EnderPearl")) snow++;
                LOGGER.info("Lads qa state: selected={} main={}x{} off={}x{} using={} hand={} thrown={} pos={},{},{}", mc.player.getInventory().getSelectedSlot(),
                    mc.player.getMainHandItem().getItem(), mc.player.getMainHandItem().getCount(), mc.player.getOffhandItem().getItem(), mc.player.getOffhandItem().getCount(),
                    mc.player.isUsingItem(), mc.player.getUsedItemHand(), snow, (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
            }
            default -> { return false; }
        }
        return true;
    }

    /** One character is a keyboard key (1 to 9, g), otherwise a name: use, attack. */
    private static InputConstants.Key keyOf(Minecraft mc, String n) {
        if (n.equalsIgnoreCase("use")) return mc.options.keyUse.getDefaultKey();
        if (n.equalsIgnoreCase("attack")) return mc.options.keyAttack.getDefaultKey();
        if (n.length() == 1 && n.charAt(0) >= '1' && n.charAt(0) <= '9') return mc.options.keyHotbarSlots[n.charAt(0) - '1'].getDefaultKey();
        if (n.length() == 1) return InputConstants.Type.KEYBOARD.getOrCreate(Character.toUpperCase(n.charAt(0)));
        throw new IllegalArgumentException("key: one character, use or attack");
    }
}
