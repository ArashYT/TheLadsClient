package com.thelads.smp.features.arise.nb3;

import com.thelads.smp.features.arise.nb3.botanypots.BotanyPotBlock;
import com.thelads.smp.features.arise.nb3.botanypots.BotanyPotBlockEntity;
import com.thelads.smp.features.arise.nb3.cosmeticarmor.CosmeticArmorFeature;
import com.thelads.smp.features.arise.nb3.snailmail.MailboxBlock;
import com.thelads.smp.features.arise.nb3.snailmail.MailboxBlockEntity;
import com.thelads.smp.features.arise.nb3.targetdummy.TargetDummyEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Common entrypoint and registry initializer for native batch NB3.
 */
public final class AriseNb3Mod {
    public static final String MOD_ID = "ladssmp";
    public static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB3");

    // Blocks
    public static final MailboxBlock MAILBOX_BLOCK = new MailboxBlock(
            BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.WOOD));
    public static final BotanyPotBlock BOTANY_POT_BLOCK = new BotanyPotBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.STONE).noOcclusion(), false);
    public static final BotanyPotBlock HOPPER_BOTANY_POT_BLOCK = new BotanyPotBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.STONE).noOcclusion(), true);

    // Block Items
    public static final BlockItem MAILBOX_ITEM = new BlockItem(MAILBOX_BLOCK, new Item.Properties());
    public static final BlockItem BOTANY_POT_ITEM = new BlockItem(BOTANY_POT_BLOCK, new Item.Properties());
    public static final BlockItem HOPPER_BOTANY_POT_ITEM = new BlockItem(HOPPER_BOTANY_POT_BLOCK, new Item.Properties());

    // Block Entity Types
    public static final BlockEntityType<MailboxBlockEntity> MAILBOX_BE_TYPE =
            new BlockEntityType<>(MailboxBlockEntity::new, Set.of(MAILBOX_BLOCK));
    public static final BlockEntityType<BotanyPotBlockEntity> BOTANY_POT_BE_TYPE =
            new BlockEntityType<>((pos, state) -> new BotanyPotBlockEntity(pos, state, false), Set.of(BOTANY_POT_BLOCK));
    public static final BlockEntityType<BotanyPotBlockEntity> HOPPER_BOTANY_POT_BE_TYPE =
            new BlockEntityType<>((pos, state) -> new BotanyPotBlockEntity(pos, state, true), Set.of(HOPPER_BOTANY_POT_BLOCK));

    // Entity Types
    public static final EntityType<TargetDummyEntity> TARGET_DUMMY_ENTITY_TYPE = TargetDummyEntity.createEntityType();

    private AriseNb3Mod() {}

    public static void init() {
        LOGGER.info("[Lads SMP] Initializing Native Batch NB3 (BackWeapon, CosmeticArmor, HoldMyItems, ImmersiveUI, TargetDummy, ObscureTooltips, VoicelessSurvival, SnailMail, BotanyPots)...");
        AriseNb3Config.load();

        // Register Blocks
        Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "mailbox"), MAILBOX_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "botany_pot"), BOTANY_POT_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "hopper_botany_pot"), HOPPER_BOTANY_POT_BLOCK);

        // Register Items
        Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "mailbox"), MAILBOX_ITEM);
        Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "botany_pot"), BOTANY_POT_ITEM);
        Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "hopper_botany_pot"), HOPPER_BOTANY_POT_ITEM);

        // Register Block Entity Types
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "mailbox"), MAILBOX_BE_TYPE);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "botany_pot"), BOTANY_POT_BE_TYPE);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "hopper_botany_pot"), HOPPER_BOTANY_POT_BE_TYPE);

        // Register Entity Types
        Registry.register(BuiltInRegistries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "target_dummy"), TARGET_DUMMY_ENTITY_TYPE);

        // Death persistence hook for cosmetic armor
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof Player player) {
                CosmeticArmorFeature.onPlayerDeath(player);
            }
        });

        LOGGER.info("[Lads SMP] Native Batch NB3 initialized successfully.");
    }
}
