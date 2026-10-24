package com.thelads.core.v1_20_1.mixin.pvp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render Tweaks, Entity Shadows off: no shadow under any entity. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherShadowMixin1201 {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void lads$hideShadow(PoseStack pose, MultiBufferSource buffers, Entity entity, float alpha, float partialTick,
                                         LevelReader level, float radius, CallbackInfo ci) {
        if (PvpVisuals.hideShadows) ci.cancel();
    }
}
