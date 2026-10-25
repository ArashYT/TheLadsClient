package com.thelads.core.v1_8_9.feature;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA probe for OptiFine defaults and shaders link:
 * - defaults (useVbo, ofFastRender, ofFastMath) are on in a fresh sandbox
 * - enabling a shader pack automatically turns Fast Render off
 * - disabling shaders turns Fast Render back on
 * - a user who turned Fast Render off themselves keeps it off
 */
public final class Probe192OfDefaults {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");

    private Probe192OfDefaults() {}

    public static void run(Minecraft mc) throws Exception {
        LOG.info("Running OptiFine defaults and shaders link probe checks");

        // 0. Video Settings > Fullscreen Mode reads OptiFine's desktop mode, null under Pylon until Lads fills it (PylonWindow189);
        //    it must be back to how OptiFine had it once Video Settings closes, or F11 goes exclusive instead of borderless
        java.lang.reflect.Field desktop = Class.forName("Config").getDeclaredField("desktopDisplayMode");
        desktop.setAccessible(true);
        Object before = desktop.get(null);
        PylonWindow189.OptiFineDesktopMode fill = new PylonWindow189.OptiFineDesktopMode();
        fill.open(new net.minecraftforge.client.event.GuiOpenEvent(new net.minecraft.client.gui.GuiVideoSettings(null, mc.gameSettings)));
        CoreProbe.check(Class.forName("Config").getMethod("getLargestDisplayMode").invoke(null) != null,
            "OptiFine has a desktop display mode in Video Settings (Fullscreen Mode option)");
        fill.open(new net.minecraftforge.client.event.GuiOpenEvent(null));
        CoreProbe.check(desktop.get(null) == before, "OptiFine's desktop mode is back after Video Settings (F11 stays borderless)");

        // 1. Defaults are on in a fresh sandbox (VBOs, Fast Render, Fast Math)
        boolean vbo = mc.gameSettings.useVbo;
        boolean fastRender = OptiFineShaderLink189.isFastRenderOn();
        boolean fastMath = isFastMathOn(mc);
        CoreProbe.check(vbo, "VBOs are on by default in fresh sandbox");
        CoreProbe.check(fastRender, "Fast Render is on by default in fresh sandbox");
        CoreProbe.check(fastMath, "Fast Math is on by default in fresh sandbox");

        // 2. Enabling a shader pack turns Fast Render off automatically
        callSetShaderPack("(internal)");
        CoreProbe.check(!OptiFineShaderLink189.isFastRenderOn(), "enabling a shader pack turns Fast Render off");
        CoreProbe.check(OptiFineShaderLink189.isDisabledByUs(), "OptiFineShaderLink remembered that we disabled Fast Render");

        // 3. Disabling turns it back on
        callSetShaderPack("OFF");
        CoreProbe.check(OptiFineShaderLink189.isFastRenderOn(), "disabling shaders turns Fast Render back on");
        CoreProbe.check(!OptiFineShaderLink189.isDisabledByUs(), "OptiFineShaderLink cleared disabledByUs flag");

        // 4. A user who turned Fast Render off themselves keeps it off
        OptiFineShaderLink189.setFastRender(false);
        CoreProbe.check(!OptiFineShaderLink189.isFastRenderOn(), "Fast Render set to off manually");
        CoreProbe.check(!OptiFineShaderLink189.isDisabledByUs(), "manual Fast Render off is not marked as disabledByUs");

        callSetShaderPack("(internal)");
        CoreProbe.check(!OptiFineShaderLink189.isFastRenderOn(), "Fast Render remains off when shaders turned on");
        CoreProbe.check(!OptiFineShaderLink189.isDisabledByUs(), "disabledByUs remains false since user had it off");

        callSetShaderPack("OFF");
        CoreProbe.check(!OptiFineShaderLink189.isFastRenderOn(), "disabling shaders keeps Fast Render off for user who turned it off themselves");

        // Restore Fast Render to on so subsequent tests/probes have default state
        OptiFineShaderLink189.setFastRender(true);
        LOG.info("OptiFine defaults and shaders link probe checks passed");
    }

    private static boolean isFastMathOn(Minecraft mc) {
        try {
            Field f = mc.gameSettings.getClass().getField("ofFastMath");
            return f.getBoolean(mc.gameSettings);
        } catch (Throwable t) {
            try {
                Class<?> mh = Class.forName("net.minecraft.util.MathHelper");
                Field f = mh.getField("fastMath");
                return f.getBoolean(null);
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private static void callSetShaderPack(String pack) {
        try {
            Class<?> shadersCls = Class.forName("net.optifine.shaders.Shaders");
            Method setPack = shadersCls.getMethod("setShaderPack", String.class);
            setPack.invoke(null, pack);
        } catch (Throwable t) {
            // Direct call fallback if Shaders class is not loaded / mock environment
            OptiFineShaderLink189.onSetShaderPack(pack);
        }
    }
}
