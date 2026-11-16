package com.thelads.smp.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bits and Balance's Improved Ladders runs at the start of every server tick for every player and reads the blocks around the player with
 * Level.getBlockState, which waits for the chunk to exist. Right after a teleport into a dimension that is not generated yet (a Nether portal, /tp) the
 * player's chunk is still being generated, so the server thread stood still until it was done: 23-25 s of frozen world on a slow CPU. The check only
 * has to look at chunks that are already loaded: skip the player while its chunk is missing, and read a not loaded neighbour as air.
 */
@Pseudo
@Mixin(targets = "org.onenonly.bitsandbalance.fabric.tweaks.FabricImprovedLadders", remap = false)
public class MixinBitsAndBalanceLaddersLoadedOnly {
    @Inject(method = {"applyImprovedClimbingPre", "applyImprovedClimbingPost"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$skipUnloaded(ServerPlayer player, CallbackInfo ci) {
        if (!com.thelads.smp.util.LoadedChunks.isLoaded(player.level(), player.blockPosition())) ci.cancel();
    }

    @WrapOperation(method = {"isNearClimbable", "findAttachmentDirectionFromChain", "getVineStateForPosition"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"), remap = false, require = 0)
    private static BlockState ladssmp$loadedOnly(Level level, BlockPos pos, Operation<BlockState> original) {
        return com.thelads.smp.util.LoadedChunks.isLoaded(level, pos) ? original.call(level, pos) : Blocks.AIR.defaultBlockState();
    }
}
