package com.thelads.smp.features;

import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.mixin.MixinRangedAttribute;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Clean-room implementations of consolidated small tweak mods:
 * - AttributeFix (raises vanilla attribute max caps)
 * - BetterSpawnerControl (5-face torch/lantern spawner suppression)
 * - DespawningEggsHatch (despawning eggs hatch into baby chicks)
 * - DoubleDoors (paired door & fence gate synchronization)
 * - VillagerNames (curated first names for unnamed villagers)
 * - SleepHeal (Regeneration II while sleeping)
 * - StackablePotions (stackable potions + splash/lingering cooldown)
 * - Chest-only Disc Randomization
 */
public final class ConsolidatedFeatures {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/Features");

    private static final String[] VILLAGER_NAMES = {
            "Arthur", "Beatrice", "Cedric", "Clara", "Desmond", "Eleanor", "Felix", "Gideon",
            "Helena", "Isaac", "Julian", "Katarina", "Leonard", "Matilda", "Nathaniel", "Odette",
            "Percival", "Rosalind", "Sebastian", "Theodora", "Ulric", "Valerie", "Walter", "Yvonne",
            "Alaric", "Brigitte", "Corwin", "Daphne", "Edmund", "Freya", "Garrick", "Hilda",
            "Alden", "Bram", "Caspian", "Dorian", "Elspeth", "Fiora", "Galen", "Harlan",
            "Imogen", "Jasper", "Kaelen", "Linnea", "Magnus", "Nora", "Orin", "Rowan",
            "Sylvia", "Talia", "Vance", "Wren", "Zane", "Maren", "Soren", "Lyra"
    };

    private static final ThreadLocal<Boolean> TOGGLING_DOUBLE_DOOR = ThreadLocal.withInitial(() -> false);

    private ConsolidatedFeatures() {}

    public static void init() {
        applyAttributeFixes();
        registerVillagerNaming();
        registerWaystonesTeleportPets();
        registerSleepHeal();
        registerStackablePotions();
        registerChestDiscLootRandomization();
        BetterJoinMessagesManager.init();
        NetherPortalFixManager.init();
        RightClickHarvestManager.init();
        ZombieImprovementsManager.init();
    }

    /**
     * Consolidates 'attributefix': Raises vanilla RangedAttribute caps so modded bosses/gear don't clamp.
     */
    public static void applyAttributeFixes() {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (!cfg.attributeFix) return;
        try {
            raiseMaxCap(Attributes.MAX_HEALTH, cfg.maxHealthCap);
            raiseMaxCap(Attributes.ARMOR, cfg.maxArmorCap);
            raiseMaxCap(Attributes.ARMOR_TOUGHNESS, cfg.maxArmorToughnessCap);
            raiseMaxCap(Attributes.ATTACK_DAMAGE, cfg.maxAttackDamageCap);
            LOGGER.info("Applied AttributeFix caps (MAX_HEALTH={}, ARMOR={}, TOUGHNESS={}, ATTACK_DAMAGE={})",
                    cfg.maxHealthCap, cfg.maxArmorCap, cfg.maxArmorToughnessCap, cfg.maxAttackDamageCap);
        } catch (Throwable t) {
            LOGGER.warn("Could not apply AttributeFix caps: {}", t.getMessage());
        }
    }

    private static void raiseMaxCap(Holder<Attribute> holder, double newMax) {
        if (holder != null && holder.value() instanceof MixinRangedAttribute accessor) {
            if (accessor.ladssmp$getMaxValue() < newMax) {
                accessor.ladssmp$setMaxValue(newMax);
            }
        }
    }

