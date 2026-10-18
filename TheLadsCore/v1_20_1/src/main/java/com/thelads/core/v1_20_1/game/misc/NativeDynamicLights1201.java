package com.thelads.core.v1_20_1.game.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.DynamicLights;
import com.thelads.core.client.ExplosionFlashes;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.DynamicLightsModule;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.TorchBlock;
import org.slf4j.LoggerFactory;

/**
 * Dynamic Lights on 1.20.1; common DynamicLights does the light and decides what to rebuild. Each client tick this lists the light
 * sources near the player: held and dropped items, worn armor, glowing items in frames, primed TNT, swelling creepers, explosion flashes,
 * burning entities and naturally glowing mobs/projectiles (vanilla + modded SMP items/entities).
 * Chunk meshing and entity rendering take the brighter light through DynamicLightsBlockMixin, DynamicLightsEntityMixin, and SodiumDynamicLightsMixin.
 */
public final class NativeDynamicLights1201 {
    // ponytail: a fixed reach for sources, tie it to the render distance if far-away lights ever matter.
    private static final double RANGE = 64;
    private static final Map<EntityType<?>, Integer> ENTITY_LUMINANCE = new IdentityHashMap<>();
    private static final Map<Item, Integer> ITEM_LUMINANCE = new IdentityHashMap<>();

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

