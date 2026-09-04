package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class GuiMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void onExtractRenderState(GuiGraphicsExtractor g, DeltaTracker tickDelta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            CpsTracker.get().tick(mc.options.keyAttack.isDown(), mc.options.keyUse.isDown());
        }
        GuiGraphicsExtractorLadsAdapter adapter = new GuiGraphicsExtractorLadsAdapter(g, mc.font);
        HudManager.getInstance().render(adapter);
    }
}
