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
import net.minecraft.client.gui.screens.ChatScreen;

/** Features owned by Lads, with no upstream mod configuration or runtime dependency. */
public final class NativeQualityOfLife {
    private static boolean narratorWasDisabled;
    private NativeQualityOfLife() {}

    public static void register() {
        ModuleSupport.registerBuiltIn("LegacySwing", "BossBar", "DisableNarrator", "HideChatIndicators", "Crosshair Tweaks",
            "SmoothHotbar", "Raised", "OldDamageTilt", "EnhancedToolbars", "EnhancedTooltips",
            "ToggleNametags", "SignalLoss", "DynamicFPS", "VerticalBobbing", "FarBlockEntities", "KillBanner", "ShulkerBoxUtils", "DiscordRPC", "RenderScale");
        NativeClientTools.register();
        NativeDiscordPresence.register();
        NativeConnectionStatus.register();
        NativeReconnect.register();
        com.thelads.core.v26_2.feature.crosshair.NativeCrosshair.register();
    }

    public static void tick() {
        NativeClientTools.tick();
        NativeNarrator.tick();
        MinimapIntegration.tick();
        com.thelads.core.v26_2.feature.raised.NativeRaisedProbe.tick();
        boolean disabled = enabled("DisableNarrator");
        if (disabled && !narratorWasDisabled) Minecraft.getInstance().getNarrator().clear();
        narratorWasDisabled = disabled;
        NativeKillBanner.tick();
        ShulkerContents.tick();
        NativeDiscordPresence.tick();
        NativeQualityProbe.tick();
        NativeSharedContentProbe.tick();
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
    public static int raisedDistance() {
        return com.thelads.core.v26_2.feature.raised.NativeRaised.active() && enabled("Raised") && Minecraft.getInstance().gui.screen() instanceof ChatScreen
            ? (int) number("Raised", "Distance", 14) : 0;
    }

}
