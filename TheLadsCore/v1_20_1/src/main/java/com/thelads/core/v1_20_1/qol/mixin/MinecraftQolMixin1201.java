package com.thelads.core.v1_20_1.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.KeepCursor;
import com.thelads.core.v1_20_1.qol.Qol1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crash Catcher (runTick's call to tick(): the game tick only, never a frame), Keep Cursor Position (setScreen: a container screen
 * closing, then one opening) and GUI Scale (the scale resizeDisplay hands the window).
 */
@Mixin(Minecraft.class)
abstract class MinecraftQolMixin1201 {
    @Shadow public Screen screen;
    @Shadow @Final public MouseHandler mouseHandler;

    /** require = 0: a pack mod that rewrites runTick (Forge + Connector) cannot stop the game from starting; Qol1201 then reports it. */
    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V"), require = 0)
    private void lads$catchCrash(Minecraft mc, Operation<Void> original) {
        Qol1201.tickHooked();
        try {
            original.call(mc);
        } catch (RuntimeException error) {
            if (!Qol1201.crashed(mc, error)) throw error;
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void lads$containerClosing(Screen next, CallbackInfo ci) {
        if (screen instanceof AbstractContainerScreen<?> && next != screen) KeepCursor.closed(mouseHandler.xpos(), mouseHandler.ypos(), System.nanoTime());
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void lads$containerOpened(Screen next, CallbackInfo ci) {
        if (screen instanceof AbstractContainerScreen<?>) Qol1201.restoreCursor((Minecraft) (Object) this);
    }

    @ModifyArg(method = "resizeDisplay", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;setGuiScale(D)V"))
    private double lads$guiScale(double vanilla) {
        return Qol1201.guiScale(vanilla);
    }
}
