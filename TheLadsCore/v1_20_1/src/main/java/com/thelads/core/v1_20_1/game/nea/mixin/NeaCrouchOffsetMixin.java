package com.thelads.core.v1_20_1.game.nea.mixin;

import com.thelads.core.v1_20_1.game.nea.Nea1201;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The whole-model crouch drop (2 pixels) eases with the cross-faded crouch pose instead of snapping. */
@Mixin(PlayerRenderer.class)
public class NeaCrouchOffsetMixin {
    @Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"),
        cancellable = true)
    private void lads$easeCrouchDrop(AbstractClientPlayer player, float partial, CallbackInfoReturnable<Vec3> callback) {
        float drop = Nea1201.crouchDrop(player, partial);
        if (drop >= 0f) {
            Vec3 offset = callback.getReturnValue();
            callback.setReturnValue(new Vec3(offset.x, -0.125 * drop, offset.z));
        }
    }
}
