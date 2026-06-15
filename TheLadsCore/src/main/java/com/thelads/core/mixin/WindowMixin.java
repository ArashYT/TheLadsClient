package com.thelads.core.mixin;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuBackend;
import com.thelads.core.client.LadsEarlyWindow;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public abstract class WindowMixin {

    /**
     * Adopt the black preLaunch window instead of creating a second one.
     * HEAD-cancel (not a redirect) so GLFW-threading mods that redirect the
     * glfwCreateWindow call site keep working.
     */
    @Inject(method = "createGlfwWindow", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ladsAdoptEarlyWindow(int width, int height, String title, long monitor, GpuBackend backend,
                                             CallbackInfoReturnable<Long> cir) {
        long early = LadsEarlyWindow.takeOver(width, height, monitor);
        if (early != 0L) cir.setReturnValue(early);
    }

    /**
     * If adoption didn't happen (early window disabled or failed), paint the
     * freshly created vanilla window black before it can flash white.
     */
    @Inject(method = "createGlfwWindow", at = @At("RETURN"), require = 0)
    private static void ladsPaintNewWindowBlack(int width, int height, String title, long monitor, GpuBackend backend,
                                                CallbackInfoReturnable<Long> cir) {
        LadsEarlyWindow.paintBlackIfForeign(cir.getReturnValueJ());
    }

    /**
     * The window definitely exists once the ctor finishes — if the early window
     * wasn't adopted by now (adoption disabled/failed), clean it up right here
     * on the correct thread.
     */
    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void ladsCleanupUnadopted(CallbackInfo ci) {
        LadsEarlyWindow.abandonIfUnused();
    }

    /** Keep the branded lads icon instead of vanilla's. */
    @Inject(method = "setIcon", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsKeepBrandedIcon(PackResources packResources, IconSet iconSet, CallbackInfo ci) {
        if (LadsEarlyWindow.isIconApplied()) ci.cancel();
    }

    /** Window title is always "The Lads Client". */
    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true, require = 0)
    private String ladsBrandTitle(String title) {
        return "The Lads Client";
    }
}
