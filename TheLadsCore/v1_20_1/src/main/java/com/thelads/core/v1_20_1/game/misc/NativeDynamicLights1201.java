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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
 * burning entities and naturally glowing mobs/projectiles (vanilla + modded SMP items/entities with wet/dry luminance).
 * Chunk meshing and entity rendering take the brighter light through DynamicLightsBlockMixin, DynamicLightsEntityMixin, and SodiumDynamicLightsMixin.
 */
public final class NativeDynamicLights1201 {
    // ponytail: a fixed reach for sources, tie it to the render distance if far-away lights ever matter.
    private static final double RANGE = 64;
    // Values stored packed: (dry << 16) | (wet & 0xFFFF)
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

    // dynamiclantern waist equipment cache (MethodHandles resolved once, cached by entity ID for up to 10 ticks, zero allocation)
    private static final int WAIST_CAP = 128;
    private static final int WAIST_MASK = 127;
    private static final int[] WAIST_ENTITY_IDS = new int[WAIST_CAP];
    private static final int[] WAIST_LIGHT_DRY = new int[WAIST_CAP];
    private static final int[] WAIST_LIGHT_WET = new int[WAIST_CAP];
    private static final long[] WAIST_UPDATE_TICK = new long[WAIST_CAP];
    private static MethodHandle waistItemsHandle;
    private static MethodHandle stackHandle;
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
            Arrays.fill(WAIST_ENTITY_IDS, 0);
            level = mc.level;
        }
        if (level == null || mc.player == null) return;
        ExplosionFlashes.WORLD.tick();
        lights.begin();
        if (module.isEnabled()) {
            if (module.explosionsAndTnt()) {
                ExplosionFlashes.WORLD.collect(lights);
            }
            long curTick = ticks;
            for (Entity entity : level.entitiesForRendering()) {
                if (entity.distanceToSqr(mc.player) > RANGE * RANGE) continue;
                int light = luminance(entity, entity == mc.player, curTick);
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
        return luminance(entity, self, ticks);
    }

    static int luminance(Entity entity, boolean self, long curTick) {
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

        // Glowing mobs & projectiles (with wet/dry luminance awareness)
        int light = 0;
        if (entity.isOnFire()) {
            light = 15;
        } else if (module.glowingMobsAndProjectiles()) {
            if (entity instanceof WitherSkull skull) {
                light = skull.isDangerous() ? 14 : 10;
            } else {
                int packed = ENTITY_LUMINANCE.getOrDefault(entity.getType(), 0);
                light = wet ? (packed & 0xFFFF) : (packed >>> 16);
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
                light = Math.max(light, waistLuminance(player, wet, curTick));
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
        int packed = ITEM_LUMINANCE.getOrDefault(item, 0);
        return wet ? (packed & 0xFFFF) : (packed >>> 16);
    }

    private static void initWaistHandles() {
        if (checkedWaist) return;
        checkedWaist = true;
        try {
            Class<?> cacheClass = Class.forName("org.com.dynamiclantern.WaistItemCache");
            MethodType mtWaist = MethodType.methodType(List.class, Player.class);
            waistItemsHandle = MethodHandles.publicLookup().findStatic(cacheClass, "getVisibleWaistItemsOrRefresh", mtWaist);
            Class<?> itemClass = Class.forName("org.com.dynamiclantern.WaistItemCache$CachedItem");
            MethodType mtStack = MethodType.methodType(ItemStack.class);
            stackHandle = MethodHandles.publicLookup().findVirtual(itemClass, "stack", mtStack);
        } catch (Throwable ignored) {
            waistItemsHandle = null;
            stackHandle = null;
        }
    }

    private static int waistLuminance(Player player, boolean wet, long curTick) {
        initWaistHandles();
        if (waistItemsHandle == null || stackHandle == null) return 0;
        int entityId = player.getId();
        int slot = entityId & WAIST_MASK;
        if (WAIST_ENTITY_IDS[slot] == entityId && (curTick - WAIST_UPDATE_TICK[slot] < 10)) {
            return wet ? WAIST_LIGHT_WET[slot] : WAIST_LIGHT_DRY[slot];
        }
        int bestDry = 0, bestWet = 0;
        try {
            List<?> items = (List<?>) waistItemsHandle.invokeExact(player);
            if (items != null) {
                int size = items.size();
                for (int i = 0; i < size; i++) {
                    Object item = items.get(i);
                    ItemStack stack = (ItemStack) stackHandle.invoke(item);
                    if (stack != null && !stack.isEmpty()) {
                        bestDry = Math.max(bestDry, luminance(stack, false));
                        bestWet = Math.max(bestWet, luminance(stack, true));
                    }
                }
            }
        } catch (Throwable ignored) {}
        WAIST_ENTITY_IDS[slot] = entityId;
        WAIST_LIGHT_DRY[slot] = bestDry;
        WAIST_LIGHT_WET[slot] = bestWet;
        WAIST_UPDATE_TICK[slot] = curTick;
        return wet ? bestWet : bestDry;
    }

    private static void putEntity(EntityType<?> type, int dry, int wet) {
        ENTITY_LUMINANCE.put(type, (dry << 16) | (wet & 0xFFFF));
    }

    private static void putEntity(EntityType<?> type, int lum) {
        putEntity(type, lum, lum);
    }

    private static void initLuminanceTables() {
        // Built-in vanilla glowing mobs & projectiles
        putEntity(EntityType.BLAZE, 12);
        putEntity(EntityType.MAGMA_CUBE, 10);
        putEntity(EntityType.GLOW_SQUID, 9);
        putEntity(EntityType.ALLAY, 8);
        putEntity(EntityType.VEX, 8);
        putEntity(EntityType.FIREBALL, 14);
        putEntity(EntityType.SMALL_FIREBALL, 12);
        putEntity(EntityType.DRAGON_FIREBALL, 14);
        putEntity(EntityType.SPECTRAL_ARROW, 8);
        putEntity(EntityType.END_CRYSTAL, 15);
        putEntity(EntityType.LIGHTNING_BOLT, 15);
        putEntity(EntityType.EXPERIENCE_ORB, 6);

        // Built-in Lads SMP modded items & entities
        registerItem("friendsandfoes:wildfire_crown", 14);
        registerItem("friendsandfoes:wildfire_crown_fragment", 12);
        registerItem("relics_artifacts:torch_charm", 14);
        registerItem("relics_artifacts:charged_core", 12);
        registerItem("relics_artifacts:void_crystal", 8);
        registerItem("relics_artifacts:echo_dust", 6);
        registerItem("usefulhats:mining_hat", 14);
        registerItem("usefulhats:halo", 12);
        registerItem("naturalist:firefly", 8, 0); // goes out underwater
        registerItem("naturalist:glow_goop", 9);
        registerItem("naturalist:anglerfish", 7);
        registerItem("naturalist:anglerfish_bucket", 7);
        registerItem("naturalist:jellyfish_bucket", 6);
        registerItem("bitsandbalance:bundle_candle", 14);

        registerEntity("friendsandfoes:wildfire", 10, 4); // 10 dry, 4 wet per LambDynamicLights data
        registerEntity("naturalist:firefly", 8, 0);

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
                        if (json.has("luminance")) {
                            JsonElement lElem = json.get("luminance");
                            if (lElem.isJsonPrimitive()) {
                                int lum = lElem.getAsInt();
                                if (lum > 0) registerEntity(typeId, lum);
                            } else if (lElem.isJsonObject()) {
                                JsonObject obj = lElem.getAsJsonObject();
                                int dry = obj.has("dry") ? obj.get("dry").getAsInt() : 0;
                                int wet = obj.has("wet") ? obj.get("wet").getAsInt() : dry;
                                if (dry > 0 || wet > 0) registerEntity(typeId, dry, wet);
                            }
                        }
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
                        if (json.has("luminance")) {
                            JsonElement lElem = json.get("luminance");
                            if (lElem.isJsonPrimitive()) {
                                int lum = lElem.getAsInt();
                                if (lum > 0) registerItem(itemId, lum);
                            } else if (lElem.isJsonObject()) {
                                JsonObject obj = lElem.getAsJsonObject();
                                int dry = obj.has("dry") ? obj.get("dry").getAsInt() : 0;
                                int wet = obj.has("wet") ? obj.get("wet").getAsInt() : dry;
                                if (dry > 0 || wet > 0) registerItem(itemId, dry, wet);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static void registerItem(String idStr, int dry, int wet) {
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id == null) return;
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR) {
                ITEM_LUMINANCE.put(item, (dry << 16) | (wet & 0xFFFF));
            }
        }
    }

    private static void registerItem(String idStr, int lum) {
        registerItem(idStr, lum, lum);
    }

    private static void registerEntity(String idStr, int dry, int wet) {
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id == null) return;
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            ENTITY_LUMINANCE.put(type, (dry << 16) | (wet & 0xFFFF));
        }
    }

    private static void registerEntity(String idStr, int lum) {
        registerEntity(idStr, lum, lum);
    }
}
