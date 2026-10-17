package com.thelads.smp.features;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.thelads.smp.config.LadsConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Consolidates 'nametagtweaks':
 * - /nametag <name> command to rename held name tags without an anvil.
 * - Named mob drops its name tag upon death.
 */
public final class NameTagTweaksManager {
    private NameTagTweaksManager() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("nametag")
                    .requires(source -> LadsConfig.get().features.nameTagTweaks && source.getEntity() instanceof ServerPlayer)
                    .executes(ctx -> {
                        ctx.getSource().sendSystemMessage(Component.literal("Usage: /nametag <name>")
                                .withStyle(ChatFormatting.RED));
                        return 1;
                    })
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
                                if (!stack.is(Items.NAME_TAG)) {
                                    stack = player.getItemInHand(InteractionHand.OFF_HAND);
                                }
                                if (!stack.is(Items.NAME_TAG)) {
                                    player.sendSystemMessage(Component.literal("You must hold a Name Tag in either hand to rename it.")
                                            .withStyle(ChatFormatting.RED));
                                    return 1;
                                }

                                String name = StringArgumentType.getString(ctx, "name");
                                if (LadsConfig.get().features.nameTagCommandReplaceUnderscoresWithSpaces) {
                                    name = name.replace('_', ' ');
                                }

                                stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
                                stack.set(DataComponents.REPAIR_COST, 0);

                                player.sendSystemMessage(Component.literal("Name tag name set to '" + name + "'.")
                                        .withStyle(ChatFormatting.DARK_GREEN));
                                return 1;
                            })));
        });
    }
}
