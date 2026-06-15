package com.thelads.core.mixin;

import com.thelads.core.modules.ToggleNametagsModule;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsToggleNametags(Entity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (ToggleNametagsModule.shouldHide(entity)) cir.setReturnValue(false);
    }
}
