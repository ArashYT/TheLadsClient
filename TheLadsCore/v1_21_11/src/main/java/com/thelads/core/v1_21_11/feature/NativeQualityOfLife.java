package com.thelads.core.v1_21_11.feature;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;

/** Features owned by Lads, with no upstream mod configuration or runtime dependency. Same role and helpers as 26.x. */
public final class NativeQualityOfLife {
    private NativeQualityOfLife() {}

    /** Ported features register here (registerBuiltIn plus their own register()) once their hooks work on 1.21.11. */
    public static void register() {
        // ChatMixin / ChatIndicatorMixin / ScreenshotChatMixin and BorderlessWindowMixin, as on 26.x.
        ModuleSupport.registerBuiltIn("Chat", "BorderlessFullscreen");
        // 1.21.11 is frozen: Discord presence (1.7.1) was not ported, so no Discord connection is made here.
        ModuleSupport.registerUnavailable("DiscordRPC", "Discord presence runs on Minecraft 1.8.9, 26.2 and 26.3.");
        // U3 HUD pipeline: the hud mixins (Autohide scope and faded GUI states, SmoothHotbar, BossBar overlay).
        ModuleSupport.registerBuiltIn("RawInput", "Autohide", "SmoothHotbar", "BossBar");
        NativeAutohide.register();
        // NativeKillBanner through the KillBanner stats, attack and HUD mixins, as on 26.x.
        ModuleSupport.registerBuiltIn("KillBanner");
        // NametagMixin, NametagShadowMixin, OwnNametagMixin, NicknameTabMixin and the ChatMixin rename, as on 26.x.
        ModuleSupport.registerBuiltIn("Nametags");
        // NativeOldAnimations through the OldAnimations* mixins, ClientTickMixin (swing while using) and GuiMixin (hearts).
        ModuleSupport.registerBuiltIn(com.thelads.core.modules.OldAnimationsModule.NAME);
        // LegacySwingMixin (and OldAnimationsHandMixin's no-dip tick hook), as on 26.x.
        ModuleSupport.registerBuiltIn("LegacySwing");
        // QA only (-Dthelads.verifyAutoWorld): the isolated auto-world runtime; registers nothing in normal launches.
        NativeWorldVerification.register();
    }

    /** Every client tick, from ClientTickMixin (Minecraft.tick HEAD). */
    public static void tick() {
        // Registers "Minimap" on the first tick once Xaero is found (the catalog export follows the revision).
        MinimapIntegration.tick();
        NativeKillBanner.tick();
        rawInput();
        NativeQualityProbe.tick();
        NativeMenuAccessProbe.tick();
        NativeHudProbe.tick();
    }

    private static Boolean rawApplied;
    /** RawInput is vanilla's Raw Input (Mouse Settings): the module sets it, and a change made there flows back into the module. */
    private static void rawInput() {
        var options = net.minecraft.client.Minecraft.getInstance().options;
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
}
