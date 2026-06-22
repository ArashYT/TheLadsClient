package com.thelads.core.client.hud;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@Mixin(Hud.class)
public class GuiMixin {
    @Inject(method = "render", at = @At("TAIL"))
    public void onRender(GuiGraphicsExtractor g, float tickDelta, CallbackInfo ci) {
    }
}
