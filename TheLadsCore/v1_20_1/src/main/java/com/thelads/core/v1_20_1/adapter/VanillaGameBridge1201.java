package com.thelads.core.v1_20_1.adapter;

import com.thelads.core.client.GameTimeText;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.v1_20_1.mixin.BossBarAccessor1201;
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

    // ---- Potion effects ----
    private net.minecraft.client.player.LocalPlayer potionPlayer;
    private int potionTick = Integer.MIN_VALUE;
    private List<String> potions = List.of();
    private List<PotionEffectInfo> detailedPotions = List.of();

    private static String romanNumeral(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(value);
        };
    }

    @Override
    public List<PotionEffectInfo> getActivePotions() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            potionPlayer = null;
            potionTick = Integer.MIN_VALUE;
            potions = List.of();
            return detailedPotions = List.of();
        }
        if (player != potionPlayer || player.tickCount != potionTick) {
            potionPlayer = player;
            potionTick = player.tickCount;
            var snapshot = new ArrayList<String>();
            var detailed = new ArrayList<PotionEffectInfo>();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                String name = net.minecraft.network.chat.Component.translatable(effect.getEffect().getDescriptionId()).getString();
                if (effect.getAmplifier() > 0) name += " " + romanNumeral(effect.getAmplifier() + 1);
                int totalSec = effect.getDuration() / 20;
                String dur = effect.isInfiniteDuration() ? "**:**" : totalSec >= 60 ? String.format("%d:%02d", totalSec / 60, totalSec % 60) : totalSec + "s";
                var key = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
                detailed.add(new PotionEffectInfo(name, dur, 0, key == null ? "" : key.toString(), 0xFFFFFFFF));
                snapshot.add(name + " (" + (effect.isInfiniteDuration() ? "**:**" : totalSec + "s") + ")");
            }
            potions = List.copyOf(snapshot);
            detailedPotions = List.copyOf(detailed);
        }
        return detailedPotions;
    }

    @Override
    public List<String> getActivePotionEffects() {
        getActivePotions();
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

    @Override
    public boolean isDebugScreenOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options != null && mc.options.renderDebug;
    }

    // ---- Scoreboard (the sidebar the player would see, in vanilla's order) ----
    private Object scoreboardPlayer;
    private int scoreboardTick = Integer.MIN_VALUE;
    private ScoreboardSnapshot scoreboardSnapshot;

    @Override
    public ScoreboardSnapshot getScoreboard() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            scoreboardPlayer = null;
            scoreboardSnapshot = null;
            return null;
        }
        if (scoreboardPlayer == mc.player && scoreboardTick == mc.player.tickCount) return scoreboardSnapshot;
        scoreboardPlayer = mc.player;
        scoreboardTick = mc.player.tickCount;
        var board = mc.level.getScoreboard();
        var team = board.getPlayersTeam(mc.player.getScoreboardName());
        net.minecraft.world.scores.Objective objective = null;
        if (team != null) {
            int slot = team.getColor().getId();
            if (slot >= 0) objective = board.getDisplayObjective(3 + slot);
        }
        if (objective == null) objective = board.getDisplayObjective(1);
        if (objective == null) {
            scoreboardSnapshot = null;
            return null;
        }
        // Vanilla keeps the highest 15 scores of the sorted (ascending) list and draws the highest at the top.
        List<net.minecraft.world.scores.Score> scores = new ArrayList<>();
        for (var score : board.getPlayerScores(objective))
            if (score.getOwner() != null && !score.getOwner().startsWith("#")) scores.add(score);
        if (scores.size() > 15) scores = new ArrayList<>(scores.subList(scores.size() - 15, scores.size()));
        List<ScoreLine> lines = new ArrayList<>(scores.size());
        for (int i = scores.size() - 1; i >= 0; i--) {
            var score = scores.get(i);
            lines.add(new ScoreLine(legacy(net.minecraft.world.scores.PlayerTeam.formatNameForTeam(
                board.getPlayersTeam(score.getOwner()), net.minecraft.network.chat.Component.literal(score.getOwner()))),
                "§r§c" + score.getScore()));
        }
        scoreboardSnapshot = new ScoreboardSnapshot(legacy(objective.getDisplayName()), lines);
        return scoreboardSnapshot;
    }

    /** The text with its colours and styles as section-sign codes, which the Lads HUD draws. */
    private static String legacy(net.minecraft.network.chat.Component text) {
        StringBuilder out = new StringBuilder();
        text.visit((style, part) -> {
            if (part.isEmpty()) return java.util.Optional.empty();
            out.append("§r");
            if (style.getColor() != null) out.append('§').append(com.thelads.core.client.LegacyText.code(style.getColor().getValue()));
            if (style.isBold()) out.append("§l");
            if (style.isItalic()) out.append("§o");
            if (style.isUnderlined()) out.append("§n");
            if (style.isStrikethrough()) out.append("§m");
            if (style.isObfuscated()) out.append("§k");
            out.append(part);
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out.toString();
    }

    @Override
    public int bossBarCount() {
        var mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.getBossOverlay() == null) return 0;
        try {
            var map = ((BossBarAccessor1201) mc.gui.getBossOverlay()).ladsEvents();
            return map == null ? 0 : map.size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private java.util.Collection<net.minecraft.server.packs.repository.Pack> packSelection = List.of();
    private List<String> packNames = List.of();

    @Override
    public List<String> getActiveResourcePacks() {
        var repository = Minecraft.getInstance().getResourcePackRepository();
        if (repository == null) { packSelection = List.of(); return packNames = List.of(); }
        var selected = repository.getSelectedPacks();
        if (!packSelection.equals(selected)) {
            packSelection = List.copyOf(selected);
            packNames = selected.stream().map(net.minecraft.server.packs.repository.Pack::getId).toList();
        }
        return packNames;
    }

    @Override
    public String getItemCountText(int selection) { return com.thelads.core.v1_20_1.hud.chat.NativeClientTools1201.itemCount(selection); }

    @Override
    public String getRecentReachText() { return com.thelads.core.v1_20_1.hud.chat.NativeClientTools1201.reachText(); }

    @Override
    public boolean hasPaperDollRenderer() { return com.thelads.core.v1_20_1.hud.overlay.PaperDoll1201.active(); }

    @Override
    public boolean hasMinimap() { return com.thelads.core.v1_20_1.hud.Minimap1201.available(); }

    @Override
    public int[] minimapSize() { return com.thelads.core.v1_20_1.hud.Minimap1201.size(); }

    @Override
    public void positionMinimap(int x, int y) { com.thelads.core.v1_20_1.hud.Minimap1201.position(x, y); }

    @Override
    public VoiceChatState voiceChat() { return com.thelads.core.v1_20_1.hud.VoiceChat1201.state(); }

    @Override
    public void previewKillBannerSound(String skin, float volume) { com.thelads.core.v1_20_1.feature.NativeKillBanner1201.previewSound(skin, volume); }

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
