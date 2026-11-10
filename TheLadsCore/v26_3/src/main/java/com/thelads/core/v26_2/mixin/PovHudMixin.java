package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.PovCamera;
import com.thelads.core.v26_2.feature.Spectate26;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** POV spectating: hotbar, hearts and bars come from our own (server-mirrored) player, so the whole hotbar shows. */
@Mixin(Hud.class)
public abstract class PovHudMixin {
    @Inject(method = "getCameraPlayer", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$povHudPlayer(CallbackInfoReturnable<Player> cir) {
        if (PovCamera.active()) cir.setReturnValue(Minecraft.getInstance().player);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("TAIL"), require = 1)
    private void lads$spectateHud(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        Spectate26.render(graphics);
    }
}
