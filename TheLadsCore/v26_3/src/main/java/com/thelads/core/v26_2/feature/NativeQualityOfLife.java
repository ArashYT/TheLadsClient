package com.thelads.core.v26_2.feature;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.CrosshairModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Features owned by Lads, with no upstream mod configuration or runtime dependency. */
public final class NativeQualityOfLife {
    private static boolean narratorWasDisabled;
    /** True while the chat draws with the Chat module's Text Shadow off (ChatTextShadowMixin). */
    public static boolean chatWithoutShadow;
    private NativeQualityOfLife() {}

    public static void register() {
        ModuleSupport.registerBuiltIn("RawInput", "Autohide", "Jade", "LegacySwing", "BossBar", "DisableNarrator", "Chat", "BorderlessFullscreen",
            "SmoothHotbar", "Raised", "OldDamageTilt", "EnhancedTooltips",
            "Nametags", "SignalLoss", "DynamicFPS", "VerticalBobbing", "FarBlockEntities", "KillBanner", com.thelads.core.modules.ComboSoundsModule.NAME, "ShulkerBoxUtils", "DiscordRPC", "BetterResolution",
            com.thelads.core.modules.OldAnimationsModule.NAME, "ResourcePacks");
        // A loaded Custom Crosshair Mod or Durability Tooltip jar keeps its module (ExternalModSettings registered it as installed).
        if (!com.thelads.core.v26_2.feature.crosshair.NativeCrosshair.externalPresent()) ModuleSupport.registerBuiltIn("Crosshair Tweaks");
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("durabilitytooltip")) ModuleSupport.registerBuiltIn("EnhancedToolbars");
        Raised26.register();
        ModuleSupport.registerBuiltIn(com.thelads.core.modules.MouseTweaksModule.NAME); // NativeMouseTweaks
        ModuleSupport.registerBuiltIn(com.thelads.core.client.hud.ActionBar.MODULE); // NativeHudMixin, ActionBarHudElement
        ((com.thelads.core.config.ActionOption)module("Jade").getOption("Settings and addons")).setAction(()->{
            var mc=Minecraft.getInstance();mc.setScreenAndShow(new snownee.jade.gui.HomeConfigScreen(mc.gui.screen()));
        });
        for (String voice : new String[] {"Voice Chat", "Voice Chat Group"})
            if (VoiceChatIntegration.loaded()) ModuleSupport.registerBuiltIn(voice);
            else ModuleSupport.registerUnavailable(voice, "Simple Voice Chat is not installed in this game.");
        // The Entity Culling switch is the 1.8.9 Core's own culling; 26.x culls hidden entities by itself, with no switch.
        ModuleSupport.registerUnavailable("EntityCulling", "Only for Minecraft 1.8.9: this version already skips drawing hidden entities by itself.");
        NativeClientTools.register();
        NativeConnectionStatus.register();
        NativeReconnect.register();
        ConnectionTweaks.register();
        com.thelads.core.v26_2.feature.crosshair.NativeCrosshair.register();
        com.thelads.core.v26_2.feature.survivaltweaks.NativeSurvivalTweaks.register();
        NativeStackRefill.register();
    }

    public static void tick() {
        NativeClientTools.tick();
        NativeStackRefill.tick();
        com.thelads.core.v26_2.feature.cartcam.MinecartCameraRotation.onEndClientTick(Minecraft.getInstance());
        NativeNarrator.tick();
        MinimapIntegration.tick();
        Raised26.titleProbe();
        com.thelads.core.v26_2.feature.paperdoll.PaperDoll26.tick(Minecraft.getInstance());
        boolean disabled = enabled("DisableNarrator");
        if (disabled && !narratorWasDisabled) Minecraft.getInstance().getNarrator().clear();
        narratorWasDisabled = disabled;
        NativeKillBanner.tick();
        rawInput();
        ShulkerContents.tick();
        NativeDiscordPresence.tick();
        NativeQualityProbe.tick();
        NativeSharedContentProbe.tick();
    }

    private static Boolean rawApplied;
    /**
     * RawInput: Minecraft 26.3 has no Raw Input switch; its mouse (SDL relative mode) is raw already. On keeps that; off applies
     * Windows' pointer speed and acceleration, as vanilla's switch did when it was off.
     */
    private static void rawInput() {
        boolean on = enabled("RawInput");
        if (rawApplied != null && rawApplied == on) return;
        org.lwjgl.sdl.SDLHints.SDL_SetHint(org.lwjgl.sdl.SDLHints.SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE, on ? "0" : "1");
        rawApplied = on;
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
