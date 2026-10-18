package com.thelads.smp.features.arise.nb3;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.thelads.smp.features.arise.nb3.backweapon.BackWeaponFeature;
import com.thelads.smp.features.arise.nb3.backweapon.SwapBackWeaponPayload;
import com.thelads.smp.features.arise.nb3.botanypots.BotanyPotBlock;
import com.thelads.smp.features.arise.nb3.botanypots.BotanyPotBlockEntity;
import com.thelads.smp.features.arise.nb3.cosmeticarmor.CosmeticArmorFeature;
import com.thelads.smp.features.arise.nb3.cosmeticarmor.SyncCosmeticArmorPayload;
import com.thelads.smp.features.arise.nb3.cosmeticarmor.UpdateCosmeticArmorPayload;
import com.thelads.smp.features.arise.nb3.snailmail.MailboxBlock;
import com.thelads.smp.features.arise.nb3.snailmail.MailboxBlockEntity;
import com.thelads.smp.features.arise.nb3.snailmail.SnailMailFeature;
import com.thelads.smp.features.arise.nb3.targetdummy.TargetDummyEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    // Resource Keys
    public static final net.minecraft.resources.ResourceKey<Block> MAILBOX_BLOCK_KEY =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "mailbox"));
    public static final net.minecraft.resources.ResourceKey<Block> BOTANY_POT_BLOCK_KEY =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "botany_pot"));
    public static final net.minecraft.resources.ResourceKey<Block> HOPPER_BOTANY_POT_BLOCK_KEY =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "hopper_botany_pot"));

    public static final net.minecraft.resources.ResourceKey<Item> MAILBOX_ITEM_KEY =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "mailbox"));
    public static final net.minecraft.resources.ResourceKey<Item> BOTANY_POT_ITEM_KEY =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "botany_pot"));
    public static final net.minecraft.resources.ResourceKey<Item> HOPPER_BOTANY_POT_ITEM_KEY =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "hopper_botany_pot"));
    public static final net.minecraft.resources.ResourceKey<Item> TARGET_DUMMY_ITEM_KEY =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "target_dummy"));

    // Blocks
    public static final MailboxBlock MAILBOX_BLOCK = new MailboxBlock(
            BlockBehaviour.Properties.of().setId(MAILBOX_BLOCK_KEY).strength(2.0f).sound(SoundType.WOOD));
    public static final BotanyPotBlock BOTANY_POT_BLOCK = new BotanyPotBlock(
            BlockBehaviour.Properties.of().setId(BOTANY_POT_BLOCK_KEY).strength(1.5f).sound(SoundType.STONE).noOcclusion(), false);
    public static final BotanyPotBlock HOPPER_BOTANY_POT_BLOCK = new BotanyPotBlock(
            BlockBehaviour.Properties.of().setId(HOPPER_BOTANY_POT_BLOCK_KEY).strength(1.5f).sound(SoundType.STONE).noOcclusion(), true);

    // Items
    public static final BlockItem MAILBOX_ITEM = new BlockItem(MAILBOX_BLOCK, new Item.Properties().setId(MAILBOX_ITEM_KEY));
    public static final BlockItem BOTANY_POT_ITEM = new BlockItem(BOTANY_POT_BLOCK, new Item.Properties().setId(BOTANY_POT_ITEM_KEY));
    public static final BlockItem HOPPER_BOTANY_POT_ITEM = new BlockItem(HOPPER_BOTANY_POT_BLOCK, new Item.Properties().setId(HOPPER_BOTANY_POT_ITEM_KEY));
    public static final Item TARGET_DUMMY_ITEM = new com.thelads.smp.features.arise.nb3.targetdummy.TargetDummyItem(new Item.Properties().setId(TARGET_DUMMY_ITEM_KEY));

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
        LOGGER.info("[Lads SMP] Initializing Native Batch NB3 (BackWeapon, CosmeticArmor, HoldMyItems, ImmersiveUI, TargetDummy, ObscureTooltips, SnailMail, BotanyPots)...");
        AriseNb3Config.load();

        // Register Blocks
        Registry.register(BuiltInRegistries.BLOCK, MAILBOX_BLOCK_KEY, MAILBOX_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, BOTANY_POT_BLOCK_KEY, BOTANY_POT_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, HOPPER_BOTANY_POT_BLOCK_KEY, HOPPER_BOTANY_POT_BLOCK);

        // Register Items
        Registry.register(BuiltInRegistries.ITEM, MAILBOX_ITEM_KEY, MAILBOX_ITEM);
        Registry.register(BuiltInRegistries.ITEM, BOTANY_POT_ITEM_KEY, BOTANY_POT_ITEM);
        Registry.register(BuiltInRegistries.ITEM, HOPPER_BOTANY_POT_ITEM_KEY, HOPPER_BOTANY_POT_ITEM);
        Registry.register(BuiltInRegistries.ITEM, TARGET_DUMMY_ITEM_KEY, TARGET_DUMMY_ITEM);

        // Register Block Entity Types
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "mailbox"), MAILBOX_BE_TYPE);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "botany_pot"), BOTANY_POT_BE_TYPE);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "hopper_botany_pot"), HOPPER_BOTANY_POT_BE_TYPE);

        // Register Entity Types
        Registry.register(BuiltInRegistries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "target_dummy"), TARGET_DUMMY_ENTITY_TYPE);
        // A LivingEntity type without default attributes NPEs when spawned (AttributeMap of a null supplier)
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(TARGET_DUMMY_ENTITY_TYPE,
                net.minecraft.world.entity.decoration.ArmorStand.createAttributes());

        // Register Custom Network Payloads
        PayloadTypeRegistry.serverboundPlay().register(UpdateCosmeticArmorPayload.TYPE, UpdateCosmeticArmorPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SwapBackWeaponPayload.TYPE, SwapBackWeaponPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncCosmeticArmorPayload.TYPE, SyncCosmeticArmorPayload.STREAM_CODEC);

        // Server Network Packet Receivers
        ServerPlayNetworking.registerGlobalReceiver(UpdateCosmeticArmorPayload.TYPE, (payload, context) -> {
            var player = context.player();
            var data = CosmeticArmorFeature.getOrCreate(player.getUUID());
            data.set(EquipmentSlot.HEAD, payload.head());
            data.set(EquipmentSlot.CHEST, payload.chest());
            data.set(EquipmentSlot.LEGS, payload.legs());
            data.set(EquipmentSlot.FEET, payload.feet());
            data.setVisibility(EquipmentSlot.HEAD, payload.headVis());
            data.setVisibility(EquipmentSlot.CHEST, payload.chestVis());
            data.setVisibility(EquipmentSlot.LEGS, payload.legsVis());
            data.setVisibility(EquipmentSlot.FEET, payload.feetVis());

            SyncCosmeticArmorPayload sync = new SyncCosmeticArmorPayload(
                    player.getUUID(),
                    payload.head(), payload.chest(), payload.legs(), payload.feet(),
                    payload.headVis(), payload.chestVis(), payload.legsVis(), payload.feetVis()
            );
            for (var trackingPlayer : PlayerLookup.tracking(player)) {
                ServerPlayNetworking.send(trackingPlayer, sync);
            }
            ServerPlayNetworking.send(player, sync);
        });

        ServerPlayNetworking.registerGlobalReceiver(SwapBackWeaponPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                BackWeaponFeature.swapBackWeapon(context.player());
            });
        });

        // Player Join: Record name for mail delivery & sync existing cosmetics
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer joiningPlayer = handler.getPlayer();
            SnailMailFeature.recordPlayerName(joiningPlayer.getName().getString(), joiningPlayer.getUUID());

            // Sync cosmetic armor of all players to the joining player
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                var data = CosmeticArmorFeature.getOrCreate(other.getUUID());
                SyncCosmeticArmorPayload sync = new SyncCosmeticArmorPayload(
                        other.getUUID(),
                        data.get(EquipmentSlot.HEAD), data.get(EquipmentSlot.CHEST),
                        data.get(EquipmentSlot.LEGS), data.get(EquipmentSlot.FEET),
                        data.isVisible(EquipmentSlot.HEAD), data.isVisible(EquipmentSlot.CHEST),
                        data.isVisible(EquipmentSlot.LEGS), data.isVisible(EquipmentSlot.FEET)
                );
                ServerPlayNetworking.send(joiningPlayer, sync);
            }
        });

        // Snail Mail Server Tick for scheduled transit arrivals
        ServerTickEvents.END_SERVER_TICK.register(SnailMailFeature::tickDeliveries);

        // Death persistence hook for cosmetic armor
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof Player player) {
                CosmeticArmorFeature.onPlayerDeath(player);
            }
        });

        // Register commands: /mail send <player> and /cosmetics
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("mail")
                    .then(Commands.literal("send")
                            .then(Commands.argument("recipient", StringArgumentType.word())
                                    .executes(ctx -> {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        String recipient = StringArgumentType.getString(ctx, "recipient");
                                        ItemStack held = player.getMainHandItem();
                                        if (held.isEmpty()) {
                                            ctx.getSource().sendFailure(Component.literal("You must hold an item in your main hand to send mail!"));
                                            return 0;
                                        }
                                        ItemStack toSend = held.copy();
                                        held.setCount(0);
                                        SnailMailFeature.scheduleDelivery(player.getUUID(), recipient, toSend, System.currentTimeMillis());
                                        ctx.getSource().sendSuccess(() -> Component.literal("Sent " + toSend.getHoverName().getString() + " to " + recipient + " via Snail Mail!").withStyle(ChatFormatting.GREEN), false);
                                        return 1;
                                    }))));

            dispatcher.register(Commands.literal("cosmetics")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal("Use the [C] key or click the 👘 button in your inventory to open Cosmetic Armor!").withStyle(ChatFormatting.GOLD), false);
                        return 1;
                    }));
        });

        LOGGER.info("[Lads SMP] Native Batch NB3 initialized successfully.");
    }
}
