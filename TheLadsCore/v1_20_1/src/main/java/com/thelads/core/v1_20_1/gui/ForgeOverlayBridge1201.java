package com.thelads.core.v1_20_1.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.slf4j.LoggerFactory;

/**
 * Forge's loading overlay keeps two Forge-only objects (its early display window and the "Minecraft Progress" meter). The
 * Lads overlay (ForgeLoadingOverlayMixin) still has to close the window and finish the meter exactly as Forge would, but
 * the Fabric build cannot link against Forge, so it goes through reflection, resolved once.
 */
public final class ForgeOverlayBridge1201 {
    private static Field windowField, progressField;
    private static Method closeWindow, setAbsolute, complete;
    private static boolean resolved, failed;

    private ForgeOverlayBridge1201() {}

    private static boolean resolve(Object overlay) {
        if (resolved) return !failed;
        resolved = true;
        try {
            Class<?> type = overlay.getClass();
            while (type != null && !type.getName().equals("net.minecraftforge.client.loading.ForgeLoadingOverlay")) type = type.getSuperclass();
            if (type == null) throw new ClassNotFoundException("ForgeLoadingOverlay");
            windowField = type.getDeclaredField("displayWindow");
            progressField = type.getDeclaredField("progress");
            windowField.setAccessible(true);
            progressField.setAccessible(true);
            closeWindow = windowField.getType().getMethod("close");
            setAbsolute = progressField.getType().getMethod("setAbsolute", int.class);
            complete = progressField.getType().getMethod("complete");
        } catch (ReflectiveOperationException | LinkageError failure) {
            failed = true;
            LoggerFactory.getLogger("TheLadsCore").warn("Forge's loading overlay internals are not as expected; its own screen is kept", failure);
        }
        return !failed;
    }

    /** False when the internals could not be resolved: the Forge overlay then draws itself. */
    public static boolean ready(Object overlay) {
        return resolve(overlay);
    }

    public static void progress(Object overlay, float fraction) {
        try {
            setAbsolute.invoke(progressField.get(overlay), Math.max(0, Math.min(100, (int) (fraction * 100))));
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    public static void finishProgress(Object overlay) {
        try {
            complete.invoke(progressField.get(overlay));
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    public static void closeWindow(Object overlay) {
        try {
            closeWindow.invoke(windowField.get(overlay));
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
}
