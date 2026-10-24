package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.modules.GlintColorizerModule;
import com.thelads.core.v1_8_9.feature.PvpVisuals189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Glint Colorizer on items: renderEffect's glint colour (-8372020, both layers) and the clock its two layers scroll by. */
@Mixin(RenderItem.class)
public class PvpGlintItemMixin189 {
    @ModifyConstant(method = "renderEffect", constant = @Constant(intValue = -8372020), require = 0)
    private int lads$glintColor(int color) {
        GlintColorizerModule glint = PvpVisuals.GLINT;
        return glint.on ? glint.vertexColor : color;
    }

    @Redirect(method = "renderEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSystemTime()J"), require = 0)
    private long lads$glintClock() {
        return PvpVisuals189.glintMillis(Minecraft.getSystemTime());
    }
}