    /**
     * Consolidates 'villagernames': Assigns a deterministic first name from VILLAGER_NAMES based on UUID
     * to unnamed AbstractVillager entities when loaded on the server.
     */
    private static void registerVillagerNaming() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!LadsConfig.get().features.villagerNames) return;
            if (entity instanceof AbstractVillager villager && !villager.hasCustomName()) {
                int idx = Math.floorMod(villager.getUUID().hashCode(), VILLAGER_NAMES.length);
                villager.setCustomName(Component.literal(VILLAGER_NAMES[idx]));
                villager.setCustomNameVisible(false);
            }
        });
    }

    /**
     * Consolidates 'betterspawnercontrol': Checks if a Spawner at pos has torches or lanterns on at least
     * spawnerRequiredTorchFaces (default 5: North, South, East, West, Up).
     */
    public static boolean isSpawnerSuppressedByTorches(Level level, BlockPos pos) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (!cfg.betterSpawnerControl || level == null || pos == null) return false;

        Direction[] faces = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
        int count = 0;
        for (int i = 0; i < 5; i++) {
            BlockState neighbor = level.getBlockState(pos.relative(faces[i]));
            if (isTorchOrLantern(neighbor)) {
                count++;
            }
        }
        if (count < cfg.spawnerRequiredTorchFaces && isTorchOrLantern(level.getBlockState(pos.below()))) {
            count++;
        }
        return count >= cfg.spawnerRequiredTorchFaces;
    }

    private static boolean isTorchOrLantern(BlockState state) {
        if (state == null || state.isAir()) return false;
        return state.getBlock() instanceof BaseTorchBlock
                || state.getBlock() instanceof LanternBlock
                || state.is(Blocks.TORCH)
                || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH)
                || state.is(Blocks.SOUL_WALL_TORCH)
                || state.is(Blocks.REDSTONE_TORCH)
                || state.is(Blocks.REDSTONE_WALL_TORCH)
                || state.is(Blocks.LANTERN)
                || state.is(Blocks.SOUL_LANTERN);
    }

    /**
     * Consolidates 'despawningeggshatch': Called when an ItemEntity reaches age >= 5999 and is about to despawn.
     */
    public static void tryHatchDespawningEgg(ItemEntity itemEntity) {
        LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
        if (!cfg.despawningEggsHatch) return;
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) return;

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty() || !stack.is(Items.EGG)) return;

        AABB searchBox = itemEntity.getBoundingBox().inflate(24.0);
        List<Chicken> nearby = serverLevel.getEntitiesOfClass(Chicken.class, searchBox);
        if (nearby.size() >= cfg.maxNearbyChickensForHatch) return;

        // Higher hatch chance if on a Hay Block (matching Collective's hay nest mechanic), or configurable base chance
        boolean onHay = serverLevel.getBlockState(itemEntity.blockPosition().below()).is(Blocks.HAY_BLOCK);
        double chance = onHay ? Math.max(cfg.eggHatchChance, 0.85) : cfg.eggHatchChance;

        if (serverLevel.getRandom().nextDouble() < chance) {
            @SuppressWarnings("unchecked")
            EntityType<Chicken> chickenType = (EntityType<Chicken>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("chicken"));
            Chicken chick = chickenType.create(serverLevel, EntitySpawnReason.BREEDING);
            if (chick != null) {
                chick.setBaby(true);
                chick.setPos(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
                serverLevel.addFreshEntity(chick);
            }
        }
    }

    /**
     * Consolidates 'doubledoors': Toggles the paired adjacent wooden door when a player opens/closes a door.
     */
    public static void tryTogglePairedDoor(Level level, BlockPos pos, BlockState state, boolean newOpenState) {
        if (!LadsConfig.get().features.doubleDoors || level.isClientSide() || TOGGLING_DOUBLE_DOOR.get()) {
            return;
        }
        if (!(state.getBlock() instanceof DoorBlock doorBlock) || !doorBlock.type().canOpenByHand()) {
            return;
        }

        Direction facing = state.getValue(DoorBlock.FACING);
        DoorHingeSide hinge = state.getValue(DoorBlock.HINGE);
        DoubleBlockHalf half = state.getValue(DoorBlock.HALF);

        // The paired double door sits to the right if hinge is LEFT, or to the left if hinge is RIGHT
        Direction neighborDir = (hinge == DoorHingeSide.LEFT) ? facing.getClockWise() : facing.getCounterClockWise();
        BlockPos neighborPos = pos.relative(neighborDir);
        BlockState neighborState = level.getBlockState(neighborPos);

        if (neighborState.is(doorBlock)
                && neighborState.getValue(DoorBlock.FACING) == facing
                && neighborState.getValue(DoorBlock.HINGE) != hinge
                && neighborState.getValue(DoorBlock.HALF) == half
                && neighborState.getValue(DoorBlock.OPEN) != newOpenState) {
            try {
                TOGGLING_DOUBLE_DOOR.set(true);
                doorBlock.setOpen(null, level, neighborState, neighborPos, newOpenState);
            } finally {
                TOGGLING_DOUBLE_DOOR.set(false);
            }
        }
    }

    /**
     * Consolidates 'doubledoors' for vertically or horizontally adjacent matching FenceGates.
     */
    public static void tryTogglePairedFenceGate(Level level, BlockPos pos, BlockState state, boolean newOpenState) {
        if (!LadsConfig.get().features.doubleDoors || level.isClientSide() || TOGGLING_DOUBLE_DOOR.get()) {
            return;
        }
        if (!(state.getBlock() instanceof FenceGateBlock)) return;
        Direction facing = state.getValue(FenceGateBlock.FACING);

        try {
            TOGGLING_DOUBLE_DOOR.set(true);
            for (Direction dir : new Direction[]{Direction.UP, Direction.DOWN, facing.getClockWise(), facing.getCounterClockWise()}) {
                BlockPos neighborPos = pos.relative(dir);
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.is(state.getBlock())
                        && neighborState.getValue(FenceGateBlock.FACING).getAxis() == facing.getAxis()
                        && neighborState.getValue(FenceGateBlock.OPEN) != newOpenState) {
                    level.setBlock(neighborPos, neighborState.setValue(FenceGateBlock.OPEN, newOpenState), 10);
                }
            }
        } finally {
            TOGGLING_DOUBLE_DOOR.set(false);
        }
    }

    /**
     * Consolidates 'golem_spawn_fix': Checks whether a block state in the two under-arm corners of an Iron Golem
     * pattern should be treated as empty/replaceable so grass, flowers, snow, or carpets don't block golem creation.
     */
    public static boolean isPermittedGolemCornerBlock(BlockState state) {
        if (state == null || state.isAir()) return true;
        if (!LadsConfig.get().features.golemSpawnFix) return false;
        return state.canBeReplaced()
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SMALL_FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.WOOL_CARPETS)
                || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.CANDLES)
                || state.is(BlockTags.CROPS)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH);
    }

    /**
     * Consolidates 'waystones_teleport_pets': Hooks Waystones' WaystoneTeleportEvent.Before.EVENT
     * so non-sitting tamed TamableAnimal pets within waystonesPetSearchRadius teleport with their owner.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerWaystonesTeleportPets() {
        try {
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("waystones")) {
                return;
            }
            Class<?> beforeEventClass = Class.forName("net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Before");
            Object eventMapper = beforeEventClass.getField("EVENT").get(null);
            java.lang.reflect.Method registerMethod = eventMapper.getClass().getMethod("register", Object.class);
            java.lang.reflect.Method getContextMethod = beforeEventClass.getMethod("getContext");

            java.util.function.Consumer consumer = (event) -> {
                try {
                    LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
                    if (!cfg.waystonesTeleportPets) return;
                    Object context = getContextMethod.invoke(event);
                    if (context == null) return;
                    java.lang.reflect.Method getEntityMethod = context.getClass().getMethod("getEntity");
                    java.lang.reflect.Method addEntityMethod = context.getClass().getMethod("addAdditionalEntity", net.minecraft.world.entity.Entity.class);
                    Object entityObj = getEntityMethod.invoke(context);
                    if (!(entityObj instanceof net.minecraft.world.entity.player.Player player)) {
                        return;
                    }
                    AABB searchBox = player.getBoundingBox().inflate(cfg.waystonesPetSearchRadius);
                    List<net.minecraft.world.entity.TamableAnimal> pets = player.level().getEntitiesOfClass(
                            net.minecraft.world.entity.TamableAnimal.class, searchBox, p -> true);
                    for (net.minecraft.world.entity.TamableAnimal pet : pets) {
                        if (pet.isInSittingPose()) continue;
                        if (pet.isTame()) {
                            net.minecraft.world.entity.LivingEntity owner = pet.getOwner();
                            if (owner != null && owner.getUUID().equals(player.getUUID())) {
                                addEntityMethod.invoke(context, pet);
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            };
            registerMethod.invoke(eventMapper, consumer);
            LOGGER.info("Registered WaystonesTeleportPets listener on WaystoneTeleportEvent.Before.EVENT");
        } catch (Throwable t) {
            LOGGER.debug("Waystones API not available for pet teleport hook: {}", t.getMessage());
        }
    }

    /**
     * Consolidates 'sleep-heal': Grants Regeneration II while sleeping in a bed and removes it upon waking.
     */
    private static void registerSleepHeal() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!LadsConfig.get().features.sleepHeal) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isSleeping()) {
                    MobEffectInstance existing = player.getEffect(MobEffects.REGENERATION);
                    if (existing == null || existing.getDuration() < 40) {
                        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1, false, true));
                    }
                }
            }
        });

        EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
            if (LadsConfig.get().features.sleepHeal && entity instanceof ServerPlayer player) {
                player.removeEffect(MobEffects.REGENERATION);
            }
        });
    }

    /**
     * Consolidates 'stackablepotions': Makes potions stackable up to potionMaxStackSize (default 16)
     * and applies a 0.75s throw cooldown on splash and lingering potions.
     */
    private static void registerStackablePotions() {
        DefaultItemComponentEvents.MODIFY.register(context -> {
            LadsConfig.FeatureSettings cfg = LadsConfig.get().features;
            if (!cfg.stackablePotions) return;
            int maxStack = Math.clamp(cfg.potionMaxStackSize, 1, 64);
            context.modify(Items.POTION, builder -> builder.set(DataComponents.MAX_STACK_SIZE, maxStack));
            context.modify(Items.SPLASH_POTION, builder -> builder
                    .set(DataComponents.MAX_STACK_SIZE, maxStack)
                    .set(DataComponents.USE_COOLDOWN, new UseCooldown(0.75f)));
            context.modify(Items.LINGERING_POTION, builder -> builder
                    .set(DataComponents.MAX_STACK_SIZE, maxStack)
                    .set(DataComponents.USE_COOLDOWN, new UseCooldown(0.75f)));
        });
    }

    public static boolean isChestLootTablePath(String path) {
        return path != null && path.startsWith("chests/");
    }

    /**
     * Chest-only music disc loot randomization (never touches block loot tables so ItemsDisplayed never crashes).
     */
    private static void registerChestDiscLootRandomization() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!LadsConfig.get().features.randomizeChestDiscLoot) return;
            String path = key.identifier().getPath();
            if (!isChestLootTablePath(path)) {
                return;
            }
            Item[] discs = {
                    Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS,
                    Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_FAR, Items.MUSIC_DISC_MALL,
                    Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_STRAD,
                    Items.MUSIC_DISC_WARD, Items.MUSIC_DISC_11, Items.MUSIC_DISC_WAIT,
                    Items.MUSIC_DISC_OTHERSIDE, Items.MUSIC_DISC_PIGSTEP, Items.MUSIC_DISC_RELIC,
                    Items.MUSIC_DISC_CREATOR, Items.MUSIC_DISC_CREATOR_MUSIC_BOX, Items.MUSIC_DISC_PRECIPICE
            };
            LootPool.Builder pool = LootPool.lootPool()
                    .when(LootItemRandomChanceCondition.randomChance(0.05f));
            for (Item disc : discs) {
                pool.add(LootItem.lootTableItem(disc).setWeight(1));
            }
            tableBuilder.withPool(pool);
        });
    }
}
