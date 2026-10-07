package com.thelads.core.v1_8_9.adapter;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.mods.LoadedMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;

/** Game state for the HUD modules, read from 1.8.9's client; values 1.8.9 cannot provide keep the interface defaults. */
public class VanillaGameBridge189 implements LadsGameBridge {
    private static Minecraft mc() { return Minecraft.getMinecraft(); }
    /** HUD texts rebuilt only when their numbers change, and potion names translated once (I18n.format runs String.format). */
    private long reachCents = -1, clockTicks = -1;
    private String reachText, clockText;
    private final java.util.Map<String, String> effectNames = new java.util.HashMap<>();

    private String effectLanguage;

    private String effectName(String key) {
        String language = mc().gameSettings.language;
        if (!java.util.Objects.equals(language, effectLanguage)) { effectLanguage = language; effectNames.clear(); }
        String name = effectNames.get(key);
        if (name == null) effectNames.put(key, name = I18n.format(key));
        return name;
    }

    @Override
    public boolean isIngame() {
        return mc().theWorld != null && mc().thePlayer != null;
    }

    @Override
    public int getFps() {
        return Minecraft.getDebugFPS();
    }

    @Override
    public boolean hasPlayer() {
        return mc().thePlayer != null;
    }

    @Override
    public int getPlayerX() {
        return mc().thePlayer != null ? MathHelper.floor_double(mc().thePlayer.posX) : 0;
    }

    @Override
    public int getPlayerY() {
        return mc().thePlayer != null ? MathHelper.floor_double(mc().thePlayer.posY) : 0;
    }

    @Override
    public int getPlayerZ() {
        return mc().thePlayer != null ? MathHelper.floor_double(mc().thePlayer.posZ) : 0;
    }

    /** 1.8.9 biomes have display names ("Extreme Hills"), no namespaced ids, so getBiomeId keeps its null default. */
    @Override
    public String getBiomeName() {
        Minecraft mc = mc();
        return isIngame() ? mc.theWorld.getBiomeGenForCoords(new BlockPos(mc.thePlayer)).biomeName : "Plains";
    }

    @Override
    public String getDimensionId() {
        if (mc().theWorld == null) return "";
        switch (mc().theWorld.provider.getDimensionId()) {
            case 0: return "minecraft:overworld";
            case -1: return "minecraft:the_nether";
            case 1: return "minecraft:the_end";
            default: return "";
        }
    }

    @Override
    public void previewKillBannerSound(String skin, float volume) {
        com.thelads.core.v1_8_9.feature.KillBanner189.previewSound(skin, volume);
    }

    @Override
    public String getServerAddress() {
        Minecraft mc = mc();
        return mc.isSingleplayer() || mc.getCurrentServerData() == null ? "Singleplayer" : mc.getCurrentServerData().serverIP;
    }

