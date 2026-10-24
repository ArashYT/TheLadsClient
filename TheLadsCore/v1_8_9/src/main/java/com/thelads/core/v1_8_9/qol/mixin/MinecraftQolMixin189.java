package com.thelads.core.v1_8_9.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.CrashCatcher;
import com.thelads.core.client.KeepCursor;
import com.thelads.core.v1_8_9.qol.Qol189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crash Catcher (runGameLoop's call to runTick(): the game tick only, never a frame; refreshResources marks a resource reload, which
 * runs inside the tick on 1.8.9 and is never caught) and Keep Cursor Position (displayGuiScreen: a container screen closing, then
 * one opening). The tick hook is require = 0 so a mod that rewrites runGameLoop cannot stop the game from starting: Qol189 then
 * reports Crash Catcher as unavailable.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftQolMixin189 {
    @Shadow public GuiScreen currentScreen;

    @WrapOperation(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick()V"), require = 0)
    private void ladsCatchCrash(Minecraft mc, Operation<Void> original) {
        Qol189.tickHooked();
        try {
            original.call(mc);
        } catch (RuntimeException error) {
            if (!Qol189.crashed(mc, error)) throw error;
        }
    }

    @Inject(method = "refreshResources", at = @At("HEAD"))
    private void ladsReloadStart(CallbackInfo ci) {
        CrashCatcher.reloading = true;
    }

    @Inject(method = "refreshResources", at = @At("RETURN"))
    private void ladsReloadEnd(CallbackInfo ci) {
        CrashCatcher.reloading = false;
    }

    @Inject(method = "displayGuiScreen", at = @At("HEAD"))
    private void ladsContainerClosing(GuiScreen next, CallbackInfo ci) {
        if (currentScreen instanceof GuiContainer && next != currentScreen) KeepCursor.closed(Mouse.getX(), Mouse.getY(), System.nanoTime());
    }

    @Inject(method = "displayGuiScreen", at = @At("TAIL"))
    private void ladsContainerOpened(GuiScreen next, CallbackInfo ci) {
        if (currentScreen instanceof GuiContainer) Qol189.restoreCursor();
    }
}
