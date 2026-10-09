package com.thelads.core.v1_20_1.hud;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.RaisedModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the 1.8.5 HUD / chat / overlay / tooltip modules on 1.20.1 (plain Fabric and Zero SMP's Forge + Connector).
 * Every group registers itself from here; TheLadsCoreClient1201 calls {@link #register()} once.
 */
public final class Hud1201 {
    public static final Logger LOG = LoggerFactory.getLogger("TheLadsCore-1.20.1-hud");
    private static boolean registered;

    private Hud1201() {}

    public static void register() {
        if (registered) return;
        registered = true;
        // Modules the entry point lists as built in that only work with another mod, or have an upstream mod installed.
        if (Mods1201.loaded("raised")) ModuleSupport.registerExternal(RaisedModule.NAME, "Raised", "raised", true);
        for (String voice : new String[] {"Voice Chat", "Voice Chat Group"})
            if (VoiceChat1201.loaded()) ModuleSupport.registerBuiltIn(voice);
            else ModuleSupport.registerUnavailable(voice, "Simple Voice Chat is not installed in this game.");
        if (!Mods1201.loaded("xaerominimap")) ModuleSupport.registerUnavailable("Minimap", "Xaero's Minimap is not installed in this game.");
        NativeBetterF3_1201.register();
        ForgeOverlays1201.register();
        // One line per group; each group owns its class, package and mixin config.
        com.thelads.core.v1_20_1.hud.chat.ChatFeatures1201.register();
        com.thelads.core.v1_20_1.hud.overlay.OverlayFeatures1201.register();
        com.thelads.core.v1_20_1.hud.items.ItemFeatures1201.register();
        com.thelads.core.v1_20_1.hud.jade.JadeFeatures1201.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private static void tick() {
        Minimap1201.tick();
    }

    public static Module module(String name) { return ModuleManager.getInstance().getModule(name); }

    public static boolean enabled(String name) {
        Module module = module(name);
        return module != null && module.isEnabled();
    }

    public static boolean bool(String module, String option, boolean fallback) {
        Module value = module(module);
        return value != null && value.getOption(option) instanceof BoolOption setting ? setting.get() : fallback;
    }

    public static int choice(String module, String option, int fallback) {
        Module value = module(module);
        return value != null && value.getOption(option) instanceof DropdownOption setting ? setting.getIndex() : fallback;
    }

    public static double number(String module, String option, double fallback) {
        Module value = module(module);
        return value != null && value.getOption(option) instanceof SliderOption setting ? setting.getValue() : fallback;
    }

    public static String string(String module, String option, String fallback) {
        Module value = module(module);
        return value != null && value.getOption(option) instanceof com.thelads.core.config.StringOption setting ? setting.get() : fallback;
    }
}
