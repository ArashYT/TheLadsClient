package com.thelads.smp.features.arise.nb1;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnDataManager;

/**
 * Native clean-room implementation of Carry On Extend.
 * Allows players carrying a mob or block via Carry On to throw it forward with projectile velocity.
 */
public final class CarryOnExtendFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-CarryOnExtend");

    public static void init() {
        // Register network payload for keybind throw
        PayloadTypeRegistry.serverboundPlay().register(ThrowCarriedPayload.TYPE, ThrowCarriedPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ThrowCarriedPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                throwCarried(player);
            });
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
        LOGGER.info("[Lads SMP] Carry On Extend initialized");
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().carryOnExtend.enabled;
    }

    public static boolean throwCarried(ServerPlayer player) {
        if (!isEnabled()) return false;

        try {
            CarryOnData carryData = CarryOnDataManager.getCarryData(player);
            if (carryData == null || !carryData.isCarrying()) {
                return false;
            }

            float power = AriseNB1Config.get().carryOnExtend.throwPower;
            Vec3 look = player.getLookAngle();
            Vec3 velocity = new Vec3(look.x * power, Math.max(0.2, look.y * power + 0.25), look.z * power);

            if (carryData.getType() == CarryOnData.CarryType.ENTITY) {
                Entity entity = carryData.getEntity(player.level());
                if (entity != null) {
                    entity.setPos(player.getX(), player.getEyeY() - 0.2, player.getZ());
                    entity.setDeltaMovement(velocity);
                    player.level().addFreshEntity(entity);
                }
            } else if (carryData.getType() == CarryOnData.CarryType.BLOCK) {
                BlockState state = carryData.getBlock();
                if (state != null && !state.isAir()) {
                    BlockPos eyePos = BlockPos.containing(player.getEyePosition());
                    FallingBlockEntity falling = FallingBlockEntity.fall(player.level(), eyePos, state);
                    CompoundTag contentNbt = carryData.getContentNbt();
                    if (contentNbt != null && !contentNbt.isEmpty()) {
                        falling.blockData = contentNbt;
                    }
                    falling.setDeltaMovement(velocity);
                }
            }

            // Clear the player's carry state
            carryData.clear();
            CarryOnDataManager.setCarryData(player, carryData);

            // Audio & visual feedback
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.2f);
            player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            return true;
        } catch (Throwable t) {
            LOGGER.error("Error throwing carried object for player {}", player.getName().getString(), t);
            return false;
        }
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("throw")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (throwCarried(player)) {
                        ctx.getSource().sendSuccess(() -> Component.literal("Threw carried object!").withStyle(ChatFormatting.GREEN), false);
                        return 1;
                    } else {
                        ctx.getSource().sendFailure(Component.literal("You are not carrying anything with Carry On.").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                })
        );

        dispatcher.register(Commands.literal("carryon")
                .then(Commands.literal("throw")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (throwCarried(player)) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Threw carried object!").withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            } else {
                                ctx.getSource().sendFailure(Component.literal("You are not carrying anything with Carry On.").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                        }))
        );

        dispatcher.register(Commands.literal("ladssmp")
                .then(Commands.literal("carry")
                        .then(Commands.literal("throw")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    if (throwCarried(player)) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("Threw carried object!").withStyle(ChatFormatting.GREEN), false);
                                        return 1;
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("You are not carrying anything with Carry On.").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                })))
        );
    }
}
