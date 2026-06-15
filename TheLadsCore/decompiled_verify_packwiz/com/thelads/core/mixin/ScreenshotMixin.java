/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Screenshot
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin;

import com.thelads.core.client.ScreenshotMeta;
import java.io.File;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Screenshot.class})
public class ScreenshotMixin {
    @Inject(method={"getFile"}, at={@At(value="RETURN")}, require=0)
    private static void ladsWriteMeta(File picDir, CallbackInfoReturnable<File> cir) {
        File png = (File)cir.getReturnValue();
        if (png != null) {
            ScreenshotMeta.write(png);
        }
    }
}

