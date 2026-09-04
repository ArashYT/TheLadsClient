package com.thelads.core.v1_21_1.mixin;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.v1_21_1.adapter.GuiGraphicsLadsAdapter;
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

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void onRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            CpsTracker.get().tick(mc.options.keyAttack.isDown(), mc.options.keyUse.isDown());
        }
        GuiGraphicsLadsAdapter adapter = new GuiGraphicsLadsAdapter(guiGraphics, mc.font);
        HudManager.getInstance().render(adapter);
    }
}
