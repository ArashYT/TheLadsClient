package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.modules.GlintColorizerModule;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Glint Colorizer on armour: renderGlint's second colour (the glint, 76% of the item glint's) and its scroll, which moves with the
 * entity's age (ticksExisted + partial ticks) times the speed.
 */
@Mixin(LayerArmorBase.class)
public class PvpGlintArmourMixin189 {
    @Redirect(method = "renderGlint", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V", ordinal = 1),
        require = 0)
    private void lads$glintColor(float red, float green, float blue, float alpha) {
        GlintColorizerModule glint = PvpVisuals.GLINT;
        if (!glint.on) {
            GlStateManager.color(red, green, blue, alpha);
            return;
        }
        int c = glint.vertexColor;
        float k = GlintColorizerModule.ARMOUR / 255;
        GlStateManager.color((c >> 16 & 0xFF) * k, (c >> 8 & 0xFF) * k, (c & 0xFF) * k, alpha);
    }

    @ModifyArg(method = "renderGlint", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V"), index = 1,
        require = 0)
    private float lads$glintScroll(float y) {
        GlintColorizerModule glint = PvpVisuals.GLINT;
        return glint.on ? y * glint.speedFactor : y;
    }
}
