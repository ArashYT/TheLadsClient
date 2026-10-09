package com.thelads.core.v1_20_1.game.skin.mixin;

import com.thelads.core.v1_20_1.game.skin.SkinLayers1201;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** QA only (inert outside QA runs): players wear QaSkin's checkerboard outer layers so 3D layers stand out in captures. */
@Mixin(AbstractClientPlayer.class)
abstract class QaSkinMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void lads$qaSkin(CallbackInfoReturnable<ResourceLocation> callback) {
        ResourceLocation skin = SkinLayers1201.qaSkin();
        if (skin != null) callback.setReturnValue(skin);
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void lads$qaModel(CallbackInfoReturnable<String> callback) {
        if (SkinLayers1201.qaWide()) callback.setReturnValue("default");
    }
}
