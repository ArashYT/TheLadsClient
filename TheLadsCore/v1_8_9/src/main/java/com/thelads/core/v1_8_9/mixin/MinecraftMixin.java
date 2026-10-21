package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Borderless189;
import com.thelads.core.v1_8_9.feature.FrameWork189;
import com.thelads.core.v1_8_9.feature.ItemPhysics189;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.DynamicFPS189;
import com.thelads.core.v1_8_9.feature.WorldBackup189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.world.WorldSettings;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** F11 and the fullscreen option: borderless with the BorderlessFullscreen module, and a window that stays resizable after fullscreen.
 * Opening a world a newer version saved asks for a backup first (WorldBackup189).
 * 1.7 Animations: the use key blocks, draws a bow or eats while the attack key mines a block, as in 1.7 (1.8 waits for the mining).
 * Item Physics: with right-click pickup on, the use key on a dropped item picks it up.
 * Every attack click swings the arm, even when vanilla refuses (OldAnimations189.swingAnyway).
 * The pause menu without a world (after a join from its server list left it) is the main menu, as no screen is.
 * QA (Probe173Perf): each frame's work up to Display.update (FrameWork189). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /** Pylon: a resize Lads' own input pumps saw (PylonWindow189.afterPump) still resizes the game. */
    @Redirect(method = "checkWindowResize", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;wasResized()Z", remap = false))
    private boolean lads$wasResized() {
        return com.thelads.core.v1_8_9.feature.PylonWindow189.wasResized();
    }

    private static final long TIMER_RES = org.lwjgl.Sys.getTimerResolution();

    /** Cache the immutable timer resolution to eliminate redundant JNI calls every getSystemTime invocation. */
    @Redirect(method = "getSystemTime", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/Sys;getTimerResolution()J", remap = false), require = 1, allow = 1)
    private static long ladsCachedTimerResolution() {
        return TIMER_RES;
    }

    @Shadow private boolean fullscreen;
    @Shadow public EntityPlayerSP thePlayer;
    @Shadow private int rightClickDelayTimer;
    @Shadow public net.minecraft.client.multiplayer.WorldClient theWorld;
    @Shadow public net.minecraft.client.gui.GuiScreen currentScreen;
    @Shadow public net.minecraft.client.settings.GameSettings gameSettings;
    @Shadow public net.minecraft.profiler.Profiler mcProfiler;
    @Shadow public boolean skipRenderWorld;
    @Shadow public abstract void resize(int width, int height);

    /** Dynamic FPS: vanilla's limit (30 in a menu without a world, else the option), lowered in the background states (DynamicFPS189). */
    @Inject(method = "getLimitFramerate", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsUnfocusedCap(CallbackInfoReturnable<Integer> cir) {
        int vanilla = theWorld == null && currentScreen != null ? 30 : gameSettings.limitFramerate;
        int limit = DynamicFPS189.limit(vanilla);
        if (limit != vanilla) cir.setReturnValue(limit);
    }

    @Redirect(method = "runGameLoop", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;skipRenderWorld:Z"), require = 1)
    private boolean ladsSkipWorldRender(Minecraft mc) {
        return DynamicFPS189.shouldSkipWorldRender(mc.skipRenderWorld);
    }

    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsBorderless(CallbackInfo ci) {
        if (!Borderless189.active() && (fullscreen || !Borderless189.enabled())) return;
        fullscreen = Borderless189.toggle((Minecraft) (Object) this);
        ci.cancel();
    }

    @Inject(method = "toggleFullscreen", at = @At("TAIL"), require = 1)
    private void ladsResizable(CallbackInfo ci) {
        if (!fullscreen) {
            Borderless189.resizable();
            resize(Display.getWidth(), Display.getHeight());
        }
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

    /** After vanilla's clickMouse (which does nothing for 10 ticks after a miss): restarts the swing under vanilla's halfway rule, so spam clicking swings at vanilla speed. */
    @Inject(method = "clickMouse", at = @At("RETURN"), require = 1)
    private void ladsSwingAnyway(CallbackInfo ci) {
        OldAnimations189.swingAnyway(thePlayer);
    }

    @ModifyVariable(method = "displayGuiScreen", at = @At("HEAD"), argsOnly = true, require = 1)
    private net.minecraft.client.gui.GuiScreen ladsNoPauseWithoutWorld(net.minecraft.client.gui.GuiScreen screen) {
        return screen instanceof net.minecraft.client.gui.GuiIngameMenu && theWorld == null ? new net.minecraft.client.gui.GuiMainMenu() : screen;
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"), require = 0)
    private void ladsFrameStart(CallbackInfo ci) {
        FrameWork189.start();
        if (com.thelads.core.v1_8_9.feature.Probe173Perf.profilingRequested) {
            if (mcProfiler != null && !mcProfiler.profilingEnabled) mcProfiler.profilingEnabled = true;
        } else if (mcProfiler != null && mcProfiler.profilingEnabled && (gameSettings == null || !gameSettings.showDebugProfilerChart)) {
            mcProfiler.profilingEnabled = false;
        }
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateDisplay()V"), require = 0)
    private void ladsFrameWork(CallbackInfo ci) {
        FrameWork189.end();
    }

    @Inject(method = "launchIntegratedServer", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsWorldBackup(String folder, String name, WorldSettings settings, CallbackInfo ci) {
        if (WorldBackup189.intercept((Minecraft) (Object) this, folder, name, settings)) ci.cancel();
    }

    @Inject(method = "refreshResources", at = @At("HEAD"))
    private void ladsBeforeRefreshResources(CallbackInfo ci) {
        com.thelads.core.client.pack.PackAssetMask.onReloadStart();
    }

    @Inject(method = "refreshResources", at = @At("RETURN"))
    private void ladsAfterRefreshResources(CallbackInfo ci) {
        com.thelads.core.client.pack.PackAssetMask.endReload();
    }
}
