package com.thelads.core.v1_21_1.feature;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;

/** Features owned by Lads, with no upstream mod configuration or runtime dependency. Same role and helpers as 26.x. */
public final class NativeQualityOfLife {
    private NativeQualityOfLife() {}

    /** Ported features register here (registerBuiltIn plus their own register()) once their hooks work on 1.21.1. */
    public static void register() {
        // NativeFeatures through KeyboardHandler/MouseHandler/KeyboardInput/SprintInput/Zoom/Fullbright mixins.
        ModuleSupport.registerBuiltIn("Zoom", "ToggleSprint", "ToggleSneak", "Fullbright");
        NativeKeyBindings.register();
        // "Soon" card as on 26.x, whose presence tick sends nothing yet; no Discord connection is made.
        ModuleSupport.registerBuiltIn("DiscordRPC");
        // U3 HUD pipeline: the hud mixins (Autohide scope, SmoothHotbar, BossBar overlay).
        ModuleSupport.registerBuiltIn("Autohide", "SmoothHotbar", "BossBar");
        NativeAutohide.register();
        // QA only (-Dthelads.verifyAutoWorld): the isolated auto-world runtime; registers nothing in normal launches.
        NativeWorldVerification.register();
    }

    /** Every client tick, from ClientTickMixin (Minecraft.tick HEAD). */
    public static void tick() {
        // Registers "Minimap" on the first tick once Xaero is found (the catalog export follows the revision).
        MinimapIntegration.tick();
        NativeQualityProbe.tick();
        NativeMenuAccessProbe.tick();
        NativeHudProbe.tick();
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
}
