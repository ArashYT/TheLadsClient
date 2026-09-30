package com.thelads.core.v1_21_1.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.v1_21_1.feature.NativeFeatures;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 1.21.1 Input has no sprint flag: aiStep's only KeyMapping.isDown() calls are its two options.keySprint reads (javap). */
@Mixin(LocalPlayer.class)
public class SprintInputMixin {
    @ModifyExpressionValue(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"), require = 1)
    private boolean ladsSprintInput(boolean down) { return NativeFeatures.sprintInput(down); }
}
