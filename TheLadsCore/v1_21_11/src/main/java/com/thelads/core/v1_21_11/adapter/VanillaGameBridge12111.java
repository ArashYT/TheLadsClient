package com.thelads.core.v1_21_11.adapter;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.v1_21_11.feature.GameTimeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

public class VanillaGameBridge12111 implements LadsGameBridge {
    private final GameTimeText gameTimeText = new GameTimeText();
    private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {
        net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.LEGS,
        net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.HEAD
    };
    private Object armorPlayer;
    private int armorTick = Integer.MIN_VALUE;
    private List<ArmorPiece> armor = List.of();
    private Object scoreboardPlayer;
    private int scoreboardTick = Integer.MIN_VALUE;
    private ScoreboardSnapshot scoreboardSnapshot;
    private static final java.util.Comparator<net.minecraft.world.scores.PlayerScoreEntry> SCORE_ORDER =
        java.util.Comparator.comparingInt(net.minecraft.world.scores.PlayerScoreEntry::value).reversed()
            .thenComparing(net.minecraft.world.scores.PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

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
            var slot = net.minecraft.world.scores.DisplaySlot.teamColorToSlot(team.getColor());
            if (slot != null) objective = board.getDisplayObjective(slot);
        }
        if (objective == null) objective = board.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        if (objective == null) {
            scoreboardSnapshot = null;
            return null;
        }
        var format = objective.numberFormatOrDefault(net.minecraft.network.chat.numbers.StyledFormat.SIDEBAR_DEFAULT);
        List<ScoreLine> lines = board.listPlayerScores(objective).stream()
            .filter(score -> !score.isHidden()).sorted(SCORE_ORDER).limit(15)
            .map(score -> new ScoreLine(net.minecraft.world.scores.PlayerTeam.formatNameForTeam(
                board.getPlayersTeam(score.owner()), score.ownerName()).getString(),
                score.formatValue(format).getString())).toList();
        // Keep blank/custom number-format display text; never substitute the raw integer score.
        scoreboardSnapshot = new ScoreboardSnapshot(objective.getDisplayName().getString(), lines);
        return scoreboardSnapshot;
    }

    @Override
    public String getBiomeId() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.player == null ? null
            : mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                .map(key -> key.identifier().toString()).orElse(null);
    }

    @Override
    public float getYaw() {
        var player = Minecraft.getInstance().player;
        if (player == null) return -1;
        float yaw = player.getYRot() % 360;
        return yaw < 0 ? yaw + 360 : yaw;
    }

    @Override
    public float getAbsorption() {
        var player = Minecraft.getInstance().player;
        return player == null ? -1 : player.getAbsorptionAmount();
    }

    @Override
    public float getSaturation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        // Vanilla health packets do not keep saturation exact between hunger/health changes.
        // The integrated server owns exact values; no invented multiplayer value.
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
                return biomeHolder.unwrapKey().map(k -> k.identifier().getPath()).orElse("Plains");
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
        return gameTimeText.get(mc.level.getDayTime());
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
}
