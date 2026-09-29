package com.thelads.core.v1_21_11.mixin;


import com.thelads.core.client.hud.HudManager;
import com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"), require = 1)
    private void onRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.thelads.core.v1_21_11.gui.DraggableHudScreen12111) return;

        GuiGraphicsLadsAdapter adapter = new GuiGraphicsLadsAdapter(guiGraphics, mc.font);
        HudManager.getInstance().render(adapter);
    }
}
