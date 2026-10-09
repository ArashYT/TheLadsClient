package com.thelads.core.v1_20_1.game.misc;

import com.thelads.core.client.DynamicLights;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.DynamicLightsModule;
import com.thelads.core.v1_20_1.game.ModPresence1201;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import org.slf4j.LoggerFactory;

/**
 * Dynamic Lights on 1.20.1; common DynamicLights does the light and decides what to rebuild. Each client tick this lists the light
 * sources near the player: held and dropped items give off what the block they place does, burning entities 15 and a few mobs and
 * projectiles glow by themselves. The light reaches the screen four ways, all through the brighter-of rule below: vanilla chunk
 * meshing, block entities and particles (LevelRenderer.getLightColor), entity rendering (EntityRenderer.getBlockLightLevel), and the
 * light cache of Sodium 0.5 and Embeddium 0.3 (LightDataAccess.compute: they mesh chunks without asking vanilla). While a
 * LambDynamicLights jar is loaded it keeps the job and this stays off.
 */
public final class NativeDynamicLights1201 {
    // ponytail: a fixed reach for sources, tie it to the render distance if far-away lights ever matter.
    private static final double RANGE = 64;
    private static final Map<EntityType<?>, Integer> GLOWING = Map.of(EntityType.BLAZE, 12, EntityType.MAGMA_CUBE, 10,
        EntityType.GLOW_SQUID, 9, EntityType.ALLAY, 8, EntityType.FIREBALL, 14, EntityType.SMALL_FIREBALL, 12,
        EntityType.DRAGON_FIREBALL, 14, EntityType.SPECTRAL_ARROW, 8);
    /** QA: time spent collecting and publishing sources, ticks measured, section boxes sent for rebuilding, light lookups changed by each path. */
    public static volatile long tickNanos, ticks, rebuilds, vanillaHits, entityHits, sodiumHits;
    private static final DynamicLights.Rebuild REBUILD = (minX, minY, minZ, maxX, maxY, maxZ) -> {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.levelRenderer == null) return;
        int low = Math.max(minY, level.getMinSection()), high = Math.min(maxY, level.getMaxSection() - 1);
        for (int x = minX; x <= maxX; x++)
            for (int y = low; y <= high; y++)
                for (int z = minZ; z <= maxZ; z++) mc.levelRenderer.setSectionDirty(x, y, z);
        rebuilds++;
    };
    private static DynamicLightsModule module;
    private static ClientLevel level;
    private static boolean active;

    private NativeDynamicLights1201() {}

    public static void initialize() {
        if (module != null) return;
        if (ModPresence1201.loaded("lambdynlights")) {
            ModuleSupport.registerExternal(DynamicLightsModule.NAME, "LambDynamicLights", "lambdynlights", true);
            LoggerFactory.getLogger("TheLadsCore").info("Lads Dynamic Lights: LambDynamicLights is installed and keeps the job");
            return;
        }
        module = (DynamicLightsModule) ModuleManager.getInstance().getModule(DynamicLightsModule.NAME);
        ModuleSupport.registerBuiltIn(DynamicLightsModule.NAME);
        active = true;
        ClientTickEvents.END_CLIENT_TICK.register(NativeDynamicLights1201::tick);
    }

    /** This adapter lights the world (false while LambDynamicLights does, or before the first tick of a world). */
    public static boolean active() { return active; }

    public static DynamicLightsModule module() { return module; }

    /** The dynamic block light (0-15) at this block, 0 when nothing lights it (any thread). */
    public static int light(int x, int y, int z) {
        return active ? DynamicLights.WORLD.at(x, y, z) : 0;
    }

    /** Packed light coordinates with the dynamic block light at this block (any thread): LevelRenderer.getLightColor. */
    public static int coords(int coords, BlockPos pos) {
        int light = light(pos.getX(), pos.getY(), pos.getZ());
        if (light <= LightTexture.block(coords)) return coords;
        vanillaHits++;
        return LightTexture.pack(light, LightTexture.sky(coords));
    }

    /** An entity's block light with the dynamic light at its light probe: EntityRenderer.getBlockLightLevel. */
    public static int blockLight(int light, BlockPos pos) {
        int dynamic = light(pos.getX(), pos.getY(), pos.getZ());
        if (dynamic <= light) return light;
        entityHits++;
        return dynamic;
    }

    /**
     * Sodium's and Embeddium's packed light word for a block (LightDataAccess.compute): block light in bits 0-3, a full opaque block
     * with no glow of its own is left at 0 as they leave it. Their meshing threads call this.
     */
    public static int sodiumWord(int word, int x, int y, int z) {
        boolean solid = (word >>> 30 & 1) != 0 && (word >>> 8 & 0xF) == 0;
        if (solid || !active) return word;
        int light = DynamicLights.WORLD.at(x, y, z);
        if (light <= (word & 0xF)) return word;
        sodiumHits++;
        return word & ~0xF | light;
    }

    /** QA: whether the Sodium/Embeddium light cache class carries our hook (Mixin merges the handler into it): "applied", "not hooked" or "absent". */
    public static String sodiumMixinState() {
        for (String name : new String[] {"me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess"}) {
            try {
                Class<?> cache = Class.forName(name, false, NativeDynamicLights1201.class.getClassLoader());
                for (java.lang.reflect.Method method : cache.getDeclaredMethods()) if (method.getName().contains("lads$dynamicLight")) return "applied";
                return "not hooked";
            } catch (Throwable absent) {
                return "absent";
            }
        }
        return "absent";
    }

    private static void tick(Minecraft mc) {
        long start = System.nanoTime();
        DynamicLights lights = DynamicLights.WORLD;
        if (mc.level != level) {
            lights.reset();
            level = mc.level;
        }
        if (level == null || mc.player == null) return;
        lights.begin();
        if (module.isEnabled()) {
            for (Entity entity : level.entitiesForRendering()) {
                if (entity.distanceToSqr(mc.player) > RANGE * RANGE) continue;
                int light = luminance(entity, entity == mc.player);
                if (light > 0) lights.add(entity.getId(), entity.getX(), entity.getEyeY(), entity.getZ(), light);
            }
        }
        boolean fancy = module.fancy();
        lights.end(module.radius(), fancy ? 0.25f : 0.5f, fancy ? 1 : 4, REBUILD);
        tickNanos += System.nanoTime() - start;
        ticks++;
    }

    /** The local player always counts; other entities with Entities on, dropped items with Dropped Items on. */
    static int luminance(Entity entity, boolean self) {
        boolean wet = entity.isUnderWater();
        if (entity instanceof ItemEntity item) return module.droppedItems() ? luminance(item.getItem(), wet) : 0;
        if (!self && !module.entities()) return 0;
        int light = entity.isOnFire() ? 15 : GLOWING.getOrDefault(entity.getType(), 0);
        if (entity instanceof LivingEntity living)
            light = Math.max(light, Math.max(luminance(living.getMainHandItem(), wet), luminance(living.getOffhandItem(), wet)));
        return light;
    }

    /** What the block or fluid the stack places gives off. Torches and campfires go out underwater unless Underwater is on. */
    static int luminance(ItemStack stack, boolean wet) {
        if (stack.isEmpty()) return 0;
        if (stack.getItem() == Items.GLOW_BERRIES) return 14; // the cave vine they place glows only while it bears them
        if (stack.getItem() == Items.LAVA_BUCKET) return 15;
        if (!(stack.getItem() instanceof BlockItem item)) return 0;
        Block block = item.getBlock();
        if (wet && !module.underwater() && (block instanceof TorchBlock || block instanceof CampfireBlock)) return 0;
        return block.defaultBlockState().getLightEmission();
    }
}
