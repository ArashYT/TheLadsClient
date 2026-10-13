package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.Probe172HudFlicker.command;

import com.mojang.authlib.GameProfile;
import com.thelads.core.v1_8_9.mixin.NetworkPlayerInfoAccessor;
import com.thelads.core.v1_8_9.world.mixin.NetworkPlayerInfoCapeAccessor;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Items;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1.9.2 many-players benchmark scenes (R6-BENCH section 2):
 * N = 20, 60, or 150 fake EntityOtherPlayerMP placed in direct sight (concentric semicircular arcs)
 * facing the pinned camera (0.5, 4.0, 0.5):
 * - 16 deterministic synthetic 64x64 skins (outer 3D layers filled with alpha 255 for 3D Skin Layers), half slim
 * - Equipment: 40% Protection IV diamond, 30% iron, 15% dyed leather + gold helmet, 15% chainmail
 * - Held items: 35% Sharpness V diamond sword, 25% Power V bow, 15% fishing rod, 15% golden apple, 10% diamond pickaxe
 * - 50% capes (4 synthetic 64x32 cape textures)
 * - Scoreboard teams ([MVP+], [VIP+], [RED], [BLUE]) and belowName health (20 HP)
 * - Invisible armor stand holograms ("CLICK TO PLAY") hovering at y + 2.3 for every 4th entity
 * - Deterministic sinusoidal movement interpolation (setPositionAndRotation2)
 * Completely offline, zero network requests.
 */
public final class Probe192Players {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final String[] TEAMS = {"TeamMVP", "TeamVIP", "TeamRed", "TeamBlue"};
    private static final String[] PREFIXES = {"\u00a7b[MVP\u00a7c+\u00a7b] ", "\u00a7a[VIP\u00a76+\u00a7a] ", "\u00a7c[RED] ", "\u00a79[BLUE] "};
    private static final ResourceLocation[] SKINS = new ResourceLocation[16];
    private static final ResourceLocation[] CAPES = new ResourceLocation[4];
    private static boolean texturesInitialized = false;

    private static final List<UUID> spawnedUuids = new ArrayList<UUID>();
    private static double[] baseX;
    private static double[] baseZ;
    private static float[] baseYaw;
    private static int activeCount = 0;

    private Probe192Players() {}

    public static boolean isPlayerScene(String name) {
        return name != null && name.startsWith("players");
    }

