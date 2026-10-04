package com.thelads.core.v26_2.mixin;

import com.thelads.core.modules.CustomFovModule;
import com.thelads.core.v26_2.feature.NativeCustomFov;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Custom FOV: the narrower FOV with the camera in water or lava, kept at its share. */
@Mixin(Camera.class)
public class CustomFovCameraMixin {
    @ModifyConstant(method = "modifyFovBasedOnDeathOrFluid", constant = @Constant(floatValue = 0.85714287F), require = 1)
    private float lads$underwater(float change) { return NativeCustomFov.scaled(change, CustomFovModule.UNDERWATER); }
}
