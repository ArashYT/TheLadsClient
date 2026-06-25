package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("mainCamera")
    void setMainCamera(net.minecraft.client.Camera mainCamera);
}
