package com.thelads.smp.features;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.thelads.smp.config.LadsConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.stream.Collectors;

/**
 * Consolidates 'enchantingcommands':
 * - /ec command suite to inspect registered enchantments and apply enchantments
 *   above vanilla max levels (levels 0-255).
 */
public final class EnchantingCommandsManager {
    private EnchantingCommandsManager() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> {
            dispatcher.register(Commands.literal("ec")
                    .requires(source -> LadsConfig.get().features.enchantingCommands && source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(ctx -> {
                        sendUsage(ctx.getSource().getPlayerOrException());
                        return 1;
                    })
                    .then(Commands.literal("list")
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                Registry<Enchantment> registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                                String enchants = registry.keySet().stream()
                                        .map(Object::toString)
                                        .sorted()
                                        .collect(Collectors.joining(", "));
                                player.sendSystemMessage(Component.literal("[Enchanting Commands] Registered Enchantments:\n" + enchants)
                                        .withStyle(ChatFormatting.DARK_GREEN));
                                return 1;
                            }))
                    .then(Commands.literal("enchant")
                            .then(Commands.argument("enchantment", ResourceArgument.resource(buildContext, Registries.ENCHANTMENT))
                                    .then(Commands.argument("level", IntegerArgumentType.integer(0, 255))
                                            .executes(ctx -> {
                                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                ItemStack stack = player.getMainHandItem();
                                                if (stack.isEmpty()) {
                                                    player.sendSystemMessage(Component.literal("You must hold an item in your main hand.")
                                                            .withStyle(ChatFormatting.RED));
                                                    return 1;
                                                }

                                                Holder.Reference<Enchantment> enchantmentHolder = ResourceArgument.getEnchantment(ctx, "enchantment");
                                                int level = IntegerArgumentType.getInteger(ctx, "level");
                                                String enchantmentId = enchantmentHolder.key().identifier().toString();

                                                if (level == 0) {
                                                    ItemEnchantments existing = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                                                    ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(existing);
                                                    mutable.set(enchantmentHolder, 0);
                                                    stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
                                                    player.sendSystemMessage(Component.literal("Enchantment '" + enchantmentId + "' removed from held item.")
                                                            .withStyle(ChatFormatting.DARK_GREEN));
                                                } else {
                                                    stack.enchant(enchantmentHolder, level);
                                                    player.sendSystemMessage(Component.literal("Enchantment '" + enchantmentId + "' level " + level + " added to held item.")
                                                            .withStyle(ChatFormatting.DARK_GREEN));
                                                }
                                                return 1;
                                            })))));
        });
    }

    private static void sendUsage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("[Enchanting Commands] Usage:")
                .withStyle(ChatFormatting.DARK_GREEN));
        player.sendSystemMessage(Component.literal("  /ec list")
                .withStyle(ChatFormatting.DARK_GREEN));
        player.sendSystemMessage(Component.literal("  /ec enchant <enchantment> <level (0-255)>")
                .withStyle(ChatFormatting.DARK_GREEN));
    }
}
