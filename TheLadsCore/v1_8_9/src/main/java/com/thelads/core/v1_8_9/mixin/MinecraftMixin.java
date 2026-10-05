package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Borderless189;
import com.thelads.core.v1_8_9.feature.FrameWork189;
import com.thelads.core.v1_8_9.feature.ItemPhysics189;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.UnfocusedFpsCap189;
import com.thelads.core.v1_8_9.feature.WorldBackup189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.world.WorldSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** F11 and the fullscreen option: borderless with the BorderlessFullscreen module, and a window that stays resizable after fullscreen.
 * Opening a world a newer version saved asks for a backup first (WorldBackup189).
 * 1.7 Animations: the use key blocks, draws a bow or eats while the attack key mines a block, as in 1.7 (1.8 waits for the mining).
 * Item Physics: with right-click pickup on, the use key on a dropped item picks it up.
 * QA (Probe173Perf): each frame's work up to Display.update (FrameWork189). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow private boolean fullscreen;
    @Shadow public EntityPlayerSP thePlayer;
    @Shadow private int rightClickDelayTimer;
    @Shadow public net.minecraft.client.multiplayer.WorldClient theWorld;
    @Shadow public net.minecraft.client.gui.GuiScreen currentScreen;
    @Shadow public net.minecraft.client.settings.GameSettings gameSettings;

    /** Unfocused FPS cap: vanilla's limit (30 in a menu without a world, else the option), lowered while the window is not focused. */
    @Inject(method = "getLimitFramerate", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsUnfocusedCap(CallbackInfoReturnable<Integer> cir) {
        int vanilla = theWorld == null && currentScreen != null ? 30 : gameSettings.limitFramerate;
        int limit = UnfocusedFpsCap189.limit(vanilla);
        if (limit != vanilla) cir.setReturnValue(limit);
    }

    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsBorderless(CallbackInfo ci) {
        if (!Borderless189.active() && (fullscreen || !Borderless189.enabled())) return;
        fullscreen = Borderless189.toggle((Minecraft) (Object) this);
        ci.cancel();
    }

    @Inject(method = "toggleFullscreen", at = @At("TAIL"), require = 1)
    private void ladsResizable(CallbackInfo ci) {
        if (!fullscreen) Borderless189.resizable();
    }

    @Redirect(method = "rightClickMouse", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;getIsHittingBlock()Z"), require = 1, allow = 1)
    private boolean ladsUseWhileMining(PlayerControllerMP controller) {
        return controller.getIsHittingBlock() && !OldAnimations189.useWhileMining(thePlayer.getHeldItem());
    }

    @Inject(method = "rightClickMouse", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsRightClickPickup(CallbackInfo ci) {
        if (!ItemPhysics189.pickUp((Minecraft) (Object) this)) return;
        rightClickDelayTimer = 4;
        ci.cancel();
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"), require = 0)
    private void ladsFrameStart(CallbackInfo ci) {
        FrameWork189.start();
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateDisplay()V"), require = 0)
    private void ladsFrameWork(CallbackInfo ci) {
        FrameWork189.end();
    }

    @Inject(method = "launchIntegratedServer", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsWorldBackup(String folder, String name, WorldSettings settings, CallbackInfo ci) {
        if (WorldBackup189.intercept((Minecraft) (Object) this, folder, name, settings)) ci.cancel();
    }
}
