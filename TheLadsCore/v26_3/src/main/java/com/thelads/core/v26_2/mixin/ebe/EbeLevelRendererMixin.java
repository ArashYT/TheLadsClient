package com.thelads.core.v26_2.mixin.ebe;

import com.thelads.core.client.ebe.BakedBlockEntities;
import com.thelads.core.v26_2.feature.ebe.BakedBlockEntitiesProbe263;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame counter for the baked/renderer hand-over. */
@Mixin(LevelRenderer.class)
public abstract class EbeLevelRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void lads$ebeFrame(CallbackInfo ci) {
        BakedBlockEntities.nextFrame();
        if (BakedBlockEntitiesProbe263.recording) BakedBlockEntitiesProbe263.frame();
    }
}
