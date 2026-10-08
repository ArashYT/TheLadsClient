package com.thelads.core.v1_20_1.adapter;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.GameTimeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

public class VanillaGameBridge1201 implements LadsGameBridge {
    private final GameTimeText gameTimeText = new GameTimeText();
    private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {
        net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.LEGS,
        net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.HEAD
    };
    private Object armorPlayer;
    private int armorTick = Integer.MIN_VALUE;
    private List<ArmorPiece> armor = List.of();

    @Override
    public boolean isIngame() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null;
    }

    @Override
    public int getFps() {
        return Minecraft.getInstance().getFps();
    }

    @Override
    public boolean hasPlayer() {
        return Minecraft.getInstance().player != null;
    }

    @Override
    public int getPlayerX() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getBlockX() : 0;
    }

    @Override
    public int getPlayerY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getBlockY() : 0;
    }

    @Override
    public int getPlayerZ() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getBlockZ() : 0;
    }

    @Override
    public String getBiomeName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return "Unknown";
        BlockPos pos = mc.player.blockPosition();
        return mc.level.getBiome(pos).unwrapKey()
            .map(key -> key.location().getPath().replace('_', ' '))
            .orElse("Unknown");
    }

    @Override
    public String getBiomeId() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.player == null ? null
            : mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString()).orElse(null);
    }

    @Override
    public String getPlayerDirection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "North";
        return mc.player.getDirection().getName();
    }

    @Override
    public float getYaw() {
        var player = Minecraft.getInstance().player;
        if (player == null) return -1;
        float yaw = player.getYRot() % 360;
        return yaw < 0 ? yaw + 360 : yaw;
    }

    @Override
    public long getDayCount() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getDayTime() / 24000L : 0;
    }

    @Override
    public String getGameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? gameTimeText.get(mc.level.getDayTime()) : "00:00";
    }

    @Override
    public int getPing() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) {
                return info.getLatency();
            }
        }
        return 0;
    }

    @Override
    public float getHealth() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getHealth() : 20.0f;
    }

    @Override
    public float getMaxHealth() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getMaxHealth() : 20.0f;
    }

    @Override
    public float getAbsorption() {
        var player = Minecraft.getInstance().player;
        return player == null ? -1 : player.getAbsorptionAmount();
    }

    @Override
    public int getFoodLevel() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getFoodData().getFoodLevel() : 20;
    }

    @Override
    public float getSaturation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        var server = mc.getSingleplayerServer();
        if (server == null) return -1;
        var authoritativePlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
        return authoritativePlayer == null ? -1 : authoritativePlayer.getFoodData().getSaturationLevel();
    }

    @Override
    public List<ArmorPiece> getArmor() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            armorPlayer = null;
            armor = List.of();
            return armor;
        }
        if (armorPlayer == player && armorTick == player.tickCount) return armor;
        armorPlayer = player;
        armorTick = player.tickCount;
        List<ArmorPiece> result = new ArrayList<>(4);
        for (var slot : ARMOR_SLOTS) {
            var stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            int maximum = stack.isDamageableItem() ? stack.getMaxDamage() : 0;
            result.add(new ArmorPiece(stack.getHoverName().getString(),
                maximum <= 0 ? 0 : Math.max(0, maximum - stack.getDamageValue()), maximum));
        }
        armor = List.copyOf(result);
        return armor;
    }

    private net.minecraft.client.player.LocalPlayer potionPlayer;
    private int potionTick = Integer.MIN_VALUE;
    private List<String> potions = List.of();

    @Override
    public List<String> getActivePotionEffects() {
        var player = Minecraft.getInstance().player;
        if (player == null) { potionPlayer = null; potionTick = Integer.MIN_VALUE; return potions = List.of(); }
        if (player != potionPlayer || player.tickCount != potionTick) {
            potionPlayer = player; potionTick = player.tickCount;
            var snapshot = new ArrayList<String>();
            for (MobEffectInstance effect : player.getActiveEffects())
                snapshot.add(net.minecraft.network.chat.Component.translatable(effect.getEffect().getDescriptionId()).getString()
                    + " (" + (effect.getDuration() / 20) + "s)");
            potions = List.copyOf(snapshot);
        }
        return potions;
    }

    @Override
    public double getSpeed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            var delta = mc.player.getDeltaMovement();
            return Math.sqrt(delta.x * delta.x + delta.z * delta.z) * 20.0;
        }
        return 0.0;
    }

    @Override
    public int getXpLevel() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.experienceLevel : 0;
    }

    @Override
    public float getXpProgress() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.experienceProgress : 0.0f;
    }

    @Override
    public String getDimensionId() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.dimension().location().toString() : "";
    }

    @Override
    public String getServerAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSingleplayer()) return "Singleplayer";
        return mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Unknown";
    }

    @Override
    public boolean isKeyDown(String keyName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return false;
        switch (keyName.toUpperCase()) {
            case "W": return mc.options.keyUp.isDown();
            case "S": return mc.options.keyDown.isDown();
            case "A": return mc.options.keyLeft.isDown();
            case "D": return mc.options.keyRight.isDown();
            case "SPACE": return mc.options.keyJump.isDown();
            case "LMB": return mc.options.keyAttack.isDown();
            case "RMB": return mc.options.keyUse.isDown();
            default: return false;
        }
    }

    @Override
    public boolean isHudHidden() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options != null && mc.options.hideGui;
    }
}
