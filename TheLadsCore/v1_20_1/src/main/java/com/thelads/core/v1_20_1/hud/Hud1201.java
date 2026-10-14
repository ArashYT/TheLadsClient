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
        LOG.info("Lads mod presence: forge={} fabric-api={} appleskin={} jade={} chat_heads={} voicechat={} xaerominimap={} tabtweaks={} durabilitytooltip={} paperdoll={} custom-crosshair-mod={}",
            ForgeOverlays1201.present(), Mods1201.loaded("fabric-api"), Mods1201.loaded("appleskin"), Mods1201.loaded("jade"),
            Mods1201.loaded("chat_heads"), Mods1201.loaded("voicechat"), Mods1201.loaded("xaerominimap"), Mods1201.loaded("tabtweaks"),
            Mods1201.loaded("durabilitytooltip"), Mods1201.loaded("paperdoll"), Mods1201.loaded("custom-crosshair-mod"));
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
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    private static void tick(net.minecraft.client.Minecraft client) {
        Minimap1201.tick();
        HudQa1201.tick(client);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Module> MODULE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, com.thelads.core.config.Option> OPTION_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static Module module(String name) {
        Module cached = MODULE_CACHE.get(name);
        if (cached != null) return cached;
        Module m = ModuleManager.getInstance().getModule(name);
        if (m != null) MODULE_CACHE.put(name, m);
        return m;
    }

    public static com.thelads.core.config.Option option(String modName, String optName) {
        String key = modName + "/" + optName;
        com.thelads.core.config.Option cached = OPTION_CACHE.get(key);
        if (cached != null) return cached;
        Module m = module(modName);
        if (m != null) {
            com.thelads.core.config.Option opt = m.getOption(optName);
            if (opt != null) {
                OPTION_CACHE.put(key, opt);
                return opt;
            }
        }
        return null;
    }

    public static boolean enabled(String name) {
        Module module = module(name);
        return module != null && module.isEnabled();
    }

    public static boolean bool(String module, String option, boolean fallback) {
        return option(module, option) instanceof BoolOption setting ? setting.get() : fallback;
    }

    public static int choice(String module, String option, int fallback) {
        return option(module, option) instanceof DropdownOption setting ? setting.getIndex() : fallback;
    }

    public static double number(String module, String option, double fallback) {
        return option(module, option) instanceof SliderOption setting ? setting.getValue() : fallback;
    }

    public static String string(String module, String option, String fallback) {
        return option(module, option) instanceof com.thelads.core.config.StringOption setting ? setting.get() : fallback;
    }
}
