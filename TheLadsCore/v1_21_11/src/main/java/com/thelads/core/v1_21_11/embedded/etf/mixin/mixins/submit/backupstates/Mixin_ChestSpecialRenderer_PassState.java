package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.submit.backupstates;


import net.minecraft.client.renderer.special.ChestSpecialRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFSubmitData;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFEntity;

@Mixin(ChestSpecialRenderer.class)
public class Mixin_ChestSpecialRenderer_PassState {

    @Inject(method = "submit", at = @At(value = "HEAD"))
    private static void emf$dummyState(CallbackInfo ci) {
        var state = ETFEntityRenderState.forEntity(
                // TODO do we really need the actual chest type here? this is just so inventory anims can play
                (ETFEntity) new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()));
        ETFState.mount(state);
    }

    @Inject(method = "submit", at = @At(value = "TAIL"))
    private static void emf$reset(CallbackInfo ci) {
        ETFState.unMount();
    }

}