package com.thelads.core.mixin.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ToggleNametagsModule;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderer.class)
public class NametagRenderMixin {

    // Modify the background color variable (opacity) to 0 if disabled
    @ModifyVariable(method = "renderNameTag", at = @At("STORE"), ordinal = 0, require = 0)
    private int ladsModifyNametagBackground(int originalColor) {
        if (ToggleNametagsModule.isBackgroundDisabled()) {
            return 0; // Transparent
        }
        return originalColor;
    }

    // Redirect the drawInBatch dropShadow boolean
    @ModifyArg(
        method = "renderNameTag",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font;II)I"),
        index = 4,
        require = 0
    )
    private boolean ladsModifyNametagShadow(boolean originalShadow) {
        if (ToggleNametagsModule.isShadowEnabled()) {
            return true;
        }
        return originalShadow;
    }
}
