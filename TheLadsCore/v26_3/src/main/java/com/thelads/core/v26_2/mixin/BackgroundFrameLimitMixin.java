package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.thelads.core.v26_2.feature.BackgroundFrameLimit;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public class BackgroundFrameLimitMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private int framerateLimit;
    @Unique private final BackgroundFrameLimit lads$backgroundLimit = new BackgroundFrameLimit();

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 1)
    private void lads$limit(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(lads$backgroundLimit.apply(callback.getReturnValueI(), framerateLimit,
            NativeQualityOfLife.enabled("DynamicFPS"), NativeQualityOfLife.choice("DynamicFPS", "Mode", 0),
            minecraft.isWindowActive(), minecraft.getWindow().isIconified(),
            (int) NativeQualityOfLife.number("DynamicFPS", "Unfocused FPS", 15),
            (int) NativeQualityOfLife.number("DynamicFPS", "Hidden FPS", 5), System.nanoTime()));
    }
}
