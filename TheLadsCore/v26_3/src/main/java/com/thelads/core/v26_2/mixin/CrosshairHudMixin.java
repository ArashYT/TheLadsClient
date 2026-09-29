package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.crosshair.NativeCrosshair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class CrosshairHudMixin {
    @Inject(method="extractCrosshair",at=@At("HEAD"),cancellable=true,require=1)
    private void lads$crosshair(GuiGraphicsExtractor graphics,DeltaTracker delta,CallbackInfo callback){
        if(NativeCrosshair.active()){NativeCrosshair.extract(graphics,delta);callback.cancel();}
    }
    @Inject(method="extractRenderState",at=@At("TAIL"),require=1)
    private void lads$hiddenHudCrosshair(GuiGraphicsExtractor graphics,DeltaTracker delta,CallbackInfo callback){
        if(Minecraft.getInstance().gui.hud.isHidden()&&NativeCrosshair.active()&&NativeCrosshair.flag("Visible with Hidden HUD")&&!(Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen))NativeCrosshair.extract(graphics,delta);
    }
}
