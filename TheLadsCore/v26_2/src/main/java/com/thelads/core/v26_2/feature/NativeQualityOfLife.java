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
            "Nametags", "SignalLoss", "DynamicFPS", "VerticalBobbing", "FarBlockEntities", "KillBanner", "ShulkerBoxUtils", "DiscordRPC", "BetterResolution",
            com.thelads.core.modules.OldAnimationsModule.NAME);
        // A loaded Custom Crosshair Mod or Durability Tooltip jar keeps its module (ExternalModSettings registered it as installed).
        if (!com.thelads.core.v26_2.feature.crosshair.NativeCrosshair.externalPresent()) ModuleSupport.registerBuiltIn("Crosshair Tweaks");
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("durabilitytooltip")) ModuleSupport.registerBuiltIn("EnhancedToolbars");
        Raised26.register();
        ModuleSupport.registerBuiltIn(com.thelads.core.modules.MouseTweaksModule.NAME); // NativeMouseTweaks
        ((com.thelads.core.config.ActionOption)module("Jade").getOption("Settings and addons")).setAction(()->{
            var mc=Minecraft.getInstance();mc.setScreenAndShow(new snownee.jade.gui.HomeConfigScreen(mc.gui.screen()));
        });
        for (String voice : new String[] {"Voice Chat", "Voice Chat Group"})
            if (VoiceChatIntegration.loaded()) ModuleSupport.registerBuiltIn(voice);
            else ModuleSupport.registerUnavailable(voice, "Simple Voice Chat is not installed in this game.");
        NativeClientTools.register();
        NativeConnectionStatus.register();
        NativeReconnect.register();
        ConnectionTweaks.register();
        com.thelads.core.v26_2.feature.crosshair.NativeCrosshair.register();
    }

    public static void tick() {
        NativeClientTools.tick();
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
    /** RawInput is vanilla's Raw Input (Mouse Settings): the module sets it, and a change made there flows back into the module. */
    private static void rawInput() {
        var options = Minecraft.getInstance().options;
        Module module = module("RawInput");
        if (module == null) return;
        boolean vanilla = options.rawMouseInput().get();
        if (rawApplied != null && vanilla != rawApplied) {
            module.setEnabled(vanilla);
            com.thelads.core.config.ConfigManager.save();
        } else if (vanilla != module.isEnabled()) {
            options.rawMouseInput().set(module.isEnabled());
            options.save();
        }
        rawApplied = options.rawMouseInput().get();
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