    // dynamiclantern waist equipment reflection cache
    private static Method waistItemsMethod;
    private static Method stackMethod;
    private static boolean checkedWaist;

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
        initLuminanceTables();
        ClientTickEvents.END_CLIENT_TICK.register(NativeDynamicLights1201::tick);
    }

    /** Triggered client-side when an explosion packet is received. */
    public static void onExplosion(double x, double y, double z, float power) {
        if (module != null && module.isEnabled() && module.explosionsAndTnt()) {
            ExplosionFlashes.WORLD.add(x, y, z, power);
        }
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
            ExplosionFlashes.WORLD.reset();
            level = mc.level;
        }
        if (level == null || mc.player == null) return;
        ExplosionFlashes.WORLD.tick();
        lights.begin();
        if (module.isEnabled()) {
            if (module.explosionsAndTnt()) {
                ExplosionFlashes.WORLD.collect(lights);
            }
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

        // Explosions & TNT: primed TNT, TNT minecart, creeper fuse
        if (module.explosionsAndTnt()) {
            if (entity instanceof PrimedTnt) return 15;
            if (entity instanceof MinecartTNT tnt && tnt.isPrimed()) return 15;
            if (entity instanceof Creeper creeper) {
                float swelling = creeper.getSwelling(0.0f);
                if (swelling > 0) return Math.max(1, Math.min(15, (int) (swelling * 15 + 0.5f)));
            }
        }

        // Falling block of glowing block
        if (entity instanceof FallingBlockEntity falling) {
            int emission = falling.getBlockState().getLightEmission();
            if (emission > 0) return emission;
        }

        // Item frames
        if (entity instanceof ItemFrame frame) {
            int frameLight = frame instanceof GlowItemFrame ? 8 : 0;
            ItemStack held = frame.getItem();
            if (!held.isEmpty()) frameLight = Math.max(frameLight, luminance(held, wet));
            return frameLight;
        }

        // Glowing mobs & projectiles
        int light = 0;
        if (entity.isOnFire()) {
            light = 15;
        } else if (module.glowingMobsAndProjectiles()) {
            if (entity instanceof WitherSkull skull) {
                light = skull.isDangerous() ? 14 : 10;
            } else {
                light = ENTITY_LUMINANCE.getOrDefault(entity.getType(), 0);
            }
        }

        // Players & mobs: held and worn equipment, plus waist items
        if (entity instanceof LivingEntity living) {
            light = Math.max(light, Math.max(luminance(living.getMainHandItem(), wet), luminance(living.getOffhandItem(), wet)));
            light = Math.max(light, Math.max(luminance(living.getItemBySlot(EquipmentSlot.HEAD), wet),
                    luminance(living.getItemBySlot(EquipmentSlot.CHEST), wet)));
            light = Math.max(light, Math.max(luminance(living.getItemBySlot(EquipmentSlot.LEGS), wet),
                    luminance(living.getItemBySlot(EquipmentSlot.FEET), wet)));
            if (entity instanceof Player player) {
                light = Math.max(light, waistLuminance(player, wet));
            }
        }
        return light;
    }

    /** What the item, block or fluid gives off. Torches and campfires go out underwater unless Underwater is on. */
    static int luminance(ItemStack stack, boolean wet) {
        if (stack.isEmpty()) return 0;
        Item item = stack.getItem();
        if (item == Items.GLOW_BERRIES) return 14;
        if (item == Items.LAVA_BUCKET) return 15;
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (wet && !module.underwater() && (block instanceof TorchBlock || block instanceof CampfireBlock)) return 0;
            return block.defaultBlockState().getLightEmission();
        }
        return ITEM_LUMINANCE.getOrDefault(item, 0);
    }

    private static int waistLuminance(Player player, boolean wet) {
        if (!checkedWaist) {
            checkedWaist = true;
            try {
                Class<?> cacheClass = Class.forName("org.com.dynamiclantern.WaistItemCache");
                waistItemsMethod = cacheClass.getMethod("getVisibleWaistItemsOrRefresh", Player.class);
                Class<?> itemClass = Class.forName("org.com.dynamiclantern.WaistItemCache$CachedItem");
                stackMethod = itemClass.getMethod("stack");
            } catch (Throwable ignored) {}
        }
        if (waistItemsMethod == null) return 0;
        try {
            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) waistItemsMethod.invoke(null, player);
            if (items == null || items.isEmpty()) return 0;
            int best = 0;
            for (Object item : items) {
                ItemStack stack = (ItemStack) stackMethod.invoke(item);
                if (stack != null && !stack.isEmpty()) {
                    best = Math.max(best, luminance(stack, wet));
                }
            }
            return best;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void initLuminanceTables() {
        // Built-in vanilla glowing mobs & projectiles
        ENTITY_LUMINANCE.put(EntityType.BLAZE, 12);
        ENTITY_LUMINANCE.put(EntityType.MAGMA_CUBE, 10);
        ENTITY_LUMINANCE.put(EntityType.GLOW_SQUID, 9);
        ENTITY_LUMINANCE.put(EntityType.ALLAY, 8);
        ENTITY_LUMINANCE.put(EntityType.VEX, 8);
        ENTITY_LUMINANCE.put(EntityType.FIREBALL, 14);
        ENTITY_LUMINANCE.put(EntityType.SMALL_FIREBALL, 12);
        ENTITY_LUMINANCE.put(EntityType.DRAGON_FIREBALL, 14);
        ENTITY_LUMINANCE.put(EntityType.SPECTRAL_ARROW, 8);
        ENTITY_LUMINANCE.put(EntityType.END_CRYSTAL, 15);
        ENTITY_LUMINANCE.put(EntityType.LIGHTNING_BOLT, 15);
        ENTITY_LUMINANCE.put(EntityType.EXPERIENCE_ORB, 6);

        // Built-in Lads SMP modded items & entities
        registerItem("friendsandfoes:wildfire_crown", 14);
        registerItem("friendsandfoes:wildfire_crown_fragment", 12);
        registerItem("relics_artifacts:torch_charm", 14);
        registerItem("relics_artifacts:charged_core", 12);
        registerItem("relics_artifacts:void_crystal", 8);
        registerItem("relics_artifacts:echo_dust", 6);
        registerItem("usefulhats:mining_hat", 14);
        registerItem("usefulhats:halo", 12);
        registerItem("naturalist:firefly", 8);
        registerItem("naturalist:glow_goop", 9);
        registerItem("naturalist:anglerfish", 7);
        registerItem("naturalist:anglerfish_bucket", 7);
        registerItem("naturalist:jellyfish_bucket", 6);
        registerItem("bitsandbalance:bundle_candle", 14);

        registerEntity("friendsandfoes:wildfire", 10);
        registerEntity("naturalist:firefly", 8);

        // Data-driven LambDynamicLights / dynamiclights tags and definitions shipped in mod jars
        try {
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                // Item tags: data/dynamiclights/tags/item/light_level/{level}.json
                loadItemTags(mod, "data/dynamiclights/tags/item/light_level");
                loadItemTags(mod, "data/lambdynlights/tags/item/light_level");
                // Entity luminance definitions
                loadEntityDefinitions(mod, "data/lambdynlights/dynamiclights/entity");
                loadEntityDefinitions(mod, "data/dynamiclights/entity");
                // Item luminance definitions
                loadItemDefinitions(mod, "data/lambdynlights/dynamiclights/item");
                loadItemDefinitions(mod, "data/dynamiclights/item");
            }
        } catch (Throwable failure) {
            LoggerFactory.getLogger("TheLadsCore").warn("Failed scanning data-driven dynamic lights", failure);
        }
    }

    private static void loadItemTags(ModContainer mod, String pathStr) {
        Optional<Path> dir = mod.findPath(pathStr);
        if (dir.isEmpty() || !Files.isDirectory(dir.get())) return;
        try (Stream<Path> stream = Files.list(dir.get())) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                String name = p.getFileName().toString();
                int dot = name.lastIndexOf('.');
                int level;
                try {
                    level = Integer.parseInt(name.substring(0, dot));
                } catch (NumberFormatException ignored) {
                    return;
                }
                try (Reader reader = Files.newBufferedReader(p)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("values") && json.get("values").isJsonArray()) {
                        for (JsonElement elem : json.getAsJsonArray("values")) {
                            if (elem.isJsonPrimitive()) {
                                registerItem(elem.getAsString(), level);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static void loadEntityDefinitions(ModContainer mod, String pathStr) {
        Optional<Path> dir = mod.findPath(pathStr);
        if (dir.isEmpty() || !Files.isDirectory(dir.get())) return;
        try (Stream<Path> stream = Files.list(dir.get())) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try (Reader reader = Files.newBufferedReader(p)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("match") && json.getAsJsonObject("match").has("type")) {
                        String typeId = json.getAsJsonObject("match").get("type").getAsString();
                        int lum = 0;
                        if (json.has("luminance")) {
                            JsonElement lElem = json.get("luminance");
                            if (lElem.isJsonPrimitive()) lum = lElem.getAsInt();
                            else if (lElem.isJsonObject() && lElem.getAsJsonObject().has("dry")) {
                                lum = lElem.getAsJsonObject().get("dry").getAsInt();
                            }
                        }
                        if (lum > 0) registerEntity(typeId, lum);
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static void loadItemDefinitions(ModContainer mod, String pathStr) {
        Optional<Path> dir = mod.findPath(pathStr);
        if (dir.isEmpty() || !Files.isDirectory(dir.get())) return;
        try (Stream<Path> stream = Files.list(dir.get())) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try (Reader reader = Files.newBufferedReader(p)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("match") && json.getAsJsonObject("match").has("item")) {
                        String itemId = json.getAsJsonObject("match").get("item").getAsString();
                        int lum = 0;
                        if (json.has("luminance")) {
                            JsonElement lElem = json.get("luminance");
                            if (lElem.isJsonPrimitive()) lum = lElem.getAsInt();
                            else if (lElem.isJsonObject() && lElem.getAsJsonObject().has("dry")) {
                                lum = lElem.getAsJsonObject().get("dry").getAsInt();
                            }
                        }
                        if (lum > 0) registerItem(itemId, lum);
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static void registerItem(String idStr, int lum) {
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id == null) return;
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR) {
                ITEM_LUMINANCE.put(item, lum);
            }
        }
    }

    private static void registerEntity(String idStr, int lum) {
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id == null) return;
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            ENTITY_LUMINANCE.put(type, lum);
        }
    }
}
