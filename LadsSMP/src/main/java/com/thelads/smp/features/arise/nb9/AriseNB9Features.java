package com.thelads.smp.features.arise.nb9;

import com.thelads.smp.features.arise.nb9.block.*;
import com.thelads.smp.features.arise.nb9.entity.*;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Main feature orchestrator and registry for Arise SMP Native Batch NB9
 * (Amendments-Style Vanilla Block Upgrades).
 */
public final class AriseNB9Features {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-NB9");
    private static boolean initialized = false;

    // Blocks
    public static Block DYED_CAULDRON;
    public static Block POTION_CAULDRON;
    public static Block WALL_LANTERN;
    public static Block SOUL_WALL_LANTERN;
    public static Block SKULL_PILE;
    public static Block SKULL_CANDLE;
    public static Block CARPETED_STAIRS;
    public static Block CARPETED_SLAB;
    public static Block DOUBLE_CAKE;
    public static Block TOOL_HOOK;
    public static Block HANGING_POT;

    // Block Entity Types
    public static BlockEntityType<DyedCauldronBlockEntity> DYED_CAULDRON_BE;
    public static BlockEntityType<PotionCauldronBlockEntity> POTION_CAULDRON_BE;
    public static BlockEntityType<WallLanternBlockEntity> WALL_LANTERN_BE;
    public static BlockEntityType<SkullPileBlockEntity> SKULL_PILE_BE;
    public static BlockEntityType<SkullCandleBlockEntity> SKULL_CANDLE_BE;
    public static BlockEntityType<CarpetedStairsBlockEntity> CARPETED_STAIRS_BE;
    public static BlockEntityType<CarpetedSlabBlockEntity> CARPETED_SLAB_BE;
    public static BlockEntityType<ToolHookBlockEntity> TOOL_HOOK_BE;
    public static BlockEntityType<HangingPotBlockEntity> HANGING_POT_BE;

    public static void init() {
        if (initialized) return;
        initialized = true;

        AriseNB9Config.load();
        registerBlocksAndEntities();
        registerEventCallbacks();

        LOGGER.info("[LadsSMP-NB9] Initialized Amendments-style vanilla block upgrades (Cauldrons, WallLanterns, HangingSigns, Lectern, Skulls, Carpets, DoubleCake, Bells, ToolHook)");
    }

    private static void registerBlocksAndEntities() {
        // 1. Cauldrons
        DYED_CAULDRON = registerBlock("dyed_cauldron", DyedCauldronBlock::new,
                BlockBehaviour.Properties.ofFullCopy(Blocks.WATER_CAULDRON));
        POTION_CAULDRON = registerBlock("potion_cauldron", PotionCauldronBlock::new,
                BlockBehaviour.Properties.ofFullCopy(Blocks.WATER_CAULDRON));

        // 2. Wall Lanterns
        WALL_LANTERN = registerBlock("wall_lantern", props -> new WallLanternBlock(false, props),
                BlockBehaviour.Properties.ofFullCopy(Blocks.LANTERN).lightLevel(state -> 15));
        SOUL_WALL_LANTERN = registerBlock("soul_wall_lantern", props -> new WallLanternBlock(true, props),
                BlockBehaviour.Properties.ofFullCopy(Blocks.SOUL_LANTERN).lightLevel(state -> 10));

        // 3. Skulls (Pile & Candle)
        SKULL_PILE = registerBlock("skull_pile", SkullPileBlock::new,
                BlockBehaviour.Properties.ofFullCopy(Blocks.SKELETON_SKULL));
        SKULL_CANDLE = registerBlock("skull_candle", SkullCandleBlock::new,
                BlockBehaviour.Properties.ofFullCopy(Blocks.SKELETON_SKULL)
                        .lightLevel(state -> state.getValue(SkullCandleBlock.LIT) ? state.getValue(SkullCandleBlock.CANDLES) * 3 : 0));

        // 4. Carpets (Stairs & Slab)
        CARPETED_STAIRS = registerBlock("carpeted_stairs", CarpetedStairsBlock::new,
                BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_STAIRS).sound(SoundType.WOOL));
        CARPETED_SLAB = registerBlock("carpeted_slab", CarpetedSlabBlock::new,
                BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SLAB).sound(SoundType.WOOL));

        // 5. Double Cake
        DOUBLE_CAKE = registerBlock("double_cake", DoubleCakeBlock::new,
                BlockBehaviour.Properties.ofFullCopy(Blocks.CAKE));

        // 6. Tool Hook
        TOOL_HOOK = registerBlock("tool_hook", ToolHookBlock::new,
                BlockBehaviour.Properties.ofFullCopy(Blocks.TRIPWIRE_HOOK));

        // 7. Hanging Pot
        HANGING_POT = registerBlock("hanging_pot", HangingPotBlock::new,
                BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWER_POT));

        // Block Entity Types
        DYED_CAULDRON_BE = registerBlockEntity("dyed_cauldron", DyedCauldronBlockEntity::new, DYED_CAULDRON);
        POTION_CAULDRON_BE = registerBlockEntity("potion_cauldron", PotionCauldronBlockEntity::new, POTION_CAULDRON);
        WALL_LANTERN_BE = registerBlockEntity("wall_lantern", WallLanternBlockEntity::new, WALL_LANTERN, SOUL_WALL_LANTERN);
        SKULL_PILE_BE = registerBlockEntity("skull_pile", SkullPileBlockEntity::new, SKULL_PILE);
        SKULL_CANDLE_BE = registerBlockEntity("skull_candle", SkullCandleBlockEntity::new, SKULL_CANDLE);
        CARPETED_STAIRS_BE = registerBlockEntity("carpeted_stairs", CarpetedStairsBlockEntity::new, CARPETED_STAIRS);
        CARPETED_SLAB_BE = registerBlockEntity("carpeted_slab", CarpetedSlabBlockEntity::new, CARPETED_SLAB);
        TOOL_HOOK_BE = registerBlockEntity("tool_hook", ToolHookBlockEntity::new, TOOL_HOOK);
        HANGING_POT_BE = registerBlockEntity("hanging_pot", HangingPotBlockEntity::new, HANGING_POT);
    }

    private static void registerEventCallbacks() {
        UseBlockCallback.EVENT.register(AmendmentsBlockInteractions::handleUseBlock);
        AttackEntityCallback.EVENT.register(TorchFireHandler::handleAttack);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            if (HangingSignItemManager.isHangingSign(state)) {
                HangingSignItemManager.onSignRemoved(level, pos, be);
            }
            return true;
        });
    }

    private static <T extends Block> T registerBlock(
            String path,
            Function<BlockBehaviour.Properties, T> factory,
            BlockBehaviour.Properties properties
    ) {
        Identifier id = Identifier.fromNamespaceAndPath("ladssmp", path);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        properties.setId(blockKey);
        T block = factory.apply(properties);
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);

        return block;
    }

    private static <T extends BlockEntity> BlockEntityType<T> registerBlockEntity(
            String path,
            FabricBlockEntityTypeBuilder.Factory<T> factory,
            Block... blocks
    ) {
        Identifier id = Identifier.fromNamespaceAndPath("ladssmp", path);
        ResourceKey<BlockEntityType<?>> beKey = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id);
        BlockEntityType<T> type = FabricBlockEntityTypeBuilder.create(factory, blocks).build();
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, beKey, type);
    }
}
