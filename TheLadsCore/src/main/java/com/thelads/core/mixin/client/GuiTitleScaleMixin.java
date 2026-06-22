package com.thelads.core.mixin.client;

import com.thelads.core.modules.TitleScaleModule;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Hud.class)
public class GuiTitleScaleMixin {
    // The vanilla title rendering logic uses poseStack.scale(4.0F, 4.0F, 4.0F) for title
    // and poseStack.scale(2.0F, 2.0F, 2.0F) for subtitle.
    // We can multiply these constants by our custom scale factor.
    
    @ModifyArg(
        method = "extractTitle",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", ordinal = 0),
        index = 0,
        require = 0
    )
    private float modifyTitleScaleX(float original) {
        return original * TitleScaleModule.getScale();
    }

    @ModifyArg(
        method = "extractTitle",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", ordinal = 0),
        index = 1,
        require = 0
    )
    private float modifyTitleScaleY(float original) {
        return original * TitleScaleModule.getScale();
    }

    @ModifyArg(
        method = "extractTitle",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", ordinal = 0),
        index = 2,
        require = 0
    )
    private float modifyTitleScaleZ(float original) {
        return original * TitleScaleModule.getScale();
    }
}
