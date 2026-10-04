package com.thelads.core.v26_2.feature;

import com.thelads.core.client.DynamicLights;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.DynamicLightsModule;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;

/**
 * Dynamic Lights on 26.x; common DynamicLights does the light and decides what to rebuild. Each client tick this lists the light
 * sources near the player: held and dropped items give off what the block or fluid they place does, burning entities 15 and a
 * few mobs and projectiles glow by themselves. Chunk meshing (vanilla's and Sodium's both ask LightCoordsUtil.getLightCoords) and
 * entity rendering take the brighter light through DynamicLightsBlockMixin and DynamicLightsEntityMixin. While a LambDynamicLights
 * jar is loaded it keeps the job and this stays off.
 */
public final class NativeDynamicLights {
    // ponytail: a fixed reach for sources, tie it to the render distance if far-away lights ever matter.
    private static final double RANGE = 64;
    private static final Map<EntityType<?>, Integer> GLOWING = Map.of(EntityTypes.BLAZE, 12, EntityTypes.MAGMA_CUBE, 10,
        EntityTypes.GLOW_SQUID, 9, EntityTypes.ALLAY, 8, EntityTypes.FIREBALL, 14, EntityTypes.SMALL_FIREBALL, 12,
        EntityTypes.DRAGON_FIREBALL, 14, EntityTypes.SPECTRAL_ARROW, 8);
    /** QA: time spent collecting and publishing sources, ticks measured and section boxes sent for rebuilding. */
    static long tickNanos, ticks, rebuilds;
    private static final DynamicLights.Rebuild REBUILD = (minX, minY, minZ, maxX, maxY, maxZ) -> {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) level.setSectionRangeDirty(minX, minY, minZ, maxX, maxY, maxZ);
        rebuilds++;
    };
    private static DynamicLightsModule module;
    private static ClientLevel level;

    private NativeDynamicLights() {}

    public static void initialize() {
        if (module != null || FabricLoader.getInstance().isModLoaded("lambdynlights")) return;
        module = (DynamicLightsModule) ModuleManager.getInstance().getModule(DynamicLightsModule.NAME);
        ModuleSupport.registerBuiltIn(DynamicLightsModule.NAME);
        ClientTickEvents.END_CLIENT_TICK.register(NativeDynamicLights::tick);
    }

    /** Packed light coordinates with the dynamic block light at this block (any thread). */
    public static int coords(int coords, BlockPos pos) {
        int light = DynamicLights.WORLD.at(pos.getX(), pos.getY(), pos.getZ());
        return light > LightCoordsUtil.block(coords) ? LightCoordsUtil.withBlock(coords, light) : coords;
    }

    /** An entity's block light with the dynamic light at its light probe. */
    public static int blockLight(int light, BlockPos pos) {
        return Math.max(light, DynamicLights.WORLD.at(pos.getX(), pos.getY(), pos.getZ()));
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
        if (stack.getItem() instanceof BucketItem bucket) return bucket.getContent().defaultFluidState().createLegacyBlock().getLightEmission();
        if (!(stack.getItem() instanceof BlockItem item)) return 0;
        Block block = item.getBlock();
        if (wet && !module.underwater() && (block instanceof BaseTorchBlock || block instanceof CampfireBlock)) return 0;
        return block.defaultBlockState().getLightEmission();
    }
}
