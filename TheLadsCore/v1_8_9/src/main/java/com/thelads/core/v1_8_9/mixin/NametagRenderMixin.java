package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Nametags189;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nametags background and Text Shadow on the standing name tag (Render.renderLivingLabel). There is no Forge event inside
 * the label, and cancelling RenderLivingEvent.Specials would also drop Essential's icon, which Essential injects here.
 * OptiFine patches this class: require = 0 leaves vanilla's tag if an injection point ever moves.
 */
@Mixin(Render.class)
public abstract class NametagRenderMixin {
    @Shadow public abstract FontRenderer getFontRendererFromRenderManager();

    @ModifyArg(method = "renderLivingLabel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/WorldRenderer;color(FFFF)Lnet/minecraft/client/renderer/WorldRenderer;"), index = 3, require = 0)
    private float ladsBackground(float alpha) {
        return Nametags189.background() ? alpha : 0;
    }

    /** A shadowed draw just before vanilla's normal pass, which redraws the same text on top; the see-through pass stays flat. */
    @Inject(method = "renderLivingLabel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 1), require = 0)
    private void ladsShadow(Entity entity, String str, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        if (!Nametags189.shadow()) return;
        FontRenderer font = getFontRendererFromRenderManager();
        font.drawString(str, -font.getStringWidth(str) / 2, "deadmau5".equals(str) ? -10 : 0, -1, true);
    }
}
