package com.thelads.core.mixin;

import com.thelads.core.client.ScreenshotMeta;
import net.minecraft.client.Screenshot;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

/**
 * When a screenshot filename is chosen, write a metadata sidecar next to it.
 * require = 0 so a mapping change just skips the sidecar instead of crashing.
 */
@Mixin(Screenshot.class)
public class ScreenshotMixin {
    @Inject(method = "getFile", at = @At("RETURN"), require = 0)
    private static void ladsWriteMeta(File picDir, CallbackInfoReturnable<File> cir) {
        File png = cir.getReturnValue();
        if (png != null) {
            ScreenshotMeta.write(png);
            com.thelads.core.features.decentscreenshot.InteractiveScreenshotWidget.lastScreenshot = png;
            com.thelads.core.features.decentscreenshot.InteractiveScreenshotWidget.screenshotTime = System.currentTimeMillis();
            try {
                Object mc = Minecraft.getInstance();
                Object toastManager = null;
                for (java.lang.reflect.Method m : mc.getClass().getMethods()) {
                    if (m.getReturnType().getSimpleName().equals("ToastManager") && m.getParameterCount() == 0) {
                        toastManager = m.invoke(mc);
                        break;
                    }
                }
                if (toastManager == null) {
                    for (java.lang.reflect.Field f : mc.getClass().getDeclaredFields()) {
                        if (f.getType().getSimpleName().equals("ToastManager")) {
                            f.setAccessible(true);
                            toastManager = f.get(mc);
                            break;
                        }
                    }
                }
                if (toastManager != null) {
                    java.lang.reflect.Method addMethod = null;
                    for (java.lang.reflect.Method m : toastManager.getClass().getMethods()) {
                        if (m.getParameterCount() == 1 && m.getParameterTypes()[0].getSimpleName().equals("Toast")) {
                            addMethod = m;
                            break;
                        }
                    }
                    if (addMethod != null) {
                        addMethod.invoke(toastManager, new com.thelads.core.features.decentscreenshot.PolaroidScreenshotToast(png));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
