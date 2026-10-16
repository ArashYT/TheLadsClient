package com.thelads.smp.mixin;

import dev.architectury.event.EventResult;
import dev.architectury.utils.value.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Custom Nether Portals' private event handlers, called by {@link MixinCustomNetherPortalsEvents}. */
@Pseudo
@Mixin(targets = "net.night.customnetherportals.portal.event.PortalIgniteHandler", remap = false)
public interface MixinCustomNetherPortalsInvoker {

    @Invoker(value = "onRightClickBlock", remap = false)
    static InteractionResult ladssmp$onRightClickBlock(Player player, InteractionHand hand, BlockPos pos, Direction face) {
        throw new AssertionError();
    }

    @Invoker(value = "onBlockBreak", remap = false)
    static EventResult ladssmp$onBlockBreak(Level level, BlockPos pos, BlockState state, ServerPlayer player, IntValue xp) {
        throw new AssertionError();
    }
}
