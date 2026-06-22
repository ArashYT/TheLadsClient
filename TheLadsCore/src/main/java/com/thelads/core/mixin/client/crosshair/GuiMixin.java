package com.thelads.core.mixin.client.crosshair;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.CrosshairModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class GuiMixin {

    @Inject(method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
    private void renderCustomCrosshair(GuiGraphicsExtractor g, CallbackInfo ci) {
        CrosshairModule m = (CrosshairModule) ModuleManager.getInstance().getModule("Crosshair Tweaks");
        if (m == null || !m.isEnabled()) return;
        
        ci.cancel();

        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        
        int x = width / 2;
        int y = height / 2;

        float scale = (float) m.scale.get();
        float thickness = (float) m.thickness.get();
        int color = m.color.getColor();

        int length = (int) (5 * scale); // 5 is base length
        int t = Math.max(1, (int) thickness);

        int halfT = t / 2;
        int restT = t - halfT;
        
        // Draw horizontal line
        g.fill(x - length, y - halfT, x + length + 1, y + restT, color);
        // Draw vertical line
        g.fill(x - halfT, y - length, x + restT, y + length + 1, color);
    }
}
