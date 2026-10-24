package com.thelads.core.v1_20_1.game.pvp.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.v1_20_1.game.pvp.PvpVisuals1201;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block Overlay: the module draws the targeted block's outline (and fill) instead of Minecraft's black 40% lines. */
@Mixin(LevelRenderer.class)
public class PvpBlockOverlayMixin1201 {
    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void lads$blockOverlay(PoseStack pose, VertexConsumer lines, Entity camera, double camX, double camY, double camZ, BlockPos pos,
                                   BlockState state, CallbackInfo ci) {
        if (PvpVisuals1201.blockOverlay(pose, camera, camX, camY, camZ, pos, state)) ci.cancel();
    }
}