    public static int countFor(String name) {
        if (!isPlayerScene(name)) return 20;
        String num = name.substring("players".length());
        int underscore = num.indexOf('_');
        if (underscore >= 0) num = num.substring(0, underscore);
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return 20;
        }
    }

    private static void ensureTextures(Minecraft mc) {
        if (texturesInitialized) return;
        texturesInitialized = true;

        for (int s = 0; s < 16; s++) {
            BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            int baseR = (50 + s * 13) % 256;
            int baseG = (100 + s * 23) % 256;
            int baseB = (150 + s * 37) % 256;
            int baseColor = 0xFF000000 | (baseR << 16) | (baseG << 8) | baseB;

            int outerR = (baseR + 90) % 256;
            int outerG = (baseG + 50) % 256;
            int outerB = (baseB + 110) % 256;
            int outerColor = 0xFF000000 | (outerR << 16) | (outerG << 8) | outerB;

            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    boolean isOuter = (x >= 32 && y < 16)
                        || (x >= 16 && x < 40 && y >= 32 && y < 48)
                        || (x >= 40 && x < 56 && y >= 32 && y < 48)
                        || (x >= 48 && x < 64 && y >= 48 && y < 64)
                        || (x < 16 && y >= 32 && y < 48)
                        || (x < 16 && y >= 48 && y < 64);

                    int c = isOuter ? outerColor : baseColor;
                    if ((x + y) % 2 == 0) {
                        c = 0xFF000000 | (Math.min(255, ((c >> 16) & 0xFF) + 16) << 16)
                                       | (Math.min(255, ((c >> 8) & 0xFF) + 16) << 8)
                                       | Math.min(255, (c & 0xFF) + 16);
                    }
                    img.setRGB(x, y, c);
                }
            }
            SKINS[s] = new ResourceLocation("theladscore", "lads_perf/skin_" + s);
            mc.getTextureManager().loadTexture(SKINS[s], new DynamicTexture(img));
        }

        for (int c = 0; c < 4; c++) {
            BufferedImage capeImg = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
            int capeR = (c * 70 + 40) % 256;
            int capeG = (c * 50 + 90) % 256;
            int capeB = (c * 90 + 130) % 256;
            int capeColor = 0xFF000000 | (capeR << 16) | (capeG << 8) | capeB;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 64; x++) {
                    int col = ((x / 4 + y / 4) % 2 == 0) ? capeColor : (capeColor ^ 0x00252525);
                    capeImg.setRGB(x, y, col);
                }
            }
            CAPES[c] = new ResourceLocation("theladscore", "lads_perf/cape_" + c);
            mc.getTextureManager().loadTexture(CAPES[c], new DynamicTexture(capeImg));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, NetworkPlayerInfo> getPlayerInfoMap(Minecraft mc) {
        try {
            Field f = ReflectionHelper.findField(NetHandlerPlayClient.class, "playerInfoMap", "field_147310_i");
            f.setAccessible(true);
            return (Map<UUID, NetworkPlayerInfo>) f.get(mc.getNetHandler());
        } catch (Throwable t) {
            LOG.warn("Failed to get playerInfoMap", t);
            return null;
        }
    }

    public static void build(Minecraft mc, String sceneName) {
        ensureTextures(mc);
        int count = countFor(sceneName);
        activeCount = count;
        spawnedUuids.clear();
        baseX = new double[count];
        baseZ = new double[count];
        baseYaw = new float[count];

        Scoreboard sb = mc.theWorld != null ? mc.theWorld.getScoreboard() : null;
        if (sb != null) {
            for (int t = 0; t < 4; t++) {
                ScorePlayerTeam team = sb.getTeam(TEAMS[t]);
                if (team == null) team = sb.createTeam(TEAMS[t]);
                team.setNamePrefix(PREFIXES[t]);
            }
            ScoreObjective healthObj = sb.getObjective("lads_health");
            if (healthObj == null) healthObj = sb.addScoreObjective("lads_health", IScoreObjectiveCriteria.DUMMY);
            healthObj.setDisplayName("\u00a7c\u2764");
            sb.setObjectiveInDisplaySlot(2, healthObj);
        }

        Map<UUID, NetworkPlayerInfo> tabMap = getPlayerInfoMap(mc);

        int rows = count <= 20 ? 2 : count <= 60 ? 4 : 6;
        int perRow = (count + rows - 1) / rows;

        for (int i = 0; i < count; i++) {
            int row = i % rows;
            int col = i / rows;
            double r = 5.0 + row * 2.5;
            double theta = perRow <= 1 ? 0.0 : -0.55 + (1.10 * col) / (perRow - 1);
            double px = 0.5 + r * Math.sin(theta);
            double pz = 0.5 + r * Math.cos(theta);
            double py = 4.0;
            float yaw = 180.0f - (float) Math.toDegrees(theta);

            baseX[i] = px;
            baseZ[i] = pz;
            baseYaw[i] = yaw;

            boolean slim = (i % 2 != 0);
            long lsb = ((long) i & ~1L) | (slim ? 1L : 0L);
            UUID uuid = new UUID(0x192000L, lsb);
            spawnedUuids.add(uuid);

            String name = "Player_" + (i + 1);
            GameProfile profile = new GameProfile(uuid, name);
            EntityOtherPlayerMP player = new EntityOtherPlayerMP(mc.theWorld, profile);

            NetworkPlayerInfo info = new NetworkPlayerInfo(profile);
            ((NetworkPlayerInfoAccessor) info).ladsSetSkin(SKINS[i % 16]);
            ((NetworkPlayerInfoAccessor) info).ladsSetSkinType(slim ? "slim" : "default");
            ((NetworkPlayerInfoAccessor) info).ladsSetTexturesLoaded(true);
            if (i % 2 == 0) {
                ((NetworkPlayerInfoCapeAccessor) info).ladsSetCape(CAPES[(i / 2) % 4]);
            }
            if (tabMap != null) tabMap.put(uuid, info);

            int armorTier = i % 20;
            ItemStack boots, legs, chest, helm;
            if (armorTier < 8) { // 40% Protection IV diamond
                boots = new ItemStack(Items.diamond_boots);
                boots.addEnchantment(Enchantment.protection, 4);
                legs = new ItemStack(Items.diamond_leggings);
                legs.addEnchantment(Enchantment.protection, 4);
                chest = new ItemStack(Items.diamond_chestplate);
                chest.addEnchantment(Enchantment.protection, 4);
                helm = new ItemStack(Items.diamond_helmet);
                helm.addEnchantment(Enchantment.protection, 4);
            } else if (armorTier < 14) { // 30% iron
                boots = new ItemStack(Items.iron_boots);
                legs = new ItemStack(Items.iron_leggings);
                chest = new ItemStack(Items.iron_chestplate);
                helm = new ItemStack(Items.iron_helmet);
            } else if (armorTier < 17) { // 15% dyed leather + gold helmet
                boots = new ItemStack(Items.leather_boots);
                legs = new ItemStack(Items.leather_leggings);
                chest = new ItemStack(Items.leather_chestplate);
                int color = (((i * 41) & 0xFF) << 16) | (((i * 73) & 0xFF) << 8) | ((i * 109) & 0xFF);
                ((ItemArmor) Items.leather_boots).setColor(boots, color);
                ((ItemArmor) Items.leather_leggings).setColor(legs, color);
                ((ItemArmor) Items.leather_chestplate).setColor(chest, color);
                helm = new ItemStack(Items.golden_helmet);
            } else { // 15% chainmail
                boots = new ItemStack(Items.chainmail_boots);
                legs = new ItemStack(Items.chainmail_leggings);
                chest = new ItemStack(Items.chainmail_chestplate);
                helm = new ItemStack(Items.chainmail_helmet);
            }

            int itemTier = i % 20;
            ItemStack held;
            if (itemTier < 7) { // 35% Sharpness V diamond sword
                held = new ItemStack(Items.diamond_sword);
                held.addEnchantment(Enchantment.sharpness, 5);
            } else if (itemTier < 12) { // 25% Power V bow
                held = new ItemStack(Items.bow);
                held.addEnchantment(Enchantment.power, 5);
            } else if (itemTier < 15) { // 15% fishing rod
                held = new ItemStack(Items.fishing_rod);
            } else if (itemTier < 18) { // 15% golden apple
                held = new ItemStack(Items.golden_apple);
            } else { // 10% diamond pickaxe
                held = new ItemStack(Items.diamond_pickaxe);
            }

            player.setCurrentItemOrArmor(0, held);
            player.setCurrentItemOrArmor(1, boots);
            player.setCurrentItemOrArmor(2, legs);
            player.setCurrentItemOrArmor(3, chest);
            player.setCurrentItemOrArmor(4, helm);

            player.setPositionAndRotation(px, py, pz, yaw, 0);
            player.rotationYawHead = player.renderYawOffset = yaw;

            int entityId = -19200 - i;
            mc.theWorld.addEntityToWorld(entityId, player);

            String teamName = TEAMS[i % 4];
            if (sb != null) {
                ScorePlayerTeam team = sb.getTeam(teamName);
                if (team != null) sb.addPlayerToTeam(name, teamName);
                ScoreObjective hObj = sb.getObjective("lads_health");
                if (hObj != null) {
                    Score sc = sb.getValueFromObjective(name, hObj);
                    sc.setScorePoints(20);
                }
            }

            if (i % 4 == 0) {
                EntityArmorStand stand = new EntityArmorStand(mc.theWorld);
                stand.setInvisible(true);
                stand.setCustomNameTag("\u00a7e\u00a7lCLICK TO PLAY");
                stand.setAlwaysRenderNameTag(true);
                stand.setPosition(px, py + 2.3, pz);
                mc.theWorld.addEntityToWorld(-19500 - (i / 4), stand);
            }
        }
        LOG.info("Lads 1.8.9 perf: Probe192Players built scene {} (N = {})", sceneName, count);
    }

    public static void tick(Minecraft mc, String sceneName, int tick) {
        if (!isPlayerScene(sceneName) || mc.theWorld == null) return;
        for (int i = 0; i < activeCount; i++) {
            Entity entity = mc.theWorld.getEntityByID(-19200 - i);
            if (entity instanceof EntityOtherPlayerMP) {
                double targetX = baseX[i] + 0.05 * Math.cos(tick * 0.1 + i);
                double targetZ = baseZ[i] + 0.05 * Math.sin(tick * 0.1 + i);
                ((EntityOtherPlayerMP) entity).setPositionAndRotation2(targetX, 4.0, targetZ, baseYaw[i], 0, 3, true);
            }
        }
    }

    public static void teardown(Minecraft mc, String sceneName) {
        if (!isPlayerScene(sceneName) || mc.theWorld == null) return;
        for (int i = 0; i < activeCount; i++) {
            mc.theWorld.removeEntityFromWorld(-19200 - i);
            if (i % 4 == 0) mc.theWorld.removeEntityFromWorld(-19500 - (i / 4));
        }
        Map<UUID, NetworkPlayerInfo> tabMap = getPlayerInfoMap(mc);
        if (tabMap != null) {
            for (UUID uuid : spawnedUuids) tabMap.remove(uuid);
        }
        spawnedUuids.clear();

        Scoreboard sb = mc.theWorld.getScoreboard();
        if (sb != null) {
            ScoreObjective healthObj = sb.getObjective("lads_health");
            if (healthObj != null) sb.removeObjective(healthObj);
            for (String t : TEAMS) {
                ScorePlayerTeam team = sb.getTeam(t);
                if (team != null) sb.removeTeam(team);
            }
        }

        activeCount = 0;
        LOG.info("Lads 1.8.9 perf: Probe192Players torn down scene {}", sceneName);
    }

    private static int closeupPhase = 0;
    private static int closeupTimer = 0;
    private static EntityOtherPlayerMP closeupPlayer = null;
    private static UUID closeupUuid = null;
    private static boolean originalHideGui = false;

    public static boolean closeups(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null) return false;
        ensureTextures(mc);
        double camX = 0.5, camY = 4.0, camZ = 0.5;
        mc.thePlayer.setPosition(camX, camY, camZ);
        mc.thePlayer.prevPosX = mc.thePlayer.lastTickPosX = camX;
        mc.thePlayer.prevPosZ = mc.thePlayer.lastTickPosZ = camZ;
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
        mc.thePlayer.onGround = true;
        mc.thePlayer.capabilities.isFlying = false;
        mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = 0.0f;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 0.0f;

        if (closeupPlayer == null) {
            originalHideGui = mc.gameSettings.hideGUI;
            mc.gameSettings.hideGUI = true;
            mc.gameSettings.thirdPersonView = 0;
            closeupUuid = new UUID(0x192000L, 0x9999L);
            GameProfile profile = new GameProfile(closeupUuid, "ProbeInspector");
            closeupPlayer = new EntityOtherPlayerMP(mc.theWorld, profile);
            NetworkPlayerInfo info = new NetworkPlayerInfo(profile);
            ((NetworkPlayerInfoAccessor) info).ladsSetSkin(SKINS[0]);
            ((NetworkPlayerInfoAccessor) info).ladsSetSkinType("default");
            ((NetworkPlayerInfoAccessor) info).ladsSetTexturesLoaded(true);
            ((NetworkPlayerInfoCapeAccessor) info).ladsSetCape(CAPES[0]);
            Map<UUID, NetworkPlayerInfo> tabMap = getPlayerInfoMap(mc);
            if (tabMap != null) tabMap.put(closeupUuid, info);

            closeupPlayer.setCurrentItemOrArmor(0, new ItemStack(Items.diamond_sword));
            closeupPlayer.setPositionAndRotation(camX, camY, camZ + 2.0, 180.0f, 0.0f);
            closeupPlayer.rotationYawHead = closeupPlayer.renderYawOffset = 180.0f;
            mc.theWorld.addEntityToWorld(-19999, closeupPlayer);

            closeupPhase = 0;
            closeupTimer = 0;
            return false;
        }

        closeupTimer++;
        if (closeupPhase == 0) {
            closeupPlayer.setPositionAndRotation(camX, camY, camZ + 2.0, 180.0f, 0.0f);
            closeupPlayer.rotationYawHead = closeupPlayer.renderYawOffset = 180.0f;
            if (closeupTimer >= 12) {
                CoreProbe.screenshot(mc, "closeup-dist2");
                closeupPhase = 1;
                closeupTimer = 0;
            }
            return false;
        } else if (closeupPhase == 1) {
            closeupPlayer.setPositionAndRotation(camX, camY, camZ + 6.0, 180.0f, 0.0f);
            closeupPlayer.rotationYawHead = closeupPlayer.renderYawOffset = 180.0f;
            if (closeupTimer >= 12) {
                CoreProbe.screenshot(mc, "closeup-dist6");
                CoreProbe.screenshot(mc, "lod8-dist6");
                closeupPhase = 2;
                closeupTimer = 0;
            }
            return false;
        } else if (closeupPhase == 2) {
            closeupPlayer.setPositionAndRotation(camX, camY, camZ + 10.0, 180.0f, 0.0f);
            closeupPlayer.rotationYawHead = closeupPlayer.renderYawOffset = 180.0f;
            if (closeupTimer >= 12) {
                CoreProbe.screenshot(mc, "closeup-dist10");
                closeupPhase = 3;
                closeupTimer = 0;
            }
            return false;
        } else if (closeupPhase == 3) {
            closeupPlayer.setPositionAndRotation(camX, camY, camZ + 12.0, 180.0f, 0.0f);
            closeupPlayer.rotationYawHead = closeupPlayer.renderYawOffset = 180.0f;
            if (closeupTimer >= 12) {
                CoreProbe.screenshot(mc, "closeup-dist12");
                CoreProbe.screenshot(mc, "lod8-dist12");
                closeupPhase = 4;
                closeupTimer = 0;
            }
            return false;
        } else {
            mc.theWorld.removeEntityFromWorld(-19999);
            Map<UUID, NetworkPlayerInfo> tabMap = getPlayerInfoMap(mc);
            if (tabMap != null && closeupUuid != null) tabMap.remove(closeupUuid);
            closeupPlayer = null;
            closeupUuid = null;
            closeupPhase = 0;
            closeupTimer = 0;
            mc.gameSettings.hideGUI = originalHideGui;
            LOG.info("Lads 1.8.9 perf: Probe192Players closeups captured (dist 2, 6, 10, 12)");
            return true;
        }
    }
}
