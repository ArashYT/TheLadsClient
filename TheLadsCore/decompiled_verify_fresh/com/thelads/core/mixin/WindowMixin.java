/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.IconSet
 *  com.mojang.blaze3d.platform.Window
 *  com.mojang.blaze3d.systems.GpuBackend
 *  net.minecraft.server.packs.PackResources
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.ModifyVariable
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
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

@Mixin(value={Window.class})
public abstract class WindowMixin {
    @Inject(method={"createGlfwWindow"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private static void ladsAdoptEarlyWindow(int width, int height, String title, long monitor, GpuBackend backend, CallbackInfoReturnable<Long> cir) {
        long early = LadsEarlyWindow.takeOver(width, height, monitor);
        if (early != 0L) {
            cir.setReturnValue((Object)early);
        }
    }

    @Inject(method={"createGlfwWindow"}, at={@At(value="RETURN")}, require=0)
    private static void ladsPaintNewWindowBlack(int width, int height, String title, long monitor, GpuBackend backend, CallbackInfoReturnable<Long> cir) {
        LadsEarlyWindow.paintBlackIfForeign(cir.getReturnValueJ());
    }

    @Inject(method={"<init>"}, at={@At(value="TAIL")}, require=0)
    private void ladsCleanupUnadopted(CallbackInfo ci) {
        LadsEarlyWindow.abandonIfUnused();
    }

    @Inject(method={"setIcon"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void ladsKeepBrandedIcon(PackResources packResources, IconSet iconSet, CallbackInfo ci) {
        if (LadsEarlyWindow.isIconApplied()) {
            ci.cancel();
        }
    }

    @ModifyVariable(method={"setTitle"}, at=@At(value="HEAD"), argsOnly=true, require=0)
    private String ladsBrandTitle(String title) {
        return "The Lads Client";
    }
}

