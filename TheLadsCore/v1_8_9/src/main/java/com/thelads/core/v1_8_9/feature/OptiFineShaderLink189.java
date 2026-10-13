package com.thelads.core.v1_8_9.feature;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Automatically disables OptiFine's Fast Render when a shader pack is switched ON, and restores Fast Render back ON
 * when shaders are switched back OFF (only if we disabled it). Better Resolution holds it off the same way while it scales the
 * world: OptiFine turns framebuffers off under Fast Render, which would leave Better Resolution doing nothing.
 */
public final class OptiFineShaderLink189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static boolean disabledByUs = false;
    private static boolean shadersActive = false;
    /** Better Resolution is scaling the world and needs framebuffers (RenderScale189). */
    private static boolean scaleHolds = false;

    private OptiFineShaderLink189() {}

    public static boolean isShaderPack(String pack) {
        return pack != null && !pack.trim().isEmpty() && !"OFF".equalsIgnoreCase(pack.trim());
    }

    public static boolean isDisabledByUs() {
        return disabledByUs;
    }

    public static void setDisabledByUs(boolean value) {
        disabledByUs = value;
    }

    public static boolean isShadersActive() {
        return shadersActive;
    }

    public static void setShadersActive(boolean active) {
        shadersActive = active;
    }

    public static void onSetShaderPack(String shaderPack) {
        boolean wantsShaders = isShaderPack(shaderPack);
        LOG.info("OptiFine onSetShaderPack called with '{}' (wantsShaders={})", shaderPack, wantsShaders);
        if (wantsShaders) {
            if (!shadersActive) {
                if (isFastRenderOn()) {
                    disabledByUs = true;
                    setFastRender(false);
                    LOG.info("OptiFine Fast Render disabled automatically for shader pack");
                } else {
                    disabledByUs = false;
                }
            }
            shadersActive = true;
        } else {
            if (disabledByUs && !scaleHolds) {
                disabledByUs = false;
                setFastRender(true);
                LOG.info("OptiFine Fast Render restored automatically after shader pack disabled");
            }
            shadersActive = false;
        }
    }

    /** RenderScale189, when Better Resolution starts or stops wanting a non-native scale. */
    public static void betterResolution(boolean scaling) {
        if (scaling == scaleHolds) return;
        scaleHolds = scaling;
        if (scaling) {
            if (isFastRenderOn()) {
                disabledByUs = true;
                setFastRender(false);
                LOG.info("OptiFine Fast Render disabled automatically for Better Resolution");
            }
        } else if (disabledByUs && !shadersActive) {
            disabledByUs = false;
            setFastRender(true);
            LOG.info("OptiFine Fast Render restored automatically after Better Resolution");
        }
    }

    public static boolean isFastRenderOn() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.gameSettings == null) return false;
            Field f = getFastRenderField(mc.gameSettings.getClass());
            return f != null && f.getBoolean(mc.gameSettings);
        } catch (Throwable t) {
            LOG.warn("Could not read ofFastRender from GameSettings", t);
            return false;
        }
    }

    public static void setFastRender(boolean on) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.gameSettings == null) return;
            Field f = getFastRenderField(mc.gameSettings.getClass());
            if (f != null) {
                f.setBoolean(mc.gameSettings, on);
                try {
                    Method save = mc.gameSettings.getClass().getMethod("saveOfOptions");
                    save.invoke(mc.gameSettings);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            LOG.warn("Could not set ofFastRender on GameSettings", t);
        }
    }

    private static Field getFastRenderField(Class<?> cls) {
        try {
            return cls.getField("ofFastRender");
        } catch (NoSuchFieldException e) {
            try {
                Field f = cls.getDeclaredField("ofFastRender");
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
