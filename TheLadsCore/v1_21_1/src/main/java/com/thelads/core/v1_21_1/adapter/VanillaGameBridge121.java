package com.thelads.core.v1_21_1.adapter;

import com.thelads.core.client.bridge.LadsGameBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

public class VanillaGameBridge121 implements LadsGameBridge {
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
        if (mc.level != null && mc.player != null) {
            BlockPos pos = mc.player.blockPosition();
            var biomeHolder = mc.level.getBiome(pos);
            if (biomeHolder.isBound()) {
                return biomeHolder.unwrapKey().map(k -> k.location().getPath()).orElse("Plains");
            }
        }
        return "Plains";
    }

    @Override
    public String getPlayerDirection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return mc.player.getDirection().getName();
        }
        return "North";
    }

    @Override
    public long getDayCount() {
        Minecraft mc = Minecraft.getInstance();
        return (mc.level != null) ? (mc.level.getDayTime() / 24000L) : 0;
    }

    @Override
    public String getGameTime() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "12:00";
        long time = (mc.level.getDayTime() + 6000L) % 24000L;
        long hours = time / 1000L;
        long minutes = (time % 1000L) * 60L / 1000L;
        return String.format("%02d:%02d", hours, minutes);
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
    public int getFoodLevel() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getFoodData().getFoodLevel() : 20;
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
    public double getSpeed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            var delta = mc.player.getDeltaMovement();
            return Math.sqrt(delta.x * delta.x + delta.z * delta.z) * 20.0;
        }
        return 0.0;
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
    public List<String> getActivePotionEffects() {
        List<String> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            for (MobEffectInstance eff : mc.player.getActiveEffects()) {
                list.add(eff.getEffect().value().getDescriptionId() + " (" + (eff.getDuration() / 20) + "s)");
            }
        }
        return list;
    }

    @Override
    public List<String> getActiveResourcePacks() {
        List<String> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getResourcePackRepository() != null) {
            for (var pack : mc.getResourcePackRepository().getSelectedPacks()) {
                list.add(pack.getId());
            }
        }
        return list;
    }

    @Override
    public boolean isHudHidden() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options != null && mc.options.hideGui;
    }

    @Override
    public List<com.thelads.core.mods.LoadedMod> loadedMods() {
        List<com.thelads.core.mods.LoadedMod> mods = new ArrayList<>();
        for (net.fabricmc.loader.api.ModContainer container : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
            var meta = container.getMetadata();
            List<String> depends = new ArrayList<>();
            for (var dependency : meta.getDependencies())
                if (dependency.getKind() == net.fabricmc.loader.api.metadata.ModDependency.Kind.DEPENDS) depends.add(dependency.getModId());
            List<String> authors = new ArrayList<>();
            for (var person : meta.getAuthors()) authors.add(person.getName());
            // The containing mod, never ModOrigin.getParentModId(): that throws for builtin mods such as minecraft and java.
            String parent = container.getContainingMod().map(containing -> containing.getMetadata().getId()).orElse(null);
            mods.add(new com.thelads.core.mods.LoadedMod(meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(), parent,
                authors, String.join(", ", meta.getLicense()), depends, List.copyOf(meta.getProvides()), libraryBadge(meta), meta.getType()));
        }
        return mods;
    }

    /** Mod Menu's custom "badges" may hold anything; only a string "library" entry in an array counts. */
    private static boolean libraryBadge(net.fabricmc.loader.api.metadata.ModMetadata meta) {
        var modmenu = meta.getCustomValue("modmenu");
        if (modmenu == null || modmenu.getType() != net.fabricmc.loader.api.metadata.CustomValue.CvType.OBJECT) return false;
        var badges = modmenu.getAsObject().get("badges");
        if (badges == null || badges.getType() != net.fabricmc.loader.api.metadata.CustomValue.CvType.ARRAY) return false;
        for (var badge : badges.getAsArray())
            if (badge.getType() == net.fabricmc.loader.api.metadata.CustomValue.CvType.STRING && "library".equals(badge.getAsString())) return true;
        return false;
    }
}
