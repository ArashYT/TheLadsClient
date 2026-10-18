package com.thelads.smp.features.arise.nb8;

import com.thelads.smp.config.LadsConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

/**
 * Server-side manager for Vein Mining.
 * Handles block break interception, per-tick queue scheduling, hunger/durability deduction,
 * tool tier validation, claim/spawn protection checks, and drop spawning at the origin block.
 */
public final class VeinMiningManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/VeinMining");

    private static final Map<UUID, PlayerVeinState> PLAYER_STATES = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<VeinTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();
    private static final ThreadLocal<Boolean> IS_PROCESSING_VEIN = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final TagKey<Block> CONVENTIONAL_ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));

    public static final class PlayerVeinState {
        public volatile boolean active = false;
        public volatile VeinShape shape = VeinShape.SHAPELESS;
        public volatile Direction hitFace = null;
    }

    private static final class VeinTask {
        final UUID playerId;
        final ServerLevel level;
        final BlockPos originPos;
        final BlockState originState;
        final List<BlockPos> remainingBlocks;
        int totalMined = 0;

        VeinTask(UUID playerId, ServerLevel level, BlockPos originPos, BlockState originState, List<BlockPos> remainingBlocks) {
            this.playerId = playerId;
            this.level = level;
            this.originPos = originPos;
            this.originState = originState;
            this.remainingBlocks = remainingBlocks;
        }
    }

    public static void init() {
        // Intercept block break events on server
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            if (IS_PROCESSING_VEIN.get()) {
                return;
            }
            handleBlockBroken(serverLevel, serverPlayer, pos, state);
        });

        // Server tick processing for active vein mining tasks
        ServerTickEvents.END_SERVER_TICK.register(VeinMiningManager::tick);

        // Player disconnect cleanup
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                removePlayer(handler.player.getUUID());
            }
        });
    }

    public static PlayerVeinState getOrCreatePlayerState(UUID playerId) {
        return PLAYER_STATES.computeIfAbsent(playerId, id -> new PlayerVeinState());
    }

    public static PlayerVeinState getPlayerState(UUID playerId) {
        return PLAYER_STATES.get(playerId);
    }

    public static void updatePlayerState(UUID playerId, boolean active, int shapeOrdinal, int hitDirectionOrdinal) {
        PlayerVeinState state = getOrCreatePlayerState(playerId);
        state.active = active;
        state.shape = VeinShape.fromOrdinal(shapeOrdinal);
        state.hitFace = hitDirectionOrdinal >= 0 ? Direction.from3DDataValue(hitDirectionOrdinal) : null;
    }

    public static void removePlayer(UUID playerId) {
        PLAYER_STATES.remove(playerId);
        ACTIVE_TASKS.removeIf(task -> task.playerId.equals(playerId));
    }

    /**
     * Called when a player successfully breaks an initial block.
     */
    public static void handleBlockBroken(ServerLevel level, ServerPlayer player, BlockPos originPos, BlockState originState) {
        AriseNB8Config config = AriseNB8Config.get();
        if (!config.enabled) return;
        if (!LadsConfig.get().features.veinMining) return;

        PlayerVeinState veinState = getPlayerState(player.getUUID());
        if (veinState == null || !veinState.active) {
            return;
        }

        ItemStack tool = player.getMainHandItem();
        if (config.requireTool && tool.isEmpty()) {
            return;
        }

        // Must not be unbreakable
        if (originState.getDestroySpeed(level, originPos) < 0) {
            return;
        }

        // Categorize origin block
        boolean isOre = isOreBlock(originState);
        boolean isLog = isLogBlock(originState);
        boolean matchesConfigTag = matchesMergeTags(originState, config.mergeTags);

        // In shapeless mode, only mine matching ores, logs, or configured merge tags
        if (veinState.shape == VeinShape.SHAPELESS && !isOre && !isLog && !matchesConfigTag) {
            return;
        }

        boolean eyeLevel = originPos.getY() >= player.getEyeY() - 0.5;

        // Build candidate filter
        Predicate<BlockPos> filter = pos -> {
            if (pos.equals(originPos)) return false; // Already broken

            BlockState candidate = level.getBlockState(pos);
            if (candidate.isAir()) return false;
            if (candidate.getDestroySpeed(level, pos) < 0) return false;

            // Container check
            if (!config.mineContainers) {
                if (candidate.getBlock() instanceof BaseEntityBlock || level.getBlockEntity(pos) instanceof Container) {
                    return false;
                }
            }

            // Tool harvestability check
            if (candidate.requiresCorrectToolForDrops() && !player.hasCorrectToolForDrops(candidate)) {
                return false;
            }

            // Shape matching criteria
            if (veinState.shape != VeinShape.SHAPELESS && config.shapedAnyBlock) {
                return true; // Shaped allows carving all harvestable blocks
            }

            return areBlocksCompatible(originState, candidate, isOre, isLog, config.mergeTags);
        };

        // Calculate positions
        List<BlockPos> rawPositions = veinState.shape.calculatePositions(
                level,
                originPos,
                veinState.hitFace,
                player.getDirection(),
                eyeLevel,
                filter,
                config.maxBlocks + 1
        );

        List<BlockPos> toBreak = new ArrayList<>();
        for (BlockPos p : rawPositions) {
            if (!p.equals(originPos)) {
                toBreak.add(p);
                if (toBreak.size() >= config.maxBlocks) break;
            }
        }

        if (toBreak.isEmpty()) {
            return;
        }

        // Queue task for server tick processing
        ACTIVE_TASKS.add(new VeinTask(player.getUUID(), level, originPos, originState, toBreak));
    }

    /**
     * Executes queued vein mining tasks with a per-tick budget to avoid lag spikes.
     */
    public static void tick(MinecraftServer server) {
        if (ACTIVE_TASKS.isEmpty()) return;

        AriseNB8Config config = AriseNB8Config.get();
        int budget = Math.max(1, config.blocksPerTick);

        IS_PROCESSING_VEIN.set(Boolean.TRUE);
        try {
            Iterator<VeinTask> it = ACTIVE_TASKS.iterator();
            while (it.hasNext()) {
                VeinTask task = it.next();
                ServerPlayer player = server.getPlayerList().getPlayer(task.playerId);

                if (player == null || !player.isAlive() || player.level() != task.level) {
                    it.remove();
                    continue;
                }

                int processedThisTick = 0;
                while (!task.remainingBlocks.isEmpty() && processedThisTick < budget) {
                    BlockPos targetPos = task.remainingBlocks.remove(0);
                    processedThisTick++;

                    // Verify player state before each block
                    if (player.getFoodData().getFoodLevel() <= 0) {
                        task.remainingBlocks.clear();
                        break;
                    }

                    ItemStack tool = player.getMainHandItem();
                    if (config.requireTool && tool.isEmpty()) {
                        task.remainingBlocks.clear();
                        break;
                    }

                    // Durability threshold check (prevent tool breaking if configured)
                    if (config.preventToolBreak > 0 && tool.isDamageableItem()) {
                        int remainingDurability = tool.getMaxDamage() - tool.getDamageValue();
                        if (remainingDurability <= config.preventToolBreak) {
                            task.remainingBlocks.clear();
                            break;
                        }
                    }

                    BlockState state = task.level.getBlockState(targetPos);
                    if (state.isAir()) continue;

                    // Re-validate harvestability and protection
                    if (state.requiresCorrectToolForDrops() && !player.hasCorrectToolForDrops(state)) {
                        continue;
                    }

                    // Spawn protection
                    if (server.isUnderSpawnProtection(task.level, targetPos, player)) {
                        continue;
                    }

                    // Player restricted (adventure mode, etc.)
                    if (player.blockActionRestricted(task.level, targetPos, player.gameMode.getGameModeForPlayer())) {
                        continue;
                    }

                    BlockEntity blockEntity = task.level.getBlockEntity(targetPos);

                    // Claims compatibility (fires Fabric BEFORE event, catching claim plugins/mods)
                    if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(task.level, player, targetPos, state, blockEntity)) {
                        continue;
                    }

                    // Handle container inventory drops if container mining is permitted
                    if (blockEntity instanceof Container container) {
                        if (!config.mineContainers) {
                            continue;
                        }
                        Containers.dropContents(task.level, task.originPos, container);
                    }

                    // Drop block loot and experience at the first block (originPos)
                    Block.dropResources(state, task.level, task.originPos, blockEntity, player, tool);

                    // Remove block from world
                    task.level.removeBlock(targetPos, false);

                    // Visual particles and block break sound
                    task.level.levelEvent(2001, targetPos, Block.getId(state));

                    // Tool durability cost
                    tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

                    // Hunger exhaustion cost
                    player.causeFoodExhaustion((float) (config.exhaustionMultiplier * 0.005F));

                    // Stats
                    player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
                    task.totalMined++;

                    // If tool broke completely, abort remaining blocks
                    if (tool.isEmpty()) {
                        task.remainingBlocks.clear();
                        break;
                    }
                }

                if (task.remainingBlocks.isEmpty()) {
                    it.remove();
                }
            }
        } finally {
            IS_PROCESSING_VEIN.set(Boolean.FALSE);
        }
    }

    public static boolean isOreBlock(BlockState state) {
        if (state.is(BlockTags.ORES) || state.is(CONVENTIONAL_ORES)) {
            return true;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && id.getPath().endsWith("_ore");
    }

    public static boolean isLogBlock(BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return true;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return false;
        String path = id.getPath();
        return path.endsWith("_log") || path.endsWith("_stem") || path.endsWith("_wood");
    }

    public static boolean matchesMergeTags(BlockState state, List<String> mergeTags) {
        if (mergeTags == null || mergeTags.isEmpty()) return false;
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) return false;

        String idStr = blockId.toString();
        for (String pattern : mergeTags) {
            if (pattern.equals("*")) return true;
            if (pattern.startsWith("#")) {
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, Identifier.parse(pattern.substring(1)));
                if (state.is(tag)) return true;
            } else if (pattern.contains("*")) {
                String regex = pattern.replace(".", "\\.").replace("*", ".*");
                if (idStr.matches(regex)) return true;
            } else if (idStr.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    public static boolean areBlocksCompatible(BlockState origin, BlockState candidate, boolean isOre, boolean isLog, List<String> mergeTags) {
        if (origin.is(candidate.getBlock())) {
            return true;
        }

        // If both are ores, allow merging if configured in mergeTags or standard ore variants
        if (isOre && isOreBlock(candidate)) {
            // E.g. deepslate_iron_ore and iron_ore
            String originName = BuiltInRegistries.BLOCK.getKey(origin.getBlock()).getPath().replace("deepslate_", "");
            String candName = BuiltInRegistries.BLOCK.getKey(candidate.getBlock()).getPath().replace("deepslate_", "");
            if (originName.equals(candName)) {
                return true;
            }
            if (matchesMergeTags(origin, mergeTags) && matchesMergeTags(candidate, mergeTags)) {
                return true;
            }
        }

        // If both are logs of same wood type
        if (isLog && isLogBlock(candidate)) {
            String originPath = BuiltInRegistries.BLOCK.getKey(origin.getBlock()).getPath();
            String candPath = BuiltInRegistries.BLOCK.getKey(candidate.getBlock()).getPath();
            String originBase = originPath.replace("stripped_", "").replace("_wood", "").replace("_log", "").replace("_stem", "");
            String candBase = candPath.replace("stripped_", "").replace("_wood", "").replace("_log", "").replace("_stem", "");
            if (originBase.equals(candBase)) {
                return true;
            }
        }

        return false;
    }
}
