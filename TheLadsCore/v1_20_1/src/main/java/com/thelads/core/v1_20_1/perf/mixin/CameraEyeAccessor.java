package com.thelads.core.v1_20_1.perf.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The camera's eye height at the last two ticks (SkyFogColor1201: where the frames' camera is). */
@Mixin(Camera.class)
public interface CameraEyeAccessor {
    @Accessor("eyeHeight")
    float lads$eyeHeight();

    @Accessor("eyeHeightOld")
    float lads$eyeHeightOld();
}
