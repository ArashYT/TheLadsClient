package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Food189;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.OldAnimations189.Hook;
import com.thelads.core.v1_8_9.feature.Raised189;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.7 Animations, No heart flashing. Forge draws the health bar in GuiIngameForge.renderHealth (OptiFine leaves it alone); its one
 * boolean local, "highlight", is the blink after damage (the lighter outline and the lost hearts). A Forge class: no remap (its
 * vanilla calls remap).
 * Raised: the held item name and the action bar, which Forge draws without an overlay event, move up with the hotbar.
 */
@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class GuiIngameForgeMixin {
    @ModifyVariable(method = "renderHealth", at = @At("STORE"), ordinal = 0, require = 1, allow = 1)
    private boolean ladsHeartsBlink(boolean highlight) {
        boolean shown = OldAnimations189.MODULE.heartsBlink(OldAnimations189.PLATFORM, highlight);
        if (shown != highlight) OldAnimations189.hit(Hook.HEARTS);
        return shown;
    }

    /** Food189 (the AppleSkin module): where Forge drew each heart and hunger icon, shake included, so previews sit on them. */
    @Redirect(method = "renderHealth", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/GuiIngameForge;drawTexturedModalRect(IIIIII)V", remap = true), require = 1)
    private void ladsHeartIcon(GuiIngameForge gui, int x, int y, int u, int v, int width, int height) {
        gui.drawTexturedModalRect(x, y, u, v, width, height);
        Food189.heartIcon(x, y);
    }

    @Redirect(method = "renderFood", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/GuiIngameForge;drawTexturedModalRect(IIIIII)V", remap = true), require = 1)
    private void ladsFoodIcon(GuiIngameForge gui, int x, int y, int u, int v, int width, int height) {
        gui.drawTexturedModalRect(x, y, u, v, width, height);
        Food189.foodIcon(x, y);
    }

    @Inject(method = {"renderToolHightlight", "renderRecordOverlay"}, at = @At("HEAD"), require = 2)
    private void ladsRaise(CallbackInfo ci) {
        Raised189.push();
    }

    @Inject(method = {"renderToolHightlight", "renderRecordOverlay"}, at = @At("RETURN"), require = 2)
    private void ladsLower(CallbackInfo ci) {
        GlStateManager.popMatrix();
    }
}
