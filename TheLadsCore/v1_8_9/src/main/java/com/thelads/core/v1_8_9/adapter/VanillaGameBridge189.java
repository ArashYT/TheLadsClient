package com.thelads.core.v1_8_9.adapter;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.mods.LoadedMod;
import java.util.ArrayList;
import java.util.Collection;
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
    public int getRenderedChunks() {
        Minecraft mc = mc();
        if (mc.renderGlobal == null) return 0;
        // F3's own "C: rendered/total" line: vanilla's RenderGlobal and Ionium (which replaces the chunk lists) both answer it.
        // It builds a String, so it is read at most twice a second.
        long now = System.nanoTime();
        if (now - renderedChunksAt > 500_000_000L) {
            renderedChunksAt = now;
            renderedChunks = parseRenderedChunks(mc.renderGlobal.getDebugInfoRenders());
        }
        return renderedChunks;
    }

    private int renderedChunks;
    private long renderedChunksAt;

    /** The first number after "C: " in F3's chunk line ("C: 123/456 ..."), 0 when there is none. */
    static int parseRenderedChunks(String line) {
        int at = line == null ? -1 : line.indexOf("C: ");
        if (at < 0) return 0;
        int n = 0;
        for (int i = at + 3; i < line.length() && Character.isDigit(line.charAt(i)); i++) n = n * 10 + (line.charAt(i) - '0');
        return n;
    }

    @Override
    public boolean hasPlayer() {
        return mc().thePlayer != null;
    }

    @Override
    public String getPlayerName() {
        return mc().thePlayer != null ? mc().thePlayer.getName() : "";
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
    public float getMasterVolume() {
        return mc().gameSettings.getSoundLevel(net.minecraft.client.audio.SoundCategory.MASTER);
    }

    /** This frame's partial tick, as Forge hands it to the HUD (NativeHud). */
    public static float partialTicks;

    @Override
    public float partialTick() {
        return partialTicks;
    }

    /** Combo Sounds' built-in sound, in both ears (ListenerSound189); uploads go through CustomSounds, and the bridge's default file dialog (AWT) serves 1.8.9. */
    @Override
    public void playComboSound(final float tone, final float level) {
        mc().getSoundHandler().playSound(new com.thelads.core.v1_8_9.feature.ListenerSound189(new net.minecraft.util.ResourceLocation("note.pling"), level, tone));
    }

    @Override
    public void playBowSound(final boolean headshot, final float level) {
        mc().getSoundHandler().playSound(new net.minecraft.client.audio.PositionedSound(new net.minecraft.util.ResourceLocation(headshot ? "random.anvil_land" : "random.levelup")) {
            {
                volume = headshot ? level * .5F : level;
                pitch = headshot ? 1.8F : 1.0F;
                attenuationType = net.minecraft.client.audio.ISound.AttenuationType.NONE;
            }
        });
    }

    @Override
    public Session session() {
        net.minecraft.util.Session session = mc().getSession();
        return session == null ? null : new Session(net.minecraft.entity.player.EntityPlayer.getUUID(session.getProfile()), session.getUsername(), session.getToken());
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
        net.minecraft.item.Item targetItem = null;
        String label = "Items";
        switch (selection) {
            case 1:
                targetItem = net.minecraft.init.Items.arrow;
                label = "Arrows";
                break;
            case 2:
                return "Totems: 0";
            case 3:
                targetItem = net.minecraft.init.Items.fireworks;
                label = "Rockets";
                break;
            case 4:
                targetItem = net.minecraft.init.Items.golden_apple;
                label = "Golden Apples";
                break;
            case 5:
                targetItem = net.minecraft.init.Items.ender_pearl;
                label = "Ender Pearls";
                break;
            case 6:
                targetItem = net.minecraft.init.Items.potionitem;
                label = "Potions";
                break;
            case 7:
                targetItem = net.minecraft.init.Items.mushroom_stew;
                label = "Soups";
                break;
            default:
                ItemStack held = mc.thePlayer.getHeldItem();
                if (held != null) {
                    targetItem = held.getItem();
                    label = held.getDisplayName();
                }
                break;
        }
        if (targetItem == null) return label + ": 0";
        int total = 0;
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
            if (stack != null && stack.getItem() == targetItem) {
                total += stack.stackSize;
            }
        }
        return label + ": " + total;
    }

    private int lastInventoryScanTick = -1;
    private int cachedPotionCount = 0;
    private int cachedSoupCount = 0;
    private boolean lastAttackKeyDown = false;

    private void updateInventoryCountsIfNeeded() {
        Minecraft mc = mc();
        if (mc.thePlayer == null) {
            cachedPotionCount = 0;
            cachedSoupCount = 0;
            return;
        }
        int curTick = mc.thePlayer.ticksExisted;
        if (curTick == lastInventoryScanTick) return;
        lastInventoryScanTick = curTick;
        int pots = 0, soups = 0;
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
            if (stack != null) {
                if (stack.getItem() == net.minecraft.init.Items.potionitem) pots += stack.stackSize;
                else if (stack.getItem() == net.minecraft.init.Items.mushroom_stew) soups += stack.stackSize;
            }
        }
        cachedPotionCount = pots;
        cachedSoupCount = soups;
    }

    @Override
    public int getPotionCount() {
        updateInventoryCountsIfNeeded();
        return cachedPotionCount;
    }

    @Override
    public int getSoupCount() {
        updateInventoryCountsIfNeeded();
        return cachedSoupCount;
    }

    @Override
    public boolean hasItemCooldowns() {
        return false;
    }

    @Override
    public List<CooldownEntry> getActiveCooldowns() {
        // Minecraft 1.8.9 has no item cooldowns in vanilla netcode (ItemCooldowns was added in 1.9)
        return Collections.emptyList();
    }

    @Override
    public void tickClient() {
        Minecraft mc = mc();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        int currentTick = mc.thePlayer.ticksExisted;

        // 1. Air swing detection (attack key pressed with no entity under crosshair)
        if (mc.gameSettings != null) {
            boolean attackDown = mc.gameSettings.keyBindAttack.isKeyDown();
            if (attackDown && !lastAttackKeyDown && mc.currentScreen == null) {
                if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY) {
                    com.thelads.core.client.CombatTracker.get().onAirSwing();
                }
            }
            lastAttackKeyDown = attackDown;

            // 2. Block counter placed detection (decrease within 2 ticks of use on block)
            boolean useDown = mc.gameSettings.keyBindUseItem.isKeyDown();
            boolean hitBlock = mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK;
            if (useDown && hitBlock && mc.currentScreen == null) {
                com.thelads.core.client.BlockTracker.get().onUseKeyOnBlock(currentTick);
            }
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        boolean isBlock = held != null && held.getItem() instanceof net.minecraft.item.ItemBlock;
        int heldCount = held != null ? held.stackSize : 0;
        int slot = mc.thePlayer.inventory.currentItem;
        com.thelads.core.client.BlockTracker.get().checkHeldBlockTick(currentTick, slot, isBlock, heldCount);

        // 3. Golden apple eating detection
        boolean using = mc.thePlayer.isUsingItem();
        boolean isGapple = held != null && held.getItem() == net.minecraft.init.Items.golden_apple;
        int gappleCount = isGapple ? held.stackSize : 0;
        ItemStack useItem = mc.thePlayer.getItemInUse();
        boolean isUsingGapple = using && useItem != null && useItem.getItem() == net.minecraft.init.Items.golden_apple;
        int totalDuration = isUsingGapple ? useItem.getMaxItemUseDuration() : 32;
        int ticksUsed = isUsingGapple ? mc.thePlayer.getItemInUseDuration() : 0;
        com.thelads.core.client.GappleTracker.get().onTick(isUsingGapple, isGapple, slot, gappleCount, ticksUsed, totalDuration);

        // 4. Item tracker inventory diffing (every 10 client ticks)
        if (currentTick % 10 == 0) {
            boolean containerOpen = mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiInventory);
            com.thelads.core.client.InventoryDiffTracker diff = com.thelads.core.client.InventoryDiffTracker.get();
            diff.beginScan(containerOpen);
            if (!containerOpen) {
                for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
                    if (stack != null && stack.getItem() != null) {
                        diff.addScannedItem(net.minecraft.item.Item.getIdFromItem(stack.getItem()), stack.stackSize, stack.getDisplayName());
                    }
                }
            }
            diff.endScan();
        }
    }

    @Override
    public DefenseEnchants getDefenseEnchants() {
        Minecraft mc = mc();
        if (mc.thePlayer == null) return new DefenseEnchants(0, 0, 0, 0, 0);
        int prot = 0, fire = 0, blast = 0, proj = 0, ff = 0;
        for (ItemStack stack : mc.thePlayer.inventory.armorInventory) {
            if (stack != null) {
                prot += net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.enchantment.Enchantment.protection.effectId, stack);
                fire += net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.enchantment.Enchantment.fireProtection.effectId, stack);
                blast += net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.enchantment.Enchantment.blastProtection.effectId, stack);
                proj += net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.enchantment.Enchantment.projectileProtection.effectId, stack);
                ff += net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.enchantment.Enchantment.featherFalling.effectId, stack);
            }
        }
        return new DefenseEnchants(prot, fire, blast, proj, ff);
    }

    @Override
    public int getArmorValue() {
        Minecraft mc = mc();
        return mc.thePlayer != null ? mc.thePlayer.getTotalArmorValue() : 0;
    }

    @Override
    public boolean isHardcore() {
        Minecraft mc = mc();
        return mc.theWorld != null && mc.theWorld.getWorldInfo().isHardcoreModeEnabled();
    }

    @Override
    public boolean isMainInventoryEmpty() {
        Minecraft mc = mc();
        if (mc.thePlayer == null) return true;
        net.minecraft.item.ItemStack[] slots = mc.thePlayer.inventory.mainInventory;
        for (int i = 9; i < slots.length; i++) if (slots[i] != null) return false;
        return true;
    }

    @Override
    public int getHeldItemBlockCount() {
        Minecraft mc = mc();
        if (mc.thePlayer == null) return -1;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof net.minecraft.item.ItemBlock)) return -1;
        int total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() == held.getItem() && (!held.getHasSubtypes() || stack.getItemDamage() == held.getItemDamage())) {
                total += stack.stackSize;
            }
        }
        return total;
    }

    @Override
    public int getHotbarBlockCount() {
        Minecraft mc = mc();
        if (mc.thePlayer == null) return -1;
        int total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof net.minecraft.item.ItemBlock) {
                total += stack.stackSize;
            }
        }
        return total;
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
    public float getPitch() {
        if (mc().thePlayer == null) return 0;
        return mc().thePlayer.rotationPitch;
    }

    @Override
    public int getClientTicks() {
        Minecraft mc = mc();
        if (mc.thePlayer != null) return mc.thePlayer.ticksExisted;
        return (int) (System.currentTimeMillis() / 50);
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
    public int[] getListedLatencies() {
        Minecraft mc = mc();
        if (mc.getNetHandler() == null) return new int[0];
        java.util.Collection<NetworkPlayerInfo> players = mc.getNetHandler().getPlayerInfoMap();
        int[] latencies = new int[players.size()];
        int i = 0;
        for (NetworkPlayerInfo info : players) if (i < latencies.length) latencies[i++] = info.getResponseTime();
        return latencies;
    }

    @Override
    public java.net.InetSocketAddress getServerSocketAddress() {
        Minecraft mc = mc();
        java.net.SocketAddress address = mc.getNetHandler() == null ? null : mc.getNetHandler().getNetworkManager().getRemoteAddress();
        return address instanceof java.net.InetSocketAddress ? (java.net.InetSocketAddress) address : null;
    }

    @Override
    public void sendChatLine(String text) {
        if (mc().thePlayer != null) mc().thePlayer.sendChatMessage(text);
    }

    @Override
    public void displayChatMessage(String text) {
        if (mc().isCallingFromMinecraftThread()) {
            if (mc().thePlayer != null) mc().thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(text));
        } else {
            mc().addScheduledTask(() -> {
                if (mc().thePlayer != null) mc().thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(text));
            });
        }
    }

    public List<String> getOnlinePlayerNames() {
        if (mc().getNetHandler() == null) return Collections.emptyList();
        Collection<net.minecraft.client.network.NetworkPlayerInfo> playerInfoMap = mc().getNetHandler().getPlayerInfoMap();
        if (playerInfoMap == null || playerInfoMap.isEmpty()) return Collections.emptyList();
        List<String> names = new ArrayList<>(playerInfoMap.size());
        for (net.minecraft.client.network.NetworkPlayerInfo info : playerInfoMap) {
            if (info != null && info.getGameProfile() != null && info.getGameProfile().getName() != null) {
                names.add(info.getGameProfile().getName());
            }
        }
        return names;
    }

    @Override
    public void showToast(String title, String message) {
        if (mc().isCallingFromMinecraftThread()) {
            if (mc().thePlayer != null) {
                mc().thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("§c[" + title + "] §f" + message));
            }
        } else {
            mc().addScheduledTask(() -> {
                if (mc().thePlayer != null) {
                    mc().thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("§c[" + title + "] §f" + message));
                }
            });
        }
    }

    @Override
    public boolean sendPluginMessage(String channel, byte[] data) {
        return com.thelads.core.v1_8_9.feature.Hypixel189.send(channel, data);
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
        if (keyName.equalsIgnoreCase("SNK") || keyName.equalsIgnoreCase("SNEAK") || keyName.equalsIgnoreCase("SHIFT")) return mc.gameSettings.keyBindSneak.isKeyDown();
        if (keyName.equalsIgnoreCase("SPR") || keyName.equalsIgnoreCase("SPRINT")) return mc.gameSettings.keyBindSprint.isKeyDown();
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

    private long lastPotionHash = Long.MIN_VALUE;
    private Object lastPotionPlayer = null;
    private List<PotionEffectInfo> cachedDetailedPotions = Collections.emptyList();
    private List<String> cachedPotionEffects = Collections.emptyList();

    private static String formatPotionDuration(int totalSec) {
        if (totalSec < 60) return totalSec + "s";
        int m = totalSec / 60;
        int s = totalSec % 60;
        return s < 10 ? (m + ":0" + s) : (m + ":" + s);
    }

    private long computePotionSignature(Collection<PotionEffect> effects) {
        if (effects == null || effects.isEmpty()) return 0L;
        long h = 1L;
        for (PotionEffect effect : effects) {
            h = 31L * h + effect.getPotionID();
            h = 31L * h + effect.getAmplifier();
            h = 31L * h + (effect.getDuration() / 20);
        }
        return h;
    }

    @Override
    public List<PotionEffectInfo> getActivePotions() {
        if (mc().thePlayer == null) return Collections.emptyList();
        Collection<PotionEffect> effects = mc().thePlayer.getActivePotionEffects();
        long sig = computePotionSignature(effects);
        if (mc().thePlayer == lastPotionPlayer && sig == lastPotionHash) {
            return cachedDetailedPotions;
        }
        updatePotionCaches(effects, sig);
        return cachedDetailedPotions;
    }

    /** Localized effect names ("Speed (30s)"), as on the other versions. */
    @Override
    public List<String> getActivePotionEffects() {
        if (mc().thePlayer == null) return Collections.emptyList();
        Collection<PotionEffect> effects = mc().thePlayer.getActivePotionEffects();
        long sig = computePotionSignature(effects);
        if (mc().thePlayer == lastPotionPlayer && sig == lastPotionHash) {
            return cachedPotionEffects;
        }
        updatePotionCaches(effects, sig);
        return cachedPotionEffects;
    }

    private void updatePotionCaches(Collection<PotionEffect> effects, long sig) {
        if (effects == null || effects.isEmpty()) {
            cachedDetailedPotions = Collections.emptyList();
            cachedPotionEffects = Collections.emptyList();
        } else {
            List<PotionEffectInfo> detailed = new ArrayList<>(effects.size());
            List<String> legacy = new ArrayList<>(effects.size());
            for (PotionEffect effect : effects) {
                net.minecraft.potion.Potion potion = effect.getPotionID() < net.minecraft.potion.Potion.potionTypes.length
                    ? net.minecraft.potion.Potion.potionTypes[effect.getPotionID()] : null;
                String name = effectName(effect.getEffectName());
                if (effect.getAmplifier() > 0) {
                    name += " " + romanNumeral(effect.getAmplifier() + 1);
                }
                int totalSec = effect.getDuration() / 20;
                String dur = formatPotionDuration(totalSec);
                int iconIndex = potion != null && potion.hasStatusIcon() ? potion.getStatusIconIndex() : -1;
                String effectId = potion != null ? potion.getName().replace("potion.", "") : "";
                int color = potion != null ? potion.getLiquidColor() : 0xFFFFFFFF;
                detailed.add(new PotionEffectInfo(name, dur, iconIndex, effectId, color));
                legacy.add(effectName(effect.getEffectName()) + " (" + totalSec + "s)");
            }
            cachedDetailedPotions = Collections.unmodifiableList(detailed);
            cachedPotionEffects = Collections.unmodifiableList(legacy);
        }
        lastPotionHash = sig;
        lastPotionPlayer = mc().thePlayer;
    }

    private List<ResourcePackRepository.Entry> cachedPackEntries = Collections.emptyList();
    private List<String> cachedPackNames = Collections.emptyList();

    /** The player's packs, highest priority first (the top of the pack screen; the repository lists the lowest first). */
    @Override
    public List<String> getActiveResourcePacks() {
        List<ResourcePackRepository.Entry> entries = mc().getResourcePackRepository().getRepositoryEntries();
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        if (entries.equals(cachedPackEntries)) {
            return cachedPackNames;
        }
        List<String> packs = new ArrayList<>(entries.size());
        for (int i = entries.size() - 1; i >= 0; i--) {
            packs.add(entries.get(i).getResourcePackName());
        }
        cachedPackEntries = new ArrayList<>(entries);
        cachedPackNames = Collections.unmodifiableList(packs);
        return cachedPackNames;
    }

    @Override
    public boolean isHudHidden() {
        return mc().gameSettings != null && mc().gameSettings.hideGUI;
    }

    @Override
    public boolean isDebugScreenOpen() {
        return mc().gameSettings != null && mc().gameSettings.showDebugInfo;
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

    @Override
    public void disconnectWithReason(String title, String message) {
        Minecraft mc = mc();
        mc.addScheduledTask(() -> {
            boolean integrated = mc.isIntegratedServerRunning();
            if (mc.theWorld != null) {
                mc.theWorld.sendQuittingDisconnectingPacket();
            }
            mc.loadWorld(null);
            net.minecraft.client.gui.GuiScreen parent = integrated
                ? new net.minecraft.client.gui.GuiMainMenu()
                : new net.minecraft.client.gui.GuiMultiplayer(new net.minecraft.client.gui.GuiMainMenu());
            mc.displayGuiScreen(new net.minecraft.client.gui.GuiDisconnected(parent, "disconnect.lost", new net.minecraft.util.ChatComponentText(message)));
        });
    }
}
