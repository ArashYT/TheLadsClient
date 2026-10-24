package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.modules.FreelookModule;
import net.minecraft.client.renderer.entity.RenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook on 1.8.9: name tags and other billboards face Freelook's camera (the view angles RenderManager caches every frame). */
@Mixin(RenderManager.class)
public abstract class FreelookRenderManagerMixin189 {
    @Shadow public float playerViewY;
    @Shadow public float playerViewX;

    @Inject(method = "cacheActiveRenderInfo", at = @At("TAIL"), require = 1)
    private void lads$freelookView(CallbackInfo ci) {
        FreelookModule look = FreelookModule.get();
        if (!look.detached()) return;
        playerViewY = look.yaw();
        playerViewX = look.pitch();
    }
}
