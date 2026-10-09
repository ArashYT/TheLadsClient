package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.modules.CustomFovModule;
import com.thelads.core.v1_20_1.game.NativeCustomFov1201;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Custom FOV: the narrower FOV with the camera in water or lava, kept at its share. */
@Mixin(GameRenderer.class)
public class CustomFovCameraMixin {
    @ModifyConstant(method = "getFov", constant = @Constant(doubleValue = 0.8571428656578064))
    private double lads$underwater(double change) { return NativeCustomFov1201.scaled(change, CustomFovModule.UNDERWATER); }
}
