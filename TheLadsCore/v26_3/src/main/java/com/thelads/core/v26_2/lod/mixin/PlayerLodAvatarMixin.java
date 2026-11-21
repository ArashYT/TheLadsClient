package com.thelads.core.v26_2.lod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.perf.PlayerLod;
import com.thelads.core.v26_2.lod.LodFlags26;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player LOD: flags are set while the state is extracted; outer layer, cape and name tag are handled here, the rest in the layer mixins. */
@Mixin(AvatarRenderer.class)
abstract class PlayerLodAvatarMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
    private void lads$lodExtract(Avatar avatar, AvatarRenderState state, float partial, CallbackInfo ci) {
        int bits = LodFlags26.avatarBits(avatar);
        ((LodFlags26) state).lads$lod(bits);
        if ((bits & PlayerLod.OUTER) != 0) {
            state.showHat = false;
            state.showJacket = false;
            state.showLeftSleeve = false;
            state.showRightSleeve = false;
            state.showLeftPants = false;
            state.showRightPants = false;
        }
        if ((bits & PlayerLod.CAPE) != 0) state.showCape = false;
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void lads$lodName(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (LodFlags26.has(state, PlayerLod.NAME)) ci.cancel();
    }
}
