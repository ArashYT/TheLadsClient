package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.DynamicLightsModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.settings.GameSettings;
import org.apache.logging.log4j.LogManager;

/**
 * Dynamic Lights on 1.8.9: the Lads module switches OptiFine's own Dynamic Lights (Off, Fast or Fancy) through the field OptiFine
 * adds to GameSettings, and Minecraft's options save writes it to OptiFine's optionsof.txt, as OptiFine's Video Settings do. A
 * change made there flows back into the module; at start-up the module takes on OptiFine's saved setting, so nobody's 1.8.9 lights
 * change by themselves. Without OptiFine the module is unavailable.
 */
public final class DynamicLights189 {
    private static Field setting;
    private static Method removeLights;
    private static int applied = -1;

    private DynamicLights189() {}

    /** After the other 1.8.9 module statuses: without OptiFine the module is unavailable. */
    public static void register() {
        try {
            setting = GameSettings.class.getField("ofDynamicLights");
        } catch (ReflectiveOperationException | LinkageError absent) {
            ModuleSupport.registerUnavailable(DynamicLightsModule.NAME, "On Minecraft 1.8.9 it switches OptiFine's Dynamic Lights, and OptiFine is not loaded.");
            return;
        }
        try { // what OptiFine's own menu calls when the setting changes, so lights that went out leave no lit blocks behind
            removeLights = Class.forName("net.optifine.DynamicLights").getMethod("removeLights", RenderGlobal.class);
        } catch (ReflectiveOperationException | LinkageError absent) {
            LogManager.getLogger("TheLadsCore").warn("OptiFine's DynamicLights.removeLights is missing; lights turned off may linger until blocks update");
        }
        module().drivesOptiFine(); // built in (TheLadsCore189.LIMITED says what OptiFine's lights lack)
    }

    /** Each client tick: a change on either side reaches the other. */
    public static void tick(Minecraft mc) {
        if (setting == null || mc.gameSettings == null) return;
        DynamicLightsModule module = module();
        try {
            int optiFine = setting.getInt(mc.gameSettings);
            if (optiFine != applied) { // start-up, or OptiFine's Video Settings
                if (module.optiFineSetting() != optiFine) {
                    module.followOptiFine(optiFine);
                    ConfigManager.save();
                }
            } else if (module.optiFineSetting() != optiFine) { // the Lads menu
                setting.setInt(mc.gameSettings, module.optiFineSetting());
                if (removeLights != null && mc.renderGlobal != null) removeLights.invoke(null, mc.renderGlobal);
                mc.gameSettings.saveOptions();
            }
            applied = setting.getInt(mc.gameSettings);
        } catch (ReflectiveOperationException failure) {
            LogManager.getLogger("TheLadsCore").error("Dynamic Lights cannot reach OptiFine's setting; the module stops driving it", failure);
            setting = null;
        }
    }

    static DynamicLightsModule module() { return (DynamicLightsModule) Options189.module(DynamicLightsModule.NAME); }
}
