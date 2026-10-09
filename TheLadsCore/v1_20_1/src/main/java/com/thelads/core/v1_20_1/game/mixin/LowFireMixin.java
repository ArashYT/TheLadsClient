package com.thelads.core.v1_20_1.game.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.OldAnimationsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class LowFireMixin {
    @Inject(method = "renderFire(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"))
    private static void ladsLowFire(Minecraft minecraft, PoseStack poseStack, CallbackInfo ci) {
        Module mod = ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
        if (mod instanceof OldAnimationsModule oldAnims && oldAnims.isEnabled()
                && oldAnims.active(OldAnimationsModule.Feature.LOW_FIRE, OldAnimationsModule.Platform.MODERN)) {
            poseStack.translate(0.0f, -0.25f, 0.0f);
        }
    }
}
