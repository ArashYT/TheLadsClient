package com.thelads.core.v1_21_1.feature.qa.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The world FOV the renderer uses, including ZoomMixin's factor (QA reads it to prove Zoom changes the real view). */
@Mixin(GameRenderer.class)
public interface GameRendererQaInvoker {
    @Invoker("getFov") double ladsQaFov(Camera camera, float partialTick, boolean useFovSetting);
}