    @Override
    public String getItemCountText(int selection) {
        Minecraft mc = mc();
        if (mc.thePlayer == null) return "Items: 0";
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return "Items: 0";
        int total = 0;
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
            if (stack != null && stack.getItem() == held.getItem() && (!held.getHasSubtypes() || stack.getItemDamage() == held.getItemDamage())) {
                total += stack.stackSize;
            }
        }
        return held.getDisplayName() + ": " + total;
    }

    @Override
    public String getRecentReachText() {
        Minecraft mc = mc();
        if (mc.thePlayer == null || mc.objectMouseOver == null) return "Reach: --";
        if (mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY
            && mc.objectMouseOver.entityHit != null && mc.objectMouseOver.hitVec != null) {
            double dist = mc.thePlayer.getPositionEyes(1.0f).distanceTo(mc.objectMouseOver.hitVec);
            long cents = Math.round(dist * 100);
            if (cents != reachCents || reachText == null) { // formatted when the 2 decimals change, not every frame
                reachCents = cents;
                reachText = "Reach: " + cents / 100 + (cents % 100 < 10 ? ".0" : ".") + cents % 100 + "m";
            }
            return reachText;
        }
        return "Reach: --";
    }

    @Override
    public boolean hasPaperDollRenderer() {
        return true;
    }

    @Override
    public int bossBarCount() {
        return net.minecraft.entity.boss.BossStatus.bossName != null && net.minecraft.entity.boss.BossStatus.statusBarTime > 0 ? 1 : 0;
    }

    @Override
    public String getPlayerDirection() {
        return mc().thePlayer != null ? mc().thePlayer.getHorizontalFacing().getName() : "North";
    }

    @Override
    public float getYaw() {
        if (mc().thePlayer == null) return -1;
        float yaw = mc().thePlayer.rotationYaw % 360;
        return yaw < 0 ? yaw + 360 : yaw;
    }

    @Override
    public long getDayCount() {
        return mc().theWorld != null ? mc().theWorld.getWorldTime() / 24000L : 0;
    }

    @Override
    public String getGameTime() {
        if (mc().theWorld == null) return "12:00";
        long time = (mc().theWorld.getWorldTime() + 6000L) % 24000L;
        if (time != clockTicks || clockText == null) {
            clockTicks = time;
            long hour = time / 1000L, minute = (time % 1000L) * 60L / 1000L;
            clockText = (hour < 10 ? "0" : "") + hour + (minute < 10 ? ":0" : ":") + minute;
        }
        return clockText;
    }

    @Override
    public float getHealth() {
        return mc().thePlayer != null ? mc().thePlayer.getHealth() : 20.0f;
    }

    @Override
    public float getMaxHealth() {
        return mc().thePlayer != null ? mc().thePlayer.getMaxHealth() : 20.0f;
    }

    @Override
    public float getAbsorption() {
        return mc().thePlayer != null ? mc().thePlayer.getAbsorptionAmount() : -1;
    }

    @Override
    public int getFoodLevel() {
        return mc().thePlayer != null ? mc().thePlayer.getFoodStats().getFoodLevel() : 20;
    }

    /** Exact only from the integrated server, as on the other versions; multiplayer saturation is not sent to the client. */
    @Override
    public float getSaturation() {
        Minecraft mc = mc();
        if (mc.thePlayer == null || mc.getIntegratedServer() == null) return -1;
        EntityPlayerMP player = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(mc.thePlayer.getUniqueID());
        return player == null ? -1 : player.getFoodStats().getSaturationLevel();
    }

    @Override
    public int getXpLevel() {
        return mc().thePlayer != null ? mc().thePlayer.experienceLevel : 0;
    }

    @Override
    public float getXpProgress() {
        return mc().thePlayer != null ? mc().thePlayer.experience : 0.0f;
    }

    @Override
    public int getPing() {
        Minecraft mc = mc();
        if (mc.getNetHandler() == null || mc.thePlayer == null) return 0;
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return info != null ? info.getResponseTime() : 0;
    }

    @Override
    public double getSpeed() {
        if (mc().thePlayer == null) return 0.0;
        double x = mc().thePlayer.motionX, z = mc().thePlayer.motionZ;
        return Math.sqrt(x * x + z * z) * 20.0;
    }

    @Override
    public boolean isKeyDown(String keyName) {
        Minecraft mc = mc();
        if (mc.gameSettings == null) return false;
        if (keyName.equalsIgnoreCase("W")) return mc.gameSettings.keyBindForward.isKeyDown();
        if (keyName.equalsIgnoreCase("S")) return mc.gameSettings.keyBindBack.isKeyDown();
        if (keyName.equalsIgnoreCase("A")) return mc.gameSettings.keyBindLeft.isKeyDown();
        if (keyName.equalsIgnoreCase("D")) return mc.gameSettings.keyBindRight.isKeyDown();
        if (keyName.equalsIgnoreCase("SPACE")) return mc.gameSettings.keyBindJump.isKeyDown();
        if (keyName.equalsIgnoreCase("LMB")) return mc.gameSettings.keyBindAttack.isKeyDown();
        if (keyName.equalsIgnoreCase("RMB")) return mc.gameSettings.keyBindUseItem.isKeyDown();
        return false;
    }

    private static String romanNumeral(int value) {
        switch (value) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return String.valueOf(value);
        }
    }

    @Override
    public List<PotionEffectInfo> getActivePotions() {
        if (mc().thePlayer == null) return Collections.emptyList();
        List<PotionEffectInfo> detailed = new ArrayList<>();
        for (PotionEffect effect : mc().thePlayer.getActivePotionEffects()) {
            net.minecraft.potion.Potion potion = effect.getPotionID() < net.minecraft.potion.Potion.potionTypes.length
                ? net.minecraft.potion.Potion.potionTypes[effect.getPotionID()] : null;
            String name = effectName(effect.getEffectName());
            if (effect.getAmplifier() > 0) {
                name += " " + romanNumeral(effect.getAmplifier() + 1);
            }
            int totalSec = effect.getDuration() / 20;
            String dur = totalSec >= 60 ? String.format("%d:%02d", totalSec / 60, totalSec % 60) : totalSec + "s";
            int iconIndex = potion != null && potion.hasStatusIcon() ? potion.getStatusIconIndex() : -1;
            String effectId = potion != null ? potion.getName().replace("potion.", "") : "";
            int color = potion != null ? potion.getLiquidColor() : 0xFFFFFFFF;
            detailed.add(new PotionEffectInfo(name, dur, iconIndex, effectId, color));
        }
        return detailed;
    }

    /** Localized effect names ("Speed (30s)"), as on the other versions. */
    @Override
    public List<String> getActivePotionEffects() {
        if (mc().thePlayer == null) return Collections.emptyList();
        List<String> effects = new ArrayList<>();
        for (PotionEffect effect : mc().thePlayer.getActivePotionEffects())
            effects.add(effectName(effect.getEffectName()) + " (" + (effect.getDuration() / 20) + "s)");
        return effects;
    }

    @Override
    public List<String> getActiveResourcePacks() {
        List<String> packs = new ArrayList<>();
        for (ResourcePackRepository.Entry entry : mc().getResourcePackRepository().getRepositoryEntries())
            packs.add(entry.getResourcePackName());
        return packs;
    }

    @Override
    public boolean isHudHidden() {
        return mc().gameSettings != null && mc().gameSettings.hideGUI;
    }

    /** Worn armor, feet first (1.8.9's armorInventory order matches the other versions' slot order). */
    @Override
    public List<ArmorPiece> getArmor() {
        if (mc().thePlayer == null) return Collections.emptyList();
        List<ArmorPiece> armor = new ArrayList<>(4);
        for (ItemStack stack : mc().thePlayer.inventory.armorInventory) {
            if (stack == null) continue;
            int maximum = stack.isItemStackDamageable() ? stack.getMaxDamage() : 0;
            armor.add(new ArmorPiece(stack.getDisplayName(), maximum <= 0 ? 0 : Math.max(0, maximum - stack.getItemDamage()), maximum));
        }
        return armor;
    }

    private Object scoreboardPlayer;
    private int scoreboardTick;
    private ScoreboardSnapshot scoreboardSnapshot;

    /**
     * The sidebar the player sees (team-colour slot first), top line first, as GuiIngame draws it (15 highest, "#" hidden). Built
     * once per tick (it is read several times a frame); an unchanged sidebar keeps its snapshot, so the HUD keeps its measurements.
     */
    @Override
    public ScoreboardSnapshot getScoreboard() {
        Minecraft mc = mc();
        if (!isIngame()) {
            scoreboardPlayer = null;
            scoreboardSnapshot = null;
            return null;
        }
        if (scoreboardPlayer == mc.thePlayer && scoreboardTick == mc.thePlayer.ticksExisted) return scoreboardSnapshot;
        scoreboardPlayer = mc.thePlayer;
        scoreboardTick = mc.thePlayer.ticksExisted;
        ScoreboardSnapshot next = buildScoreboard(mc);
        if (next == null || !next.equals(scoreboardSnapshot)) scoreboardSnapshot = next;
        return scoreboardSnapshot;
    }

    private static ScoreboardSnapshot buildScoreboard(Minecraft mc) {
        Scoreboard board = mc.theWorld.getScoreboard();
        ScorePlayerTeam team = board.getPlayersTeam(mc.thePlayer.getName());
        ScoreObjective objective = null;
        if (team != null && team.getChatFormat().getColorIndex() >= 0)
            objective = board.getObjectiveInDisplaySlot(3 + team.getChatFormat().getColorIndex());
        if (objective == null) objective = board.getObjectiveInDisplaySlot(1);
        if (objective == null) return null;
        List<Score> scores = new ArrayList<>();
        for (Score score : board.getSortedScores(objective))
            if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) scores.add(score);
        List<ScoreLine> lines = new ArrayList<>();
        for (int i = scores.size() - 1; i >= Math.max(0, scores.size() - 15); i--) {
            Score score = scores.get(i);
            lines.add(new ScoreLine(ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(score.getPlayerName()), score.getPlayerName()),
                String.valueOf(score.getScorePoints())));
        }
        return new ScoreboardSnapshot(objective.getDisplayName(), lines);
    }

    /** FML's mod containers plus Minecraft itself, so the catalog and the Installed mods view name this game's versions. */
    @Override
    public List<LoadedMod> loadedMods() {
        List<LoadedMod> mods = new ArrayList<>();
        mods.add(new LoadedMod("minecraft", "Minecraft", Loader.MC_VERSION, null, Collections.<String>emptyList(), "",
            Collections.<String>emptyList(), Collections.<String>emptyList(), false, "builtin"));
        for (ModContainer container : Loader.instance().getActiveModList()) {
            List<String> depends = new ArrayList<>();
            for (ArtifactVersion requirement : container.getRequirements()) depends.add(requirement.getLabel());
            List<String> authors = container.getMetadata() != null ? container.getMetadata().authorList : Collections.<String>emptyList();
            mods.add(new LoadedMod(container.getModId(), container.getName(), container.getVersion(), null, authors, "",
                depends, Collections.<String>emptyList(), false, "forge"));
        }
        return mods;
    }
}
