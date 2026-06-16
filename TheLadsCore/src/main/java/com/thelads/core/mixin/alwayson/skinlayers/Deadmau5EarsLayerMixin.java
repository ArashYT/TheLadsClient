package com.thelads.core.mixin.alwayson.skinlayers;

import com.thelads.core.features.alwayson.skinlayers.accessor.PlayerEntityModelAccessor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Deadmau5EarsLayer.class)
public class Deadmau5EarsLayerMixin {
    @Shadow
    @Final
    private HumanoidModel<AvatarRenderState> model;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet modelSet, CallbackInfo ci) {
        if (this.model instanceof PlayerEntityModelAccessor) {
            ((PlayerEntityModelAccessor) this.model).setIgnored(true);
        }
    }
}
